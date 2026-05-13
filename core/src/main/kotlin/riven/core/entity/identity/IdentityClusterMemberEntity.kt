package cranium.core.entity.identity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import cranium.core.models.identity.IdentityClusterMember
import java.time.ZonedDateTime
import java.util.UUID

/**
 * JPA entity for identity_cluster_members join table.
 *
 * This is a system-managed join table — it does NOT extend AuditableSoftDeletableEntity.
 * Members are hard-deleted when clusters merge; no soft-delete lifecycle applies here.
 */
@Entity
@Table(name = "identity_cluster_members")
data class IdentityClusterMemberEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "cluster_id", nullable = false, columnDefinition = "uuid")
    val clusterId: UUID,

    @Column(name = "entity_id", nullable = false, columnDefinition = "uuid")
    val entityId: UUID,

    @Column(name = "joined_at", nullable = false, columnDefinition = "timestamptz")
    val joinedAt: ZonedDateTime = ZonedDateTime.now(),

    @Column(name = "joined_by", columnDefinition = "uuid")
    val joinedBy: UUID? = null,
) {

    /** Convert this entity to the domain model. */
    fun toModel(): IdentityClusterMember {
        val id = requireNotNull(this.id) { "IdentityClusterMemberEntity ID must not be null" }
        return IdentityClusterMember(
            id = id,
            clusterId = clusterId,
            entityId = entityId,
            joinedAt = joinedAt,
            joinedBy = joinedBy,
        )
    }
}
