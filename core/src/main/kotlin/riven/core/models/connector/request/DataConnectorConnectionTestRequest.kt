package cranium.core.models.connector.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import cranium.core.enums.connector.SslMode
import java.util.UUID

/**
 * Dry-run request: exercises SSRF + read-only gates without persisting anything.
 * [workspaceId] scopes the probe so the service-layer workspace gate can reject
 * probes from users without access to the workspace.
 */
data class DataConnectorConnectionTestRequest(
    @field:NotNull
    val workspaceId: UUID,

    @field:NotBlank
    val host: String,

    @field:Min(1) @field:Max(65535)
    val port: Int,

    @field:NotBlank
    val database: String,

    @field:NotBlank
    val user: String,

    @field:NotBlank
    val password: String,

    val sslMode: SslMode = SslMode.REQUIRE,
) {
    override fun toString(): String =
        "TestDataConnectorConnectionRequest(workspaceId=$workspaceId,host=$host,port=$port," +
            "database=$database,user=$user,password=***,sslMode=$sslMode)"
}
