package riven.core.service.util.factory.entity

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import riven.core.entity.entity.EntityAttributeEntity
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityRelationshipEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.entity.entity.RelationshipDefinitionEntity
import riven.core.entity.entity.RelationshipTargetRuleEntity
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.integration.SourceType
import riven.core.models.common.validation.Schema
import riven.core.models.entity.EntityTypeSchema
import riven.core.models.entity.configuration.ColumnConfiguration
import java.util.*

object EntityFactory {

    /**
     * Creates an EntityTypeEntity with the given parameters and reasonable defaults.
     */
    fun createEntityType(
        id: UUID? = UUID.randomUUID(),
        key: String = "test_entity",
        displayNameSingular: String = "Test Entity",
        displayNamePlural: String = "Test Entities",
        workspaceId: UUID = UUID.randomUUID(),
        schema: EntityTypeSchema = createSimpleSchema(),
        columnConfiguration: ColumnConfiguration? = null,
        attributeKeyMapping: Map<String, String>? = null,
        version: Int = 1,
        protected: Boolean = false,
        readonly: Boolean = false,
        sourceType: SourceType = SourceType.USER_CREATED,
        sourceIntegrationId: UUID? = null,
        sourceManifestId: UUID? = null,
        sourceSchemaHash: String? = null,
        pendingSchemaUpdate: Boolean = false,
        deleted: Boolean = false,
        identifierKey: UUID = schema.properties?.keys?.first() ?: UUID.randomUUID(),
        semanticGroup: SemanticGroup = SemanticGroup.UNCATEGORIZED,
        lifecycleDomain: LifecycleDomain = LifecycleDomain.UNCATEGORIZED,
    ): EntityTypeEntity {
        val defaultConfig = columnConfiguration ?: ColumnConfiguration(
            order = schema.properties?.keys?.toList() ?: emptyList()
        )

        val entity = EntityTypeEntity(
            id = id,
            key = key,
            displayNameSingular = displayNameSingular,
            displayNamePlural = displayNamePlural,
            workspaceId = workspaceId,
            schema = schema,
            columnConfiguration = defaultConfig,
            attributeKeyMapping = attributeKeyMapping,
            version = version,
            protected = protected,
            readonly = readonly,
            sourceType = sourceType,
            sourceIntegrationId = sourceIntegrationId,
            sourceManifestId = sourceManifestId,
            sourceSchemaHash = sourceSchemaHash,
            pendingSchemaUpdate = pendingSchemaUpdate,
            identifierKey = identifierKey,
            semanticGroup = semanticGroup,
            lifecycleDomain = lifecycleDomain,
        )

        if (deleted) {
            entity.deleted = true
            entity.deletedAt = java.time.ZonedDateTime.now()
        }

        return entity
    }

    /**
     * Creates a simple schema with a name field for testing.
     */
    fun createSimpleSchema(properties: Map<UUID, Schema<UUID>>? = null): EntityTypeSchema {
        return Schema(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = properties ?: mapOf(
                UUID.randomUUID() to Schema(
                    key = SchemaType.TEXT,
                    type = DataType.STRING,
                    required = true
                )
            )
        )
    }

    /**
     * Creates an EntityRelationshipEntity (relationship instance) with the given parameters.
     *
     * Pass [createdAt] to override the audit timestamp set by JPA — useful for tests
     * that assert on relative recency (e.g. enrichment context "latestActivityAt").
     */
    fun createRelationshipEntity(
        id: UUID? = UUID.randomUUID(),
        workspaceId: UUID = UUID.randomUUID(),
        sourceId: UUID = UUID.randomUUID(),
        targetId: UUID = UUID.randomUUID(),
        definitionId: UUID = UUID.randomUUID(),
        semanticContext: String? = null,
        linkSource: riven.core.enums.integration.SourceType = riven.core.enums.integration.SourceType.USER_CREATED,
        targetKind: riven.core.enums.entity.RelationshipTargetKind = riven.core.enums.entity.RelationshipTargetKind.ENTITY,
        targetParentId: UUID? = null,
        createdAt: java.time.ZonedDateTime? = null,
    ): EntityRelationshipEntity {
        val rel = EntityRelationshipEntity(
            id = id,
            workspaceId = workspaceId,
            sourceId = sourceId,
            targetId = targetId,
            definitionId = definitionId,
            semanticContext = semanticContext,
            linkSource = linkSource,
            targetKind = targetKind,
            targetParentId = targetParentId,
        )
        if (createdAt != null) {
            rel.createdAt = createdAt
        }
        return rel
    }

    /**
     * Creates a RelationshipDefinitionEntity for testing.
     */
    fun createRelationshipDefinitionEntity(
        id: UUID = UUID.randomUUID(),
        workspaceId: UUID = UUID.randomUUID(),
        sourceEntityTypeId: UUID = UUID.randomUUID(),
        name: String = "Related Entity",
        cardinalityDefault: EntityRelationshipCardinality = EntityRelationshipCardinality.MANY_TO_MANY,
        protected: Boolean = false,
        systemType: riven.core.enums.entity.SystemRelationshipType? = null,
    ): RelationshipDefinitionEntity {
        return RelationshipDefinitionEntity(
            id = id,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = name,
            cardinalityDefault = cardinalityDefault,
            protected = protected,
            systemType = systemType,
        )
    }

    /**
     * Creates a RelationshipTargetRuleEntity for testing.
     */
    fun createTargetRuleEntity(
        id: UUID = UUID.randomUUID(),
        relationshipDefinitionId: UUID = UUID.randomUUID(),
        targetEntityTypeId: UUID = UUID.randomUUID(),
        cardinalityOverride: EntityRelationshipCardinality? = null,
        inverseName: String = "Inverse",
    ): RelationshipTargetRuleEntity {
        return RelationshipTargetRuleEntity(
            id = id,
            relationshipDefinitionId = relationshipDefinitionId,
            targetEntityTypeId = targetEntityTypeId,
            cardinalityOverride = cardinalityOverride,
            inverseName = inverseName,
        )
    }

    /**
     * Creates an EntityAttributeEntity with the given parameters and reasonable defaults.
     */
    fun createEntityAttributeEntity(
        id: UUID? = UUID.randomUUID(),
        entityId: UUID = UUID.randomUUID(),
        workspaceId: UUID = UUID.randomUUID(),
        typeId: UUID = UUID.randomUUID(),
        attributeId: UUID = UUID.randomUUID(),
        schemaType: SchemaType = SchemaType.TEXT,
        value: JsonNode = JsonNodeFactory.instance.stringNode("test-value"),
    ): EntityAttributeEntity {
        return EntityAttributeEntity(
            id = id,
            entityId = entityId,
            workspaceId = workspaceId,
            typeId = typeId,
            attributeId = attributeId,
            schemaType = schemaType,
            value = value,
        )
    }

    /**
     * Creates an EntityEntity with the given parameters and reasonable defaults.
     */
    fun createEntityEntity(
        id: UUID? = null,
        workspaceId: UUID = UUID.randomUUID(),
        typeId: UUID = UUID.randomUUID(),
        typeKey: String = "test_entity",
        identifierKey: UUID = UUID.randomUUID(),
        iconColour: IconColour = IconColour.NEUTRAL,
        iconType: IconType = IconType.FILE,
        sourceType: SourceType = SourceType.USER_CREATED,
        sourceIntegrationId: UUID? = null,
        sourceExternalId: String? = null,
        firstSyncedAt: java.time.ZonedDateTime? = null,
        lastSyncedAt: java.time.ZonedDateTime? = null,
    ): EntityEntity {
        return EntityEntity(
            id = id,
            workspaceId = workspaceId,
            typeId = typeId,
            typeKey = typeKey,
            identifierKey = identifierKey,
            iconColour = iconColour,
            iconType = iconType,
            sourceType = sourceType,
            sourceIntegrationId = sourceIntegrationId,
            sourceExternalId = sourceExternalId,
            firstSyncedAt = firstSyncedAt,
            lastSyncedAt = lastSyncedAt,
        )
    }
}
