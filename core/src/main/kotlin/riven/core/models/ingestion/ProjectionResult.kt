package cranium.core.models.ingestion

import java.util.UUID

/**
 * Result of projecting integration entities into core lifecycle entities.
 */
data class ProjectionResult(
    val created: Int,
    val updated: Int,
    val skipped: Int,
    val errors: Int,
    val details: List<ProjectionDetail> = emptyList(),
)

/**
 * Detail for a single entity projection outcome.
 */
data class ProjectionDetail(
    val sourceEntityId: UUID,
    val targetEntityId: UUID?,
    val outcome: ProjectionOutcome,
    val message: String? = null,
)

enum class ProjectionOutcome {
    CREATED,
    UPDATED,
    SKIPPED_NO_MATCH,
    SKIPPED_AUTO_CREATE_DISABLED,
    SKIPPED_SOFT_DELETED,
    SKIPPED_STALE_VERSION,
    ERROR,
}
