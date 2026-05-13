package cranium.core.enums.workflow

/**
 * Error handling modes for bulk update operations.
 *
 * - FAIL_FAST: Stop on first entity update failure. No rollback -- entities updated before
 *   the failure remain updated. Output includes count of what succeeded before failure.
 * - BEST_EFFORT: Process all entities regardless of individual failures. Output includes
 *   entitiesUpdated count, entitiesFailed count, and details of failed entities.
 */
enum class BulkUpdateErrorHandling {
    FAIL_FAST,
    BEST_EFFORT
}
