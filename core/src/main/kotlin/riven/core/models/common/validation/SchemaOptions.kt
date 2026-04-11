package riven.core.models.common.validation

import riven.core.enums.common.OptionSortingType
import java.time.ZonedDateTime

/**
 * Attribute configuration options shared across core model definitions and runtime schemas.
 *
 * Core model attributes (lifecycle definitions) and entity type schemas (runtime) both reference
 * this class. During template installation, options are carried from the manifest into the
 * persisted schema unchanged.
 */
data class SchemaOptions(
    val defaultValue: DefaultValue? = null,
    val prefix: String? = null,
    val regex: String? = null,
    val enum: List<String>? = null,
    val enumSorting: OptionSortingType? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val minDate: ZonedDateTime? = null,
    val maxDate: ZonedDateTime? = null,
) {
    /**
     * Extracts the static default value for validation purposes.
     * Returns the literal value from [DefaultValue.Static]; null for dynamic defaults
     * (validated via enum, not value) or when no default is configured.
     */
    fun extractStaticDefault(): Any? =
        when (defaultValue) {
            is DefaultValue.Static -> defaultValue.value
            is DefaultValue.Dynamic -> null
            null -> null
        }
}
