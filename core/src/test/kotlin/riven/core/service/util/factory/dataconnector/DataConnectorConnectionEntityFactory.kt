package riven.core.service.util.factory.dataconnector

import riven.core.entity.connector.DataConnectorConnectionEntity
import riven.core.enums.integration.ConnectionStatus
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Test factory for [DataConnectorConnectionEntity] (Phase 2 CONN-01).
 *
 * Required by core/CLAUDE.md: never construct JPA entities inline in tests —
 * always route construction through a factory. Downstream plans (02-02..04)
 * rely on this factory's defaults so they don't need to know the entity's
 * column layout.
 *
 * `id` is intentionally left unset so `save()` calls `persist()` not
 * `merge()` (see core/CLAUDE.md "Never manually generate UUIDs for
 * JPA-managed entities").
 */
object DataConnectorConnectionEntityFactory {

    fun create(
        workspaceId: UUID = UUID.randomUUID(),
        name: String = "test-postgres-${UUID.randomUUID().toString().take(8)}",
        connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTED,
        encryptedCredentials: ByteArray = ByteArray(48) { it.toByte() },
        iv: ByteArray = ByteArray(12) { it.toByte() },
        keyVersion: Int = 1,
        lastVerifiedAt: ZonedDateTime? = null,
        lastFailureReason: String? = null,
    ): DataConnectorConnectionEntity = DataConnectorConnectionEntity(
        workspaceId = workspaceId,
        name = name,
        connectionStatus = connectionStatus,
        encryptedCredentials = encryptedCredentials,
        iv = iv,
        keyVersion = keyVersion,
        lastVerifiedAt = lastVerifiedAt,
        lastFailureReason = lastFailureReason,
    )
}
