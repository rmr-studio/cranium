package cranium.core.service.entity

import io.github.oshai.kotlinlogging.KLogger
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import cranium.core.entity.entity.EntityTypeEntity
import java.util.UUID

/**
 * Workspace-wide full-text search over `entities.search_vector`.
 *
 * Notion-style ranking: the entity's identifier attribute is weighted A (title match);
 * every other TEXT / EMAIL / URL / PHONE attribute that is not flagged
 * `excludeFromSearch` in the type schema feeds the body half, weighted B. Recomputed
 * by [recompute] on every entity write — callers must invoke it after attributes are
 * persisted or the index will drift.
 *
 * The recompute path swallows exceptions: a search-index failure must not abort the
 * surrounding entity save (e.g. on H2 in unit tests where `tsvector` does not exist).
 */
@Service
class EntitySearchService(
    private val entityManager: EntityManager,
    private val logger: KLogger,
) {

    /**
     * Recompute `search_vector` for a single entity. Pulls attribute values directly
     * from the persisted `entity_attributes` rows so the source of truth is always the
     * post-save state, not the in-memory request payload.
     */
    @Transactional
    fun recompute(workspaceId: UUID, entityId: UUID, type: EntityTypeEntity) {
        val excluded = excludedAttributeIds(type)
        try {
            val sql = """
                UPDATE entities e
                SET search_vector =
                    setweight(
                        to_tsvector('english',
                            coalesce((
                                SELECT ea.value->>'value'
                                FROM entity_attributes ea
                                WHERE ea.entity_id = e.id
                                  AND ea.attribute_id = e.identifier_key
                                  AND ea.deleted = false
                                LIMIT 1
                            ), '')
                        ), 'A')
                    ||
                    setweight(
                        to_tsvector('english',
                            coalesce((
                                SELECT string_agg(ea.value->>'value', ' ')
                                FROM entity_attributes ea
                                WHERE ea.entity_id = e.id
                                  AND ea.attribute_id <> e.identifier_key
                                  AND ea.deleted = false
                                  AND ea.schema_type IN ('TEXT', 'EMAIL', 'URL', 'PHONE')
                                  AND NOT (ea.attribute_id = ANY(CAST(:excluded AS uuid[])))
                            ), '')
                        ), 'B')
                WHERE e.id = CAST(:id AS uuid)
                  AND e.workspace_id = CAST(:workspaceId AS uuid)
            """.trimIndent()

            entityManager.createNativeQuery(sql)
                .setParameter("id", entityId)
                .setParameter("workspaceId", workspaceId)
                .setParameter("excluded", excluded.toTypedArray())
                .executeUpdate()
        } catch (e: Exception) {
            logger.debug(e) { "search_vector recompute skipped for entity $entityId — backend likely lacks tsvector support" }
        }
    }

    /**
     * Workspace-scoped FTS query. Returns matching entity IDs ranked by `ts_rank_cd`
     * descending. Optional [typeIds] narrows by entity type. [limit] caps the page size.
     *
     * Uses `websearch_to_tsquery` so callers can pass natural-language queries
     * (`acme & contact`, `"exact phrase"`, `-excluded`) without parsing.
     */
    @Suppress("UNCHECKED_CAST")
    fun search(
        workspaceId: UUID,
        query: String,
        typeIds: Collection<UUID>? = null,
        limit: Int = 50,
    ): List<UUID> {
        if (query.isBlank()) return emptyList()
        val typeFilter = typeIds?.takeIf { it.isNotEmpty() }
        val sql = """
            SELECT e.id, ts_rank_cd(e.search_vector, websearch_to_tsquery('english', :q)) AS rank
            FROM entities e
            WHERE e.workspace_id = CAST(:workspaceId AS uuid)
              AND e.deleted = false
              AND e.search_vector IS NOT NULL
              AND e.search_vector @@ websearch_to_tsquery('english', :q)
              ${if (typeFilter != null) "AND e.type_id = ANY(CAST(:typeIds AS uuid[]))" else ""}
            ORDER BY rank DESC
            LIMIT :limit
        """.trimIndent()

        val q = entityManager.createNativeQuery(sql)
            .setParameter("q", query)
            .setParameter("workspaceId", workspaceId)
            .setParameter("limit", limit)
        if (typeFilter != null) q.setParameter("typeIds", typeFilter.toTypedArray())

        return (q.resultList as List<Array<Any>>).map { it[0] as UUID }
    }

    private fun excludedAttributeIds(type: EntityTypeEntity): List<UUID> =
        type.schema.properties
            ?.filterValues { it.options?.excludeFromSearch == true }
            ?.keys
            ?.toList()
            ?: emptyList()
}
