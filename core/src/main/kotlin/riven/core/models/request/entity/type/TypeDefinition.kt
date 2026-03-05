package riven.core.models.request.entity.type

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.Schema
import riven.core.deserializer.TypeDefinitionRequestDeserializer
import riven.core.enums.entity.EntityTypeRequestDefinition
import java.util.*

@Schema(hidden = true)
@JsonDeserialize(using = TypeDefinitionRequestDeserializer::class)
sealed interface TypeDefinition {
    val type: EntityTypeRequestDefinition
    val id: UUID?
    val key: String
}