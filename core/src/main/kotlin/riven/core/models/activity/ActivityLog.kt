package cranium.core.models.activity

import cranium.core.enums.activity.Activity
import cranium.core.enums.core.ApplicationEntityType
import cranium.core.enums.util.OperationType
import cranium.core.models.common.json.JsonObject
import java.time.ZonedDateTime
import java.util.*

data class ActivityLog(
    val id: UUID,
    val userId: UUID,
    val workspaceId: UUID,
    val activity: Activity,
    val operation: OperationType,
    val entityType: ApplicationEntityType,
    val entityId: UUID? = null,
    val timestamp: ZonedDateTime,
    val details: JsonObject,
)