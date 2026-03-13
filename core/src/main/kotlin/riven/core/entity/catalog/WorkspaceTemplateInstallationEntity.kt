package riven.core.entity.catalog

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import riven.core.models.catalog.WorkspaceTemplateInstallationModel
import java.time.ZonedDateTime
import java.util.*

/**
 * JPA entity tracking which templates have been installed into which workspaces.
 *
 * Enables duplication protection (unique constraint on workspace_id + manifest_key),
 * template origin tracking (attribute_mappings JSONB), and future uninstall support.
 * Does NOT extend AuditableEntity — system-managed installation record.
 */
@Entity
@Table(
    name = "workspace_template_installations",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["workspace_id", "manifest_key"])
    ]
)
data class WorkspaceTemplateInstallationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Column(name = "manifest_key", nullable = false)
    val manifestKey: String,

    @Column(name = "installed_by", nullable = false)
    val installedBy: UUID,

    @Column(name = "installed_at", nullable = false, updatable = false)
    val installedAt: ZonedDateTime = ZonedDateTime.now(),

    @Type(JsonBinaryType::class)
    @Column(name = "attribute_mappings", columnDefinition = "jsonb", nullable = false)
    val attributeMappings: Map<String, Any> = emptyMap(),
) {
    fun toModel() = WorkspaceTemplateInstallationModel(
        id = id,
        workspaceId = workspaceId,
        manifestKey = manifestKey,
        installedBy = installedBy,
        installedAt = installedAt,
        attributeMappings = attributeMappings,
    )
}
