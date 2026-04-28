---
tags:
  - adr/accepted
  - architecture/decision
Created: 2026-03-16
---
# ADR-008: Integration-Based Entity Types as Readonly Materialized Views

---

## Context

The system connects to third-party SaaS tools (HubSpot, Salesforce, Stripe, Zendesk, Intercom, Gmail) and materializes their data models into the workspace-scoped entity ecosystem. A core architectural question is: how should integration data coexist with user-created data?

Two competing models were considered during the design of the integration entity system:

1. **Merged model** — Integration data is merged into user-defined entity types. A user's `Customer` entity type gains attributes populated by HubSpot, Stripe, etc. Attribute-level provenance tracks which fields came from which source, with user override semantics allowing manual edits that survive re-sync.

2. **Separated model** — Each integration produces its own readonly entity types (e.g., `HUBSPOT_COMPANY`, `STRIPE_CUSTOMER`) that faithfully mirror the provider's data model. Users create their own entity types and connect them to integration types via relationships.

The merged model was initially explored and partially built (an `entity_attribute_provenance` table, JPA entity, and repository were created). However, it introduced significant complexity: conflict resolution when multiple integrations write to the same attribute, user override tracking per attribute per entity, re-sync logic that must respect overrides, and a blurred boundary between "what the source system said" and "what the user decided." The attribute-level provenance system was never wired into any service — no code ever read from or wrote to it.

The separated model was adopted instead, making the provenance system orphaned. It was subsequently removed (see commit history).

---

## Decision

Integration-based entity types are **readonly, protected materialized views** of external data models. They are structurally separate from user-defined entity types and connected via relationships, not merged at the attribute level.

### Core properties of integration entity types

| Property | Value | Enforced by |
|----------|-------|-------------|
| `sourceType` | `SourceType.INTEGRATION` | Set during materialization, immutable |
| `sourceIntegrationId` | UUID of the `IntegrationDefinitionEntity` | Set during materialization, immutable |
| `readonly` | `true` | Blocks schema modifications in `EntityTypeService` |
| `protected` | `true` | Blocks deletion by users |

### What readonly means in practice

- **Blocked:** Adding, removing, or modifying attributes. Changing the entity type name, description, or schema. Deleting the type.
- **Allowed:** Modifying column configuration (display preferences — column order, visibility, width). Overriding semantic metadata (definitions, classifications, tags) since these are interpretive, not structural.
- **Guard location:** `EntityTypeService.updateEntityTypeConfiguration()` checks `readonly` before applying mutations. `EntityTypeService.saveEntityTypeDefinition()` requires `!readonly` via a `require()` precondition.

### Entity-level source tracking (what remains)

Entity instances created by integration sync carry source metadata directly on `EntityEntity`:

| Field | Purpose |
|-------|---------|
| `sourceType` | `INTEGRATION` — discriminates synced records from user-created ones |
| `sourceIntegrationId` | Which integration synced this entity |
| `sourceExternalId` | Record ID in the external system (e.g., HubSpot contact ID) |
| `sourceUrl` | URL to view the record in the source system |
| `firstSyncedAt` | When this entity was first synced |
| `lastSyncedAt` | When this entity was last updated from the integration |
| `syncVersion` | Monotonic version counter for deduplication |

These fields exist on the entity row itself — no separate provenance table is needed because there is no attribute-level tracking. The `SourceType` enum (`USER_CREATED`, `INTEGRATION`, `IMPORT`, `API`, `WORKFLOW`) discriminates origin across the system.

### The two-layer model

```
Layer 1: Integration Entity Types (readonly, system-managed)
  HUBSPOT_COMPANY, HUBSPOT_CONTACT, HUBSPOT_DEAL
  STRIPE_CUSTOMER, STRIPE_SUBSCRIPTION, STRIPE_INVOICE
  ZENDESK_TICKET, INTERCOM_CONVERSATION, GMAIL_THREAD

Layer 2: User-Defined Entity Types (mutable, user-managed)
  Customer, Account, Project, ...

Connection: Relationships (not attribute merging)
  Customer ──one-to-one──> HUBSPOT_COMPANY
  Customer ──one-to-one──> STRIPE_CUSTOMER
  Customer ──one-to-one──> INTERCOM_CONTACT
```

Users build their mental model in Layer 2 and link it to source data in Layer 1. The knowledge layer reasons across both layers through relationships.

---

## Rationale

- **Data integrity at the storage layer.** Readonly types preserve exactly what the source system reported. There is no risk of user edits corrupting sync data or sync operations overwriting user decisions. What Stripe said and what HubSpot said are never mixed in the same row.
- **Eliminates conflict resolution complexity.** The merged model requires per-attribute conflict resolution when multiple integrations or user edits compete for the same field. The separated model has no conflicts — each integration owns its own types exclusively.
- **Eliminates attribute-level provenance tracking.** No need for a separate provenance table tracking which source wrote which attribute, whether the user overrode it, and when. Entity-level `sourceType` and `sourceIntegrationId` on the entity row itself is sufficient.
- **Simpler sync pipeline.** Re-sync writes to integration-owned types without checking for user overrides. The sync pipeline is a straightforward upsert keyed on `sourceExternalId` with `syncVersion` for deduplication.
- **Schema fidelity to source.** Integration entity types mirror the provider's actual data model (HubSpot's fields, Stripe's fields). This makes field mappings predictable and debugging straightforward — the entity type schema matches the API documentation.
- **User mental model is explicit.** Rather than implicitly merging data into opaque entities, users explicitly model their business concepts and connect them to data sources. This makes the data lineage visible and the user's model intentional.
- **Cross-domain reasoning still works.** The knowledge layer traverses relationships to pull data from integration types into user-defined contexts. A query like "show me customers where revenue is declining and support tickets are increasing" works by following relationships from `Customer` to `STRIPE_SUBSCRIPTION` and `INTERCOM_TICKET`.

---

## Alternatives Considered

### Option 1: Merged Attributes with Attribute-Level Provenance

Integration data is merged into user-defined entity types. Each attribute tracks its source via an `entity_attribute_provenance` table with fields for `sourceType`, `sourceIntegrationId`, `sourceExternalField`, `overrideByUser`, and `overrideAt`.

- **Pros:** Unified entity view — users see all data in one place. Fewer entity types to manage. Familiar "single customer record" mental model.
- **Cons:** Requires per-attribute conflict resolution (what happens when HubSpot and Salesforce both write `company_name`?). Requires user override tracking (if a user edits a synced field, re-sync must preserve the override). Provenance table adds write amplification on every sync. Schema becomes a union of all source schemas plus user additions — hard to reason about which fields are "real." Complicates the mapping engine: it must understand merge semantics, not just write semantics.
- **Why rejected:** The complexity cost was not justified by the UX benefit. The provenance system was partially built but never wired into any service — no code path ever read or wrote provenance records, confirming that the merged model's requirements were not being served. The separated model achieves the same analytical outcomes (cross-domain reasoning) with dramatically less machinery.

### Option 2: Virtual Merged Views (DB-level)

Keep physical separation but create database views that join integration entities with user-defined entities via relationships, presenting a merged view to the query layer.

- **Pros:** Physical data integrity preserved. Merged view available for read-heavy workloads. No conflict resolution at the write layer.
- **Cons:** View maintenance complexity. Performance implications of joins across multiple integration types. Still requires defining which fields from which sources appear in the merged view. The view definition becomes a schema in itself that needs management.
- **Why rejected:** Adds a layer of abstraction without clear benefit over direct relationship traversal. The knowledge layer already knows how to traverse relationships — a materialized view would duplicate that capability at the database layer with additional maintenance cost.

---

## Consequences

### Positive

- No attribute-level provenance tracking needed — entity-level `sourceType` and `sourceIntegrationId` fields on `EntityEntity` are sufficient. The `entity_attribute_provenance` table, JPA entity, and repository have been removed.
- Sync pipeline is a clean upsert with no override-checking logic. Each integration type is exclusively owned by the sync process.
- Schema modifications on integration types are blocked at the service layer via `readonly` flag checks. Users cannot accidentally break sync contracts.
- Integration type deletion is blocked via `protected` flag. Lifecycle is managed exclusively through the enable/disable flow.
- Enable/disable is fully reversible via soft-delete. Disabling soft-deletes entity types and snapshots `lastSyncedAt` on the installation record. Re-enabling restores types and allows sync to catch up from the snapshot point.
- Deterministic UUID generation (via `UUID.nameUUIDFromBytes`) ensures idempotent materialization — reconnecting the same integration produces identical workspace-scoped entity types and attribute IDs.

### Negative

- Users must create their own entity types and manually link them to integration types to build a unified view. This is an explicit design step that the merged model would have handled implicitly.
- More entity types in the workspace — each integration adds its own set of types rather than enriching existing ones. At scale (many integrations enabled), the type list grows. Mitigated by UI organization (grouping by source, collapsing integration types).
- Cross-domain queries require relationship traversal rather than attribute access on a single entity. This adds a hop but is architecturally consistent with how all entity relationships work in the system.

### Neutral

- The `SourceType` enum (`USER_CREATED`, `INTEGRATION`, `IMPORT`, `API`, `WORKFLOW`) serves as the universal discriminator for entity origin across both entity types and entity instances. It remains unchanged by this decision.
- Entity-level source fields (`sourceType`, `sourceIntegrationId`, `sourceExternalId`, `sourceUrl`, `firstSyncedAt`, `lastSyncedAt`, `syncVersion`) on `EntityEntity` are actively used by the sync pipeline and are not affected by the removal of attribute-level provenance.
- Integration entity type schemas are defined in declarative JSON manifests (see [[2. Areas/2.1 Startup & Content/Riven/2. System Design/decisions/ADR-004 Declarative-First Storage for Integration Mappings and Entity Templates]]) and materialized into workspaces by `TemplateMaterializationService`. This materialization path is unchanged.

---

## Implementation Notes

- **Materialization path:** `IntegrationEnablementService.enableIntegration()` → `TemplateMaterializationService.materializeIntegrationTemplates()` → reads catalog entity type definitions → creates workspace-scoped `EntityTypeEntity` with `sourceType=INTEGRATION, sourceIntegrationId={defId}, readonly=true, protected=true`.
- **Readonly enforcement** in `EntityTypeService`: `updateEntityTypeConfiguration()` allows only column configuration changes when `readonly=true`. `saveEntityTypeDefinition()` blocks with `require(!existing.readonly)`.
- **Soft-delete lifecycle:** `IntegrationEnablementService.disableIntegration()` → `EntityTypeService.softDeleteByIntegration(integrationId)` marks types as `deleted=true`. Re-enable restores them. `WorkspaceIntegrationInstallationEntity.lastSyncedAt` is snapshotted before disable for gap recovery.
- **Deterministic UUIDs:** Attribute IDs generated as `UUID.nameUUIDFromBytes("integration:entityTypeKey:attributeKey")` for idempotent materialization across reconnections.
- **Cross-integration relationships** (e.g., HubSpot Contact relates to Stripe Customer via shared email) are not declared in manifests. These are discovered at runtime through identity resolution and connected via the catch-all relationship mechanism without modifying readonly schemas.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/decisions/ADR-001 Nango as Integration Infrastructure]]
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/decisions/ADR-004 Declarative-First Storage for Integration Mappings and Entity Templates]]
- [[riven/docs/system-design/integrations/Integration Domain Strategy]]
- [[riven/docs/system-design/feature-design/_Sub-Domain Plans/Entity Integration Sync]]
- [[riven/docs/system-design/feature-design/3. Active/Integration Access Layer]]
- [[riven/docs/system-design/flows/Integration Connection Lifecycle]]
- [[riven/docs/system-design/feature-design/4. Completed/Predefined Integration Entity Types]]
