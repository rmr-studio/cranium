# Architecture Suggestions

## [2026-04-10] — Enrichment Pipeline Cross-Domain Coupling and Vector Dimension Drift

**Trigger:** Authored documentation for the new enrichment pipeline subdomain in `domains/Knowledge/Enrichment Pipeline/`. Several architectural observations surfaced that warrant human review.

**Affected vault notes:**

- `domains/Knowledge/Knowledge.md` — domain identity and dependency map
- `domains/Knowledge/Enrichment Pipeline/Enrichment Pipeline.md` — subdomain index
- `feature-design/_Sub-Domain Plans/Knowledge Layer.md` — original design plan referenced re-batching workflow that is not implemented yet
- Possibly a new `System Design/System Patterns/` note for "vector / embedding patterns"

**Suggested updates / things to review:**

1. **Knowledge → Entities coupling.** `EnrichmentService` injects 10 Entity-domain repositories directly (`EntityRepository`, `EntityTypeRepository`, `EntityAttributeService`, `EntityRelationshipRepository`, `RelationshipDefinitionRepository`, `IdentityClusterMemberRepository`, `RelationshipTargetRuleRepository`, `EntityTypeSemanticMetadataRepository`, plus the new `EntityEmbeddingRepository`). Historically the Knowledge domain was a controller-only thin layer that delegated everything via `EntityTypeSemanticMetadataService`. This is a significant boundary expansion and probably worth either: (a) introducing an `EnrichmentContextProvider` in the Entities domain that exposes a single `getEnrichmentSnapshot(entityId)` method, or (b) explicitly documenting that Knowledge is now allowed to read from Entity repositories (matching how `IdentityResolution` does today). Either is fine — but the current state is undocumented at the architecture-pattern level.

2. **Hard-coded `vector(1536)` in two places.** The schema (`db/schema/01_tables/enrichment.sql`) hard-codes `vector(1536)`, and `EntityEmbeddingEntity.embedding` uses `@Array(length = 1536)`. The `riven.enrichment.vector-dimensions` config property exists with a default of `1536` but is currently informational only — it is not connected to the schema or the entity. Changing models to one with different output dimensions silently breaks deserialization or insertion. Suggest extracting the literal to a single source of truth (likely a Kotlin `const`), or using a runtime check against `EmbeddingProvider.getDimensions()` at startup to fail fast if the configured model does not match the schema.

3. **`schemaVersion` is persisted but never read.** `EntityEmbeddingEntity.schemaVersion` defaults to 1 and is stored on every row, but no code path uses it. The Knowledge Layer plan (`feature-design/_Sub-Domain Plans/Knowledge Layer.md`) mentions a `ReBatchingWorkflowImpl` for re-embedding on schema drift; that work is not in this branch. Consider either adding the re-batching workflow as a planned phase or removing the field for now to avoid the appearance of a feature.

4. **`EntityEmbeddingRepository` has no similarity-search method.** The HNSW cosine index exists on `entity_embeddings.embedding`, but the repository only exposes `findByEntityId`, `findByWorkspaceId`, and `deleteByEntityId`. The query side is the actual reason this domain exists — leaving it absent makes the doc claim "produces vector embeddings for semantic search" technically aspirational. Add a `findSimilar(...)` method using pgvector's `<=>` operator (native SQL or `@Query`) before the next phase.

5. **Possible duplicate test factory.** The branch adds two files at `core/src/test/kotlin/riven/core/service/util/factory/EnrichmentFactory.kt` AND `core/src/test/kotlin/riven/core/service/util/factory/enrichment/EnrichmentFactory.kt`. One is likely a misplaced rename; deciding which to keep is a small cleanup but should happen before more tests reference whichever survives.

6. **Pattern: queue job that completes via Temporal.** `ENRICHMENT` is the first job type in `execution_queue` whose status flows `CLAIMED → COMPLETED` from inside the workflow itself. Earlier job types (`WORKFLOW_EXECUTION`, `IDENTITY_MATCH`) flow `CLAIMED → DISPATCHED` and have completion tracked elsewhere. Worth documenting this as a system pattern (`Workflow-completed queue jobs`) so the next contributor doesn't accidentally treat it as one-off.

7. **Knowledge domain identity needs adjustment.** `Knowledge.md` currently describes the domain as the "AI Data Knowledge Query Layer" with emphasis on a future query layer and "perspectives" (sub-agents). This branch ships the producer side (embeddings) but not the query / perspective side. The domain overview was updated to reflect the producer side only — verify the framing still reads correctly to the rest of the team.

---

## [2026-04-09] — Note Embedding Pipeline Documentation Updates

**Trigger:** Implemented note embedding pipeline with new cross-domain dependency (Integration → Note)
**Affected vault notes:** Integration Sync Pipeline, Note Domain, Data Flow diagrams
**Suggested update:** Document the note embedding routing in the sync pipeline flow (fetchAndProcessRecords now has a pre-check for noteEmbedding config that short-circuits entity creation). Document the entity-spanning notes architecture change. Update Note domain docs to reflect multi-entity attachment model and readonly/source tracking fields.

## [2026-03-27] — Entity Ingestion Pipeline Documentation Updates

**Trigger:** Engineering review defined the Entity Ingestion Pipeline architecture with 6 confirmed architectural decisions, 3 new services, and cross-domain dependencies.
**Affected vault notes:**

- `feature-design/1. Planning/Smart Projection Architecture.md` — updated with source wins, multi-source conflict resolution, ProjectionAcceptRule as List, backfill projection, audit trail
- `feature-design/1. Planning/Entity Ingestion Pipeline.md` — new feature design document
- `domains/Identity Resolution/Identity Resolution.md` — integration with ingestion pipeline
- `domains/Integrations/Integrations.md` — ingestion pipeline section
- `domains/Entities/Entities.md` — projected entities and hub model section
- `domains/Catalog/Catalog.md` — field mapping for ingestion section
  **Suggested update:** Review all updated documents for accuracy against current codebase state. The ingestion pipeline feature design is comprehensive but describes future architecture — verify implementation matches when built. The Smart Projection doc now references the Ingestion Pipeline doc — ensure both stay in sync.

---

## [2026-03-14] — Notification Domain: Future Extension Considerations

**Trigger:** Built notification domain with inbox, read-state, resolution lifecycle, and WebSocket delivery.
**Affected vault notes:** Notification domain documentation (new), System Patterns (notification lifecycle)
**Suggested update:** Document the following extension gaps identified during implementation:

1. **Expiry cleanup** — expired notifications are filtered from inbox queries but accumulate in the database. Consider a scheduled cleanup job or TTL-based purge.
2. **Batch notification creation** — `NotificationDeliveryService.createForUser` creates one notification per call. Workspace-wide user-targeted notifications (e.g., notifying all members) require N separate transactions. Consider a batch creation path.
3. **Forward cursor pagination** — inbox only supports backward pagination (`createdAt < cursor`). No mechanism to fetch "notifications newer than X" for real-time catch-up after reconnection.
4. **Notification updates** — notifications are immutable after creation. No update endpoint exists for correcting content or extending expiry.

## [2026-03-14] — WebSocket Infrastructure Documentation Needed

**Trigger:** Added WebSocket real-time notification infrastructure as a new cross-cutting domain.
**Affected vault notes:** System Patterns (new pattern: event-driven WebSocket notifications), Domain documentation (new websocket domain), Infrastructure (WebSocket broker topology)
**Suggested update:** Document the WebSocket event flow (service → ApplicationEvent → WebSocketEventListener → STOMP broker → client), the topic namespace structure (/topic/workspace/{id}/{channel}), the authentication model (JWT on CONNECT, workspace auth on SUBSCRIBE), and the broker migration path (SimpleBroker → external broker).

## 2026-03-14 — Integration Enablement: Domain Dependency Update

**Trigger:** Integration Enablement feature adds Integration → Entity domain dependency.
**Affected vault notes:** System Design/Domain Boundaries, System Design/Dependency Map
**Suggested update:** The Integration domain now directly depends on EntityTypeService for soft-delete/restore operations during disable/enable. This should be reflected in the domain dependency map. Previously, Entity was only accessed via the Catalog domain's materialization service.

## 2026-03-12 — Onboarding Domain Documentation Stub Needed

**Trigger:** Introduced new `onboarding` domain with cross-domain orchestration service
**Affected vault notes:** `System Design/` — no existing Onboarding section
**Suggested update:** Create `System Design/Onboarding/` vault section documenting the onboarding flow, atomicity guarantees (Phase 1 transactional, Phase 2 best-effort), the Internal method pattern for bypassing `@PreAuthorize`, and the `onboardingCompletedAt` idempotency gate.

## 2026-03-08 — Auto-Generated Identifier (`ID` SchemaType) and Template Default Value Support — ✅ IMPLEMENTED 2026-03-09

**Trigger:** The `time-entry` entity in the project-management manifest uses `"identifierKey": "description"` — a freetext field that is neither unique nor stable. Fixing this properly requires two pieces of infrastructure that do not yet exist.

**Affected vault notes:**

- `System Design/Entity Domain/` — entity creation flow, attribute types, schema options
- `System Design/System Patterns/` — schema validation pipeline, template installation

**Suggested update:**

### 1. New `ID` SchemaType (auto-generated identifier)

Add `ID` to the `SchemaType` enum as a first-class attribute type. Behavior:

- Auto-generates a prefixed sequential identifier on entity creation (e.g. `TE-00001`)
- Prefix defined in `SchemaOptions` (e.g. `options.prefix: "TE"`)
- Read-only after creation — users cannot edit the generated value
- Always unique within a workspace + entity type scope
- Generation strategy: query max existing value, increment, zero-pad
- Concurrency handling: retry on unique constraint violation or use a DB sequence

Manifest usage example:

```json
"reference-number": {
  "key": "ID",
  "label": "Reference",
  "type": "string",
  "options": { "prefix": "TE" }
}
```

This would allow `time-entry` (and any future entity type) to use a stable, unique, auto-generated identifier instead of relying on freetext fields.

### 2. Template default value support

`SchemaOptions.default` is currently hard-coded to `null` — defaults defined in manifests are never parsed or applied. Two gaps to close:

- **Parse defaults:** `TemplateInstallationService.parseSchemaOptions()` should read the `default` field from manifest attribute options and populate `SchemaOptions.default`.
- **Inject defaults:** `EntityService.saveEntity()` should inject attribute defaults before validation runs, so that attributes with configured defaults (e.g. a SELECT attribute defaulting to `"draft"`) are pre-populated on creation.

This applies to all attribute types, not just `ID`.

## 2026-03-17 — Identity Resolution Domain Documentation

**Trigger:** Created full Identity Resolution domain documentation (14 new docs, 7 updates to Workflows domain docs).
**Affected vault notes:** Domain Boundaries/Dependency Map, System Patterns (event-driven Temporal pipeline, pg_trgm similarity matching, canonical UUID ordering)
**Suggested update:** Review and author substantive architectural content for the Identity Resolution domain. The vault now has structural scaffolding and component docs, but the following warrant human review: (1) the cross-domain dependency from Identity → Entities via native SQL (bypasses normal service layer) should be reflected in the dependency map, (2) the "no @PreAuthorize" pattern for Temporal-called services (workspace isolation at query level) is a new security pattern worth documenting in System Patterns, (3) cluster entities are scaffolded but not functional — track when services are implemented.

## 2026-03-09 — Relationship Simplification May Affect System Design Docs

**Trigger:** Removed semantic group targeting, polymorphic toggle, and exclusion mechanism from entity relationships
**Affected vault notes:** Entity domain relationship documentation, any diagrams showing relationship targeting flow
**Suggested update:** Review and update relationship architecture documentation to reflect simplified model where all target rules are explicit (type ID based) and polymorphic behavior is system-only (CONNECTED_ENTITIES)
