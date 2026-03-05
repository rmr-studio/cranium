# Phase 3: Read Surface and Integration Tests - Context

**Gathered:** 2026-03-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Downstream services can query the manifest catalog via ManifestCatalogService, and the full startup pipeline is verified end-to-end by integration tests covering load, idempotent reload, and manifest removal reconciliation.

</domain>

<decisions>
## Implementation Decisions

### Query response shape
- New read-only domain models (not reusing pipeline `ResolvedManifest` data classes) — pipeline models carry `stale` flag and other persistence concerns that don't belong in query responses
- `getAvailableTemplates()` and `getAvailableModels()` return lightweight summary models (id, key, name, description, manifestVersion, entityTypeCount) — no deep hydration for list endpoints
- `getManifestByKey(key)` returns a fully hydrated model: manifest metadata + entity types (with schema, columns, semantic metadata) + relationships (with target rules) + field mappings
- `getEntityTypesForManifest(manifestId)` returns entity type models with schema, columns, and semantic metadata — no relationships or field mappings (those are manifest-level, not entity-type-level)
- Domain models use `toModel()` extension pattern on JPA entities, matching existing project convention

### Stale filtering
- Query methods always exclude stale entries (`stale = false`) — stale manifests are invisible to downstream consumers
- `getManifestByKey(key)` throws `NotFoundException` if the manifest is stale or doesn't exist — no distinction between "missing" and "stale" for consumers
- No optional stale filter parameter — simplicity over flexibility. The stale concept is loader-internal; query consumers should not need to reason about it
- Repository queries add `findByStalefalse` / `findByKeyAndStalefalse` derived queries

### Integration test scope
- True end-to-end: use actual `ManifestLoaderService.loadAllManifests()` with test fixture manifests from `src/test/resources/manifests/`
- Testcontainers PostgreSQL (real DB) — integration tests need JSONB support and real constraint enforcement that H2 can't fully replicate
- Follow existing integration test pattern: `@ActiveProfiles("integration")` with `@DynamicPropertySource` wiring
- Idempotent reload test: run `loadAllManifests()` twice, assert identical row counts and data
- Manifest removal reconciliation test: load fixtures, then remove a fixture file from the classpath resource path (or use a configurable manifest path), re-run loader, assert stale flag set
- Manifest removal simulation: use a test-specific manifest directory (temp dir) that can be manipulated between loader runs, injected via a test property override for the manifest base path

### Claude's Discretion
- Exact domain model field names and structure
- Whether to use a sealed class hierarchy or flat data classes for query responses
- Integration test helper methods and assertion patterns
- Whether ManifestCatalogService needs KLogger injection (likely yes for consistency)
- Repository query method naming

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ManifestCatalogRepository`: Already has `findByManifestType()`, `findByStaleTrue()`, `findByKeyAndManifestType()` — need to add stale-filtered variants
- `CatalogEntityTypeRepository`: Has `findByManifestId()` — ready for hydration queries
- `CatalogRelationshipRepository`: Has `findByManifestId()` — ready for hydration queries
- `CatalogRelationshipTargetRuleRepository`: Needs `findByCatalogRelationshipIdIn()` or similar for batch loading target rules
- `CatalogSemanticMetadataRepository`: Needs `findByCatalogEntityTypeIdIn()` for batch loading semantics
- `CatalogFieldMappingRepository`: Has `findByManifestId()` (needs verification) — ready for hydration
- Pipeline data classes (`ResolvedManifest`, `ResolvedEntityType`, etc.): Reference for field structure but NOT reused in query layer

### Established Patterns
- `toModel()` on JPA entities for entity-to-domain mapping (project-wide convention)
- `ServiceUtil.findOrThrow { }` for single-entity lookups that should throw `NotFoundException`
- Constructor injection with `KLogger` prototype bean
- `@PreAuthorize` not needed — catalog is global, no workspace scope
- No `@ControllerAdvice` concern — no controllers in this phase (service layer only)

### Integration Points
- `ManifestLoaderService.loadAllManifests()` — the pipeline being tested end-to-end
- `ManifestScannerService` — reads from classpath; integration tests need real fixture files
- All 6 catalog repositories — queried by ManifestCatalogService
- Integration test base class pattern with Testcontainers PostgreSQL

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches. User gave Claude full discretion on all areas.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 03-read-surface-and-integration-tests*
*Context gathered: 2026-03-05*
