# Phase 3: Read Surface and Integration Tests - Research

**Researched:** 2026-03-05
**Domain:** Query service layer + Testcontainers integration testing
**Confidence:** HIGH

## Summary

Phase 3 has two distinct workstreams: (1) a read-only `ManifestCatalogService` that queries 6 existing catalog repositories to serve downstream consumers, and (2) integration tests that exercise the full loader pipeline against a real PostgreSQL database via Testcontainers.

The query service is straightforward -- all repositories exist, all entity classes exist, and the project has well-established patterns for `toModel()` mapping, `ServiceUtil.findOrThrow`, and constructor injection. The main design challenge is efficient hydration for `getManifestByKey()` which needs to batch-load across 6 tables. New read-only domain models are needed (decided in CONTEXT.md) since pipeline `ResolvedManifest` carries `stale` and other persistence concerns.

The integration tests follow the established `EntityQueryIntegrationTestBase` pattern: Testcontainers PostgreSQL with `pgvector/pgvector:pg16`, `@ActiveProfiles("integration")`, `@DynamicPropertySource`, and `ddl-auto: create-drop`. The key challenge is simulating manifest removal for reconciliation tests -- CONTEXT.md decided on a test-specific temp directory with property override for the manifest base path.

**Primary recommendation:** Build ManifestCatalogService with flat data class query models and `toModel()` extensions on JPA entities, then write integration tests that wire the full pipeline (Scanner -> Resolver -> Upserter) against Testcontainers PostgreSQL with manipulable temp-directory fixtures.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- New read-only domain models (not reusing pipeline `ResolvedManifest` data classes) -- pipeline models carry `stale` flag and other persistence concerns that don't belong in query responses
- `getAvailableTemplates()` and `getAvailableModels()` return lightweight summary models (id, key, name, description, manifestVersion, entityTypeCount) -- no deep hydration for list endpoints
- `getManifestByKey(key)` returns a fully hydrated model: manifest metadata + entity types (with schema, columns, semantic metadata) + relationships (with target rules) + field mappings
- `getEntityTypesForManifest(manifestId)` returns entity type models with schema, columns, and semantic metadata -- no relationships or field mappings
- Domain models use `toModel()` extension pattern on JPA entities
- Query methods always exclude stale entries (`stale = false`)
- `getManifestByKey(key)` throws `NotFoundException` if the manifest is stale or doesn't exist
- No optional stale filter parameter
- Repository queries add `findByStaleFalse` / `findByKeyAndStaleFalse` derived queries
- True end-to-end integration tests using actual `ManifestLoaderService.loadAllManifests()` with test fixture manifests
- Testcontainers PostgreSQL (real DB) for integration tests
- Follow existing `@ActiveProfiles("integration")` with `@DynamicPropertySource` pattern
- Idempotent reload test: run `loadAllManifests()` twice, assert identical row counts
- Manifest removal simulation: test-specific manifest directory (temp dir) manipulated between loader runs via property override

### Claude's Discretion
- Exact domain model field names and structure
- Whether to use sealed class hierarchy or flat data classes for query responses
- Integration test helper methods and assertion patterns
- Whether ManifestCatalogService needs KLogger injection (likely yes for consistency)
- Repository query method naming

### Deferred Ideas (OUT OF SCOPE)
None
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| QUERY-01 | `ManifestCatalogService.getAvailableTemplates()` returning all active template manifests | New `findByManifestTypeAndStaleFalse()` repository method + lightweight summary model with `toModel()` on entity |
| QUERY-02 | `ManifestCatalogService.getAvailableModels()` returning all active model manifests | Same pattern as QUERY-01 with `ManifestType.MODEL` |
| QUERY-03 | `ManifestCatalogService.getManifestByKey(key)` returning single manifest with nested entity types and relationships | `findByKeyAndStaleFalse()` + batch hydration from 5 child repositories + hydrated domain model |
| QUERY-04 | `ManifestCatalogService.getEntityTypesForManifest(manifestId)` returning entity type definitions | `CatalogEntityTypeRepository.findByManifestId()` + semantic metadata batch load + entity type domain model |
| TEST-06 | Integration tests verify full startup load cycle | Testcontainers PostgreSQL + temp fixture directory + ManifestLoaderService.loadAllManifests() + repository count assertions |
| TEST-07 | Integration tests verify idempotent reload | Run loadAllManifests() twice, assert identical counts across all 6 tables |
| TEST-08 | Integration tests verify manifest removal reconciliation | Remove fixture file between runs, re-run loader, assert stale=true on removed manifest |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Data JPA | 3.5.x | Repository queries | Already in use, all repositories exist |
| Testcontainers PostgreSQL | (existing) | Integration test DB | Established pattern in EntityQueryIntegrationTestBase |
| JUnit 5 | (existing) | Test framework | Project standard |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| pgvector/pgvector:pg16 | Docker image | Testcontainers image | PostgreSQL with extensions matching production |
| mockito-kotlin | (existing) | Mocking (if needed) | Only for unit tests of ManifestCatalogService |

No new dependencies required. Everything needed is already in the project.

## Architecture Patterns

### Recommended Package Structure
```
src/main/kotlin/riven/core/
├── service/catalog/
│   ├── ManifestCatalogService.kt       # NEW - query service
│   ├── ManifestLoaderService.kt        # EXISTING
│   ├── ManifestScannerService.kt       # EXISTING
│   ├── ManifestResolverService.kt      # EXISTING
│   └── ManifestUpsertService.kt        # EXISTING
├── models/catalog/
│   ├── ManifestPipelineModels.kt       # EXISTING
│   └── ManifestQueryModels.kt          # NEW - read-only domain models
├── repository/catalog/
│   └── ManifestCatalogRepository.kt    # MODIFIED - add stale-filtered queries
└── entity/catalog/
    └── *.kt                            # MODIFIED - add toModel() extensions

src/test/kotlin/riven/core/
├── service/catalog/
│   ├── ManifestCatalogServiceTest.kt   # NEW - unit tests (optional)
│   └── ManifestLoaderIntegrationTest.kt # NEW - integration tests
```

### Pattern 1: Read-Only Query Models (Flat Data Classes)
**What:** Separate data classes for query responses, not reusing pipeline models
**When to use:** Query responses should exclude internal fields like `stale`, `createdAt`, `updatedAt`
**Example:**
```kotlin
// In models/catalog/ManifestQueryModels.kt

/** Lightweight summary for list endpoints */
data class ManifestSummary(
    val id: UUID,
    val key: String,
    val name: String,
    val description: String?,
    val manifestVersion: String?,
    val entityTypeCount: Int
)

/** Fully hydrated manifest for detail endpoint */
data class ManifestDetail(
    val id: UUID,
    val key: String,
    val name: String,
    val description: String?,
    val manifestType: ManifestType,
    val manifestVersion: String?,
    val entityTypes: List<CatalogEntityTypeModel>,
    val relationships: List<CatalogRelationshipModel>,
    val fieldMappings: List<CatalogFieldMappingModel>
)

/** Entity type with schema and semantic metadata */
data class CatalogEntityTypeModel(
    val id: UUID,
    val key: String,
    val displayNameSingular: String,
    val displayNamePlural: String,
    val iconType: IconType,
    val iconColour: IconColour,
    val semanticGroup: SemanticGroup,
    val identifierKey: String?,
    val readonly: Boolean,
    val schema: Map<String, Any>,
    val columns: List<Map<String, Any>>?,
    val semanticMetadata: List<CatalogSemanticMetadataModel>
)
```

### Pattern 2: toModel() on JPA Entities
**What:** Extension or member function on entity classes for domain mapping
**When to use:** All entity-to-model conversions per project convention
**Example:**
```kotlin
// On ManifestCatalogEntity:
fun toSummary(entityTypeCount: Int) = ManifestSummary(
    id = id!!,
    key = key,
    name = name,
    description = description,
    manifestVersion = manifestVersion,
    entityTypeCount = entityTypeCount
)
```

### Pattern 3: Batch Hydration for Deep Queries
**What:** Load child rows in batch by parent ID, then associate in memory
**When to use:** `getManifestByKey()` which needs entity types + relationships + target rules + semantics + field mappings
**Example:**
```kotlin
// In ManifestCatalogService:
fun getManifestByKey(key: String): ManifestDetail {
    val catalog = manifestCatalogRepository.findByKeyAndStaleFalse(key)
        ?: throw NotFoundException("Manifest not found: $key")
    val manifestId = catalog.id!!

    val entityTypes = catalogEntityTypeRepository.findByManifestId(manifestId)
    val entityTypeIds = entityTypes.mapNotNull { it.id }

    // Batch load child data
    val semanticsByEntityType = catalogSemanticMetadataRepository
        .findByCatalogEntityTypeIdIn(entityTypeIds)
        .groupBy { it.catalogEntityTypeId }

    val relationships = catalogRelationshipRepository.findByManifestId(manifestId)
    val relationshipIds = relationships.mapNotNull { it.id }
    val targetRulesByRelationship = catalogRelationshipTargetRuleRepository
        .findByCatalogRelationshipIdIn(relationshipIds)
        .groupBy { it.catalogRelationshipId }

    val fieldMappings = catalogFieldMappingRepository.findByManifestId(manifestId)

    // Assemble with toModel() calls
    return catalog.toDetail(
        entityTypes = entityTypes.map { it.toModel(semanticsByEntityType[it.id] ?: emptyList()) },
        relationships = relationships.map { it.toModel(targetRulesByRelationship[it.id] ?: emptyList()) },
        fieldMappings = fieldMappings.map { it.toModel() }
    )
}
```

### Pattern 4: Integration Test with Temp Fixture Directory
**What:** Copy test fixtures to a temp dir, configure the scanner to read from there, manipulate files between runs
**When to use:** Manifest removal reconciliation test (TEST-08)
**Example:**
```kotlin
// The ManifestScannerService reads from classpath:manifests/ via ResourcePatternResolver
// For integration tests, we need filesystem-based fixtures that can be added/removed

// Approach: Use a FileSystemResourceLoader or override the resource pattern
// OR: inject a configurable base path property into ManifestScannerService

// Simplest approach: Use @TestPropertySource or @DynamicPropertySource to point
// to a temp directory, and modify ManifestScannerService to accept configurable paths
```

### Anti-Patterns to Avoid
- **N+1 queries in hydration:** Do NOT load semantic metadata per entity type in a loop. Use `findByCatalogEntityTypeIdIn()` batch query.
- **Reusing pipeline models for queries:** CONTEXT.md explicitly decided against this. Pipeline models have `stale` and are mutable concerns.
- **Filtering stale in application code:** Use repository-level `findByStaleFalse()` queries, not post-fetch filtering.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Stale filtering | Application-level filter | Spring Data derived query `findByStaleFalse` | Pushes filter to DB, simpler |
| Entity-to-model mapping | Separate mapper class | `toModel()` on entity | Project convention |
| Not-found handling | Custom if-null-throw | `?: throw NotFoundException(...)` | Matches `ServiceUtil.findOrThrow` idiom |
| Test container setup | Custom Docker management | Testcontainers singleton pattern | Established in project |

## Common Pitfalls

### Pitfall 1: ManifestScannerService Hardcoded Classpath Paths
**What goes wrong:** The scanner uses `classpath:manifests/models/*.json` etc. For reconciliation tests (TEST-08), you need to remove a manifest file and re-run the loader. Classpath resources can't be deleted at runtime.
**Why it happens:** The scanner was designed for production startup, not test manipulation.
**How to avoid:** Make the scanner's base path configurable via a property (e.g., `riven.manifests.base-path`). Default to `classpath:manifests/` for production. In integration tests, use `@DynamicPropertySource` to point to a temp directory on the filesystem. The scanner needs minor refactoring to support both `classpath:` and `file:` resource prefixes.
**Warning signs:** Tests that try to manipulate `src/test/resources` files directly will be fragile and may affect other tests.

### Pitfall 2: Missing Batch Repository Methods
**What goes wrong:** `CatalogSemanticMetadataRepository` lacks `findByCatalogEntityTypeIdIn()` and `CatalogRelationshipTargetRuleRepository` lacks `findByCatalogRelationshipIdIn()` for batch loading.
**Why it happens:** These repositories were designed for the delete-reinsert pipeline, not batch reads.
**How to avoid:** Add `findByCatalogEntityTypeIdIn(ids: List<UUID>)` and `findByCatalogRelationshipIdIn(ids: List<UUID>)` derived queries before implementing the catalog service.
**Warning signs:** N+1 query patterns in the service layer.

### Pitfall 3: Integration Test Schema Generation with JSONB
**What goes wrong:** `ddl-auto: create-drop` with Hibernate may not create JSONB columns correctly for PostgreSQL without proper type annotations.
**Why it happens:** Hypersistence `JsonBinaryType` needs the `columnDefinition = "jsonb"` annotation on entities (already present).
**How to avoid:** The existing entities already have `@Type(JsonBinaryType::class)` and `columnDefinition = "jsonb"`. The `application-integration.yml` already uses `org.hibernate.dialect.PostgreSQLDialect`. This should work out of the box, but verify during the first test run.
**Warning signs:** Errors about unknown column types during schema creation.

### Pitfall 4: Test Fixture Classpath vs Filesystem Confusion
**What goes wrong:** Test fixtures in `src/test/resources/manifests/` are available on classpath. Integration tests that manipulate fixtures need filesystem access, not classpath access.
**Why it happens:** Two different resource resolution mechanisms.
**How to avoid:** For idempotent reload (TEST-07), classpath fixtures are fine -- no manipulation needed. For reconciliation (TEST-08), copy fixtures to a temp dir and configure the scanner to read from `file:${tempDir}/manifests/`. Clearly separate the two test scenarios.
**Warning signs:** Tests that pass locally but fail in CI because of classpath resource caching.

### Pitfall 5: ManifestCatalogEntity.findByKeyAndStaleFalse Not Scoped by ManifestType
**What goes wrong:** `findByKeyAndStaleFalse(key)` could match multiple manifests if a model and template share the same key (allowed by the UNIQUE(key, manifest_type) constraint).
**Why it happens:** Keys are unique per type, not globally.
**How to avoid:** Either (a) `getManifestByKey()` should also accept a `ManifestType` parameter, or (b) the query should return a list and the service should handle ambiguity. Given CONTEXT.md says "single manifest", option (a) is cleaner: `findByKeyAndManifestTypeAndStaleFalse(key, type)`. Alternatively, if keys are expected to be globally unique in practice, document this assumption.
**Warning signs:** Unexpected results when a model and template share a key.

## Code Examples

### Repository Additions Needed

```kotlin
// ManifestCatalogRepository additions:
fun findByManifestTypeAndStaleFalse(manifestType: ManifestType): List<ManifestCatalogEntity>
fun findByKeyAndStaleFalse(key: String): ManifestCatalogEntity?

// CatalogSemanticMetadataRepository addition:
fun findByCatalogEntityTypeIdIn(catalogEntityTypeIds: List<UUID>): List<CatalogSemanticMetadataEntity>

// CatalogRelationshipTargetRuleRepository addition:
fun findByCatalogRelationshipIdIn(catalogRelationshipIds: List<UUID>): List<CatalogRelationshipTargetRuleEntity>
```

### ManifestCatalogService Structure

```kotlin
@Service
class ManifestCatalogService(
    private val manifestCatalogRepository: ManifestCatalogRepository,
    private val catalogEntityTypeRepository: CatalogEntityTypeRepository,
    private val catalogRelationshipRepository: CatalogRelationshipRepository,
    private val catalogRelationshipTargetRuleRepository: CatalogRelationshipTargetRuleRepository,
    private val catalogSemanticMetadataRepository: CatalogSemanticMetadataRepository,
    private val catalogFieldMappingRepository: CatalogFieldMappingRepository,
    private val logger: KLogger
) {
    // No @PreAuthorize -- catalog is global, no workspace scope

    fun getAvailableTemplates(): List<ManifestSummary> { ... }
    fun getAvailableModels(): List<ManifestSummary> { ... }
    fun getManifestByKey(key: String): ManifestDetail { ... }
    fun getEntityTypesForManifest(manifestId: UUID): List<CatalogEntityTypeModel> { ... }
}
```

### Integration Test Base Structure

```kotlin
@SpringBootTest(
    classes = [ManifestLoaderIntegrationTestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("integration")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ManifestLoaderIntegrationTest {

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
        ).withDatabaseName("riven_test")
         .withUsername("test")
         .withPassword("test")

        init { postgres.start() }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    // Inject all catalog repositories + ManifestLoaderService + ManifestCatalogService
}
```

### Integration Test Config Class Pattern

```kotlin
@Configuration
@EnableAutoConfiguration(
    exclude = [
        SecurityAutoConfiguration::class,
        UserDetailsServiceAutoConfiguration::class,
        OAuth2ResourceServerAutoConfiguration::class,
    ],
    excludeName = [
        "io.temporal.spring.boot.autoconfigure.ServiceStubsAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.RootNamespaceAutoConfiguration",
        // ... same Temporal exclusions as EntityQueryIntegrationTestBase
    ],
)
@EnableJpaRepositories(basePackages = ["riven.core.repository.catalog", "riven.core.repository.integration"])
@EntityScan("riven.core.entity")
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "dateTimeProvider")
class ManifestLoaderIntegrationTestConfig {
    // Beans for scanner, resolver, upserter, loader, catalog service
    // Mock IntegrationDefinitionRepository or include it
}
```

### Scanner Refactoring for Configurable Base Path

```kotlin
// Add property to ManifestScannerService:
@Service
class ManifestScannerService(
    private val resourcePatternResolver: ResourcePatternResolver,
    private val objectMapper: ObjectMapper,
    private val logger: KLogger,
    @Value("\${riven.manifests.base-path:classpath:manifests}")
    private val basePath: String
) {
    fun scanModels(): List<ScannedManifest> {
        val resources = resourcePatternResolver.getResources("$basePath/models/*.json")
        // ... rest unchanged
    }
    // Similar for scanTemplates() and scanIntegrations()
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| N/A (greenfield) | Spring Data derived queries for stale filtering | Current | Simple, no JPQL needed |
| N/A (greenfield) | Testcontainers singleton pattern | Current | Shared container across test classes |

## Open Questions

1. **Key uniqueness across manifest types**
   - What we know: DB constraint is `UNIQUE(key, manifest_type)`, allowing same key across types
   - What's unclear: Should `getManifestByKey(key)` require a type parameter, or are keys practically unique?
   - Recommendation: Accept just `key` for simplicity (per CONTEXT.md decision), but the implementation should handle the edge case by throwing if multiple non-stale results match. The `findByKeyAndStaleFalse` query may return a list; take the single result or throw `ConflictException`.

2. **ManifestScannerService refactoring scope**
   - What we know: Scanner uses hardcoded `classpath:manifests/` prefix. TEST-08 needs manipulable fixtures.
   - What's unclear: How much refactoring is acceptable for the scanner vs. test workarounds?
   - Recommendation: Add a `@Value` property for the base path (minimal change, 3 lines). This is backward-compatible and production-safe with the default value.

3. **EntityTypeCount for summary models**
   - What we know: CONTEXT.md says summaries include `entityTypeCount`
   - What's unclear: Count via a separate query or via `@Formula`/derived query?
   - Recommendation: Use `catalogEntityTypeRepository.countByManifestId(manifestId)` or batch-load counts with a JPQL `SELECT c.manifestId, COUNT(c) FROM CatalogEntityTypeEntity c WHERE c.manifestId IN :ids GROUP BY c.manifestId`. For small catalogs, loading entity types per manifest and counting in memory is acceptable.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Testcontainers |
| Config file | `src/test/resources/application-integration.yml` |
| Quick run command | `./gradlew test --tests "*ManifestCatalogServiceTest*"` |
| Full suite command | `./gradlew test` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| QUERY-01 | getAvailableTemplates returns active templates | unit | `./gradlew test --tests "*ManifestCatalogServiceTest*getAvailableTemplates*" -x` | No - Wave 0 |
| QUERY-02 | getAvailableModels returns active models | unit | `./gradlew test --tests "*ManifestCatalogServiceTest*getAvailableModels*" -x` | No - Wave 0 |
| QUERY-03 | getManifestByKey returns hydrated manifest | unit | `./gradlew test --tests "*ManifestCatalogServiceTest*getManifestByKey*" -x` | No - Wave 0 |
| QUERY-04 | getEntityTypesForManifest returns entity types | unit | `./gradlew test --tests "*ManifestCatalogServiceTest*getEntityTypesForManifest*" -x` | No - Wave 0 |
| TEST-06 | Full startup load verified | integration | `./gradlew test --tests "*ManifestLoaderIntegrationTest*fullLoadCycle*" -x` | No - Wave 0 |
| TEST-07 | Idempotent reload verified | integration | `./gradlew test --tests "*ManifestLoaderIntegrationTest*idempotentReload*" -x` | No - Wave 0 |
| TEST-08 | Manifest removal reconciliation | integration | `./gradlew test --tests "*ManifestLoaderIntegrationTest*removalReconciliation*" -x` | No - Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "*catalog*" -x`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `ManifestCatalogServiceTest.kt` -- unit tests for query methods (QUERY-01 through QUERY-04)
- [ ] `ManifestLoaderIntegrationTest.kt` -- integration tests (TEST-06, TEST-07, TEST-08)
- [ ] `ManifestLoaderIntegrationTestConfig.kt` -- Spring config for integration test context

## Sources

### Primary (HIGH confidence)
- Project codebase: All entity classes, repositories, services, and existing integration test patterns examined directly
- `EntityQueryIntegrationTestBase.kt` -- established Testcontainers pattern with `pgvector/pgvector:pg16`, `@ActiveProfiles("integration")`, `@DynamicPropertySource`
- `ManifestCatalogRepository.kt` -- existing queries, missing stale-filtered variants identified
- `ManifestScannerService.kt` -- hardcoded `classpath:manifests/` paths confirmed, refactoring needed for TEST-08
- `ManifestUpsertService.kt` -- delete-reinsert child reconciliation pattern documented
- `application-integration.yml` -- PostgreSQL dialect, `ddl-auto: create-drop`, Temporal disabled

### Secondary (MEDIUM confidence)
- Spring Data JPA derived query naming conventions -- well-documented, `findByStaleFalse` is standard

### Tertiary (LOW confidence)
- None

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new dependencies, all patterns established in codebase
- Architecture: HIGH -- follows existing service/repository/model conventions exactly
- Pitfalls: HIGH -- derived from direct code analysis of scanner and repository limitations

**Research date:** 2026-03-05
**Valid until:** 2026-04-05 (stable domain, no external dependencies changing)
