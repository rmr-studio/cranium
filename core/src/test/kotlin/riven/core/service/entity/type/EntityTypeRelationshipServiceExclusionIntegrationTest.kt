package riven.core.service.entity.type

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.transaction.annotation.Transactional
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import riven.core.entity.entity.*
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.EntityPropertyType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.models.common.validation.Schema
import riven.core.models.entity.configuration.EntityTypeAttributeColumn
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.repository.entity.*
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.*

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
class ExclusionIntegrationTestConfig {

    @Bean
    fun auditorProvider(): AuditorAware<UUID> = AuditorAware { Optional.empty() }

    @Bean
    fun dateTimeProvider(): DateTimeProvider =
        DateTimeProvider { Optional.of<TemporalAccessor>(ZonedDateTime.now()) }
}

@SpringBootTest(
    classes = [ExclusionIntegrationTestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("integration")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntityTypeRelationshipServiceExclusionIntegrationTest {

    @Autowired
    private lateinit var entityRelationshipRepository: EntityRelationshipRepository

    @Autowired
    private lateinit var exclusionRepository: RelationshipDefinitionExclusionRepository

    @Autowired
    private lateinit var definitionRepository: RelationshipDefinitionRepository

    @Autowired
    private lateinit var targetRuleRepository: RelationshipTargetRuleRepository

    @Autowired
    private lateinit var entityTypeRepository: EntityTypeRepository

    @Autowired
    private lateinit var entityRepository: EntityRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

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

    private val workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val nameAttrId = UUID.fromString("10000000-0000-0000-0000-000000000001")

    private lateinit var sourceTypeId: UUID
    private lateinit var targetTypeId: UUID
    private lateinit var definitionId: UUID
    private lateinit var sourceEntityId: UUID
    private lateinit var targetEntityId: UUID

    @BeforeAll
    fun setup() {
        truncateAll()
        seedTestData()
    }

    private fun truncateAll() {
        jdbcTemplate.execute("TRUNCATE TABLE relationship_definition_exclusions CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE entity_relationships CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE relationship_target_rules CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE relationship_definitions CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE entities CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE entity_types CASCADE")
    }

    private fun seedTestData() {
        val schema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = mapOf(
                nameAttrId to Schema(key = SchemaType.TEXT, type = DataType.STRING, required = true),
            ),
        )
        val columns = listOf(EntityTypeAttributeColumn(nameAttrId, EntityPropertyType.ATTRIBUTE))

        val savedSourceType = entityTypeRepository.save(EntityTypeEntity(
            key = "source_type", workspaceId = workspaceId,
            displayNameSingular = "Source", displayNamePlural = "Sources",
            identifierKey = nameAttrId, schema = schema, columns = columns,
        ))
        sourceTypeId = savedSourceType.id!!

        val savedTargetType = entityTypeRepository.save(EntityTypeEntity(
            key = "target_type", workspaceId = workspaceId,
            displayNameSingular = "Target", displayNamePlural = "Targets",
            identifierKey = nameAttrId, schema = schema, columns = columns,
        ))
        targetTypeId = savedTargetType.id!!

        val savedDef = definitionRepository.save(RelationshipDefinitionEntity(
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceTypeId,
            name = "Related",
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
        ))
        definitionId = savedDef.id!!

        targetRuleRepository.save(RelationshipTargetRuleEntity(
            relationshipDefinitionId = definitionId,
            targetEntityTypeId = targetTypeId,
            inverseName = "Related From",
        ))

        val savedSource = entityRepository.save(EntityEntity(
            workspaceId = workspaceId, typeId = sourceTypeId, typeKey = "source_type",
            identifierKey = nameAttrId,
        ))
        sourceEntityId = savedSource.id!!

        val savedTarget = entityRepository.save(EntityEntity(
            workspaceId = workspaceId, typeId = targetTypeId, typeKey = "target_type",
            identifierKey = nameAttrId,
        ))
        targetEntityId = savedTarget.id!!

        entityRelationshipRepository.save(EntityRelationshipEntity(
            workspaceId = workspaceId,
            sourceId = sourceEntityId,
            targetId = targetEntityId,
            definitionId = definitionId,
        ))
    }

    @Test
    @Transactional
    fun `softDeleteByDefinitionIdAndTargetEntityTypeId - marks matching relationships as deleted`() {
        val countBefore = countActiveRelationships()
        assertTrue(countBefore > 0, "Should have active relationships before soft-delete")

        entityRelationshipRepository.softDeleteByDefinitionIdAndTargetEntityTypeId(definitionId, targetTypeId)

        val countAfter = countActiveRelationships()
        assertEquals(0, countAfter, "All relationships for this definition+type should be soft-deleted")

        val deletedCount = countDeletedRelationships()
        assertTrue(deletedCount > 0, "Rows should exist with deleted=true")
    }

    @Test
    @Transactional
    fun `restoreByDefinitionIdAndTargetEntityTypeId - restores soft-deleted relationships`() {
        entityRelationshipRepository.softDeleteByDefinitionIdAndTargetEntityTypeId(definitionId, targetTypeId)
        assertEquals(0, countActiveRelationships())

        entityRelationshipRepository.restoreByDefinitionIdAndTargetEntityTypeId(definitionId, targetTypeId)

        val countAfterRestore = countActiveRelationships()
        assertTrue(countAfterRestore > 0, "Relationships should be restored after restore call")
    }

    @Test
    fun `exclusion record - persists and queries correctly`() {
        val exclusion = exclusionRepository.save(RelationshipDefinitionExclusionEntity(
            relationshipDefinitionId = definitionId,
            entityTypeId = targetTypeId,
        ))

        assertNotNull(exclusion.id)

        val found = exclusionRepository.findByRelationshipDefinitionIdAndEntityTypeId(definitionId, targetTypeId)
        assertTrue(found.isPresent, "Exclusion should be findable by definition+entityType")

        val byDef = exclusionRepository.findByRelationshipDefinitionId(definitionId)
        assertTrue(byDef.any { it.entityTypeId == targetTypeId })

        val byType = exclusionRepository.findByEntityTypeId(targetTypeId)
        assertTrue(byType.any { it.relationshipDefinitionId == definitionId })

        // Cleanup
        exclusionRepository.deleteByRelationshipDefinitionIdAndEntityTypeId(definitionId, targetTypeId)
        assertFalse(
            exclusionRepository.findByRelationshipDefinitionIdAndEntityTypeId(definitionId, targetTypeId).isPresent,
            "Exclusion should be deleted",
        )
    }

    @Test
    @Transactional
    fun `countByDefinitionIdAndTargetEntityTypeId - returns correct count via native SQL`() {
        val count = entityRelationshipRepository.countByDefinitionIdAndTargetEntityTypeId(definitionId, targetTypeId)
        assertTrue(count > 0, "Should count relationships where target entity has the specified type")
    }

    private fun countActiveRelationships(): Long {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM entity_relationships WHERE relationship_definition_id = ? AND deleted = false",
            Long::class.java,
            definitionId,
        )!!
    }

    private fun countDeletedRelationships(): Long {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM entity_relationships WHERE relationship_definition_id = ? AND deleted = true",
            Long::class.java,
            definitionId,
        )!!
    }
}
