package cranium.core.entity.integration

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import cranium.core.entity.util.AuditableEntity
import cranium.core.enums.integration.ConnectionStatus
import cranium.core.models.integration.IntegrationConnectionModel
import java.util.*

/**
 * JPA entity for integration connections (per-workspace).
 *
 * Tracks workspace-specific connections to integrations with Nango connection IDs,
 * status lifecycle, and metadata. Extends AuditableEntity for audit fields
 * (created_at/updated_at/created_by/updated_by) since this is workspace-owned data.
 */
@Entity
@Table(
    name = "integration_connections",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_workspace_integration",
            columnNames = ["workspace_id", "integration_id"]
        )
    ]
)
data class IntegrationConnectionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Column(name = "integration_id", nullable = false, columnDefinition = "uuid")
    val integrationId: UUID,

    @Column(name = "nango_connection_id", nullable = false)
    var nangoConnectionId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    var status: ConnectionStatus = ConnectionStatus.CONNECTED,

    @Type(JsonBinaryType::class)
    @Column(name = "connection_metadata", columnDefinition = "jsonb")
    var connectionMetadata: Map<String, Any>? = null
) : AuditableEntity() {
    fun toModel() = IntegrationConnectionModel(
        id = requireNotNull(id) { "IntegrationConnectionEntity.id must not be null when mapping to model" },
        workspaceId = workspaceId,
        integrationId = integrationId,
        nangoConnectionId = nangoConnectionId,
        status = status,
        connectionMetadata = connectionMetadata,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
