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
import org.springframework.dao.DataIntegrityViolationException
import riven.core.exceptions.ConflictException
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

        val (toCreate, reused) = partitionEntityTypesByExistence(workspaceId, manifest.entityTypes)
        val newResults = createEntityTypes(workspaceId, toCreate)
        val mergedResults = newResults + reused

        val relationshipsCreated = createRelationships(workspaceId, manifest.relationships, manifest.entityTypes, mergedResults)
        applySemanticMetadata(workspaceId, toCreate, newResults)
        recordTemplateInstallation(workspaceId, templateKey, userId, mergedResults)
        logTemplateActivity(userId, workspaceId, templateKey, manifest, newResults)

        return buildResponse(templateKey, manifest, newResults, relationshipsCreated)
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
        val (manifests, allCandidateEntityTypes) = loadManifestsAndMergeEntityTypes(templatesToInstall, skippedEntityIdMap)

        val (toCreate, reusedFromWorkspace) = partitionEntityTypesByExistence(workspaceId, allCandidateEntityTypes)
        val creationResults = createEntityTypes(workspaceId, toCreate)
        val mergedIdMap = creationResults + reusedFromWorkspace + skippedEntityIdMap

        val relationshipsCreated = prepareAndCreateRelationships(workspaceId, manifests, mergedIdMap)
        applySemanticMetadata(workspaceId, toCreate, creationResults)
        recordBundleTemplateInstallations(workspaceId, manifests, templatesToInstall, mergedIdMap, userId)
        logBundleActivity(userId, workspaceId, bundleKey, bundle, creationResults, templatesToInstall, templatesToSkip)

        return buildBundleResponse(bundleKey, bundle, templatesToInstall, templatesToSkip, creationResults, relationshipsCreated)
    }

    // ------ Entity Type Creation ------

    /**
     * Splits manifest entity types into those that need creating vs those that already exist
     * in the workspace. Reused entities are returned as EntityTypeCreationResult with empty
     * attributeKeyMap (attribute-level metadata is already applied).
     */
    private fun partitionEntityTypesByExistence(
        workspaceId: UUID,
        catalogEntityTypes: List<CatalogEntityTypeModel>,
    ): Pair<List<CatalogEntityTypeModel>, Map<String, EntityTypeCreationResult>> {
        if (catalogEntityTypes.isEmpty()) return emptyList<CatalogEntityTypeModel>() to emptyMap()

        val keys = catalogEntityTypes.map { it.key }
        val existing = entityTypeRepository.findByworkspaceIdAndKeyIn(workspaceId, keys)
        val existingByKey = existing.associateBy { it.key }

        val toCreate = mutableListOf<CatalogEntityTypeModel>()
        val reused = mutableMapOf<String, EntityTypeCreationResult>()

        for (catalogType in catalogEntityTypes) {
            val entity = existingByKey[catalogType.key]
            if (entity != null) {
                val entityId = requireNotNull(entity.id)
                reused[catalogType.key] = EntityTypeCreationResult(
                    entityTypeId = entityId,
                    attributeKeyMap = emptyMap(),
                    attributeCount = entity.schema.properties?.size ?: 0,
                    displayName = entity.displayNameSingular,
                    key = catalogType.key,
                )
                logger.info { "Reusing existing entity type '${catalogType.key}' ($entityId)" }
            } else {
                toCreate.add(catalogType)
            }
        }

        return toCreate to reused
    }

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
        val (properties, columns, attributeKeyMap) = buildAttributeSchema(catalogType)

        require(catalogType.identifierKey == null || catalogType.identifierKey in attributeKeyMap) {
            "identifierKey '${catalogType.identifierKey}' not found in schema attributes for entity type '${catalogType.key}'"
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

    /**
     * Translates a catalog entity type's string-keyed schema into UUID-keyed properties and columns.
     */
    private fun buildAttributeSchema(
        catalogType: CatalogEntityTypeModel,
    ): Triple<Map<UUID, Schema<UUID>>, List<EntityTypeAttributeColumn>, Map<String, UUID>> {
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

        return Triple(properties, columns, attributeKeyMap)
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
                ?: throw IllegalArgumentException(
                    "Relationship '${catalogRel.key}' references source entity type '${catalogRel.sourceEntityTypeKey}' " +
                        "which is not present in this installation context. This is likely a cross-template dependency — " +
                        "install the template that provides '${catalogRel.sourceEntityTypeKey}' first, or install via a bundle."
                )

            val targetRules = resolveRelationshipTargetRules(catalogRel, creationResults)
            val semantics = findRelationshipSemantics(
                semanticsByEntityTypeKey[catalogRel.sourceEntityTypeKey], catalogRel.key,
            )
            val request = buildRelationshipRequest(catalogRel, targetRules, semantics)

            relationshipService.createRelationshipDefinition(workspaceId, sourceResult.entityTypeId, request)
            count++

            logger.info { "Created relationship '${catalogRel.name}' from ${catalogRel.sourceEntityTypeKey}" }
        }

        return count
    }

    /**
     * Resolves catalog target rules to workspace-scoped SaveTargetRuleRequests by mapping
     * string entity type keys to their created UUIDs.
     */
    private fun resolveRelationshipTargetRules(
        catalogRel: CatalogRelationshipModel,
        creationResults: Map<String, EntityTypeCreationResult>,
    ): List<SaveTargetRuleRequest> {
        return catalogRel.targetRules.map { rule ->
            val targetResult = creationResults[rule.targetEntityTypeKey]
                ?: throw IllegalArgumentException(
                    "Relationship '${catalogRel.key}' references target entity type '${rule.targetEntityTypeKey}' " +
                        "which is not present in this installation context. This is likely a cross-template dependency — " +
                        "install the template that provides '${rule.targetEntityTypeKey}' first, or install via a bundle."
                )
            SaveTargetRuleRequest(
                targetEntityTypeId = targetResult.entityTypeId,
                semanticTypeConstraint = rule.semanticTypeConstraint,
                cardinalityOverride = rule.cardinalityOverride,
                inverseName = rule.inverseName ?: "",
            )
        }
    }

    /** Builds a SaveRelationshipDefinitionRequest from catalog data and resolved target rules. */
    private fun buildRelationshipRequest(
        catalogRel: CatalogRelationshipModel,
        targetRules: List<SaveTargetRuleRequest>,
        semantics: SaveSemanticMetadataRequest?,
    ): SaveRelationshipDefinitionRequest {
        return SaveRelationshipDefinitionRequest(
            key = catalogRel.key,
            name = catalogRel.name,
            iconType = catalogRel.iconType,
            iconColour = catalogRel.iconColour,
            allowPolymorphic = catalogRel.allowPolymorphic,
            cardinalityDefault = catalogRel.cardinalityDefault,
            targetRules = targetRules,
            semantics = semantics,
        )
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
                    SemanticMetadataTargetType.ATTRIBUTE -> requireNotNull(result.attributeKeyMap[metadata.targetId]) {
                        "Semantic metadata references unknown attribute '${metadata.targetId}' on entity type '${catalogType.key}'"
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
     * Loads manifests for the given template keys and merges their entity types,
     * filtering out entity types that already exist from skipped templates.
     *
     * @return the loaded manifests and the deduplicated entity types to create
     */
    private fun loadManifestsAndMergeEntityTypes(
        templatesToInstall: List<String>,
        skippedEntityIdMap: Map<String, EntityTypeCreationResult>,
    ): Pair<List<ManifestDetail>, List<CatalogEntityTypeModel>> {
        val manifests = templatesToInstall.map { key ->
            catalogService.getManifestByKey(key, ManifestType.TEMPLATE)
        }
        val allEntityTypes = mergeAndDeduplicateEntityTypes(manifests)
            .filter { it.key !in skippedEntityIdMap }
        return manifests to allEntityTypes
    }

    /** Gathers relationships and entity types from all manifests and creates them. */
    private fun prepareAndCreateRelationships(
        workspaceId: UUID,
        manifests: List<ManifestDetail>,
        mergedIdMap: Map<String, EntityTypeCreationResult>,
    ): Int {
        val allRelationships = manifests.flatMap { it.relationships }
        val allCatalogEntityTypes = manifests.flatMap { it.entityTypes }
        return createRelationships(workspaceId, allRelationships, allCatalogEntityTypes, mergedIdMap)
    }

    /** Records a template installation entry for each template in the bundle. */
    private fun recordBundleTemplateInstallations(
        workspaceId: UUID,
        manifests: List<ManifestDetail>,
        templatesToInstall: List<String>,
        mergedIdMap: Map<String, EntityTypeCreationResult>,
        userId: UUID,
    ) {
        val manifestsByKey = manifests.associateBy { it.key }
        for (templateKey in templatesToInstall) {
            val templateEntityKeys = manifestsByKey[templateKey]?.entityTypes?.map { it.key }?.toSet() ?: emptySet()
            val templateScopedMappings = mergedIdMap.filterKeys { it in templateEntityKeys }
            recordTemplateInstallation(workspaceId, templateKey, userId, templateScopedMappings)
        }
    }

    private fun buildBundleResponse(
        bundleKey: String,
        bundle: BundleDetail,
        templatesToInstall: List<String>,
        templatesToSkip: List<String>,
        creationResults: Map<String, EntityTypeCreationResult>,
        relationshipsCreated: Int,
    ): BundleInstallationResponse {
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
            val entity = requireNotNull(entityByKey[key]) {
                "Entity type '$key' is declared in a previously installed template but does not exist in workspace $workspaceId"
            }
            val entityId = requireNotNull(entity.id) {
                "Entity type '$key' exists in workspace $workspaceId but has a null ID"
            }

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
     * When the same entity type key appears in multiple templates, schemas are merged
     * additively — attributes from later templates are added to the first occurrence.
     * Conflicting attribute definitions (same key, different definition) use first-wins
     * with a WARN log.
     */
    private fun mergeAndDeduplicateEntityTypes(
        manifests: List<ManifestDetail>,
    ): List<CatalogEntityTypeModel> {
        val mergedByKey = mutableMapOf<String, CatalogEntityTypeModel>()
        for (manifest in manifests) {
            for (entityType in manifest.entityTypes) {
                val existing = mergedByKey[entityType.key]
                if (existing == null) {
                    mergedByKey[entityType.key] = entityType
                } else {
                    mergedByKey[entityType.key] = mergeEntityTypeSchemas(existing, entityType)
                }
            }
        }
        return mergedByKey.values.toList()
    }

    /**
     * Merges two catalog entity types with the same key by consolidating their schemas
     * and semantic metadata. Attributes from the incoming type are added to the base if
     * not already present. Conflicting definitions (same attribute key, different definition)
     * keep the base version (first wins) and log a warning.
     */
    private fun mergeEntityTypeSchemas(
        base: CatalogEntityTypeModel,
        incoming: CatalogEntityTypeModel,
    ): CatalogEntityTypeModel {
        val mergedSchema = base.schema.toMutableMap()
        for ((attrKey, attrDef) in incoming.schema) {
            val existingDef = mergedSchema[attrKey]
            if (existingDef == null) {
                mergedSchema[attrKey] = attrDef
            } else if (existingDef != attrDef) {
                logger.warn { "Attribute conflict on entity type '${base.key}', attribute '$attrKey': keeping first definition, ignoring incoming" }
            }
        }

        val existingMetadataKeys = base.semanticMetadata
            .map { it.targetType to it.targetId }
            .toSet()
        val additionalMetadata = incoming.semanticMetadata.filter { meta ->
            (meta.targetType to meta.targetId) !in existingMetadataKeys
        }
        val mergedMetadata = base.semanticMetadata + additionalMetadata

        return base.copy(
            schema = mergedSchema,
            semanticMetadata = mergedMetadata,
        )
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

        try {
            installationRepository.save(
                WorkspaceTemplateInstallationEntity(
                    workspaceId = workspaceId,
                    manifestKey = templateKey,
                    installedBy = userId,
                    attributeMappings = attributeMappings,
                )
            )
        } catch (e: DataIntegrityViolationException) {
            throw ConflictException("Template '$templateKey' is already installed in workspace $workspaceId")
        }
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
            default = opts["default"],
            prefix = opts["prefix"] as? String,
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
