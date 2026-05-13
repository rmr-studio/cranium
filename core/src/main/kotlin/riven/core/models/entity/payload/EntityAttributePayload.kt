package cranium.core.models.entity.payload

import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.deserializer.EntityAttributePayloadDeserializer
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.entity.EntityPropertyType
import cranium.core.models.common.json.JsonValue
import cranium.core.models.entity.EntityLink
import java.util.*


@Schema(hidden = true)
@JsonDeserialize(using = EntityAttributePayloadDeserializer::class)
sealed interface EntityAttributePayload {
    val type: EntityPropertyType
}

@Schema(
    name = "EntityAttributePrimitivePayload",
    description = "An attribute payload representing a primitive value with a defined schema type"
)
@JsonDeserialize(using = ValueDeserializer.None::class)
data class EntityAttributePrimitivePayload(
    @get:Schema(type = "object", implementation = Any::class)
    val value: JsonValue,
    val schemaType: SchemaType
) : EntityAttributePayload {
    override val type: EntityPropertyType = EntityPropertyType.ATTRIBUTE
}

@Schema(
    name = "EntityAttributeRelationPayloadReference",
    description = "An attribute payload representing relationships to other entities by their IDs"
)
@JsonDeserialize(using = ValueDeserializer.None::class)
data class EntityAttributeRelationPayloadReference(
    val relations: List<UUID>
) : EntityAttributePayload {
    override val type: EntityPropertyType = EntityPropertyType.RELATIONSHIP
}

@Schema(
    name = "EntityAttributeRelationPayload",
    description = "An attribute payload representing a relationship to another entity, with a full identifying link"
)
@JsonDeserialize(using = ValueDeserializer.None::class)
data class EntityAttributeRelationPayload(
    val relations: List<EntityLink>
) : EntityAttributePayload {
    override val type: EntityPropertyType = EntityPropertyType.RELATIONSHIP
}

data class EntityAttributeRequest(
    @field:Schema(
        oneOf = [EntityAttributePrimitivePayload::class, EntityAttributeRelationPayloadReference::class],
        discriminatorProperty = "type",
        discriminatorMapping = [
            DiscriminatorMapping(value = "ATTRIBUTE", schema = EntityAttributePrimitivePayload::class),
            DiscriminatorMapping(value = "RELATIONSHIP", schema = EntityAttributeRelationPayloadReference::class),
        ]
    )
    val payload: EntityAttributePayload
)

data class EntityAttribute(
    @field:Schema(
        oneOf = [EntityAttributePrimitivePayload::class, EntityAttributeRelationPayload::class],
        discriminatorProperty = "type",
        discriminatorMapping = [
            DiscriminatorMapping(value = "ATTRIBUTE", schema = EntityAttributePrimitivePayload::class),
            DiscriminatorMapping(value = "RELATIONSHIP", schema = EntityAttributeRelationPayload::class),
        ]
    )
    val payload: EntityAttributePayload
)