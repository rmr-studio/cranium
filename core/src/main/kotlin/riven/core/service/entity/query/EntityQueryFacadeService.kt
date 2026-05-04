package riven.core.service.entity.query

import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.runBlocking
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import riven.core.models.entity.Entity
import riven.core.models.entity.partitionForEntityProjection
import riven.core.models.entity.payload.EntityAttribute
import riven.core.models.entity.payload.EntityAttributeRelationPayload
import riven.core.models.entity.query.EntityQuery
import riven.core.models.entity.query.QueryProjection
import riven.core.models.request.entity.EntityQueryRequest
import riven.core.models.response.entity.EntityQueryResponse
import riven.core.service.entity.EntityRelationshipService
import java.util.*

/**
 * Facade service that composes the entity query engine with relationship hydration.
 *
 * Bridges the raw SQL query engine (EntityQueryService) with JPA-based relationship
 * loading (EntityRelationshipService) to return fully hydrated entities for data tables.
 */
@Service
class EntityQueryFacadeService(
    private val entityQueryService: EntityQueryService,
    private val entityRelationshipService: EntityRelationshipService,
    private val logger: KLogger,
) {

    /**
     * Queries entities by type with optional filtering, pagination, and sorting,
     * then hydrates relationship attributes for the result page.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun queryEntities(
        workspaceId: UUID,
        entityTypeId: UUID,
        request: EntityQueryRequest,
    ): EntityQueryResponse {
        val query = EntityQuery(
            entityTypeId = entityTypeId,
            filter = request.filter,
            maxDepth = request.maxDepth,
        )

        val result = runBlocking {
            entityQueryService.execute(
                query = query,
                workspaceId = workspaceId,
                pagination = request.pagination,
                projection = request.projection,
                includeCount = request.includeCount,
            )
        }

        val hydrated = if (shouldHydrateRelationships(request.projection)) {
            hydrateRelationships(result.entities, workspaceId)
        } else {
            result.entities
        }
        val entities = applyProjection(hydrated, request.projection)

        return EntityQueryResponse(
            entities = entities,
            totalCount = result.totalCount,
            hasNextPage = result.hasNextPage,
            limit = request.pagination.limit,
            offset = request.pagination.offset,
        )
    }

    // ------ Private Helpers ------

    /**
     * Filters entity payloads to only include attributes and relationships specified in the projection.
     * Returns entities unchanged when projection is null (backward compatible).
     */
    private fun applyProjection(entities: List<Entity>, projection: QueryProjection?): List<Entity> {
        if (projection == null) return entities

        val includeAttrs = projection.includeAttributes?.toSet()
        val includeRels = projection.includeRelationships?.toSet()

        if (includeAttrs == null && includeRels == null) return entities

        return entities.map { entity ->
            val filtered = entity.payload.filter { (key, attr) ->
                val isRelationship = attr.payload is EntityAttributeRelationPayload
                if (isRelationship) {
                    includeRels?.contains(key) ?: false
                } else {
                    includeAttrs?.contains(key) ?: false
                }
            }
            entity.copy(payload = filtered)
        }
    }

    /**
     * Returns true when the projection requires relationship data, false when hydration can be skipped.
     */
    private fun shouldHydrateRelationships(projection: QueryProjection?): Boolean {
        if (projection == null) return true
        if (projection.includeAttributes == null && projection.includeRelationships == null) return true
        return !projection.includeRelationships.isNullOrEmpty()
    }

    private fun hydrateRelationships(entities: List<Entity>, workspaceId: UUID): List<Entity> {
        if (entities.isEmpty()) return entities

        val entityIds = entities.map { it.id }.toSet()
        val linksByEntity = entityRelationshipService.findRelatedEntities(entityIds, workspaceId)

        return entities.map { entity ->
            val entityLinks = linksByEntity[entity.id] ?: emptyList()
            if (entityLinks.isEmpty()) return@map entity

            val (relationships, knowledgeRefs) = entityLinks.partitionForEntityProjection()
            val relationshipAttributes = relationships.mapValues { (_, links) ->
                EntityAttribute(payload = EntityAttributeRelationPayload(relations = links))
            }
            entity.copy(
                payload = entity.payload + relationshipAttributes,
                knowledgeRefs = knowledgeRefs,
            )
        }
    }
}
