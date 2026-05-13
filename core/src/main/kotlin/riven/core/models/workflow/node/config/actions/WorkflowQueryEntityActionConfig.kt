package cranium.core.models.workflow.node.config.actions

import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.coroutines.runBlocking
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.workflow.OutputFieldType
import cranium.core.enums.workflow.WorkflowActionType
import cranium.core.enums.workflow.WorkflowNodeConfigFieldType
import cranium.core.enums.workflow.WorkflowNodeType
import cranium.core.models.entity.query.EntityQuery
import cranium.core.models.entity.query.QueryProjection
import cranium.core.models.entity.query.pagination.QueryPagination
import cranium.core.models.workflow.engine.state.NodeOutput
import cranium.core.models.workflow.engine.state.QueryEntityOutput
import cranium.core.models.workflow.engine.state.WorkflowDataStore
import cranium.core.models.workflow.node.NodeServiceProvider
import cranium.core.models.workflow.node.config.WorkflowActionConfig
import cranium.core.models.workflow.node.config.WorkflowNodeConfigField
import cranium.core.models.workflow.node.config.WorkflowNodeOutputField
import cranium.core.models.workflow.node.config.WorkflowNodeOutputMetadata
import cranium.core.models.workflow.node.config.WorkflowNodeTypeMetadata
import cranium.core.models.workflow.node.config.validation.ConfigValidationError
import cranium.core.models.workflow.node.config.validation.ConfigValidationResult
import cranium.core.models.workflow.node.service
import cranium.core.service.entity.query.EntityQueryService
import cranium.core.service.workflow.state.WorkflowNodeConfigValidationService
import cranium.core.service.workflow.state.WorkflowNodeInputResolverService
import java.util.*

private val log = KotlinLogging.logger {}

private const val DEFAULT_QUERY_LIMIT = 100

/**
 * Configuration for QUERY_ENTITY action nodes.
 *
 * Queries entities of a specific type with optional filtering by attributes and relationships.
 *
 * ## Configuration Properties
 *
 * @property query Core query definition with entity type, workspace, and optional filters
 * @property pagination Optional pagination and ordering configuration
 * @property projection Optional field selection for results
 * @property timeoutSeconds Optional timeout override in seconds
 *
 * ## Example Configurations
 *
 * ### Simple attribute filter (Status == "Active"):
 * ```json
 * {
 *   "version": 1,
 *   "type": "ACTION",
 *   "subType": "QUERY_ENTITY",
 *   "query": {
 *     "entityTypeId": "client-type-uuid",
 *     "filter": {
 *       "type": "ATTRIBUTE",
 *       "attributeId": "status-uuid",
 *       "operator": "EQUALS",
 *       "value": { "kind": "LITERAL", "value": "Active" }
 *     }
 *   }
 * }
 * ```
 *
 * ### Compound filter (Status == "Active" AND ARR > 100000):
 * ```json
 * {
 *   "query": {
 *     "entityTypeId": "client-type-uuid",
 *     "filter": {
 *       "type": "AND",
 *       "conditions": [
 *         {
 *           "type": "ATTRIBUTE",
 *           "attributeId": "status-uuid",
 *           "operator": "EQUALS",
 *           "value": { "kind": "LITERAL", "value": "Active" }
 *         },
 *         {
 *           "type": "ATTRIBUTE",
 *           "attributeId": "arr-uuid",
 *           "operator": "GREATER_THAN",
 *           "value": { "kind": "LITERAL", "value": 100000 }
 *         }
 *       ]
 *     }
 *   }
 * }
 * ```
 *
 * ### Relationship filter (Projects related to a specific Client):
 * ```json
 * {
 *   "query": {
 *     "entityTypeId": "project-type-uuid",
 *     "filter": {
 *       "type": "RELATIONSHIP",
 *       "relationshipId": "client-relationship-uuid",
 *       "condition": {
 *         "type": "TARGET_EQUALS",
 *         "entityIds": ["{{ trigger.input.clientId }}"]
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * ### Nested relationship filter (Projects with Premium Clients):
 * ```json
 * {
 *   "query": {
 *     "entityTypeId": "project-type-uuid",
 *     "filter": {
 *       "type": "RELATIONSHIP",
 *       "relationshipId": "client-relationship-uuid",
 *       "condition": {
 *         "type": "TARGET_MATCHES",
 *         "filter": {
 *           "type": "ATTRIBUTE",
 *           "attributeId": "client-tier-uuid",
 *           "operator": "EQUALS",
 *           "value": { "kind": "LITERAL", "value": "Premium" }
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * ## Output
 *
 * Returns map with:
 * - `entities`: List of matching entities with payload, icon, identifier, timestamps
 * - `totalCount`: Total number of matching entities (before pagination)
 * - `hasMore`: Boolean indicating if more results exist beyond current page
 */
@Schema(
    name = "WorkflowQueryEntityActionConfig",
    description = "Configuration for QUERY_ENTITY action nodes that query entities by type with filtering."
)
@JsonTypeName("workflow_query_entity_action")
@JsonDeserialize(using = ValueDeserializer.None::class)
data class WorkflowQueryEntityActionConfig(
    override val version: Int = 1,

    @param:Schema(
        description = "Core query definition with entity type, workspace scope, and optional filters."
    )
    val query: EntityQuery,

    @param:Schema(
        description = "Optional pagination and ordering configuration.",
        nullable = true
    )
    val pagination: QueryPagination? = null,

    @param:Schema(
        description = "Optional field selection for query results.",
        nullable = true
    )
    val projection: QueryProjection? = null,

    @param:Schema(
        description = "Optional timeout override in seconds.",
        nullable = true
    )
    val timeoutSeconds: Long? = null

) : WorkflowActionConfig {

    override val type: WorkflowNodeType
        get() = WorkflowNodeType.ACTION

    override val subType: WorkflowActionType
        get() = WorkflowActionType.QUERY_ENTITY

    /**
     * Returns typed fields as a map for template resolution.
     * Used by WorkflowCoordinationService to resolve templates before execution.
     */
    override val config: Map<String, Any?>
        get() = mapOf(
            "entityTypeId" to query.entityTypeId.toString(),
            "filter" to query.filter,
            "pagination" to pagination,
            "projection" to projection,
            "timeoutSeconds" to timeoutSeconds
        )

    override val configSchema: List<WorkflowNodeConfigField>
        get() = Companion.configSchema

    companion object {
        val metadata = WorkflowNodeTypeMetadata(
            label = "Query Entities",
            description = "Searches and retrieves entity instances",
            icon = IconType.SEARCH,
            category = WorkflowNodeType.ACTION
        )

        val configSchema: List<WorkflowNodeConfigField> = listOf(
            WorkflowNodeConfigField(
                key = "query",
                label = "Entity Query",
                type = WorkflowNodeConfigFieldType.ENTITY_QUERY,
                required = true,
                description = "Query definition with entity type and optional filters"
            ),
            WorkflowNodeConfigField(
                key = "pagination",
                label = "Pagination",
                type = WorkflowNodeConfigFieldType.JSON,
                required = false,
                description = "Pagination and ordering configuration"
            ),
            WorkflowNodeConfigField(
                key = "projection",
                label = "Projection",
                type = WorkflowNodeConfigFieldType.JSON,
                required = false,
                description = "Field selection for query results"
            ),
            WorkflowNodeConfigField(
                key = "timeoutSeconds",
                label = "Timeout (seconds)",
                type = WorkflowNodeConfigFieldType.DURATION,
                required = false,
                description = "Optional timeout override in seconds"
            )
        )

        val outputMetadata = WorkflowNodeOutputMetadata(
            fields = listOf(
                WorkflowNodeOutputField(
                    key = "entities",
                    label = "Matching Entities",
                    type = OutputFieldType.ENTITY_LIST,
                    description = "List of entities matching the query filters",
                    entityTypeId = null,  // Dynamic - resolved from query.entityTypeId at runtime
                    exampleValue = listOf(
                        mapOf(
                            "id" to "550e8400-e29b-41d4-a716-446655440000",
                            "typeId" to "660e8400-e29b-41d4-a716-446655440001",
                            "payload" to mapOf("attr-uuid" to "Example Value")
                        )
                    )
                ),
                WorkflowNodeOutputField(
                    key = "totalCount",
                    label = "Total Count",
                    type = OutputFieldType.NUMBER,
                    description = "Total number of matching entities (before pagination limit)",
                    exampleValue = 42
                ),
                WorkflowNodeOutputField(
                    key = "hasMore",
                    label = "Has More",
                    type = OutputFieldType.BOOLEAN,
                    description = "Whether more results exist beyond the system limit",
                    exampleValue = false
                )
            )
        )
    }

    /**
     * Validates this configuration.
     *
     * Checks:
     * - entityTypeId is valid UUID
     * - filter structure is valid (recursive validation)
     * - pagination values are non-negative
     * - timeout is non-negative if provided
     *
     * @param injector Spring managed provider to inject services into model
     * @return Validation result with any errors
     */
    override fun validate(injector: NodeServiceProvider): ConfigValidationResult {
        val validationService = injector.service<WorkflowNodeConfigValidationService>()
        val errors = mutableListOf<ConfigValidationError>()

        // Validate filter if present
        query.filter?.let { filter ->
            errors.addAll(WorkflowFilterTemplateUtils.validateFilter(filter, "query.filter", validationService))
        }

        // Validate pagination if present
        pagination?.let { paging ->
            if (paging.limit < 0) {
                errors.add(ConfigValidationError("pagination.limit", "Limit must be non-negative"))
            }
            if (paging.offset < 0) {
                errors.add(ConfigValidationError("pagination.offset", "Offset must be non-negative"))
            }
        }

        // Validate timeout
        errors.addAll(validationService.validateOptionalDuration(timeoutSeconds, "timeoutSeconds"))

        return ConfigValidationResult(errors)
    }

    override fun execute(
        dataStore: WorkflowDataStore,
        inputs: Map<String, Any?>,
        services: NodeServiceProvider
    ): NodeOutput {
        log.info { "Executing QUERY_ENTITY for type: ${query.entityTypeId}" }

        // Get required services
        val entityQueryService = services.service<EntityQueryService>()
        val inputResolverService = services.service<WorkflowNodeInputResolverService>()

        // Resolve template values in the filter tree
        val resolvedFilter = query.filter?.let { filter ->
            WorkflowFilterTemplateUtils.resolveFilterTemplates(filter, dataStore, inputResolverService)
        }

        // Build query with resolved filter
        val resolvedQuery = EntityQuery(
            entityTypeId = query.entityTypeId,
            filter = resolvedFilter,
            maxDepth = query.maxDepth
        )

        // Apply system-wide query limit to prevent runaway queries
        val effectivePagination = pagination?.let {
            it.copy(limit = minOf(it.limit, DEFAULT_QUERY_LIMIT))
        } ?: QueryPagination(limit = DEFAULT_QUERY_LIMIT)

        // Execute query (EntityQueryService.execute is suspend, so wrap in runBlocking)
        val result = runBlocking {
            entityQueryService.execute(
                query = resolvedQuery,
                workspaceId = dataStore.metadata.workspaceId,
                pagination = effectivePagination,
                projection = projection
            )
        }

        // Transform entities to maps with full entity structure
        val entityMaps = result.entities.map { entity ->
            mapOf(
                "id" to entity.id,
                "typeId" to entity.typeId,
                "payload" to entity.payload,
                "icon" to entity.icon,
                "identifierKey" to entity.identifierKey,
                "createdAt" to entity.createdAt,
                "updatedAt" to entity.updatedAt
            )
        }

        // Return QueryEntityOutput with resolved data
        return QueryEntityOutput(
            entities = entityMaps,
            totalCount = (result.totalCount ?: 0L).toInt(),
            hasMore = result.hasNextPage
        )
    }

}
