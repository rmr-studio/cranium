package riven.core.service.activity

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import riven.core.entity.activity.ActivityLogEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.util.OperationType
import riven.core.models.activity.ActivityLog
import riven.core.models.common.json.JsonObject
import riven.core.repository.activity.ActivityLogRepository
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