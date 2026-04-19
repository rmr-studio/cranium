package riven.core.service.dev

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.models.response.catalog.TemplateInstallationResponse
import riven.core.service.catalog.TemplateInstallationService
import riven.core.enums.common.validation.SchemaType
import riven.core.models.core.CoreModelDefinition
import riven.core.models.core.CoreModelRegistry
import riven.core.models.core.CoreModelSet
import riven.core.models.common.json.JsonValue
import riven.core.models.entity.EntityType
import riven.core.models.entity.RelationshipDefinition
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.models.entity.payload.EntityAttributeRequest
import riven.core.models.request.entity.AddRelationshipRequest
import riven.core.models.request.entity.SaveEntityRequest
import riven.core.models.response.dev.DevSeedResponse
import riven.core.models.response.dev.EntityTypeSeedDetail
import riven.core.repository.catalog.WorkspaceTemplateInstallationRepository
import riven.core.service.entity.EntityRelationshipService
import riven.core.service.entity.EntityService
import riven.core.service.entity.type.EntityTypeRelationshipService
import riven.core.service.entity.type.EntityTypeService
import java.util.*
import kotlin.random.Random

/**
 * Dev-only service that seeds a workspace with realistic mock entity data
 * based on its installed template (B2C SaaS or DTC E-commerce).
 */
@Service
@ConditionalOnProperty(name = ["riven.dev.seed.enabled"], havingValue = "true")
class DevSeedService(
    private val logger: KLogger,
    private val installationRepository: WorkspaceTemplateInstallationRepository,
    private val entityTypeService: EntityTypeService,
    private val entityService: EntityService,
    private val entityRelationshipService: EntityRelationshipService,
    private val entityTypeRelationshipService: EntityTypeRelationshipService,
    private val dataGenerator: DevSeedDataGenerator,
    private val templateInstallationService: TemplateInstallationService,
) {

    private val random = Random(42)

    // ------ Public API ------

    /**
     * Seeds a workspace with mock entities and relationships based on its installed template.
     * Idempotent — returns early if entities already exist for any template type.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun seedWorkspace(workspaceId: UUID): DevSeedResponse {
        val (templateKey, modelSet) = resolveTemplate(workspaceId)
            ?: return DevSeedResponse(templateKey = null)

        val entityTypes = entityTypeService.getWorkspaceEntityTypes(workspaceId)
        val templateTypes = matchTemplateTypes(entityTypes, modelSet)

        if (templateTypes.isEmpty()) {
            logger.warn { "No template entity types found in workspace $workspaceId for template $templateKey" }
            return DevSeedResponse(templateKey = templateKey)
        }

        if (isAlreadySeeded(templateTypes)) {
            logger.info { "Workspace $workspaceId already seeded" }
            return DevSeedResponse(alreadySeeded = true, templateKey = templateKey)
        }

        val attributeMappings = loadAttributeMappings(workspaceId, templateKey)

        val createdEntities = createEntities(workspaceId, modelSet, templateTypes, attributeMappings)

        val relationshipsCreated = createRelationships(workspaceId, templateTypes, createdEntities)

        val details = buildDetails(templateTypes, createdEntities, relationshipsCreated)

        return DevSeedResponse(
            templateKey = templateKey,
            entitiesCreated = createdEntities.values.sumOf { it.size },
            relationshipsCreated = relationshipsCreated.values.sum(),
            details = details,
        )
    }

    /**
     * Dev-only: force re-installation of a template by wiping the existing installation
     * record and delegating to [TemplateInstallationService.installTemplate]. Existing
     * workspace entity types with matching keys are reused (see `partitionEntityTypesByExistence`);
     * missing types are created. Intended for local iteration on template manifests.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun reinstallTemplate(workspaceId: UUID, templateKey: String): TemplateInstallationResponse {
        installationRepository.findByWorkspaceIdAndManifestKey(workspaceId, templateKey)?.let {
            installationRepository.delete(it)
            installationRepository.flush()
            logger.info { "Removed existing installation record for template '$templateKey' in workspace $workspaceId" }
        }
        return templateInstallationService.installTemplate(workspaceId, templateKey)
    }

    // ------ Template Resolution ------

    private fun resolveTemplate(workspaceId: UUID): Pair<String, CoreModelSet>? {
        val installations = installationRepository.findByWorkspaceId(workspaceId)
        if (installations.isEmpty()) {
            logger.warn { "No template installed in workspace $workspaceId" }
            return null
        }

        val manifestKey = installations.first().manifestKey
        val modelSet = CoreModelRegistry.findModelSet(manifestKey)
        if (modelSet == null) {
            logger.warn { "Unknown template manifest key: $manifestKey" }
            return null
        }

        return manifestKey to modelSet
    }

    private fun matchTemplateTypes(
        entityTypes: List<EntityType>,
        modelSet: CoreModelSet,
    ): Map<String, EntityType> {
        val modelKeys = modelSet.models.map { it.key }.toSet()
        return entityTypes
            .filter { it.key in modelKeys }
            .associateBy { it.key }
    }

    private fun isAlreadySeeded(templateTypes: Map<String, EntityType>): Boolean {
        return templateTypes.values.any { it.entitiesCount > 0 }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadAttributeMappings(workspaceId: UUID, templateKey: String): Map<String, Map<String, String>> {
        val installation = installationRepository.findByWorkspaceIdAndManifestKey(workspaceId, templateKey)
            ?: return emptyMap()

        return installation.attributeMappings.mapValues { (_, value) ->
            (value as? Map<String, String>) ?: emptyMap()
        }
    }

    // ------ Entity Creation ------

    private fun createEntities(
        workspaceId: UUID,
        modelSet: CoreModelSet,
        templateTypes: Map<String, EntityType>,
        attributeMappings: Map<String, Map<String, String>>,
    ): Map<String, List<UUID>> {
        val createdEntities = mutableMapOf<String, MutableList<UUID>>()

        for (model in modelSet.models) {
            val entityType = templateTypes[model.key] ?: continue
            val entityTypeId = entityType.id
            val attrMapping = attributeMappings[model.key] ?: continue

            val sampleData = dataGenerator.generate(model)
            val entityIds = mutableListOf<UUID>()

            for (record in sampleData) {
                val payload = buildEntityPayload(record, attrMapping, model)
                val response = entityService.saveEntity(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    request = SaveEntityRequest(payload = payload),
                )
                response.entity?.id?.let { entityIds.add(it) }
                    ?: logger.warn { "Failed to create ${model.key} entity: ${response.errors}" }
            }

            createdEntities[model.key] = entityIds
            logger.info { "Created ${entityIds.size} ${model.key} entities" }
        }

        return createdEntities
    }

    private fun buildEntityPayload(
        record: Map<String, JsonValue>,
        attrMapping: Map<String, String>,
        model: CoreModelDefinition,
    ): Map<UUID, EntityAttributeRequest> {
        return record.mapNotNull { (attrKey, value) ->
            if (value == null) return@mapNotNull null
            val uuidStr = attrMapping[attrKey] ?: return@mapNotNull null
            val uuid = UUID.fromString(uuidStr)
            val attr = model.attributes[attrKey] ?: return@mapNotNull null

            uuid to EntityAttributeRequest(
                payload = EntityAttributePrimitivePayload(
                    value = value,
                    schemaType = attr.schemaType,
                )
            )
        }.toMap()
    }

    // ------ Relationship Creation ------

    private fun createRelationships(
        workspaceId: UUID,
        templateTypes: Map<String, EntityType>,
        createdEntities: Map<String, List<UUID>>,
    ): Map<String, Int> {
        val entityTypeIds = templateTypes.values.mapNotNull { it.id }
        val definitionsByType = entityTypeRelationshipService.getDefinitionsForEntityTypes(
            workspaceId, entityTypeIds,
        )

        val typeIdToKey = templateTypes.entries.associate { (key, type) -> type.id to key }
        val relationshipCounts = mutableMapOf<String, Int>()

        for ((typeId, definitions) in definitionsByType) {
            val sourceKey = typeIdToKey[typeId] ?: continue
            val sourceEntityIds = createdEntities[sourceKey] ?: continue
            if (sourceEntityIds.isEmpty()) continue

            for (definition in definitions) {
                if (definition.isPolymorphic) continue
                if (definition.sourceEntityTypeId != typeId) continue

                for (targetRule in definition.targetRules) {
                    val targetKey = typeIdToKey[targetRule.targetEntityTypeId] ?: continue
                    val targetEntityIds = createdEntities[targetKey] ?: continue
                    if (targetEntityIds.isEmpty()) continue

                    val count = wireRelationships(
                        workspaceId, definition, sourceEntityIds, targetEntityIds,
                    )
                    relationshipCounts[sourceKey] = (relationshipCounts[sourceKey] ?: 0) + count
                }
            }
        }

        return relationshipCounts
    }

    private fun wireRelationships(
        workspaceId: UUID,
        definition: RelationshipDefinition,
        sourceEntityIds: List<UUID>,
        targetEntityIds: List<UUID>,
    ): Int {
        var count = 0

        for (sourceId in sourceEntityIds) {
            val targetId = targetEntityIds[random.nextInt(targetEntityIds.size)]
            try {
                entityRelationshipService.addRelationship(
                    workspaceId = workspaceId,
                    sourceEntityId = sourceId,
                    request = AddRelationshipRequest(
                        targetEntityId = targetId,
                        definitionId = definition.id,
                    ),
                )
                count++
            } catch (e: Exception) {
                logger.debug { "Skipping relationship: ${e.message}" }
            }
        }

        return count
    }

    // ------ Response Building ------

    private fun buildDetails(
        templateTypes: Map<String, EntityType>,
        createdEntities: Map<String, List<UUID>>,
        relationshipCounts: Map<String, Int>,
    ): Map<String, EntityTypeSeedDetail> {
        return templateTypes.map { (key, type) ->
            key to EntityTypeSeedDetail(
                entityTypeKey = key,
                entityTypeId = type.id,
                entitiesCreated = createdEntities[key]?.size ?: 0,
                relationshipsCreated = relationshipCounts[key] ?: 0,
            )
        }.toMap()
    }
}
