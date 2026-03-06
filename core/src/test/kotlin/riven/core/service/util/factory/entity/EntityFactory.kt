package riven.core.service.util.factory.entity

import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityRelationshipEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.entity.entity.RelationshipDefinitionEntity
import riven.core.entity.entity.RelationshipDefinitionExclusionEntity
import riven.core.entity.entity.RelationshipTargetRuleEntity
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.EntityPropertyType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.common.validation.Schema
import riven.core.models.entity.EntityTypeSchema
import riven.core.models.entity.configuration.EntityTypeAttributeColumn
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import java.util.*

object EntityFactory {

    /**
     * Creates an EntityTypeEntity with the given parameters and reasonable defaults.
     */
    fun createEntityType(
        id: UUID = UUID.randomUUID(),
        key: String = "test_entity",
        displayNameSingular: String = "Test Entity",
        displayNamePlural: String = "Test Entities",
        workspaceId: UUID = UUID.randomUUID(),
        schema: EntityTypeSchema = createSimpleSchema(),
        order: List<EntityTypeAttributeColumn>? = null,
        version: Int = 1,
        protected: Boolean = false,
        identifierKey: UUID = schema.properties?.keys?.first() ?: UUID.randomUUID(),
        semanticGroup: SemanticGroup = SemanticGroup.UNCATEGORIZED,
    ): EntityTypeEntity {
        val defaultOrder = order ?: (schema.properties?.keys ?: listOf()).map { attrId ->
            EntityTypeAttributeColumn(attrId, EntityPropertyType.ATTRIBUTE)
        }

        return EntityTypeEntity(
            id = id,
            key = key,
            displayNameSingular = displayNameSingular,
            displayNamePlural = displayNamePlural,
            workspaceId = workspaceId,
            schema = schema,
            columns = defaultOrder,
            version = version,
            protected = protected,
            identifierKey = identifierKey,
            semanticGroup = semanticGroup,
        )
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
     */
    fun createRelationshipEntity(
        id: UUID = UUID.randomUUID(),
        workspaceId: UUID = UUID.randomUUID(),
        sourceId: UUID = UUID.randomUUID(),
        targetId: UUID = UUID.randomUUID(),
        definitionId: UUID = UUID.randomUUID(),
        semanticContext: String? = null,
        linkSource: riven.core.enums.integration.SourceType = riven.core.enums.integration.SourceType.USER_CREATED,
    ): EntityRelationshipEntity {
        return EntityRelationshipEntity(
            id = id,
            workspaceId = workspaceId,
            sourceId = sourceId,
            targetId = targetId,
            definitionId = definitionId,
            semanticContext = semanticContext,
            linkSource = linkSource,
        )
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
        allowPolymorphic: Boolean = false,
        protected: Boolean = false,
    ): RelationshipDefinitionEntity {
        return RelationshipDefinitionEntity(
            id = id,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = name,
            cardinalityDefault = cardinalityDefault,
            allowPolymorphic = allowPolymorphic,
            protected = protected,
        )
    }

    /**
     * Creates a RelationshipTargetRuleEntity for testing.
     */
    fun createTargetRuleEntity(
        id: UUID = UUID.randomUUID(),
        relationshipDefinitionId: UUID = UUID.randomUUID(),
        targetEntityTypeId: UUID? = UUID.randomUUID(),
        semanticTypeConstraint: SemanticGroup? = null,
        cardinalityOverride: EntityRelationshipCardinality? = null,
        inverseName: String = "Inverse",
    ): RelationshipTargetRuleEntity {
        return RelationshipTargetRuleEntity(
            id = id,
            relationshipDefinitionId = relationshipDefinitionId,
            targetEntityTypeId = targetEntityTypeId,
            semanticTypeConstraint = semanticTypeConstraint,
            cardinalityOverride = cardinalityOverride,
            inverseName = inverseName,
        )
    }

    /**
     * Creates a RelationshipDefinitionExclusionEntity for testing.
     */
    fun createExclusionEntity(
        id: UUID = UUID.randomUUID(),
        relationshipDefinitionId: UUID = UUID.randomUUID(),
        entityTypeId: UUID = UUID.randomUUID(),
    ): RelationshipDefinitionExclusionEntity {
        return RelationshipDefinitionExclusionEntity(
            id = id,
            relationshipDefinitionId = relationshipDefinitionId,
            entityTypeId = entityTypeId,
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
        payload: Map<String, EntityAttributePrimitivePayload> = emptyMap(),
        iconColour: IconColour = IconColour.NEUTRAL,
        iconType: IconType = IconType.FILE,
    ): EntityEntity {
        return EntityEntity(
            id = id,
            workspaceId = workspaceId,
            typeId = typeId,
            typeKey = typeKey,
            identifierKey = identifierKey,
            payload = payload,
            iconColour = iconColour,
            iconType = iconType,
        )
    }
}
