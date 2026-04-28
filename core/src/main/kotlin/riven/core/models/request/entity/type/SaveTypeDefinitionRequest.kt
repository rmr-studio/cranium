package riven.core.models.request.entity.type

import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.annotations.media.Schema
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.EntityTypeRequestDefinition
import riven.core.models.entity.EntityTypeSchema
import java.util.*


@Schema(
    name = "SaveAttributeDefinitionRequest",
    description = "Request to save a schema attribute definition for an entity type"
)
@JsonDeserialize(using = ValueDeserializer.None::class)
data class SaveAttributeDefinitionRequest(
    override val key: String,
    override val id: UUID,
    val schema: EntityTypeSchema,
    val semantics: SaveSemanticMetadataRequest? = null,
) : TypeDefinition {
    override val type: EntityTypeRequestDefinition = EntityTypeRequestDefinition.SAVE_SCHEMA
}


@Schema(
    name = "SaveRelationshipDefinitionRequest",
    description = "Request to save a relationship definition for an entity type"
)
@JsonDeserialize(using = ValueDeserializer.None::class)
data class SaveRelationshipDefinitionRequest(
    override val key: String,
    override val id: UUID? = null,
    val name: String,
    val iconType: IconType?,
    val iconColour: IconColour?,
    val cardinalityDefault: EntityRelationshipCardinality,
    val targetRules: List<SaveTargetRuleRequest> = emptyList(),
    val semantics: SaveSemanticMetadataRequest? = null,
) : TypeDefinition {
    override val type: EntityTypeRequestDefinition = EntityTypeRequestDefinition.SAVE_RELATIONSHIP
}

@Schema(name = "SaveTargetRuleRequest", description = "Request to save a target rule for a relationship definition")
data class SaveTargetRuleRequest(
    val id: UUID? = null,
    val targetEntityTypeId: UUID,
    val cardinalityOverride: EntityRelationshipCardinality? = null,
    val inverseName: String,
)


data class SaveTypeDefinitionRequest(
    val index: Int?,
    @field:Schema(
        oneOf = [SaveAttributeDefinitionRequest::class, SaveRelationshipDefinitionRequest::class],
        discriminatorProperty = "type",
        discriminatorMapping = [
            DiscriminatorMapping(value = "SAVE_RELATIONSHIP", schema = SaveRelationshipDefinitionRequest::class),
            DiscriminatorMapping(value = "SAVE_SCHEMA", schema = SaveAttributeDefinitionRequest::class),
        ]
    )
    val definition: TypeDefinition
)
