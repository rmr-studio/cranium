package cranium.core.repository.identity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import cranium.core.entity.identity.IdentityClusterMemberEntity
import java.util.UUID

/**
 * Repository for IdentityClusterMemberEntity instances.
 */
interface IdentityClusterMemberRepository : JpaRepository<IdentityClusterMemberEntity, UUID> {

    /**
     * Returns the cluster membership for a given entity, or null if the entity is not in any cluster.
     */
    fun findByEntityId(entityId: UUID): IdentityClusterMemberEntity?

    /**
     * Returns all members of the given cluster.
     */
    fun findByClusterId(clusterId: UUID): List<IdentityClusterMemberEntity>

    /**
     * Returns the membership for a specific entity within a specific cluster, or null if not found.
     *
     * Used by [cranium.core.service.identity.IdentityClusterService] to verify a target member
     * is actually in the cluster before creating a relationship with the new entity.
     */
    fun findByClusterIdAndEntityId(clusterId: UUID, entityId: UUID): IdentityClusterMemberEntity?

    /**
     * Hard-deletes all members of the given cluster.
     * Used during cluster merge operations where all members of the source cluster
     * are reassigned to the target cluster and the old membership rows are removed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM IdentityClusterMemberEntity m WHERE m.clusterId = :clusterId")
    fun deleteByClusterId(@Param("clusterId") clusterId: UUID)
}
