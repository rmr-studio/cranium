package cranium.core.models.workflow.node.config.controls

import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.workflow.WorkflowControlType
import cranium.core.enums.workflow.WorkflowNodeConfigFieldType
import cranium.core.enums.workflow.WorkflowNodeType
import cranium.core.models.common.json.JsonObject
import cranium.core.models.workflow.engine.state.ConditionOutput
import cranium.core.models.workflow.engine.state.NodeOutput
import cranium.core.models.workflow.engine.state.WorkflowDataStore
import cranium.core.models.workflow.node.NodeServiceProvider
import cranium.core.models.workflow.node.config.WorkflowControlConfig
import cranium.core.models.workflow.node.config.WorkflowNodeConfigField
import cranium.core.models.workflow.node.config.WorkflowNodeTypeMetadata
import cranium.core.models.workflow.node.config.validation.ConfigValidationError
import cranium.core.models.workflow.node.config.validation.ConfigValidationResult
import cranium.core.models.workflow.node.service
import cranium.core.service.workflow.state.EntityContextService
import cranium.core.service.workflow.state.WorkflowNodeConfigValidationService
import cranium.core.service.workflow.state.WorkflowNodeExpressionEvaluatorService
import cranium.core.service.workflow.state.WorkflowNodeExpressionParserService
import java.util.*

private val log = KotlinLogging.logger {}

/**
 * Configuration for CONDITION control flow nodes.
 *
 * ## Configuration Properties
 *
 * @property expression SQL-like expression to evaluate (must return boolean)
 * @property contextEntityId Optional entity ID to load as evaluation context (template-enabled)
 * @property timeoutSeconds Optional timeout override in seconds
 *
 * ## Example Configuration
 *
 * ```json
 * {
 *   "version": 1,
 *   "type": "CONTROL_FLOW",
 *   "subType": "CONDITION",
 *   "expression": "entity.status == 'active' && entity.balance > 0",
 *   "contextEntityId": "{{ steps.fetch_account.output.entityId }}"
 * }
 * ```
 *
 * ## Output
 *
 * Returns map with:
 * - `conditionResult`: Boolean result of expression evaluation
 *
 * ## Expression Syntax
 *
 * Supports SQL-like syntax:
 * - Comparison: ==, !=, <, >, <=, >=
 * - Logical: &&, ||, !
 * - Property access: entity.field.nested
 * - Literals: 'string', 123, true, false, null
 *
 * ## DAG Coordination
 *
 * The conditionResult is used by the DAG coordinator to determine which
 * branch to execute next. Conditional edges in the workflow graph reference
 * this node's output to route execution flow.
 */
@Schema(
    name = "WorkflowConditionControlConfig",
    description = "Configuration for CONDITION control flow nodes."
)
@JsonTypeName("workflow_condition_control")
@JsonDeserialize(using = ValueDeserializer.None::class)
data class WorkflowConditionControlConfig(
    override val version: Int = 1,

    @Schema(
        description = "SQL-like expression to evaluate. Must return boolean.",
        example = "entity.status == 'active' && entity.balance > 0"
    )
    val expression: String,

    @Schema(
        description = "Optional entity ID to use as evaluation context. Can be a static UUID or template.",
        example = "{{ steps.fetch_account.output.entityId }}",
        nullable = true
    )
    val contextEntityId: String? = null,

    @Schema(
        description = "Optional timeout override in seconds",
        nullable = true
    )
    val timeoutSeconds: Long? = null

) : WorkflowControlConfig {

    override val type: WorkflowNodeType
        get() = WorkflowNodeType.CONTROL_FLOW

    override val subType: WorkflowControlType
        get() = WorkflowControlType.CONDITION

    override val config: JsonObject
        get() = mapOf(
            "expression" to expression,
            "contextEntityId" to contextEntityId,
            "timeoutSeconds" to timeoutSeconds
        )

    override val configSchema: List<WorkflowNodeConfigField>
        get() = Companion.configSchema

    companion object {
        val metadata = WorkflowNodeTypeMetadata(
            label = "Condition",
            description = "Branches workflow based on a condition",
            icon = IconType.GIT_BRANCH,
            category = WorkflowNodeType.CONTROL_FLOW
        )

        val configSchema: List<WorkflowNodeConfigField> = listOf(
            WorkflowNodeConfigField(
                key = "expression",
                label = "Condition Expression",
                type = WorkflowNodeConfigFieldType.TEMPLATE,
                required = true,
                description = "SQL-like expression to evaluate (must return boolean)",
                placeholder = "entity.status == 'active' && entity.balance > 0"
            ),
            WorkflowNodeConfigField(
                key = "contextEntityId",
                label = "Context Entity ID",
                type = WorkflowNodeConfigFieldType.UUID,
                required = false,
                description = "Optional entity ID to load as evaluation context"
            ),
            WorkflowNodeConfigField(
                key = "timeoutSeconds",
                label = "Timeout (seconds)",
                type = WorkflowNodeConfigFieldType.DURATION,
                required = false,
                description = "Optional timeout override in seconds"
            )
        )
    }

    /**
     * Validates this configuration.
     *
     * Checks:
     * - expression is not blank
     * - expression has valid syntax (parses without error)
     * - contextEntityId is valid UUID or template if provided
     * - timeout is non-negative if provided
     *
     * @param injector Spring managed provider to inject services into model
     * @return Validation result with any errors
     */
    override fun validate(injector: NodeServiceProvider): ConfigValidationResult {
        val validationService = injector.service<WorkflowNodeConfigValidationService>()
        val expressionParserService = injector.service<WorkflowNodeExpressionParserService>()
        val errors = mutableListOf<ConfigValidationError>()

        // Validate expression is not blank
        errors.addAll(validationService.validateRequiredString(expression, "expression"))

        // Validate expression syntax
        if (expression.isNotBlank()) {
            try {
                expressionParserService.parse(expression)
            } catch (e: Exception) {
                errors.add(ConfigValidationError("expression", "Invalid expression syntax: ${e.message}"))
            }
        }

        // Validate contextEntityId if provided
        if (contextEntityId != null) {
            errors.addAll(validationService.validateTemplateOrUuid(contextEntityId, "contextEntityId"))
        }

        // Validate timeout
        errors.addAll(validationService.validateOptionalDuration(timeoutSeconds, "timeoutSeconds"))

        return ConfigValidationResult(errors)
    }

    override fun execute(
        dataStore: WorkflowDataStore,
        inputs: Map<String, Any?>,
        services: NodeServiceProvider
    ): NodeOutput {
        // Extract resolved inputs
        val resolvedExpression = inputs["expression"] as? String ?: expression
        val resolvedContextEntityId = inputs["contextEntityId"] as? String

        // Get services on-demand
        val entityContextService = services.service<EntityContextService>()
        val workflowNodeExpressionParserService = services.service<WorkflowNodeExpressionParserService>()
        val workflowNodeExpressionEvaluatorService = services.service<WorkflowNodeExpressionEvaluatorService>()

        // Resolve entity context if provided
        val evaluationContext: Map<String, Any?> = if (resolvedContextEntityId != null) {
            val entityId = UUID.fromString(resolvedContextEntityId)
            entityContextService.buildContext(entityId, dataStore.metadata.workspaceId)
        } else {
            emptyMap()
        }

        // Parse and evaluate expression
        val ast = workflowNodeExpressionParserService.parse(resolvedExpression)
        val result = workflowNodeExpressionEvaluatorService.evaluate(ast, evaluationContext)

        // Validate boolean result
        if (result !is Boolean) {
            throw IllegalStateException(
                "CONDITION expression must evaluate to boolean, got: ${result?.let { it::class.simpleName } ?: "null"}"
            )
        }

        log.debug { "CONDITION evaluated: $resolvedExpression -> $result (context: ${evaluationContext.keys})" }

        // Return typed output for DAG branching
        return ConditionOutput(
            result = result,
            evaluatedExpression = resolvedExpression
        )
    }
}
