package riven.core.service.entity.query

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.EntityPropertyType
import riven.core.enums.entity.query.FilterOperator
import riven.core.models.common.validation.Schema
import riven.core.models.entity.configuration.EntityTypeAttributeColumn
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.models.entity.query.EntityQuery
import riven.core.models.entity.query.filter.FilterValue
import riven.core.models.entity.query.filter.QueryFilter
import riven.core.models.entity.query.pagination.QueryPagination
import java.util.*

/**
 * Integration tests for pagination, ordering, and workspace isolation
 * in EntityQueryService.
 */
class EntityQueryPaginationIntegrationTest : EntityQueryIntegrationTestBase() {

    // Other workspace entity type ID
    private lateinit var otherWorkspaceTypeId: UUID

    @BeforeAll
    fun setupOtherWorkspace() {
        // Create entity type in other workspace
        val otherCompanyType = EntityTypeEntity(
            key = "company",
            workspaceId = otherWorkspaceId,
            displayNameSingular = "Company",
            displayNamePlural = "Companies",
            identifierKey = companyNameAttrId,
            schema = Schema(
                label = "Company",
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                required = true,
                properties = mapOf(
                    companyNameAttrId to Schema(
                        label = "Name",
                        key = SchemaType.TEXT,
                        type = DataType.STRING,
                        required = true
                    )
                )
            ),
            columns = listOf(
                EntityTypeAttributeColumn(companyNameAttrId, EntityPropertyType.ATTRIBUTE)
            )
        )
        val saved = entityTypeRepository.save(otherCompanyType)
        otherWorkspaceTypeId = saved.id!!

        // Create 3 entities in other workspace
        repeat(3) { i ->
            entityRepository.save(
                EntityEntity(
                    workspaceId = otherWorkspaceId,
                    typeId = otherWorkspaceTypeId,
                    typeKey = "company",
                    identifierKey = companyNameAttrId,
                )
            )
        }
    }

    @Test
    fun `test default pagination returns first page`() = runBlocking {
        val query = EntityQuery(entityTypeId = companyTypeId)
        val result = entityQueryService.execute(query, workspaceId, QueryPagination())

        assertEquals(10, result.totalCount)
        assertEquals(10, result.entities.size)
        assertFalse(result.hasNextPage)
    }

    @Test
    fun `test limit caps result count`() = runBlocking {
        val query = EntityQuery(entityTypeId = companyTypeId)
        val result = entityQueryService.execute(
            query,
            workspaceId,
            QueryPagination(limit = 3, offset = 0)
        )

        assertEquals(3, result.entities.size)
        assertEquals(10, result.totalCount)
        assertTrue(result.hasNextPage)
    }

    @Test
    fun `test offset skips results`() = runBlocking {
        val query = EntityQuery(entityTypeId = companyTypeId)

        val firstPage = entityQueryService.execute(
            query,
            workspaceId,
            QueryPagination(limit = 3, offset = 0)
        )
        val secondPage = entityQueryService.execute(
            query,
            workspaceId,
            QueryPagination(limit = 3, offset = 3)
        )

        assertEquals(3, firstPage.entities.size)
        assertEquals(3, secondPage.entities.size)
        assertEquals(10, firstPage.totalCount)
        assertEquals(10, secondPage.totalCount)

        // Ensure different entities
        val firstPageIds = firstPage.entities.map { it.id }.toSet()
        val secondPageIds = secondPage.entities.map { it.id }.toSet()
        assertEquals(0, firstPageIds.intersect(secondPageIds).size)
    }

    @Test
    fun `test offset beyond total returns empty`() = runBlocking {
        val query = EntityQuery(entityTypeId = companyTypeId)
        val result = entityQueryService.execute(
            query,
            workspaceId,
            QueryPagination(limit = 10, offset = 20)
        )

        assertEquals(0, result.entities.size)
        assertEquals(10, result.totalCount)
        assertFalse(result.hasNextPage)
    }

    @Test
    fun `test hasNextPage is true when more results exist`() = runBlocking {
        val query = EntityQuery(entityTypeId = companyTypeId)
        val result = entityQueryService.execute(
            query,
            workspaceId,
            QueryPagination(limit = 5, offset = 0)
        )

        assertEquals(5, result.entities.size)
        assertEquals(10, result.totalCount)
        assertTrue(result.hasNextPage)
    }

    @Test
    fun `test hasNextPage is false on last page`() = runBlocking {
        val query = EntityQuery(entityTypeId = companyTypeId)
        val result = entityQueryService.execute(
            query,
            workspaceId,
            QueryPagination(limit = 5, offset = 5)
        )

        assertEquals(5, result.entities.size)
        assertEquals(10, result.totalCount)
        assertFalse(result.hasNextPage)
    }

    @Test
    fun `test pagination with filter reduces total count`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyIndustryAttrId,
                operator = FilterOperator.EQUALS,
                value = FilterValue.Literal("Technology")
            )
        )

        val result = entityQueryService.execute(
            query,
            workspaceId,
            QueryPagination(limit = 2, offset = 0)
        )

        // 3 Technology companies: Acme Corp, Beta Inc, Zeta Systems
        assertEquals(2, result.entities.size)
        assertEquals(3, result.totalCount)
        assertTrue(result.hasNextPage)
    }

    @Test
    fun `test default ordering is created_at DESC, id ASC`() = runBlocking {
        val query = EntityQuery(entityTypeId = companyTypeId)
        val result = entityQueryService.execute(query, workspaceId, QueryPagination())

        assertEquals(10, result.entities.size)
        assertEquals(10, result.entities.map { it.id }.toSet().size)

        // Verify created_at DESC with id ASC tie-breaker
        for (i in 0 until result.entities.size - 1) {
            val current = result.entities[i]
            val next = result.entities[i + 1]
            val currentCreatedAt = requireNotNull(current.createdAt)
            val nextCreatedAt = requireNotNull(next.createdAt)

            if (currentCreatedAt == nextCreatedAt) {
                // Tie-breaker: id ASC
                assertTrue(
                    current.id < next.id,
                    "When createdAt is equal, ids should be ascending: ${current.id} should be < ${next.id}"
                )
            } else {
                // Primary sort: created_at DESC
                assertTrue(
                    currentCreatedAt.isAfter(nextCreatedAt),
                    "createdAt should be descending: $currentCreatedAt should be after $nextCreatedAt"
                )
            }
        }

        // Verify stable ordering across executions
        val result2 = entityQueryService.execute(query, workspaceId, QueryPagination())
        assertEquals(result.entities.map { it.id }, result2.entities.map { it.id })
    }

    @Test
    fun `test workspace isolation prevents cross-tenant access`() = runBlocking {
        // Query primary workspace
        val primaryQuery = EntityQuery(entityTypeId = companyTypeId)
        val primaryResult = entityQueryService.execute(
            primaryQuery,
            workspaceId,
            QueryPagination()
        )

        // Should only see the 10 companies from primary workspace
        assertEquals(10, primaryResult.totalCount)
        assertEquals(10, primaryResult.entities.size)

        // Query other workspace
        val otherQuery = EntityQuery(entityTypeId = otherWorkspaceTypeId)
        val otherResult = entityQueryService.execute(
            otherQuery,
            otherWorkspaceId,
            QueryPagination()
        )

        // Should only see the 3 entities from other workspace
        assertEquals(3, otherResult.totalCount)
        assertEquals(3, otherResult.entities.size)

        // Verify no overlap
        val primaryIds = primaryResult.entities.map { it.id }.toSet()
        val otherIds = otherResult.entities.map { it.id }.toSet()
        assertEquals(0, primaryIds.intersect(otherIds).size)
    }
}
