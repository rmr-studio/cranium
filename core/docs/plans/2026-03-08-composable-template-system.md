# Composable Template System Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace monolithic templates with a composable three-layer system (models → templates → bundles) where bundles flatten multiple templates into one atomic installation with entity type deduplication and idempotency tracking.

**Architecture:** Bundles are lightweight catalog entries storing a list of template keys. Bundle installation resolves all referenced templates, merges entity types (deduplicating by key), creates them in one pass, then creates all relationships using a merged ID map. Each template is tracked individually in `workspace_template_installations` for idempotency.

**Tech Stack:** Spring Boot 3.5, Kotlin, JPA/Hibernate, PostgreSQL JSONB, Hypersistence JsonBinaryType

---

## Task 1: Delete Old Monolithic Templates

**Files:**
- Delete: `src/main/resources/manifests/templates/saas-startup/manifest.json`
- Delete: `src/main/resources/manifests/templates/dtc-ecommerce/manifest.json`
- Delete: `src/main/resources/manifests/templates/service-business/manifest.json`

**Step 1: Delete the three old monolithic template directories**

```bash
rm -rf src/main/resources/manifests/templates/saas-startup
rm -rf src/main/resources/manifests/templates/dtc-ecommerce
rm -rf src/main/resources/manifests/templates/service-business
```

**Step 2: Verify only composable templates remain**

```bash
ls src/main/resources/manifests/templates/
```

Expected: `billing/`, `crm/`, `ecommerce/`, `project-management/`, `saas/`, `support/`, `.gitkeep`

**Step 3: Commit**

```bash
git add -A src/main/resources/manifests/templates/
git commit -m "refactor: delete monolithic templates replaced by composable templates + bundles"
```

---

## Task 2: Add `templateKeys` to ManifestCatalogEntity

**Files:**
- Modify: `src/main/kotlin/riven/core/entity/catalog/ManifestCatalogEntity.kt`

**Step 1: Add the `templateKeys` field to ManifestCatalogEntity**

Add after the `contentHash` field (line 53), before `createdAt`:

```kotlin
@Type(JsonBinaryType::class)
@Column(name = "template_keys", columnDefinition = "jsonb")
val templateKeys: List<String>? = null,
```

Add the import at the top:

```kotlin
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import org.hibernate.annotations.Type
```

**Step 2: Add a `toBundleDetail()` method on ManifestCatalogEntity**

Add after the existing `toDetail()` method:

```kotlin
fun toBundleDetail() = BundleDetail(
    id = id!!,
    key = key,
    name = name,
    description = description,
    templateKeys = templateKeys ?: emptyList()
)
```

**Step 3: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/kotlin/riven/core/entity/catalog/ManifestCatalogEntity.kt
git commit -m "feat: add templateKeys JSONB field to ManifestCatalogEntity"
```

---

## Task 3: Add Bundle Scanning to ManifestScannerService

**Files:**
- Modify: `src/main/kotlin/riven/core/service/catalog/ManifestScannerService.kt`

**Step 1: Add `scanBundles()` method**

Add after `scanIntegrations()` (after line 58):

```kotlin
/** Scans classpath bundles directory for bundle manifests. Key derived from directory name. */
fun scanBundles(): List<ScannedManifest> {
    val resources = resourcePatternResolver.getResources("$basePath/bundles/*/manifest.json")
    return resources.mapNotNull { resource ->
        val key = extractDirectoryName(resource, "bundles")
        parseAndValidate(resource, key, ManifestType.BUNDLE, "manifests/schemas/bundle.schema.json")
    }
}
```

**Step 2: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/riven/core/service/catalog/ManifestScannerService.kt
git commit -m "feat: add scanBundles() to ManifestScannerService"
```

---

## Task 4: Add Bundle Resolution to ManifestResolverService

**Files:**
- Modify: `src/main/kotlin/riven/core/service/catalog/ManifestResolverService.kt`

**Step 1: Add `resolveBundle()` method**

Add a new public method after `resolveManifest()`. Bundles are simple — just extract the template key list from the JSON:

```kotlin
/**
 * Resolves a bundle manifest into a ResolvedBundle.
 * Bundles are lightweight — they only store a list of template keys.
 * No entity type resolution needed; that happens at installation time.
 */
fun resolveBundle(scanned: ScannedManifest): ResolvedBundle {
    require(scanned.type == ManifestType.BUNDLE) { "Expected BUNDLE manifest, got ${scanned.type}" }
    val json = scanned.json

    return ResolvedBundle(
        key = json.get("key").asText(),
        name = json.get("name").asText(),
        description = json.get("description")?.asText(),
        manifestVersion = json.get("manifestVersion")?.asText(),
        templateKeys = json.get("templates").map { it.asText() },
    )
}
```

Add the import for `ResolvedBundle` (already in the same package `riven.core.models.catalog`).

**Step 2: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/riven/core/service/catalog/ManifestResolverService.kt
git commit -m "feat: add resolveBundle() to ManifestResolverService"
```

---

## Task 5: Add Bundle Upsert to ManifestUpsertService

**Files:**
- Modify: `src/main/kotlin/riven/core/service/catalog/ManifestUpsertService.kt`

**Step 1: Add `upsertBundle()` method**

Add after `upsertManifest()`:

```kotlin
/**
 * Persists a resolved bundle to the catalog. Bundles have no child rows
 * (no entity types or relationships) — only the catalog entry with templateKeys JSONB.
 */
@Transactional
fun upsertBundle(resolved: ResolvedBundle) {
    val contentHash = computeBundleContentHash(resolved)
    val existing = manifestCatalogRepository.findByKeyAndManifestType(resolved.key, ManifestType.BUNDLE)

    if (existing != null && contentHash == existing.contentHash) {
        existing.lastLoadedAt = ZonedDateTime.now()
        manifestCatalogRepository.save(existing)
        return
    }

    val entity = if (existing != null) {
        existing.copy(
            name = resolved.name,
            description = resolved.description,
            manifestVersion = resolved.manifestVersion,
            lastLoadedAt = ZonedDateTime.now(),
            stale = resolved.stale,
            contentHash = contentHash,
            templateKeys = resolved.templateKeys,
        )
    } else {
        ManifestCatalogEntity(
            key = resolved.key,
            name = resolved.name,
            description = resolved.description,
            manifestType = ManifestType.BUNDLE,
            manifestVersion = resolved.manifestVersion,
            lastLoadedAt = ZonedDateTime.now(),
            stale = resolved.stale,
            contentHash = contentHash,
            templateKeys = resolved.templateKeys,
        )
    }

    manifestCatalogRepository.save(entity)
}

private fun computeBundleContentHash(resolved: ResolvedBundle): String {
    val content = objectMapper.writeValueAsString(
        mapOf(
            "name" to resolved.name,
            "description" to resolved.description,
            "manifestVersion" to resolved.manifestVersion,
            "templateKeys" to resolved.templateKeys,
        )
    )
    return MessageDigest.getInstance("SHA-256")
        .digest(content.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
```

Add import for `ManifestCatalogEntity` (already imported via `riven.core.entity.catalog.*`).

**Step 2: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/riven/core/service/catalog/ManifestUpsertService.kt
git commit -m "feat: add upsertBundle() to ManifestUpsertService"
```

---

## Task 6: Add Bundle Loading Phase to ManifestLoaderService

**Files:**
- Modify: `src/main/kotlin/riven/core/service/catalog/ManifestLoaderService.kt`

**Step 1: Add bundle scanning and loading to `loadAllManifests()`**

In `loadAllManifests()`, after the `scannedIntegrations` scan line (line 60), add:

```kotlin
val scannedBundles = scannerService.scanBundles()
```

After the `integrationsResult` line (line 64), add:

```kotlin
val bundlesResult = loadBundles(scannedBundles, seenManifests)
```

Update the `reconcileStaleEntries` call (line 66) to include bundle count:

```kotlin
reconcileStaleEntries(scannedModels.size + scannedTemplates.size + scannedIntegrations.size + scannedBundles.size, seenManifests)
```

Update the `totalSkipped` and log line (lines 69-71):

```kotlin
val totalSkipped = modelsResult.skipped + templatesResult.skipped + integrationsResult.skipped + bundlesResult.skipped
val staleCount = manifestCatalogRepository.findByStaleTrue().size
logger.info { "Manifest load complete: ${modelsResult.loaded} models, ${templatesResult.loaded} templates, ${integrationsResult.loaded} integrations, ${bundlesResult.loaded} bundles loaded. $staleCount stale. $totalSkipped skipped." }
```

**Step 2: Add `loadBundles()` private method**

Add after `loadIntegrations()`:

```kotlin
private fun loadBundles(
    scannedBundles: List<ScannedManifest>,
    seenManifests: MutableSet<Pair<String, ManifestType>>
): PhaseResult {
    var loaded = 0
    var skipped = 0

    for (scanned in scannedBundles) {
        try {
            val resolved = resolverService.resolveBundle(scanned)
            upsertService.upsertBundle(resolved)
            if (!resolved.stale) {
                loaded++
                seenManifests.add(scanned.key to scanned.type)
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load bundle manifest: ${scanned.key}" }
            skipped++
        }
    }

    return PhaseResult(loaded, skipped)
}
```

**Step 3: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/kotlin/riven/core/service/catalog/ManifestLoaderService.kt
git commit -m "feat: add bundle loading phase to ManifestLoaderService"
```

---

## Task 7: Add Bundle Query Methods to ManifestCatalogService

**Files:**
- Modify: `src/main/kotlin/riven/core/service/catalog/ManifestCatalogService.kt`

**Step 1: Add `getAvailableBundles()` and `getBundleByKey()` methods**

Add after `getAvailableModels()` (after line 43):

```kotlin
/** Returns all non-stale bundle manifests with their template key lists. */
fun getAvailableBundles(): List<BundleDetail> {
    val manifests = manifestCatalogRepository.findByManifestTypeAndStaleFalse(ManifestType.BUNDLE)
    return manifests.map { it.toBundleDetail() }
}

/**
 * Returns a bundle detail by key.
 *
 * @throws NotFoundException if the bundle key doesn't exist or is stale
 */
fun getBundleByKey(key: String): BundleDetail {
    val catalog = manifestCatalogRepository.findByKeyAndManifestTypeAndStaleFalse(key, ManifestType.BUNDLE)
        ?: throw NotFoundException("Bundle not found: $key")
    return catalog.toBundleDetail()
}
```

Add the import:

```kotlin
import riven.core.models.catalog.BundleDetail
```

**Step 2: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/riven/core/service/catalog/ManifestCatalogService.kt
git commit -m "feat: add bundle query methods to ManifestCatalogService"
```

---

## Task 8: Add BundleInstallationResponse Model

**Files:**
- Create: `src/main/kotlin/riven/core/models/response/catalog/BundleInstallationResponse.kt`

**Step 1: Create the response model**

```kotlin
package riven.core.models.response.catalog

data class BundleInstallationResponse(
    val bundleKey: String,
    val bundleName: String,
    val templatesInstalled: List<String>,
    val templatesSkipped: List<String>,
    val entityTypesCreated: Int,
    val relationshipsCreated: Int,
    val entityTypes: List<CreatedEntityTypeSummary>,
)
```

**Step 2: Create the InstallBundleRequest model**

Create: `src/main/kotlin/riven/core/models/request/catalog/InstallBundleRequest.kt`

```kotlin
package riven.core.models.request.catalog

data class InstallBundleRequest(
    val bundleKey: String,
)
```

**Step 3: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/kotlin/riven/core/models/response/catalog/BundleInstallationResponse.kt \
        src/main/kotlin/riven/core/models/request/catalog/InstallBundleRequest.kt
git commit -m "feat: add BundleInstallationResponse and InstallBundleRequest models"
```

---

## Task 9: Add `installBundle()` to TemplateInstallationService

This is the core task. The bundle installation method flattens all templates, deduplicates entity types, creates them in one pass, then creates all relationships.

**Files:**
- Modify: `src/main/kotlin/riven/core/service/catalog/TemplateInstallationService.kt`

**Step 1: Add `WorkspaceTemplateInstallationRepository` to constructor**

Add to the constructor parameter list:

```kotlin
private val installationRepository: WorkspaceTemplateInstallationRepository,
```

Add the import:

```kotlin
import riven.core.repository.catalog.WorkspaceTemplateInstallationRepository
import riven.core.entity.catalog.WorkspaceTemplateInstallationEntity
import riven.core.models.response.catalog.BundleInstallationResponse
import riven.core.models.catalog.BundleDetail
```

**Step 2: Add `installBundle()` public method**

Add after `installTemplate()`:

```kotlin
/**
 * Install a bundle into a workspace, resolving all referenced templates and creating
 * entity types, relationships, and semantic metadata atomically.
 *
 * Templates already installed in this workspace are skipped, but their entity type IDs
 * are resolved so that cross-template relationships can reference them.
 *
 * @param workspaceId the workspace to install into
 * @param bundleKey the bundle key identifying the collection of templates
 * @return summary of installed/skipped templates and created entities
 */
@Transactional
@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
fun installBundle(workspaceId: UUID, bundleKey: String): BundleInstallationResponse {
    val userId = authTokenService.getUserId()
    val bundle = catalogService.getBundleByKey(bundleKey)

    val (templatesToInstall, templatesToSkip) = partitionTemplatesByInstallationStatus(
        workspaceId, bundle.templateKeys
    )

    val skippedEntityIdMap = resolveExistingEntityTypeIds(workspaceId, templatesToSkip)
    val manifests = templatesToInstall.map { key ->
        catalogService.getManifestByKey(key, ManifestType.TEMPLATE)
    }

    val allEntityTypes = mergeAndDeduplicateEntityTypes(manifests)
    val creationResults = createEntityTypes(workspaceId, allEntityTypes)
    val mergedIdMap = creationResults + skippedEntityIdMap

    val allRelationships = manifests.flatMap { it.relationships }
    val allCatalogEntityTypes = manifests.flatMap { it.entityTypes }
    val relationshipsCreated = createRelationships(
        workspaceId, allRelationships, allCatalogEntityTypes, mergedIdMap
    )

    applySemanticMetadata(workspaceId, allEntityTypes, creationResults)

    for (templateKey in templatesToInstall) {
        recordTemplateInstallation(workspaceId, templateKey, userId, mergedIdMap)
    }

    logBundleActivity(userId, workspaceId, bundleKey, bundle, creationResults, templatesToInstall, templatesToSkip)

    return BundleInstallationResponse(
        bundleKey = bundleKey,
        bundleName = bundle.name,
        templatesInstalled = templatesToInstall,
        templatesSkipped = templatesToSkip,
        entityTypesCreated = creationResults.size,
        relationshipsCreated = relationshipsCreated,
        entityTypes = creationResults.values.map { result ->
            CreatedEntityTypeSummary(
                id = result.entityTypeId,
                key = result.key,
                displayName = result.displayName,
                attributeCount = result.attributeCount,
            )
        },
    )
}
```

**Step 3: Add private helper methods for bundle installation**

Add in a new section after the "Response Building" section:

```kotlin
// ------ Bundle Installation Helpers ------

/**
 * Partitions template keys into install vs skip sets based on workspace_template_installations.
 */
private fun partitionTemplatesByInstallationStatus(
    workspaceId: UUID,
    templateKeys: List<String>,
): Pair<List<String>, List<String>> {
    val existing = installationRepository.findByWorkspaceIdAndManifestKeyIn(workspaceId, templateKeys)
    val installedKeys = existing.map { it.manifestKey }.toSet()
    val toInstall = templateKeys.filter { it !in installedKeys }
    val toSkip = templateKeys.filter { it in installedKeys }
    return toInstall to toSkip
}

/**
 * For skipped templates, resolves their entity type IDs from the workspace so that
 * cross-template relationships can reference already-created entity types.
 */
private fun resolveExistingEntityTypeIds(
    workspaceId: UUID,
    skippedTemplateKeys: List<String>,
): Map<String, EntityTypeCreationResult> {
    if (skippedTemplateKeys.isEmpty()) return emptyMap()

    val skippedManifests = skippedTemplateKeys.map { key ->
        catalogService.getManifestByKey(key, ManifestType.TEMPLATE)
    }
    val allEntityTypeKeys = skippedManifests.flatMap { m -> m.entityTypes.map { it.key } }

    val existingEntities = entityTypeRepository.findByworkspaceIdAndKeyIn(workspaceId, allEntityTypeKeys)
    val entityByKey = existingEntities.associateBy { it.key }

    val results = mutableMapOf<String, EntityTypeCreationResult>()
    for (key in allEntityTypeKeys) {
        val entity = entityByKey[key] ?: continue
        val entityId = entity.id ?: continue

        @Suppress("UNCHECKED_CAST")
        val attributeKeys = (entity.schema.properties as? Map<UUID, *>)?.keys?.toList() ?: emptyList()

        results[key] = EntityTypeCreationResult(
            entityTypeId = entityId,
            attributeKeyMap = emptyMap(),
            attributeCount = attributeKeys.size,
            displayName = entity.displayNameSingular,
            key = key,
        )
    }
    return results
}

/**
 * Merges entity types from all manifests, deduplicating by key.
 * First occurrence wins — entity types are identical across templates
 * since they come from the same shared model.
 */
private fun mergeAndDeduplicateEntityTypes(
    manifests: List<ManifestDetail>,
): List<CatalogEntityTypeModel> {
    val seen = mutableSetOf<String>()
    val merged = mutableListOf<CatalogEntityTypeModel>()
    for (manifest in manifests) {
        for (entityType in manifest.entityTypes) {
            if (seen.add(entityType.key)) {
                merged.add(entityType)
            }
        }
    }
    return merged
}

/**
 * Records a template installation for idempotency tracking.
 * Stores attribute key mappings so future operations can trace template provenance.
 */
private fun recordTemplateInstallation(
    workspaceId: UUID,
    templateKey: String,
    userId: UUID,
    creationResults: Map<String, EntityTypeCreationResult>,
) {
    val attributeMappings = creationResults.mapValues { (_, result) ->
        result.attributeKeyMap as Any
    }

    installationRepository.save(
        WorkspaceTemplateInstallationEntity(
            workspaceId = workspaceId,
            manifestKey = templateKey,
            installedBy = userId,
            attributeMappings = attributeMappings,
        )
    )
}

private fun logBundleActivity(
    userId: UUID,
    workspaceId: UUID,
    bundleKey: String,
    bundle: BundleDetail,
    creationResults: Map<String, EntityTypeCreationResult>,
    templatesInstalled: List<String>,
    templatesSkipped: List<String>,
) {
    activityService.log(
        activity = Activity.TEMPLATE,
        operation = OperationType.CREATE,
        userId = userId,
        workspaceId = workspaceId,
        entityType = ApplicationEntityType.ENTITY_TYPE,
        entityId = null,
        "bundleKey" to bundleKey,
        "bundleName" to bundle.name,
        "templatesInstalled" to templatesInstalled,
        "templatesSkipped" to templatesSkipped,
        "entityTypesCreated" to creationResults.size,
    )
}
```

**Step 4: Update `installTemplate()` to add idempotency check and installation tracking**

Replace the `installTemplate()` method body with:

```kotlin
@Transactional
@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
fun installTemplate(workspaceId: UUID, templateKey: String): TemplateInstallationResponse {
    val userId = authTokenService.getUserId()

    val existingInstallation = installationRepository.findByWorkspaceIdAndManifestKey(workspaceId, templateKey)
    if (existingInstallation != null) {
        val manifest = catalogService.getManifestByKey(templateKey, ManifestType.TEMPLATE)
        return TemplateInstallationResponse(
            templateKey = templateKey,
            templateName = manifest.name,
            entityTypesCreated = 0,
            relationshipsCreated = 0,
            entityTypes = emptyList(),
        )
    }

    val manifest = catalogService.getManifestByKey(templateKey, ManifestType.TEMPLATE)

    val creationResults = createEntityTypes(workspaceId, manifest.entityTypes)
    val relationshipsCreated = createRelationships(workspaceId, manifest.relationships, manifest.entityTypes, creationResults)
    applySemanticMetadata(workspaceId, manifest.entityTypes, creationResults)
    recordTemplateInstallation(workspaceId, templateKey, userId, creationResults)
    logTemplateActivity(userId, workspaceId, templateKey, manifest, creationResults)

    return buildResponse(templateKey, manifest, creationResults, relationshipsCreated)
}
```

**Step 5: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add src/main/kotlin/riven/core/service/catalog/TemplateInstallationService.kt
git commit -m "feat: add installBundle() with idempotency tracking to TemplateInstallationService"
```

---

## Task 10: Add Bundle Endpoints to TemplateController

**Files:**
- Modify: `src/main/kotlin/riven/core/controller/catalog/TemplateController.kt`

**Step 1: Add bundle list and install endpoints**

Add the imports:

```kotlin
import riven.core.models.catalog.BundleDetail
import riven.core.models.request.catalog.InstallBundleRequest
import riven.core.models.response.catalog.BundleInstallationResponse
```

Add after the existing `installTemplate` endpoint:

```kotlin
@GetMapping("/bundles")
@Operation(
    summary = "List available bundles",
    description = "Returns all bundles available for installation. Each bundle is a curated collection of templates."
)
@ApiResponses(
    ApiResponse(responseCode = "200", description = "Bundles retrieved successfully"),
    ApiResponse(responseCode = "401", description = "Unauthorized access"),
)
fun listBundles(): ResponseEntity<List<BundleDetail>> =
    ResponseEntity.ok(catalogService.getAvailableBundles())

@PostMapping("/{workspaceId}/install-bundle")
@Operation(
    summary = "Install bundle into workspace",
    description = "Installs all templates in a bundle into the specified workspace. " +
        "Templates already installed are skipped. Installation is atomic."
)
@ApiResponses(
    ApiResponse(responseCode = "200", description = "Bundle installed successfully"),
    ApiResponse(responseCode = "403", description = "No access to workspace"),
    ApiResponse(responseCode = "404", description = "Bundle not found"),
)
fun installBundle(
    @PathVariable workspaceId: UUID,
    @RequestBody request: InstallBundleRequest,
): ResponseEntity<BundleInstallationResponse> =
    ResponseEntity.ok(installationService.installBundle(workspaceId, request.bundleKey))
```

**Step 2: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/riven/core/controller/catalog/TemplateController.kt
git commit -m "feat: add bundle list and install endpoints to TemplateController"
```

---

## Task 11: Update Tests for Idempotency in TemplateInstallationServiceTest

**Files:**
- Modify: `src/test/kotlin/riven/core/service/catalog/TemplateInstallationServiceTest.kt`

**Step 1: Add mock for WorkspaceTemplateInstallationRepository**

Add to the test class:

```kotlin
@MockitoBean
private lateinit var installationRepository: WorkspaceTemplateInstallationRepository
```

Add import:

```kotlin
import riven.core.repository.catalog.WorkspaceTemplateInstallationRepository
import riven.core.entity.catalog.WorkspaceTemplateInstallationEntity
```

**Step 2: Update existing test helper setup**

In each existing test that calls `installTemplate`, add a mock for the installation check. In the test setup or helper methods, add:

```kotlin
whenever(installationRepository.findByWorkspaceIdAndManifestKey(any(), any())).thenReturn(null)
whenever(installationRepository.save(any<WorkspaceTemplateInstallationEntity>())).thenAnswer { it.arguments[0] }
```

**Step 3: Add idempotency test for `installTemplate`**

```kotlin
@Test
fun `installTemplate returns empty response when template already installed`() {
    val existingInstallation = WorkspaceTemplateInstallationEntity(
        workspaceId = workspaceId,
        manifestKey = "test-template",
        installedBy = userId,
    )
    whenever(installationRepository.findByWorkspaceIdAndManifestKey(workspaceId, "test-template"))
        .thenReturn(existingInstallation)

    val manifest = createManifestWithEntityTypes(listOf(createCatalogEntityType()))
    whenever(catalogService.getManifestByKey("test-template", ManifestType.TEMPLATE))
        .thenReturn(manifest)

    val result = service.installTemplate(workspaceId, "test-template")

    assertEquals(0, result.entityTypesCreated)
    assertEquals(0, result.relationshipsCreated)
    assertTrue(result.entityTypes.isEmpty())
    verify(entityTypeRepository, never()).save(any())
}
```

**Step 4: Run tests to verify**

```bash
./gradlew test --tests "riven.core.service.catalog.TemplateInstallationServiceTest"
```

Expected: All tests pass

**Step 5: Commit**

```bash
git add src/test/kotlin/riven/core/service/catalog/TemplateInstallationServiceTest.kt
git commit -m "test: add idempotency test and update mocks for installation tracking"
```

---

## Task 12: Add Bundle Installation Tests

**Files:**
- Modify: `src/test/kotlin/riven/core/service/catalog/TemplateInstallationServiceTest.kt`

**Step 1: Add test for bundle installation with multiple templates**

```kotlin
@Test
fun `installBundle creates entity types from all templates and deduplicates shared types`() {
    val bundle = BundleDetail(
        id = UUID.randomUUID(),
        key = "test-bundle",
        name = "Test Bundle",
        description = "A test bundle",
        templateKeys = listOf("crm", "billing"),
    )
    whenever(catalogService.getBundleByKey("test-bundle")).thenReturn(bundle)
    whenever(installationRepository.findByWorkspaceIdAndManifestKeyIn(eq(workspaceId), any()))
        .thenReturn(emptyList())
    whenever(installationRepository.save(any<WorkspaceTemplateInstallationEntity>()))
        .thenAnswer { it.arguments[0] }

    val customerType = createCatalogEntityType(key = "customer")
    val invoiceType = createCatalogEntityType(key = "invoice")

    val crmManifest = createManifestWithEntityTypes(listOf(customerType), key = "crm", name = "CRM")
    val billingManifest = createManifestWithEntityTypes(listOf(customerType, invoiceType), key = "billing", name = "Billing")

    whenever(catalogService.getManifestByKey("crm", ManifestType.TEMPLATE)).thenReturn(crmManifest)
    whenever(catalogService.getManifestByKey("billing", ManifestType.TEMPLATE)).thenReturn(billingManifest)

    stubEntityTypeSave()
    stubFallbackDefinition()

    val result = service.installBundle(workspaceId, "test-bundle")

    assertEquals("test-bundle", result.bundleKey)
    assertEquals(listOf("crm", "billing"), result.templatesInstalled)
    assertTrue(result.templatesSkipped.isEmpty())
    // customer appears in both templates but should only be created once
    assertEquals(2, result.entityTypesCreated) // customer + invoice
}
```

**Step 2: Add test for bundle installation with skipped templates**

```kotlin
@Test
fun `installBundle skips already-installed templates and resolves their entity types`() {
    val bundle = BundleDetail(
        id = UUID.randomUUID(),
        key = "test-bundle",
        name = "Test Bundle",
        description = null,
        templateKeys = listOf("crm", "billing"),
    )
    whenever(catalogService.getBundleByKey("test-bundle")).thenReturn(bundle)

    val existingInstallation = WorkspaceTemplateInstallationEntity(
        workspaceId = workspaceId,
        manifestKey = "crm",
        installedBy = userId,
    )
    whenever(installationRepository.findByWorkspaceIdAndManifestKeyIn(eq(workspaceId), any()))
        .thenReturn(listOf(existingInstallation))
    whenever(installationRepository.save(any<WorkspaceTemplateInstallationEntity>()))
        .thenAnswer { it.arguments[0] }

    // CRM is skipped — resolve its entity types from workspace
    val crmManifest = createManifestWithEntityTypes(
        listOf(createCatalogEntityType(key = "customer")),
        key = "crm",
        name = "CRM",
    )
    whenever(catalogService.getManifestByKey("crm", ManifestType.TEMPLATE)).thenReturn(crmManifest)

    val existingCustomerEntity = mock<EntityTypeEntity>()
    val customerId = UUID.randomUUID()
    whenever(existingCustomerEntity.id).thenReturn(customerId)
    whenever(existingCustomerEntity.key).thenReturn("customer")
    whenever(existingCustomerEntity.displayNameSingular).thenReturn("Customer")
    whenever(existingCustomerEntity.schema).thenReturn(mock())
    whenever(entityTypeRepository.findByworkspaceIdAndKeyIn(eq(workspaceId), any()))
        .thenReturn(listOf(existingCustomerEntity))

    // Billing is installed — has invoice type
    val invoiceType = createCatalogEntityType(key = "invoice")
    val billingManifest = createManifestWithEntityTypes(listOf(invoiceType), key = "billing", name = "Billing")
    whenever(catalogService.getManifestByKey("billing", ManifestType.TEMPLATE)).thenReturn(billingManifest)

    stubEntityTypeSave()
    stubFallbackDefinition()

    val result = service.installBundle(workspaceId, "test-bundle")

    assertEquals(listOf("billing"), result.templatesInstalled)
    assertEquals(listOf("crm"), result.templatesSkipped)
    assertEquals(1, result.entityTypesCreated) // only invoice, customer was skipped
}
```

**Step 3: Run tests**

```bash
./gradlew test --tests "riven.core.service.catalog.TemplateInstallationServiceTest"
```

Expected: All tests pass

**Step 4: Commit**

```bash
git add src/test/kotlin/riven/core/service/catalog/TemplateInstallationServiceTest.kt
git commit -m "test: add bundle installation tests with deduplication and skip logic"
```

---

## Task 13: Full Build Verification

**Step 1: Run the full test suite**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, all tests pass

**Step 2: Run a full build**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

**Step 3: Verify the composable template manifest structure**

```bash
ls src/main/resources/manifests/templates/
ls src/main/resources/manifests/bundles/
ls src/main/resources/manifests/models/
```

Expected:
- templates: `billing/`, `crm/`, `ecommerce/`, `project-management/`, `saas/`, `support/`
- bundles: `dtc-ecommerce/`, `saas-startup/`, `service-business/`
- models: `communication.json`, `customer.json`, `invoice.json`, `support-ticket.json`
