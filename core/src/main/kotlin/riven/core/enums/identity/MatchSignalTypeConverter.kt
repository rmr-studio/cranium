package riven.core.enums.identity

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA AttributeConverter for [MatchSignalType] <-> DB column `signal_type`.
 *
 * Handles the mismatch between the DB value "CUSTOM" and the enum value
 * [MatchSignalType.CUSTOM_IDENTIFIER]. All other values map by name.
 */
@Converter(autoApply = false)
class MatchSignalTypeConverter : AttributeConverter<MatchSignalType?, String?> {

    override fun convertToDatabaseColumn(attribute: MatchSignalType?): String? = when (attribute) {
        MatchSignalType.CUSTOM_IDENTIFIER -> "CUSTOM"
        null -> null
        else -> attribute.name
    }

    override fun convertToEntityAttribute(dbData: String?): MatchSignalType? =
        MatchSignalType.fromColumnValue(dbData)
}
