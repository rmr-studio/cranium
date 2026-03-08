package riven.core.service.entity.type

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.entity.RelationshipDefinitionEntity
import riven.core.entity.entity.RelationshipTargetRuleEntity
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.entity.entity.EntityTypeEntity
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.models.request.entity.type.SaveRelationshipDefinitionRequest
import riven.core.models.request.entity.type.SaveTargetRuleRequest
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.RelationshipDefinitionExclusionRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.entity.RelationshipTargetRuleRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.EntityTypeSemanticMetadataService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.entity.EntityFactory
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        EntityTypeRelationshipServiceTest.TestConfig::class,
        EntityTypeRelationshipService::class,
    ]
)
@WithUserPersona(
    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
    email = "test@test.com",
    displayName = "Test User",
    roles = [
        WorkspaceRole(
            workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
            role = WorkspaceRoles.OWNER
        )
    ]
)
class EntityTypeRelationshipServiceTest : BaseServiceTest() {

    @Configuration
    class TestConfig

    @MockitoBean
    private lateinit var definitionRepository: RelationshipDefinitionRepository

    @MockitoBean
    private lateinit var targetRuleRepository: RelationshipTargetRuleRepository

    @MockitoBean
    private lateinit var entityTypeRepository: EntityTypeRepository

    @MockitoBean
    private lateinit var exclusionRepository: RelationshipDefinitionExclusionRepository

    @MockitoBean
    private lateinit var entityRelationshipRepository: EntityRelationshipRepository

    @MockitoBean
    private lateinit var activityService: ActivityService

    @MockitoBean
    private lateinit var semanticMetadataService: EntityTypeSemanticMetadataService

    @Autowired
    private lateinit var service: EntityTypeRelationshipService

    private val sourceEntityTypeId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        reset(
            definitionRepository,
            targetRuleRepository,
            exclusionRepository,
            entityTypeRepository,
            entityRelationshipRepository,
            activityService,
            semanticMetadataService,
        )
    }

    // ------ Create ------

    @Test
    fun `createRelationshipDefinition - single target - saves definition and rule`() {
        val targetTypeId = UUID.randomUUID()
        val request = SaveRelationshipDefinitionRequest(
            key = "related_companies",
            id = UUID.randomUUID(),
            name = "Related Companies",
            iconType = IconType.LINK,
            iconColour = IconColour.NEUTRAL,
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            targetRules = listOf(
                SaveTargetRuleRequest(targetEntityTypeId = targetTypeId, inverseName = "Related Companies")
            ),
        )

        whenever(definitionRepository.save(any<RelationshipDefinitionEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as RelationshipDefinitionEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }
        whenever(targetRuleRepository.saveAll(any<List<RelationshipTargetRuleEntity>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val entities = invocation.arguments[0] as List<RelationshipTargetRuleEntity>
            entities.map { if (it.id == null) it.copy(id = UUID.randomUUID()) else it }
        }

        val result = service.createRelationshipDefinition(workspaceId, sourceEntityTypeId, request)

        assertNotNull(result)
        assertEquals("Related Companies", result.name)
        assertEquals(EntityRelationshipCardinality.MANY_TO_MANY, result.cardinalityDefault)
        assertEquals(1, result.targetRules.size)
        assertEquals(targetTypeId, result.targetRules[0].targetEntityTypeId)

        verify(definitionRepository).save(any<RelationshipDefinitionEntity>())
        verify(targetRuleRepository).saveAll(any<List<RelationshipTargetRuleEntity>>())
    }

    @Test
    fun `createRelationshipDefinition - multi target - saves definition and multiple rules`() {
        val targetTypeId1 = UUID.randomUUID()
        val targetTypeId2 = UUID.randomUUID()
        val request = SaveRelationshipDefinitionRequest(
            key = "related_entities",
            id = UUID.randomUUID(),
            name = "Related Entities",
            iconType = IconType.LINK,
            iconColour = IconColour.NEUTRAL,
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            targetRules = listOf(
                SaveTargetRuleRequest(targetEntityTypeId = targetTypeId1, inverseName = "Related Entities"),
                SaveTargetRuleRequest(
                    targetEntityTypeId = targetTypeId2,
                    cardinalityOverride = EntityRelationshipCardinality.ONE_TO_MANY,
                    inverseName = "Linked From"
                ),
            ),
        )

        whenever(definitionRepository.save(any<RelationshipDefinitionEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as RelationshipDefinitionEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }
        whenever(targetRuleRepository.saveAll(any<List<RelationshipTargetRuleEntity>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val entities = invocation.arguments[0] as List<RelationshipTargetRuleEntity>
            entities.map { if (it.id == null) it.copy(id = UUID.randomUUID()) else it }
        }

        val result = service.createRelationshipDefinition(workspaceId, sourceEntityTypeId, request)

        assertEquals(2, result.targetRules.size)

        verify(targetRuleRepository).saveAll(argThat<List<RelationshipTargetRuleEntity>> { size == 2 })
    }

    @Test
    fun `createRelationshipDefinition - polymorphic - saves definition with no rules`() {
        val request = SaveRelationshipDefinitionRequest(
            key = "related_any",
            id = UUID.randomUUID(),
            name = "Related Anything",
            iconType = IconType.LINK,
            iconColour = IconColour.NEUTRAL,
            allowPolymorphic = true,
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            targetRules = emptyList(),
        )

        whenever(definitionRepository.save(any<RelationshipDefinitionEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as RelationshipDefinitionEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }
        whenever(targetRuleRepository.saveAll(any<List<RelationshipTargetRuleEntity>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val entities = invocation.arguments[0] as List<RelationshipTargetRuleEntity>
            entities.map { if (it.id == null) it.copy(id = UUID.randomUUID()) else it }
        }

        val result = service.createRelationshipDefinition(workspaceId, sourceEntityTypeId, request)

        assertTrue(result.allowPolymorphic)
        assertTrue(result.targetRules.isEmpty())
    }

    @Test
    fun `createRelationshipDefinition - with semantic constraint - saves rule with constraint`() {
        val request = SaveRelationshipDefinitionRequest(
            key = "organizations",
            id = UUID.randomUUID(),
            name = "Organizations",
            iconType = IconType.LINK,
            iconColour = IconColour.NEUTRAL,
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            targetRules = listOf(
                SaveTargetRuleRequest(semanticTypeConstraint = SemanticGroup.OPERATIONAL, inverseName = "Organizations")
            ),
        )

        whenever(definitionRepository.save(any<RelationshipDefinitionEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as RelationshipDefinitionEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }
        whenever(targetRuleRepository.saveAll(any<List<RelationshipTargetRuleEntity>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val entities = invocation.arguments[0] as List<RelationshipTargetRuleEntity>
            entities.map { if (it.id == null) it.copy(id = UUID.randomUUID()) else it }
        }

        val result = service.createRelationshipDefinition(workspaceId, sourceEntityTypeId, request)

        assertEquals(1, result.targetRules.size)
        assertEquals(SemanticGroup.OPERATIONAL, result.targetRules[0].semanticTypeConstraint)
        assertNull(result.targetRules[0].targetEntityTypeId)
    }

    // ------ Update ------

    @Test
    fun `updateRelationshipDefinition - change name - updates definition`() {
        val defId = UUID.randomUUID()
        val existingEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Old Name",
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
        )
        val request = SaveRelationshipDefinitionRequest(
            key = "related",
            id = defId,
            name = "New Name",
            iconType = IconType.LINK,
            iconColour = IconColour.NEUTRAL,
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            targetRules = emptyList(),
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(existingEntity))
        whenever(definitionRepository.save(any<RelationshipDefinitionEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as RelationshipDefinitionEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }
        whenever(targetRuleRepository.findByRelationshipDefinitionId(defId)).thenReturn(emptyList())
        whenever(targetRuleRepository.saveAll(any<List<RelationshipTargetRuleEntity>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val entities = invocation.arguments[0] as List<RelationshipTargetRuleEntity>
            entities.map { if (it.id == null) it.copy(id = UUID.randomUUID()) else it }
        }

        val (result, impact) = service.updateRelationshipDefinition(workspaceId, defId, request)

        assertNull(impact)
        assertEquals("New Name", result!!.name)
        verify(definitionRepository).save(argThat<RelationshipDefinitionEntity> { name == "New Name" })
    }

    @Test
    fun `updateRelationshipDefinition - add target rule - saves new rule`() {
        val defId = UUID.randomUUID()
        val newTargetTypeId = UUID.randomUUID()
        val existingEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Related",
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
        )
        val request = SaveRelationshipDefinitionRequest(
            key = "related",
            id = defId,
            name = "Related",
            iconType = IconType.LINK,
            iconColour = IconColour.NEUTRAL,
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            targetRules = listOf(
                SaveTargetRuleRequest(targetEntityTypeId = newTargetTypeId, inverseName = "Related")
            ),
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(existingEntity))
        whenever(definitionRepository.save(any<RelationshipDefinitionEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as RelationshipDefinitionEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }
        whenever(targetRuleRepository.findByRelationshipDefinitionId(defId)).thenReturn(emptyList())
        whenever(targetRuleRepository.saveAll(any<List<RelationshipTargetRuleEntity>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val entities = invocation.arguments[0] as List<RelationshipTargetRuleEntity>
            entities.map { if (it.id == null) it.copy(id = UUID.randomUUID()) else it }
        }

        val (result, impact) = service.updateRelationshipDefinition(workspaceId, defId, request)

        assertNull(impact)
        assertEquals(1, result!!.targetRules.size)
        verify(targetRuleRepository).saveAll(argThat<List<RelationshipTargetRuleEntity>> { size == 1 })
    }

    @Test
    fun `updateRelationshipDefinition - remove target rule - deletes rule`() {
        val defId = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val existingEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Related",
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
        )
        val existingRule = EntityFactory.createTargetRuleEntity(
            id = ruleId,
            relationshipDefinitionId = defId,
            targetEntityTypeId = UUID.randomUUID(),
        )
        val request = SaveRelationshipDefinitionRequest(
            key = "related",
            id = defId,
            name = "Related",
            iconType = IconType.LINK,
            iconColour = IconColour.NEUTRAL,
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            targetRules = emptyList(),
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(existingEntity))
        whenever(definitionRepository.save(any<RelationshipDefinitionEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as RelationshipDefinitionEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }
        whenever(targetRuleRepository.findByRelationshipDefinitionId(defId)).thenReturn(listOf(existingRule))
        whenever(targetRuleRepository.saveAll(any<List<RelationshipTargetRuleEntity>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val entities = invocation.arguments[0] as List<RelationshipTargetRuleEntity>
            entities.map { if (it.id == null) it.copy(id = UUID.randomUUID()) else it }
        }

        val (result, impact) = service.updateRelationshipDefinition(workspaceId, defId, request)

        assertNull(impact)
        assertTrue(result!!.targetRules.isEmpty())
        verify(targetRuleRepository).deleteAll(argThat<List<RelationshipTargetRuleEntity>> { size == 1 && first().id == ruleId })
    }

    @Test
    fun `updateRelationshipDefinition - change cardinality default - updates definition`() {
        val defId = UUID.randomUUID()
        val existingEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Related",
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
        )
        val request = SaveRelationshipDefinitionRequest(
            key = "related",
            id = defId,
            name = "Related",
            iconType = IconType.LINK,
            iconColour = IconColour.NEUTRAL,
            cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY,
            targetRules = emptyList(),
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(existingEntity))
        whenever(definitionRepository.save(any<RelationshipDefinitionEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as RelationshipDefinitionEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }
        whenever(targetRuleRepository.findByRelationshipDefinitionId(defId)).thenReturn(emptyList())
        whenever(targetRuleRepository.saveAll(any<List<RelationshipTargetRuleEntity>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val entities = invocation.arguments[0] as List<RelationshipTargetRuleEntity>
            entities.map { if (it.id == null) it.copy(id = UUID.randomUUID()) else it }
        }

        val (result, impact) = service.updateRelationshipDefinition(workspaceId, defId, request)

        assertNull(impact)
        assertEquals(EntityRelationshipCardinality.ONE_TO_MANY, result!!.cardinalityDefault)
    }

    // ------ Delete ------

    @Test
    fun `deleteRelationshipDefinition - no instance data - soft deletes definition`() {
        val defId = UUID.randomUUID()
        val existingEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Related",
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(existingEntity))
        whenever(entityRelationshipRepository.countByDefinitionId(defId)).thenReturn(0)
        whenever(definitionRepository.save(any<RelationshipDefinitionEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as RelationshipDefinitionEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }

        service.deleteRelationshipDefinition(workspaceId, defId, impactConfirmed = false)

        verify(definitionRepository).save(argThat<RelationshipDefinitionEntity> { deleted })
        verify(targetRuleRepository).deleteByRelationshipDefinitionId(defId)
        verify(semanticMetadataService).deleteForTarget(
            entityTypeId = sourceEntityTypeId,
            targetType = SemanticMetadataTargetType.RELATIONSHIP,
            targetId = defId,
        )
    }

    @Test
    fun `deleteRelationshipDefinition - with instance data - returns impact count when not confirmed`() {
        val defId = UUID.randomUUID()
        val existingEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Related",
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(existingEntity))
        whenever(entityRelationshipRepository.countByDefinitionId(defId)).thenReturn(5)

        val result = service.deleteRelationshipDefinition(workspaceId, defId, impactConfirmed = false)

        assertNotNull(result)
        assertEquals(5L, result?.impactedLinkCount)
        verify(definitionRepository, never()).save(any())
    }

    @Test
    fun `deleteRelationshipDefinition - protected definition - throws exception`() {
        val defId = UUID.randomUUID()
        val existingEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Protected Relationship",
            protected = true,
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(existingEntity))

        assertThrows(IllegalStateException::class.java) {
            service.deleteRelationshipDefinition(workspaceId, defId, impactConfirmed = false)
        }
    }

    @Test
    fun `deleteRelationshipDefinition - soft-deletes associated links`() {
        val defId = UUID.randomUUID()
        val existingEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Related",
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(existingEntity))
        whenever(entityRelationshipRepository.countByDefinitionId(defId)).thenReturn(3)
        whenever(definitionRepository.save(any<RelationshipDefinitionEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as RelationshipDefinitionEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }

        service.deleteRelationshipDefinition(workspaceId, defId, impactConfirmed = true)

        verify(entityRelationshipRepository).softDeleteByDefinitionId(defId)
        verify(definitionRepository).save(argThat<RelationshipDefinitionEntity> { deleted })
    }

    // ------ Read ------

    @Test
    fun `getDefinitionsForEntityType - returns forward and inverse definitions`() {
        val entityTypeId = UUID.randomUUID()
        val forwardDefId = UUID.randomUUID()
        val inverseDefId = UUID.randomUUID()

        val forwardDef = EntityFactory.createRelationshipDefinitionEntity(
            id = forwardDefId,
            workspaceId = workspaceId,
            sourceEntityTypeId = entityTypeId,
            name = "Forward Relationship",
        )
        val inverseDef = EntityFactory.createRelationshipDefinitionEntity(
            id = inverseDefId,
            workspaceId = workspaceId,
            sourceEntityTypeId = UUID.randomUUID(), // different source
            name = "Origin Relationship",
        )
        val inverseRule = EntityFactory.createTargetRuleEntity(
            relationshipDefinitionId = inverseDefId,
            targetEntityTypeId = entityTypeId,
            inverseName = "Seen From Target",
        )

        whenever(definitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId))
            .thenReturn(listOf(forwardDef))
        whenever(targetRuleRepository.findByTargetEntityTypeId(entityTypeId))
            .thenReturn(listOf(inverseRule))
        whenever(definitionRepository.findAllById(listOf(inverseDefId)))
            .thenReturn(listOf(inverseDef))
        whenever(exclusionRepository.findByEntityTypeId(entityTypeId))
            .thenReturn(emptyList())
        whenever(targetRuleRepository.findByRelationshipDefinitionIdIn(any()))
            .thenReturn(listOf(inverseRule))
        whenever(exclusionRepository.findByRelationshipDefinitionIdIn(any()))
            .thenReturn(emptyList())

        val result = service.getDefinitionsForEntityType(workspaceId, entityTypeId)

        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "Forward Relationship" })
        assertTrue(result.any { it.name == "Origin Relationship" })
    }

    @Test
    fun `getDefinitionsForEntityType - includes inverse definitions via semantic group match`() {
        val entityTypeId = UUID.randomUUID()
        val semanticDefId = UUID.randomUUID()
        val otherSourceTypeId = UUID.randomUUID()

        val entityType = EntityFactory.createEntityType(
            id = entityTypeId,
            semanticGroup = SemanticGroup.CUSTOMER,
        )
        val semanticDef = EntityFactory.createRelationshipDefinitionEntity(
            id = semanticDefId,
            workspaceId = workspaceId,
            sourceEntityTypeId = otherSourceTypeId,
            name = "Semantic Relationship",
        )
        val semanticRule = EntityFactory.createTargetRuleEntity(
            relationshipDefinitionId = semanticDefId,
            targetEntityTypeId = null,
            semanticTypeConstraint = SemanticGroup.CUSTOMER,
        )

        // No explicit rules point at this entity type
        whenever(definitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId))
            .thenReturn(emptyList())
        whenever(targetRuleRepository.findByTargetEntityTypeId(entityTypeId))
            .thenReturn(emptyList())
        // Semantic group lookup finds the rule
        whenever(entityTypeRepository.findById(entityTypeId))
            .thenReturn(Optional.of(entityType))
        whenever(targetRuleRepository.findBySemanticTypeConstraint(SemanticGroup.CUSTOMER))
            .thenReturn(listOf(semanticRule))
        whenever(definitionRepository.findAllById(listOf(semanticDefId)))
            .thenReturn(listOf(semanticDef))
        whenever(exclusionRepository.findByEntityTypeId(entityTypeId))
            .thenReturn(emptyList())
        whenever(targetRuleRepository.findByRelationshipDefinitionIdIn(any()))
            .thenReturn(listOf(semanticRule))
        whenever(exclusionRepository.findByRelationshipDefinitionIdIn(any()))
            .thenReturn(emptyList())

        val result = service.getDefinitionsForEntityType(workspaceId, entityTypeId)

        assertEquals(1, result.size)
        assertEquals("Semantic Relationship", result[0].name)
    }

    @Test
    fun `getDefinitionsForEntityType - self-referential semantic rule - no duplicate definitions`() {
        val entityTypeId = UUID.randomUUID()
        val defId = UUID.randomUUID()

        // Entity type is both source AND matches its own semantic rule
        val entityType = EntityFactory.createEntityType(
            id = entityTypeId,
            semanticGroup = SemanticGroup.CUSTOMER,
        )
        val forwardDef = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = entityTypeId, // This type is the source
            name = "Customer Connections",
        )
        val semanticRule = EntityFactory.createTargetRuleEntity(
            relationshipDefinitionId = defId,
            targetEntityTypeId = null,
            semanticTypeConstraint = SemanticGroup.CUSTOMER, // Matches the source type's group
        )

        whenever(definitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId))
            .thenReturn(listOf(forwardDef))
        whenever(targetRuleRepository.findByTargetEntityTypeId(entityTypeId))
            .thenReturn(emptyList())
        whenever(entityTypeRepository.findById(entityTypeId))
            .thenReturn(Optional.of(entityType))
        whenever(targetRuleRepository.findBySemanticTypeConstraint(SemanticGroup.CUSTOMER))
            .thenReturn(listOf(semanticRule)) // Would match this definition as inverse too
        // findAllById should NOT be called because the defId is already in forward set
        whenever(exclusionRepository.findByEntityTypeId(entityTypeId))
            .thenReturn(emptyList())
        whenever(targetRuleRepository.findByRelationshipDefinitionIdIn(any()))
            .thenReturn(listOf(semanticRule))
        whenever(exclusionRepository.findByRelationshipDefinitionIdIn(any()))
            .thenReturn(emptyList())

        val result = service.getDefinitionsForEntityType(workspaceId, entityTypeId)

        // Should appear exactly once (as forward), not twice
        assertEquals(1, result.size)
        assertEquals("Customer Connections", result[0].name)
    }

    @Test
    fun `getDefinitionsForEntityType - filters out inverse definitions from other workspaces`() {
        val entityTypeId = UUID.randomUUID()
        val inverseDefId = UUID.randomUUID()
        val otherWorkspaceId = UUID.randomUUID()

        val otherWorkspaceDef = EntityFactory.createRelationshipDefinitionEntity(
            id = inverseDefId,
            workspaceId = otherWorkspaceId, // different workspace
            sourceEntityTypeId = UUID.randomUUID(),
            name = "Cross-Workspace",
        )
        val inverseRule = EntityFactory.createTargetRuleEntity(
            relationshipDefinitionId = inverseDefId,
            targetEntityTypeId = entityTypeId,
        )

        whenever(definitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId))
            .thenReturn(emptyList())
        whenever(targetRuleRepository.findByTargetEntityTypeId(entityTypeId))
            .thenReturn(listOf(inverseRule))
        whenever(definitionRepository.findAllById(listOf(inverseDefId)))
            .thenReturn(listOf(otherWorkspaceDef))
        whenever(exclusionRepository.findByEntityTypeId(entityTypeId))
            .thenReturn(emptyList())

        val result = service.getDefinitionsForEntityType(workspaceId, entityTypeId)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getDefinitionsForEntityType - UNCATEGORIZED type does not match semantic rules`() {
        val entityTypeId = UUID.randomUUID()

        val entityType = EntityFactory.createEntityType(
            id = entityTypeId,
            semanticGroup = SemanticGroup.UNCATEGORIZED,
        )

        whenever(definitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId))
            .thenReturn(emptyList())
        whenever(targetRuleRepository.findByTargetEntityTypeId(entityTypeId))
            .thenReturn(emptyList())
        whenever(entityTypeRepository.findById(entityTypeId))
            .thenReturn(Optional.of(entityType))

        val result = service.getDefinitionsForEntityType(workspaceId, entityTypeId)

        assertTrue(result.isEmpty())
        // Should never query semantic rules for UNCATEGORIZED
        verify(targetRuleRepository, never()).findBySemanticTypeConstraint(any())
    }
}
