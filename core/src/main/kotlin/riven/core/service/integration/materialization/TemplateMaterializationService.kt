package riven.core.service.integration.materialization

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.catalog.CatalogEntityTypeEntity
import riven.core.entity.catalog.CatalogRelationshipEntity
import riven.core.entity.catalog.CatalogRelationshipTargetRuleEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.entity.entity.RelationshipDefinitionEntity
import riven.core.entity.entity.RelationshipTargetRuleEntity
import riven.core.enums.catalog.ManifestType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataFormat
import riven.core.enums.core.DataType
import riven.core.enums.entity.EntityPropertyType
import riven.core.enums.integration.SourceType
import riven.core.exceptions.NotFoundException
import riven.core.models.common.Icon
import riven.core.models.common.validation.Schema
import riven.core.models.entity.EntityTypeSchema
import riven.core.models.entity.configuration.EntityTypeAttributeColumn
import riven.core.models.integration.materialization.MaterializationResult
import riven.core.repository.catalog.CatalogEntityTypeRepository
import riven.core.repository.catalog.CatalogRelationshipRepository
import riven.core.repository.catalog.CatalogRelationshipTargetRuleRepository
import riven.core.repository.catalog.ManifestCatalogRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.entity.RelationshipTargetRuleRepository
import java.util.*

/**
 * Creates workspace-scoped entity types and relationships from catalog definitions
 * when an integration is connected. Bridges the global catalog (string-keyed) and
 * workspace-scoped entity types (UUID-keyed).
 */
@Service
class TemplateMaterializationService(
    private val entityTypeRepository: EntityTypeRepository,
    private val relationshipDefinitionRepository: RelationshipDefinitionRepository,
    private val relationshipTargetRuleRepository: RelationshipTargetRuleRepository,
    private val catalogEntityTypeRepository: CatalogEntityTypeRepository,
    private val catalogRelationshipRepository: CatalogRelationshipRepository,
    private val catalogRelationshipTargetRuleRepository: CatalogRelationshipTargetRuleRepository,
    private val manifestCatalogRepository: ManifestCatalogRepository,
    private val objectMapper: ObjectMapper,
    private val logger: KLogger
) {

    /**
     * Materialize all integration templates for a workspace.
     *
     * Creates workspace-scoped EntityTypeEntity and RelationshipDefinitionEntity instances
     * from catalog definitions. Uses deterministic UUID v3 for attribute key mapping.
     * Entity types are saved and flushed first so their IDs are available for relationship FKs.
     *
     * @param workspaceId the workspace to materialize into
     * @param integrationSlug the integration slug (e.g. "hubspot")
     * @return result with counts of created/restored entity types and relationships
     */
    @Transactional
    fun materializeIntegrationTemplates(workspaceId: UUID, integrationSlug: String): MaterializationResult {
        val manifest = manifestCatalogRepository.findByKeyAndManifestType(integrationSlug, ManifestType.INTEGRATION)
            ?: throw NotFoundException("Integration manifest not found for slug: $integrationSlug")

        val catalogEntityTypes = catalogEntityTypeRepository.findByManifestId(manifest.id!!)
        val catalogRelationships = catalogRelationshipRepository.findByManifestId(manifest.id!!)

        if (catalogEntityTypes.isEmpty()) {
            return MaterializationResult(0, 0, 0, integrationSlug)
        }

        val entityTypeKeys = catalogEntityTypes.map { it.key }
        val existingEntityTypes = entityTypeRepository.findByworkspaceIdAndKeyIn(workspaceId, entityTypeKeys)
        val softDeletedEntityTypes = entityTypeRepository.findSoftDeletedByWorkspaceIdAndKeyIn(workspaceId, entityTypeKeys)

        val entityTypeMaterializationResult = materializeEntityTypes(
            workspaceId, integrationSlug, catalogEntityTypes, existingEntityTypes, softDeletedEntityTypes
        )

        val relationshipsCreated = materializeRelationships(
            workspaceId, catalogRelationships, entityTypeMaterializationResult.keyToIdMap
        )

        return MaterializationResult(
            entityTypesCreated = entityTypeMaterializationResult.created,
            entityTypesRestored = entityTypeMaterializationResult.restored,
            relationshipsCreated = relationshipsCreated,
            integrationSlug = integrationSlug
        )
    }

    // ------ Entity Type Materialization ------

    /**
     * Materializes entity types: creates new ones, restores soft-deleted ones, and skips
     * already-existing ones. Returns the key-to-UUID mapping for relationship resolution.
     */
    private fun materializeEntityTypes(
        workspaceId: UUID,
        integrationSlug: String,
        catalogEntityTypes: List<CatalogEntityTypeEntity>,
        existingEntityTypes: List<EntityTypeEntity>,
        softDeletedEntityTypes: List<EntityTypeEntity>
    ): EntityTypeMaterializationResult {
        val existingKeys = existingEntityTypes.map { it.key }.toSet()
        val softDeletedByKey = softDeletedEntityTypes.associateBy { it.key }
        val keyToIdMap = mutableMapOf<String, UUID>()
        var created = 0
        var restored = 0

        // Include already-existing entity type IDs in the map for relationship resolution
        existingEntityTypes.forEach { keyToIdMap[it.key] = it.id!! }

        for (catalogType in catalogEntityTypes) {
            val softDeleted = softDeletedByKey[catalogType.key]

            if (softDeleted != null) {
                val restoredEntity = restoreEntityType(softDeleted)
                keyToIdMap[catalogType.key] = restoredEntity.id!!
                restored++
            } else if (catalogType.key !in existingKeys) {
                val newEntity = createEntityType(workspaceId, integrationSlug, catalogType)
                keyToIdMap[catalogType.key] = newEntity.id!!
                created++
            }
        }

        return EntityTypeMaterializationResult(created, restored, keyToIdMap)
    }

    /**
     * Creates a new workspace-scoped entity type from a catalog definition.
     */
    private fun createEntityType(
        workspaceId: UUID,
        integrationSlug: String,
        catalogType: CatalogEntityTypeEntity
    ): EntityTypeEntity {
        val schema = buildWorkspaceSchema(catalogType.schema, integrationSlug, catalogType.key)
        val identifierKey = resolveIdentifierKey(catalogType.identifierKey, integrationSlug, catalogType.key)
        val columns = buildColumnsFromSchema(schema, catalogType.columns, integrationSlug, catalogType.key)

        val entity = EntityTypeEntity(
            key = catalogType.key,
            displayNameSingular = catalogType.displayNameSingular,
            displayNamePlural = catalogType.displayNamePlural,
            iconType = catalogType.iconType,
            iconColour = catalogType.iconColour,
            semanticGroup = catalogType.semanticGroup,
            sourceType = SourceType.INTEGRATION,
            readonly = true,
            `protected` = true,
            identifierKey = identifierKey,
            workspaceId = workspaceId,
            schema = schema,
            columns = columns
        )

        return entityTypeRepository.save(entity)
    }

    /**
     * Restores a soft-deleted entity type by clearing the deleted flag and timestamp.
     */
    private fun restoreEntityType(entity: EntityTypeEntity): EntityTypeEntity {
        entity.deleted = false
        entity.deletedAt = null
        return entityTypeRepository.save(entity)
    }

    // ------ Schema Conversion ------

    /**
     * Converts a catalog schema (string-keyed Map) to a workspace Schema<UUID>
     * using deterministic UUID v3 for attribute keys.
     */
    private fun buildWorkspaceSchema(
        catalogSchema: Map<String, Any>,
        integrationSlug: String,
        entityTypeKey: String
    ): EntityTypeSchema {
        val properties = mutableMapOf<UUID, Schema<UUID>>()

        for ((attributeKey, attributeDefRaw) in catalogSchema) {
            val uuid = generateAttributeUuid(integrationSlug, entityTypeKey, attributeKey)
            val attributeDef = toStringMap(attributeDefRaw)
            properties[uuid] = buildAttributeSchema(attributeDef)
        }

        return Schema(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = properties
        )
    }

    /**
     * Builds a Schema<UUID> for a single attribute from its catalog definition map.
     */
    private fun buildAttributeSchema(attributeDef: Map<String, Any>): Schema<UUID> {
        val schemaType = parseSchemaType(attributeDef["key"] as? String)
        val dataType = parseDataType(attributeDef["type"] as? String)
        val dataFormat = parseDataFormat(attributeDef["format"] as? String)

        return Schema(
            label = attributeDef["label"] as? String,
            key = schemaType,
            type = dataType,
            format = dataFormat,
            required = attributeDef["required"] as? Boolean ?: false,
            unique = attributeDef["unique"] as? Boolean ?: false,
            `protected` = attributeDef["protected"] as? Boolean ?: false
        )
    }

    /**
     * Resolves the catalog identifier key (string) to its deterministic UUID.
     * Falls back to a random UUID if no identifier key is set.
     */
    private fun resolveIdentifierKey(
        identifierKey: String?,
        integrationSlug: String,
        entityTypeKey: String
    ): UUID {
        if (identifierKey == null) return UUID.randomUUID()
        return generateAttributeUuid(integrationSlug, entityTypeKey, identifierKey)
    }

    /**
     * Builds EntityTypeAttributeColumn list from the schema.
     * If catalog columns are present, maps them with UUID keys. Otherwise, generates from schema.
     */
    private fun buildColumnsFromSchema(
        schema: EntityTypeSchema,
        catalogColumns: List<Map<String, Any>>?,
        integrationSlug: String,
        entityTypeKey: String
    ): List<EntityTypeAttributeColumn> {
        if (catalogColumns != null) {
            return catalogColumns.mapNotNull { col ->
                val stringKey = col["key"] as? String ?: return@mapNotNull null
                val uuid = generateAttributeUuid(integrationSlug, entityTypeKey, stringKey)
                val width = (col["width"] as? Number)?.toInt() ?: 150
                EntityTypeAttributeColumn(key = uuid, type = EntityPropertyType.ATTRIBUTE, width = width)
            }
        }

        // Fallback: generate columns from schema properties
        return schema.properties?.map { (uuid, _) ->
            EntityTypeAttributeColumn(key = uuid, type = EntityPropertyType.ATTRIBUTE)
        } ?: emptyList()
    }

    // ------ Relationship Materialization ------

    /**
     * Materializes relationships from catalog definitions, resolving string entity type keys
     * to workspace-scoped UUIDs. Deduplicates on reconnect: skips existing relationships,
     * restores soft-deleted ones.
     */
    private fun materializeRelationships(
        workspaceId: UUID,
        catalogRelationships: List<CatalogRelationshipEntity>,
        keyToIdMap: Map<String, UUID>
    ): Int {
        if (catalogRelationships.isEmpty()) return 0

        val catalogRelIds = catalogRelationships.mapNotNull { it.id }
        val allTargetRules = catalogRelationshipTargetRuleRepository.findByCatalogRelationshipIdIn(catalogRelIds)
        val targetRulesByRelId = allTargetRules.groupBy { it.catalogRelationshipId }
        var relationshipsCreated = 0

        for (catalogRel in catalogRelationships) {
            val sourceEntityTypeId = keyToIdMap[catalogRel.sourceEntityTypeKey] ?: continue
            val targetRules = targetRulesByRelId[catalogRel.id] ?: emptyList()

            val created = materializeRelationship(workspaceId, catalogRel, sourceEntityTypeId, targetRules, keyToIdMap)
            if (created) relationshipsCreated++
        }

        return relationshipsCreated
    }

    /**
     * Creates a single RelationshipDefinitionEntity and its target rules.
     * Returns true if a new relationship was created or restored, false if it already existed.
     */
    private fun materializeRelationship(
        workspaceId: UUID,
        catalogRel: CatalogRelationshipEntity,
        sourceEntityTypeId: UUID,
        catalogTargetRules: List<CatalogRelationshipTargetRuleEntity>,
        keyToIdMap: Map<String, UUID>
    ): Boolean {
        // Check for existing active relationship — skip if found
        val existing = relationshipDefinitionRepository
            .findByWorkspaceIdAndSourceEntityTypeIdAndName(workspaceId, sourceEntityTypeId, catalogRel.name)
        if (existing.isPresent) {
            logger.debug { "Relationship '${catalogRel.name}' already exists for source=$sourceEntityTypeId, skipping" }
            return false
        }

        // Check for soft-deleted relationship — restore if found
        val softDeleted = relationshipDefinitionRepository
            .findSoftDeletedByWorkspaceIdAndSourceEntityTypeIdAndName(workspaceId, sourceEntityTypeId, catalogRel.name)
        if (softDeleted != null) {
            softDeleted.deleted = false
            softDeleted.deletedAt = null
            relationshipDefinitionRepository.save(softDeleted)
            logger.info { "Restored soft-deleted relationship '${catalogRel.name}' for source=$sourceEntityTypeId" }
            return true
        }

        val savedRelDef = relationshipDefinitionRepository.save(
            RelationshipDefinitionEntity(
                workspaceId = workspaceId,
                sourceEntityTypeId = sourceEntityTypeId,
                name = catalogRel.name,
                iconType = catalogRel.iconType,
                iconColour = catalogRel.iconColour,
                allowPolymorphic = catalogRel.allowPolymorphic,
                cardinalityDefault = catalogRel.cardinalityDefault,
                `protected` = true
            )
        )

        materializeTargetRules(savedRelDef.id!!, catalogTargetRules, catalogRel.name, keyToIdMap)
        return true
    }

    /**
     * Creates target rules for a relationship definition, skipping rules with unresolvable targets.
     */
    private fun materializeTargetRules(
        relationshipDefId: UUID,
        catalogTargetRules: List<CatalogRelationshipTargetRuleEntity>,
        relationshipName: String,
        keyToIdMap: Map<String, UUID>
    ) {
        for (catalogRule in catalogTargetRules) {
            val targetEntityTypeId = keyToIdMap[catalogRule.targetEntityTypeKey]
            if (targetEntityTypeId == null && catalogRule.semanticTypeConstraint == null) {
                logger.warn {
                    "Skipping target rule for relationship '$relationshipName': " +
                        "unresolvable target key '${catalogRule.targetEntityTypeKey}' and no semantic constraint"
                }
                continue
            }

            relationshipTargetRuleRepository.save(
                RelationshipTargetRuleEntity(
                    relationshipDefinitionId = relationshipDefId,
                    targetEntityTypeId = targetEntityTypeId,
                    semanticTypeConstraint = catalogRule.semanticTypeConstraint,
                    cardinalityOverride = catalogRule.cardinalityOverride,
                    inverseName = catalogRule.inverseName ?: relationshipName
                )
            )
        }
    }

    // ------ UUID Generation ------

    /**
     * Generates a deterministic UUID v3 from the integration slug, entity type key, and attribute key.
     * Same input always produces the same UUID -- idempotent across reconnections.
     */
    private fun generateAttributeUuid(integrationSlug: String, entityTypeKey: String, attributeKey: String): UUID {
        return UUID.nameUUIDFromBytes("$integrationSlug:$entityTypeKey:$attributeKey".toByteArray())
    }

    // ------ Parsing Helpers ------

    private fun parseSchemaType(value: String?): SchemaType {
        if (value == null) return SchemaType.TEXT
        return try { SchemaType.valueOf(value.uppercase()) } catch (_: Exception) { SchemaType.TEXT }
    }

    private fun parseDataType(value: String?): DataType {
        if (value == null) return DataType.STRING
        return DataType.entries.find { it.jsonValue == value } ?: DataType.STRING
    }

    private fun parseDataFormat(value: String?): DataFormat? {
        if (value == null) return null
        return DataFormat.entries.find { it.jsonValue == value }
    }

    @Suppress("UNCHECKED_CAST")
    private fun toStringMap(value: Any): Map<String, Any> {
        return value as? Map<String, Any> ?: emptyMap()
    }

    // ------ Internal Data Classes ------

    private data class EntityTypeMaterializationResult(
        val created: Int,
        val restored: Int,
        val keyToIdMap: Map<String, UUID>
    )
}
