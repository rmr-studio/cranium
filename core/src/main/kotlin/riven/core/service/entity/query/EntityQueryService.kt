package riven.core.service.entity.query

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import riven.core.configuration.properties.QueryConfigurationProperties
import riven.core.exceptions.NotFoundException
import riven.core.exceptions.query.InvalidAttributeReferenceException
import riven.core.exceptions.query.QueryExecutionException
import riven.core.exceptions.query.QueryValidationException
import riven.core.enums.entity.query.QueryDirection
import riven.core.models.entity.RelationshipDefinition
import riven.core.models.entity.query.EntityQuery
import riven.core.models.entity.query.EntityQueryResult
import riven.core.models.entity.query.QueryProjection
import riven.core.models.entity.query.filter.QueryFilter
import riven.core.models.entity.query.pagination.QueryPagination
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.service.entity.type.EntityTypeRelationshipService
import java.util.*
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/**
 * Single entry point for executing entity queries.
 *
 * Takes an [EntityQuery] with optional filter criteria, validates all filter references
 * against the entity type schema, executes parameterized SQL via [NamedParameterJdbcTemplate],
 * and returns an [EntityQueryResult] with typed [riven.core.models.entity.Entity] domain models
 * preserving pagination order.
 *
 * @property entityTypeRepository Repository for loading entity type schemas
 * @property entityRepository Repository for batch-loading full entity objects by IDs
 * @property assembler Query assembler that produces parameterized SQL from filters
 * @property validator Pre-validator for filter references and relationship depth
 * @property jdbcTemplate Dedicated JDBC template with configured query timeout
 */
@Service
class EntityQueryService(
    private val entityTypeRepository: EntityTypeRepository,
    private val entityRepository: EntityRepository,
    private val assembler: EntityQueryAssembler,
    private val validator: QueryFilterValidator,
    private val entityTypeRelationshipService: EntityTypeRelationshipService,
    private val entityAttributeService: riven.core.service.entity.EntityAttributeService,
    dataSource: DataSource,
    queryProperties: QueryConfigurationProperties,
) {

    private val jdbcTemplate: NamedParameterJdbcTemplate

    init {
        val innerTemplate = JdbcTemplate(dataSource)
        innerTemplate.queryTimeout = queryProperties.timeoutSeconds.toInt()
        jdbcTemplate = NamedParameterJdbcTemplate(innerTemplate)
        logger.debug { "EntityQueryService initialized with query timeout: ${queryProperties.timeoutSeconds}s" }
    }

    /**
     * Executes an entity query and returns matching entities with pagination metadata.
     *
     * @param query The entity query with filter criteria and depth limits
     * @param workspaceId UUID of the workspace for isolation
     * @param pagination Pagination configuration (limit, offset, orderBy)
     * @param projection Optional field selection hints (not yet implemented in Phase 5)
     * @return [EntityQueryResult] with entities, totalCount, hasNextPage, and projection
     * @throws NotFoundException if the entity type does not exist
     * @throws QueryValidationException if filter references are invalid or depth exceeded
     * @throws QueryExecutionException if SQL execution fails
     */
    suspend fun execute(
        query: EntityQuery,
        workspaceId: UUID,
        pagination: QueryPagination = QueryPagination(),
        projection: QueryProjection? = null,
        includeCount: Boolean = true,
    ): EntityQueryResult {
        logger.debug {
            "Executing entity query for entity type ${query.entityTypeId} " +
                    "with filter: ${query.filter != null}, " +
                    "pagination: limit=${pagination.limit} offset=${pagination.offset}, " +
                    "includeCount: $includeCount, " +
                    "maxDepth: ${query.maxDepth}"
        }

        // Step 1: Load Entity Type
        val entityTypeEntity = withContext(Dispatchers.IO) {
            entityTypeRepository.findById(query.entityTypeId)
        }.orElseThrow { NotFoundException("Entity type ${query.entityTypeId} not found") }

        val entityTypeId = requireNotNull(entityTypeEntity.id) { "Entity type ID cannot be null" }

        // Step 2: Validate Filter References (if filter present)
        val resolvedDefinitions = if (query.filter != null) {
            loadRelationshipDefinitions(workspaceId, entityTypeId)
        } else {
            emptyMap()
        }

        if (query.filter != null) {
            val attributeIds = entityTypeEntity.schema.properties?.keys ?: emptySet()
            val definitionsOnly = resolvedDefinitions.mapValues { it.value.first }
            validateFilterReferences(query.filter, attributeIds, definitionsOnly, query.maxDepth)
        }

        // Step 3: Assemble SQL
        val relationshipDirections = resolvedDefinitions.mapValues { it.value.second }
        val paramGen = ParameterNameGenerator()
        val assembled = assembler.assemble(entityTypeId, workspaceId, query.filter, pagination, paramGen, relationshipDirections, includeCount)

        logger.debug {
            "Assembled SQL with ${assembled.dataQuery.parameters.size} data parameters" +
                    if (assembled.countQuery != null) " and ${assembled.countQuery.parameters.size} count parameters" else " (count skipped)"
        }

        // Step 4: Execute Queries
        val (entityIds, totalCount) = if (includeCount) {
            coroutineScope {
                val dataDeferred = async(Dispatchers.IO) { executeDataQuery(assembled.dataQuery) }
                val countDeferred = async(Dispatchers.IO) { executeCountQuery(assembled.countQuery!!) }
                Pair(dataDeferred.await(), countDeferred.await())
            }
        } else {
            val ids = withContext(Dispatchers.IO) { executeDataQuery(assembled.dataQuery) }
            Pair(ids, null)
        }

        logger.debug { "Query returned ${entityIds.size} entity IDs with totalCount=$totalCount" }

        // Step 5: Determine hasNextPage and trim results
        val hasNextPage = if (includeCount) {
            (pagination.offset + pagination.limit) < (totalCount ?: 0L)
        } else {
            entityIds.size > pagination.limit
        }
        val trimmedIds = if (!includeCount && entityIds.size > pagination.limit) {
            entityIds.take(pagination.limit)
        } else {
            entityIds
        }

        // Step 6: Load Entities by IDs
        if (trimmedIds.isEmpty()) {
            return EntityQueryResult(
                entities = emptyList(),
                totalCount = totalCount,
                hasNextPage = false,
                projection = projection,
            )
        }

        val entityEntities = withContext(Dispatchers.IO) {
            entityRepository.findByIdIn(trimmedIds)
        }

        // Load attributes and convert to domain models
        val allAttributes = entityAttributeService.getAttributesForEntities(trimmedIds)
        val entities = entityEntities.map {
            val eid = requireNotNull(it.id)
            it.toModel(
                audit = true,
                relationships = emptyMap(),
                attributes = allAttributes[eid] ?: emptyMap(),
            )
        }

        // Step 7: Re-sort to match original SQL order
        val idToIndex = trimmedIds.withIndex().associate { it.value to it.index }
        val sortedEntities = entities.sortedBy { idToIndex[it.id] ?: Int.MAX_VALUE }

        return EntityQueryResult(
            entities = sortedEntities,
            totalCount = totalCount,
            hasNextPage = hasNextPage,
            projection = projection,
        )
    }

    /**
     * Loads relationship definitions for an entity type, including both forward and inverse-visible.
     * Returns a map of definition ID to (definition, direction) pairs.
     * Delegates definition loading to EntityTypeRelationshipService and maps direction locally.
     */
    private fun loadRelationshipDefinitions(
        workspaceId: UUID,
        entityTypeId: UUID,
    ): Map<UUID, Pair<RelationshipDefinition, QueryDirection>> {
        val definitions = entityTypeRelationshipService.getDefinitionsForEntityTypeAsMap(workspaceId, entityTypeId)
        return definitions.mapValues { (_, definition) ->
            val direction = if (definition.sourceEntityTypeId == entityTypeId) {
                QueryDirection.FORWARD
            } else {
                QueryDirection.INVERSE
            }
            definition to direction
        }
    }

    /**
     * Validates all filter references against the entity type schema.
     *
     * Performs two-part validation:
     * - Part A: Attribute ID validation (walks tree checking attributeId in attributeIds)
     * - Part B: Relationship validation (delegates to QueryFilterValidator)
     *
     * Collects all errors and throws [QueryValidationException] if any are found.
     *
     * Note: For Phase 5, attribute validation within nested relationship filters validates
     * against the SAME entity type's attribute set. Full cross-type validation is a known
     * simplification to be addressed in future phases.
     *
     * @param filter The filter tree to validate
     * @param attributeIds Set of valid attribute UUIDs from the entity type schema
     * @param relationshipDefinitions Map of relationship ID to definition
     * @param maxDepth Maximum allowed relationship traversal depth
     * @throws QueryValidationException if any validation errors are found
     */
    private fun validateFilterReferences(
        filter: QueryFilter?,
        attributeIds: Set<UUID>,
        relationshipDefinitions: Map<UUID, RelationshipDefinition>,
        maxDepth: Int,
    ) {
        if (filter == null) return

        val errors = mutableListOf<riven.core.exceptions.query.QueryFilterException>()

        // Part A: Attribute ID validation
        walkFilterForAttributes(filter, attributeIds, errors)

        // Part B: Relationship validation (delegates to existing validator)
        val relationshipErrors = validator.validate(filter, relationshipDefinitions, maxDepth)
        errors.addAll(relationshipErrors)

        // Throw if any errors found
        if (errors.isNotEmpty()) {
            throw QueryValidationException(errors)
        }
    }

    /**
     * Recursively walks the filter tree checking attribute references.
     *
     * Collects all invalid attribute references into the errors list.
     *
     * @param filter Current filter node
     * @param attributeIds Valid attribute UUIDs
     * @param errors Mutable list accumulating validation errors
     */
    private fun walkFilterForAttributes(
        filter: QueryFilter,
        attributeIds: Set<UUID>,
        errors: MutableList<riven.core.exceptions.query.QueryFilterException>,
    ) {
        when (filter) {
            is QueryFilter.Attribute -> {
                if (filter.attributeId !in attributeIds) {
                    errors.add(
                        InvalidAttributeReferenceException(
                            attributeId = filter.attributeId,
                            reason = "not found on entity type. Valid attributes: [${attributeIds.joinToString()}]",
                        )
                    )
                }
            }

            is QueryFilter.And -> {
                for (condition in filter.conditions) {
                    walkFilterForAttributes(condition, attributeIds, errors)
                }
            }

            is QueryFilter.Or -> {
                for (condition in filter.conditions) {
                    walkFilterForAttributes(condition, attributeIds, errors)
                }
            }

            is QueryFilter.Relationship -> {
                // Nested relationship filters reference target entity type attributes,
                // not root entity type attributes. Skip attribute validation here;
                // cross-type validation requires loading target entity type schemas.
            }

            is QueryFilter.IsRelatedTo -> {
                // No attribute references — nothing to validate
            }
        }
    }


    /**
     * Executes the data query and returns a list of entity IDs.
     *
     * Wraps SQL execution in try/catch to convert [DataAccessException] to [QueryExecutionException].
     *
     * @param dataQuery SQL fragment with SELECT e.id query
     * @return List of entity UUIDs in SQL-defined order (created_at DESC, id ASC)
     * @throws QueryExecutionException if SQL execution fails
     */
    private fun executeDataQuery(dataQuery: SqlFragment): List<UUID> {
        return try {
            val paramSource = MapSqlParameterSource(dataQuery.parameters)
            jdbcTemplate.queryForList(dataQuery.sql, paramSource, UUID::class.java)
        } catch (e: DataAccessException) {
            logger.error(e) { "Entity data query execution failed" }
            throw QueryExecutionException("Entity query execution failed: ${e.message}", e)
        }
    }

    /**
     * Executes the count query and returns the total number of matching entities.
     *
     * Wraps SQL execution in try/catch to convert [DataAccessException] to [QueryExecutionException].
     *
     * @param countQuery SQL fragment with SELECT COUNT(*) query
     * @return Total count of matching entities across all pages
     * @throws QueryExecutionException if SQL execution fails
     */
    private fun executeCountQuery(countQuery: SqlFragment): Long {
        return try {
            val paramSource = MapSqlParameterSource(countQuery.parameters)
            jdbcTemplate.queryForObject(countQuery.sql, paramSource, Long::class.java) ?: 0L
        } catch (e: DataAccessException) {
            logger.error(e) { "Entity count query execution failed" }
            throw QueryExecutionException("Entity count query execution failed: ${e.message}", e)
        }
    }
}
