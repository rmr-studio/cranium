# TODOS

## P2 — Frontend: Source Attribution UI
**What:** Integration-sourced notes should show a source badge (e.g., HubSpot icon), lock icon for readonly, and disabled edit/delete buttons.
**Why:** Users currently can't distinguish integration notes from user-created notes. Without visual cues, they'll try to edit readonly notes and hit a 403.
**Pros:** Clear UX for mixed note sources, prevents confusion.
**Cons:** Requires integration icon assets and conditional rendering in note components.
**Context:** NoteEntity now has `sourceType` (USER/INTEGRATION), `sourceIntegrationId`, and `readonly` fields. API returns these in the Note model. Frontend needs to consume them.
**Depends on:** Note embedding pipeline shipping (backend).

## P2 — Frontend: Multi-Entity Note Context Display
**What:** Workspace notes list should show all attached entities for entity-spanning notes, not just one.
**Why:** With the join table migration, notes can be attached to multiple entities. The current WorkspaceNote enrichment returns entity context but the frontend breadcrumb only shows one entity.
**Pros:** Full context for notes attached to contact + deal + ticket.
**Cons:** UI needs to handle variable-length entity lists gracefully (2-5 entities per note).
**Context:** Note model now returns `entityIds: List<UUID>` instead of `entityId: UUID`. WorkspaceNote enrichment updated for multi-entity context.
**Depends on:** Entity-spanning notes migration shipping (backend).

## P3 — Performance: EXPLAIN ANALYZE Join Table Queries
**What:** Run EXPLAIN ANALYZE on entity-scoped note queries after join table migration to verify index usage and query cost.
**Why:** All entity-scoped queries now JOIN through note_entity_attachments instead of direct WHERE entity_id. Need to confirm the idx_note_attachments_entity index is used.
**Pros:** Catches performance regressions early.
**Cons:** One-time task, low effort.
**Context:** Queries affected: findByEntityIdAndWorkspaceId, searchByEntityIdAndWorkspaceId, findEntityContext. Index on note_entity_attachments(entity_id) should make this fast.
**Depends on:** Entity-spanning notes migration deployed to a database with realistic data volume.

## P2 — Unify IntegrationSyncWorkflow via IngestionOrchestrator
**What:** Migrate IntegrationSyncActivitiesImpl (801 lines) to delegate record-processing to IngestionOrchestrator through NangoAdapter. Eliminates split path between custom-source and Nango pipelines.
**Why:** Two-layer data plan ships NangoAdapter as thin wrapper but never wires it. Leaves parallel scaffolding that drifts over time.
**Pros:** Single record-processing pipeline; one place to fix bugs in mapping/upsert/resolution/projection.
**Cons:** Touches hot path used by all existing integrations; needs careful staged rollout (feature flag + shadow mode).
**Context:** See `.planning/` two-layer data model. IngestionOrchestrator introduced for CustomSourceSyncWorkflow but Nango pipeline stays as-is. NangoAdapter created in plan specifically to enable this future migration.
**Depends on:** Custom source Postgres adapter shipped and validated with Mac.

## P2 — Schema Drift Detection for Custom Sources
**What:** Detect and surface user DB schema changes (added/removed/renamed columns) between syncs.
**Why:** Two-layer plan Open Question #2 defers to "next introspection surfaces unmapped column, user decides." No proactive detection of removed or renamed columns.
**Pros:** User sees schema changes before they break sync; prevents silent data loss when a source column disappears.
**Cons:** Needs periodic introspection separate from record sync; UX for "3 columns removed, remap?" is non-trivial.
**Context:** Existing schema-reconciliation pattern (project_schema_reconciliation.md) applies to workspace entity types. Custom source drift is similar but origin is external DB, not core model changes. CustomSourceSchemaInferenceService already introspects; extend to diff against stored SourceSchema.
**Depends on:** PostgresAdapter + SchemaInferenceService shipped.
