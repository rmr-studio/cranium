package cranium.core.models.workflow.node.config.trigger

import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.workflow.WorkflowNodeConfigFieldType
import cranium.core.enums.workflow.WorkflowNodeType
import cranium.core.enums.workflow.WorkflowTriggerType
import cranium.core.models.common.json.JsonObject
import cranium.core.models.workflow.engine.state.NodeOutput
import cranium.core.models.workflow.engine.state.WorkflowDataStore
import cranium.core.models.workflow.node.NodeServiceProvider
import cranium.core.models.workflow.node.config.WorkflowNodeConfigField
import cranium.core.models.workflow.node.config.WorkflowNodeTypeMetadata
import cranium.core.models.workflow.node.config.WorkflowTriggerConfig
import cranium.core.models.workflow.node.config.validation.ConfigValidationError
import cranium.core.models.workflow.node.config.validation.ConfigValidationResult
import java.time.Duration
import java.util.*

/**
 * Configuration for SCHEDULE trigger nodes.
 *
 * Triggers workflow execution on a schedule defined by either
 * a cron expression or a fixed interval.
 */
@Schema(
    name = "WorkflowScheduleTriggerConfig",
    description = "Configuration for SCHEDULE trigger nodes. Triggers workflow execution on a schedule."
)
@JsonTypeName("workflow_schedule_trigger")
@JsonDeserialize(using = ValueDeserializer.None::class)
data class WorkflowScheduleTriggerConfig(
    override val version: Int = 1,
    // Either a cron expression or an interval must be provided.
    val cronExpression: String? = null,
    val interval: Duration? = null,
    val timeZone: TimeZone
) : WorkflowTriggerConfig {
    override val subType: WorkflowTriggerType
        get() = WorkflowTriggerType.SCHEDULE

    override val config: JsonObject
        get() = mapOf(
            "cronExpression" to cronExpression,
            "interval" to interval?.toString(),
            "timeZone" to timeZone.id
        )

    override val configSchema: List<WorkflowNodeConfigField>
        get() = Companion.configSchema

    companion object {
        val metadata = WorkflowNodeTypeMetadata(
            label = "Schedule",
            description = "Triggers on a recurring schedule or cron expression",
            icon = IconType.CLOCK,
            category = WorkflowNodeType.TRIGGER
        )

        val configSchema: List<WorkflowNodeConfigField> = listOf(
            WorkflowNodeConfigField(
                key = "cronExpression",
                label = "Cron Expression",
                type = WorkflowNodeConfigFieldType.STRING,
                required = false,
                description = "Cron expression for scheduling (e.g., '0 0 * * *' for daily at midnight)",
                placeholder = "0 0 * * *"
            ),
            WorkflowNodeConfigField(
                key = "interval",
                label = "Interval",
                type = WorkflowNodeConfigFieldType.DURATION,
                required = false,
                description = "Fixed interval between executions (alternative to cron)"
            ),
            WorkflowNodeConfigField(
                key = "timeZone",
                label = "Time Zone",
                type = WorkflowNodeConfigFieldType.STRING,
                required = true,
                description = "Time zone for schedule interpretation",
                placeholder = "America/New_York"
            )
        )
    }

    init {
        require(cronExpression != null || interval != null) {
            "Either cronExpression or interval must be provided for schedule trigger"
        }
    }

    /**
     * Validates this configuration.
     *
     * Checks:
     * - Either cronExpression or interval is provided (already in init block)
     * - cronExpression has valid format if provided
     * - interval is positive if provided
     * - timeZone is valid
     *
     * @param injector Spring managed provider to inject services into model
     * @return Validation result with any errors
     */
    @Suppress("UNUSED_PARAMETER")
    override fun validate(injector: NodeServiceProvider): ConfigValidationResult {
        val errors = mutableListOf<ConfigValidationError>()

        // Check at least one scheduling option
        if (cronExpression == null && interval == null) {
            errors.add(ConfigValidationError("cronExpression", "Either cronExpression or interval must be provided"))
        }

        // Validate cron expression format if provided
        if (cronExpression != null && cronExpression.isBlank()) {
            errors.add(ConfigValidationError("cronExpression", "Cron expression cannot be blank"))
        }

        // Validate interval is positive if provided
        if (interval != null && interval.isNegative) {
            errors.add(ConfigValidationError("interval", "Interval must be positive"))
        }

        // timeZone is required (non-null in constructor), so it's always present

        return ConfigValidationResult(errors)
    }

    override fun execute(
        dataStore: WorkflowDataStore,
        inputs: Map<String, Any?>,
        services: NodeServiceProvider
    ): NodeOutput {
        // Triggers are entry points, not executed during workflow
        throw UnsupportedOperationException("TRIGGER nodes don't execute during workflow")
    }
}
