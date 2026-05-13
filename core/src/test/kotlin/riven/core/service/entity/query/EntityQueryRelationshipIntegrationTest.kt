package cranium.core.service.entity.query

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.entity.query.FilterOperator
import cranium.core.models.entity.payload.EntityAttributePrimitivePayload
import cranium.core.service.util.factory.entity.EntityFactory
import cranium.core.exceptions.query.QueryValidationException
import cranium.core.exceptions.query.RelationshipDepthExceededException
import cranium.core.models.entity.query.EntityQuery
import cranium.core.models.entity.query.filter.FilterValue
import cranium.core.models.entity.query.filter.QueryFilter
import cranium.core.models.entity.query.filter.RelationshipFilter
import cranium.core.models.entity.query.filter.TypeBranch
import cranium.core.models.entity.query.pagination.QueryPagination

/**
 * Integration tests for relationship filtering in EntityQueryService.
 *
 * Tests all 5 relationship conditions (EXISTS, NOT_EXISTS, TARGET_EQUALS,
 * TARGET_MATCHES, TARGET_TYPE_MATCHES) plus depth nesting validation.
 */
class EntityQueryRelationshipIntegrationTest : EntityQueryIntegrationTestBase() {

    @Test
    fun `test EXISTS returns companies with employees`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Relationship(
                relationshipId = companyEmployeesRelId,
                condition = RelationshipFilter.Exists
            )
        )

        val result = entityQueryService.execute(query, workspaceId, QueryPagination())

        // All 10 companies have employees based on seed data
        assertEquals(10, result.totalCount)
        assertEquals(10, result.entities.size)
    }


    @Test
    fun `test NOT_EXISTS returns companies without employees`() = runBlocking {
        // This test modifies shared state, so restore it afterward
        try {
            truncateAll()
            createEntityTypes()
            createRelationshipDefinitions()
            seedEntities()

            // Create a company with no employees
            val lonelyCompany = createCompany("Lonely Corp", "Technology", 1000000.0, "true", "2023", null).second
            companyEntities["Lonely Corp"] = lonelyCompany

            // Create original relationships (excludes Lonely Corp)
            createRelationships()

            val query = EntityQuery(
                entityTypeId = companyTypeId,
                filter = QueryFilter.Relationship(
                    relationshipId = companyEmployeesRelId,
                    condition = RelationshipFilter.NotExists
                )
            )

            val result = entityQueryService.execute(query, workspaceId, QueryPagination())

            // Only "Lonely Corp" should appear (no employees)
            assertEquals(1, result.totalCount)
            assertEquals(1, result.entities.size)
            assertEquals(lonelyCompany, result.entities[0].id)
        } finally {
            // Restore shared state for subsequent tests
            truncateAll()
            createEntityTypes()
            createRelationshipDefinitions()
            seedEntities()
            createRelationships()
        }
    }

    @Test
    fun `test TargetEquals returns companies related to specific employees`() = runBlocking {
        // Acme Corp has: Alice Anderson, Bob Brown, Oliver O'Brien
        // Get Alice and Bob IDs
        val aliceId = employeeEntities["Alice Anderson"]!!
        val bobId = employeeEntities["Bob Brown"]!!

        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Relationship(
                relationshipId = companyEmployeesRelId,
                condition = RelationshipFilter.TargetEquals(
                    entityIds = listOf(aliceId.toString(), bobId.toString())
                )
            )
        )

        val result = entityQueryService.execute(query, workspaceId, QueryPagination())

        // Only Acme Corp should appear (has both Alice and Bob)
        assertEquals(1, result.totalCount)
        assertEquals(1, result.entities.size)
        assertEquals(companyEntities["Acme Corp"], result.entities[0].id)
    }

    @Test
    fun `test TargetMatches returns companies with high-salary employees`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Relationship(
                relationshipId = companyEmployeesRelId,
                condition = RelationshipFilter.TargetMatches(
                    filter = QueryFilter.Attribute(
                        attributeId = employeeSalaryAttrId,
                        operator = FilterOperator.GREATER_THAN,
                        value = FilterValue.Literal(100000.0)
                    )
                )
            )
        )

        val result = entityQueryService.execute(query, workspaceId, QueryPagination())

        // Companies with employees earning > 100k:
        // Acme Corp (Alice: 120k)
        // Gamma LLC (Diana: 110k)
        // Epsilon Ltd (Fiona: 105k)
        // Zeta Systems (George: 130k, Samuel: 125k)
        // Theta Group (Jane: 140k, Teresa: 135k)
        // Kappa Industries (Michael: 115k)
        assertEquals(6, result.totalCount)

        val resultCompanyNames = result.entities.map { entity ->
            companyEntities.entries.find { it.value == entity.id }?.key
        }

        assertTrue(resultCompanyNames.contains("Acme Corp"))
        assertTrue(resultCompanyNames.contains("Gamma LLC"))
        assertTrue(resultCompanyNames.contains("Epsilon Ltd"))
        assertTrue(resultCompanyNames.contains("Zeta Systems"))
        assertTrue(resultCompanyNames.contains("Theta Group"))
        assertTrue(resultCompanyNames.contains("Kappa Industries"))
    }

    @Test
    fun `test TargetTypeMatches with OR semantics across type branches`() = runBlocking {
        // Owner relationship is polymorphic (Employee or Project)
        // Acme Corp owner: Alice Anderson (Employee, Engineering)
        // Gamma LLC owner: CRM Integration (Project, Planning)

        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Relationship(
                relationshipId = companyOwnerRelId,
                condition = RelationshipFilter.TargetTypeMatches(
                    branches = listOf(
                        TypeBranch(
                            entityTypeId = employeeTypeId,
                            filter = QueryFilter.Attribute(
                                attributeId = employeeDepartmentAttrId,
                                operator = FilterOperator.EQUALS,
                                value = FilterValue.Literal("Engineering")
                            )
                        ),
                        TypeBranch(
                            entityTypeId = projectTypeId,
                            filter = QueryFilter.Attribute(
                                attributeId = projectStatusAttrId,
                                operator = FilterOperator.EQUALS,
                                value = FilterValue.Literal("Active")
                            )
                        )
                    )
                )
            )
        )

        val result = entityQueryService.execute(query, workspaceId, QueryPagination())

        // Acme Corp: owner is Alice (Engineering employee) - MATCH
        // Gamma LLC: owner is CRM Integration (Planning project) - NO MATCH
        assertEquals(1, result.totalCount)
        assertEquals(companyEntities["Acme Corp"], result.entities[0].id)
    }

    @Test
    fun `test TargetTypeMatches with no filter on a branch`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Relationship(
                relationshipId = companyOwnerRelId,
                condition = RelationshipFilter.TargetTypeMatches(
                    branches = listOf(
                        TypeBranch(
                            entityTypeId = employeeTypeId,
                            filter = null // Any employee
                        )
                    )
                )
            )
        )

        val result = entityQueryService.execute(query, workspaceId, QueryPagination())

        // Acme Corp has Alice Anderson as owner (Employee)
        assertEquals(1, result.totalCount)
        assertEquals(companyEntities["Acme Corp"], result.entities[0].id)
    }

    @Test
    fun `test 1-deep relationship nesting succeeds`() = runBlocking {
        // Same as EXISTS test - single level relationship
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Relationship(
                relationshipId = companyEmployeesRelId,
                condition = RelationshipFilter.Exists
            )
        )

        val result = entityQueryService.execute(query, workspaceId, QueryPagination())

        assertEquals(10, result.totalCount)
    }

    @Test
    fun `test 2-deep relationship nesting succeeds`() = runBlocking {
        // Company -> Employee -> Project (2 levels)
        // Find companies that have employees who work on "Active" projects
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Relationship(
                relationshipId = companyEmployeesRelId,
                condition = RelationshipFilter.TargetMatches(
                    filter = QueryFilter.Relationship(
                        relationshipId = employeeProjectsRelId,
                        condition = RelationshipFilter.TargetMatches(
                            filter = QueryFilter.Attribute(
                                attributeId = projectStatusAttrId,
                                operator = FilterOperator.EQUALS,
                                value = FilterValue.Literal("Active")
                            )
                        )
                    )
                )
            )
        )

        val result = entityQueryService.execute(query, workspaceId, QueryPagination())

        // Companies with employees working on active projects:
        // - Acme Corp: Alice -> Platform Redesign (Active), Mobile App Launch (Active)
        // - Zeta Systems: George -> API Gateway (Active)
        // - Kappa Industries: Michael -> Payment System (Active)
        assertEquals(3, result.totalCount)

        val resultCompanyNames = result.entities.map { entity ->
            companyEntities.entries.find { it.value == entity.id }?.key
        }

        assertTrue(resultCompanyNames.contains("Acme Corp"))
        assertTrue(resultCompanyNames.contains("Zeta Systems"))
        assertTrue(resultCompanyNames.contains("Kappa Industries"))
    }

    @Test
    fun `test 3-deep relationship nesting succeeds`() = runBlocking {
        // Company -> Employee -> Project -> Company (3 levels)
        // Find companies that have employees who work on projects for client companies in "Finance"
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Relationship(
                relationshipId = companyEmployeesRelId,
                condition = RelationshipFilter.TargetMatches(
                    filter = QueryFilter.Relationship(
                        relationshipId = employeeProjectsRelId,
                        condition = RelationshipFilter.TargetMatches(
                            filter = QueryFilter.Relationship(
                                relationshipId = projectClientRelId,
                                condition = RelationshipFilter.TargetMatches(
                                    filter = QueryFilter.Attribute(
                                        attributeId = companyIndustryAttrId,
                                        operator = FilterOperator.EQUALS,
                                        value = FilterValue.Literal("Finance")
                                    )
                                )
                            )
                        )
                    )
                )
            ),
            maxDepth = 3
        )

        val result = entityQueryService.execute(query, workspaceId, QueryPagination())

        // Company -> Employee -> Project -> Client Company (Finance)
        // - Acme Corp: Alice -> Platform Redesign -> Gamma LLC (Finance) ✓
        // - Beta Inc: Charlie -> Data Migration -> Beta Inc (Technology, not Finance)
        // - Gamma LLC: Diana -> CRM Integration -> Theta Group (Finance) ✓
        // - Zeta Systems: George -> API Gateway -> Acme Corp (Technology, not Finance)
        // - Kappa Industries: Michael -> Payment System -> Epsilon Ltd (Healthcare, not Finance)

        // Companies with employees working on projects for Finance clients
        assertEquals(2, result.totalCount)

        val resultCompanyNames = result.entities.map { entity ->
            companyEntities.entries.find { it.value == entity.id }?.key
        }
        assertTrue(resultCompanyNames.contains("Acme Corp"))
        assertTrue(resultCompanyNames.contains("Gamma LLC"))
    }

    @Test
    fun `test 3-deep nesting with maxDepth 2 is rejected`() {
        // Company -> Employee -> Project -> Company (3 levels, but maxDepth=2)
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.Relationship(
                relationshipId = companyEmployeesRelId,
                condition = RelationshipFilter.TargetMatches(
                    filter = QueryFilter.Relationship(
                        relationshipId = employeeProjectsRelId,
                        condition = RelationshipFilter.TargetMatches(
                            filter = QueryFilter.Relationship(
                                relationshipId = projectClientRelId,
                                condition = RelationshipFilter.Exists
                            )
                        )
                    )
                )
            ),
            maxDepth = 2 // Only allow 2 levels, but we have 3
        )

        val exception = assertThrows<QueryValidationException> {
            runBlocking {
                entityQueryService.execute(query, workspaceId, QueryPagination())
            }
        }

        // Should contain RelationshipDepthExceededException
        assertTrue(exception.validationErrors.any { it is RelationshipDepthExceededException })
        val depthError = exception.validationErrors.filterIsInstance<RelationshipDepthExceededException>().first()
        assertEquals(3, depthError.depth)
        assertEquals(2, depthError.maxDepth)
    }

    // ------ Inverse relationship query tests ------

    @Test
    fun `test INVERSE EXISTS - returns employees who have a company`() = runBlocking {
        // Company -> Employees targets Employee type (inverse from Employee perspective)
        // Query Employee type, filter by companyEmployeesRelId which is inverse for employees
        val query = EntityQuery(
            entityTypeId = employeeTypeId,
            filter = QueryFilter.Relationship(
                relationshipId = companyEmployeesRelId,
                condition = RelationshipFilter.Exists
            )
        )

        val result = entityQueryService.execute(query, workspaceId, QueryPagination())

        // All 20 employees have a company (every employee is linked to a company)
        assertEquals(20, result.totalCount)
    }

    @Test
    fun `test INVERSE TargetMatches - returns employees of technology companies`() = runBlocking {
        // Query Employee type, filter by inverse Company->Employees relationship
        // to find employees whose company is in Technology
        val query = EntityQuery(
            entityTypeId = employeeTypeId,
            filter = QueryFilter.Relationship(
                relationshipId = companyEmployeesRelId,
                condition = RelationshipFilter.TargetMatches(
                    filter = QueryFilter.Attribute(
                        attributeId = companyIndustryAttrId,
                        operator = FilterOperator.EQUALS,
                        value = FilterValue.Literal("Technology")
                    )
                )
            )
        )

        val result = entityQueryService.execute(query, workspaceId, QueryPagination())

        // Technology companies: Acme Corp (3 employees), Beta Inc (2), Zeta Systems (3) = 8 employees
        assertEquals(8, result.totalCount)

        val resultEmployeeNames = result.entities.map { entity ->
            employeeEntities.entries.find { it.value == entity.id }?.key
        }

        // Acme Corp employees
        assertTrue(resultEmployeeNames.contains("Alice Anderson"))
        assertTrue(resultEmployeeNames.contains("Bob Brown"))
        assertTrue(resultEmployeeNames.contains("Oliver O'Brien"))
        // Beta Inc employees
        assertTrue(resultEmployeeNames.contains("Charlie Chen"))
        assertTrue(resultEmployeeNames.contains("Patricia Parker"))
        // Zeta Systems employees
        assertTrue(resultEmployeeNames.contains("George Garcia"))
        assertTrue(resultEmployeeNames.contains("Hannah Harris"))
        assertTrue(resultEmployeeNames.contains("Samuel Smith"))
    }

    @Test
    fun `test INVERSE NOT_EXISTS - returns employees without companies`() = runBlocking {
        // All employees are linked to companies in seed data, so this should return 0
        // unless we add an unlinked employee
        try {
            truncateAll()
            createEntityTypes()
            createRelationshipDefinitions()
            seedEntities()
            createRelationships()

            // Add an employee with no company link
            val freelancerId = entityRepository.save(
                EntityFactory.createEntityEntity(
                    workspaceId = workspaceId,
                    typeId = employeeTypeId,
                    typeKey = "employee",
                    identifierKey = employeeFirstNameAttrId,
                )
            ).id!!

            val query = EntityQuery(
                entityTypeId = employeeTypeId,
                filter = QueryFilter.Relationship(
                    relationshipId = companyEmployeesRelId,
                    condition = RelationshipFilter.NotExists
                )
            )

            val result = entityQueryService.execute(query, workspaceId, QueryPagination())

            assertEquals(1, result.totalCount)
            assertEquals(freelancerId, result.entities[0].id)
        } finally {
            truncateAll()
            createEntityTypes()
            createRelationshipDefinitions()
            seedEntities()
            createRelationships()
        }
    }

    // ------ Combined forward + inverse ------

    @Test
    fun `test AND of attribute filter and relationship filter`() = runBlocking {
        val query = EntityQuery(
            entityTypeId = companyTypeId,
            filter = QueryFilter.And(
                conditions = listOf(
                    QueryFilter.Attribute(
                        attributeId = companyIndustryAttrId,
                        operator = FilterOperator.EQUALS,
                        value = FilterValue.Literal("Technology")
                    ),
                    QueryFilter.Relationship(
                        relationshipId = companyEmployeesRelId,
                        condition = RelationshipFilter.Exists
                    )
                )
            )
        )

        val result = entityQueryService.execute(query, workspaceId, QueryPagination())

        // Technology companies with employees:
        // - Acme Corp (Technology, has employees)
        // - Beta Inc (Technology, has employees)
        // - Zeta Systems (Technology, has employees)
        assertEquals(3, result.totalCount)

        val resultCompanyNames = result.entities.map { entity ->
            companyEntities.entries.find { it.value == entity.id }?.key
        }

        assertTrue(resultCompanyNames.contains("Acme Corp"))
        assertTrue(resultCompanyNames.contains("Beta Inc"))
        assertTrue(resultCompanyNames.contains("Zeta Systems"))
    }

}
