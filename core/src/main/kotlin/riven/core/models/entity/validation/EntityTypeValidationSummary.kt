package cranium.core.models.entity.validation


data class EntityTypeValidationSummary(
    val totalEntities: Int,
    val validCount: Int,
    val invalidCount: Int,
    val sampleErrors: List<EntityValidationError>
)