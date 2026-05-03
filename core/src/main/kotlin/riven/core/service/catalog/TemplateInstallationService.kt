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
import riven.core.enums.core.DynamicDefaultFunction
import riven.core.enums.entity.SystemRelationshipType
import riven.core.enums.knowledge.KnowledgeEntityTypeKey
import riven.core.models.common.validation.DefaultValue
import riven.core.enums.integration.SourceType
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.enums.util.OperationType
import riven.core.models.catalog.CatalogEntityTypeModel
import riven.core.models.catalog.CatalogRelationshipModel
import riven.core.models.catalog.CatalogSemanticMetadataModel
import riven.core.models.catalog.ManifestDetail
import riven.core.models.common.validation.Schema
import riven.core.models.common.validation.SchemaOptions
import riven.core.models.entity.configuration.ColumnConfiguration
import riven.core.models.request.entity.type.SaveRelationshipDefinitionRequest
import riven.core.models.request.entity.type.SaveSemanticMetadataRequest
import riven.core.models.request.entity.type.SaveTargetRuleRequest
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
import riven.core.exceptions.SchemaValidationException
import riven.core.service.entity.EntityTypeSemanticMetadataService
import riven.core.service.entity.type.EntityTypeRelationshipService
import riven.core.service.entity.type.EntityTypeSequenceService
import riven.core.service.schema.SchemaService
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
    private val schemaService: SchemaService,
    private val sequenceService: EntityTypeSequenceService,
    private val logger: KLogger,
) {

    /**
     * Install a template into a workspace. Templates are protected sets of entity types
     * that form the lifecycle data model foundation. Entity types are marked protected=true
     * (non-deletable), core attributes are marked Schema.protected=true (immutable), and
     * sourceType is set to TEMPLATE.
     *
     * Called during workspace onboarding, or via the recovery endpoint if onboarding
     * installation failed. Idempotent — returns early if already installed.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun installTemplate(workspaceId: UUID, spineKey: String): TemplateInstallationResponse {
        val userId = authTokenService.getUserId()
        return installTemplateInternal(workspaceId, spineKey, userId)
    }

    /**
     * Internal variant of [installTemplate] without @PreAuthorize.
     *
     * Used by [riven.core.service.onboarding.OnboardingService] during onboarding when the
     * workspace was just created and the JWT does not yet contain the new workspace's role
     * authorities.
     *
     * @param userId the user performing the installation (passed explicitly since JWT may not reflect workspace membership)
     */
    @Transactional
    internal fun installTemplateInternal(workspaceId: UUID, spineKey: String, userId: UUID): TemplateInstallationResponse {
        val existingInstallation = installationRepository.findByWorkspaceIdAndManifestKey(workspaceId, spineKey)
        if (existingInstallation != null) {
            val manifest = catalogService.getManifestByKey(spineKey, ManifestType.TEMPLATE)
            return TemplateInstallationResponse(
                templateKey = spineKey,
                templateName = manifest.name,
                entityTypesCreated = 0,
                relationshipsCreated = 0,
                entityTypes = emptyList(),
            )
        }

        val manifest = catalogService.getManifestByKey(spineKey, ManifestType.TEMPLATE)

        val (toCreate, reused) = partitionEntityTypesByExistence(workspaceId, manifest.entityTypes)
        val newResults = createEntityTypesInternal(workspaceId, toCreate)
        promoteReusedToTemplate(workspaceId, reused, manifest.entityTypes)
        val mergedResults = newResults + reused

        val relationshipsCreated = createRelationshipsInternal(workspaceId, manifest.relationships, manifest.entityTypes, mergedResults, userId)
        applySemanticMetadata(workspaceId, toCreate, newResults)
        recordTemplateInstallation(workspaceId, spineKey, userId, mergedResults)
        logTemplateActivity(userId, workspaceId, spineKey, manifest, newResults)

        return buildResponse(spineKey, manifest, newResults, relationshipsCreated)
    }

    // ------ Template Promotion ------

    /**
     * Promotes reused entity types to template status. When the template installation
     * encounters entity types that already exist in the workspace (e.g. from a previously
     * installed template), those types must be upgraded to protected=true with
     * sourceType=TEMPLATE and the correct lifecycle domain.
     */
    private fun promoteReusedToTemplate(
        workspaceId: UUID,
        reused: Map<String, EntityTypeCreationResult>,
        catalogEntityTypes: List<CatalogEntityTypeModel>,
    ) {
        if (reused.isEmpty()) return

        val catalogByKey = catalogEntityTypes.associateBy { it.key }
        for ((key, result) in reused) {
            val catalogType = catalogByKey[key] ?: continue
            val lifecycleDomain = catalogType.lifecycleDomain.name

            entityTypeRepository.promoteToTemplate(
                entityTypeId = result.entityTypeId,
                workspaceId = workspaceId,
                lifecycleDomain = lifecycleDomain,
                sourceManifestId = catalogType.manifestId,
                sourceSchemaHash = catalogType.schemaHash,
            )
            // Backfill any system edges that the existing entity type may be missing.
            // getOrCreateSystemDefinition (invoked by seedKnowledgeRelationships ->
            // createSystemDefinitionInternal) is idempotent on the unique
            // (workspace_id, source_entity_type_id, system_type) constraint, so re-running
            // is a no-op when the row already exists.
            //
            // Role is intentionally not overwritten on reuse: a workspace user may have
            // customised the role on an existing entity type, and the template should
            // not silently revert that. Lifecycle domain / source manifest are aligned
            // by promoteToTemplate above; everything else is left as-is.
            seedKnowledgeRelationships(workspaceId, catalogType.key, result.entityTypeId)
            logger.info { "Promoted reused entity type '$key' (${result.entityTypeId}) to template" }
        }
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
     * metadata and system connection definitions for each entity type.
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

            relationshipService.createSystemConnectionDefinition(workspaceId, savedId)

            // Initialize sequences for ID-type attributes
            entity.schema.properties?.forEach { (attrId, attrSchema) ->
                if (attrSchema.key == SchemaType.ID) {
                    sequenceService.initializeSequence(savedId, attrId)
                }
            }

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
     * Internal variant of [createEntityTypes] that uses auth-bypass relationship methods.
     */
    private fun createEntityTypesInternal(
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

            relationshipService.createSystemConnectionDefinitionInternal(workspaceId, savedId)
            seedKnowledgeRelationships(workspaceId, catalogType.key, savedId)

            entity.schema.properties?.forEach { (attrId, attrSchema) ->
                if (attrSchema.key == SchemaType.ID) {
                    sequenceService.initializeSequence(savedId, attrId)
                }
            }

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
     * Per-key system relationship seed list for KNOWLEDGE entity types.
     * Adding a new KNOWLEDGE type means adding one entry here; the seeding
     * loop is otherwise type-agnostic.
     */
    private val knowledgeSystemEdges: Map<KnowledgeEntityTypeKey, List<SystemRelationshipType>> = mapOf(
        KnowledgeEntityTypeKey.NOTE to listOf(SystemRelationshipType.ATTACHMENT, SystemRelationshipType.MENTION),
        KnowledgeEntityTypeKey.GLOSSARY to listOf(SystemRelationshipType.DEFINES, SystemRelationshipType.MENTION),
    )

    private fun seedKnowledgeRelationships(workspaceId: UUID, key: String, entityTypeId: UUID) {
        val knowledgeKey = KnowledgeEntityTypeKey.fromKey(key) ?: return
        val edges = knowledgeSystemEdges[knowledgeKey] ?: return
        edges.forEach { edge ->
            relationshipService.getOrCreateSystemDefinitionInternal(workspaceId, entityTypeId, edge)
        }
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
        val (properties, columnOrder, attributeKeyMap) = buildAttributeSchema(catalogType)

        require(catalogType.identifierKey == null || catalogType.identifierKey in attributeKeyMap) {
            "identifierKey '${catalogType.identifierKey}' not found in schema attributes for entity type '${catalogType.key}'"
        }

        val identifierKey = catalogType.identifierKey?.let { attributeKeyMap[it] }
            ?: attributeKeyMap.values.first()

        val entity = EntityTypeEntity(
            key = catalogType.key,
            displayNameSingular = catalogType.displayNameSingular,
            displayNamePlural = catalogType.displayNamePlural,
            role = catalogType.role,
            iconType = catalogType.iconType,
            iconColour = catalogType.iconColour,
            semanticGroup = catalogType.semanticGroup,
            lifecycleDomain = catalogType.lifecycleDomain,
            sourceType = SourceType.TEMPLATE,
            sourceManifestId = catalogType.manifestId,
            sourceSchemaHash = catalogType.schemaHash,
            identifierKey = identifierKey,
            workspaceId = workspaceId,
            protected = true,
            schema = Schema(
                type = DataType.OBJECT,
                key = SchemaType.OBJECT,
                protected = true,
                required = true,
                properties = properties,
            ),
            columnConfiguration = ColumnConfiguration(order = columnOrder),
        )

        return entity to attributeKeyMap
    }

    /**
     * Translates a catalog entity type's string-keyed schema into UUID-keyed properties and columns.
     */
    private fun buildAttributeSchema(
        catalogType: CatalogEntityTypeModel,
    ): Triple<Map<UUID, Schema<UUID>>, List<UUID>, Map<String, UUID>> {
        val attributeKeyMap = mutableMapOf<String, UUID>()
        val properties = mutableMapOf<UUID, Schema<UUID>>()
        val columnOrder = mutableListOf<UUID>()

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
                protected = true,
                options = parseSchemaOptions(attrDef["options"]),
            )

            // Validate default value if present
            val attrSchema = properties[attrId]!!
            val staticDefault = attrSchema.options?.extractStaticDefault()
            if (staticDefault != null) {
                val defaultErrors = schemaService.validateDefault(attrSchema, staticDefault)
                if (defaultErrors.isNotEmpty()) {
                    throw SchemaValidationException(
                        defaultErrors.map { "Attribute '$attrKey': $it" }
                    )
                }
            }

            // Validate ID attributes have a prefix
            if (attrSchema.key == SchemaType.ID && attrSchema.options?.prefix.isNullOrBlank()) {
                throw SchemaValidationException(
                    listOf("ID attribute '$attrKey' must have a non-blank 'prefix' in options")
                )
            }

            columnOrder.add(attrId)
        }

        return Triple(properties, columnOrder, attributeKeyMap)
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
     * Internal variant of [createRelationships] that uses auth-bypass relationship methods.
     */
    private fun createRelationshipsInternal(
        workspaceId: UUID,
        catalogRelationships: List<CatalogRelationshipModel>,
        catalogEntityTypes: List<CatalogEntityTypeModel>,
        creationResults: Map<String, EntityTypeCreationResult>,
        userId: UUID,
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

            relationshipService.createRelationshipDefinitionInternal(workspaceId, sourceResult.entityTypeId, request, userId)
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

    /**
     * Records a template installation for idempotency tracking.
     * Stores attribute key mappings for idempotent re-installation and key traceability.
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
    private fun parseSchemaOptions(optionsRaw: Any?): SchemaOptions? {
        if (optionsRaw == null) return null
        val opts = optionsRaw as? Map<String, Any> ?: return null

        return SchemaOptions(
            defaultValue = parseDefaultValue(opts),
            prefix = opts["prefix"] as? String,
            regex = opts["regex"] as? String,
            enum = (opts["enum"] as? List<*>)?.map { it.toString() },
            minLength = (opts["minLength"] as? Number)?.toInt(),
            maxLength = (opts["maxLength"] as? Number)?.toInt(),
            minimum = (opts["minimum"] as? Number)?.toDouble(),
            maximum = (opts["maximum"] as? Number)?.toDouble(),
        )
    }

    /**
     * Parses the defaultValue from a manifest options map.
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseDefaultValue(opts: Map<String, Any>): DefaultValue? {
        (opts["defaultValue"] as? Map<String, Any>)?.let { dvMap ->
            return when (dvMap["type"]) {
                "static" -> DefaultValue.Static(dvMap["value"])
                "dynamic" -> DefaultValue.Dynamic(
                    DynamicDefaultFunction.valueOf(dvMap["function"] as String)
                )
                else -> null
            }
        }

        return null
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
