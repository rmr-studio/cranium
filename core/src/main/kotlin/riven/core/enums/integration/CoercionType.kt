package riven.core.enums.integration

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Primitive type targets for field value coercion during integration mapping.
 *
 * Distinct from [riven.core.enums.common.validation.SchemaType] which describes
 * what an attribute means — this enum describes what a value should be cast to.
 */
enum class CoercionType {
    @JsonProperty("string")   STRING,
    @JsonProperty("number")   NUMBER,
    @JsonProperty("boolean")  BOOLEAN,
    @JsonProperty("date")     DATE,
    @JsonProperty("datetime") DATETIME
}
