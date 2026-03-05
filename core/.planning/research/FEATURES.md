# Feature Research

**Domain:** Declarative manifest catalog and consumption pipeline (Spring Boot / Kotlin backend)
**Researched:** 2026-02-28
**Confidence:** HIGH — derived directly from PROJECT.md requirements, codebase analysis, and established patterns in configuration management systems (Spring Boot's own ConfigurationProperties loading, Kubernetes operator reconciliation loops, Helm chart registries, and NPM package manifests)

---

## Feature Landscape

### Table Stakes (Users Expect These)

These are the features without which the manifest catalog loader is broken or useless. Missing any of these means the system does not fulfil its stated purpose.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Startup loading via ApplicationReadyEvent** | The catalog must be available before any request is served. Loading on startup (not lazily) is the only safe guarantee. | LOW | `@EventListener(ApplicationReadyEvent::class)` on `ManifestLoaderService`. Fires after context is fully initialized, safe for JPA writes. |
| **Idempotent upsert keyed on `manifest_catalog.key`** | Loader runs on every startup. Without idempotency, re-deployment doubles the catalog. UPSERT semantics (insert or update-in-place) are the only correct behaviour. | MEDIUM | `manifest_catalog.key` is the natural key. JPA `save()` with `findByKey()` + conditional create/update is cleaner than PostgreSQL `ON CONFLICT` for this ORM stack. |
| **JSON Schema validation at load time** | Malformed manifests must be caught before they reach the database. Garbage in = garbage catalog. Without this, a typo in one manifest silently corrupts downstream consumption. | MEDIUM | Use Jackson + `networknt/json-schema-validator` (already a common dependency in Spring ecosystems) or `everit-org/json-schema`. Validate the parsed JSON against a JSON Schema file before processing. |
| **Invalid manifest skip with warning log — no startup failure** | Application must start successfully even with bad manifests. A broken manifest should not block the workspace creation flow for all tenants. | LOW | Wrap each manifest load in try/catch. Log at WARN with manifest path and error. Continue to next manifest. |
| **Directory-based manifest discovery** | The loader must find manifest files automatically from configured directories. Hard-coding paths is not tenable when new manifests are added. | LOW | Spring `ResourcePatternResolver` or `ClassPathResource` for classpath manifests; `File` walking for filesystem manifests. Discover all `.json` files in `models/`, `templates/`, `integrations/` subdirectories. |
| **Dependency-ordered loading (models before templates)** | Templates reference models via `$ref`. If templates load before models, reference resolution fails or requires a two-pass. Ordering is a hard requirement for correctness. | LOW | Process directories in explicit order: `models/` → `templates/` → `integrations/`. Within a directory, alphabetical order is sufficient (no cross-manifest dependencies within a type). |
| **`$ref` resolution for shared model references** | Templates include entity models from the shared model library via `$ref: "models/contact.json#/entity_types/contact"`. Without resolution, templates are incomplete definitions. | MEDIUM | Resolve `$ref` strings to already-loaded model entries from the in-memory catalog state (not from the database — models are loaded first in the same run). Resolution must happen before writing the template to the DB. |
| **Full reconciliation on startup (remove stale catalog entries)** | Manifests deleted from disk must not persist in the catalog indefinitely. Stale entries create ghost templates that appear in the UI but cannot be instantiated. | MEDIUM | After processing all found manifests, query all existing catalog entries and delete those whose `key` is not in the current manifest set. Cascade deletes handle child tables. |
| **Child table upsert for catalog entity types, attributes, and relationships** | The catalog is not a flat table — each manifest entry has child rows (entity types, attributes, relationship definitions, semantic metadata). Child upsert must be idempotent too. | HIGH | For each parent manifest upsert: 1) load existing children by parent FK, 2) upsert children keyed on their natural key (e.g., entity type `key` within the manifest), 3) delete children no longer present in the manifest. This is a full child reconciliation, not just parent. |
| **`extend` merge logic (shallow, additive)** | Template manifests can extend a shared model, adding attributes. The merge must be additive only — new attributes added, existing untouched, no deletions. | MEDIUM | Shallow merge: take base model's entity type definition, overlay template's attribute additions. Do NOT deep-merge nested objects. Do NOT delete attributes present in base but absent in template overlay. |
| **Relationship format normalization (shorthand to full format)** | Manifest authors use shorthand relationship syntax for brevity. The loader must normalize to the full `targetRules[]` format before persisting. | LOW | Detect shorthand vs. full format. Transform shorthand to `{ targetRules: [{ target: "key", cardinality: "...", ... }] }`. Fail-fast (warn + skip manifest) if neither format is recognizable. |
| **Relationship validation (key existence, cardinality enum)** | Relationship definitions reference source and target entity type keys. If a key doesn't exist in the catalog (even after `$ref` resolution), the relationship is dangling. Cardinality values must match the enum. | MEDIUM | After all entity types for a manifest are resolved, validate: (a) `sourceEntityTypeKey` exists in the manifest's resolved entity types, (b) each `targetRules[].target` key exists in the in-memory manifest state, (c) cardinality value is one of `ONE_TO_ONE`, `ONE_TO_MANY`, `MANY_TO_ONE`, `MANY_TO_MANY`. |
| **`ManifestCatalogService` — read-only query API** | Downstream consumers (future CatalogCloneService, API endpoints) need a clean service API to browse the catalog. Direct repository access from multiple call sites creates coupling. | LOW | Four methods per PROJECT.md: `getAvailableTemplates()`, `getAvailableModels()`, `getManifestByKey(key)`, `getEntityTypesForManifest(manifestKey)`. No workspace scoping — global catalog. No `@PreAuthorize`. |
| **Directory scaffolding with README** | Without directory structure and authoring documentation, the manifest format is not self-documenting. Developers cannot author new manifests correctly without guidance. | LOW | Create `manifests/models/`, `manifests/templates/`, `manifests/integrations/` directories with a `README.md` in each covering the manifest format, required fields, and examples. |
| **Test fixture manifests** | Unit and integration tests require known-good manifests covering all structural patterns. Without fixtures, tests rely on constructing manifests inline, which is fragile and verbose. | LOW | Fixtures in `src/test/resources/manifests/`: one complete model manifest, one template manifest using `$ref` + `extend`, one integration manifest. Cover edge cases: empty attributes, polymorphic relationships, multiple entity types. |

---

### Differentiators (Production Readiness)

Features that make the system robust, observable, and maintainable. Not required for functional correctness but separate a production-grade system from a prototype.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Per-manifest load result summary log** | At startup, operators need to know what loaded successfully, what was skipped, and what was removed. A summary log line per manifest (loaded / skipped / removed) makes startup observable. | LOW | After the load run completes, log a structured summary: `ManifestLoader: loaded=12, skipped=1, removed=3`. Log each skipped manifest at WARN with the validation error detail. |
| **In-memory manifest state during load (not mid-load DB reads)** | Reference resolution (`$ref`, relationship target key validation) must work against the current run's manifest set, not whatever is currently in the database from a previous run. This avoids stale-read bugs during the load phase. | LOW | Maintain a `Map<String, ResolvedManifest>` in-memory during a single load run. Populate it as models are processed. Use it for `$ref` resolution and relationship validation. Only write to DB once resolution is complete. |
| **Manifest type discriminated by directory, not field** | Requiring a `type: "model"` field in every manifest is redundant and error-prone. Manifest type is determined by directory (`models/` = model, `templates/` = template, `integrations/` = integration). | LOW | Strip the `type` field requirement from JSON Schemas. Inject the type from directory context during loading. Simpler manifest format = fewer authoring errors. |
| **Structural validation separate from semantic validation** | JSON Schema validates structure (required fields, field types, allowed enum values). Semantic validation (key references exist, cardinality is valid, no circular extends) runs after JSON Schema passes. Separate phases produce clearer error messages. | MEDIUM | Two-phase validation per manifest: Phase 1 = JSON Schema (structural). Phase 2 = semantic rules (key existence, relationship integrity). Error messages from Phase 1 cite JSON Schema path. Phase 2 errors cite logical rule. |
| **Checksum-based skip optimisation (future)** | If a manifest's content hash matches the stored hash, skip full upsert processing. Reduces DB write amplification on large catalogs with stable manifests. | MEDIUM | Compute SHA-256 of manifest file content. Store in `manifest_catalog.content_hash`. On load: if hash matches, skip child reconciliation entirely. Only implement once catalog has 20+ manifests causing perceptible startup latency. |
| **Configurable manifest directory path** | Manifests should be configurable as a classpath root or filesystem path via `application.yml`. Hardcoding classpath location makes it impossible to mount manifests from external volumes in production. | LOW | `riven.manifests.base-path: classpath:manifests/` in `application.yml`. Allow override to `file:/some/path` for container deployments with mounted manifest volumes. |
| **Loader dry-run mode (future)** | For CI validation of manifest PRs without a running database. Validates JSON Schema and semantic rules, outputs what would be loaded/removed, exits 0 or 1. | HIGH | Requires abstracting the persistence layer behind an interface. Worth doing once CI validates manifest contributions. Not for Phase 1. |

---

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem valuable but create scope creep, correctness bugs, or architectural debt at this stage.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| **Hot reload / file system watching** | Developers want manifest changes to appear without restart. | Introduces a background thread, file watcher lifecycle, partial-load windows, and cache invalidation complexity that are disproportionate to the benefit. Manifests are infrastructure, not live data. | Restart the application. In development, `./gradlew bootRun` is fast enough. Hot reload belongs in a future DX milestone if manifest authoring becomes a primary workflow. |
| **REST API catalog browsing endpoints (Phase 1)** | Frontends want to call `GET /api/v1/catalog/templates`. | Controllers, OpenAPI docs, auth decisions, and response DTOs add scope to what should be a pure backend service layer. The consuming service (CatalogCloneService) doesn't need HTTP — it calls service methods directly. | Build `ManifestCatalogService` as a clean internal service. Add REST endpoints in the next phase when the clone service is built and there is a concrete consumer. |
| **Deep recursive `extend` merge** | Template authors want to override nested attribute properties (e.g., change a label inside a base model's attribute). | Deep merge semantics are ambiguous (what wins? what merges? what deletes?), hard to document, and create surprising behaviour when base models evolve. Shallow merge is predictable. | Restrict `extend` to additive-only: add new attributes to the base entity type. If a template needs a different label on an inherited attribute, the template should declare its own entity type rather than extending. |
| **UUID-keyed references in catalog relationships (Phase 1)** | Using actual UUIDs from workspace `entity_types` seems natural since that's how workspace relationships work. | Catalog entries are blueprints, not workspace-scoped records. UUIDs are unstable across re-loads (catalog rows may be deleted and recreated). VARCHAR keys are stable across re-loads and unambiguous in manifest JSON. | Use VARCHAR keys throughout the catalog. UUID resolution happens at clone time when the catalog blueprint is instantiated into a workspace. |
| **Readonly guard clauses on EntityTypeService** | Prevent catalog-sourced entity types from being modified via the standard entity type API. | This is a downstream concern of the clone service phase, not the catalog phase. Adding guard clauses now couples the catalog to the workspace entity type service before the clone service exists. | Defer. When CatalogCloneService is built, add a `source_manifest_key` column to `entity_types` and enforce readonly semantics on that service. |
| **Generic mapping engine / field mapping interpretation** | Integrate field mapping execution into the catalog loader for integration manifests. | Out of scope per PROJECT.md. The mapping engine is a runtime concern of the sync pipeline, not a startup catalog concern. Conflating the two creates a god service. | The catalog loader stores field mapping JSON verbatim. The mapping engine reads it at sync runtime. These are separate systems. |
| **Circular extend detection beyond one level** | Templates extending templates that extend models creates a diamond problem. | Infinite loop detection adds complexity. The simpler rule is: `extend` is supported only for template extending a model. Template-extends-template is unsupported and fails validation. | Disallow template-extends-template. If a complex shared base is needed, extract it to a shared model. Document this constraint clearly in the README. |

---

## Feature Dependencies

```
[Directory Scaffolding + README]
    └──enables──> [Directory-based Manifest Discovery]
                      └──requires──> [Startup Loading via ApplicationReadyEvent]

[JSON Schema Files]
    └──enables──> [JSON Schema Validation at Load Time]
                      └──requires──> [Directory-based Manifest Discovery]
                      └──enables──> [Structural Validation Phase]
                                        └──enables──> [Semantic Validation Phase]
                                                           (key existence, cardinality, ref targets)

[Idempotent Upsert (manifest_catalog)]
    └──requires──> [Startup Loading]
    └──enables──> [Full Reconciliation (stale removal)]

[Child Table Upsert]
    └──requires──> [Idempotent Upsert (parent)]
    └──requires──> [$ref Resolution]

[$ref Resolution]
    └──requires──> [Dependency-ordered Loading (models first)]
    └──requires──> [In-memory Manifest State]

[extend Merge Logic]
    └──requires──> [$ref Resolution]
    └──requires──> [Structural Validation passes first]

[Relationship Format Normalization]
    └──requires──> [$ref Resolution] (target keys must be resolved before normalization)
    └──requires──> [Relationship Validation]

[ManifestCatalogService]
    └──requires──> [Idempotent Upsert] (catalog must exist to query)
    └──enables──> [CatalogCloneService (future)]

[Test Fixture Manifests]
    └──enables──> [Unit tests for $ref, extend, normalization]
    └──enables──> [Integration tests for full startup cycle]
```

### Dependency Notes

- **`$ref` resolution requires models to load first:** The in-memory manifest state is populated as models are processed. Templates cannot resolve their `$ref` strings unless the referenced model is already in the state map.
- **Child table upsert requires parent upsert:** A catalog entity type row cannot exist without its parent `manifest_catalog` row. The parent upsert must complete before children are written.
- **Semantic validation requires structural validation to pass:** There is no point checking that `sourceEntityTypeKey` exists if the manifest hasn't even validated that `sourceEntityTypeKey` is a non-null string.
- **Full reconciliation requires a complete manifest key set:** Reconciliation must run AFTER all manifests are processed (not per-manifest). It deletes catalog entries whose keys are absent from the final set of successfully loaded manifests.
- **Test fixtures must cover all structural patterns:** The integration test for the full startup cycle is invalid if fixtures don't exercise `$ref`, `extend`, shorthand relationships, and multi-entity-type templates.

---

## MVP Definition

### Launch With (v1 — this milestone)

The minimum set to make the catalog functional and the downstream clone service buildable in the next phase.

- [x] Database schema for all 6 catalog tables + 2 `entity_types` column additions
- [x] JPA entities for all catalog tables
- [x] `ManifestLoaderService` running on `ApplicationReadyEvent`
- [x] Directory-based discovery of `models/`, `templates/`, `integrations/`
- [x] Dependency-ordered loading (models → templates → integrations)
- [x] JSON Schema files for all 3 manifest types
- [x] JSON Schema validation at load time (warn + skip on failure, never block startup)
- [x] `$ref` resolution using in-memory manifest state
- [x] `extend` merge logic (shallow, additive only)
- [x] Relationship format normalization (shorthand → full `targetRules[]`)
- [x] Relationship validation (key existence + cardinality enum)
- [x] Idempotent upsert keyed on `manifest_catalog.key`
- [x] Child table upsert for entity types, attributes, relationships, semantic metadata
- [x] Full reconciliation — remove stale catalog entries not present on disk
- [x] `ManifestCatalogService` with 4 read-only query methods
- [x] Directory scaffolding + README authoring guidelines
- [x] Test fixture manifests (model, template with `$ref`+`extend`, integration)
- [x] Unit tests for `$ref` resolution, `extend` merge, relationship normalization, key validation
- [x] Integration tests for full startup load cycle, idempotent reload, manifest removal reconciliation

### Add After Validation (v1.x — next milestone)

Features that require the catalog to be functional first.

- [ ] `CatalogCloneService` — clone a template or install a model into a workspace
- [ ] REST API endpoints for catalog browsing (`GET /api/v1/catalog/*`)
- [ ] `readonly` guard clauses on `EntityTypeService` for catalog-sourced types
- [ ] Integration with workspace creation flow (template selection during onboarding)

### Future Consideration (v2+)

Features to defer until catalog usage patterns are established.

- [ ] Checksum-based skip optimisation — only when catalog size makes startup latency perceptible
- [ ] Dry-run mode for CI manifest validation — only when manifest authoring is a team workflow
- [ ] Hot reload via filesystem watching — only if manifests become a live-authored resource

---

## Feature Prioritization Matrix

| Feature | System Value | Implementation Cost | Priority |
|---------|--------------|---------------------|----------|
| Startup loading via ApplicationReadyEvent | HIGH | LOW | P1 |
| Idempotent upsert (parent) | HIGH | LOW | P1 |
| Child table upsert + reconciliation | HIGH | HIGH | P1 |
| JSON Schema validation + skip-on-fail | HIGH | MEDIUM | P1 |
| `$ref` resolution | HIGH | MEDIUM | P1 |
| Dependency-ordered loading | HIGH | LOW | P1 |
| `extend` merge logic | HIGH | MEDIUM | P1 |
| Relationship normalization + validation | HIGH | MEDIUM | P1 |
| ManifestCatalogService (read-only) | HIGH | LOW | P1 |
| Full reconciliation (stale removal) | HIGH | MEDIUM | P1 |
| Directory scaffolding + README | MEDIUM | LOW | P1 |
| Test fixtures + unit tests | HIGH | MEDIUM | P1 |
| Integration tests (startup cycle) | HIGH | MEDIUM | P1 |
| Configurable manifest base path | MEDIUM | LOW | P2 |
| Per-manifest load result summary log | MEDIUM | LOW | P2 |
| In-memory manifest state (vs mid-load DB reads) | MEDIUM | LOW | P2 |
| Checksum-based skip optimisation | LOW | MEDIUM | P3 |
| Dry-run mode | LOW | HIGH | P3 |
| Hot reload | LOW | HIGH | P3 |

**Priority key:**
- P1: Must have for this milestone — catalog is non-functional without it
- P2: Should have — production observability and correctness improvements
- P3: Nice to have — defer to future milestone

---

## Reference Systems Analysis

This is an internal backend system, not a consumer product. "Competitors" are architectural reference points — how similar systems in the ecosystem handle the same problems.

| Problem | Spring Boot ConfigurationProperties | Kubernetes CRD Reconciler | Helm Chart Registry | Our Approach |
|---------|------------------------------------|--------------------------|--------------------|--------------|
| Load trigger | Application startup | Watch loop (continuous) | On-demand pull | ApplicationReadyEvent (startup) |
| Idempotency | Properties are stateless | Desired state reconciliation | Immutable chart versions | UPSERT keyed on manifest `key` |
| Schema validation | Binder type-checking | OpenAPI schema in CRD spec | JSON Schema in `Chart.yaml` | JSON Schema files per manifest type |
| Reference resolution | `@Value`/`@ConfigurationProperties` binding | Cross-resource references via name | Chart dependencies (tarball) | In-memory state during load run |
| Stale removal | N/A (properties replaced entirely) | Garbage collection via owner references | Explicit uninstall | Key-based reconciliation on every startup |
| Partial failure | Fail fast (stops context load) | Retry with exponential backoff | Atomic transaction | Warn + skip invalid manifests, continue |

**Takeaway:** The "warn + skip + continue" partial failure mode is the right call for a startup loader in a multi-tenant system. Kubernetes operators retry indefinitely (they have a watch loop). Spring properties fail fast because they're structural config. Our case is more like a data seeder — one bad seed file shouldn't poison the entire application.

---

## Sources

- **Project context:** `/home/jared/dev/worktrees/template-manifestation/core/.planning/PROJECT.md` — requirements and constraints (HIGH confidence — primary source)
- **Codebase analysis:** `IntegrationDefinitionEntity.kt`, `IntegrationDefinitionService.kt`, `db/schema/01_tables/integrations.sql` — global catalog precedent and global-vs-workspace pattern (HIGH confidence — direct code inspection)
- **Codebase analysis:** `EntityTypeEntity.kt`, `EntityTypeService.kt`, `RelationshipDefinitionEntity.kt`, `RelationshipTargetRuleEntity.kt` — entity type structure and upsert patterns (HIGH confidence — direct code inspection)
- **Codebase analysis:** `EntityQueryIntegrationTestBase.kt` — integration test patterns with Testcontainers (HIGH confidence — direct code inspection)
- **Pattern reference:** Spring Boot `ApplicationReadyEvent` — fires after full context initialization, safe for JPA operations (HIGH confidence — established Spring Boot documentation pattern)
- **Pattern reference:** Kubernetes reconciliation loop — desired-state-vs-actual-state reconciliation with stale removal (MEDIUM confidence — general knowledge, well-established pattern)

---

*Feature research for: Declarative Manifest Catalog and Consumption Pipeline*
*Researched: 2026-02-28*
