package riven.core.repository.identity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import riven.core.entity.identity.MatchSuggestionEntity
import java.util.UUID



/**
 * Repository for MatchSuggestionEntity instances.
 */
interface MatchSuggestionRepository : JpaRepository<MatchSuggestionEntity, UUID> {

    /**
     * Finds an active (non-deleted) suggestion for the given canonical entity pair.
     *
     * Uses native SQL with explicit deleted filter rather than JPQL with @SQLRestriction
     * because this query runs in the same transaction as [findRejectedSuggestion], which
     * loads deleted rows into the persistence context. JPQL queries can return stale
     * L1-cached entities that don't respect @SQLRestriction, causing false positives.
     *
     * Used by the suggestion service to detect duplicates before inserting a new suggestion.
     */
    @Query(
        value = """
            SELECT * FROM match_suggestions
            WHERE workspace_id = :workspaceId
              AND source_entity_id = :sourceEntityId
              AND target_entity_id = :targetEntityId
              AND deleted = false
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findActiveSuggestion(
        @Param("workspaceId") workspaceId: UUID,
        @Param("sourceEntityId") sourceEntityId: UUID,
        @Param("targetEntityId") targetEntityId: UUID,
    ): MatchSuggestionEntity?

    /**
     * Finds the most recently rejected suggestion for the given canonical entity pair.
     *
     * Used to compare prior signal snapshot when re-evaluating a previously dismissed pair.
     */
    @Query(
        value = """
            SELECT * FROM match_suggestions
            WHERE workspace_id = :workspaceId
              AND source_entity_id = :sourceEntityId
              AND target_entity_id = :targetEntityId
              AND status = 'REJECTED'
              AND deleted = true
            ORDER BY created_at DESC
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findRejectedSuggestion(
        @Param("workspaceId") workspaceId: UUID,
        @Param("sourceEntityId") sourceEntityId: UUID,
        @Param("targetEntityId") targetEntityId: UUID,
    ): MatchSuggestionEntity?

    /**
     * Returns all non-deleted suggestions for the given workspace (PENDING + CONFIRMED).
     *
     * @SQLRestriction on [MatchSuggestionEntity] auto-excludes deleted rows, so this derived
     * query returns only active suggestions without an explicit deleted filter.
     */
    fun findByWorkspaceId(workspaceId: UUID): List<MatchSuggestionEntity>

    /**
     * Counts PENDING suggestions where the given entity is source OR target.
     *
     * @SQLRestriction on [MatchSuggestionEntity] auto-excludes deleted rows,
     * so no explicit deleted filter is needed.
     */
    @Query(
        """
        SELECT COUNT(m) FROM MatchSuggestionEntity m
        WHERE m.workspaceId = :workspaceId
          AND (m.sourceEntityId = :entityId OR m.targetEntityId = :entityId)
          AND m.status = riven.core.enums.identity.MatchSuggestionStatus.PENDING
        """
    )
    fun countPendingForEntity(
        @Param("workspaceId") workspaceId: UUID,
        @Param("entityId") entityId: UUID,
    ): Long
}
