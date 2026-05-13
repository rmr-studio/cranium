package cranium.core.entity.connector

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import cranium.core.entity.util.AuditableSoftDeletableEntity
import cranium.core.enums.integration.ConnectionStatus
import cranium.core.models.connector.DataConnectorConnectionModel
import java.time.ZonedDateTime
import java.util.UUID

/**
 * JPA entity for user-defined data connector connections (Phase 2 CONN-01).
 *
 * Stores AES-256-GCM encrypted credentials as raw bytea with IV + key version
 * metadata. Decryption is performed by the service layer at read-time; the
 * corresponding [DataConnectorConnectionModel] intentionally omits the
 * encrypted bytes, IV, and key version so credentials never leave the service
 * boundary.
 *
 * Extends [AuditableSoftDeletableEntity] so deletion is a soft flag and
 * `@SQLRestriction("deleted = false")` automatically filters deleted rows.
 */
@Entity
@Table(name = "data_connector_connections")
@SQLRestriction("deleted = false")
class DataConnectorConnectionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 50)
    var connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTED,

    @Column(name = "encrypted_credentials", nullable = false, columnDefinition = "bytea")
    var encryptedCredentials: ByteArray,

    @Column(name = "iv", nullable = false, columnDefinition = "bytea")
    var iv: ByteArray,

    @Column(name = "key_version", nullable = false)
    var keyVersion: Int = 1,

    @Column(name = "last_verified_at")
    var lastVerifiedAt: ZonedDateTime? = null,

    @Column(name = "last_failure_reason", length = 1000)
    var lastFailureReason: String? = null,
) : AuditableSoftDeletableEntity() {

    /**
     * Map to the redacted response DTO. Decrypted credential fields must be
     * supplied by the service layer after AES-GCM decryption; this entity
     * deliberately refuses to expose its encrypted bytes, IV, or key version.
     */
    fun toModel(
        host: String,
        port: Int,
        database: String,
        user: String,
        sslMode: String,
    ): DataConnectorConnectionModel = DataConnectorConnectionModel(
        id = requireNotNull(id) {
            "DataConnectorConnectionEntity.id must not be null when mapping to model"
        },
        workspaceId = workspaceId,
        name = name,
        host = host,
        port = port,
        database = database,
        user = user,
        sslMode = sslMode,
        connectionStatus = connectionStatus,
        lastVerifiedAt = lastVerifiedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
