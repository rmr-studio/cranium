# Project Research Summary

**Project:** Declarative Manifest Catalog and Consumption Pipeline
**Domain:** Startup-loaded catalog for entity templates, shared models, and integration definitions — Spring Boot 3.5.3 / Kotlin backend extension
**Researched:** 2026-02-28
**Confidence:** HIGH

## Executive Summary

The manifest catalog is a well-understood pattern: a startup-time data seeder that loads structured JSON files from the classpath into a set of global, workspace-independent database tables, making them queryable at runtime by downstream services. The closest analogues are Kubernetes CRD reconcilers (desired-state-vs-actual-state, stale removal, partial failure tolerance) and Helm chart registries (typed blueprints keyed by stable string identifiers). Unlike those systems, this pipeline runs exactly once per application startup with no background watch loop, which dramatically simplifies the design. The core recommendation is to treat the pipeline as a pure data transformation sequence — scan, validate, resolve, normalize, upsert — with each stage implemented as a dedicated, single-responsibility service, and no global transaction spanning the full load.

The most important architectural decision is to keep the catalog completely decoupled from workspace-scoped services. Catalog entities have no `workspace_id`, no `AuditableEntity` base, no RLS policies, and no UUID foreign key references to workspace entity types. All cross-catalog references use stable VARCHAR keys, with UUID resolution deferred to clone time (a future milestone). This design mirrors the existing `IntegrationDefinitionEntity` precedent in the codebase and is explicitly supported by ADR-004. No new library dependencies are required — all necessary capabilities (JSON Schema validation, Jackson deserialization, Spring classpath scanning, JPA upsert) are already present in `build.gradle.kts`.

The primary risks are implementation-level, not architectural. The most dangerous are: wrapping the full load in a single `@Transactional` boundary (causes startup lock contention and all-or-nothing failure when one bad manifest should be skipped), getting the merge direction wrong in the `extend` logic (silently drops base-model attributes), and relying on filesystem ordering for the models-before-templates dependency (fails in Docker images where classpath resource ordering is undefined). All three are preventable with unit tests written before the implementation code. A secondary risk is reconciliation design: hard-deleting catalog entries at reconciliation time will break future clone references, so soft-delete must be on the schema from day one even though the clone service is not built in this milestone.

## Key Findings

### Recommended Stack

All required capabilities exist in the current dependency tree. No new dependencies are needed. Every component of the manifest loading pipeline maps to an already-present library.

**Core technologies:**
- `com.networknt:json-schema-validator:1.0.83` — JSON Schema validation of manifest files — already used in `SchemaService`, proven API against `Draft 2019-09`
- `com.fasterxml.jackson.module:jackson-module-kotlin` (via Spring BOM) — manifest file deserialization into typed Kotlin data classes — already configured with `KotlinModule`, `FAIL_ON_UNKNOWN_PROPERTIES=false`
- `PathMatchingResourcePatternResolver` (Spring Core) — classpath scanning for manifest `.json` files — stable since Spring 2.x, scans `classpath*:manifests/**/*.json`
- `ApplicationReadyEvent` (Spring Core) — startup trigger that fires after JPA/Hibernate is fully initialized — preferred over `@PostConstruct` or `CommandLineRunner`
- `Spring Data JPA` / `@Modifying nativeQuery` — idempotent `INSERT ... ON CONFLICT DO UPDATE` upserts — already demonstrated in `EntityUniqueValuesRepository`
- `io.github.oshai:kotlin-logging-jvm:7.0.0` — structured warning logs for skipped manifests — project standard, constructor-injected via `LoggerConfig`

**Critical version note:** Stay on `networknt 1.0.83`. The `1.4.x` line has breaking API changes (`JsonSchemaFactory.getInstance()` with `SpecVersion` is replaced by a new configuration builder). Do not upgrade for this milestone.

### Expected Features

**Must have (table stakes — catalog non-functional without these):**
- Startup loading via `ApplicationReadyEvent` — catalog must be populated before any request is served
- Idempotent upsert keyed on `manifest_catalog(key, manifest_type)` — re-deployment must not duplicate the catalog
- JSON Schema validation at load time — invalid manifests skip with WARN log, application always starts
- Directory-based manifest discovery — `models/`, `templates/`, `integrations/` auto-scanned
- Dependency-ordered loading — models fully processed before templates; enforced by code, not filesystem ordering
- `$ref` resolution using in-memory model index — templates expand shared model references before DB write
- `extend` merge logic — shallow, additive only (new attributes added, base attributes preserved on key conflict, no deletion semantics)
- Relationship format normalization — shorthand single-target → full `targetRules[]` format
- Relationship validation — key existence in current run's manifest set, cardinality enum values
- Full reconciliation — soft-delete stale catalog entries not present on disk (not hard-delete)
- Child table upsert with reconciliation — entity types, attributes, relationships, semantic metadata; delete-then-reinsert per parent within one transaction
- `ManifestCatalogService` read-only query API — `getAvailableTemplates()`, `getAvailableModels()`, `getManifestByKey()`, `getEntityTypesForManifest()`
- Directory scaffolding and README authoring guide
- Test fixture manifests (one model, one template with `$ref` + `extend`, one integration)
- Unit tests for `$ref`, `extend` merge, normalization, validation
- Integration tests for full startup cycle, idempotent reload, manifest removal reconciliation

**Should have (production observability):**
- Per-manifest load result summary log — `loaded=12, skipped=1, removed=3` at startup
- In-memory manifest state during load — no mid-load DB reads for reference resolution
- Manifest type discriminated by directory, not by a `type` field in the manifest JSON
- Two-phase validation: structural (JSON Schema) then semantic (key references, cardinality)
- Configurable manifest base path via `application.yml` — allow override to `file:/path` for mounted volumes

**Defer to v2+:**
- Checksum-based skip optimization — only when catalog has 20+ manifests causing perceptible startup latency
- Dry-run mode for CI manifest validation — when manifest authoring becomes a team workflow
- Hot reload via filesystem watching — disproportionate complexity, manifests are infrastructure not live data
- REST API catalog browsing endpoints — defer until clone service exists as a concrete consumer
- `CatalogCloneService` — installs a template or model into a workspace — out of scope for this milestone

**Anti-features (explicitly avoid):**
- Deep recursive `extend` merge — ambiguous semantics, hard to document, brittle when base models evolve
- UUID-keyed references in catalog relationships — unstable across re-loads; use VARCHAR keys throughout the catalog
- `readonly` guard clauses on `EntityTypeService` for catalog-sourced types — premature; add when clone service ships
- Generic field mapping execution in the catalog loader — conflates catalog storage with runtime sync pipeline

### Architecture Approach

The system follows a clear two-phase architecture: a startup-time write pipeline (manifest loader) feeding a runtime read surface (ManifestCatalogService). The pipeline is a sequential transformation chain: scan files → validate structure → build in-memory model index → resolve `$ref` in templates → normalize relationships → upsert to DB → reconcile stale entries. Each stage is implemented as a separate single-responsibility service under `service.catalog`. No controller layer is added in this milestone — the catalog is an internal service, not a REST API. The catalog tables are global (no workspace scope, no RLS) and follow the `IntegrationDefinitionEntity` precedent exactly.

**Major components:**
1. `ManifestLoaderService` — orchestrator only; fires on `ApplicationReadyEvent`; no business logic
2. `ManifestScanService` — classpath walker; enforces explicit load order: models → templates → integrations
3. `ManifestValidatorService` — JSON Schema validation per manifest; returns discriminated result (valid/invalid); never throws on invalid
4. `RefResolutionService` — builds in-memory model index from validated models; resolves template `$ref` strings via key lookup (never DB reads)
5. `RelationshipNormalizationService` — transforms shorthand relationship format to full `targetRules[]`; validates key existence and cardinality enum
6. `ManifestUpsertService` — per-manifest `@Transactional` upsert: parent upsert by `(key, manifest_type)`, child delete-and-reinsert, stale reconciliation (soft-delete)
7. `ManifestCatalogService` — read-only query surface for downstream consumers; no `@PreAuthorize` (catalog is global)
8. 6 JPA entities in `entity.catalog` + 6 repositories in `repository.catalog`
9. `ManifestType` enum: `MODEL`, `TEMPLATE`, `INTEGRATION`
10. Database: 6 catalog tables in `db/schema/01_tables/catalog.sql`; no RLS; composite unique constraint `UNIQUE(key, manifest_type)` on parent table

**Key data flow:** Filesystem is the source of truth. Database is the query surface. Loader bridges them at every startup via idempotent upsert. `$ref` resolution happens entirely in-memory against the current run's manifest set — never against the database.

### Critical Pitfalls

1. **`@Transactional` on the `ApplicationReadyEvent` listener method** — The transaction proxy may not intercept it correctly at startup, and even if it does, a single transaction spanning the full load holds write locks for seconds. Use `@Transactional` only on `ManifestUpsertService.upsertSingleManifest()` (per-manifest transaction). The orchestrator's `onApplicationReady()` method is not transactional.

2. **Non-idempotent child row upsert** — The parent manifest entry upserts by key, but child rows (entity types, attributes, relationships) accumulate duplicates on re-load if the delete-before-reinsert step is missing or outside the transaction. Child reconciliation pattern: within one `@Transactional` boundary, delete all existing children for the parent ID, then insert the new set.

3. **Hard-delete during reconciliation breaks future clone references** — Catalog entries removed from disk are deleted from the DB, but future workspace clones will reference catalog keys by string. Use soft-delete (`deleted = true`) on the `manifest_catalog` table from day one. Only hard-delete entries with no downstream references (and only when the clone service explicitly confirms it is safe to do so).

4. **Filesystem ordering dependency in Docker** — Loading models before templates by relying on alphabetical filesystem ordering fails inside Docker JAR files where classpath resource ordering is undefined. The loading order must be enforced by explicit code: `listOf("models", "templates", "integrations").forEach { loadDirectory(it) }`, not by scanning `**/*.json` and hoping models sort first.

5. **Wrong `extend` merge direction silently drops base model attributes** — The Kotlin `+` operator on `Map` preserves base-only keys but the direction of `base + extension` vs `extension + base` determines which side wins on key conflict. The required semantics are: base wins on conflict, extension adds new keys. Write a unit test before implementing: "template attribute with same key as base attribute → base definition must win."

6. **Key namespace collision** — A model and a template with the same `key` string are distinct catalog entries. The unique constraint on `manifest_catalog` must be `UNIQUE(key, manifest_type)`, not `UNIQUE(key)`. Without the composite constraint, one silently overwrites the other on upsert.

## Implications for Roadmap

Research dictates three phases with hard dependencies flowing from the database schema through the loader pipeline to the read surface. The ARCHITECTURE.md file provides the most direct roadmap input — its "Suggested Build Order" aligns with code dependency constraints.

### Phase 1: Database Foundation and Directory Scaffolding

**Rationale:** JPA entities and repositories must exist before any service can write to them. Schema design decisions (composite unique constraint, soft-delete column, JSONB vs VARCHAR) cannot be changed easily after Phase 2 builds on top of them. Getting the schema right first eliminates rework. Directory scaffolding and JSON Schema files belong here because they are authoring infrastructure needed by Phase 2.

**Delivers:** All 6 catalog tables in `db/schema/01_tables/catalog.sql`, 6 JPA entities (`entity.catalog.*`), 6 Spring Data repositories (`repository.catalog.*`), `ManifestType` enum, `models/` + `templates/` + `integrations/` directory structure with READMEs, JSON Schema files for all 3 manifest types, test fixture manifests in `src/test/resources/manifests/`.

**Addresses:** Table stakes features — idempotent upsert (schema prerequisite), child table upsert (entity structure), `$ref` resolution (fixture manifests for testing), directory scaffolding

**Avoids:**
- Pitfall 3 (reconciliation hard-delete) — soft-delete `deleted` column on `manifest_catalog` is a schema decision; must be done here
- Pitfall 6 (key namespace collision) — `UNIQUE(key, manifest_type)` composite constraint is a schema decision; must be done here
- Pitfall 4 (`AuditableEntity` on catalog entities) — follow `IntegrationDefinitionEntity` pattern; no `AuditableEntity`, no `SoftDeletable` infrastructure

**Research flag:** No additional research needed. All schema decisions are fully specified in ARCHITECTURE.md. Pattern is established by `IntegrationDefinitionEntity`. Standard Spring Data JPA.

### Phase 2: Loader Pipeline

**Rationale:** All pipeline services (`ManifestScanService`, `ManifestValidatorService`, `RefResolutionService`, `RelationshipNormalizationService`, `ManifestUpsertService`, `ManifestLoaderService`) depend on the repositories from Phase 1 existing. The pure-logic services (`ManifestScanService`, `ManifestValidatorService`, `RefResolutionService`, `RelationshipNormalizationService`) can be built and unit-tested without a running database, while `ManifestUpsertService` requires Phase 1 entities. Build and unit-test all services before wiring the orchestrator.

**Delivers:** Full startup load pipeline running on `ApplicationReadyEvent`. On each startup: scans manifest files in explicit order, validates against JSON Schema, resolves `$ref` in-memory, normalizes relationships, upserts all catalog tables per-manifest, soft-deletes stale entries. Per-manifest `@Transactional` isolation. Warn-and-skip for invalid manifests.

**Implements:** Architecture components 1-7 (all loader services)

**Avoids:**
- Pitfall 1 (`@Transactional` on event listener) — `@Transactional` only on `ManifestUpsertService` methods
- Pitfall 2 (non-idempotent child upsert) — delete-before-reinsert within per-manifest transaction
- Pitfall 4 (filesystem ordering in Docker) — explicit `listOf("models", "templates", "integrations")` load order
- Pitfall 5 (wrong merge direction) — unit tests written before merge implementation
- Pitfall 2 (`$ref` classpath vs filesystem resolution) — separate `RefResolutionService` uses key-based in-memory lookup, not URI resolution

**Research flag:** No additional research needed. Implementation patterns are fully specified by ARCHITECTURE.md and PITFALLS.md with code-level examples. The key technical decision (per-manifest transactions vs global transaction) is resolved by PITFALLS.md.

### Phase 3: Read Surface and Integration Tests

**Rationale:** `ManifestCatalogService` is pure read-only and has no dependencies on the loader pipeline — it reads from the catalog tables that Phase 1 created. However, the integration tests for the full startup cycle require both Phase 1 (tables) and Phase 2 (loader) to be complete. This phase finalizes the system by providing the downstream-consumer-facing service and validating the full pipeline end-to-end.

**Delivers:** `ManifestCatalogService` with 4 read-only query methods. Integration tests covering: full startup load cycle (fixtures loaded correctly), idempotent reload (second startup produces identical state), manifest removal reconciliation (removed manifest results in soft-deleted catalog entry, not hard-deleted).

**Addresses:** `ManifestCatalogService` (table stakes for future clone service), per-manifest load result summary log (production observability)

**Avoids:**
- Pitfall 3 (reconciliation correctness) — integration test explicitly verifies `deleted = true` not `COUNT = 0`
- Pitfall 2 (non-idempotent upsert) — integration test runs load twice and asserts identical row counts
- "Looks Done But Isn't" checklist items from PITFALLS.md — all 8 checklist items are integration test targets

**Research flag:** No additional research needed. `ManifestCatalogService` follows the `IntegrationDefinitionService` read-only service pattern exactly. Integration test patterns follow `EntityQueryIntegrationTestBase` in the existing codebase.

### Phase Ordering Rationale

- **Schema before services:** JPA entities and repositories are compile-time dependencies of every service. No service compiles without them.
- **Pure-logic services before database-dependent services:** `ManifestScanService`, `ManifestValidatorService`, `RefResolutionService`, and `RelationshipNormalizationService` have zero database dependencies and can be unit-tested in H2 profile. `ManifestUpsertService` requires real repositories. Build in this order to maximize test coverage before any database writing is needed.
- **Loader before read surface:** `ManifestCatalogService` is meaningless without data in the catalog tables. The loader populates those tables.
- **Read surface and integration tests together:** The integration tests are the verification gate for the entire pipeline. They belong in the same phase as the final component (`ManifestCatalogService`) because only then is the system complete enough to test end-to-end.
- **Pitfall-driven design at schema phase:** Both the soft-delete decision (Pitfall 3) and the composite unique constraint decision (Pitfall 6) are schema-level decisions that cannot be retrofitted cleanly. They must be specified in Phase 1.

### Research Flags

**Phases with standard patterns (no additional research needed):**
- **Phase 1 (Database Foundation):** Follows `IntegrationDefinitionEntity` pattern exactly. SQL schema follows `db/schema/README.md` conventions. JPA entity structure mirrors existing entities. No novel patterns.
- **Phase 2 (Loader Pipeline):** All patterns specified in ARCHITECTURE.md with code examples. Transaction design, in-memory resolution, per-manifest upsert — all are established Spring Boot patterns documented in PITFALLS.md.
- **Phase 3 (Read Surface + Integration Tests):** `ManifestCatalogService` follows `IntegrationDefinitionService`. Integration tests follow `EntityQueryIntegrationTestBase`. No novel patterns.

No phase in this milestone requires `/gsd:research-phase` during planning. All architectural decisions are resolved and sourced from direct codebase inspection.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All findings from direct `build.gradle.kts` inspection and `SchemaService.kt` code analysis. No new dependencies needed — eliminates all dependency evaluation uncertainty. |
| Features | HIGH | Derived directly from `PROJECT.md` requirements and codebase analysis of existing patterns. Feature scope is explicitly bounded in `PROJECT.md`. Anti-features section eliminates scope creep risks. |
| Architecture | HIGH | Based on ADR-004, `PROJECT.md`, `CLAUDE.md` architecture rules, and `IntegrationDefinitionEntity` precedent. All component boundaries and data flows are specified to code-level detail. |
| Pitfalls | HIGH | 8 critical pitfalls identified with specific prevention steps and code examples. Each pitfall is mapped to the phase where it must be addressed. All derived from codebase analysis + first-principles reasoning about the specific tech stack. |

**Overall confidence:** HIGH

### Gaps to Address

- **Soft-delete vs hard-delete for reconciliation is a product decision:** PITFALLS.md recommends soft-delete to protect future clone references. FEATURES.md describes the reconciliation as "remove stale catalog entries." These are in tension. The recommended resolution — soft-delete from day one — is the safer default, but the product semantics (should a user be able to see deprecated templates in the catalog?) need to be confirmed during Phase 1 schema design. If templates should disappear from query results when removed from disk, the `ManifestCatalogService` queries must filter `WHERE deleted = false`.

- **Manifest file location (classpath vs project root):** ARCHITECTURE.md shows manifest directories at the project root (`models/`, `templates/`, `integrations/`) rather than inside `src/main/resources/`. If manifests live outside `src/main/resources/`, they will not be on the classpath and `PathMatchingResourcePatternResolver` will not find them. The correct location for classpath-packaged manifests is `src/main/resources/manifests/`. Confirm the intended manifest location before Phase 1 scaffolding.

- **`entity_types` table column additions:** FEATURES.md mentions "2 new column additions to `entity_types`" as part of the MVP. These are `source_manifest_key` (for tracking catalog origin) and possibly a `protected` flag. These additions affect the existing workspace entity type domain, not just the catalog domain. Confirm the column names and semantics before adding them to the schema in Phase 1, since modifying the `entity_types` table requires understanding the full execution order and downstream effects per `CLAUDE.md`.

## Sources

### Primary (HIGH confidence — direct codebase inspection)

- `build.gradle.kts` — dependency inventory confirming no new libraries needed
- `src/main/kotlin/riven/core/service/schema/SchemaService.kt` — `networknt` validator usage pattern
- `src/main/kotlin/riven/core/entity/integration/IntegrationDefinitionEntity.kt` — global catalog entity pattern
- `src/main/kotlin/riven/core/repository/entity/EntityUniqueValuesRepository.kt` — `@Modifying` upsert pattern
- `src/main/kotlin/riven/core/service/entity/EntityTypeSemanticMetadataService.kt` — find-and-save upsert pattern
- `docs/system-design/decisions/ADR-004 Declarative-First Storage for Integration Mappings and Entity Templates.md` — authoritative design decision
- `.planning/PROJECT.md` — requirements, out-of-scope boundaries, `$ref` semantics specification

### Secondary (HIGH confidence — established framework documentation)

- Spring Framework `ApplicationReadyEvent` — fires after full context initialization, safe for JPA writes
- Spring `PathMatchingResourcePatternResolver` — stable classpath scanning API since Spring 2.x
- `com.networknt:json-schema-validator:1.0.83` vs `1.4.x` API differences — version upgrade assessment

### Tertiary (MEDIUM confidence — reference pattern analysis)

- Kubernetes CRD reconciler pattern — desired-state reconciliation with stale removal and partial failure tolerance
- Helm chart registry pattern — typed blueprint catalog keyed by stable string identifiers

---
*Research completed: 2026-02-28*
*Ready for roadmap: yes*
