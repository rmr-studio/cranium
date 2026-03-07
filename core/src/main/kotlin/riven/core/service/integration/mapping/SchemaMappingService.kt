package riven.core.service.integration.mapping

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.models.integration.mapping.FieldCoverage
import riven.core.models.integration.mapping.FieldTransform
import riven.core.models.integration.mapping.MappingError
import riven.core.models.integration.mapping.MappingResult
import riven.core.models.integration.mapping.MappingWarning
import riven.core.models.integration.mapping.ResolvedFieldMapping
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.*

/**
 * Generic mapping engine that transforms external JSON payloads into entity attribute payloads
 * using declarative field mappings.
 *
 * Every sync from every integration passes through this service. It is resilient: partial
 * failures produce partial results with structured health reports, not exceptions.
 */
@Service
class SchemaMappingService(
    private val logger: KLogger
) {

    /**
     * Maps an external payload to entity attributes using the provided field mappings
     * and key mapping for attribute UUID translation.
     *
     * @param externalPayload the raw external data as a flat or nested map
     * @param fieldMappings target attribute key to resolved mapping definition
     * @param keyMapping target attribute string key to workspace UUID
     * @return MappingResult with mapped attributes, warnings, errors, and coverage
     */
    fun mapPayload(
        externalPayload: Map<String, Any?>,
        fieldMappings: Map<String, ResolvedFieldMapping>,
        keyMapping: Map<String, UUID>
    ): MappingResult {
        val attributes = mutableMapOf<String, EntityAttributePrimitivePayload>()
        val warnings = mutableListOf<MappingWarning>()
        val errors = mutableListOf<MappingError>()

        for ((targetKey, mapping) in fieldMappings) {
            processFieldMapping(targetKey, mapping, externalPayload, keyMapping, attributes, warnings, errors)
        }

        val total = fieldMappings.size
        val mapped = attributes.size
        val ratio = if (total == 0) 0.0 else mapped.toDouble() / total.toDouble()

        return MappingResult(
            attributes = attributes,
            warnings = warnings,
            errors = errors,
            fieldCoverage = FieldCoverage(mapped = mapped, total = total, ratio = ratio)
        )
    }

    // ------ Private Helpers ------

    /**
     * Processes a single field mapping: extracts the source value, applies the transform,
     * resolves the target UUID key, and adds the result to the attributes map.
     */
    private fun processFieldMapping(
        targetKey: String,
        mapping: ResolvedFieldMapping,
        payload: Map<String, Any?>,
        keyMapping: Map<String, UUID>,
        attributes: MutableMap<String, EntityAttributePrimitivePayload>,
        warnings: MutableList<MappingWarning>,
        errors: MutableList<MappingError>
    ) {
        val uuid = keyMapping[targetKey]
        if (uuid == null) {
            errors.add(MappingError(targetKey, mapping.sourcePath, "key_mapping", "No UUID mapping found for target key '$targetKey'"))
            return
        }

        val sourceValue = extractSourceValue(payload, mapping.sourcePath, mapping.transform)

        if (sourceValue == null && mapping.transform !is FieldTransform.DefaultValue) {
            if (!payload.containsKey(mapping.sourcePath)) {
                warnings.add(MappingWarning(targetKey, mapping.sourcePath, "Source field '${mapping.sourcePath}' not found in payload"))
                return
            }
        }

        try {
            val transformedValue = applyTransform(sourceValue, mapping.transform, payload, mapping.sourcePath)
            attributes[uuid.toString()] = EntityAttributePrimitivePayload(
                value = transformedValue,
                schemaType = mapping.targetSchemaType
            )
        } catch (e: MissingPathException) {
            warnings.add(MappingWarning(targetKey, mapping.sourcePath, e.message ?: "Nested path not found"))
        } catch (e: TransformException) {
            errors.add(MappingError(
                targetKey = targetKey,
                sourcePath = mapping.sourcePath,
                transformType = e.transformType,
                message = e.message ?: "Transform failed",
                cause = e.cause?.message
            ))
        }
    }

    /**
     * Extracts the raw source value from the payload by key lookup.
     */
    private fun extractSourceValue(
        payload: Map<String, Any?>,
        sourcePath: String,
        transform: FieldTransform
    ): Any? {
        return payload[sourcePath]
    }

    /**
     * Applies the field transform to the extracted source value.
     */
    private fun applyTransform(
        value: Any?,
        transform: FieldTransform,
        payload: Map<String, Any?>,
        sourcePath: String
    ): Any? {
        return when (transform) {
            is FieldTransform.Direct -> value
            is FieldTransform.TypeCoercion -> coerceType(value, transform.targetType)
            is FieldTransform.DefaultValue -> resolveDefault(value, transform, payload, sourcePath)
            is FieldTransform.JsonPathExtraction -> extractNestedPath(value, transform.path)
        }
    }

    /**
     * Resolves the value for a DefaultValue transform: returns the source value if present
     * and non-null, otherwise returns the default.
     */
    private fun resolveDefault(
        value: Any?,
        transform: FieldTransform.DefaultValue,
        payload: Map<String, Any?>,
        sourcePath: String
    ): Any? {
        if (!payload.containsKey(sourcePath) || value == null) {
            return transform.value
        }
        return value
    }

    /**
     * Coerces a value to the specified target type (number, boolean, date, datetime).
     *
     * @throws TransformException if the coercion fails
     */
    private fun coerceType(value: Any?, targetType: String): Any {
        if (value == null) {
            throw TransformException("type_coercion", "Cannot coerce null value to $targetType")
        }

        return when (targetType) {
            "number" -> coerceToNumber(value)
            "boolean" -> coerceToBoolean(value)
            "date" -> coerceToDate(value)
            "datetime" -> coerceToDatetime(value)
            else -> throw TransformException("type_coercion", "Unsupported target type: $targetType")
        }
    }

    private fun coerceToNumber(value: Any): Any {
        return when (value) {
            is Number -> value.toDouble()
            is String -> {
                value.toDoubleOrNull()
                    ?: throw TransformException("type_coercion", "Cannot convert '$value' to number")
            }
            else -> throw TransformException("type_coercion", "Cannot convert ${value::class.simpleName} to number")
        }
    }

    private fun coerceToBoolean(value: Any): Any {
        return when (value) {
            is Boolean -> value
            is String -> when (value.lowercase()) {
                "true" -> true
                "false" -> false
                else -> throw TransformException("type_coercion", "Cannot convert '$value' to boolean")
            }
            else -> throw TransformException("type_coercion", "Cannot convert ${value::class.simpleName} to boolean")
        }
    }

    private fun coerceToDate(value: Any): Any {
        return when (value) {
            is String -> {
                try {
                    LocalDate.parse(value)
                    value
                } catch (e: DateTimeParseException) {
                    throw TransformException("type_coercion", "Cannot parse '$value' as date", e)
                }
            }
            else -> throw TransformException("type_coercion", "Cannot convert ${value::class.simpleName} to date")
        }
    }

    private fun coerceToDatetime(value: Any): Any {
        return when (value) {
            is String -> {
                try {
                    OffsetDateTime.parse(value)
                    value
                } catch (e: DateTimeParseException) {
                    throw TransformException("type_coercion", "Cannot parse '$value' as datetime", e)
                }
            }
            else -> throw TransformException("type_coercion", "Cannot convert ${value::class.simpleName} to datetime")
        }
    }

    /**
     * Extracts a nested value from a map using a dot-separated path.
     *
     * @throws TransformException if the source value is not a map
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractNestedPath(root: Any?, path: String): Any? {
        if (root == null) {
            throw MissingPathException("Source value is null, cannot extract path '$path'")
        }
        if (root !is Map<*, *>) {
            throw TransformException("json_path_extraction", "Source value is not a map, cannot extract path '$path'")
        }

        val segments = path.split(".")
        var current: Any? = root

        for (segment in segments) {
            if (current == null || current !is Map<*, *>) {
                throw MissingPathException("Path segment '$segment' not found in nested path '$path'")
            }
            if (!current.containsKey(segment)) {
                throw MissingPathException("Path segment '$segment' not found in nested path '$path'")
            }
            current = current[segment]
        }

        return current
    }

    /**
     * Internal exception for transform failures. Caught by processFieldMapping
     * and converted to MappingError.
     */
    private class TransformException(
        val transformType: String,
        override val message: String,
        override val cause: Throwable? = null
    ) : Exception(message, cause)

    /**
     * Internal exception for missing nested paths. Caught by processFieldMapping
     * and converted to MappingWarning (not an error — missing data is non-fatal).
     */
    private class MissingPathException(
        override val message: String
    ) : Exception(message)
}
