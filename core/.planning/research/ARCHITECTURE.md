# Architecture Research

**Domain:** Declarative manifest catalog and consumption pipeline — Spring Boot / Kotlin layered backend
**Researched:** 2026-02-28
**Confidence:** HIGH — derived from direct codebase inspection, ADR-004, PROJECT.md, and existing integration_definitions precedent within the same codebase.

---

## System Overview

The manifest catalog is a **startup-time write pipeline feeding a runtime read surface**. Manifests on disk are the authoring format; the database is the query surface; the loader bridges them at every startup via idempotent upsert.

```
┌──────────────────────────────────────────────────────────────────────┐
│                         FILESYSTEM (source of truth)                  │
│  ┌──────────────┐  ┌──────────────────┐  ┌──────────────────────┐    │
│  │  models/     │  │  templates/      │  │  integrations/       │    │
│  │  *.json      │  │  */manifest.json │  │  */manifest.json     │    │
│  └──────┬───────┘  └───────┬──────────┘  └──────────┬───────────┘    │
└─────────┼──────────────────┼─────────────────────────┼───────────────┘
          │                  │                         │
          ▼                  ▼                         ▼
┌──────────────────────────────────────────────────────────────────────┐
│                    MANIFEST LOADER (startup pipeline)                  │
│                                                                        │
│  ┌───────────────────────┐    ┌──────────────────────────────────┐    │
│  │  ManifestScanService  │    │  ManifestValidatorService        │    │
│  │  (directory walker,   │───▶│  (JSON Schema validation,        │    │
│  │   ordering enforcer)  │    │   skip-on-fail, warn log)        │    │
│  └───────────────────────┘    └──────────────┬───────────────────┘    │
│                                              │                         │
│  ┌───────────────────────┐    ┌──────────────▼───────────────────┐    │
│  │  RefResolutionService │    │  ManifestLoaderService           │    │
│  │  (in-memory $ref map, │◀───│  (ApplicationReadyEvent,         │    │
│  │   extend shallow merge│    │   orchestrates full pipeline)    │    │
│  └───────────────────────┘    └──────────────┬───────────────────┘    │
│                                              │                         │
│  ┌───────────────────────┐    ┌──────────────▼───────────────────┐    │
│  │  RelationshipNorm-    │    │  ManifestUpsertService           │    │
│  │  alizationService     │◀───│  (idempotent upsert, reconcile   │    │
│  │  (shorthand→full fmt) │    │   stale entries on each boot)   │    │
│  └───────────────────────┘    └──────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────┘
                          │
                          ▼ writes
┌──────────────────────────────────────────────────────────────────────┐
│                       DATABASE (runtime query surface)                 │
│                                                                        │
│  manifest_catalog          catalog_entity_types                        │
│  ├── key (UNIQUE)          ├── catalog_id (FK)                         │
│  ├── type (MODEL/TEMPLATE/ ├── key (string, no UUID)                   │
│  │        INTEGRATION)     ├── schema (JSONB)                          │
│  ├── name                  └── ...                                     │
│  └── ...                                                               │
│                                                                        │
│  catalog_relationship_definitions   catalog_relationship_target_rules  │
│  catalog_entity_type_semantic_metadata                                 │
│  catalog_field_mappings (integrations only)                            │
└──────────────────────────────────────────────────────────────────────┘
                          │
                          ▼ reads
┌──────────────────────────────────────────────────────────────────────┐
│                       READ SURFACE (query service)                     │
│                                                                        │
│  ManifestCatalogService                                                │
│  ├── getAvailableTemplates()                                           │
│  ├── getAvailableModels()                                              │
│  ├── getManifestByKey()                                                │
│  └── getEntityTypesForManifest()                                       │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Component Responsibilities

| Component | Package | Responsibility | Communicates With |
|-----------|---------|----------------|-------------------|
| `ManifestLoaderService` | `service.catalog` | `ApplicationReadyEvent` listener; orchestrates startup pipeline in dependency order: scan → validate → resolve → upsert → reconcile | `ManifestScanService`, `ManifestValidatorService`, `RefResolutionService`, `RelationshipNormalizationService`, `ManifestUpsertService` |
| `ManifestScanService` | `service.catalog` | Walks `models/`, `templates/`, `integrations/` directories; enforces loading order (models first); returns raw `JsonNode` per file | Filesystem only |
| `ManifestValidatorService` | `service.catalog` | Validates each `JsonNode` against the manifest-type-specific JSON Schema; returns result with skip-on-failure semantics; logs warnings for invalid manifests | `SchemaService` (existing) or standalone Jackson `JsonSchemaFactory` |
| `RefResolutionService` | `service.catalog` | Holds in-memory model key→definition map; resolves `$ref` references in template manifests; applies `extend` shallow merge | In-memory map only (no DB reads) |
| `RelationshipNormalizationService` | `service.catalog` | Converts shorthand relationship format (single `target`) to full format (`targetRules[]`); validates format mutual exclusivity and enum fields | No external dependencies |
| `ManifestUpsertService` | `service.catalog` | Idempotent upsert of all catalog tables keyed on `manifest_catalog.key` + child unique constraints; reconciles stale entries (deletes DB rows for keys no longer on disk) | All catalog repositories |
| `ManifestCatalogService` | `service.catalog` | Read-only query service; exposes catalog to downstream consumers (future clone service, future API); no `@PreAuthorize` — catalog is global | Catalog repositories |
| Catalog JPA Entities | `entity.catalog` | `ManifestCatalogEntity`, `CatalogEntityTypeEntity`, `CatalogRelationshipDefinitionEntity`, `CatalogRelationshipTargetRuleEntity`, `CatalogEntityTypeSemanticMetadataEntity`, `CatalogFieldMappingEntity` | No workspace scoping, no `AuditableEntity` base |
| Catalog Repositories | `repository.catalog` | Standard `JpaRepository` per catalog entity; custom finders for key-based lookups and manifest-scoped queries | JPA / PostgreSQL only |

---

## Package Structure

The manifest catalog follows the existing `riven.core.{layer}.{domain}` convention. The new domain is `catalog`.

```
src/main/kotlin/riven/core/
├── entity/
│   └── catalog/
│       ├── ManifestCatalogEntity.kt          # parent catalog entry (key, type, name, description)
│       ├── CatalogEntityTypeEntity.kt         # entity type definition (schema as JSONB, string key)
│       ├── CatalogRelationshipDefinitionEntity.kt
│       ├── CatalogRelationshipTargetRuleEntity.kt
│       ├── CatalogEntityTypeSemanticMetadataEntity.kt
│       └── CatalogFieldMappingEntity.kt       # integrations only
│
├── repository/
│   └── catalog/
│       ├── ManifestCatalogRepository.kt
│       ├── CatalogEntityTypeRepository.kt
│       ├── CatalogRelationshipDefinitionRepository.kt
│       ├── CatalogRelationshipTargetRuleRepository.kt
│       ├── CatalogEntityTypeSemanticMetadataRepository.kt
│       └── CatalogFieldMappingRepository.kt
│
├── service/
│   └── catalog/
│       ├── ManifestLoaderService.kt           # startup orchestrator
│       ├── ManifestScanService.kt             # filesystem walker
│       ├── ManifestValidatorService.kt        # JSON Schema validation
│       ├── RefResolutionService.kt            # $ref + extend merge
│       ├── RelationshipNormalizationService.kt # shorthand → full format
│       ├── ManifestUpsertService.kt           # idempotent DB writes + reconciliation
│       └── ManifestCatalogService.kt          # read-only query surface
│
├── enums/
│   └── catalog/
│       └── ManifestType.kt                   # MODEL, TEMPLATE, INTEGRATION
│
models/                                        # manifest source files (project root, not src/)
  *.json
templates/
  */manifest.json
integrations/
  */manifest.json

schemas/                                       # JSON Schema files for manifest validation
  model-manifest.schema.json
  template-manifest.schema.json
  integration-manifest.schema.json

db/schema/
  01_tables/
    catalog.sql                                # all 6 catalog tables in one file
  02_indexes/
    catalog_indexes.sql
  05_rls/
    (none — catalog tables are global, no RLS)
  09_grants/
    (update auth_grants.sql if anon role needs SELECT on catalog tables)

src/test/resources/
  manifests/
    models/
      test-customer.json
    templates/
      test-saas/manifest.json                  # references test-customer, adds extend + inline type
    integrations/
      test-crm/manifest.json
```

---

## Data Flow

### Startup Load Pipeline

```
ApplicationReadyEvent
    │
    ▼
ManifestLoaderService.onApplicationReady()
    │
    ├─1─▶ ManifestScanService.scanModels()
    │       → returns List<RawManifest>(type=MODEL, key, jsonNode)
    │
    ├─2─▶ ManifestValidatorService.validateAll(models)
    │       → skips invalids (warns), returns List<ValidatedManifest>
    │
    ├─3─▶ RefResolutionService.buildModelIndex(validModels)
    │       → in-memory Map<String, ResolvedEntityTypeDef>
    │
    ├─4─▶ ManifestScanService.scanTemplates() + scanIntegrations()
    │       → returns raw manifests for each
    │
    ├─5─▶ ManifestValidatorService.validateAll(templates + integrations)
    │       → skips invalids
    │
    ├─6─▶ RefResolutionService.resolveTemplates(templates, modelIndex)
    │       → expands $ref, applies extend merge → List<ResolvedManifest>
    │
    ├─7─▶ RelationshipNormalizationService.normalize(allManifests)
    │       → converts shorthand → full, validates, skips invalid relationships
    │
    └─8─▶ ManifestUpsertService.upsertAll(resolvedManifests)
            ├── upsert manifest_catalog rows (keyed on key)
            ├── upsert catalog_entity_types (keyed on catalog_id + key)
            ├── upsert catalog_relationship_definitions (keyed on catalog_id + relationship_key)
            ├── upsert catalog_relationship_target_rules
            ├── upsert catalog_entity_type_semantic_metadata
            ├── upsert catalog_field_mappings (integrations only)
            └── reconcile: delete DB rows whose keys are absent from disk manifests
```

### Read Path (post-startup, downstream consumers)

```
Downstream consumer (future CatalogCloneService, future REST controller)
    │
    ▼
ManifestCatalogService
    ├── getAvailableTemplates() → List<ManifestCatalogEntity> where type=TEMPLATE
    ├── getAvailableModels() → List<ManifestCatalogEntity> where type=MODEL
    ├── getManifestByKey(key) → ManifestCatalogEntity (with child entities)
    └── getEntityTypesForManifest(catalogId) → List<CatalogEntityTypeEntity>
    │
    ▼
Catalog Repositories (JpaRepository, standard Spring Data queries)
    │
    ▼
PostgreSQL — catalog tables (no RLS, globally readable)
```

---

## Architectural Patterns

### Pattern 1: Event-Driven Startup (ApplicationReadyEvent)

**What:** `ManifestLoaderService` implements `@EventListener(ApplicationReadyEvent::class)`. This fires after the application context is fully initialized and all beans are ready — after JPA is connected, after security is configured.

**When to use:** Any system-level initialization that requires the full application context: database-writing startup tasks, cache warm-up, seed data loading.

**Why not `@PostConstruct` or `ApplicationRunner`:** `@PostConstruct` fires before JPA transactions are available in the full context. `ApplicationReadyEvent` is the correct hook for "run after the server is ready to serve."

**Example:**
```kotlin
@Service
class ManifestLoaderService(
    private val logger: KLogger,
    private val manifestScanService: ManifestScanService,
    private val manifestValidatorService: ManifestValidatorService,
    private val refResolutionService: RefResolutionService,
    private val relationshipNormalizationService: RelationshipNormalizationService,
    private val manifestUpsertService: ManifestUpsertService
) {

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun onApplicationReady() {
        logger.info { "Starting manifest catalog load..." }
        val models = loadAndValidate(manifestScanService.scanModels(), ManifestType.MODEL)
        val modelIndex = refResolutionService.buildModelIndex(models)
        val templates = loadAndValidate(manifestScanService.scanTemplates(), ManifestType.TEMPLATE)
        val integrations = loadAndValidate(manifestScanService.scanIntegrations(), ManifestType.INTEGRATION)
        val resolved = refResolutionService.resolveTemplates(templates, modelIndex) + models + integrations
        val normalized = relationshipNormalizationService.normalize(resolved)
        manifestUpsertService.upsertAll(normalized)
        logger.info { "Manifest catalog load complete." }
    }
}
```

### Pattern 2: Key-Based Idempotent Upsert

**What:** All catalog tables use a string `key` as the logical identity for upsert. On each startup, the loader computes the "current set of keys from disk" and:
1. Inserts rows for new keys.
2. Updates rows for existing keys (reconcile field changes).
3. Deletes rows whose keys are no longer present on disk (reconcile removals).

**When to use:** Any startup-loaded catalog where the database must exactly mirror the filesystem state.

**Implementation note:** Use `@Query` with `ON CONFLICT (key) DO UPDATE` native SQL for atomicity, OR fetch existing rows by key, diff in-memory, then execute targeted insert/update/delete batches. The second approach is safer with JPA (avoids native SQL drift) and sufficient at the manifest scale (tens of manifests, not millions).

**Example upsert logic:**
```kotlin
@Transactional
fun upsertManifestCatalog(resolved: List<ResolvedManifest>) {
    val existingByKey = catalogRepository.findAll().associateBy { it.key }
    val incomingKeys = resolved.map { it.key }.toSet()

    // Insert or update
    for (manifest in resolved) {
        val existing = existingByKey[manifest.key]
        if (existing == null) {
            catalogRepository.save(manifest.toEntity())
        } else {
            catalogRepository.save(existing.applyUpdates(manifest))
        }
    }

    // Reconcile — delete stale entries
    existingByKey.keys
        .filter { it !in incomingKeys }
        .forEach { staleKey -> catalogRepository.deleteByKey(staleKey) }
}
```

### Pattern 3: In-Memory Resolution (No DB Round-Trip During Load)

**What:** `RefResolutionService` holds models in a `Map<String, ResolvedEntityTypeDef>` built from the validated model manifests. Template `$ref` resolution is a pure in-memory lookup against this map — no database reads during template processing.

**Why:** Avoids a chicken-and-egg problem (models may not yet be in the DB when templates are being resolved). Keeps the load pipeline deterministic and fast.

**Consequence:** Models must always be scanned and validated before templates. This is enforced by `ManifestLoaderService` calling `scanModels()` before `scanTemplates()`.

### Pattern 4: No `AuditableEntity` on Catalog Entities

**What:** Catalog JPA entities do not extend `AuditableEntity` and do not implement `SoftDeletable`. They have only `created_at` and `updated_at` timestamps (set by the loader, not by Spring auditing). No `created_by`, `updated_by`, `deleted` columns.

**Why:** Catalog data is system-managed by the loader, not user-managed. There is no auth context at startup time (`AuthTokenService` cannot provide a user UUID when no request is in flight). This matches the `IntegrationDefinitionEntity` precedent in the same codebase.

**Consequence:** Stale entries are hard-deleted during reconciliation (not soft-deleted). The catalog is a mirror of the filesystem, not an append-only audit log.

### Pattern 5: Fail-Safe Loading (Skip-on-Error, Not Crash)

**What:** `ManifestValidatorService` returns a result type that distinguishes valid manifests from invalid ones. `ManifestLoaderService` logs a `WARN` for invalid manifests and continues. Individual invalid relationship entries (post-normalization validation failures) are skipped similarly. The application always starts.

**When to use:** Any startup task that processes external files where authoring errors are possible and must not block the system.

**Implementation note:** Return a sealed class or `Result<T>` from validation. Accumulate failures in a list and log them all at the end of the load cycle, not one-at-a-time.

---

## Catalog Table Design

### Entity Hierarchy (mirrors workspace entity domain, key-based not UUID-based)

```
manifest_catalog                  (top-level entry per manifest file)
├── key VARCHAR UNIQUE            (slug: "saas-startup", "hubspot", "customer")
├── type VARCHAR                  (MODEL, TEMPLATE, INTEGRATION)
├── name VARCHAR
├── description TEXT
├── created_at, updated_at TIMESTAMPTZ

catalog_entity_types              (one row per entity type per manifest)
├── id UUID
├── catalog_id UUID FK → manifest_catalog.id
├── key VARCHAR                   (entity type key within manifest: "customer", "hubspot-contact")
├── schema JSONB                  (EntityTypeSchema format: UUID-key map)
├── display_name_singular VARCHAR
├── display_name_plural VARCHAR
├── icon_type VARCHAR
├── icon_colour VARCHAR
├── description TEXT
├── protected BOOLEAN
├── source_manifest_type VARCHAR  (MODEL, TEMPLATE, INTEGRATION — for clone behavior)
├── UNIQUE (catalog_id, key)

catalog_relationship_definitions  (mirrors relationship_definitions, key-based not UUID FK)
├── id UUID
├── catalog_id UUID FK → manifest_catalog.id
├── relationship_key VARCHAR       (stable key declared in manifest)
├── source_entity_type_key VARCHAR (references catalog_entity_types.key within same catalog)
├── name VARCHAR
├── icon_type VARCHAR, icon_colour VARCHAR
├── allow_polymorphic BOOLEAN
├── cardinality_default VARCHAR
├── protected BOOLEAN
├── UNIQUE (catalog_id, relationship_key)

catalog_relationship_target_rules (mirrors relationship_target_rules)
├── id UUID
├── relationship_definition_id UUID FK → catalog_relationship_definitions.id
├── target_entity_type_key VARCHAR (string key, not UUID — resolved at clone time)
├── semantic_type_constraint VARCHAR
├── cardinality_override VARCHAR
├── inverse_visible BOOLEAN
├── inverse_name VARCHAR

catalog_entity_type_semantic_metadata
├── id UUID
├── catalog_id UUID FK → manifest_catalog.id
├── entity_type_key VARCHAR
├── target_type VARCHAR           (ATTRIBUTE, RELATIONSHIP — using existing SemanticMetadataTargetType)
├── target_key VARCHAR            (attribute key or relationship_key — string, not UUID)
├── definition TEXT
├── classification VARCHAR
├── tags JSONB
├── UNIQUE (catalog_id, entity_type_key, target_type, target_key)

catalog_field_mappings            (integrations only)
├── id UUID
├── catalog_id UUID FK → manifest_catalog.id
├── entity_type_key VARCHAR
├── source_field VARCHAR
├── target_attribute_key VARCHAR  (attribute key in catalog_entity_types.schema)
├── transform JSONB               (transform definition)
```

---

## Integration with Existing Layered Architecture

### How new components slot into the existing layers

| Existing Layer | New Catalog Additions | Notes |
|----------------|-----------------------|-------|
| `entity.{domain}` | `entity.catalog.*` — 6 new JPA entities | No `AuditableEntity`, no `SoftDeletable`. Follow `IntegrationDefinitionEntity` pattern. |
| `repository.{domain}` | `repository.catalog.*` — 6 new repositories | Standard `JpaRepository<Entity, UUID>`. Add custom finders for `findByCatalogId(catalogId)`, `findByKey(key)`. |
| `service.{domain}` | `service.catalog.*` — 7 new services | `ManifestLoaderService` is startup-only. `ManifestCatalogService` is the only read-path service. No `@PreAuthorize` — catalog is global. No `ActivityService` calls — loader is system, not user. |
| `controller.{domain}` | None in this milestone | REST API deferred to future milestone. |
| `enums.{domain}` | `enums.catalog.ManifestType` | MODEL, TEMPLATE, INTEGRATION |
| `db/schema/01_tables/` | `catalog.sql` | All 6 catalog tables in one file. No RLS. No workspace FK. No user FK. |

### No new dependencies on existing workspace-scoped services

The catalog pipeline has **zero dependencies on workspace-scoped services** (`EntityTypeService`, `WorkspaceService`, etc.). It reads from the filesystem and writes to catalog-specific tables. The existing `SchemaService` bean may be reused for its JSON Schema validation infrastructure (it already uses `networknt/json-schema-validator`).

### Cross-domain boundary: ManifestCatalogService is the public API

Future consumers (clone service, REST controllers) depend only on `ManifestCatalogService`. They do not access catalog repositories directly. This mirrors how `IntegrationDefinitionService` is the public surface for the integration catalog.

---

## Suggested Build Order (Phase Dependencies)

Build order is dictated by hard dependency constraints:

**Phase 1 — Foundation (no code dependencies)**
- Database migration: `catalog.sql` with all 6 tables
- JPA entity classes: 6 `entity.catalog.*` entities
- Repositories: 6 `repository.catalog.*` repositories
- `ManifestType` enum
- Directory scaffolding: `models/`, `templates/`, `integrations/` with README
- JSON Schema files: `schemas/*.schema.json`
- Test fixture manifests in `src/test/resources/manifests/`

**Phase 2 — Loader pipeline (depends on Phase 1 repositories + schemas)**
- `ManifestScanService` (pure filesystem, no DB dependency)
- `ManifestValidatorService` (depends on schema files + Jackson)
- `RefResolutionService` (pure in-memory, no DB dependency)
- `RelationshipNormalizationService` (pure transformation, no DB dependency)
- `ManifestUpsertService` (depends on all repositories from Phase 1)
- `ManifestLoaderService` (orchestrator, depends on all Phase 2 services)
- Unit tests for each service in isolation

**Phase 3 — Read surface + integration tests (depends on Phase 1 + 2)**
- `ManifestCatalogService` (depends on repositories from Phase 1)
- Integration tests: full startup cycle, re-load idempotency, manifest removal reconciliation

**Why this order:** JPA entities and repositories must exist before any service can write to them. The scan/validate/resolve services have no DB dependencies and can be built and unit-tested without a running database. The upsert service requires all entities and repositories. The catalog query service is last because it is pure read-only and has no upstream dependencies from the loader pipeline.

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Reading Models from the Database During Template Resolution

**What people do:** Save model manifests to the database first, then query them back during template `$ref` resolution.

**Why it's wrong:** Introduces a database round-trip inside the load pipeline, creates a dependency on write-ordering within a transaction, and makes unit testing the resolver require a real database.

**Do this instead:** Build the in-memory `modelIndex` map from validated model `JsonNode`s before touching the database. Resolution is a pure in-memory transformation. Only the final resolved output goes to the database.

### Anti-Pattern 2: One Monolithic `ManifestLoaderService` Doing Everything

**What people do:** Put scan, validate, resolve, normalize, and upsert logic all inside `onApplicationReady()` in a single class.

**Why it's wrong:** Violates the "one responsibility per service" rule in CLAUDE.md. Makes unit testing impossible (can't test normalization without triggering filesystem scans). Inflates the service to a god class.

**Do this instead:** Each distinct concern (scan, validate, resolve, normalize, upsert) is its own service. `ManifestLoaderService` is the orchestrator only — it calls these services in sequence, holds no logic of its own.

### Anti-Pattern 3: Halting Startup on Manifest Errors

**What people do:** Throw an exception when a manifest fails validation, preventing application startup.

**Why it's wrong:** A single malformed manifest file authored during development should not take down the service. The startup-tolerance requirement is explicit: "Invalid manifests log warning, are skipped, don't block startup."

**Do this instead:** Return a discriminated result from validation (`ValidManifest` / `InvalidManifest`). Filter invalid results out, log all failures together at the end, continue loading valid manifests.

### Anti-Pattern 4: Using `AuditableEntity` or `SoftDeletable` for Catalog Entities

**What people do:** Extend catalog JPA entities from `AuditableEntity` because all other entities do.

**Why it's wrong:** `AuditableEntity` uses Spring Security's `AuditingEntityListener` to populate `created_by` and `updated_by` from the current authentication context. At startup time (during `ApplicationReadyEvent`), there is no security context — the populate will fail or set nulls.

**Do this instead:** Follow the `IntegrationDefinitionEntity` precedent: declare `created_at` and `updated_at` as plain `@Column` fields with `ZonedDateTime.now()` defaults. No auditing listener. No soft-delete columns. Stale entries are hard-deleted during reconciliation.

### Anti-Pattern 5: Writing Catalog Relationships with UUID FKs to `entity_types`

**What people do:** Resolve catalog relationship `source`/`target` keys to UUIDs from the workspace `entity_types` table and store UUID FK references.

**Why it's wrong:** Catalog tables are global blueprints — they have no workspace affiliation. The UUID resolution step (key → UUID) happens at clone time, not at catalog load time. Premature UUID resolution would require cross-domain database joins during startup and would make the catalog tables workspace-dependent.

**Do this instead:** `catalog_relationship_definitions.source_entity_type_key` and `catalog_relationship_target_rules.target_entity_type_key` store string keys. UUID resolution is a clone-time concern, not a catalog concern.

---

## Scalability Considerations

This system loads manifests at startup, not per-request. Scaling concerns are different from typical API scaling.

| Concern | Now (< 20 manifests) | Future (100+ manifests) | Mitigation |
|---------|---------------------|------------------------|-----------|
| Startup load time | Negligible — ~6 tables × 20 rows | May add 1-5 seconds | Add checksum-based skip: skip upsert if manifest file hash unchanged since last load |
| Memory (model index) | < 1 MB | Still < 50 MB even at 1000 models | No action needed — in-memory map is appropriate |
| DB transaction size | Single transaction is fine | Single transaction is fine | One `@Transactional` on the upsert orchestrator; JPA flush handles batching |
| Test isolation | No concern | No concern | Test manifests in `src/test/resources/` are self-contained |

---

## Sources

- **ADR-004:** `/docs/system-design/decisions/ADR-004 Declarative-First Storage for Integration Mappings and Entity Templates.md` — authoritative design decision (HIGH confidence)
- **PROJECT.md:** `/core/.planning/PROJECT.md` — explicit requirements and out-of-scope boundaries (HIGH confidence)
- **CLAUDE.md:** `/core/CLAUDE.md` — coding standards, architecture rules, service design patterns (HIGH confidence)
- **`IntegrationDefinitionEntity.kt`** — existing global catalog pattern: no `AuditableEntity`, no RLS, `createdAt`/`updatedAt` as plain columns (HIGH confidence — direct codebase source)
- **`IntegrationDefinitionService.kt`** — read-only service pattern for global catalog (HIGH confidence)
- **`db/schema/README.md`** — SQL file naming and execution order conventions (HIGH confidence)
- **`EntityTypeEntity.kt`, `RelationshipDefinitionEntity.kt`, `EntityTypeSemanticMetadataEntity.kt`** — workspace-scoped equivalents whose structure the catalog tables mirror with key-based references instead of UUID FKs (HIGH confidence)
- **Spring Boot `ApplicationReadyEvent` documentation** — correct hook for post-startup DB writes (HIGH confidence, well-established Spring pattern)

---

*Architecture research for: Declarative Manifest Catalog and Consumption Pipeline*
*Researched: 2026-02-28*
