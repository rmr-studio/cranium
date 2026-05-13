package cranium.core.models.connector.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import cranium.core.enums.connector.SslMode
import java.util.UUID

/**
 * Create-request DTO for a data connector Postgres connection.
 *
 * [toString] deliberately redacts the `password` field — it MUST NOT emit the
 * plaintext value. The global Logback TurboFilter is a backstop; this is the
 * primary redaction for Kotlin data-class interpolation in logs.
 */
data class CreateDataConnectorConnectionRequest(
    @field:NotNull
    val workspaceId: UUID,

    @field:NotBlank
    @field:Size(min = 1, max = 255)
    val name: String,

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
        "CreateDataConnectorConnectionRequest(workspaceId=$workspaceId,name=$name,host=$host," +
            "port=$port,database=$database,user=$user,password=***,sslMode=$sslMode)"
}
