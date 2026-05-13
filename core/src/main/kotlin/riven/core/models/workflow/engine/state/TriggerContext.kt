package cranium.core.models.workflow.engine.state

import cranium.core.enums.util.OperationType
import cranium.core.models.common.json.JsonObject
import java.time.Instant
import java.util.UUID

/**
 * Sealed interface for workflow trigger context.
 *
 * Each trigger type provides context data accessible via {{ trigger.* }} templates.
 * The toMap() method enables template resolution to access properties dynamically.
 *
 * ## Supported Trigger Types
 *
 * - [EntityEventTrigger] - Entity create/update/delete events
 * - [WebhookTrigger] - HTTP webhook invocations
 * - [ScheduleTrigger] - Scheduled/cron executions
 * - [FunctionTrigger] - Function/API invocations
 *
 * ## Template Access
 *
 * All trigger properties are accessible via {{ trigger.propertyName }} syntax.
 * Nested properties use dot notation: {{ trigger.entity.fieldName }}
 */
sealed interface TriggerContext {
    /**
     * Convert trigger data to map for template property access.
     * Keys match property names, enabling {{ trigger.propertyName }} resolution.
     */
    fun toMap(): Map<String, Any?>
}

/**
 * Trigger context for entity events (create, update, delete).
 *
 * Template access:
 * - {{ trigger.eventType }} - CREATE, UPDATE, DELETE
 * - {{ trigger.entityId }} - Entity UUID
 * - {{ trigger.entityTypeId }} - Entity type UUID
 * - {{ trigger.entity }} - Entity data as map
 * - {{ trigger.entity.fieldName }} - Nested field access
 * - {{ trigger.previousEntity }} - Previous state (UPDATE only)
 *
 * @property eventType The type of entity operation that triggered the workflow
 * @property entityId UUID of the entity that triggered the event
 * @property entityTypeId UUID of the entity's type definition
 * @property entity Current entity payload as a map (avoids circular dependencies)
 * @property previousEntity Previous entity state for UPDATE events, null otherwise
 */
data class EntityEventTrigger(
    val eventType: OperationType,
    val entityId: UUID,
    val entityTypeId: UUID,
    val entity: Map<String, Any?>,
    val previousEntity: Map<String, Any?>? = null
) : TriggerContext {
    override fun toMap(): Map<String, Any?> = mapOf(
        "eventType" to eventType.name,
        "entityId" to entityId,
        "entityTypeId" to entityTypeId,
        "entity" to entity,
        "previousEntity" to previousEntity
    )
}

/**
 * Trigger context for webhook invocations.
 *
 * Template access:
 * - {{ trigger.headers.Content-Type }}
 * - {{ trigger.body.fieldName }}
 * - {{ trigger.queryParams.paramName }}
 *
 * @property headers HTTP headers from the webhook request
 * @property body Parsed JSON body from the webhook request
 * @property queryParams URL query parameters from the webhook request
 */
data class WebhookTrigger(
    val headers: Map<String, String>,
    val body: JsonObject,
    val queryParams: Map<String, String>
) : TriggerContext {
    override fun toMap(): Map<String, Any?> = mapOf(
        "headers" to headers,
        "body" to body,
        "queryParams" to queryParams
    )
}

/**
 * Trigger context for scheduled executions.
 *
 * Template access:
 * - {{ trigger.scheduledAt }} - ISO timestamp string
 * - {{ trigger.cronExpression }} - Cron expression (if cron-based)
 * - {{ trigger.interval }} - Interval in seconds (if interval-based)
 *
 * @property scheduledAt Timestamp when the schedule triggered
 * @property cronExpression Cron expression for cron-based schedules, null if interval-based
 * @property interval Interval in seconds for interval-based schedules, null if cron-based
 */
data class ScheduleTrigger(
    val scheduledAt: Instant,
    val cronExpression: String? = null,
    val interval: Long? = null
) : TriggerContext {
    override fun toMap(): Map<String, Any?> = mapOf(
        "scheduledAt" to scheduledAt.toString(),
        "cronExpression" to cronExpression,
        "interval" to interval
    )
}

/**
 * Trigger context for function/API invocations.
 *
 * Template access:
 * - {{ trigger.arguments.argName }}
 *
 * @property arguments Named arguments passed to the function invocation
 */
data class FunctionTrigger(
    val arguments: JsonObject
) : TriggerContext {
    override fun toMap(): Map<String, Any?> = mapOf(
        "arguments" to arguments
    )
}
