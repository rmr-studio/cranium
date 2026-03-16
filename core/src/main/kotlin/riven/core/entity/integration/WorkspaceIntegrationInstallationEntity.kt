package riven.core.entity.integration

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.Type
import riven.core.entity.util.AuditableEntity
import riven.core.enums.integration.InstallationStatus
import riven.core.models.common.SoftDeletable
import riven.core.models.integration.SyncConfiguration
import java.time.ZonedDateTime
import java.util.*

/**
 * JPA entity tracking which integrations are enabled per workspace.
 *
 * Implements SoftDeletable so that disabling an integration sets deleted = true
 * and re-enabling restores the row, preserving sync state (lastSyncedAt) for
 * gap-recovery on resume.
 */
@Entity
@Table(
    name = "workspace_integration_installations",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_workspace_integration_installation",
            columnNames = ["workspace_id", "integration_definition_id"]
        )
    ]
)
@SQLRestriction("deleted = false")
data class WorkspaceIntegrationInstallationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Column(name = "integration_definition_id", nullable = false, columnDefinition = "uuid")
    val integrationDefinitionId: UUID,

    @Column(name = "manifest_key", nullable = false)
    val manifestKey: String,

    @Column(name = "installed_by", nullable = false, columnDefinition = "uuid")
    val installedBy: UUID,

    @Column(name = "installed_at", nullable = false, updatable = false)
    val installedAt: ZonedDateTime = ZonedDateTime.now(),

    @Type(JsonBinaryType::class)
    @Column(name = "sync_config", columnDefinition = "jsonb")
    var syncConfig: SyncConfiguration = SyncConfiguration(),

    @Column(name = "last_synced_at")
    var lastSyncedAt: ZonedDateTime? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    var status: InstallationStatus = InstallationStatus.ACTIVE,

    @Column(name = "deleted", nullable = false)
    override var deleted: Boolean = false,

    @Column(name = "deleted_at")
    override var deletedAt: ZonedDateTime? = null
) : AuditableEntity(), SoftDeletable
