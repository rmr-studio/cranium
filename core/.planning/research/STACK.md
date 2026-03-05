# Stack Research

**Domain:** Declarative manifest catalog and consumption pipeline — Spring Boot / Kotlin backend
**Researched:** 2026-02-28
**Confidence:** HIGH (based on direct codebase inspection + library version verification from build.gradle.kts)

---

## Key Finding: The Stack Is Already Present

All required capabilities are available in the existing dependency tree. **No new dependencies are needed.** Every component of the manifest loading pipeline can be built with what is already in `build.gradle.kts`. The research below explains how each existing dependency addresses each pipeline requirement.

---

## Recommended Stack

### Core Technologies (Already in build.gradle.kts)

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| `com.networknt:json-schema-validator` | `1.0.83` | JSON Schema validation of manifest files at load time | Already in use in `SchemaService`. Supports Draft 2019-09 (`SpecVersion.VersionFlag.V201909`) which is the current standard. Validates a `JsonNode` against a schema node — exactly the pattern needed for manifest validation. |
| `com.fasterxml.jackson.module:jackson-module-kotlin` | via Spring BOM | JSON parsing: reading manifest `.json` files from classpath, deserializing into typed Kotlin data classes | Already configured via `ObjectMapperConfig` with `KotlinModule`, `JavaTimeModule`, `FAIL_ON_UNKNOWN_PROPERTIES=false`, and `ACCEPT_CASE_INSENSITIVE_ENUMS`. Used pervasively across the codebase. |
| `org.springframework.boot:spring-boot-starter-data-jpa` | `3.5.3` BOM | JPA entities for catalog tables, Spring Data repositories, idempotent `save()` upsert pattern | Already the persistence foundation. Catalog entities follow `IntegrationDefinitionEntity` pattern — no `AuditableEntity`, no `workspace_id`, no RLS. |
| Spring Core (`ApplicationContext`, `ResourceLoader`) | `3.5.3` BOM | Classpath resource scanning, `ApplicationReadyEvent` for startup loading | Built into Spring Boot. `PathMatchingResourcePatternResolver` scans `classpath*:manifests/**/*.json`. `ApplicationReadyEvent` fires after context fully initialized, including JPA/Hibernate, ensuring repositories are ready. |
| `org.springframework.data:spring-data-jpa` | `3.5.3` BOM | `@Modifying` + `nativeQuery = true` for `INSERT ... ON CONFLICT DO UPDATE` upsert queries | Already used in `EntityUniqueValuesRepository` and `EntityRelationshipRepository`. The `ON CONFLICT DO UPDATE` pattern is the correct approach for idempotent catalog upserts keyed on `key` column. |

### Supporting Libraries (Already in build.gradle.kts)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `io.github.oshai:kotlin-logging-jvm` | `7.0.0` | Structured warning logs for invalid manifests skipped at load time | Use for all loader warning/error logs. Already the project standard — injected via constructor from `LoggerConfig`. |
| `org.springframework.boot:spring-boot-starter-validation` | `3.5.3` BOM | `@Valid`, `@NotNull`, `@NotBlank` on manifest data classes for secondary validation | Use for simple field presence checks on manifest data classes, complementing JSON Schema structural validation. |
| `com.h2database:h2` + `org.testcontainers:testcontainers-postgresql` | test scope | Unit tests (H2 in PostgreSQL-compat mode) and integration tests (real PostgreSQL via Testcontainers) | Use H2 profile for unit-level loader tests. Use Testcontainers integration profile for full startup cycle tests including `ON CONFLICT` upsert verification. |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `./gradlew test` | Run test suite | Use `-t` flag for continuous mode during development. Run after every service method change. |
| IntelliJ JSON Schema inspection | Validate manifest `.json` files against schema during authoring | Associate `src/main/resources/manifests/schemas/*.json` in IDE settings for live validation. Not a runtime dependency — authoring aid only. |

---

## Installation

No new dependencies required. All capabilities exist in the current dependency tree.

If a future version upgrade of `json-schema-validator` is warranted, the next stable line is `1.4.x` (significant API changes — NOT a drop-in upgrade, requires migration). Stay on `1.0.83` for this milestone.

```kotlin
// Already in build.gradle.kts — no changes needed
implementation("com.networknt:json-schema-validator:1.0.83")
implementation("com.fasterxml.jackson.module:jackson-module-kotlin") // via BOM
```

---

## How Each Pipeline Requirement Maps to Existing Stack

### JSON Manifest File Loading on Startup

**Use:** `PathMatchingResourcePatternResolver` + `ApplicationReadyEvent`

```kotlin
@EventListener(ApplicationReadyEvent::class)
fun onApplicationReady() {
    val resolver = PathMatchingResourcePatternResolver()
    val resources = resolver.getResources("classpath*:manifests/models/**/*.json")
    // ...
}
```

- `classpath*:` prefix scans all classpath roots (including test resources when running tests)
- Pattern-based glob lets you scan subdirectories by type (`models/`, `templates/`, `integrations/`)
- Returns `Resource[]` — each has `inputStream` for reading and `filename` for identification
- `ApplicationReadyEvent` is preferred over `CommandLineRunner` or `@PostConstruct` because it fires after the full context including JPA, Hibernate schema validation, and all repository beans are ready. `@PostConstruct` on a service fires before JPA is fully initialized.

**Confidence:** HIGH — `PathMatchingResourcePatternResolver` is a core Spring class, stable since Spring 2.x, no version concerns.

### JSON Schema Validation

**Use:** `com.networknt:json-schema-validator:1.0.83` (already in codebase)

The existing `SchemaService` shows exactly how to use this library:

```kotlin
val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909)
val schemaNode: JsonNode = objectMapper.readTree(schemaResource.inputStream)
val jsonSchema = schemaFactory.getSchema(schemaNode)
val payloadNode: JsonNode = objectMapper.readTree(manifestResource.inputStream)
val errors = jsonSchema.validate(payloadNode)
```

- `getSchema(JsonNode)` loads a schema from an already-parsed `JsonNode` — works identically with classpath-loaded schema files
- `validate(JsonNode)` returns a `Set<ValidationMessage>` — non-empty means invalid manifest
- Invalid manifests must log a warning and be skipped — `errors.isNotEmpty()` is the check
- The manifest loader should NOT call `validateOrThrow` — it must catch errors and continue. Build a parallel to `validate()` that returns errors without throwing.

**Confidence:** HIGH — verified against existing `SchemaService.kt` which uses this exact API.

### `$ref` Resolution

**Use:** Pure Kotlin in-process — no library needed.

`$ref` in these manifests is a custom convention (e.g., `"$ref": "shared/contact-model"`) pointing to a named shared model by key. This is NOT JSON Schema `$ref` (pointer-based resolution into a schema document). It is application-level reference resolution done before validation.

The resolution algorithm is:
1. Load all model manifests first (dependency ordering: models before templates)
2. Build a `Map<String, LoadedModel>` keyed by manifest key
3. Walk each template manifest, find `"$ref"` nodes, look up the referenced key in the map
4. Substitute the referenced entity type definition inline

This is trivially done with Jackson's `ObjectNode.get("$ref")` traversal. No external `$ref` resolution library is needed or appropriate here.

**Confidence:** HIGH — the PROJECT.md specifies shallow merge, additive semantics. This is a custom convention, not JSON Schema pointer resolution.

### Shallow Merge / `extend` Logic

**Use:** Pure Kotlin map operations — no library needed.

```kotlin
// Shallow merge: new attributes added, existing attributes untouched
fun mergeAttributes(base: Map<String, Attribute>, extension: Map<String, Attribute>): Map<String, Attribute> {
    return base + extension.filterKeys { it !in base }
}
```

- No recursive merge
- No deletion semantics
- `base + overrides.filterKeys { it !in base }` is the exact Kotlin idiom

**Confidence:** HIGH — PROJECT.md explicitly specifies "shallow merge only, additive attributes, no deep recursive merge, no deletion semantics."

### Idempotent Database Upserts

**Use:** `@Modifying` + `nativeQuery = true` with PostgreSQL `INSERT ... ON CONFLICT DO UPDATE`

The existing codebase uses `findById` + conditional `save` (upsert-by-load) for simple cases like `upsertMetadataInternal`. For catalog tables with a stable string `key` column, a native SQL upsert is cleaner for multi-table startup loading:

```kotlin
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query(
    value = """
        INSERT INTO manifest_catalog (id, key, name, type, schema, created_at, updated_at)
        VALUES (:id, :key, :name, :type, :schema::jsonb, now(), now())
        ON CONFLICT (key) DO UPDATE SET
            name = EXCLUDED.name,
            schema = EXCLUDED.schema,
            updated_at = now()
    """,
    nativeQuery = true
)
fun upsertByKey(id: UUID, key: String, name: String, type: String, schema: String)
```

Alternatively, the find-and-save pattern (`findByKey(key)?.apply { ... } ?: newEntity`) works for lower-volume startup loading where Hibernate session management is less of a concern. Either approach is correct — the native upsert is more explicit about idempotency intent.

**Confidence:** HIGH — both patterns demonstrated in existing codebase (`EntityUniqueValuesRepository`, `upsertMetadataInternal`).

### Global Catalog Table Pattern (No RLS, No Workspace Scope)

**Use:** Follow `IntegrationDefinitionEntity` exactly.

The `integration_definitions` table is the established precedent:
- UUID primary key with `@GeneratedValue(strategy = GenerationType.UUID)`
- No `workspace_id` column
- Does NOT extend `AuditableEntity` (no `created_by`/`updated_by` — loader is system, not user)
- Has `created_at`/`updated_at` timestamps set manually
- No RLS policies in `05_rls/`
- No `@SQLRestriction` on the entity class

Catalog entities for this feature follow the same pattern. The `key` column (VARCHAR, UNIQUE, NOT NULL) serves as the idempotent upsert key instead of `slug`.

**Confidence:** HIGH — verified against `IntegrationDefinitionEntity.kt` and `integrations.sql`.

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| `PathMatchingResourcePatternResolver` (Spring built-in) | `Files.walk()` on filesystem path | Only if manifests are loaded from a configurable external directory outside the classpath. For classpath-packaged manifests, use the Spring resolver. |
| `networknt:json-schema-validator` (already present) | `everit-org/json-schema` | Everit is no longer actively maintained (last release 2021). Do not use. networknt is actively maintained and already in the project. |
| `networknt:json-schema-validator` (already present) | `com.github.java-json-tools:json-schema-validator` | Also no longer maintained. Replaced by networknt. Do not use. |
| `networknt:json-schema-validator 1.0.83` | `networknt 1.4.x` | 1.4.x introduces significant API breaking changes including new validation configuration API. Migration cost is not justified for this milestone. Stay on 1.0.83. |
| `ApplicationReadyEvent` | `@PostConstruct` on ManifestLoaderService | `@PostConstruct` fires before JPA/Hibernate is fully ready. `ApplicationReadyEvent` guarantees all Spring beans including repositories are initialized. Always prefer `ApplicationReadyEvent` for database-writing startup logic. |
| `ApplicationReadyEvent` | `CommandLineRunner` | Functionally equivalent. `ApplicationReadyEvent` is more idiomatic for Spring services. `CommandLineRunner` is more appropriate for CLI tool behavior. |
| Jackson `ObjectMapper.readValue<T>()` | Gson | Gson is not in the project. Jackson is already configured with Kotlin module and custom settings. Using Gson would require a parallel serializer with different behavior. |
| Native SQL `ON CONFLICT DO UPDATE` | Hibernate `saveOrUpdate` / `merge()` | `merge()` requires an attached entity with full state. For bulk startup loading where the entity state is constructed from file, native upsert is simpler and avoids Hibernate session state management complexity. Either works — native upsert is the clearer choice for the loader. |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `everit-org/json-schema` | No longer maintained. Last Maven release was 2021. No draft 2019-09 support. | `com.networknt:json-schema-validator:1.0.83` (already present) |
| `com.github.java-json-tools:json-schema-validator` | Archived/unmaintained. Do not add. | `com.networknt:json-schema-validator:1.0.83` (already present) |
| `networknt 1.4.x` upgrade | API breaking change. `JsonSchemaFactory.getInstance()` with `SpecVersion` is replaced by a new configuration-builder API. Migration is non-trivial and not justified for this milestone. | Stay on `1.0.83`. |
| `@PostConstruct` for manifest loading | Fires before JPA/Hibernate repositories are guaranteed ready. Will cause intermittent `NullPointerException` on repository injection or `EntityManagerFactory` not ready errors. | `@EventListener(ApplicationReadyEvent::class)` on a `@Service` method |
| `Flyway` or `Liquibase` for catalog SQL | Project explicitly does not use migration tools (`ddl-auto: none`, Flyway disabled in `application.yml`). SQL goes in `db/schema/01_tables/` following the existing execution order. | Raw SQL file in `db/schema/01_tables/catalog.sql` per README instructions |
| Custom JSON `$ref` resolution library (json-ref, json-ref-lite) | These target JSON Schema `$ref` pointer resolution (URI-based `#/definitions/Foo`). The manifest `$ref` is an application-level key lookup convention, not a JSON Pointer. These libraries solve the wrong problem. | Inline Kotlin map lookup during manifest loading |
| Jackson `@JsonProperty(required = true)` for validation | This enforces presence at Jackson deserialization time and throws `JsonMappingException`. Does not produce the structured error list needed for "log and skip" behavior. | JSON Schema validation via networknt, which returns a `Set<ValidationMessage>` without throwing |
| `AuditableEntity` base class for catalog entities | Requires `created_by` / `updated_by` UUID FK columns referencing `users`. Catalog loader is a system process with no user context. Will cause `AuditingEntityListener` to set `createdBy = null` which violates any NOT NULL constraint on those columns. | Follow `IntegrationDefinitionEntity` pattern: no `AuditableEntity`, manual `created_at` / `updated_at` |

---

## Stack Patterns by Variant

**If manifest directories are external to the jar (configurable path):**
- Use `@ConfigurationProperties` to bind a `manifest.base-path` property
- Use `Files.walk(Paths.get(basePath))` instead of `PathMatchingResourcePatternResolver`
- For this milestone, manifests are classpath-packaged — use `PathMatchingResourcePatternResolver`

**If manifest volume grows large (hundreds of manifests):**
- `PathMatchingResourcePatternResolver` scans classpath eagerly at startup — fine for tens to hundreds of files
- At 1,000+ files, consider lazy loading or checksum-based skip (PROJECT.md notes this as a future optimization)
- For this milestone, the full-scan-on-startup pattern is the right starting point

**If JSON Schema validation needs to be shared with CI:**
- The same `.json` schema files in `src/main/resources/manifests/schemas/` can be used in CI scripts with any JSON Schema CLI validator (e.g., `ajv-cli`)
- No additional library changes needed — the schema files are format-agnostic

---

## Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| `networknt:json-schema-validator:1.0.83` | `jackson-databind:2.x` (any 2.x) | Requires Jackson. The existing Jackson version (managed by Spring Boot 3.5.3 BOM, approximately `2.17.x`) is compatible. |
| `networknt:json-schema-validator:1.0.83` | `SpecVersion.VersionFlag.V201909` | Project uses Draft 2019-09. V7 (`SpecVersion.VersionFlag.V7`) is also supported. Do not mix draft versions between schema files. |
| `spring-boot:3.5.3` | `PathMatchingResourcePatternResolver` | Stable API, no version compatibility concerns. |
| `spring-boot:3.5.3` | `ApplicationReadyEvent` | Stable API. Available since Spring Framework 4.2. |
| `hypersistence-utils-hibernate-63:3.9.2` | `JsonBinaryType` for catalog JSONB columns | Already in use. Catalog entities with JSONB schema columns use `@Type(JsonBinaryType::class)` following `IntegrationDefinitionEntity`. |

---

## Sources

- `build.gradle.kts` (direct inspection) — confirmed `networknt:json-schema-validator:1.0.83` is already present — HIGH confidence
- `src/main/kotlin/riven/core/service/schema/SchemaService.kt` (direct inspection) — confirmed `JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909)` usage pattern — HIGH confidence
- `src/main/kotlin/riven/core/entity/integration/IntegrationDefinitionEntity.kt` (direct inspection) — confirmed global catalog table pattern without `AuditableEntity` — HIGH confidence
- `src/main/kotlin/riven/core/repository/entity/EntityUniqueValuesRepository.kt` (direct inspection) — confirmed `@Modifying` + `nativeQuery = true` upsert pattern — HIGH confidence
- `src/main/kotlin/riven/core/service/entity/EntityTypeSemanticMetadataService.kt` (direct inspection) — confirmed find-and-save upsert pattern — HIGH confidence
- `src/main/kotlin/riven/core/configuration/util/ObjectMapperConfig.kt` (direct inspection) — confirmed `ObjectMapper` configuration with `KotlinModule`, `FAIL_ON_UNKNOWN_PROPERTIES=false` — HIGH confidence
- `.planning/PROJECT.md` (direct inspection) — confirmed requirements for `$ref` resolution, shallow merge, idempotent upserts, startup loading — HIGH confidence
- Spring Framework documentation (training data) — `PathMatchingResourcePatternResolver`, `ApplicationReadyEvent` — MEDIUM confidence (stable APIs, unlikely to have changed)
- networknt json-schema-validator 1.0.x vs 1.4.x API differences — MEDIUM confidence (based on training data; version upgrade assessment may need verification if upgrade is considered in future)

---
*Stack research for: Declarative Manifest Catalog and Consumption Pipeline*
*Researched: 2026-02-28*
