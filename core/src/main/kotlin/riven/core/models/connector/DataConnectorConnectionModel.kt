package riven.core.models.connector

import riven.core.enums.integration.ConnectionStatus
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Response DTO for a data connector connection.
 *
 * Intentionally omits `encryptedCredentials`, `iv`, `keyVersion`, and any
 * password field — credentials never leave the service boundary. The
 * decrypted non-secret connection parameters (host, port, database, user,
 * sslMode) are supplied by the service after AES-GCM decryption.
 *
 * Defensive contract: future edits MUST NOT add a `password` or credential
 * bytes field here. The [init] block documents this guardrail for reviewers.
 */
data class DataConnectorConnectionModel(
    val id: UUID,
    val workspaceId: UUID,
    val name: String,
    val host: String,
    val port: Int,
    val database: String,
    val user: String,
    val sslMode: String,
    val connectionStatus: ConnectionStatus,
    val lastVerifiedAt: ZonedDateTime?,
    val createdAt: ZonedDateTime?,
    val updatedAt: ZonedDateTime?,
) {
    init {
        // Defensive: this model must never carry `password`, `encryptedCredentials`,
        // `iv`, or `keyVersion`. If a future edit adds one, fail code review.
    }
}
