package cranium.core.service.entity.query

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import cranium.core.enums.entity.query.FilterOperator
import cranium.core.models.entity.payload.EntityAttributePrimitivePayload
import cranium.core.models.entity.query.EntityQuery
import cranium.core.models.entity.query.filter.FilterValue
import cranium.core.models.entity.query.filter.QueryFilter

/**
 * Integration tests for attribute filtering and logical composition.
 *
 * Tests all 14 FilterOperators plus AND/OR logical combinations against real PostgreSQL JSONB.
 */
class EntityQueryAttributeFilterIntegrationTest : EntityQueryIntegrationTestBase() {

    // ==================== FilterOperator Tests ====================

    @Test
    fun `EQUALS operator filters exact string match`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyIndustryAttrId,
                operator = FilterOperator.EQUALS,
                value = FilterValue.Literal("Technology")
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        assertEquals(3, result.entities.size, "Should return 3 Technology companies")
        assertEquals(3L, result.totalCount)

        // Verify all returned entities have industry = "Technology"
        result.entities.forEach { entity ->
            val industryAttr = entity.payload[companyIndustryAttrId]
            assertNotNull(industryAttr)
            val payload = industryAttr!!.payload as EntityAttributePrimitivePayload
            assertEquals("Technology", payload.value)
        }
    }

    @Test
    fun `NOT_EQUALS operator filters non-matching values`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyIndustryAttrId,
                operator = FilterOperator.NOT_EQUALS,
                value = FilterValue.Literal("Technology")
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        assertEquals(7, result.entities.size, "Should return 7 non-Technology companies")
        assertEquals(7L, result.totalCount)
    }

    @Test
    fun `CONTAINS operator filters string contains case-insensitive`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyNameAttrId,
                operator = FilterOperator.CONTAINS,
                value = FilterValue.Literal("Corp")
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        assertEquals(2, result.entities.size, "Should return 2 companies with 'Corp' in name")
        assertEquals(2L, result.totalCount)

        // Verify both contain "Corp"
        val names = result.entities.map {
            (it.payload[companyNameAttrId]!!.payload as EntityAttributePrimitivePayload).value as String
        }
        assertTrue(names.contains("Acme Corp"))
        assertTrue(names.contains("Delta Corp"))
    }

    @Test
    fun `NOT_CONTAINS operator filters string not contains`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyNameAttrId,
                operator = FilterOperator.NOT_CONTAINS,
                value = FilterValue.Literal("Corp")
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        assertEquals(8, result.entities.size, "Should return 8 companies without 'Corp' in name")
        assertEquals(8L, result.totalCount)
    }

    @Test
    fun `STARTS_WITH operator filters string prefix`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyNameAttrId,
                operator = FilterOperator.STARTS_WITH,
                value = FilterValue.Literal("E")
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        assertEquals(2, result.entities.size, "Should return 2 companies starting with 'E'")
        assertEquals(2L, result.totalCount)

        val names = result.entities.map {
            (it.payload[companyNameAttrId]!!.payload as EntityAttributePrimitivePayload).value as String
        }
        assertTrue(names.contains("Epsilon Ltd"))
        assertTrue(names.contains("Eta Solutions"))
    }

    @Test
    fun `ENDS_WITH operator filters string suffix`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyNameAttrId,
                operator = FilterOperator.ENDS_WITH,
                value = FilterValue.Literal("Corp")
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        assertEquals(2, result.entities.size, "Should return 2 companies ending with 'Corp'")
        assertEquals(2L, result.totalCount)
    }

    @Test
    fun `IN operator filters values in list`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyIndustryAttrId,
                operator = FilterOperator.IN,
                value = FilterValue.Literal(listOf("Technology", "Finance"))
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        assertEquals(6, result.entities.size, "Should return 6 companies in Technology or Finance")
        assertEquals(6L, result.totalCount)
    }

    @Test
    fun `NOT_IN operator filters values not in list`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyIndustryAttrId,
                operator = FilterOperator.NOT_IN,
                value = FilterValue.Literal(listOf("Technology", "Finance"))
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        assertEquals(4, result.entities.size, "Should return 4 companies not in Technology or Finance")
        assertEquals(4L, result.totalCount)
    }

    @Test
    fun `GREATER_THAN operator filters numeric values`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyRevenueAttrId,
                operator = FilterOperator.GREATER_THAN,
                value = FilterValue.Literal(5000000.0)
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        assertEquals(4, result.entities.size, "Should return 4 companies with revenue > 5M")
        assertEquals(4L, result.totalCount)

        // Verify all have revenue > 5M
        result.entities.forEach { entity ->
            val revenue =
                ((entity.payload[companyRevenueAttrId]!!.payload as EntityAttributePrimitivePayload).value as Number).toDouble()
            assertTrue(revenue > 5000000.0, "Revenue $revenue should be > 5000000")
        }
    }

    @Test
    fun `GREATER_THAN_OR_EQUALS operator filters numeric values`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyRevenueAttrId,
                operator = FilterOperator.GREATER_THAN_OR_EQUALS,
                value = FilterValue.Literal(5000000.0)
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        assertEquals(5, result.entities.size, "Should return 5 companies with revenue >= 5M")
        assertEquals(5L, result.totalCount)
    }

    @Test
    fun `LESS_THAN operator filters numeric values`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyRevenueAttrId,
                operator = FilterOperator.LESS_THAN,
                value = FilterValue.Literal(1000000.0)
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        assertEquals(2, result.entities.size, "Should return 2 companies with revenue < 1M")
        assertEquals(2L, result.totalCount)

        // Verify both are Delta (500K) and Iota (750K)
        val revenues = result.entities.map {
            ((it.payload[companyRevenueAttrId]!!.payload as EntityAttributePrimitivePayload).value as Number).toDouble()
        }.sorted()
        assertEquals(listOf(500000.0, 750000.0), revenues)
    }

    @Test
    fun `LESS_THAN_OR_EQUALS operator filters numeric values`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyRevenueAttrId,
                operator = FilterOperator.LESS_THAN_OR_EQUALS,
                value = FilterValue.Literal(1000000.0)
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        assertEquals(3, result.entities.size, "Should return 3 companies with revenue <= 1M")
        assertEquals(3L, result.totalCount)

        // Delta (500K), Eta (1M), Iota (750K)
        val revenues = result.entities.map {
            ((it.payload[companyRevenueAttrId]!!.payload as EntityAttributePrimitivePayload).value as Number).toDouble()
        }.sorted()
        assertEquals(listOf(500000.0, 750000.0, 1000000.0), revenues)
    }

    @Test
    fun `IS_NULL operator filters missing JSONB keys`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyWebsiteAttrId,
                operator = FilterOperator.IS_NULL,
                value = FilterValue.Literal(null)
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        assertEquals(4, result.entities.size, "Should return 4 companies with null website")
        assertEquals(4L, result.totalCount)

        // Verify website attribute is either missing or null
        result.entities.forEach { entity ->
            val websiteAttr = entity.payload[companyWebsiteAttrId]
            assertTrue(websiteAttr == null || (websiteAttr.payload as EntityAttributePrimitivePayload).value == null)
        }
    }

    @Test
    fun `IS_NOT_NULL operator filters existing JSONB keys`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyWebsiteAttrId,
                operator = FilterOperator.IS_NOT_NULL,
                value = FilterValue.Literal(null)
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        assertEquals(6, result.entities.size, "Should return 6 companies with non-null website")
        assertEquals(6L, result.totalCount)

        // Verify all have website
        result.entities.forEach { entity ->
            val websiteAttr = entity.payload[companyWebsiteAttrId]
            assertNotNull(websiteAttr)
            assertNotNull((websiteAttr!!.payload as EntityAttributePrimitivePayload).value)
        }
    }

    // ==================== Logical Composition Tests ====================

    @Test
    fun `AND composition combines multiple conditions`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.And(
                conditions = listOf(
                    QueryFilter.Attribute(
                        attributeId = companyIndustryAttrId,
                        operator = FilterOperator.EQUALS,
                        value = FilterValue.Literal("Technology")
                    ),
                    QueryFilter.Attribute(
                        attributeId = companyActiveAttrId,
                        operator = FilterOperator.EQUALS,
                        value = FilterValue.Literal("true")
                    )
                )
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        assertEquals(3, result.entities.size, "Should return 3 active Technology companies")
        assertEquals(3L, result.totalCount)

        // Verify all are Technology and active
        result.entities.forEach { entity ->
            val industry = (entity.payload[companyIndustryAttrId]!!.payload as EntityAttributePrimitivePayload).value
            val active = (entity.payload[companyActiveAttrId]!!.payload as EntityAttributePrimitivePayload).value
            assertEquals("Technology", industry)
            assertEquals("true", active)
        }
    }

    @Test
    fun `OR composition allows any condition to match`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Or(
                conditions = listOf(
                    QueryFilter.Attribute(
                        attributeId = companyIndustryAttrId,
                        operator = FilterOperator.EQUALS,
                        value = FilterValue.Literal("Technology")
                    ),
                    QueryFilter.Attribute(
                        attributeId = companyIndustryAttrId,
                        operator = FilterOperator.EQUALS,
                        value = FilterValue.Literal("Finance")
                    )
                )
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        assertEquals(6, result.entities.size, "Should return 6 companies in Technology or Finance")
        assertEquals(6L, result.totalCount)
    }

    @Test
    fun `nested AND with OR produces correct SQL parenthesization`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.And(
                conditions = listOf(
                    QueryFilter.Or(
                        conditions = listOf(
                            QueryFilter.Attribute(
                                attributeId = companyIndustryAttrId,
                                operator = FilterOperator.EQUALS,
                                value = FilterValue.Literal("Technology")
                            ),
                            QueryFilter.Attribute(
                                attributeId = companyIndustryAttrId,
                                operator = FilterOperator.EQUALS,
                                value = FilterValue.Literal("Finance")
                            )
                        )
                    ),
                    QueryFilter.Attribute(
                        attributeId = companyActiveAttrId,
                        operator = FilterOperator.EQUALS,
                        value = FilterValue.Literal("true")
                    )
                )
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        // Acme (tech, active), Beta (tech, active), Gamma (finance, active), Theta (finance, active), Zeta (tech, active)
        // Delta is finance but inactive
        assertEquals(5, result.entities.size, "Should return 5 companies matching (Tech OR Finance) AND active")
        assertEquals(5L, result.totalCount)
    }

    @Test
    fun `deeply nested OR with AND branches`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Or(
                conditions = listOf(
                    QueryFilter.And(
                        conditions = listOf(
                            QueryFilter.Attribute(
                                attributeId = companyIndustryAttrId,
                                operator = FilterOperator.EQUALS,
                                value = FilterValue.Literal("Technology")
                            ),
                            QueryFilter.Attribute(
                                attributeId = companyRevenueAttrId,
                                operator = FilterOperator.GREATER_THAN,
                                value = FilterValue.Literal(3000000.0)
                            )
                        )
                    ),
                    QueryFilter.And(
                        conditions = listOf(
                            QueryFilter.Attribute(
                                attributeId = companyIndustryAttrId,
                                operator = FilterOperator.EQUALS,
                                value = FilterValue.Literal("Finance")
                            ),
                            QueryFilter.Attribute(
                                attributeId = companyRevenueAttrId,
                                operator = FilterOperator.GREATER_THAN,
                                value = FilterValue.Literal(5000000.0)
                            )
                        )
                    )
                )
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        // (Tech AND revenue > 3M): Acme (5M), Zeta (8M)
        // (Finance AND revenue > 5M): Gamma (10M), Theta (15M)
        assertEquals(4, result.entities.size, "Should return 4 companies matching complex OR(AND, AND)")
        assertEquals(4L, result.totalCount)
    }

    @Test
    fun `query with no filter returns all entities of type`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = null
        )

        val result = entityQueryService.execute(query, workspaceId)

        assertEquals(10, result.entities.size, "Should return all 10 companies")
        assertEquals(10L, result.totalCount)
    }
}
