package riven.core.models.customsource.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import riven.core.enums.customsource.SslMode

/**
 * Dry-run request: exercises SSRF + read-only gates without persisting anything.
 * Intentionally has no `workspaceId` — this endpoint does not create an entity.
 */
data class TestCustomSourceConnectionRequest(
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
        "TestCustomSourceConnectionRequest(host=$host,port=$port,database=$database," +
            "user=$user,password=***,sslMode=$sslMode)"
}
