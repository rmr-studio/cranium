---
phase: 03-read-surface-and-integration-tests
plan: 01
subsystem: api
tags: [spring-data, query-service, batch-hydration, catalog]

# Dependency graph
requires:
  - phase: 02-loader-pipeline
    provides: "6 catalog JPA entities, repositories, and pipeline data classes"
provides:
  - "ManifestCatalogService with 4 query methods for downstream consumers"
  - "ManifestQueryModels.kt with 7 read-only domain models"
  - "Batch repository methods for N+1-free hydration"
  - "toModel/toSummary/toDetail mappings on all 6 catalog entities"
affects: [03-02, controllers, downstream-services]

# Tech tracking
tech-stack:
  added: []
  patterns: [batch-hydration-via-groupBy, stale-filtered-derived-queries, separate-query-models]

key-files:
  created:
    - src/main/kotlin/riven/core/models/catalog/ManifestQueryModels.kt
    - src/main/kotlin/riven/core/service/catalog/ManifestCatalogService.kt
    - src/test/kotlin/riven/core/service/catalog/ManifestCatalogServiceTest.kt
  modified:
    - src/main/kotlin/riven/core/repository/catalog/ManifestCatalogRepository.kt
    - src/main/kotlin/riven/core/repository/catalog/CatalogSemanticMetadataRepository.kt
    - src/main/kotlin/riven/core/repository/catalog/CatalogRelationshipTargetRuleRepository.kt
    - src/main/kotlin/riven/core/entity/catalog/ManifestCatalogEntity.kt
    - src/main/kotlin/riven/core/entity/catalog/CatalogEntityTypeEntity.kt
    - src/main/kotlin/riven/core/entity/catalog/CatalogRelationshipEntity.kt
    - src/main/kotlin/riven/core/entity/catalog/CatalogRelationshipTargetRuleEntity.kt
    - src/main/kotlin/riven/core/entity/catalog/CatalogSemanticMetadataEntity.kt
    - src/main/kotlin/riven/core/entity/catalog/CatalogFieldMappingEntity.kt

key-decisions:
  - "Flat data classes for query models (not sealed hierarchy) -- simplest approach per CONTEXT.md discretion"
  - "findByKeyAndStaleFalse returns single entity (not list) -- keys expected practically unique across types"

patterns-established:
  - "Batch hydration: load child rows with findBy*IdIn, groupBy parent ID, then assemble in memory"
  - "Separate query models from pipeline models to keep persistence concerns out of API responses"

requirements-completed: [QUERY-01, QUERY-02, QUERY-03, QUERY-04]

# Metrics
duration: 4min
completed: 2026-03-05
---

# Phase 3 Plan 1: Read Surface Summary

**ManifestCatalogService with 4 stale-filtered query methods, 7 read-only domain models, and batch hydration for N+1-free manifest detail retrieval**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-05T08:33:32Z
- **Completed:** 2026-03-05T08:37:41Z
- **Tasks:** 2
- **Files modified:** 13

## Accomplishments
- Created ManifestQueryModels.kt with 7 data classes separate from pipeline models
- Implemented ManifestCatalogService with getAvailableTemplates, getAvailableModels, getManifestByKey, getEntityTypesForManifest
- Added stale-filtered repository queries (findByManifestTypeAndStaleFalse, findByKeyAndStaleFalse)
- Added batch repository queries (findByCatalogEntityTypeIdIn, findByCatalogRelationshipIdIn) for N+1-free hydration
- Added toModel/toSummary/toDetail mappings to all 6 catalog entity classes
- 8 unit tests covering all query methods with batch loading verification

## Task Commits

Each task was committed atomically:

1. **Task 1: Query models, repository additions, and toModel() extensions** - `6e287d27f` (feat)
2. **Task 2: ManifestCatalogService and unit tests** - `bca133dff` (feat)

## Files Created/Modified
- `src/main/kotlin/riven/core/models/catalog/ManifestQueryModels.kt` - 7 read-only domain models for query responses
- `src/main/kotlin/riven/core/service/catalog/ManifestCatalogService.kt` - Query service with 4 public methods and batch hydration helpers
- `src/test/kotlin/riven/core/service/catalog/ManifestCatalogServiceTest.kt` - 8 unit tests for all query methods
- `src/main/kotlin/riven/core/repository/catalog/ManifestCatalogRepository.kt` - Added stale-filtered query methods
- `src/main/kotlin/riven/core/repository/catalog/CatalogSemanticMetadataRepository.kt` - Added batch loading method
- `src/main/kotlin/riven/core/repository/catalog/CatalogRelationshipTargetRuleRepository.kt` - Added batch loading method
- `src/main/kotlin/riven/core/entity/catalog/ManifestCatalogEntity.kt` - Added toSummary() and toDetail()
- `src/main/kotlin/riven/core/entity/catalog/CatalogEntityTypeEntity.kt` - Added toModel()
- `src/main/kotlin/riven/core/entity/catalog/CatalogRelationshipEntity.kt` - Added toModel()
- `src/main/kotlin/riven/core/entity/catalog/CatalogRelationshipTargetRuleEntity.kt` - Added toModel()
- `src/main/kotlin/riven/core/entity/catalog/CatalogSemanticMetadataEntity.kt` - Added toModel()
- `src/main/kotlin/riven/core/entity/catalog/CatalogFieldMappingEntity.kt` - Added toModel()

## Decisions Made
- Used flat data classes (not sealed hierarchy) for query models -- simplest approach, no polymorphic dispatch needed
- findByKeyAndStaleFalse returns single entity rather than list -- keys are practically unique across manifest types
- Test class uses manual mock() setup matching ManifestUpsertServiceTest pattern rather than @SpringBootTest

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed incorrect enum values in test data**
- **Found during:** Task 2 (unit tests)
- **Issue:** Test used SemanticGroup.CORE and SemanticMetadataTargetType.ENTITY which don't exist in the enum definitions
- **Fix:** Changed to SemanticGroup.CUSTOMER and SemanticMetadataTargetType.ENTITY_TYPE
- **Files modified:** ManifestCatalogServiceTest.kt
- **Verification:** Tests compile and pass
- **Committed in:** bca133dff (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Minor enum name correction in test data. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- ManifestCatalogService is ready for downstream consumers
- Integration tests (03-02) can use ManifestCatalogService to verify end-to-end pipeline

---
*Phase: 03-read-surface-and-integration-tests*
*Completed: 2026-03-05*
