package riven.core.models.customsource.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import riven.core.enums.customsource.SslMode

/**
 * PATCH request for a custom source connection. All fields optional; setting any
 * of the credential-touching fields triggers a re-run of the SSRF + read-only
 * gate chain and re-encryption with a fresh IV.
 */
data class UpdateCustomSourceConnectionRequest(
    val name: String? = null,
    val host: String? = null,
    @field:Min(1) @field:Max(65535)
    val port: Int? = null,
    val database: String? = null,
    val user: String? = null,
    val password: String? = null,
    val sslMode: SslMode? = null,
) {
    override fun toString(): String =
        "UpdateCustomSourceConnectionRequest(name=$name,host=$host,port=$port,database=$database," +
            "user=$user,password=${if (password != null) "***" else "null"},sslMode=$sslMode)"

    /** Whether any credential-affecting field is set — used to decide if gates re-run. */
    fun touchesCredentials(): Boolean =
        host != null || port != null || database != null ||
            user != null || password != null || sslMode != null
}
