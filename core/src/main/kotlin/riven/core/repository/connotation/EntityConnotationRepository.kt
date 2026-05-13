package cranium.core.repository.connotation

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import cranium.core.entity.connotation.EntityConnotationEntity
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Repository for [EntityConnotationEntity].
 *
 * One snapshot per entity, enforced by `UNIQUE(entity_id)`. Writes go through
 * [upsertByEntityId] — a single atomic `INSERT ... ON CONFLICT DO UPDATE`
 * statement so concurrent writers cannot collide on the unique constraint or
 * partially overwrite each other's snapshot.
 */
@Repository
interface EntityConnotationRepository : JpaRepository<EntityConnotationEntity, UUID> {

    /**
     * Find the connotation snapshot for a given entity.
     *
     * @param entityId The entity UUID
     * @return The snapshot row if it exists, null otherwise
     */
    @Query("SELECT e FROM EntityConnotationEntity e WHERE e.entityId = :entityId")
    fun findByEntityId(@Param("entityId") entityId: UUID): EntityConnotationEntity?

    /**
     * Atomic upsert keyed by `entity_id`.
     *
     * `INSERT ... ON CONFLICT (entity_id) DO UPDATE` guarantees a single resulting
     * row regardless of concurrent writers, with last-write-wins semantics on the
     * snapshot payload, workspace, and `updated_at`. JPQL has no upsert so this
     * is expressed as native SQL per CLAUDE.md ("Reserve native SQL for cases
     * where JPQL is genuinely insufficient").
     *
     * @param entityId The entity UUID this snapshot describes
     * @param workspaceId The workspace the entity belongs to
     * @param snapshotJson The serialised [cranium.core.models.connotation.EntityMetadataSnapshot]
     * @param now The timestamp to write into `created_at` (insert path) and `updated_at` (both paths)
     * @return Number of rows affected (always 1)
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO entity_connotation (entity_id, workspace_id, connotation_metadata, created_at, updated_at)
            VALUES (:entityId, :workspaceId, CAST(:snapshotJson AS jsonb), :now, :now)
            ON CONFLICT (entity_id) DO UPDATE
            SET connotation_metadata = EXCLUDED.connotation_metadata,
                workspace_id = EXCLUDED.workspace_id,
                updated_at = EXCLUDED.updated_at
        """,
        nativeQuery = true,
    )
    fun upsertByEntityId(
        @Param("entityId") entityId: UUID,
        @Param("workspaceId") workspaceId: UUID,
        @Param("snapshotJson") snapshotJson: String,
        @Param("now") now: ZonedDateTime,
    ): Int
}
