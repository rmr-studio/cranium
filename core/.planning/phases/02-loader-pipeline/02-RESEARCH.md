# Phase 2: Loader Pipeline - Research

**Researched:** 2026-03-05
**Domain:** Spring Boot startup loading, classpath resource scanning, JSON Schema validation, idempotent upsert, in-memory manifest resolution
**Confidence:** HIGH

## Summary

Phase 2 builds the runtime loading pipeline that turns static manifest JSON files on the classpath into catalog database rows. The pipeline runs on every application startup, scans three directories (models, templates, integrations), validates each file against its JSON Schema, resolves `$ref` references and `extend` merges in-memory, normalizes relationship shorthand to full format, validates relationship keys, and upserts everything into the catalog tables built in Phase 1. Each manifest is processed in its own transaction so one failure does not cascade.

The codebase already has everything needed: `networknt json-schema-validator 1.0.83` for JSON Schema validation (used by `SchemaService`), Jackson `ObjectMapper` configured with Kotlin module and lenient deserialization (`FAIL_ON_UNKNOWN_PROPERTIES` disabled), 6 catalog JPA entities with repositories, and the `IntegrationDefinitionEntity` with its `stale` flag as the precedent for staleness tracking.

**Primary recommendation:** Build 4 service classes following the project's single-responsibility split: `ManifestScannerService` (classpath scan + JSON Schema validation), `ManifestResolverService` (ref resolution + extend merge + relationship normalization/validation), `ManifestUpsertService` (idempotent persistence with delete-reinsert), and `ManifestLoaderService` (orchestrator that listens to `ApplicationReadyEvent` and coordinates the pipeline). Unit test the resolver logic thoroughly; the scanner and upsert are thin wrappers around Spring/JPA.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Area 1: Missing Manifest Reconciliation**
- Add `stale BOOLEAN NOT NULL DEFAULT false` to `manifest_catalog`. Same pattern as `integration_definitions.stale`.
- Reconciliation: Start of load cycle marks all existing entries `stale = true`. Each loaded manifest upserts with `stale = false`. End of cycle: anything still stale was not on disk.
- Auto-recovery: If a previously-stale manifest reappears, upsert sets `stale = false`. Seamless recovery.
- Child rows are NOT touched when parent goes stale. `ON DELETE CASCADE` only fires on actual deletion.
- When an integration manifest goes stale, loader also sets `integration_definitions.stale = true` (matched by key = slug where type = INTEGRATION). Reverse on load.
- Severity-based logging: Missing model referenced by non-stale template = WARN. Missing model not referenced = INFO. Missing template/integration = INFO.
- Single INFO summary log at end of load cycle with counts.

**Area 2: $ref Resolution Failure Behavior**
- Unresolved `$ref` skips the ENTIRE template manifest. Log WARN with template key and unresolved path.
- Failed templates are inserted as stale (upserted with `stale = true`). No child rows persisted.
- `$ref` scope is models only. Format: `"$ref": "models/<model-key>"`. No circular references possible.
- After entity type resolution, validate that all relationship source/target keys exist in the manifest's entity type set. Missing key skips entire manifest (inserted as stale, WARN).

**Area 3: Field Mapping Format**
- ADR-004 format stored as-is in JSONB. Loader does not interpret mappings.
- Map keyed by attribute key. Every value is `{ "source": string, "transform"?: object }`. No string shorthand.
- Attribute key validation: each mapping key must exist in target entity type's attributes. Invalid keys cause the mapping entry to be skipped with WARN (does NOT skip entire manifest).
- Field mappings are optional in integration manifests.
- JSON Schema for integration must be updated to accept ADR-004 object format.

### Deferred Ideas (OUT OF SCOPE)
- Configurable external manifest path (`riven.manifests.path`) -- loader reads from classpath only
- `deprecated` flag on catalog entries
- Per-manifest DEBUG log lines (summary only)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| VAL-02 | Manifest files validated against JSON Schema at load time using networknt validator | Use existing `JsonSchemaFactory.getInstance(V201909)` pattern from `SchemaService`. Load schema from classpath, validate each parsed `JsonNode` |
| VAL-03 | Invalid manifests log WARN, are skipped, do not block startup | Per-manifest try/catch in orchestrator. Log via injected `KLogger`. Insert as stale with no children |
| LOAD-01 | Loader runs on `ApplicationReadyEvent`, scans models/, templates/, integrations/ | `@EventListener(ApplicationReadyEvent::class)` on `ManifestLoaderService`. Use `ResourcePatternResolver` for classpath scan |
| LOAD-02 | Loading order enforced: models -> templates -> integrations | Hardcoded `listOf("models", "templates", "integrations")` ordering in orchestrator method |
| LOAD-03 | `$ref` resolution from in-memory model lookup map, no DB reads | Build `Map<String, ParsedModel>` from loaded models. Resolve `$ref` by key lookup before any persistence |
| LOAD-04 | `extend` merge: shallow additive attributes, base preserved on conflict | Merge function: model attributes + extend attributes (model wins on conflict). Tags appended. Scalar overrides applied |
| LOAD-05 | Relationship shorthand normalized to full targetRules[] format | Transform `targetEntityTypeKey` + `cardinality` into `targetRules[{ targetEntityTypeKey }]` + `cardinalityDefault` |
| LOAD-06 | Relationship validation: key existence, cardinality enum, format mutual exclusivity | Post-resolution validation pass checking all source/target keys exist in manifest entity type set |
| LOAD-07 | `protected` defaults: true for integration, false for template (inferred from directory) | Pass manifest type through pipeline; apply default in normalization step |
| PERS-01 | Idempotent upsert keyed on key + type | `findByKeyAndManifestType()` then update-or-create. Children use delete-reinsert |
| PERS-02 | Child table reconciliation: delete-then-reinsert within per-manifest @Transactional | Delete children by manifest_id, then insert fresh. All within single transaction |
| PERS-03 | Full reconciliation: manifests no longer on disk marked stale | Mark all stale at start, un-stale on upsert. Post-load: log stale summary, sync integration_definitions.stale |
| PERS-04 | Per-manifest transaction isolation | Each manifest upserted in its own `@Transactional` method. Orchestrator catches exceptions per-manifest |
| TEST-01 | Unit tests for $ref resolution | Test successful lookup, missing model, passthrough without $ref |
| TEST-02 | Unit tests for extend merge | Test attribute addition, base preservation on conflict, tag append, no-extend passthrough |
| TEST-03 | Unit tests for relationship normalization | Test shorthand-to-full conversion, mutual exclusivity rejection |
| TEST-04 | Unit tests for relationship validation | Test key existence check, cardinality enum, duplicate key detection |
| TEST-05 | Test fixture manifests | Create model, template (with $ref + extend), and integration fixtures in src/test/resources/manifests/ |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.5.3 | Application framework, event system, DI | Already in project |
| Jackson (jackson-module-kotlin) | Managed by Spring BOM | JSON parsing of manifest files | Already configured via `ObjectMapperConfig` |
| networknt json-schema-validator | 1.0.83 | JSON Schema validation of manifest files | Already in project, used by `SchemaService` |
| Spring Data JPA | Managed by Spring BOM | Repository persistence layer | Already in project |
| Hypersistence Utils | In project | JSONB column support | Already in project for `JsonBinaryType` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| KLogger (kotlin-logging) | In project | Structured logging | All service classes via constructor injection |
| H2 (test) | In project | Test database | Unit tests with `application-test.yml` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Manual classpath scan | Spring `ResourcePatternResolver` | ResourcePatternResolver is the standard Spring approach and handles JAR/filesystem transparently |
| Jackson tree model (`JsonNode`) | Data classes for deserialization | JsonNode is better here because manifest structure varies by type and we need raw JSONB passthrough for schema/mappings |

**No new dependencies required.** Everything needed is already in the project.

## Architecture Patterns

### Recommended Service Structure
```
riven.core.service.catalog/
├── ManifestLoaderService.kt        # Orchestrator: ApplicationReadyEvent listener, coordinates pipeline
├── ManifestScannerService.kt       # Classpath scanning + JSON Schema validation
├── ManifestResolverService.kt      # $ref resolution, extend merge, relationship normalization/validation
└── ManifestUpsertService.kt        # Idempotent persistence (upsert parent, delete-reinsert children)
```

### Pattern 1: ApplicationReadyEvent Listener
**What:** Spring event listener that triggers the load pipeline after the application context is fully initialized.
**When to use:** Exactly once per startup, after all beans are available.
**Example:**
```kotlin
@Service
class ManifestLoaderService(
    private val scannerService: ManifestScannerService,
    private val resolverService: ManifestResolverService,
    private val upsertService: ManifestUpsertService,
    private val manifestCatalogRepository: ManifestCatalogRepository,
    private val integrationDefinitionRepository: IntegrationDefinitionRepository,
    private val logger: KLogger
) {
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady(event: ApplicationReadyEvent) {
        loadAllManifests()
    }
}
```

### Pattern 2: Per-Manifest Transaction Isolation
**What:** Each manifest is upserted in its own `@Transactional` method so one failure does not roll back others.
**When to use:** The upsert service method that processes a single manifest.
**Example:**
```kotlin
@Service
class ManifestUpsertService(
    private val manifestCatalogRepository: ManifestCatalogRepository,
    // ... other repos
) {
    @Transactional
    fun upsertManifest(parsed: ParsedManifest) {
        // 1. Find or create manifest_catalog row
        // 2. Delete existing children by manifest_id
        // 3. Insert fresh children
    }
}
```

### Pattern 3: In-Memory Model Index for $ref Resolution
**What:** Models are loaded first, parsed into an in-memory map keyed by model key. Templates look up this map during $ref resolution. No DB reads.
**When to use:** During the resolve step, before persistence.
**Example:**
```kotlin
// Build index from successfully validated models
val modelIndex: Map<String, JsonNode> = loadedModels.associate { it.key to it.parsedJson }

// Resolve $ref in template entity types
fun resolveRef(ref: String, modelIndex: Map<String, JsonNode>): JsonNode? {
    val modelKey = ref.removePrefix("models/")
    return modelIndex[modelKey]
}
```

### Pattern 4: Stale-Based Reconciliation
**What:** Mark all existing entries stale before load, un-stale each loaded manifest, then handle remaining stale entries post-load.
**When to use:** At the start and end of each load cycle.
**Example:**
```kotlin
fun loadAllManifests() {
    // 1. Mark all existing entries stale
    manifestCatalogRepository.markAllStale()

    // 2. Load models -> templates -> integrations (each upserts with stale=false)

    // 3. Post-load: sync integration_definitions.stale for INTEGRATION type entries
    // 4. Log summary
}
```

### Pattern 5: Delete-Then-Reinsert for Child Reconciliation
**What:** Within a per-manifest transaction, delete all child rows by `manifest_id` then insert fresh rows. Simpler and more correct than diffing.
**When to use:** Every upsert, since manifests are small (10s of rows, not 1000s).
**Example:**
```kotlin
@Transactional
fun upsertManifest(parsed: ParsedManifest) {
    val catalog = findOrCreateCatalog(parsed)
    val manifestId = catalog.id!!

    // Delete all children (cascading order: target_rules -> relationships, semantic_metadata -> entity_types, field_mappings)
    catalogRelationshipTargetRuleRepository.deleteByCatalogRelationshipIdIn(
        catalogRelationshipRepository.findByManifestId(manifestId).map { it.id!! }
    )
    catalogRelationshipRepository.deleteByManifestId(manifestId)
    catalogSemanticMetadataRepository.deleteByCatalogEntityTypeIdIn(
        catalogEntityTypeRepository.findByManifestId(manifestId).map { it.id!! }
    )
    catalogEntityTypeRepository.deleteByManifestId(manifestId)
    catalogFieldMappingRepository.deleteByManifestId(manifestId)

    // Insert fresh children
    // ...
}
```

### Anti-Patterns to Avoid
- **Transaction on the event listener:** Do NOT put `@Transactional` on the `onApplicationReady` method. One bad manifest would roll back all manifests. Transaction scope belongs on the per-manifest upsert method only.
- **DB reads during $ref resolution:** The model index must be in-memory. Reading models from the database during template resolution defeats the purpose and creates ordering dependencies with persistence.
- **Updating children in-place:** Diffing attribute maps and relationship arrays is error-prone. Delete-reinsert is simpler, correct, and idempotent for the small row counts involved.
- **Using `ApplicationStartedEvent` instead of `ApplicationReadyEvent`:** `ApplicationStartedEvent` fires before web server is ready. `ApplicationReadyEvent` fires after everything is initialized, which is the correct hook.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON Schema validation | Custom validation logic | `networknt JsonSchemaFactory.getInstance(V201909).getSchema(schemaNode).validate(dataNode)` | Already in project, handles Draft 2019-09, returns structured errors |
| Classpath resource scanning | Manual file listing / `ClassLoader.getResource` | `ResourcePatternResolver.getResources("classpath:manifests/models/*.json")` | Handles both filesystem and JAR classpath, standard Spring pattern |
| JSON parsing | Manual string manipulation | `ObjectMapper.readTree(resource.inputStream)` to get `JsonNode` | Already configured with Kotlin module, handles all edge cases |
| Idempotent upsert | Manual SQL UPSERT / ON CONFLICT | `findByKeyAndManifestType()` + save (JPA merge) | Simpler, follows existing repository patterns, no native SQL needed |

**Key insight:** This phase is primarily orchestration and data transformation logic. The heavy lifting (JSON parsing, schema validation, database persistence) is handled by existing libraries. The custom code is the pipeline glue: ordering, resolution, normalization, and error handling.

## Common Pitfalls

### Pitfall 1: Classpath Resource Scanning in JARs
**What goes wrong:** `File`-based scanning works in development but fails when the application is packaged as a JAR (classpath resources are inside the JAR, not on the filesystem).
**Why it happens:** `Resource.getFile()` throws when the resource is inside a JAR.
**How to avoid:** Use `Resource.getInputStream()` exclusively. Never call `Resource.getFile()`. Use `ResourcePatternResolver.getResources("classpath:manifests/models/*.json")` which returns `Resource[]` that work in both filesystem and JAR contexts.
**Warning signs:** Tests pass but production deployment fails with `FileNotFoundException`.

### Pitfall 2: Directory-Based Templates/Integrations Scanning
**What goes wrong:** Templates and integrations are directory-based (`templates/saas-startup/manifest.json`), not flat files like models. The scan pattern must be `classpath:manifests/templates/*/manifest.json` not `classpath:manifests/templates/*.json`.
**Why it happens:** Different directory structures for models vs. templates/integrations.
**How to avoid:** Use `classpath:manifests/models/*.json` for models. Use `classpath:manifests/templates/*/manifest.json` and `classpath:manifests/integrations/*/manifest.json` for directory-based types. Extract the directory name as the manifest key.
**Warning signs:** Templates/integrations not being discovered despite being in the correct directory.

### Pitfall 3: Key Derivation Mismatch
**What goes wrong:** Model key is derived from filename (`customer.json` -> `customer`). Template/integration key is derived from directory name (`saas-startup/manifest.json` -> `saas-startup`). The key in the JSON file must match the derived key, but validation should prefer the filename/dirname-derived key as authoritative.
**Why it happens:** Two different naming conventions for flat vs. directory-based manifests.
**How to avoid:** Derive key from path, validate it matches the `key` field in the JSON. Log WARN if mismatch but use the filename/dirname key as authoritative (this is what the README documents).

### Pitfall 4: Transaction Boundary Placement
**What goes wrong:** Putting `@Transactional` on the event listener method means one manifest failure rolls back all previously successful upserts.
**Why it happens:** Misunderstanding of where transaction boundaries should be.
**How to avoid:** `@Transactional` goes on `ManifestUpsertService.upsertManifest()` only. The orchestrator catches exceptions per-manifest and continues.
**Warning signs:** One invalid manifest prevents ALL manifests from loading.

### Pitfall 5: Stale Marking Order with Integration Sync
**What goes wrong:** If integration_definitions.stale is synced before all manifests are processed, an integration that should be un-staled might remain stale.
**Why it happens:** Running the stale sync mid-pipeline instead of post-pipeline.
**How to avoid:** Run `integration_definitions.stale` sync AFTER all manifests have been processed. Query `manifest_catalog` for INTEGRATION type entries and sync their stale status to `integration_definitions` by matching key to slug.

### Pitfall 6: H2 Compatibility for Unit Tests
**What goes wrong:** The test profile uses H2 in PostgreSQL compatibility mode. Custom JPQL `UPDATE` queries (like `UPDATE ManifestCatalogEntity SET stale = true`) work fine. But if you write native SQL (`@Query(nativeQuery = true)`), H2 may not support PostgreSQL-specific syntax.
**Why it happens:** H2's PostgreSQL compatibility mode doesn't cover all PostgreSQL features.
**How to avoid:** Use JPQL for all bulk update queries. For `markAllStale()`, use `@Modifying @Query("UPDATE ManifestCatalogEntity m SET m.stale = true")`.

### Pitfall 7: Relationship Shorthand Normalization Edge Cases
**What goes wrong:** A manifest specifies BOTH `targetEntityTypeKey`+`cardinality` (shorthand) AND `targetRules[]` (full format). JSON Schema does not enforce mutual exclusivity by default.
**Why it happens:** Schema uses `additionalProperties: false` but doesn't use `oneOf` or `if/then/else` to enforce exclusivity between the two formats.
**How to avoid:** Validate mutual exclusivity in the resolver service code. If both are present, skip the relationship with WARN. The JSON Schema `additionalProperties: false` prevents unknown fields but doesn't handle this semantic constraint.

## Code Examples

### Classpath Resource Scanning
```kotlin
// Source: Spring Framework ResourcePatternResolver documentation
@Service
class ManifestScannerService(
    private val resourcePatternResolver: ResourcePatternResolver,
    private val objectMapper: ObjectMapper,
    private val logger: KLogger
) {
    private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909)

    fun scanModels(): List<ScannedManifest> {
        val resources = resourcePatternResolver.getResources("classpath:manifests/models/*.json")
        return resources.mapNotNull { resource ->
            val filename = resource.filename ?: return@mapNotNull null
            val key = filename.removeSuffix(".json")
            parseAndValidate(resource, key, ManifestType.MODEL, "manifests/schemas/model.schema.json")
        }
    }

    fun scanTemplates(): List<ScannedManifest> {
        val resources = resourcePatternResolver.getResources("classpath:manifests/templates/*/manifest.json")
        return resources.mapNotNull { resource ->
            val key = extractDirectoryName(resource, "templates")
            parseAndValidate(resource, key, ManifestType.TEMPLATE, "manifests/schemas/template.schema.json")
        }
    }

    private fun parseAndValidate(
        resource: Resource,
        key: String,
        type: ManifestType,
        schemaPath: String
    ): ScannedManifest? {
        return try {
            val jsonNode = objectMapper.readTree(resource.inputStream)
            val schema = loadSchema(schemaPath)
            val errors = schema.validate(jsonNode)
            if (errors.isNotEmpty()) {
                logger.warn { "Manifest $key ($type) failed validation: ${errors.map { it.message }}" }
                return null
            }
            ScannedManifest(key = key, type = type, json = jsonNode)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to parse manifest $key ($type)" }
            null
        }
    }

    private fun loadSchema(path: String): JsonSchema {
        val schemaResource = resourcePatternResolver.getResource("classpath:$path")
        val schemaNode = objectMapper.readTree(schemaResource.inputStream)
        return schemaFactory.getSchema(schemaNode)
    }
}
```

### $ref Resolution with Extend Merge
```kotlin
// In ManifestResolverService
fun resolveEntityType(
    entry: JsonNode,
    modelIndex: Map<String, JsonNode>
): ResolvedEntityType? {
    val ref = entry.get("\$ref")?.asText()

    if (ref != null) {
        // $ref resolution
        val modelKey = ref.removePrefix("models/")
        val model = modelIndex[modelKey] ?: return null // unresolved

        val extend = entry.get("extend")
        return if (extend != null) {
            applyExtend(model, extend)
        } else {
            parseEntityType(model)
        }
    } else {
        // Inline entity type
        return parseEntityType(entry)
    }
}

private fun applyExtend(base: JsonNode, extend: JsonNode): ResolvedEntityType {
    val merged = base.deepCopy() as ObjectNode

    // Scalar overrides
    extend.get("description")?.let { merged.set<JsonNode>("description", it) }
    extend.get("icon")?.let { merged.set<JsonNode>("icon", it) }
    extend.get("semanticGroup")?.let { merged.set<JsonNode>("semanticGroup", it) }
    extend.get("identifierKey")?.let { merged.set<JsonNode>("identifierKey", it) }

    // Additive attributes (base wins on conflict)
    extend.get("attributes")?.fields()?.forEach { (key, value) ->
        val baseAttrs = merged.get("attributes") as? ObjectNode ?: return@forEach
        if (!baseAttrs.has(key)) {
            baseAttrs.set<JsonNode>(key, value)
        }
    }

    // Append semantic tags
    val baseTags = base.at("/semantics/tags")
    val extendTags = extend.at("/semantics/tags")
    if (extendTags.isArray) {
        val mergedSemantics = (merged.get("semantics")?.deepCopy() as? ObjectNode)
            ?: objectMapper.createObjectNode()
        val tagArray = objectMapper.createArrayNode()
        baseTags.forEach { tagArray.add(it) }
        extendTags.forEach { tagArray.add(it) }
        mergedSemantics.set<JsonNode>("tags", tagArray)
        merged.set<JsonNode>("semantics", mergedSemantics)
    }

    return parseEntityType(merged)
}
```

### Relationship Shorthand Normalization
```kotlin
fun normalizeRelationship(rel: JsonNode, manifestType: ManifestType): NormalizedRelationship? {
    val hasShorthand = rel.has("targetEntityTypeKey") || rel.has("cardinality")
    val hasFullFormat = rel.has("targetRules")

    // Mutual exclusivity check
    if (hasShorthand && hasFullFormat) {
        return null // caller logs WARN
    }

    val protectedDefault = manifestType == ManifestType.INTEGRATION
    val isProtected = rel.get("protected")?.asBoolean() ?: protectedDefault

    if (hasShorthand) {
        val targetKey = rel.get("targetEntityTypeKey")?.asText() ?: return null
        val cardinality = rel.get("cardinality")?.asText() ?: return null

        return NormalizedRelationship(
            key = rel.get("key").asText(),
            sourceEntityTypeKey = rel.get("sourceEntityTypeKey").asText(),
            name = rel.get("name").asText(),
            cardinalityDefault = EntityRelationshipCardinality.valueOf(cardinality),
            protected = isProtected,
            targetRules = listOf(
                NormalizedTargetRule(targetEntityTypeKey = targetKey)
            )
        )
    }

    // Full format - parse targetRules directly
    // ...
}
```

### Bulk Stale Mark with JPQL
```kotlin
// In ManifestCatalogRepository
@Modifying
@Query("UPDATE ManifestCatalogEntity m SET m.stale = true")
fun markAllStale()

@Modifying
@Query("UPDATE ManifestCatalogEntity m SET m.stale = :stale, m.lastLoadedAt = :now WHERE m.key = :key AND m.manifestType = :type")
fun updateStaleAndLoadedAt(
    @Param("key") key: String,
    @Param("type") type: ManifestType,
    @Param("stale") stale: Boolean,
    @Param("now") now: ZonedDateTime
)
```

### Delete-By-ManifestId for Child Reconciliation
```kotlin
// Add to existing repositories
// CatalogEntityTypeRepository
fun deleteByManifestId(manifestId: UUID)

// CatalogRelationshipRepository
fun deleteByManifestId(manifestId: UUID)

// CatalogFieldMappingRepository
fun deleteByManifestId(manifestId: UUID)

// CatalogSemanticMetadataRepository
fun deleteByCatalogEntityTypeIdIn(entityTypeIds: List<UUID>)

// CatalogRelationshipTargetRuleRepository
fun deleteByCatalogRelationshipIdIn(relationshipIds: List<UUID>)
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `ApplicationStartedEvent` | `ApplicationReadyEvent` | Spring Boot 2.0+ | `ApplicationReadyEvent` fires after all initialization including web server. Correct for non-critical background work |
| `@PostConstruct` for startup tasks | `@EventListener(ApplicationReadyEvent::class)` | Spring Boot best practice | Event listener allows proper ordering and does not block bean initialization |
| `Resource.getFile()` | `Resource.getInputStream()` | Always | `getFile()` fails in JAR packaging. `getInputStream()` works everywhere |

## Schema Changes Required

### 1. Add `stale` column to `manifest_catalog`
```sql
-- New file: db/schema/01_tables/catalog_stale.sql (or ALTER in existing)
ALTER TABLE manifest_catalog ADD COLUMN IF NOT EXISTS stale BOOLEAN NOT NULL DEFAULT false;
```

The `ManifestCatalogEntity` must gain a `stale` field:
```kotlin
@Column(name = "stale", nullable = false)
var stale: Boolean = false,
```

### 2. Update `integration.schema.json` fieldMappings
The current schema allows `additionalProperties: true` on mappings, which accepts both old string format and new object format. It should be tightened to require the ADR-004 object format:
```json
"mappings": {
  "type": "object",
  "additionalProperties": {
    "type": "object",
    "required": ["source"],
    "properties": {
      "source": { "type": "string" },
      "transform": { "type": "object" }
    },
    "additionalProperties": false
  }
}
```

### 3. Add delete-by methods to repositories
All child repositories need `deleteByManifestId` (or equivalent) for the delete-reinsert reconciliation pattern. These are derived query methods -- no custom SQL needed.

## Data Model for In-Memory Pipeline

Intermediate data classes for the pipeline (not JPA entities):

```kotlin
// Scanner output
data class ScannedManifest(
    val key: String,
    val type: ManifestType,
    val json: JsonNode
)

// Resolver output (fully resolved, ready for persistence)
data class ResolvedManifest(
    val key: String,
    val name: String,
    val description: String?,
    val type: ManifestType,
    val manifestVersion: String?,
    val entityTypes: List<ResolvedEntityType>,
    val relationships: List<NormalizedRelationship>,
    val fieldMappings: List<ResolvedFieldMapping>,  // integration only
    val stale: Boolean = false  // true if resolution failed
)

data class ResolvedEntityType(
    val key: String,
    val displayNameSingular: String,
    val displayNamePlural: String,
    val iconType: String,
    val iconColour: String,
    val semanticGroup: String,
    val identifierKey: String?,
    val readonly: Boolean,
    val schema: Map<String, Any>,  // attribute map for JSONB
    val columns: List<Map<String, Any>>?,
    val semantics: ResolvedSemantics?
)

data class NormalizedRelationship(
    val key: String,
    val sourceEntityTypeKey: String,
    val name: String,
    val iconType: String = "LINK",
    val iconColour: String = "NEUTRAL",
    val allowPolymorphic: Boolean = false,
    val cardinalityDefault: EntityRelationshipCardinality,
    val protected: Boolean,
    val targetRules: List<NormalizedTargetRule>,
    val semantics: ResolvedRelationshipSemantics? = null
)
```

## Open Questions

1. **Classpath scanning pattern for directory-based manifests**
   - What we know: `classpath:manifests/templates/*/manifest.json` should work with `ResourcePatternResolver`
   - What's unclear: Whether this pattern works inside JARs (Ant-style `*` for directory names in classpath resources). Spring documentation confirms it does for `PathMatchingResourcePatternResolver` but behavior with nested JARs (fat JAR) should be verified.
   - Recommendation: Test with `./gradlew bootJar` and verify scanning works from the built JAR. If it fails, fall back to scanning `classpath*:manifests/templates/` and filtering for `manifest.json`.

2. **Extract directory key from Resource path**
   - What we know: `Resource.getURL().toString()` gives the full path. Need to extract the directory name.
   - What's unclear: The exact URL format differs between filesystem and JAR contexts.
   - Recommendation: Use `resource.getURL().path` and parse with standard path manipulation. Test in both contexts. A simple regex or split on `/templates/` + `/manifest.json` should work.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito (mockito-kotlin) |
| Config file | `src/test/resources/application-test.yml` |
| Quick run command | `./gradlew test --tests "riven.core.service.catalog.*"` |
| Full suite command | `./gradlew test` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| TEST-01 | $ref resolution | unit | `./gradlew test --tests "riven.core.service.catalog.ManifestResolverServiceTest" -x` | No - Wave 0 |
| TEST-02 | extend merge | unit | `./gradlew test --tests "riven.core.service.catalog.ManifestResolverServiceTest" -x` | No - Wave 0 |
| TEST-03 | relationship normalization | unit | `./gradlew test --tests "riven.core.service.catalog.ManifestResolverServiceTest" -x` | No - Wave 0 |
| TEST-04 | relationship validation | unit | `./gradlew test --tests "riven.core.service.catalog.ManifestResolverServiceTest" -x` | No - Wave 0 |
| TEST-05 | test fixture manifests | fixture | N/A (fixture files, not executable tests) | No - Wave 0 |
| VAL-02 | JSON Schema validation at load time | unit | `./gradlew test --tests "riven.core.service.catalog.ManifestScannerServiceTest" -x` | No - Wave 0 |
| VAL-03 | Invalid manifest skip + WARN | unit | `./gradlew test --tests "riven.core.service.catalog.ManifestScannerServiceTest" -x` | No - Wave 0 |
| PERS-01 | Idempotent upsert | unit | `./gradlew test --tests "riven.core.service.catalog.ManifestUpsertServiceTest" -x` | No - Wave 0 |
| PERS-04 | Per-manifest isolation | unit | `./gradlew test --tests "riven.core.service.catalog.ManifestLoaderServiceTest" -x` | No - Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "riven.core.service.catalog.*"`
- **Per wave merge:** `./gradlew test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `src/test/kotlin/riven/core/service/catalog/ManifestResolverServiceTest.kt` -- covers TEST-01, TEST-02, TEST-03, TEST-04
- [ ] `src/test/kotlin/riven/core/service/catalog/ManifestScannerServiceTest.kt` -- covers VAL-02, VAL-03
- [ ] `src/test/resources/manifests/` -- test fixture manifests (TEST-05)
- [ ] No framework install needed -- JUnit 5 and Mockito already configured

## Sources

### Primary (HIGH confidence)
- Existing codebase: `SchemaService.kt` -- JSON Schema validation pattern with `JsonSchemaFactory.getInstance(V201909)`
- Existing codebase: `IntegrationDefinitionEntity.kt` -- global catalog entity pattern with `stale` flag
- Existing codebase: `ObjectMapperConfig.kt` -- Jackson configuration (Kotlin module, lenient deserialization)
- Existing codebase: `LoggerConfig.kt` -- prototype-scoped KLogger injection pattern
- Existing codebase: `BaseServiceTest.kt` -- unit test base class pattern
- Existing codebase: All 6 catalog entity/repository pairs from Phase 1
- Existing codebase: 3 JSON Schema files (model, template, integration)
- Phase 1 RESEARCH.md and CONTEXT.md -- decisions and patterns established

### Secondary (MEDIUM confidence)
- Spring Framework documentation: `ResourcePatternResolver` for classpath scanning patterns
- Spring Boot documentation: `ApplicationReadyEvent` lifecycle event

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - all libraries already in project, no new dependencies
- Architecture: HIGH - service decomposition follows existing patterns exactly, pipeline steps are well-defined
- Pitfalls: HIGH - identified from direct analysis of codebase patterns and Spring Boot classpath behavior
- Data model: HIGH - catalog entities already exist from Phase 1, intermediate data classes are straightforward

**Research date:** 2026-03-05
**Valid until:** 2026-04-05 (stable -- no external dependencies or fast-moving libraries)
