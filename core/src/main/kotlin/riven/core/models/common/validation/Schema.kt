package cranium.core.models.common.validation

import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.core.DataFormat
import cranium.core.enums.core.DataType
import cranium.core.models.common.Icon
import cranium.core.models.common.json.JsonObject

/**
 * The Schema defines the structure and data storage requirements for a given object.
 *
 * Example JSON Schema:
 * {
 *   "type": "object",
 *   "properties": {
 *     "contacts": {
 *       "type": "array",
 *       "items": {
 *         "type": "object",
 *         "properties": {
 *           "name": { "type": "string" },
 *           "email": { "type": "string", "format": "email" },
 *           "role": { "type": "string" },
 *           "phone": { "type": "string" }
 *         },
 *         "required": ["name", "email"]
 *       }
 *     }
 *   },
 *   "required": ["contacts"]
 * }
 */
data class Schema<T>(
    // This is a human-readable label for the schema
    val label: String? = null,
    val key: SchemaType,
    val icon: Icon = Icon(
        type = IconType.LANDMARK,
        colour = IconColour.NEUTRAL
    ),
    val type: DataType = DataType.OBJECT,
    val format: DataFormat? = null,
    val required: Boolean = false,
    val properties: Map<T, Schema<T>>? = null,
    val items: Schema<T>? = null,
    val unique: Boolean = false,
    // Indicates whether this schema is protected from deletion or modification
    val protected: Boolean = false,
    val options: SchemaOptions? = null

) {
    fun toJsonSchema(
        allowAdditionalProperties: Boolean
    ): JsonObject {
        /**
         * Convert a Schema into a JSON Schema (draft 2019-09) fragment represented as a map.
         *
         * Handles object, array, string, number, boolean, and null types and applies format-specific
         * constraints (for example, email, date, uri). For the PERCENTAGE format, emits a union that
         * accepts either a numeric value in the range 0..1 or a percentage string (e.g., "12.5%").
         *
         * @param schema The BlockSchema to convert.
         * @param allowAP When true, object schemas will permit additional properties (`additionalProperties: true`); when false, additional properties are disallowed.
         * @return A map suitable for serialization as a JSON Schema fragment corresponding to the provided schema.
         */
        fun toJs(schema: Schema<T>, allowAP: Boolean): Map<String, Any> {
            return when (schema.type) {
                DataType.OBJECT -> {
                    val props = mutableMapOf<String, Any>()
                    val requiredKeys = mutableListOf<String>()
                    schema.properties?.forEach { (k, v) ->
                        props[k.toString()] = toJs(v, allowAP)
                        if (v.required) requiredKeys += k.toString()
                    }
                    buildMap {
                        put("type", "object")
                        put("properties", props)
                        put("additionalProperties", allowAP)
                        if (requiredKeys.isNotEmpty()) put("required", requiredKeys)
                    }
                }

                DataType.ARRAY -> {
                    val items = schema.items?.let { toJs(it, allowAP) } ?: emptyMap<String, Any>()
                    buildMap {
                        put("type", "array")
                        put("items", items)
                    }
                }

                DataType.STRING -> {
                    // Standard formats/patterns
                    val base = mutableMapOf<String, Any>("type" to "string")
                    when (schema.format) {
                        DataFormat.EMAIL -> base["format"] = "email"
                        DataFormat.PHONE -> base["pattern"] = "^\\+?[1-9]\\d{1,14}\$"
                        DataFormat.CURRENCY -> base["pattern"] = "^[A-Z]{3}\$"
                        DataFormat.PERCENTAGE -> {
                            // Accept string variants like "0%", "20%", "200%", "12.5%"
                            base["pattern"] = "^(\\d+)(\\.\\d+)?%$"
                        }

                        DataFormat.DATE -> base["format"] = "date"
                        DataFormat.DATETIME -> base["format"] = "date-time"
                        DataFormat.URL -> base["format"] = "uri"
                        else -> {}
                    }

                    // Apply SchemaOptions for strings
                    schema.options?.regex?.let { base["pattern"] = it }
                    schema.options?.minLength?.let { base["minLength"] = it }
                    schema.options?.maxLength?.let { base["maxLength"] = it }
                    schema.options?.enum?.let {
                        if (it.isNotEmpty()) {
                            base["enum"] = it
                        }
                    }

                    base
                }

                DataType.NUMBER -> {
                    // For PERCENTAGE, allow numeric 0..1 (interpreted as 0%..100%)
                    if (schema.format == DataFormat.PERCENTAGE) {
                        mapOf(
                            "oneOf" to listOf(
                                mapOf(
                                    "type" to "number",
                                    "minimum" to 0,
                                    "maximum" to 1
                                ),
                                mapOf(
                                    "type" to "string",
                                    "pattern" to "^(\\d+)(\\.\\d+)?%$"
                                )
                            )
                        )
                    } else {
                        buildMap {
                            put("type", "number")
                            schema.options?.minimum?.let { put("minimum", it) }
                            schema.options?.maximum?.let { put("maximum", it) }
                            schema.options?.enum?.let {
                                if (it.isNotEmpty()) {
                                    put("enum", it)
                                }
                            }
                        }
                    }
                }

                DataType.BOOLEAN -> buildMap {
                    put("type", "boolean")
                    schema.options?.enum?.let {
                        if (it.isNotEmpty()) {
                            put("enum", it)
                        }
                    }
                }

                DataType.NULL -> buildMap {
                    put("type", "null")
                }
            }
        }

        // Special case: if schema.format == PERCENTAGE, always emit union regardless of declared type.
        return if (this.format == DataFormat.PERCENTAGE) {
            mapOf(
                "oneOf" to listOf(
                    mapOf("type" to "number", "minimum" to 0, "maximum" to 1),
                    mapOf("type" to "string", "pattern" to "^(\\d+)(\\.\\d+)?%$")
                )
            )
        } else {
            toJs(this, allowAdditionalProperties)
        }
    }
}

