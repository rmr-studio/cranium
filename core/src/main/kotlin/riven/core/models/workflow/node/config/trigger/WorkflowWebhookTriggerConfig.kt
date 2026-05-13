package cranium.core.models.workflow.node.config.trigger

import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import cranium.core.enums.common.http.AuthenticationType
import cranium.core.enums.common.http.RequestMethodType
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.workflow.WorkflowNodeConfigFieldType
import cranium.core.enums.workflow.WorkflowNodeType
import cranium.core.enums.workflow.WorkflowTriggerType
import cranium.core.models.common.http.Signature
import cranium.core.models.common.json.JsonObject
import cranium.core.models.common.validation.Schema
import cranium.core.models.workflow.engine.state.NodeOutput
import cranium.core.models.workflow.engine.state.WorkflowDataStore
import cranium.core.models.workflow.node.NodeServiceProvider
import cranium.core.models.workflow.node.config.WorkflowNodeConfigField
import cranium.core.models.workflow.node.config.WorkflowNodeTypeMetadata
import cranium.core.models.workflow.node.config.WorkflowTriggerConfig
import cranium.core.models.workflow.node.config.validation.ConfigValidationResult
import io.swagger.v3.oas.annotations.media.Schema as SwaggerSchema

/**
 * Configuration for WEBHOOK trigger nodes.
 *
 * Triggers workflow execution when an HTTP webhook is received
 * at the configured endpoint.
 */
@SwaggerSchema(
    name = "WorkflowWebhookTriggerConfig",
    description = "Configuration for WEBHOOK trigger nodes. Triggers workflow execution when an HTTP webhook is received."
)
@JsonTypeName("workflow_webhook_trigger")
@JsonDeserialize(using = ValueDeserializer.None::class)
data class WorkflowWebhookTriggerConfig(
    override val version: Int = 1,
    val method: RequestMethodType,
    val authentication: AuthenticationType,
    val signature: Signature,
    val payloadSchema: Schema<String>
) : WorkflowTriggerConfig {
    override val subType: WorkflowTriggerType
        get() = WorkflowTriggerType.WEBHOOK

    override val config: JsonObject
        get() = mapOf(
            "method" to method.name,
            "authentication" to authentication.name,
            "signature" to signature,
            "payloadSchema" to payloadSchema
        )

    override val configSchema: List<WorkflowNodeConfigField>
        get() = Companion.configSchema

    companion object {
        val metadata = WorkflowNodeTypeMetadata(
            label = "Webhook",
            description = "Triggers when an HTTP request is received",
            icon = IconType.WEBHOOK,
            category = WorkflowNodeType.TRIGGER
        )

        val configSchema: List<WorkflowNodeConfigField> = listOf(
            WorkflowNodeConfigField(
                key = "method",
                label = "HTTP Method",
                type = WorkflowNodeConfigFieldType.ENUM,
                required = true,
                description = "HTTP method the webhook accepts",
                options = mapOf(
                    "GET" to "GET",
                    "POST" to "POST",
                    "PUT" to "PUT",
                    "DELETE" to "DELETE",
                    "PATCH" to "PATCH"
                )
            ),
            WorkflowNodeConfigField(
                key = "authentication",
                label = "Authentication",
                type = WorkflowNodeConfigFieldType.ENUM,
                required = true,
                description = "Authentication method for webhook requests",
                options = mapOf(
                    "NONE" to "None",
                    "API_KEY" to "API Key",
                    "BASIC" to "Basic Auth",
                    "BEARER" to "Bearer Token"
                )
            ),
            WorkflowNodeConfigField(
                key = "signature",
                label = "Signature",
                type = WorkflowNodeConfigFieldType.JSON,
                required = true,
                description = "Signature configuration for request verification"
            ),
            WorkflowNodeConfigField(
                key = "payloadSchema",
                label = "Payload Schema",
                type = WorkflowNodeConfigFieldType.JSON,
                required = true,
                description = "Schema defining the expected webhook payload structure"
            )
        )
    }

    /**
     * Validates this configuration.
     *
     * Checks:
     * - method is valid (already enforced by enum)
     * - authentication is valid (already enforced by enum)
     * - signature is provided
     * - payloadSchema is provided
     *
     * @param injector Spring managed provider to inject services into model
     * @return Validation result with any errors
     */
    @Suppress("UNUSED_PARAMETER")
    override fun validate(injector: NodeServiceProvider): ConfigValidationResult {
        // method and authentication are enums, always valid if deserialized
        // signature is non-null in constructor
        // payloadSchema is non-null in constructor
        // Could add deeper validation here if needed
        return ConfigValidationResult.valid()
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