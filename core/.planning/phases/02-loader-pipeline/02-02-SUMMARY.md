---
phase: 02-loader-pipeline
plan: 02
subsystem: api
tags: [json-schema, classpath-scanning, ref-resolution, extend-merge, relationship-normalization, networknt]

# Dependency graph
requires:
  - phase: 02-loader-pipeline
    plan: 01
    provides: Pipeline data classes (ScannedManifest, ResolvedManifest), JSON Schema files, test fixtures
provides:
  - ManifestScannerService for classpath scanning and JSON Schema validation
  - ManifestResolverService for $ref resolution, extend merge, relationship normalization/validation
  - Field mapping validation with attribute key checking
  - 24 unit tests covering all scanner and resolver behaviors
affects: [02-03-PLAN]

# Tech tracking
tech-stack:
  added: []
  patterns: [classpath-resource-scanning, json-schema-id-stripping, in-memory-model-index, shorthand-to-full-normalization]

key-files:
  created:
    - src/main/kotlin/riven/core/service/catalog/ManifestScannerService.kt
    - src/main/kotlin/riven/core/service/catalog/ManifestResolverService.kt
    - src/test/kotlin/riven/core/service/catalog/ManifestScannerServiceTest.kt
    - src/test/kotlin/riven/core/service/catalog/ManifestResolverServiceTest.kt
  modified: []

key-decisions:
  - "Strip $id and $schema from JSON Schema files before loading into networknt 1.0.83 to avoid URI resolution errors with relative $id values"
  - "Integration entity types default readonly=true via readonlyDefault parameter in parseEntityType"
  - "Full format relationships without explicit cardinality default to ONE_TO_MANY"

patterns-established:
  - "Scanner: ResourcePatternResolver.getResources for classpath scanning with inputStream (never getFile)"
  - "Resolver: Pure in-memory transformation with no repository dependencies"
  - "Extend merge: base attributes win on key conflict, scalar overrides applied, semantic tags appended"

requirements-completed: [VAL-02, VAL-03, LOAD-03, LOAD-04, LOAD-05, LOAD-06, LOAD-07, TEST-01, TEST-02, TEST-03, TEST-04]

# Metrics
duration: 8min
completed: 2026-03-05
---

# Phase 2 Plan 02: Scanner and Resolver Services Summary

**ManifestScannerService with classpath scanning + JSON Schema validation, ManifestResolverService with $ref resolution, extend merge, relationship normalization/validation, and 24 unit tests**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-05T07:07:01Z
- **Completed:** 2026-03-05T07:15:50Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- ManifestScannerService scans three classpath directories (models flat files, templates/integrations directory-based), validates each against its JSON Schema, skips invalid manifests with WARN logging
- ManifestResolverService resolves $ref from in-memory model index, applies extend merge with base-wins-on-conflict semantics, normalizes relationship shorthand to full targetRules[] format, validates all relationship source/target keys, validates field mapping attribute keys
- 24 unit tests covering all scanner and resolver behaviors including edge cases (missing ref, mutual exclusivity, duplicate keys, invalid attributes)
- Both services are stateless and DB-free -- pure transformation logic following the pipeline pattern

## Task Commits

Each task was committed atomically:

1. **Task 1: ManifestScannerService + ManifestScannerServiceTest** - `4c9f3b494` (test: RED), `f322cbc8b` (feat: GREEN)
2. **Task 2: ManifestResolverService + ManifestResolverServiceTest** - `0566b8684` (test: RED), `0fbea21ed` (feat: GREEN)

## Files Created/Modified
- `src/main/kotlin/riven/core/service/catalog/ManifestScannerService.kt` - Classpath scanning + JSON Schema validation for models, templates, integrations
- `src/main/kotlin/riven/core/service/catalog/ManifestResolverService.kt` - $ref resolution, extend merge, relationship normalization/validation, field mapping validation
- `src/test/kotlin/riven/core/service/catalog/ManifestScannerServiceTest.kt` - 6 unit tests for scanner (valid, invalid, unparseable, empty, directory extraction)
- `src/test/kotlin/riven/core/service/catalog/ManifestResolverServiceTest.kt` - 18 unit tests for resolver (ref, extend, relationships, protected defaults, field mappings)

## Decisions Made
- Strip `$id` and `$schema` from JSON Schema files before loading into networknt 1.0.83 validator -- relative `$id` values like `"model.schema.json"` cause URI resolution failures. The existing `SchemaService` doesn't hit this because it generates schemas in-memory without `$id` fields.
- Integration entity types use `readonlyDefault = true` passed to `parseEntityType`, meaning integration entity types default to readonly unless explicitly set to false in the manifest.
- Full format relationships (targetRules without explicit cardinality) default to `ONE_TO_MANY` as the cardinalityDefault.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed JSON Schema $id URI resolution error**
- **Found during:** Task 1 (ManifestScannerService)
- **Issue:** networknt 1.0.83 threw `null is an invalid segment for URI {2}` when loading schemas with relative `$id` fields
- **Fix:** Strip `$id` and `$schema` fields from schema JSON before creating the validator
- **Files modified:** ManifestScannerService.kt (loadSchema method)
- **Verification:** All scanner tests pass
- **Committed in:** f322cbc8b

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Necessary for correctness. No scope creep.

## Issues Encountered
None beyond the $id URI resolution issue documented above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Scanner and resolver services ready for ManifestLoaderService orchestrator (02-03-PLAN)
- ManifestLoaderService will use scanModels/scanTemplates/scanIntegrations to get ScannedManifest list
- ManifestLoaderService will build model index from scanned models, then call resolveManifest for each template/integration
- ManifestUpsertService will persist ResolvedManifest to catalog tables

---
*Phase: 02-loader-pipeline*
*Completed: 2026-03-05*
