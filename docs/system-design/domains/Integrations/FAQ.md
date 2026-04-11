---
tags:
  - architecture/domain
  - domain/integration
  - tools/nango
Created: 2026-03-18
Domains:
  - "[[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Integrations]]"
---
# Integrations FAQ

This document answers common questions about the Integrations domain — authentication flow, Nango configuration, webhook handling, connection lifecycle, and data sync mechanics.

---

### How does the OAuth/authentication flow work from the UI?

The authentication flow is webhook-driven, not API-driven:

1. **Frontend** opens the Nango Connect UI, passing three tags: `endUserId` (user UUID), `organizationId` (workspace UUID), and `endUserEmail` (integration definition UUID)
2. **Nango** handles the complete OAuth flow with the third-party provider (e.g. HubSpot)
3. **Nango** sends a signed webhook (`POST /api/v1/webhooks/nango`) with type `"auth"` and `success: true`
4. **[[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookHmacFilter]]** validates the `X-Nango-Hmac-Sha256` signature
5. **[[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookService]]** parses the tags, creates/reconnects the connection, creates/restores the installation, and triggers template materialization
6. **Frontend** polls the workspace integration status endpoint (`GET /api/v1/integrations/{workspaceId}/status`) to detect the new connection

The backend does NOT have a `POST /enable` endpoint for connection creation. All connections originate from the Nango auth webhook.

---

### What tags must be passed to Nango during OAuth?

Three tags are required, mapped to Nango's available tag fields:

| Nango Field      | JSON Key          | Riven Value                          | Purpose                                                      |
| ---------------- | ----------------- | ------------------------------------ | ------------------------------------------------------------ |
| `endUserId`      | `end_user_id`     | User UUID (string)                   | Identifies who initiated the OAuth flow for activity logging |
| `organizationId` | `organization_id` | Workspace UUID (string)              | Identifies the workspace to create the connection in         |
| `endUserEmail`   | `end_user_email`  | Integration Definition UUID (string) | Identifies which integration is being connected              |

**Why `endUserEmail` for integration definition ID?** Nango only provides three tag fields. Since `endUserEmail` is the only remaining field, it is pragmatically repurposed to carry the integration definition UUID. The webhook handler knows to parse it as a UUID, not as an email address.

All three fields must be valid UUID strings. Missing or malformed tags cause the webhook handler to skip processing with an error log.

---

### How does the webhook recognize the correct workspace and entities?

The webhook payload includes the tags set by the frontend during OAuth initiation. [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookService]] parses these tags:

1. `organizationId` → `workspaceId` — identifies the workspace
2. `endUserId` → `userId` — identifies the user for activity logging
3. `endUserEmail` → `integrationDefinitionId` — identifies which integration to connect

Each value is validated as a UUID using `UUID.fromString()`. The `integrationDefinitionId` is then used to look up the `IntegrationDefinitionEntity`, which provides the integration slug needed for catalog lookup and template materialization.

The `connectionId` field in the payload is Nango's internal connection identifier — it is stored on the `IntegrationConnectionEntity` as `nangoConnectionId` for future API calls to Nango (fetch records, trigger syncs, etc.).

---

### What happens if the webhook fails?

**Nango always receives a 200 response**, regardless of processing outcome. This is critical because Nango interprets non-200 responses as delivery failures and retries.

Internal failure handling:

| Failure Type | Behavior | State After Failure |
|---|---|---|
| Missing/malformed tags | Early return, error logged | No state changes |
| `success != true` | Skip processing, warning logged | No state changes |
| Integration definition not found | Early return within transaction | No state changes |
| Connection creation fails | Transaction rolls back, exception caught by top-level handler | No state changes |
| Materialization fails | Installation set to `FAILED`, transaction commits | Connection: `CONNECTED`, Installation: `FAILED` |
| Any unexpected exception | Caught by `handleWebhook()` top-level try-catch | Depends on where failure occurred |

The key design decision: **materialization failure does not roll back the connection**. A `CONNECTED` connection with a `FAILED` installation means OAuth succeeded but workspace setup is incomplete. The connection can be reused — a subsequent auth webhook or manual intervention can retry materialization.

---

### How does reconnection work after disconnect/failure?

The `createOrReconnectConnection()` method in [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Webhook Authentication/NangoWebhookService]] handles 4 scenarios:

| Existing Connection State | Action |
|---|---|
| No existing connection | Create new with `CONNECTED` status |
| `DISCONNECTED` | Set status to `CONNECTED`, update `nangoConnectionId` |
| `FAILED` | Set status to `CONNECTED`, update `nangoConnectionId` |
| `CONNECTED` | Idempotent: update `nangoConnectionId` if changed, otherwise no-op |

For installations, `findOrCreateInstallation()` handles:

| Existing Installation | Action |
|---|---|
| Active installation exists | Set status to `ACTIVE` |
| Soft-deleted installation exists | Restore: clear `deleted`/`deletedAt`, set status to `ACTIVE` |
| No installation | Create new with `ACTIVE` status |

**Deterministic UUIDs ensure idempotent materialization.** When [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/TemplateMaterializationService]] runs on reconnection, it checks for existing entity types by key. Soft-deleted types are restored, active types are skipped, and only genuinely new types are created. The deterministic UUID v3 ensures attribute keys remain stable across reconnections.

---

### What is the connection vs installation lifecycle?

These are independent lifecycle concepts:

**Connection** (`IntegrationConnectionEntity`):
- Represents the Nango OAuth state for a workspace ↔ integration pair
- 8-state machine: `CONNECTED`, `SYNCING`, `HEALTHY`, `DEGRADED`, `STALE`, `DISCONNECTING`, `DISCONNECTED`, `FAILED`
- One connection per workspace per integration (not soft-deletable — state machine handles lifecycle)
- Created by webhook handler, disconnected by disable flow

**Installation** (`WorkspaceIntegrationInstallationEntity`):
- Represents the workspace enablement state — "is this integration active in this workspace?"
- 3-state lifecycle: `PENDING_CONNECTION`, `ACTIVE`, `FAILED`
- Soft-deletable for disable/re-enable pattern
- Tracks `lastSyncedAt` for gap recovery, `syncConfig` for sync scope preferences

**They can diverge:** A connection can be `CONNECTED` while the installation is `FAILED` (OAuth succeeded, materialization failed). Conversely, the installation is soft-deleted on disable while the connection transitions to `DISCONNECTED`.

---

### How does template materialization work?

[[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Enablement/TemplateMaterializationService]] bridges the global catalog (string-keyed) and workspace entity types (UUID-keyed):

1. **Manifest lookup** — Finds the integration manifest by slug via `ManifestCatalogRepository`
2. **Catalog fetch** — Loads all `CatalogEntityTypeEntity` and `CatalogRelationshipEntity` entries for the manifest
3. **Deduplication check** — Queries workspace for existing and soft-deleted entity types matching catalog keys
4. **Entity type materialization** — For each catalog entry:
   - Soft-deleted → restore (clear `deleted`/`deletedAt`)
   - Already existing → skip but include in key-to-ID map
   - New → create with `sourceType = INTEGRATION`, `readonly = true`, `protected = true`
5. **Schema conversion** — Catalog schemas (`Map<String, Any>`) are converted to `EntityTypeSchema` with UUID-keyed attributes using deterministic UUID v3 from `{slug}:{entityTypeKey}:{attributeKey}`
6. **Metadata initialization** — Semantic metadata, fallback CONNECTED_ENTITIES relationship, and ID sequences
7. **Relationship materialization** — Catalog relationships are resolved using the key-to-ID map, with deduplication (skip existing, restore soft-deleted)

The `integrationDefinitionId` parameter was added in Phase 2 to set `sourceIntegrationId` on materialized entity types, enabling the dedup index for sync.

---

### How does field mapping/transformation work?

[[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Data Sync/SchemaMappingService]] applies declarative field mappings to transform external payloads:

**Transform types:**

| Type | Behavior | Example |
|---|---|---|
| `Direct` | Pass-through, no transformation | `email` → `email` |
| `TypeCoercion` | Convert to target type (STRING, NUMBER, BOOLEAN, DATE, DATETIME) | `"42"` → `42.0` |
| `DefaultValue` | Use source if present, fall back to default | `null` → `"Unknown"` |
| `JsonPathExtraction` | Extract nested value via dot-separated path | `address.city` from `{address: {city: "NYC"}}` |

**Error isolation:** Each field is mapped independently. Transform failures produce `MappingError` entries; missing source fields produce `MappingWarning` entries. The result includes a `FieldCoverage` ratio for health monitoring.

**No exceptions from `mapPayload()`.** Callers check the `errors` list on `MappingResult` to detect failures.

---

### What is the 8-state connection state machine?

The connection lifecycle is governed by `ConnectionStatus` with validated transitions via `canTransitionTo()`:

| State | Valid Transitions To | Description |
|---|---|---|
| `CONNECTED` | `SYNCING`, `HEALTHY`, `DISCONNECTING`, `FAILED` | OAuth succeeded, ready for sync |
| `SYNCING` | `HEALTHY`, `DEGRADED`, `FAILED` | Sync in progress |
| `HEALTHY` | `SYNCING`, `STALE`, `DEGRADED`, `DISCONNECTING`, `FAILED` | Syncs completing successfully |
| `DEGRADED` | `HEALTHY`, `STALE`, `FAILED`, `DISCONNECTING` | Partial sync failures |
| `STALE` | `SYNCING`, `DISCONNECTING`, `FAILED` | No recent sync activity |
| `DISCONNECTING` | `DISCONNECTED`, `FAILED` | Disconnect in progress |
| `DISCONNECTED` | `CONNECTED` | OAuth disconnected, can reconnect |
| `FAILED` | `CONNECTED`, `DISCONNECTED` | Unrecoverable error, can reconnect or disconnect |

**Removed states (Phase 2):** `PENDING_AUTHORIZATION` and `AUTHORIZING` were removed. With the webhook-driven flow, connections are created directly in `CONNECTED` state — the OAuth intermediate states are now handled entirely by Nango.

**Sync states (`SYNCING`, `HEALTHY`, `DEGRADED`, `STALE`)** will feed back from Temporal workflow execution in Phase 3+.

---

### How does disable/re-enable preserve data?

**On disable:**

1. All integration-created entity types and relationships are **soft-deleted** (not hard-deleted)
2. `lastSyncedAt` is **snapshotted** on the installation record before soft-deletion
3. The installation is soft-deleted
4. The Nango connection is disconnected (best-effort)

**On re-enable (next auth webhook):**

1. The connection is created/reconnected with `CONNECTED` status
2. The soft-deleted installation is **restored** (cleared `deleted`/`deletedAt`), preserving the original `lastSyncedAt`
3. Template materialization checks for existing entity types:
   - Soft-deleted entity types are **restored** (not recreated)
   - Active entity types are **skipped** (preserving any data)
   - New entity types (added to catalog since last enable) are **created**
4. The preserved `lastSyncedAt` enables **gap recovery** — sync can resume from where it left off rather than re-syncing all data

**Deterministic UUID v3** ensures that attribute keys remain stable across disable/re-enable cycles, so existing entity data remains valid after restoration.
