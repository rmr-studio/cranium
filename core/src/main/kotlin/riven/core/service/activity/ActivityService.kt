package cranium.core.service.activity

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import cranium.core.entity.activity.ActivityLogEntity
import cranium.core.enums.activity.Activity
import cranium.core.enums.core.ApplicationEntityType
import cranium.core.enums.util.OperationType
import cranium.core.models.activity.ActivityLog
import cranium.core.models.common.json.JsonObject
import cranium.core.repository.activity.ActivityLogRepository
import java.time.ZonedDateTime
import java.util.*

@Service
class ActivityService(
    private val logger: KLogger,
    private val repository: ActivityLogRepository
) {
    fun logActivity(
        activity: Activity,
        operation: OperationType,
        userId: UUID,
        workspaceId: UUID,
        entityType: ApplicationEntityType,
        entityId: UUID? = null,
        timestamp: ZonedDateTime = ZonedDateTime.now(),
        details: JsonObject
    ): ActivityLog {
        // Create database entry
        ActivityLogEntity(
            userId = userId,
            workspaceId = workspaceId,
            activity = activity,
            operation = operation,
            entityType = entityType,
            entityId = entityId,
            timestamp = timestamp,
            details = details
        ).run {
            repository.save(this)
            // Log the activity with the provided details
            logger.info {
                "Activity logged: $activity by User: $userId"
            }

            return this.toModel()
        }
    }

    fun logActivities(activities: List<ActivityLogEntity>) {
        repository.saveAll(activities)
        logger.info { "${activities.size} activities logged." }
    }

}

/** Convenience wrapper that accepts details as vararg pairs instead of requiring `mapOf(...)`. */
fun ActivityService.log(
    activity: Activity,
    operation: OperationType,
    userId: UUID,
    workspaceId: UUID,
    entityType: ApplicationEntityType,
    entityId: UUID? = null,
    vararg details: Pair<String, Any?>
): ActivityLog = logActivity(
    activity = activity,
    operation = operation,
    userId = userId,
    workspaceId = workspaceId,
    entityType = entityType,
    entityId = entityId,
    details = mapOf(*details)
)