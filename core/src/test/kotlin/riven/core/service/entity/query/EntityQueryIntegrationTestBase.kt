package riven.core.service.entity.query

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.boot.autoconfigure.domain.EntityScan
import riven.core.configuration.properties.QueryConfigurationProperties
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityRelationshipEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.entity.entity.RelationshipDefinitionEntity
import riven.core.entity.entity.RelationshipTargetRuleEntity
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.EntityPropertyType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.models.common.validation.Schema
import riven.core.models.entity.configuration.EntityTypeAttributeColumn
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.entity.RelationshipTargetRuleRepository
import riven.core.service.entity.type.EntityTypeRelationshipService
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.*
import javax.sql.DataSource

/**
 * Minimal Spring configuration for entity query integration tests.
 *
 * Loads only JPA, JDBC, and Jackson auto-configuration — excludes security, Temporal,
 * Supabase, web layer, and all other unrelated beans. Manually wires the query service
 * dependency chain since those classes are not Spring-managed components.
 */
@Configuration
@EnableAutoConfiguration(
    exclude = [
        SecurityAutoConfiguration::class,
        UserDetailsServiceAutoConfiguration::class,
        OAuth2ResourceServerAutoConfiguration::class,
    ],
    excludeName = [
        "io.temporal.spring.boot.autoconfigure.ServiceStubsAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.RootNamespaceAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.NonRootNamespaceAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.MetricsScopeAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.OpenTracingAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.TestServerAutoConfiguration",
    ],
)
@EnableJpaRepositories(basePackages = ["riven.core.repository.entity"])
@EntityScan("riven.core.entity")
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "dateTimeProvider")
class EntityQueryIntegrationTestConfig {

    @Bean
    fun auditorProvider(): AuditorAware<UUID> = AuditorAware { Optional.empty() }

    @Bean
    fun dateTimeProvider(): DateTimeProvider =
        DateTimeProvider { Optional.of<TemporalAccessor>(ZonedDateTime.now()) }

    @Bean
    fun attributeSqlGenerator() = AttributeSqlGenerator()

    @Bean
    fun relationshipSqlGenerator() = RelationshipSqlGenerator()

    @Bean
    fun attributeFilterVisitor(
        attrGen: AttributeSqlGenerator,
        relGen: RelationshipSqlGenerator,
    ) = AttributeFilterVisitor(attrGen, relGen)

    @Bean
    fun queryFilterValidator() = QueryFilterValidator()

    @Bean
    fun entityQueryAssembler(visitor: AttributeFilterVisitor) = EntityQueryAssembler(visitor)

    @Bean
    fun entityTypeRelationshipService(
        definitionRepository: RelationshipDefinitionRepository,
        targetRuleRepository: RelationshipTargetRuleRepository,
        exclusionRepository: riven.core.repository.entity.RelationshipDefinitionExclusionRepository,
        entityRelationshipRepository: EntityRelationshipRepository,
        entityTypeRepository: EntityTypeRepository,
    ) = EntityTypeRelationshipService(
        definitionRepository, targetRuleRepository, exclusionRepository, entityRelationshipRepository,
        entityTypeRepository,
        org.mockito.Mockito.mock(riven.core.service.activity.ActivityService::class.java),
        org.mockito.Mockito.mock(riven.core.service.auth.AuthTokenService::class.java),
        org.mockito.Mockito.mock(riven.core.service.entity.EntityTypeSemanticMetadataService::class.java),
        org.mockito.Mockito.mock(io.github.oshai.kotlinlogging.KLogger::class.java),
    )

    @Bean
    fun entityAttributeService(
        entityAttributeRepository: riven.core.repository.entity.EntityAttributeRepository,
    ) = riven.core.service.entity.EntityAttributeService(
        entityAttributeRepository,
        org.mockito.Mockito.mock(io.github.oshai.kotlinlogging.KLogger::class.java),
    )

    @Bean
    fun entityQueryService(
        entityTypeRepository: EntityTypeRepository,
        entityRepository: EntityRepository,
        assembler: EntityQueryAssembler,
        validator: QueryFilterValidator,
        entityTypeRelationshipService: EntityTypeRelationshipService,
        entityAttributeService: riven.core.service.entity.EntityAttributeService,
        dataSource: DataSource,
    ) = EntityQueryService(entityTypeRepository, entityRepository, assembler, validator, entityTypeRelationshipService, entityAttributeService, dataSource, QueryConfigurationProperties())
}

/**
 * Abstract base class for entity query integration tests using Testcontainers PostgreSQL.
 *
 * Provides:
 * - Singleton PostgreSQL container shared across test classes
 * - Test domain with Company, Employee, and Project entity types
 * - Seed data with ~40 entities and relationships
 * - Truncation support for clean test state
 */
@SpringBootTest(
    classes = [EntityQueryIntegrationTestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("integration")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class EntityQueryIntegrationTestBase {

    @Autowired
    protected lateinit var entityQueryService: EntityQueryService

    @Autowired
    protected lateinit var entityTypeRepository: EntityTypeRepository

    @Autowired
    protected lateinit var entityRepository: EntityRepository

    @Autowired
    protected lateinit var entityRelationshipRepository: EntityRelationshipRepository

    @Autowired
    protected lateinit var definitionRepository: RelationshipDefinitionRepository

    @Autowired
    protected lateinit var targetRuleRepository: RelationshipTargetRuleRepository

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
        )
            .withDatabaseName("riven_test")
            .withUsername("test")
            .withPassword("test")

        init {
            postgres.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    // Workspace IDs
    protected val workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    protected val otherWorkspaceId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    // Company entity type attribute UUIDs
    protected val companyNameAttrId = UUID.fromString("10000000-0000-0000-0000-000000000001")
    protected val companyIndustryAttrId = UUID.fromString("10000000-0000-0000-0000-000000000002")
    protected val companyRevenueAttrId = UUID.fromString("10000000-0000-0000-0000-000000000003")
    protected val companyActiveAttrId = UUID.fromString("10000000-0000-0000-0000-000000000004")
    protected val companyFoundedAttrId = UUID.fromString("10000000-0000-0000-0000-000000000005")
    protected val companyWebsiteAttrId = UUID.fromString("10000000-0000-0000-0000-000000000006")

    // Employee entity type attribute UUIDs
    protected val employeeFirstNameAttrId = UUID.fromString("20000000-0000-0000-0000-000000000001")
    protected val employeeLastNameAttrId = UUID.fromString("20000000-0000-0000-0000-000000000002")
    protected val employeeEmailAttrId = UUID.fromString("20000000-0000-0000-0000-000000000003")
    protected val employeeSalaryAttrId = UUID.fromString("20000000-0000-0000-0000-000000000004")
    protected val employeeDepartmentAttrId = UUID.fromString("20000000-0000-0000-0000-000000000005")

    // Project entity type attribute UUIDs
    protected val projectTitleAttrId = UUID.fromString("30000000-0000-0000-0000-000000000001")
    protected val projectBudgetAttrId = UUID.fromString("30000000-0000-0000-0000-000000000002")
    protected val projectStatusAttrId = UUID.fromString("30000000-0000-0000-0000-000000000003")

    // Entity type IDs (set during @BeforeAll)
    protected lateinit var companyTypeId: UUID
    protected lateinit var employeeTypeId: UUID
    protected lateinit var projectTypeId: UUID

    // Relationship definition IDs (set during @BeforeAll)
    protected lateinit var companyEmployeesRelId: UUID
    protected lateinit var companyProjectsRelId: UUID
    protected lateinit var companyOwnerRelId: UUID
    protected lateinit var employeeProjectsRelId: UUID
    protected lateinit var projectClientRelId: UUID

    // Entity IDs mapped by name
    protected val companyEntities = mutableMapOf<String, UUID>()
    protected val employeeEntities = mutableMapOf<String, UUID>()
    protected val projectEntities = mutableMapOf<String, UUID>()

    @BeforeAll
    fun setupTestDomain() {
        truncateAll()
        createEntityTypes()
        createRelationshipDefinitions()
        seedEntities()
        createRelationships()
    }

    protected fun truncateAll() {
        jdbcTemplate.execute("TRUNCATE TABLE entity_attributes CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE entity_relationships CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE relationship_target_rules CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE relationship_definitions CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE entities CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE entity_types CASCADE")
        companyEntities.clear()
        employeeEntities.clear()
        projectEntities.clear()
    }

    protected fun createEntityTypes() {
        // Company entity type
        val companyType = EntityTypeEntity(
            key = "company",
            workspaceId = workspaceId,
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
                    ),
                    companyIndustryAttrId to Schema(
                        label = "Industry",
                        key = SchemaType.TEXT,
                        type = DataType.STRING,
                        required = true
                    ),
                    companyRevenueAttrId to Schema(
                        label = "Revenue",
                        key = SchemaType.NUMBER,
                        type = DataType.NUMBER,
                        required = true
                    ),
                    companyActiveAttrId to Schema(
                        label = "Active",
                        key = SchemaType.TEXT,
                        type = DataType.STRING,
                        required = true
                    ),
                    companyFoundedAttrId to Schema(
                        label = "Founded",
                        key = SchemaType.TEXT,
                        type = DataType.STRING,
                        required = true
                    ),
                    companyWebsiteAttrId to Schema(
                        label = "Website",
                        key = SchemaType.TEXT,
                        type = DataType.STRING,
                        required = false
                    )
                )
            ),
            columns = listOf(
                EntityTypeAttributeColumn(companyNameAttrId, EntityPropertyType.ATTRIBUTE),
                EntityTypeAttributeColumn(companyIndustryAttrId, EntityPropertyType.ATTRIBUTE),
                EntityTypeAttributeColumn(companyRevenueAttrId, EntityPropertyType.ATTRIBUTE),
                EntityTypeAttributeColumn(companyActiveAttrId, EntityPropertyType.ATTRIBUTE),
                EntityTypeAttributeColumn(companyFoundedAttrId, EntityPropertyType.ATTRIBUTE),
                EntityTypeAttributeColumn(companyWebsiteAttrId, EntityPropertyType.ATTRIBUTE)
            )
        )
        val savedCompanyType = entityTypeRepository.save(companyType)
        companyTypeId = savedCompanyType.id!!

        // Employee entity type
        val employeeType = EntityTypeEntity(
            key = "employee",
            workspaceId = workspaceId,
            displayNameSingular = "Employee",
            displayNamePlural = "Employees",
            identifierKey = employeeFirstNameAttrId,
            schema = Schema(
                label = "Employee",
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                required = true,
                properties = mapOf(
                    employeeFirstNameAttrId to Schema(
                        label = "First Name",
                        key = SchemaType.TEXT,
                        type = DataType.STRING,
                        required = true
                    ),
                    employeeLastNameAttrId to Schema(
                        label = "Last Name",
                        key = SchemaType.TEXT,
                        type = DataType.STRING,
                        required = true
                    ),
                    employeeEmailAttrId to Schema(
                        label = "Email",
                        key = SchemaType.EMAIL,
                        type = DataType.STRING,
                        required = true
                    ),
                    employeeSalaryAttrId to Schema(
                        label = "Salary",
                        key = SchemaType.NUMBER,
                        type = DataType.NUMBER,
                        required = true
                    ),
                    employeeDepartmentAttrId to Schema(
                        label = "Department",
                        key = SchemaType.TEXT,
                        type = DataType.STRING,
                        required = true
                    )
                )
            ),
            columns = listOf(
                EntityTypeAttributeColumn(employeeFirstNameAttrId, EntityPropertyType.ATTRIBUTE),
                EntityTypeAttributeColumn(employeeLastNameAttrId, EntityPropertyType.ATTRIBUTE),
                EntityTypeAttributeColumn(employeeEmailAttrId, EntityPropertyType.ATTRIBUTE),
                EntityTypeAttributeColumn(employeeSalaryAttrId, EntityPropertyType.ATTRIBUTE),
                EntityTypeAttributeColumn(employeeDepartmentAttrId, EntityPropertyType.ATTRIBUTE)
            )
        )
        val savedEmployeeType = entityTypeRepository.save(employeeType)
        employeeTypeId = savedEmployeeType.id!!

        // Project entity type
        val projectType = EntityTypeEntity(
            key = "project",
            workspaceId = workspaceId,
            displayNameSingular = "Project",
            displayNamePlural = "Projects",
            identifierKey = projectTitleAttrId,
            schema = Schema(
                label = "Project",
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                required = true,
                properties = mapOf(
                    projectTitleAttrId to Schema(
                        label = "Title",
                        key = SchemaType.TEXT,
                        type = DataType.STRING,
                        required = true
                    ),
                    projectBudgetAttrId to Schema(
                        label = "Budget",
                        key = SchemaType.NUMBER,
                        type = DataType.NUMBER,
                        required = true
                    ),
                    projectStatusAttrId to Schema(
                        label = "Status",
                        key = SchemaType.TEXT,
                        type = DataType.STRING,
                        required = true
                    )
                )
            ),
            columns = listOf(
                EntityTypeAttributeColumn(projectTitleAttrId, EntityPropertyType.ATTRIBUTE),
                EntityTypeAttributeColumn(projectBudgetAttrId, EntityPropertyType.ATTRIBUTE),
                EntityTypeAttributeColumn(projectStatusAttrId, EntityPropertyType.ATTRIBUTE)
            )
        )
        val savedProjectType = entityTypeRepository.save(projectType)
        projectTypeId = savedProjectType.id!!
    }

    protected fun createRelationshipDefinitions() {
        // Company -> Employees (ONE_TO_MANY)
        companyEmployeesRelId = definitionRepository.save(RelationshipDefinitionEntity(
            workspaceId = workspaceId,
            sourceEntityTypeId = companyTypeId,
            name = "Employees",
            cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY,
        )).id!!
        targetRuleRepository.save(RelationshipTargetRuleEntity(
            relationshipDefinitionId = companyEmployeesRelId,
            targetEntityTypeId = employeeTypeId,
            inverseName = "Company",
        ))

        // Company -> Projects (ONE_TO_MANY)
        companyProjectsRelId = definitionRepository.save(RelationshipDefinitionEntity(
            workspaceId = workspaceId,
            sourceEntityTypeId = companyTypeId,
            name = "Projects",
            cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY,
        )).id!!
        targetRuleRepository.save(RelationshipTargetRuleEntity(
            relationshipDefinitionId = companyProjectsRelId,
            targetEntityTypeId = projectTypeId,
            inverseName = "Company",
        ))

        // Company -> Owner (MANY_TO_ONE, multiple target types: Employee or Project)
        companyOwnerRelId = definitionRepository.save(RelationshipDefinitionEntity(
            workspaceId = workspaceId,
            sourceEntityTypeId = companyTypeId,
            name = "Owner",
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_ONE,
            allowPolymorphic = false,
        )).id!!
        targetRuleRepository.save(RelationshipTargetRuleEntity(
            relationshipDefinitionId = companyOwnerRelId,
            targetEntityTypeId = employeeTypeId,
            inverseName = "Owner Of",
        ))
        targetRuleRepository.save(RelationshipTargetRuleEntity(
            relationshipDefinitionId = companyOwnerRelId,
            targetEntityTypeId = projectTypeId,
            inverseName = "Owner Of",
        ))

        // Employee -> Projects (MANY_TO_MANY)
        employeeProjectsRelId = definitionRepository.save(RelationshipDefinitionEntity(
            workspaceId = workspaceId,
            sourceEntityTypeId = employeeTypeId,
            name = "Projects",
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
        )).id!!
        targetRuleRepository.save(RelationshipTargetRuleEntity(
            relationshipDefinitionId = employeeProjectsRelId,
            targetEntityTypeId = projectTypeId,
            inverseName = "Employees",
        ))

        // Project -> Client (MANY_TO_ONE)
        projectClientRelId = definitionRepository.save(RelationshipDefinitionEntity(
            workspaceId = workspaceId,
            sourceEntityTypeId = projectTypeId,
            name = "Client",
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_ONE,
        )).id!!
        targetRuleRepository.save(RelationshipTargetRuleEntity(
            relationshipDefinitionId = projectClientRelId,
            targetEntityTypeId = companyTypeId,
            inverseName = "Projects",
        ))
    }

    protected fun seedEntities() {
        // Create 10 companies
        val companies = listOf(
            createCompany("Acme Corp", "Technology", 5000000.0, "true", "2010", "https://acme.com"),
            createCompany("Beta Inc", "Technology", 2000000.0, "true", "2015", null),
            createCompany("Gamma LLC", "Finance", 10000000.0, "true", "2005", "https://gamma.io"),
            createCompany("Delta Corp", "Finance", 500000.0, "false", "2020", null),
            createCompany("Epsilon Ltd", "Healthcare", 3000000.0, "true", "2018", "https://epsilon.health"),
            createCompany("Zeta Systems", "Technology", 8000000.0, "true", "2012", "https://zeta.dev"),
            createCompany("Eta Solutions", "Consulting", 1000000.0, "false", "2019", null),
            createCompany("Theta Group", "Finance", 15000000.0, "true", "2000", "https://theta.com"),
            createCompany("Iota Partners", "Consulting", 750000.0, "true", "2021", null),
            createCompany("Kappa Industries", "Healthcare", 6000000.0, "true", "2008", "https://kappa.med")
        )
        companies.forEach { companyEntities[it.first] = it.second }

        // Create 20 employees
        val employees = listOf(
            createEmployee("Alice", "Anderson", "alice@acme.com", 120000.0, "Engineering"),
            createEmployee("Bob", "Brown", "bob@acme.com", 85000.0, "Sales"),
            createEmployee("Charlie", "Chen", "charlie@beta.com", 95000.0, "Engineering"),
            createEmployee("Diana", "Davis", "diana@gamma.com", 110000.0, "Finance"),
            createEmployee("Edward", "Evans", "edward@gamma.com", 75000.0, "HR"),
            createEmployee("Fiona", "Fisher", "fiona@epsilon.com", 105000.0, "Engineering"),
            createEmployee("George", "Garcia", "george@zeta.com", 130000.0, "Engineering"),
            createEmployee("Hannah", "Harris", "hannah@zeta.com", 90000.0, "Marketing"),
            createEmployee("Ian", "Ivanov", "ian@eta.com", 70000.0, "Consulting"),
            createEmployee("Jane", "Jackson", "jane@theta.com", 140000.0, "Finance"),
            createEmployee("Kevin", "Kim", "kevin@theta.com", 100000.0, "Engineering"),
            createEmployee("Laura", "Lopez", "laura@iota.com", 80000.0, "Consulting"),
            createEmployee("Michael", "Miller", "michael@kappa.com", 115000.0, "Engineering"),
            createEmployee("Nancy", "Nelson", "nancy@kappa.com", 95000.0, "HR"),
            createEmployee("Oliver", "O'Brien", "oliver@acme.com", 88000.0, "Sales"),
            createEmployee("Patricia", "Parker", "patricia@beta.com", 92000.0, "Marketing"),
            createEmployee("Quinn", "Quinn", "quinn@delta.com", 65000.0, "Finance"),
            createEmployee("Rachel", "Roberts", "rachel@epsilon.com", 98000.0, "Marketing"),
            createEmployee("Samuel", "Smith", "samuel@zeta.com", 125000.0, "Engineering"),
            createEmployee("Teresa", "Taylor", "teresa@theta.com", 135000.0, "Finance")
        )
        employees.forEach { employeeEntities[it.first] = it.second }

        // Create 10 projects
        val projects = listOf(
            createProject("Platform Redesign", 500000.0, "Active"),
            createProject("Mobile App Launch", 300000.0, "Active"),
            createProject("Data Migration", 150000.0, "Completed"),
            createProject("CRM Integration", 200000.0, "Planning"),
            createProject("Security Audit", 100000.0, "Active"),
            createProject("Cloud Migration", 800000.0, "Active"),
            createProject("Website Refresh", 120000.0, "Completed"),
            createProject("API Gateway", 400000.0, "Active"),
            createProject("Analytics Dashboard", 250000.0, "Planning"),
            createProject("Payment System", 600000.0, "Active")
        )
        projects.forEach { projectEntities[it.first] = it.second }
    }

    protected fun insertAttributes(entityId: UUID, typeId: UUID, workspaceId: UUID, attributes: Map<UUID, EntityAttributePrimitivePayload>) {
        val now = java.sql.Timestamp.from(java.time.Instant.now())
        attributes.forEach { (attrId, payload) ->
            val valueJson = objectMapper.writeValueAsString(payload.value)
            jdbcTemplate.update(
                """INSERT INTO entity_attributes (id, entity_id, workspace_id, type_id, attribute_id, schema_type, value, deleted, created_at, updated_at)
                   VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, ?::jsonb, false, ?, ?)""",
                UUID.randomUUID().toString(),
                entityId.toString(),
                workspaceId.toString(),
                typeId.toString(),
                attrId.toString(),
                payload.schemaType.name,
                valueJson,
                now,
                now
            )
        }
    }

    protected fun createCompany(name: String, industry: String, revenue: Double, active: String, founded: String, website: String?): Pair<String, UUID> {
        val attributes = mutableMapOf(
            companyNameAttrId to EntityAttributePrimitivePayload(name, SchemaType.TEXT),
            companyIndustryAttrId to EntityAttributePrimitivePayload(industry, SchemaType.TEXT),
            companyRevenueAttrId to EntityAttributePrimitivePayload(revenue, SchemaType.NUMBER),
            companyActiveAttrId to EntityAttributePrimitivePayload(active, SchemaType.TEXT),
            companyFoundedAttrId to EntityAttributePrimitivePayload(founded, SchemaType.TEXT)
        )
        if (website != null) {
            attributes[companyWebsiteAttrId] = EntityAttributePrimitivePayload(website, SchemaType.TEXT)
        }

        val entity = EntityEntity(
            workspaceId = workspaceId,
            typeId = companyTypeId,
            typeKey = "company",
            identifierKey = companyNameAttrId,
        )
        val saved = entityRepository.save(entity)
        insertAttributes(saved.id!!, companyTypeId, workspaceId, attributes)
        return name to saved.id!!
    }

    private fun createEmployee(firstName: String, lastName: String, email: String, salary: Double, department: String): Pair<String, UUID> {
        val attributes = mapOf(
            employeeFirstNameAttrId to EntityAttributePrimitivePayload(firstName, SchemaType.TEXT),
            employeeLastNameAttrId to EntityAttributePrimitivePayload(lastName, SchemaType.TEXT),
            employeeEmailAttrId to EntityAttributePrimitivePayload(email, SchemaType.EMAIL),
            employeeSalaryAttrId to EntityAttributePrimitivePayload(salary, SchemaType.NUMBER),
            employeeDepartmentAttrId to EntityAttributePrimitivePayload(department, SchemaType.TEXT)
        )

        val entity = EntityEntity(
            workspaceId = workspaceId,
            typeId = employeeTypeId,
            typeKey = "employee",
            identifierKey = employeeFirstNameAttrId,
        )
        val saved = entityRepository.save(entity)
        insertAttributes(saved.id!!, employeeTypeId, workspaceId, attributes)
        return "$firstName $lastName" to saved.id!!
    }

    private fun createProject(title: String, budget: Double, status: String): Pair<String, UUID> {
        val attributes = mapOf(
            projectTitleAttrId to EntityAttributePrimitivePayload(title, SchemaType.TEXT),
            projectBudgetAttrId to EntityAttributePrimitivePayload(budget, SchemaType.NUMBER),
            projectStatusAttrId to EntityAttributePrimitivePayload(status, SchemaType.TEXT)
        )

        val entity = EntityEntity(
            workspaceId = workspaceId,
            typeId = projectTypeId,
            typeKey = "project",
            identifierKey = projectTitleAttrId,
        )
        val saved = entityRepository.save(entity)
        insertAttributes(saved.id!!, projectTypeId, workspaceId, attributes)
        return title to saved.id!!
    }

    protected fun createRelationships() {
        // Company -> Employee relationships
        val companyEmployeeLinks = mapOf(
            "Acme Corp" to listOf("Alice Anderson", "Bob Brown", "Oliver O'Brien"),
            "Beta Inc" to listOf("Charlie Chen", "Patricia Parker"),
            "Gamma LLC" to listOf("Diana Davis", "Edward Evans"),
            "Delta Corp" to listOf("Quinn Quinn"),
            "Epsilon Ltd" to listOf("Fiona Fisher", "Rachel Roberts"),
            "Zeta Systems" to listOf("George Garcia", "Hannah Harris", "Samuel Smith"),
            "Eta Solutions" to listOf("Ian Ivanov"),
            "Theta Group" to listOf("Jane Jackson", "Kevin Kim", "Teresa Taylor"),
            "Iota Partners" to listOf("Laura Lopez"),
            "Kappa Industries" to listOf("Michael Miller", "Nancy Nelson")
        )

        companyEmployeeLinks.forEach { (companyName, employeeNames) ->
            val companyId = companyEntities[companyName]!!
            employeeNames.forEach { employeeName ->
                val employeeId = employeeEntities[employeeName]!!
                entityRelationshipRepository.save(
                    EntityRelationshipEntity(
                        workspaceId = workspaceId,
                        sourceId = companyId,
                        targetId = employeeId,
                        definitionId = companyEmployeesRelId
                    )
                )
            }
        }

        // Company -> Project relationships
        val companyProjectLinks = mapOf(
            "Acme Corp" to listOf("Platform Redesign", "Mobile App Launch"),
            "Beta Inc" to listOf("Data Migration"),
            "Gamma LLC" to listOf("CRM Integration", "Security Audit"),
            "Epsilon Ltd" to listOf("Cloud Migration"),
            "Zeta Systems" to listOf("Website Refresh", "API Gateway"),
            "Theta Group" to listOf("Analytics Dashboard"),
            "Kappa Industries" to listOf("Payment System")
        )

        companyProjectLinks.forEach { (companyName, projectTitles) ->
            val companyId = companyEntities[companyName]!!
            projectTitles.forEach { projectTitle ->
                val projectId = projectEntities[projectTitle]!!
                entityRelationshipRepository.save(
                    EntityRelationshipEntity(
                        workspaceId = workspaceId,
                        sourceId = companyId,
                        targetId = projectId,
                        definitionId = companyProjectsRelId
                    )
                )
            }
        }

        // Polymorphic Owner relationships (Company -> Employee or Project)
        val ownerLinks = listOf(
            companyEntities["Acme Corp"]!! to employeeEntities["Alice Anderson"]!!,
            companyEntities["Gamma LLC"]!! to projectEntities["CRM Integration"]!!
        )

        ownerLinks.forEach { (companyId, ownerId) ->
            entityRelationshipRepository.save(
                EntityRelationshipEntity(
                    workspaceId = workspaceId,
                    sourceId = companyId,
                    targetId = ownerId,
                    definitionId = companyOwnerRelId
                )
            )
        }

        // Employee -> Project relationships (for 2-deep nesting)
        val employeeProjectLinks = mapOf(
            "Alice Anderson" to listOf("Platform Redesign", "Mobile App Launch"),
            "Charlie Chen" to listOf("Data Migration"),
            "Diana Davis" to listOf("CRM Integration"),
            "George Garcia" to listOf("API Gateway"),
            "Jane Jackson" to listOf("Analytics Dashboard"),
            "Michael Miller" to listOf("Payment System")
        )

        employeeProjectLinks.forEach { (employeeName, projectTitles) ->
            val employeeId = employeeEntities[employeeName]!!
            projectTitles.forEach { projectTitle ->
                val projectId = projectEntities[projectTitle]!!
                entityRelationshipRepository.save(
                    EntityRelationshipEntity(
                        workspaceId = workspaceId,
                        sourceId = employeeId,
                        targetId = projectId,
                        definitionId = employeeProjectsRelId
                    )
                )
            }
        }

        // Project -> Company relationships (for 3-deep nesting)
        val projectClientLinks = mapOf(
            "Platform Redesign" to "Gamma LLC",
            "Mobile App Launch" to "Zeta Systems",
            "Data Migration" to "Beta Inc",
            "CRM Integration" to "Theta Group",
            "API Gateway" to "Acme Corp",
            "Payment System" to "Epsilon Ltd"
        )

        projectClientLinks.forEach { (projectTitle, companyName) ->
            val projectId = projectEntities[projectTitle]!!
            val clientCompanyId = companyEntities[companyName]!!
            entityRelationshipRepository.save(
                EntityRelationshipEntity(
                    workspaceId = workspaceId,
                    sourceId = projectId,
                    targetId = clientCompanyId,
                    definitionId = projectClientRelId
                )
            )
        }
    }
}
