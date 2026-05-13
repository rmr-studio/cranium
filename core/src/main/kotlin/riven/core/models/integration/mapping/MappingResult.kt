package cranium.core.models.integration.mapping

import cranium.core.models.entity.payload.EntityAttributePrimitivePayload

/**
 * Result of applying field mappings to a source data record.
 *
 * Contains the successfully mapped attributes, any warnings (non-fatal issues),
 * any errors (fatal issues per field), and a coverage report.
 */
data class MappingResult(
    val attributes: Map<String, EntityAttributePrimitivePayload>,
    val warnings: List<MappingWarning>,
    val errors: List<MappingError>,
    val fieldCoverage: FieldCoverage
) {
    /** True when no errors occurred during mapping. */
    val success: Boolean get() = errors.isEmpty()
}

/**
 * A non-fatal issue encountered during field mapping (e.g. missing optional source field).
 */
data class MappingWarning(
    val targetKey: String,
    val sourcePath: String,
    val message: String
)

/**
 * A fatal issue encountered during field mapping (e.g. type coercion failure).
 */
data class MappingError(
    val targetKey: String,
    val sourcePath: String,
    val transformType: String,
    val message: String,
    val cause: String? = null
)

/**
 * Reports how many target fields were successfully mapped out of the total defined.
 */
data class FieldCoverage(
    val mapped: Int,
    val total: Int,
    val ratio: Double
)
