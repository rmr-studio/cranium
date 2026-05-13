package cranium.core.configuration.workflow

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("cranium.workflow.retry")
data class WorkflowRetryConfigurationProperties(
    val default: RetryConfig = RetryConfig(),
    val httpAction: HttpRetryConfig = HttpRetryConfig(),
    val crudAction: RetryConfig = RetryConfig(maxAttempts = 2),
    val integrationSync: RetryConfig = RetryConfig(
        maxAttempts = 3,
        initialIntervalSeconds = 30,
        backoffCoefficient = 2.0,
        maxIntervalSeconds = 120
    )
)

data class RetryConfig(
    val maxAttempts: Int = 3,
    val initialIntervalSeconds: Long = 1,
    val backoffCoefficient: Double = 2.0,
    val maxIntervalSeconds: Long = 30
)

data class HttpRetryConfig(
    val maxAttempts: Int = 3,
    val initialIntervalSeconds: Long = 2,
    val backoffCoefficient: Double = 2.0,
    val maxIntervalSeconds: Long = 60,
    val nonRetryableStatusCodes: List<Int> = listOf(400, 401, 403, 404, 422)
)
