package cranium.core.entity.identity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import cranium.core.entity.util.AuditableSoftDeletableEntity
import cranium.core.models.identity.IdentityCluster
import java.util.UUID

/**
 * JPA entity for identity_clusters table.
 *
 * An identity cluster is a group of entities that have been confirmed as representing
 * the same real-world identity. Merged clusters are soft-deleted, preserving history.
 */
@Entity
@Table(name = "identity_clusters")
data class IdentityClusterEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Column(name = "name", columnDefinition = "text")
    var name: String? = null,

    @Column(name = "member_count", nullable = false)
    var memberCount: Int = 0,
) : AuditableSoftDeletableEntity() {

    /** Convert this entity to the domain model. */
    fun toModel(): IdentityCluster {
        val id = requireNotNull(this.id) { "IdentityClusterEntity ID must not be null" }
        return IdentityCluster(
            id = id,
            workspaceId = workspaceId,
            name = name,
            memberCount = memberCount,
            createdAt = requireNotNull(createdAt) { "IdentityClusterEntity createdAt must not be null" },
            updatedAt = requireNotNull(updatedAt) { "IdentityClusterEntity updatedAt must not be null" },
        )
    }
}
