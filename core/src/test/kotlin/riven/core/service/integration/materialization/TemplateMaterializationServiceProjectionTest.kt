package riven.core.service.integration.materialization

import tools.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.entity.entity.RelationshipDefinitionEntity
import riven.core.enums.catalog.ManifestType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.integration.SourceType
import riven.core.entity.entity.EntityTypeEntity
import riven.core.entity.entity.RelationshipTargetRuleEntity
import riven.core.entity.integration.ProjectionRuleEntity
import riven.core.models.core.CoreModelRegistry
import riven.core.repository.catalog.CatalogEntityTypeRepository
import riven.core.repository.catalog.CatalogRelationshipRepository
import riven.core.repository.catalog.CatalogRelationshipTargetRuleRepository
import riven.core.repository.catalog.ManifestCatalogRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.entity.RelationshipTargetRuleRepository
import riven.core.repository.integration.ProjectionRuleRepository
import riven.core.service.entity.EntityTypeSemanticMetadataService
import riven.core.service.entity.type.EntityTypeRelationshipService
import riven.core.service.entity.type.EntityTypeSequenceService
import riven.core.service.util.factory.catalog.CatalogFactory
import riven.core.service.util.factory.entity.EntityFactory
import java.util.*

/**
 * Tests ONLY the projection rule installation part of TemplateMaterializationService.
 *
 * The base materialization flow (entity types, relationships, UUID generation) is covered
 * by TemplateMaterializationServiceTest. This test class focuses on installProjectionRules:
 * - Matching integration entity types to core models via (lifecycleDomain, semanticGroup)
 * - Creating ProjectionRuleEntity with correct source/target UUIDs
 * - Idempotency: skipping duplicate rules on re-materialization
 */
@SpringBootTest(
    classes = [
        TemplateMaterializationServiceProjectionTest.TestConfig::class,
        TemplateMaterializationService::class,
    ]
)
class TemplateMaterializationServiceProjectionTest {

    @Configuration
    class TestConfig

    private val workspaceId: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef9876543210")
    private val manifestId: UUID = UUID.fromString("a1b2c3d4-5e6f-7890-abcd-ef1234567890")
    private val integrationDefinitionId: UUID = UUID.fromString("b2c3d4e5-6f78-9012-abcd-ef2345678901")
    private val integrationSlug = "hubspot"

    @MockitoBean private lateinit var entityTypeRepository: EntityTypeRepository
    @MockitoBean private lateinit var relationshipDefinitionRepository: RelationshipDefinitionRepository
    @MockitoBean private lateinit var relationshipTargetRuleRepository: RelationshipTargetRuleRepository
    @MockitoBean private lateinit var catalogEntityTypeRepository: CatalogEntityTypeRepository
    @MockitoBean private lateinit var catalogRelationshipRepository: CatalogRelationshipRepository
    @MockitoBean private lateinit var catalogRelationshipTargetRuleRepository: CatalogRelationshipTargetRuleRepository
    @MockitoBean private lateinit var manifestCatalogRepository: ManifestCatalogRepository
    @MockitoBean private lateinit var projectionRuleRepository: ProjectionRuleRepository
    @MockitoBean private lateinit var semanticMetadataService: EntityTypeSemanticMetadataService
    @MockitoBean private lateinit var relationshipService: EntityTypeRelationshipService
    @MockitoBean private lateinit var sequenceService: EntityTypeSequenceService
    @MockitoBean private lateinit var objectMapper: ObjectMapper
    @MockitoBean private lateinit var logger: KLogger

    @Autowired
    private lateinit var service: TemplateMaterializationService

    @BeforeEach
    fun setup() {
        reset(
            entityTypeRepository, relationshipDefinitionRepository, relationshipTargetRuleRepository,
            catalogEntityTypeRepository, catalogRelationshipRepository, catalogRelationshipTargetRuleRepository,
            manifestCatalogRepository, projectionRuleRepository, semanticMetadataService,
            relationshipService, sequenceService,
        )

        // Default: manifest found
        val manifestEntity = CatalogFactory.createManifestEntity(
            type = ManifestType.INTEGRATION,
            id = manifestId,
            key = integrationSlug,
            name = "HubSpot",
        )
        whenever(manifestCatalogRepository.findByKeyAndManifestType(integrationSlug, ManifestType.INTEGRATION))
            .thenReturn(manifestEntity)

        // Default: no existing entity types, no soft-deleted
        whenever(entityTypeRepository.findByworkspaceIdAndKeyIn(eq(workspaceId), any())).thenReturn(emptyList())
        whenever(entityTypeRepository.findSoftDeletedByWorkspaceIdAndKeyIn(eq(workspaceId), any())).thenReturn(emptyList())

        // Default: no relationships
        whenever(catalogRelationshipRepository.findByManifestId(manifestId)).thenReturn(emptyList())

        // Default: save returns entity with ID
        whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { invocation ->
            val entity = invocation.getArgument<EntityTypeEntity>(0)
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }

        whenever(relationshipDefinitionRepository.save(any<RelationshipDefinitionEntity>())).thenAnswer { invocation ->
            val entity = invocation.getArgument<RelationshipDefinitionEntity>(0)
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }

        whenever(relationshipTargetRuleRepository.save(any<RelationshipTargetRuleEntity>())).thenAnswer { invocation ->
            val entity = invocation.getArgument<RelationshipTargetRuleEntity>(0)
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }

        whenever(projectionRuleRepository.save(any<ProjectionRuleEntity>())).thenAnswer { invocation ->
            val entity = invocation.getArgument<ProjectionRuleEntity>(0)
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }

        whenever(relationshipService.createSystemConnectionDefinition(any(), any())).thenReturn(
            EntityFactory.createRelationshipDefinitionEntity(workspaceId = workspaceId)
        )

        // Default: no existing projection rules (not idempotent by default)
        whenever(projectionRuleRepository.existsByWorkspaceAndSourceAndTarget(any(), any(), any())).thenReturn(false)

        // Default: no existing relationship definitions for projection
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeIdAndName(any(), any(), any()))
            .thenReturn(Optional.empty())
    }

    @Test
    fun `core model with projectionAccepts - rules created with correct UUIDs`() {
        // Find a real core model that has projectionAccepts rules
        val coreModelsWithAccepts = CoreModelRegistry.allModels.filter { it.projectionAccepts.isNotEmpty() }
        check(coreModelsWithAccepts.isNotEmpty()) { "No core models have projectionAccepts — test cannot proceed" }

        val coreModel = coreModelsWithAccepts.first()
        val acceptRule = coreModel.projectionAccepts.first()

        val integrationEntityTypeId = UUID.randomUUID()
        val coreEntityTypeId = UUID.randomUUID()

        // Create a catalog entity type whose (lifecycleDomain, semanticGroup) matches the accept rule
        val catalogType = CatalogFactory.createEntityTypeEntity(
            manifestId = manifestId,
            key = "hubspot-matching",
            displayNameSingular = "Matching Type",
            displayNamePlural = "Matching Types",
            semanticGroup = acceptRule.semanticGroup,
            schema = mapOf(
                "name" to mapOf(
                    "label" to "Name", "key" to "TEXT", "type" to "string",
                    "required" to true, "unique" to false, "protected" to true,
                )
            ),
        )
        // CatalogEntityTypeEntity also has lifecycleDomain — set it to match
        val catalogTypeWithDomain = catalogType.copy(lifecycleDomain = acceptRule.domain)

        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(catalogTypeWithDomain))

        // Make the integration entity type save return a known ID
        whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { invocation ->
            val entity = invocation.getArgument<EntityTypeEntity>(0)
            entity.copy(id = integrationEntityTypeId)
        }

        // Create a core (TEMPLATE) entity type that matches the core model key
        val coreEntityType = EntityFactory.createEntityType(
            id = coreEntityTypeId,
            key = coreModel.key,
            workspaceId = workspaceId,
            sourceType = SourceType.TEMPLATE,
        )

        whenever(entityTypeRepository.findByworkspaceId(workspaceId)).thenReturn(listOf(coreEntityType))

        service.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)

        // Verify at least one projection rule was created with correct source and target UUIDs.
        // Multiple rules may be created if multiple core models share the same (domain, group).
        verify(projectionRuleRepository, atLeastOnce()).save(argThat { rule ->
            rule.sourceEntityTypeId == integrationEntityTypeId &&
                rule.targetEntityTypeId == coreEntityTypeId &&
                rule.workspaceId == workspaceId &&
                rule.autoCreate == acceptRule.autoCreate
        })
    }

    @Test
    fun `core model without projectionAccepts - no rules created`() {
        // Create a catalog entity type with a (lifecycleDomain, semanticGroup) that does NOT match
        // any core model's projectionAccepts. Use an unusual combination.
        val catalogType = CatalogFactory.createEntityTypeEntity(
            manifestId = manifestId,
            key = "hubspot-unmatched",
            displayNameSingular = "Unmatched Type",
            displayNamePlural = "Unmatched Types",
            semanticGroup = SemanticGroup.UNCATEGORIZED,
            schema = mapOf(
                "name" to mapOf(
                    "label" to "Name", "key" to "TEXT", "type" to "string",
                    "required" to true, "unique" to false, "protected" to true,
                )
            ),
        )
        // Set lifecycleDomain to UNCATEGORIZED — no core model should accept (UNCATEGORIZED, UNCATEGORIZED)
        val catalogTypeWithDomain = catalogType.copy(lifecycleDomain = LifecycleDomain.UNCATEGORIZED)

        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(catalogTypeWithDomain))

        // Even with core entity types present, the match should fail
        val coreEntityType = EntityFactory.createEntityType(
            key = "some-core-model",
            workspaceId = workspaceId,
            sourceType = SourceType.TEMPLATE,
        )
        whenever(entityTypeRepository.findByworkspaceId(workspaceId)).thenReturn(listOf(coreEntityType))

        service.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)

        verify(projectionRuleRepository, never()).save(any<ProjectionRuleEntity>())
    }

    @Test
    fun `re-materialization - idempotent, no duplicate rules`() {
        // Find a real core model that has projectionAccepts
        val coreModelsWithAccepts = CoreModelRegistry.allModels.filter { it.projectionAccepts.isNotEmpty() }
        check(coreModelsWithAccepts.isNotEmpty()) { "No core models have projectionAccepts — test cannot proceed" }

        val coreModel = coreModelsWithAccepts.first()
        val acceptRule = coreModel.projectionAccepts.first()

        val integrationEntityTypeId = UUID.randomUUID()
        val coreEntityTypeId = UUID.randomUUID()

        val catalogType = CatalogFactory.createEntityTypeEntity(
            manifestId = manifestId,
            key = "hubspot-matching",
            displayNameSingular = "Matching Type",
            displayNamePlural = "Matching Types",
            semanticGroup = acceptRule.semanticGroup,
            schema = mapOf(
                "name" to mapOf(
                    "label" to "Name", "key" to "TEXT", "type" to "string",
                    "required" to true, "unique" to false, "protected" to true,
                )
            ),
        )
        val catalogTypeWithDomain = catalogType.copy(lifecycleDomain = acceptRule.domain)

        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(catalogTypeWithDomain))

        whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { invocation ->
            val entity = invocation.getArgument<EntityTypeEntity>(0)
            entity.copy(id = integrationEntityTypeId)
        }

        val coreEntityType = EntityFactory.createEntityType(
            id = coreEntityTypeId,
            key = coreModel.key,
            workspaceId = workspaceId,
            sourceType = SourceType.TEMPLATE,
        )
        whenever(entityTypeRepository.findByworkspaceId(workspaceId)).thenReturn(listOf(coreEntityType))

        // Rule already exists — idempotency guard
        whenever(projectionRuleRepository.existsByWorkspaceAndSourceAndTarget(
            workspaceId, integrationEntityTypeId, coreEntityTypeId
        )).thenReturn(true)

        service.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)

        verify(projectionRuleRepository, never()).save(any<ProjectionRuleEntity>())
    }
}
