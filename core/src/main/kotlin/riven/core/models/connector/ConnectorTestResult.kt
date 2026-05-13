package cranium.core.models.connector

/** Result of a dry-run gate validation. */
data class ConnectorTestResult(
    val pass: Boolean,
    val category: String?,
    val message: String,
)
