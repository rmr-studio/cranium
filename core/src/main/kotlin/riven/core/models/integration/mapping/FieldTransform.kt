package riven.core.models.integration.mapping

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver
import riven.core.configuration.util.CaseInsensitiveTypeIdResolver
import riven.core.enums.integration.CoercionType

/**
 * Sealed class hierarchy representing field transformation strategies for integration mappings.
 *
 * Deserialized from JSONB using Jackson discriminated union via the "type" property.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, property = "type")
@JsonTypeIdResolver(CaseInsensitiveTypeIdResolver::class)
@JsonSubTypes(
    JsonSubTypes.Type(value = FieldTransform.Direct::class, name = "direct"),
    JsonSubTypes.Type(value = FieldTransform.TypeCoercion::class, name = "type_coercion"),
    JsonSubTypes.Type(value = FieldTransform.DefaultValue::class, name = "default_value"),
    JsonSubTypes.Type(value = FieldTransform.JsonPathExtraction::class, name = "json_path_extraction")
)
sealed class FieldTransform {

    /** Passthrough — no transformation applied. */
    @JsonTypeName("direct")
    data object Direct : FieldTransform()

    /** Coerce the source value to a primitive target type. */
    @JsonTypeName("type_coercion")
    data class TypeCoercion(val targetType: CoercionType) : FieldTransform()

    /** Use a fallback value when the source field is missing or null. */
    @JsonTypeName("default_value")
    data class DefaultValue(val value: Any?) : FieldTransform()

    /** Extract a nested value using a dot-separated path (e.g. "address.city"). */
    @JsonTypeName("json_path_extraction")
    data class JsonPathExtraction(val path: String) : FieldTransform()
}
