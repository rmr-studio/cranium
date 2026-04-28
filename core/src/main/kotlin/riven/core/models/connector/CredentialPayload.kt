package riven.core.models.connector

import riven.core.enums.connector.SslMode

/**
 * Internal JSON shape for the AES-GCM-encrypted credential blob.
 *
 * Serialised via Jackson before encryption; deserialised on read to rehydrate
 * connection parameters for the response DTO. The `password` field
 * round-trips through serialisation but is redacted from [toString] so it
 * never surfaces in log lines.
 */
data class CredentialPayload(
    val host: String,
    val port: Int,
    val database: String,
    val user: String,
    val password: String,
    val sslMode: SslMode,
) {
    override fun toString(): String =
        "CredentialPayload(host=$host,port=$port,database=$database,user=$user,password=***,sslMode=$sslMode)"
}
