package riven.core.repository.note

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import riven.core.entity.note.NoteEntity
import java.time.ZonedDateTime
import java.util.*

interface NoteRepository : JpaRepository<NoteEntity, UUID> {

    // ------ Entity-scoped queries (existing) ------

    fun findByEntityIdAndWorkspaceIdOrderByCreatedAtDesc(entityId: UUID, workspaceId: UUID): List<NoteEntity>

    @Query(
        value = "SELECT * FROM notes WHERE entity_id = :entityId AND workspace_id = :workspaceId AND search_vector @@ plainto_tsquery('english', :searchTerm) ORDER BY created_at DESC",
        nativeQuery = true
    )
    fun searchByEntityIdAndWorkspaceId(entityId: UUID, workspaceId: UUID, searchTerm: String): List<NoteEntity>

    // ------ Workspace-scoped queries ------

    @Query(
        """
        SELECT n FROM NoteEntity n
        WHERE n.workspaceId = :workspaceId
        AND (n.createdAt < :cursorCreatedAt OR (n.createdAt = :cursorCreatedAt AND n.id < :cursorId))
        ORDER BY n.createdAt DESC, n.id DESC
        """
    )
    fun findByWorkspaceId(
        @Param("workspaceId") workspaceId: UUID,
        @Param("cursorCreatedAt") cursorCreatedAt: ZonedDateTime,
        @Param("cursorId") cursorId: UUID,
        pageable: Pageable,
    ): List<NoteEntity>

    @Query(
        value = """
            SELECT * FROM notes
            WHERE workspace_id = :workspaceId
            AND search_vector @@ plainto_tsquery('english', :searchTerm)
            AND (created_at < :cursorCreatedAt OR (created_at = :cursorCreatedAt AND id < :cursorId))
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun searchByWorkspaceId(
        @Param("workspaceId") workspaceId: UUID,
        @Param("searchTerm") searchTerm: String,
        @Param("cursorCreatedAt") cursorCreatedAt: ZonedDateTime,
        @Param("cursorId") cursorId: UUID,
        @Param("limit") limit: Int,
    ): List<NoteEntity>

    @Query(
        """
        SELECT n FROM NoteEntity n
        WHERE n.id = :noteId AND n.workspaceId = :workspaceId
        """
    )
    fun findByIdAndWorkspaceId(
        @Param("noteId") noteId: UUID,
        @Param("workspaceId") workspaceId: UUID,
    ): NoteEntity?

    fun countByWorkspaceId(workspaceId: UUID): Long

    /**
     * Batch-fetches entity context (display name, type key, icon) for a set of entity IDs.
     * Used to enrich notes with their parent entity metadata.
     *
     * Returns rows of: [entity_id, entity_display_name, entity_type_key, entity_type_icon, entity_type_colour]
     */
    @Query(
        value = """
            SELECT e.id AS entity_id,
                   (SELECT ea.value->>'value'
                    FROM entity_attributes ea
                    WHERE ea.entity_id = e.id
                      AND ea.attribute_id = e.identifier_key
                      AND ea.deleted = false
                    LIMIT 1) AS entity_display_name,
                   et.key AS entity_type_key,
                   et.icon_type AS entity_type_icon,
                   et.icon_colour AS entity_type_colour
            FROM entities e
            JOIN entity_types et ON e.type_id = et.id
            WHERE e.id IN (:entityIds)
              AND e.deleted = false
        """,
        nativeQuery = true
    )
    fun findEntityContext(@Param("entityIds") entityIds: List<UUID>): List<Array<Any?>>
}
