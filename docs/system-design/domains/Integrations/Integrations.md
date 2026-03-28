---
tags:
  - architecture/domain
  - domain/integration
  - tools/nango
Created: 2025-05-01
Updated: 2026-03-27
---
# Domain: Integrations

---

## Overview

The Integrations domain manages the full lifecycle of third-party integrations within workspaces — enabling, disabling, tracking connection state via Nango for OAuth-based integrations, and tracking per-entity-type sync progress. Integration enablement is **webhook-driven**: when a user completes OAuth in the Nango Connect UI, Nango sends a signed webhook that triggers connection creation, installation tracking, and template materialization. The domain materializes catalog-defined entity type templates into workspace-scoped entity types with deterministic UUID mapping, creating a readonly data layer that external sync processes can populate. Connection state is governed by an 8-state state machine enforced by [[IntegrationConnectionService]], while [[NangoWebhookService]] handles the auth webhook flow and [[IntegrationEnablementService]] orchestrates the disable workflow including soft-delete, Nango disconnect, and gap recovery. Sync progress per connection per entity type is tracked by [[IntegrationSyncStateEntity]] with cursor-based incremental sync support.

---

## Boundaries

### This Domain Owns

- Integration enable/disable lifecycle orchestration
- Nango connection state machine and OAuth connection management
- Workspace integration installation tracking (per-workspace enable state, sync config, and installation status lifecycle)
- Template materialization — creating workspace-scoped entity types and relationships from catalog definitions
- Field mapping between catalog string keys and workspace UUID keys (deterministic UUID v3)
- Integration definition persistence and stale flag management
- Per-connection per-entity-type sync state tracking (cursor, failure counts, record metrics)

### This Domain Does NOT Own

- Catalog definitions (manifest scanning, resolution, persistence) — owned by [[Catalog]] domain
- Entity type schema management and user-defined attributes — owned by [[Entities]] domain
- Sync execution and data ingestion — future, will be orchestrated by Temporal workflows
- OAuth flow UI — frontend responsibility; backend receives completion notification via Nango webhook

---

## Sub-Domains

| Sub-Domain | Purpose |
|---|---|
| [[Enablement]] | Orchestrates the disable lifecycle — soft-deletes entity types, disconnects Nango, snapshots sync state. Template materialization and installation tracking live here but are now invoked by [[Webhook Authentication]] |
| [[Connection Management]] | Manages Nango connection lifecycle and the 8-state connection state machine |
| [[Data Sync]] | Tracks per-connection per-entity-type sync progress — status, cursor position, failure counts, and record metrics |
| [[Webhook Authentication]] | Handles inbound Nango webhook events — HMAC signature validation, auth event processing (connection creation + materialization), sync event routing |

---

## Flows

| Flow | Type | Description |
|---|---|---|
| [[Flow - Auth Webhook]] | Background | Nango sends auth webhook after OAuth → HMAC validation → connection creation → installation tracking → materialization |
| [[Flow - Integration Disable]] | User-facing | Workspace admin disables an integration — soft-deletes entity types, disconnects Nango, snapshots sync state |

---

## Data

### Owned Entities

| Entity | Purpose | Key Fields |
|---|---|---|
| IntegrationDefinitionEntity | Global catalog entry for each supported integration | id, name, slug, nango_provider_key, stale |
| IntegrationConnectionEntity | Per-workspace Nango connection with state machine | id, workspace_id, integration_id, nango_connection_id, status |
| WorkspaceIntegrationInstallationEntity | Tracks enabled integrations per workspace with sync config and installation status | id, workspace_id, integration_definition_id, manifest_key, sync_config, status, last_synced_at, deleted |
| IntegrationSyncStateEntity | Per-connection per-entity-type sync progress | id, integration_connection_id, entity_type_id, status, last_cursor, consecutive_failure_count |

### Database Tables

| Table | Entity | Notes |
|---|---|---|
| integration_definitions | IntegrationDefinitionEntity | System-managed, uses stale flags not soft-delete |
| integration_connections | IntegrationConnectionEntity | One per workspace per integration, state machine enforced |
| workspace_integration_installations | WorkspaceIntegrationInstallationEntity | Soft-deletable, unique on (workspace_id, integration_definition_id), `status` column tracks installation lifecycle |
| integration_sync_state | IntegrationSyncStateEntity | System-managed, CASCADE deletes on connection/entity-type removal, unique on (integration_connection_id, entity_type_id) |

---

## External Dependencies

| Dependency | Purpose | Failure Impact |
|---|---|---|
| Nango | OAuth connection management — connection creation, reconnection, and deletion | Enable/disable proceeds locally; Nango cleanup errors are caught gracefully and logged |

---

## Domain Interactions

### Depends On

| Domain | What We Consume | Via Component | Related Flow |
|--------|----------------|---------------|--------------|
| [[Entities]] | Creates and soft-deletes workspace-scoped entity types and relationships | [[EntityTypeService]], [[EntityTypeRelationshipService]], [[EntityTypeSemanticMetadataService]], [[EntityTypeSequenceService]] | [[Flow - Auth Webhook]] |
| [[Catalog]] | Reads manifest catalog entries, catalog entity types, catalog relationships, and target rules | [[ManifestCatalogRepository]], [[CatalogEntityTypeRepository]], [[CatalogRelationshipRepository]] | [[Flow - Auth Webhook]] |
| [[Activity]] | Logs enable/disable and connection operations | [[ActivityService]] | All mutation flows |

### Consumed By

| Consumer | What They Consume | Via Component | Related Flow |
|----------|------------------|---------------|--------------|
| [[Catalog]] | Stale flag propagation from catalog to integration_definitions after manifest loads | [[IntegrationDefinitionStaleSyncService]] | [[Flow - Manifest Loading Pipeline]] |
| [[Entities]] | Readonly guard — checks `sourceType` on entity types to prevent modification of integration-sourced types | EntityTypeEntity.sourceType field | Entity update/delete flows |
| [[Entities]] | Integration dedup index — looks up entities by `(workspace_id, source_integration_id, source_external_id)` for deduplication during sync | [[EntityRepository]] | Integration data sync |

---

## Key Decisions

| Decision | Summary |
|---|---|
| Soft-delete for disable | Disabling soft-deletes the installation and entity types rather than hard-deleting, preserving `lastSyncedAt` for gap recovery when re-enabling |
| Readonly entity types | Integration-sourced entity types are created with `readonly = true` and `protected = true` to prevent user modifications that would break sync contracts |
| Deterministic UUIDs | UUID v3 generated from `integration:entityTypeKey:attributeKey` ensures idempotent materialization — same input always produces the same attribute UUID across reconnections |
| Connection state machine | 8-state model with validated transitions. `PENDING_AUTHORIZATION` and `AUTHORIZING` were removed in Phase 2 — connections are created directly in `CONNECTED` state by the webhook handler since OAuth intermediates are handled by Nango |
| Webhook-driven authentication | Connection creation is exclusively webhook-driven via `NangoWebhookService` — there is no `POST /enable` API endpoint. This eliminates client-side coordination of OAuth completion and connection creation |
| Graceful Nango failure handling | Nango API errors during disconnect are caught and logged rather than propagated — local state should always reach a consistent end state |
| Installation status lifecycle | 3-state `InstallationStatus` enum (PENDING_CONNECTION, ACTIVE, FAILED) with `canTransitionTo()` validation tracks installation health independently of connection state |
| DB-level entity deduplication | Unique partial index `idx_entities_integration_dedup` on `(workspace_id, source_integration_id, source_external_id)` WHERE `deleted = false` enforces one entity per integration source at the database level |

---

## Technical Debt

| Issue | Impact | Effort |
|---|---|---|
| None yet | - | - |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2025-07-17 | Integration enablement lifecycle: enable/disable endpoints, template materialization, connection management, workspace installations | Integration Enablement |
| 2026-03-17 | Sync persistence foundation: IntegrationSyncStateEntity + repository, InstallationStatus enum on installations, SyncStatus enum, new Data Sync subdomain | Integration Sync Persistence Foundation |
| 2026-03-18 | Connection model simplification (10→8 states), Nango client extensions (fetchRecords, triggerSync), webhook endpoint with HMAC verification, auth webhook handler that creates connections and triggers materialization, schema mapping engine | Integration Sync Phase 2 |
| 2026-03-27 | Entity ingestion pipeline — Temporal-orchestrated sync workflows, field mapping, identity resolution, entity projection | Entity Ingestion Pipeline |

---

## Entity Ingestion Pipeline

NangoWebhookService is being extended to dispatch Temporal workflows on sync events (currently a stub). When a sync completes, the webhook handler triggers the ingestion pipeline as a Temporal workflow.

### Pipeline Steps

The ingestion pipeline is a 4-step process:

1. **Classify** — Determine the integration entity type from the incoming records
2. **Route** — Match the integration entity type to the target core entity type via catalog definitions
3. **Map** — Transform integration fields to core schema using `FieldMappingService` and `CatalogFieldMappingEntity`
4. **Resolve** — Run identity resolution to match or create core entities via `IdentityResolutionService`

> [!info] Temporal orchestration
> The pipeline runs as Temporal workflows (`IntegrationSyncWorkflow`) with activity-level retry and cursor pagination. `IntegrationSyncStateEntity` tracks sync state (cursors, failure counts) per connection per entity type.

### New Components

| Component | Purpose |
|---|---|
| `FieldMappingService` | Applies catalog field mappings to transform integration schema → core schema |
| `IdentityResolutionService` | Ingestion-time identity matching — resolves incoming data against existing entities |
| `EntityProjectionService` | Creates projected core entity rows from mapped integration data |
| `IntegrationSyncWorkflow` | Temporal workflow orchestrating the 4-step pipeline |

### Data Flow

Integration entity rows are created (hidden, readonly) alongside projected core entity rows (visible, hub). Field mapping uses `CatalogFieldMappingEntity` to transform integration source fields into the core entity type schema, handling type coercion, value mapping, and JSONPath extraction.

### References

- [[Entity Ingestion Pipeline]]
- [[Smart Projection Architecture]]
