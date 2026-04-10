package riven.core.repository.enrichment

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import riven.core.entity.enrichment.EntityEmbeddingEntity
import java.util.*

/**
 * Repository for entity embedding persistence.
 */
@Repository
interface EntityEmbeddingRepository : JpaRepository<EntityEmbeddingEntity, UUID> {

    /**
     * Find embedding for a specific entity.
     *
     * @param entityId The entity UUID
     * @return Embedding entity if exists, null otherwise
     */
    @Query("SELECT e FROM EntityEmbeddingEntity e WHERE e.entityId = :entityId")
    fun findByEntityId(@Param("entityId") entityId: UUID): EntityEmbeddingEntity?

    /**
     * Find all embeddings for a workspace.
     *
     * @param workspaceId The workspace UUID
     * @return All embeddings in the workspace
     */
    @Query("SELECT e FROM EntityEmbeddingEntity e WHERE e.workspaceId = :workspaceId")
    fun findByWorkspaceId(@Param("workspaceId") workspaceId: UUID): List<EntityEmbeddingEntity>

    /**
     * Hard-delete embedding for an entity.
     * Used when the parent entity is soft-deleted.
     *
     * @param entityId The entity UUID
     */
    @Modifying
    @Query("DELETE FROM EntityEmbeddingEntity e WHERE e.entityId = :entityId")
    fun deleteByEntityId(@Param("entityId") entityId: UUID)
}
