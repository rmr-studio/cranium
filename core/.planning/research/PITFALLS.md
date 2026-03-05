# Pitfalls Research

**Domain:** Declarative manifest catalog and startup loading pipeline (Spring Boot 3.5.3 / Kotlin / JPA / PostgreSQL)
**Researched:** 2026-02-28
**Confidence:** HIGH — derived from deep codebase analysis, first-principles reasoning about the specific complexity areas called out in PROJECT.md, and verified against existing patterns in the codebase

---

## Critical Pitfalls

### Pitfall 1: @Transactional on ApplicationReadyEvent Listener Causes Spring Context Failure

**What goes wrong:**
If `ManifestLoaderService` implements `ApplicationListener<ApplicationReadyEvent>` and its `onApplicationEvent` method is annotated with `@Transactional`, Spring's AOP proxy wraps the class — but at `ApplicationReadyEvent` time, the transaction proxy infrastructure may not be fully initialized, causing either a proxy bypass (no transaction created) or a `BeanCurrentlyInCreationException` if there is any circular dependency in the loader's dependency graph.

**Why it happens:**
Spring event listeners that are also `@Transactional` require the full AOP proxy chain to be active. During `ApplicationReadyEvent`, the context is fully started but event handling still occurs on the main thread in the same invocation chain that calls `SpringApplication.run()`. Developers annotate the listener method with `@Transactional` directly without realizing the transaction must be initiated by a *separate* bean method, not the listener callback itself.

**How to avoid:**
Split the concerns. `ManifestLoaderService` handles the event and delegates to a separate `ManifestUpsertService` (or internal `@Transactional` private methods called from the service itself, which works because Spring AOP proxies self-invocation through the bean, not `this`). The correct pattern:
```kotlin
@Service
class ManifestLoaderService(...) : ApplicationListener<ApplicationReadyEvent> {
    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        // Not @Transactional here — delegates to the service layer
        manifestUpsertService.loadAll()
    }
}

@Service
class ManifestUpsertService(...) {
    @Transactional
    fun loadAll() { ... }
}
```
Or use `@EventListener` on a method in a different bean entirely, keeping the loader service free of AOP complexity.

**Warning signs:**
- The loader runs but database changes are silently rolled back with no error (transaction not committed)
- `BeanCurrentlyInCreationException` at startup referencing the loader service
- Any `@Transactional` annotation placed directly on `onApplicationEvent`

**Phase to address:** Database Layer + JPA Entities phase (wherever `ManifestLoaderService` is first introduced)

---

### Pitfall 2: $ref Resolution Resolves Against networknt json-schema-validator 1.0.83 Classpath, Not Filesystem

**What goes wrong:**
The project uses `com.networknt:json-schema-validator:1.0.83`. In this version, `$ref` URIs are resolved against the classpath URI scheme by default. If manifest JSON Schema files use `$ref: "models/contact.schema.json"` (relative filesystem paths), the validator will fail to resolve them at runtime because the classpath root is not the manifest directory.

This is distinct from the *business-level* `$ref` in manifest content (e.g., template referencing a model). Both the JSON Schema validation step and the manifest content parsing step have separate `$ref` resolution needs that must be handled independently.

**Why it happens:**
Developers conflate two different `$ref` systems:
1. JSON Schema `$ref` (used in `.schema.json` files to cross-reference sub-schemas)
2. Application-level `$ref` in manifest content (e.g., `"$ref": "models/contact"` referencing another manifest by key)

The validator only handles (1). Item (2) is entirely custom application logic. When manifest content uses `$ref`, the loader must resolve it manually before passing the manifest to the JSON Schema validator.

**How to avoid:**
- For JSON Schema validation: embed sub-schemas inline in the master schema file, or register them explicitly via `JsonSchemaFactory` with a custom URI fetcher. Do not rely on automatic `$ref` resolution against the filesystem.
- For manifest content `$ref`: implement a dedicated `RefResolver` that operates on the parsed JSON before validation or upsert. Build a key→manifest index of all loaded models first, then resolve `$ref` pointers using string key lookup, not URI resolution.
- Keep the two `$ref` concepts in separate, clearly named classes: `ManifestRefResolver` (application content) vs. the existing `SchemaService` (structural JSON Schema validation).

**Warning signs:**
- `SchemaValidationException` with message about unresolvable `$ref` during startup
- Templates that reference models validate correctly in unit tests (where schemas are inlined) but fail at runtime
- Test JSON Schema files have all definitions inlined while production schemas use `$ref`

**Phase to address:** `$ref` Resolution Engine phase

---

### Pitfall 3: Non-Idempotent Upsert Due to Missing Unique Constraint on Child Rows

**What goes wrong:**
The upsert keyed on `manifest_catalog.key` correctly handles the parent catalog entry. However, child rows (catalog attributes, catalog relationships, catalog relationship target rules, catalog semantic metadata) lack unique constraints matching the upsert logic. On re-load, the loader inserts new child rows without deleting the old ones, resulting in duplicate children after every restart.

**Why it happens:**
The "upsert" for parent rows uses `ON CONFLICT (key) DO UPDATE` (or a find-or-create pattern). But child tables are typically cleared and re-inserted per parent load. The bug occurs when the delete-children step is skipped, either because the developer assumes `saveAll` is idempotent or because the child delete is not inside the same transaction as the parent upsert.

**How to avoid:**
For each parent catalog entry upsert, within the same transaction:
1. Upsert the parent by `key` (INSERT ... ON CONFLICT DO UPDATE or find-then-update)
2. Delete ALL existing child rows for that parent ID before re-inserting
3. Insert the new set of child rows

This is the correct reconciliation pattern. The delete step must be within the transaction so partial failures leave no orphaned children. Alternatively, track a `version_hash` on the parent and skip child reconciliation when the hash hasn't changed — but start with delete-and-reinsert until performance is a demonstrated issue.

**Warning signs:**
- Duplicate attributes or relationships appear in catalog query results after a second startup
- Child count grows linearly with the number of application restarts
- Unit tests pass (they run once) but integration tests with repeated loads fail

**Phase to address:** Idempotent Upsert + Full Reconciliation phase

---

### Pitfall 4: Reconciliation Deletes Catalog Entries That Are Referenced by Workspace Clones

**What goes wrong:**
The reconciliation step removes catalog entries for manifests no longer on disk. If the clone service (future work) has already created workspace-scoped copies referencing a catalog entry by ID or key, deleting the catalog entry breaks those references. Even with foreign keys disabled (catalog tables are global with no FK to workspace tables), the key-based references in clone metadata become dangling strings.

**Why it happens:**
Reconciliation is designed to keep the catalog clean, but it doesn't account for downstream consumers that have already used a catalog entry. Treating reconciliation as "delete anything not currently on disk" ignores the catalog's role as a shared reference point.

**How to avoid:**
Even though the clone service is out of scope for this milestone, design the reconciliation step to be clone-safe from the start:
- Add a `source_manifest_key` column to workspace entity types at clone time (the `entity_types` table already has `key` — the two new columns mentioned in PROJECT.md are the hook for this)
- On reconciliation, if a catalog entry has any workspace entity types referencing its key, mark it as `deprecated = true` rather than deleting it
- Only hard-delete catalog entries that have no downstream references (or provide a `--force` flag for manual cleanup)

For this milestone (no clone service), the simplest safe approach is: **never hard-delete catalog entries in production reconciliation**. Soft-delete them (`deleted = true`). This matches the `SoftDeletable` pattern already used across the codebase.

**Warning signs:**
- The reconciliation logic uses hard DELETE SQL
- No `deleted`/`deprecated` column on catalog tables
- Reconciliation tests don't verify downstream reference safety

**Phase to address:** Database schema phase (add soft-delete from the start) + Reconciliation phase

---

### Pitfall 5: Shallow Merge Silently Drops Fields That Exist in the Base Model But Not in the Extending Template

**What goes wrong:**
The `extend` merge is specified as "shallow, additive — new attributes added, existing untouched, no deletion semantics." But if the merge implementation iterates the *template's* attribute map and overwrites the *base model's* attribute map with only the fields present in the template, base-model-only fields are silently dropped. The resulting merged entity type is missing attributes that were never in the template.

**Why it happens:**
Developers write `mergedSchema = baseSchema + templateSchema` (Kotlin map merge), which in Kotlin's `+` operator for maps means template values overwrite base values for shared keys, but base-only keys are preserved. However, if they instead do `templateSchema + baseSchema` (reversed), base values overwrite template values for shared keys. The direction of merge determines which "wins" on conflict, and getting this backwards produces wrong but non-obvious results.

The second mistake is operating on the serialized JSON map (Map<String, Any>) rather than on the typed schema model. Operating on raw JSON loses type safety and makes it easy to accidentally merge the wrong level of the object tree (e.g., merging the attribute map at the schema level rather than at the `properties` level).

**How to avoid:**
Specify the merge contract precisely before implementation and encode it as a test:
- Base model attributes: retained as-is
- Template attributes with same key as base: base wins (the `extend` is additive, not overriding)
- Template attributes with new keys: added to the merged result
- The merge operates on the `properties` map of the schema, not the top-level schema object

Encode this in a unit test before writing the merge code:
```
given base model with attributes [name, industry, revenue]
and template extending with attributes [revenue (overridden), website (new)]
expect merged result: [name, industry, revenue (base version, not template), website]
```

**Warning signs:**
- Merge implementation uses `+` operator on Map without a comment clarifying direction
- No unit test for "template tries to override base attribute, base should win"
- Merge is implemented at the JSON string level rather than on typed Kotlin objects

**Phase to address:** `extend` Merge Logic phase

---

### Pitfall 6: Loading Order Not Enforced by Code, Only by Documentation

**What goes wrong:**
The requirement is "models before templates." If the loader discovers files using `Files.walk()` or `ClassPathResource` directory listing, the order is filesystem-dependent (alphabetical on Linux, inode order on some JVMs, classpath-jar order in production Docker images). A template manifest may be processed before its referenced models, causing `$ref` resolution to fail with "model not found" even though the model file exists.

**Why it happens:**
Developers test locally where `models/` alphabetically precedes `templates/`, so the order happens to be correct. In production Docker images where resources are packed into a JAR, `ClassPathResource` directory listing order is undefined and may differ from the local filesystem order. Integration tests that run against `src/test/resources` also happen to have alphabetical order that matches the requirement.

**How to avoid:**
Make the loading order explicit in code, not assumed from filesystem ordering:
```kotlin
// Explicit, documented order
val loadOrder = listOf("models", "templates", "integrations")
loadOrder.forEach { directory -> loadDirectory(directory) }
```
Process all models to completion (parse + validate + upsert) before starting templates. Build the model index (key → parsed model) after loading models, and pass it as a parameter to the template loader. This makes the dependency explicit and testable.

**Warning signs:**
- Loader uses `Files.walk()` or `ResourcePatternResolver` with `**/*.json` without explicit directory ordering
- Unit tests always have models defined before templates in the test fixture
- Loading works locally but fails after Docker build

**Phase to address:** `ManifestLoaderService` core loading phase

---

### Pitfall 7: Transaction Spanning the Entire Startup Load Cycle Blocks the Application

**What goes wrong:**
Wrapping the entire `ManifestLoaderService.loadAll()` in a single `@Transactional` boundary means the transaction holds locks on all catalog tables for the duration of the full load. On a cold start with 50+ manifests, this takes seconds. Any concurrent startup health check, readiness probe, or background job that tries to read from catalog tables will block or time out.

**Why it happens:**
Developers reach for `@Transactional` on the top-level load method to ensure "all or nothing" semantics. This is correct in intent but wrong in scope. A single transaction holding table-level write locks for the full load duration degrades startup performance and can cause health check failures in Kubernetes environments that expect the readiness probe to respond quickly.

**How to avoid:**
Use per-manifest transactions, not a single global transaction for the whole load:
```kotlin
fun loadAll() {
    // No @Transactional here — no global lock
    manifestFiles.forEach { file ->
        try {
            upsertSingleManifest(file) // @Transactional on this method
        } catch (e: Exception) {
            logger.warn { "Skipping manifest ${file.name}: ${e.message}" }
        }
    }
}

@Transactional
fun upsertSingleManifest(file: ManifestFile) {
    // Transaction per manifest: parent upsert + child delete + child reinsert
}
```
This means a single malformed manifest doesn't roll back the entire load, and lock duration per transaction is minimal.

**Warning signs:**
- `@Transactional` on `loadAll()` or `onApplicationEvent()`
- Load takes more than 500ms and health checks are configured with tight timeouts
- No per-manifest try/catch in the load loop

**Phase to address:** `ManifestLoaderService` + Transaction Boundaries phase

---

### Pitfall 8: Key Uniqueness Across Manifest Types Not Validated at Load Time

**What goes wrong:**
A model manifest with `key: "contact"` and a template manifest with `key: "contact"` are different catalog types, but if the `manifest_catalog` table uses a single `key` unique constraint without a `type` discriminator, the second load will silently overwrite the first (or fail with a unique constraint violation that is logged and suppressed, leaving the catalog in a half-loaded state).

**Why it happens:**
The upsert key is designed for idempotent re-loading of the *same* manifest. Developers assume keys are unique within their type (model keys, template keys, integration keys are separate namespaces in their mental model), but the database schema uses a flat `key` column without a `(key, type)` composite unique constraint.

**How to avoid:**
The `manifest_catalog` table unique constraint must be `UNIQUE(key, manifest_type)`, not just `UNIQUE(key)`. The upsert logic keys on `(key, manifest_type)` accordingly. Document in the schema that keys are unique *within* a manifest type, not globally.

**Warning signs:**
- `manifest_catalog` table DDL has `UNIQUE(key)` without a type discriminator
- No test fixture that loads a model and a template with the same key to verify they coexist

**Phase to address:** Database schema phase

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Hard-delete on reconciliation | Simpler reconciliation logic | Breaks future clone references, no audit trail | Never — use soft-delete from day one to match codebase pattern |
| Single global transaction for full load | "All or nothing" semantics | Long lock duration, health check failures, one bad manifest blocks all others | Never — use per-manifest transactions |
| Inlining all JSON Schema validation schemas | No filesystem $ref resolution needed | Schema files become large and hard to author | Acceptable for this milestone; extract to linked files if schemas grow large |
| Skipping version hash check (always reload) | Simpler implementation | Extra DB writes on every startup even when nothing changed | Acceptable for MVP; add hash check if startup time becomes a concern at scale |
| VARCHAR key references instead of UUID FKs in catalog relationships | Avoids UUID instability across reloads | Cannot use database-level referential integrity | Acceptable — this is the documented architecture decision for this milestone |
| Loading from classpath resources only (no external directory) | No file path configuration needed | Cannot add manifests without a code deployment | Acceptable for now; external directory support is future work |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| networknt json-schema-validator 1.0.83 | Passing a `$ref` URI that points to a filesystem path; the factory tries to resolve via classpath | Register all referenced sub-schemas explicitly with `schemaFactory.getSchema()` calls before validating, or embed definitions inline using JSON Schema `$defs` |
| networknt json-schema-validator 1.0.83 | Using `SpecVersion.VersionFlag.V7` when manifest schemas are authored with `$defs` (a 2019-09 feature) | Match the spec version to the schema dialect: `V201909` for `$defs`, `V7` for `definitions`. The project `SchemaService` already uses `V201909` — use the same |
| Spring JPA / Hibernate | Calling `repository.save()` in a loop inside an `ApplicationReadyEvent` listener that is not `@Transactional` | Each `save()` opens and closes its own transaction, which is correct for per-manifest isolation, but Hibernate's first-level cache accumulates all objects in memory. Call `entityManager.clear()` periodically or use `saveAndFlush()` to prevent OOM on large loads |
| Kotlin `data class` with JPA `@Entity` | Data class `equals`/`hashCode` based on all fields causes Hibernate identity tracking issues during the upsert flow when the entity is modified after load from DB | Follow the existing project pattern: use `data class` for JPA entities as documented in CLAUDE.md, but be careful not to rely on `==` equality for entity identity during the upsert; always compare by `key` string, not by entity object identity |
| PostgreSQL JSONB upsert | Using `INSERT ... ON CONFLICT DO UPDATE SET schema = EXCLUDED.schema` for JSONB columns loses Hibernate's dirty-tracking — the `@Type(JsonBinaryType::class)` annotation requires the entity to be loaded then mutated, not bypassed via native SQL | Use the find-then-update pattern: `repository.findByKey(key) ?: newEntity` then mutate and `save()`. Native SQL upsert bypasses Hibernate's type converters |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| N+1 queries during child row validation | Startup is slow; each manifest's key validation fires individual SELECT queries to check if referenced model keys exist | Build an in-memory index of all loaded model keys after the models phase; validate template `$ref` keys against the index, not against the database | With > 20 template manifests each referencing 3–5 models |
| Hibernate session cache accumulation | Startup memory spikes; OOM on large manifest sets | Call `entityManager.clear()` after each manifest batch, or use Spring Data `saveAllAndFlush()` | With > 100 manifest files per load cycle |
| File scanning on every request to catalog API | `ManifestCatalogService` re-scans the filesystem on `getAvailableTemplates()` | Catalog is loaded at startup into the database; `ManifestCatalogService` queries the DB, never the filesystem | Any load — this should not happen if the architecture is correct, but easy to introduce if the service is confused with the loader |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Manifest files read from a user-controllable path | Path traversal, loading arbitrary JSON from the filesystem | Manifest directories must be defined by application configuration at startup, not derived from request parameters. The loader paths are fixed constants in the service, not runtime inputs |
| Catalog tables accessible without workspace scope via global API | Manifests are global/public by design, but if catalog query service accidentally exposes sensitive field mappings (integration auth config patterns) | Keep `ManifestCatalogService` read-only and scope its API contract to template/model metadata only. Integration auth config belongs in `integration_definitions.auth_config`, not in catalog relationship `field_mappings` |
| JSON Schema validation skipped in "warn and continue" mode used as attack surface | A crafted manifest that passes validation suppression could persist malformed JSONB to the database | The warn-and-skip behavior applies to structurally invalid manifests (unparseable JSON, missing required fields). Never persist a manifest that fails JSON Schema validation — skip it entirely. Only skip when `isValid = false`; never `isValid = unknown` |

---

## "Looks Done But Isn't" Checklist

- [ ] **Idempotent upsert:** First load and second load produce identical database state — verify by running the load twice in an integration test and asserting row counts are equal, not doubled
- [ ] **Reconciliation:** Remove a manifest file between two loads and assert the catalog entry is soft-deleted (not hard-deleted, and not still present) — verify by checking the `deleted` flag, not just absence from query results
- [ ] **$ref resolution:** Template manifest with `$ref` to a model that does not exist on disk — verify that the template is skipped with a warning log, not that it panics or corrupts the parent catalog entry
- [ ] **extend merge direction:** Template attribute with same key as base model attribute — verify that the base model attribute's definition wins (not the template's), confirming "additive only, no override" semantics
- [ ] **Loading order:** Rename `models/` directory to `zmodels/` and verify templates still load correctly (loader enforces explicit order, not alphabetical)
- [ ] **Transaction isolation:** Introduce a deliberate parse error in manifest #3 of 5 — verify that manifests #1, #2, #4, #5 are upserted successfully (per-manifest transactions, not global rollback)
- [ ] **Key namespace:** Load a model with key `contact` and a template with key `contact` — verify both exist in the catalog, keyed by `(key, manifest_type)`, not that one overwrites the other
- [ ] **Startup tolerance:** Introduce a manifest with valid JSON but invalid schema (missing required field) — verify application starts successfully with a WARN log, not a startup failure

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Global transaction causing startup failure | LOW | Remove `@Transactional` from `loadAll()` / `onApplicationEvent()`; add it to the per-manifest upsert method |
| Duplicate child rows from non-idempotent upsert | MEDIUM | Write a one-time SQL migration to deduplicate child rows by `(parent_id, key)` unique constraint; add the delete-before-reinsert logic to the loader |
| Hard-delete reconciliation that broke clone references | HIGH | Restore deleted catalog entries from git history of manifest files; re-run the loader; add soft-delete to schema before the clone service ships |
| Wrong merge direction (template overrides base) | MEDIUM | Identify affected templates in the catalog; correct the merge logic; wipe and re-run the loader (idempotent, so this is safe) |
| Filesystem-order loading failure in Docker | LOW | Replace unordered `Files.walk()` with explicit ordered directory load; re-run |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| @Transactional on ApplicationReadyEvent listener | Database Layer + ManifestLoaderService scaffolding | Unit test: mock ApplicationReadyEvent, assert `loadAll()` is called on a separate `@Transactional` bean method |
| $ref resolution against classpath not filesystem | `$ref` Resolution Engine | Unit test: create a `RefResolver` that uses key-based lookup; assert resolution fails gracefully when model key is missing |
| Non-idempotent child row upsert | Idempotent Upsert + Reconciliation | Integration test: run load twice, assert `SELECT COUNT(*)` from child tables equals expected count, not 2x |
| Reconciliation deletes referenced catalog entries | Database Schema (add `deleted` column) | Integration test: mark manifest removed, run reconciliation, assert `deleted = true` not `COUNT = 0` |
| Shallow merge drops base model fields | `extend` Merge Logic | Unit test: assert base-only attributes survive merge; assert base attribute wins on key conflict |
| Loading order not enforced by code | ManifestLoaderService core loading | Unit test: load templates before models via explicit parameter; assert graceful error |
| Global startup transaction blocking application | Transaction Boundaries (per-manifest) | Integration test with deliberate parse error on manifest #3; assert manifests #1 and #2 are persisted |
| Key uniqueness not enforced across manifest types | Database Schema (composite unique constraint) | Schema DDL review: assert `UNIQUE(key, manifest_type)` constraint exists |

---

## Sources

- Codebase analysis: `SchemaService` (`com.networknt:json-schema-validator:1.0.83`, `SpecVersion.VersionFlag.V201909`) — confirms the exact validator version and spec in use
- Codebase analysis: `IntegrationDefinitionEntity` — confirms the global catalog pattern (no `AuditableEntity`, no `workspace_id`) that the manifest catalog must follow
- Codebase analysis: `EntityQueryIntegrationTestBase` — confirms the per-manifest transactional isolation pattern and Testcontainers integration test base
- Codebase analysis: `application.yml` (`flyway.enabled: false`) — confirms schema is managed externally; catalog DDL follows the `db/schema/` SQL file convention
- Codebase analysis: `EntityTypeEntity` unique constraint `(workspace_id, key)` — confirms composite unique constraint pattern to follow for `(key, manifest_type)` in catalog tables
- Codebase analysis: `AuditableSoftDeletableEntity` — confirms the soft-delete pattern used across the codebase; new catalog tables should follow this for reconciliation
- First-principles: Spring AOP proxy and `ApplicationReadyEvent` ordering behavior (HIGH confidence — stable Spring Framework behavior since Spring 4)
- First-principles: PostgreSQL MVCC and transaction lock duration impact on application startup readiness (HIGH confidence)

---
*Pitfalls research for: Declarative Manifest Catalog and Consumption Pipeline*
*Researched: 2026-02-28*
