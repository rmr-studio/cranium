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
 * Integration tests reproducing the CONTAINS search bug where:
 * - "Kenneth" matches "Kenneth Hernandez" ✓
 * - "Kenneth " (trailing space) matches nothing ✗
 * - "Kenneth H" doesn't match "Kenneth Hernandez" ✗
 *
 * Uses the existing company seed data (e.g. "Acme Corp", "Delta Corp") to test
 * CONTAINS with trailing spaces, multi-word partial matches, and OR composition
 * across multiple attributes — mirroring the frontend's buildSearchFilter pattern.
 */
class EntityQuerySearchIntegrationTest : EntityQueryIntegrationTestBase() {

    // ------ Baseline CONTAINS ------

    @Test
    fun `CONTAINS single word matches substring`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyNameAttrId,
                operator = FilterOperator.CONTAINS,
                value = FilterValue.Literal("Acme")
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        assertEquals(1, result.entities.size, "Should match 'Acme Corp' via substring 'Acme'")
        val name = (result.entities[0].payload[companyNameAttrId]!!.payload as EntityAttributePrimitivePayload).value
        assertEquals("Acme Corp", name)
    }

    // ------ Trailing space ------

    @Test
    fun `CONTAINS with trailing space matches substring that has trailing content`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyNameAttrId,
                operator = FilterOperator.CONTAINS,
                value = FilterValue.Literal("Acme ")
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        // "Acme Corp" contains the substring "Acme " (with trailing space before "Corp")
        assertEquals(1, result.entities.size, "Should match 'Acme Corp' — the value contains 'Acme ' as a substring")
        val name = (result.entities[0].payload[companyNameAttrId]!!.payload as EntityAttributePrimitivePayload).value
        assertEquals("Acme Corp", name)
    }

    // ------ Multi-word partial match ------

    @Test
    fun `CONTAINS with multi-word partial match crosses word boundary`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyNameAttrId,
                operator = FilterOperator.CONTAINS,
                value = FilterValue.Literal("Acme C")
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        // "Acme Corp" contains "Acme C" as a substring
        assertEquals(1, result.entities.size, "Should match 'Acme Corp' — the value contains 'Acme C' as a substring")
        val name = (result.entities[0].payload[companyNameAttrId]!!.payload as EntityAttributePrimitivePayload).value
        assertEquals("Acme Corp", name)
    }

    // ------ Leading space (negative test) ------

    @Test
    fun `CONTAINS with leading space does not match when value has no leading space`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyNameAttrId,
                operator = FilterOperator.CONTAINS,
                value = FilterValue.Literal(" Acme")
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        // "Acme Corp" does NOT contain " Acme" (with leading space) — correct behavior
        assertEquals(0, result.entities.size, "Should NOT match — 'Acme Corp' does not contain ' Acme' substring")
    }

    // ------ OR composition across attributes (mirrors frontend buildSearchFilter) ------

    @Test
    fun `OR CONTAINS across multiple attributes matches via any attribute`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Or(
                conditions = listOf(
                    QueryFilter.Attribute(
                        attributeId = companyNameAttrId,
                        operator = FilterOperator.CONTAINS,
                        value = FilterValue.Literal("Acme C")
                    ),
                    QueryFilter.Attribute(
                        attributeId = companyIndustryAttrId,
                        operator = FilterOperator.CONTAINS,
                        value = FilterValue.Literal("Acme C")
                    )
                )
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        // Should match "Acme Corp" via name attribute, even though industry doesn't contain "Acme C"
        assertEquals(1, result.entities.size, "Should match 'Acme Corp' via OR across name and industry")
        val name = (result.entities[0].payload[companyNameAttrId]!!.payload as EntityAttributePrimitivePayload).value
        assertEquals("Acme Corp", name)
    }

    // ------ Case insensitive ------

    @Test
    fun `CONTAINS is case-insensitive via ILIKE`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Attribute(
                attributeId = companyNameAttrId,
                operator = FilterOperator.CONTAINS,
                value = FilterValue.Literal("acme c")
            )
        )

        val result = entityQueryService.execute(query, workspaceId)

        // ILIKE is case-insensitive, so "acme c" should match "Acme Corp"
        assertEquals(1, result.entities.size, "Should match 'Acme Corp' case-insensitively")
        val name = (result.entities[0].payload[companyNameAttrId]!!.payload as EntityAttributePrimitivePayload).value
        assertEquals("Acme Corp", name)
    }
}
