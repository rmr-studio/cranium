# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-28)

**Core value:** Manifest files on disk are reliably loaded into a queryable database catalog on every application startup
**Current focus:** Phase 2 — Loader Pipeline

## Current Position

Phase: 2 of 3 (Loader Pipeline) -- COMPLETE
Plan: 3 of 3 in current phase (complete)
Status: Phase 2 complete, all 3 plans executed
Last activity: 2026-03-05 — Phase 2 Plan 3 executed

Progress: [##########] 100%

## Performance Metrics

**Velocity:**
- Total plans completed: 4
- Average duration: 6.0 minutes
- Total execution time: 0.40 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 1. Database Foundation | 1/1 | 8 min | 8 min |
| 2. Loader Pipeline | 3/3 | 16 min | 5.3 min |

**Recent Trend:**
- Last 5 plans: 8 min, 3 min, 8 min, 5 min
- Trend: Stable

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Pipeline data classes use Map<String, Any> for schema/mappings JSONB passthrough rather than typed structures
- Test fixtures placed in src/test/resources/manifests/ matching production directory structure
- Strip $id and $schema from JSON Schema files before loading into networknt 1.0.83 to avoid URI resolution errors
- Integration entity types default readonly=true via readonlyDefault parameter
- Full format relationships without explicit cardinality default to ONE_TO_MANY
- Schema: Catalog tables are global (no workspace_id, no RLS) — follows IntegrationDefinitionEntity precedent
- Schema: UNIQUE(key, manifest_type) composite constraint on manifest_catalog — prevents cross-type key collision
- Schema: No deleted/deleted_at/active columns on manifest_catalog — catalog entries are permanent (CONTEXT.md)
- Schema: integration_definitions.active renamed to stale with inverted default (false) — staleness tracking for missing integration manifests
- Loading: Explicit listOf("models", "templates", "integrations") load order — cannot rely on filesystem ordering in Docker
- Transactions: @Transactional on ManifestUpsertService.upsertManifest() and markAllStale() — not on the ApplicationReadyEvent listener
- Stale sync: syncIntegrationDefinitionsStale() has its own @Transactional boundary within ManifestLoaderService
- Entity pattern: CatalogRelationshipEntity.protected uses backtick-escaped Kotlin keyword matching existing RelationshipDefinitionEntity pattern
- JSON Schema: Draft 2019-09 matching existing SchemaService SpecVersion.VersionFlag.V201909
- Semantics: Relationship semantics have definition + tags but no classification per CONTEXT.md

### Pending Todos

None.

### Blockers/Concerns

- Manifest file location resolved: src/main/resources/manifests/ (classpath) confirmed in Phase 1
- entity_types column additions deferred to clone service phase (v2) per CONTEXT.md

## Session Continuity

Last session: 2026-03-05
Stopped at: Completed 02-03-PLAN.md — Upsert service and loader orchestrator with 11 unit tests. Phase 2 complete.
Resume file: None
