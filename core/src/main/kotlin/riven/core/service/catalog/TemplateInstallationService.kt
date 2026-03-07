package riven.core.service.catalog

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.entity.EntityTypeEntity
import riven.core.enums.activity.Activity
import riven.core.enums.catalog.ManifestType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.core.DataFormat
import riven.core.enums.core.DataType
import riven.core.enums.entity.EntityPropertyType
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.enums.util.OperationType
import riven.core.models.catalog.CatalogEntityTypeModel
import riven.core.models.catalog.CatalogRelationshipModel
import riven.core.models.catalog.CatalogSemanticMetadataModel
import riven.core.models.catalog.ManifestDetail
import riven.core.models.common.validation.Schema
import riven.core.models.entity.configuration.EntityTypeAttributeColumn
import riven.core.models.request.entity.type.SaveRelationshipDefinitionRequest
import riven.core.models.request.entity.type.SaveSemanticMetadataRequest
import riven.core.models.request.entity.type.SaveTargetRuleRequest
import riven.core.models.catalog.BundleDetail
import riven.core.models.response.catalog.BundleInstallationResponse
import riven.core.models.response.catalog.CreatedEntityTypeSummary
import riven.core.models.response.catalog.TemplateInstallationResponse
import riven.core.entity.catalog.WorkspaceTemplateInstallationEntity
import riven.core.repository.catalog.WorkspaceTemplateInstallationRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.service.activity.ActivityService
import riven.core.service.activity.log
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.EntityTypeSemanticMetadataService
import riven.core.service.entity.type.EntityTypeRelationshipService
import java.util.*

/**
 * Orchestrates template installation: reads a fully-resolved template from the manifest catalog
 * and creates workspace-scoped entity types, relationships, and semantic metadata in a single
 * atomic transaction.
 */
@Service
class TemplateInstallationService(
    private val catalogService: ManifestCatalogService,
    private val entityTypeRepository: EntityTypeRepository,
    private val installationRepository: WorkspaceTemplateInstallationRepository,
    private val relationshipService: EntityTypeRelationshipService,
    private val semanticMetadataService: EntityTypeSemanticMetadataService,
    private val authTokenService: AuthTokenService,
    private val activityService: ActivityService,
    private val logger: KLogger,
) {

    /**
     * Install a template into a workspace, creating all entity types, relationships,
     * and semantic metadata defined in the template manifest.
     *
     * The installation is atomic: if any step fails, the entire transaction rolls back
     * and nothing is created. Entity types are immediately published and usable.
     *
     * @param workspaceId the workspace to install the template into
     * @param templateKey the manifest key identifying the template
     * @return summary of created entity types and relationships
     */
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

    // ------ Entity Type Creation ------

    /**
     * Creates workspace entity types from catalog entity type definitions.
     *
     * Translates string-keyed manifest schemas into UUID-keyed internal schemas,
     * generates attribute UUIDs, resolves identifier keys, and initializes semantic
     * metadata and fallback relationship definitions for each entity type.
     *
     * @return map from manifest entity type key to creation result (entity type ID + attribute key map)
     */
    private fun createEntityTypes(
        workspaceId: UUID,
        catalogEntityTypes: List<CatalogEntityTypeModel>,
    ): Map<String, EntityTypeCreationResult> {
        val results = mutableMapOf<String, EntityTypeCreationResult>()

        for (catalogType in catalogEntityTypes) {
            val (entity, attributeKeyMap) = buildEntityType(workspaceId, catalogType)
            val saved = entityTypeRepository.save(entity)
            val savedId = requireNotNull(saved.id)

            semanticMetadataService.initializeForEntityType(
                entityTypeId = savedId,
                workspaceId = workspaceId,
                attributeIds = attributeKeyMap.values.toList(),
            )

            relationshipService.createFallbackDefinition(workspaceId, savedId)

            results[catalogType.key] = EntityTypeCreationResult(
                entityTypeId = savedId,
                attributeKeyMap = attributeKeyMap,
                attributeCount = attributeKeyMap.size,
                displayName = catalogType.displayNameSingular,
                key = catalogType.key,
            )

            logger.info { "Created entity type '${catalogType.key}' ($savedId) with ${attributeKeyMap.size} attributes" }
        }

        return results
    }

    /**
     * Builds an EntityTypeEntity from a catalog entity type model, translating the string-keyed
     * manifest schema into a UUID-keyed Schema<UUID> with proper DataType and DataFormat mapping.
     *
     * @return the entity and a map of manifest attribute keys to generated UUIDs
     */
    private fun buildEntityType(
        workspaceId: UUID,
        catalogType: CatalogEntityTypeModel,
    ): Pair<EntityTypeEntity, Map<String, UUID>> {
        val attributeKeyMap = mutableMapOf<String, UUID>()
        val properties = mutableMapOf<UUID, Schema<UUID>>()
        val columns = mutableListOf<EntityTypeAttributeColumn>()

        for ((attrKey, attrDefRaw) in catalogType.schema) {
            @Suppress("UNCHECKED_CAST")
            val attrDef = attrDefRaw as Map<String, Any>
            val attrId = UUID.randomUUID()
            attributeKeyMap[attrKey] = attrId

            val isIdentifier = catalogType.identifierKey == attrKey

            properties[attrId] = Schema(
                key = SchemaType.valueOf(attrDef["key"] as String),
                type = parseDataType(attrDef["type"] as String),
                label = attrDef["label"] as? String,
                format = parseDataFormat(attrDef["format"] as? String),
                required = attrDef["required"] as? Boolean ?: false,
                unique = attrDef["unique"] as? Boolean ?: false,
                protected = isIdentifier || (attrDef["protected"] as? Boolean ?: false),
                options = parseSchemaOptions(attrDef["options"]),
            )

            columns.add(EntityTypeAttributeColumn(key = attrId, type = EntityPropertyType.ATTRIBUTE))
        }

        val identifierKey = catalogType.identifierKey?.let { attributeKeyMap[it] }
            ?: attributeKeyMap.values.first()

        val entity = EntityTypeEntity(
            key = catalogType.key,
            displayNameSingular = catalogType.displayNameSingular,
            displayNamePlural = catalogType.displayNamePlural,
            iconType = catalogType.iconType,
            iconColour = catalogType.iconColour,
            semanticGroup = catalogType.semanticGroup,
            identifierKey = identifierKey,
            workspaceId = workspaceId,
            protected = false,
            schema = Schema(
                type = DataType.OBJECT,
                key = SchemaType.OBJECT,
                protected = true,
                required = true,
                properties = properties,
            ),
            columns = columns,
        )

        return entity to attributeKeyMap
    }

    // ------ Relationship Creation ------

    /**
     * Creates relationship definitions from catalog relationship models, resolving string-keyed
     * entity type references to workspace UUID-keyed references.
     *
     * Relationship semantic metadata is looked up from the source entity type's semantic metadata
     * entries where targetType is RELATIONSHIP and targetId matches the relationship key.
     */
    private fun createRelationships(
        workspaceId: UUID,
        catalogRelationships: List<CatalogRelationshipModel>,
        catalogEntityTypes: List<CatalogEntityTypeModel>,
        creationResults: Map<String, EntityTypeCreationResult>,
    ): Int {
        val semanticsByEntityTypeKey = catalogEntityTypes.associate { et ->
            et.key to et.semanticMetadata
        }

        var count = 0
        for (catalogRel in catalogRelationships) {
            val sourceResult = creationResults[catalogRel.sourceEntityTypeKey]
                ?: error("Unknown source entity type key: ${catalogRel.sourceEntityTypeKey}")

            val targetRules = catalogRel.targetRules.map { rule ->
                val targetResult = creationResults[rule.targetEntityTypeKey]
                    ?: error("Unknown target entity type key: ${rule.targetEntityTypeKey}")
                SaveTargetRuleRequest(
                    targetEntityTypeId = targetResult.entityTypeId,
                    semanticTypeConstraint = rule.semanticTypeConstraint,
                    cardinalityOverride = rule.cardinalityOverride,
                    inverseName = rule.inverseName ?: "",
                )
            }

            val relationshipSemantics = findRelationshipSemantics(
                semanticsByEntityTypeKey[catalogRel.sourceEntityTypeKey],
                catalogRel.key,
            )

            val request = SaveRelationshipDefinitionRequest(
                key = catalogRel.key,
                name = catalogRel.name,
                iconType = catalogRel.iconType,
                iconColour = catalogRel.iconColour,
                allowPolymorphic = catalogRel.allowPolymorphic,
                cardinalityDefault = catalogRel.cardinalityDefault,
                targetRules = targetRules,
                semantics = relationshipSemantics,
            )

            relationshipService.createRelationshipDefinition(workspaceId, sourceResult.entityTypeId, request)
            count++

            logger.info { "Created relationship '${catalogRel.name}' from ${catalogRel.sourceEntityTypeKey}" }
        }

        return count
    }

    /**
     * Finds relationship semantic metadata from the source entity type's semantic metadata entries.
     *
     * Relationship semantics are stored in the CatalogSemanticMetadataModel list on the source
     * entity type with targetType = RELATIONSHIP and targetId matching the relationship key.
     */
    private fun findRelationshipSemantics(
        semanticMetadata: List<CatalogSemanticMetadataModel>?,
        relationshipKey: String,
    ): SaveSemanticMetadataRequest? {
        val match = semanticMetadata?.find { meta ->
            meta.targetType == SemanticMetadataTargetType.RELATIONSHIP && meta.targetId == relationshipKey
        } ?: return null

        return SaveSemanticMetadataRequest(
            definition = match.definition,
            classification = match.classification,
            tags = match.tags,
        )
    }

    // ------ Semantic Metadata Application ------

    /**
     * Applies semantic metadata from catalog entries onto the newly created workspace entity types
     * and their attributes. Entity-type-level and attribute-level metadata are applied here;
     * relationship-level metadata is handled during relationship creation.
     */
    private fun applySemanticMetadata(
        workspaceId: UUID,
        catalogEntityTypes: List<CatalogEntityTypeModel>,
        creationResults: Map<String, EntityTypeCreationResult>,
    ) {
        for (catalogType in catalogEntityTypes) {
            val result = creationResults[catalogType.key] ?: continue
            val entityTypeId = result.entityTypeId

            for (metadata in catalogType.semanticMetadata) {
                val targetId = when (metadata.targetType) {
                    SemanticMetadataTargetType.ENTITY_TYPE -> entityTypeId
                    SemanticMetadataTargetType.ATTRIBUTE -> {
                        result.attributeKeyMap[metadata.targetId] ?: continue
                    }
                    SemanticMetadataTargetType.RELATIONSHIP -> continue
                }

                semanticMetadataService.upsertMetadataInternal(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    targetType = metadata.targetType,
                    targetId = targetId,
                    request = SaveSemanticMetadataRequest(
                        definition = metadata.definition,
                        classification = metadata.classification,
                        tags = metadata.tags,
                    ),
                )
            }
        }
    }

    // ------ Activity Logging ------

    private fun logTemplateActivity(
        userId: UUID,
        workspaceId: UUID,
        templateKey: String,
        manifest: ManifestDetail,
        creationResults: Map<String, EntityTypeCreationResult>,
    ) {
        activityService.log(
            activity = Activity.TEMPLATE,
            operation = OperationType.CREATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.ENTITY_TYPE,
            entityId = null,
            "templateKey" to templateKey,
            "templateName" to manifest.name,
            "entityTypesCreated" to creationResults.size,
        )
    }

    // ------ Response Building ------

    private fun buildResponse(
        templateKey: String,
        manifest: ManifestDetail,
        creationResults: Map<String, EntityTypeCreationResult>,
        relationshipsCreated: Int,
    ): TemplateInstallationResponse {
        return TemplateInstallationResponse(
            templateKey = templateKey,
            templateName = manifest.name,
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

            results[key] = EntityTypeCreationResult(
                entityTypeId = entityId,
                attributeKeyMap = emptyMap(),
                attributeCount = entity.schema.properties?.size ?: 0,
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

    // ------ Schema Parsing Helpers ------

    /**
     * Parses a manifest type string (lowercase) to DataType enum (uppercase).
     */
    private fun parseDataType(typeStr: String): DataType =
        DataType.valueOf(typeStr.uppercase())

    /**
     * Maps manifest format strings to DataFormat enum values.
     *
     * Manifest format strings follow JSON Schema conventions (e.g., "date-time", "phone-number")
     * while DataFormat uses different naming. Direct valueOf() is not safe.
     */
    private fun parseDataFormat(formatStr: String?): DataFormat? {
        if (formatStr == null) return null
        return FORMAT_MAPPING[formatStr]
            ?: throw IllegalArgumentException("Unknown manifest format: $formatStr")
    }

    /**
     * Parses a manifest options map into SchemaOptions.
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseSchemaOptions(optionsRaw: Any?): Schema.SchemaOptions? {
        if (optionsRaw == null) return null
        val opts = optionsRaw as? Map<String, Any> ?: return null

        return Schema.SchemaOptions(
            default = null,
            regex = opts["regex"] as? String,
            enum = (opts["enum"] as? List<*>)?.map { it.toString() },
            minLength = (opts["minLength"] as? Number)?.toInt(),
            maxLength = (opts["maxLength"] as? Number)?.toInt(),
            minimum = (opts["minimum"] as? Number)?.toDouble(),
            maximum = (opts["maximum"] as? Number)?.toDouble(),
        )
    }

    companion object {
        /** Mapping from manifest format strings to DataFormat enum values. */
        private val FORMAT_MAPPING = mapOf(
            "date" to DataFormat.DATE,
            "date-time" to DataFormat.DATETIME,
            "email" to DataFormat.EMAIL,
            "phone-number" to DataFormat.PHONE,
            "currency" to DataFormat.CURRENCY,
            "uri" to DataFormat.URL,
            "percentage" to DataFormat.PERCENTAGE,
        )
    }
}

/**
 * Tracks the result of creating a single entity type from a catalog definition.
 */
data class EntityTypeCreationResult(
    val entityTypeId: UUID,
    val attributeKeyMap: Map<String, UUID>,
    val attributeCount: Int,
    val displayName: String,
    val key: String,
)
