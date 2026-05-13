package cranium.core.models.request.entity.type

import tools.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.deserializer.TypeDefinitionRequestDeserializer
import cranium.core.enums.entity.EntityTypeRequestDefinition
import java.util.*

@Schema(hidden = true)
@JsonDeserialize(using = TypeDefinitionRequestDeserializer::class)
sealed interface TypeDefinition {
    val type: EntityTypeRequestDefinition
    val id: UUID?
    val key: String
}