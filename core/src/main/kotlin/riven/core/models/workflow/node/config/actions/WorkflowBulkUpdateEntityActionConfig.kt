package riven.core.models.workflow.node.config.actions

import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.coroutines.runBlocking
import riven.core.enums.common.icon.IconType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.workflow.BulkUpdateErrorHandling
import riven.core.enums.workflow.OutputFieldType
import riven.core.enums.workflow.WorkflowActionType
import riven.core.enums.workflow.WorkflowNodeConfigFieldType
import riven.core.enums.workflow.WorkflowNodeType
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.models.entity.payload.EntityAttributeRequest
import riven.core.models.entity.query.EntityQuery
import riven.core.models.entity.query.pagination.QueryPagination
import riven.core.models.request.entity.SaveEntityRequest
import riven.core.models.workflow.engine.state.BulkUpdateEntityOutput
import riven.core.models.workflow.engine.state.NodeOutput
import riven.core.models.workflow.engine.state.WorkflowDataStore
import riven.core.models.workflow.node.NodeServiceProvider
import riven.core.models.workflow.node.config.WorkflowActionConfig
import riven.core.models.workflow.node.config.WorkflowNodeConfigField
import riven.core.models.workflow.node.config.WorkflowNodeOutputField
import riven.core.models.workflow.node.config.WorkflowNodeOutputMetadata
import riven.core.models.workflow.node.config.WorkflowNodeTypeMetadata
import riven.core.models.workflow.node.config.validation.ConfigValidationError
import riven.core.models.workflow.node.config.validation.ConfigValidationResult
import riven.core.models.workflow.node.service
import riven.core.service.entity.EntityService
import riven.core.service.entity.query.EntityQueryService
import riven.core.service.workflow.state.WorkflowNodeConfigValidationService
import riven.core.service.workflow.state.WorkflowNodeInputResolverService
import java.util.*

private val log = KotlinLogging.logger {}

private const val BULK_UPDATE_BATCH_SIZE = 50
private const val QUERY_PAGE_SIZE = 100
private const val MAX_BULK_UPDATE_ENTITIES = 10_000

/**
 * Configuration for BULK_UPDATE_ENTITY action nodes.
 *
 * Applies identical field updates to all entities matching a query.
 *
 * ## Configuration Properties
 *
 * @property query Embedded query definition to find entities for bulk update
 * @property payload Map of attribute updates to apply to each matching entity (template-enabled values)
 * @property errorHandling Error handling mode (FAIL_FAST or BEST_EFFORT)
 * @property pagination Optional pagination for the embedded query
 * @property timeoutSeconds Optional timeout override in seconds
 *
 * ## Example Configuration
 *
 * ```json
 * {
 *   "version": 1,
 *   "type": "ACTION",
 *   "subType": "BULK_UPDATE_ENTITY",
 *   "query": {
 *     "entityTypeId": "client-type-uuid",
 *     "filter": {
 *       "type": "ATTRIBUTE",
 *       "attributeId": "status-uuid",
 *       "operator": "EQUALS",
 *       "value": { "kind": "LITERAL", "value": "Pending" }
 *     }
 *   },
 *   "payload": {
 *     "status-uuid": "Active",
 *     "updated-by-uuid": "{{ trigger.userId }}"
 *   },
 *   "errorHandling": "BEST_EFFORT"
 * }
 * ```
 *
 * ## Output
 *
 * Returns map with:
 * - `entitiesUpdated`: Count of entities successfully updated
 * - `entitiesFailed`: Count of entities that failed to update
 * - `failedEntityDetails`: List of failed entity IDs with error messages
 * - `totalProcessed`: Total entities attempted
 */
@Schema(
    name = "WorkflowBulkUpdateEntityActionConfig",
    description = "Configuration for BULK_UPDATE_ENTITY action nodes that apply identical field updates to all entities matching a query."
)
@JsonTypeName("workflow_bulk_update_entity_action")
@JsonDeserialize(using = ValueDeserializer.None::class)
data class WorkflowBulkUpdateEntityActionConfig(
    override val version: Int = 1,

    @param:Schema(
        description = "Query to find entities for bulk update. Self-contained query configuration."
    )
    val query: EntityQuery,

    @param:Schema(
        description = "Map of attribute updates to apply to each matching entity. Keys are attribute UUID strings, values support templates.",
        example = """{"attr-uuid": "New Value", "status-uuid": "{{ steps.x.output.status }}"}"""
    )
    val payload: Map<String, String> = emptyMap(),

    @param:Schema(
        description = "Error handling mode for individual entity update failures.",
        defaultValue = "FAIL_FAST"
    )
    val errorHandling: BulkUpdateErrorHandling = BulkUpdateErrorHandling.FAIL_FAST,

    @param:Schema(
        description = "Optional pagination for the embedded query to limit entities processed.",
        nullable = true
    )
    val pagination: QueryPagination? = null,

    @param:Schema(
        description = "Optional timeout override in seconds.",
        nullable = true
    )
    val timeoutSeconds: Long? = null

) : WorkflowActionConfig {

    override val type: WorkflowNodeType
        get() = WorkflowNodeType.ACTION

    override val subType: WorkflowActionType
        get() = WorkflowActionType.BULK_UPDATE_ENTITY

    /**
     * Returns typed fields as a map for template resolution.
     * Used by WorkflowCoordinationService to resolve templates before execution.
     */
    override val config: Map<String, Any?>
        get() = mapOf(
            "entityTypeId" to query.entityTypeId.toString(),
            "filter" to query.filter,
            "payload" to payload,
            "errorHandling" to errorHandling.name,
            "pagination" to pagination,
            "timeoutSeconds" to timeoutSeconds
        )

    override val configSchema: List<WorkflowNodeConfigField>
        get() = Companion.configSchema

    companion object {
        val metadata = WorkflowNodeTypeMetadata(
            label = "Bulk Update Entities",
            description = "Applies identical updates to all entities matching a query",
            icon = IconType.LAYERS,
            category = WorkflowNodeType.ACTION
        )

        val configSchema: List<WorkflowNodeConfigField> = listOf(
            WorkflowNodeConfigField(
                key = "query",
                label = "Entity Query",
                type = WorkflowNodeConfigFieldType.ENTITY_QUERY,
                required = true,
                description = "Query to find entities for bulk update"
            ),
            WorkflowNodeConfigField(
                key = "payload",
                label = "Payload",
                type = WorkflowNodeConfigFieldType.KEY_VALUE,
                required = true,
                description = "Map of attribute updates to apply to each entity"
            ),
            WorkflowNodeConfigField(
                key = "errorHandling",
                label = "Error Handling",
                type = WorkflowNodeConfigFieldType.ENUM,
                required = true,
                description = "How to handle individual entity update failures"
            ),
            WorkflowNodeConfigField(
                key = "pagination",
                label = "Pagination",
                type = WorkflowNodeConfigFieldType.JSON,
                required = false,
                description = "Optional pagination to limit entities processed"
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
                    key = "entitiesUpdated",
                    label = "Entities Updated",
                    type = OutputFieldType.NUMBER,
                    description = "Count of entities successfully updated",
                    exampleValue = 25
                ),
                WorkflowNodeOutputField(
                    key = "entitiesFailed",
                    label = "Entities Failed",
                    type = OutputFieldType.NUMBER,
                    description = "Count of entities that failed to update",
                    exampleValue = 0
                ),
                WorkflowNodeOutputField(
                    key = "failedEntityDetails",
                    label = "Failed Entity Details",
                    type = OutputFieldType.LIST,
                    description = "Details of failed entity updates with entityId and error",
                    exampleValue = emptyList<Map<String, Any?>>()
                ),
                WorkflowNodeOutputField(
                    key = "totalProcessed",
                    label = "Total Processed",
                    type = OutputFieldType.NUMBER,
                    description = "Total entities attempted",
                    exampleValue = 25
                )
            )
        )
    }

    /**
     * Validates this configuration.
     *
     * Checks:
     * - query.filter structure is valid (recursive validation)
     * - payload values have valid template syntax
     * - pagination values are non-negative if present
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

        // Validate payload templates
        errors.addAll(validationService.validateTemplateMap(payload, "payload"))

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
        log.info { "Executing BULK_UPDATE_ENTITY for type: ${query.entityTypeId}, errorHandling: $errorHandling" }

        val entityQueryService = services.service<EntityQueryService>()
        val entityService = services.service<EntityService>()
        val inputResolver = services.service<WorkflowNodeInputResolverService>()
        val workspaceId = dataStore.metadata.workspaceId

        // Resolve template FilterValues in query.filter
        val resolvedFilter = query.filter?.let { filter ->
            WorkflowFilterTemplateUtils.resolveFilterTemplates(filter, dataStore, inputResolver)
        }

        // Resolve payload templates — fail explicitly if payload is missing or malformed
        @Suppress("UNCHECKED_CAST")
        val resolvedPayload = inputs["payload"] as? Map<*, *>
            ?: error("Expected 'payload' in resolved inputs but was missing or not a Map")

        val allEntityIds = collectMatchingEntityIds(entityQueryService, resolvedFilter, workspaceId)

        log.info { "Found ${allEntityIds.size} total entities to update" }

        if (allEntityIds.isEmpty()) {
            return BulkUpdateEntityOutput(
                entitiesUpdated = 0,
                entitiesFailed = 0,
                failedEntityDetails = emptyList(),
                totalProcessed = 0
            )
        }

        // Build payload once (same for all entities in bulk update)
        val entityPayload = buildEntityPayload(resolvedPayload)

        return processBatches(allEntityIds, entityPayload, entityService, workspaceId)
    }

    /**
     * Queries all matching entity IDs using paginated fetches with a hard cap.
     *
     * @throws IllegalStateException if the query matches more than [MAX_BULK_UPDATE_ENTITIES] entities
     */
    private fun collectMatchingEntityIds(
        entityQueryService: EntityQueryService,
        resolvedFilter: riven.core.models.entity.query.filter.QueryFilter?,
        workspaceId: UUID
    ): List<UUID> {
        val allEntityIds = mutableListOf<UUID>()
        var currentOffset = 0
        var hasNextPage = true

        log.info { "Querying all matching entities with pagination (page size: $QUERY_PAGE_SIZE)" }

        while (hasNextPage) {
            val pageQuery = EntityQuery(
                entityTypeId = query.entityTypeId,
                filter = resolvedFilter,
                maxDepth = query.maxDepth
            )

            val pagePagination = QueryPagination(
                limit = QUERY_PAGE_SIZE,
                offset = currentOffset
            )

            val pageResult = runBlocking {
                entityQueryService.execute(
                    query = pageQuery,
                    workspaceId = workspaceId,
                    pagination = pagePagination,
                    projection = null
                )
            }

            allEntityIds.addAll(pageResult.entities.map { it.id })

            if (allEntityIds.size > MAX_BULK_UPDATE_ENTITIES) {
                throw IllegalStateException(
                    "Bulk update query matched more than $MAX_BULK_UPDATE_ENTITIES entities. " +
                        "Narrow your query filter or use pagination."
                )
            }

            hasNextPage = pageResult.hasNextPage
            currentOffset += QUERY_PAGE_SIZE

            log.debug { "Fetched page with ${pageResult.entities.size} entities, total so far: ${allEntityIds.size}" }
        }

        return allEntityIds
    }

    /**
     * Builds the entity payload map from resolved template values.
     *
     * Maps attribute UUID string keys to [EntityAttributeRequest] wrappers.
     */
    private fun buildEntityPayload(resolvedPayload: Map<*, *>): Map<UUID, EntityAttributeRequest> {
        // TODO: Infer schema type from entity type schema for proper typing
        return resolvedPayload.mapKeys { (key, _) ->
            try {
                UUID.fromString(key as String)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Invalid attribute UUID key '$key' in bulk update payload", e
                )
            }
        }.mapValues { (_, value) ->
            EntityAttributeRequest(
                EntityAttributePrimitivePayload(
                    value = value,
                    schemaType = SchemaType.TEXT  // Default to TEXT, infer from schema later
                )
            )
        }
    }

    /**
     * Processes entity updates in batches with error handling.
     *
     * Uses [query.entityTypeId] directly instead of fetching each entity individually.
     */
    private fun processBatches(
        allEntityIds: List<UUID>,
        entityPayload: Map<UUID, EntityAttributeRequest>,
        entityService: EntityService,
        workspaceId: UUID
    ): BulkUpdateEntityOutput {
        var entitiesUpdated = 0
        var entitiesFailed = 0
        val failedDetails = mutableListOf<Map<String, Any?>>()

        log.info { "Processing ${allEntityIds.size} entities in batches of $BULK_UPDATE_BATCH_SIZE" }

        for (batch in allEntityIds.chunked(BULK_UPDATE_BATCH_SIZE)) {
            for (entityId in batch) {
                try {
                    val saveRequest = SaveEntityRequest(
                        id = entityId,
                        payload = entityPayload,
                        icon = null
                    )

                    entityService.saveEntity(
                        workspaceId,
                        query.entityTypeId,
                        saveRequest
                    )

                    entitiesUpdated++

                } catch (e: Exception) {
                    when (errorHandling) {
                        BulkUpdateErrorHandling.FAIL_FAST -> {
                            log.error { "FAIL_FAST: Entity $entityId update failed: ${e.message}" }
                            return BulkUpdateEntityOutput(
                                entitiesUpdated = entitiesUpdated,
                                entitiesFailed = 1,
                                failedEntityDetails = listOf(
                                    mapOf(
                                        "entityId" to entityId.toString(),
                                        "error" to (e.message ?: "Unknown error")
                                    )
                                ),
                                totalProcessed = entitiesUpdated + 1
                            )
                        }
                        BulkUpdateErrorHandling.BEST_EFFORT -> {
                            log.warn { "BEST_EFFORT: Entity $entityId update failed: ${e.message}" }
                            entitiesFailed++
                            failedDetails.add(
                                mapOf(
                                    "entityId" to entityId.toString(),
                                    "error" to (e.message ?: "Unknown error")
                                )
                            )
                        }
                    }
                }
            }
        }

        val totalProcessed = entitiesUpdated + entitiesFailed
        log.info { "BULK_UPDATE_ENTITY complete: $entitiesUpdated updated, $entitiesFailed failed, $totalProcessed total" }

        return BulkUpdateEntityOutput(
            entitiesUpdated = entitiesUpdated,
            entitiesFailed = entitiesFailed,
            failedEntityDetails = failedDetails,
            totalProcessed = totalProcessed
        )
    }

}
