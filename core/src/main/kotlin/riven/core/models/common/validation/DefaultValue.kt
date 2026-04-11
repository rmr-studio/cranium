package riven.core.models.common.validation

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import riven.core.enums.core.DynamicDefaultFunction
import riven.core.models.common.json.JsonValue

/**
 * Unified representation of attribute default values.
 *
 * Unified representation stored in [SchemaOptions.defaultValue]. Each subtype
 * handles a distinct resolution strategy:
 *
 * - [Static] — a literal value baked into the schema, injected as-is on entity creation.
 * - [Dynamic] — resolved at entity creation time by evaluating a known function.
 *
 * The sealed hierarchy is open to future extension (formulas, lookups, aggregations)
 * without changing the SchemaOptions structure or existing serialization.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DefaultValue.Static::class, name = "static"),
    JsonSubTypes.Type(value = DefaultValue.Dynamic::class, name = "dynamic"),
)
sealed interface DefaultValue {

    /** A literal value baked into the schema — injected as-is on entity creation. */
    data class Static(val value: JsonValue) : DefaultValue

    /** Resolved at entity creation time by evaluating a known function. */
    data class Dynamic(val function: DynamicDefaultFunction) : DefaultValue
}
