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
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.SystemRelationshipType
import riven.core.enums.integration.SourceType
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.models.request.entity.type.SaveRelationshipDefinitionRequest
import riven.core.models.request.entity.type.SaveTargetRuleRequest
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.entity.RelationshipTargetRuleRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.EntityTypeSemanticMetadataService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import org.junit.jupiter.api.Nested
import riven.core.service.util.factory.entity.EntityFactory
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
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

        val sourceEntityType = EntityFactory.createEntityType(id = sourceEntityTypeId, workspaceId = workspaceId)
        val targetEntityType = EntityFactory.createEntityType(id = targetTypeId, workspaceId = workspaceId)
        whenever(entityTypeRepository.findById(sourceEntityTypeId)).thenReturn(Optional.of(sourceEntityType))
        whenever(entityTypeRepository.findById(targetTypeId)).thenReturn(Optional.of(targetEntityType))
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

        val sourceEntityType = EntityFactory.createEntityType(id = sourceEntityTypeId, workspaceId = workspaceId)
        val targetEntity1 = EntityFactory.createEntityType(id = targetTypeId1, workspaceId = workspaceId)
        val targetEntity2 = EntityFactory.createEntityType(id = targetTypeId2, workspaceId = workspaceId)
        whenever(entityTypeRepository.findById(sourceEntityTypeId)).thenReturn(Optional.of(sourceEntityType))
        whenever(entityTypeRepository.findById(targetTypeId1)).thenReturn(Optional.of(targetEntity1))
        whenever(entityTypeRepository.findById(targetTypeId2)).thenReturn(Optional.of(targetEntity2))
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
        whenever(targetRuleRepository.findByRelationshipDefinitionIdIn(any()))
            .thenReturn(listOf(inverseRule))

        val result = service.getDefinitionsForEntityType(workspaceId, entityTypeId)

        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "Forward Relationship" })
        assertTrue(result.any { it.name == "Origin Relationship" })
    }

    // ------ Remove Target Rule ------

    @Test
    fun `removeTargetRule - last rule with links - impactConfirmed false - returns definition impact`() {
        val defId = UUID.randomUUID()
        val targetEntityTypeId = UUID.randomUUID()
        val definitionEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Has Contacts",
        )
        val rule = EntityFactory.createTargetRuleEntity(
            relationshipDefinitionId = defId,
            targetEntityTypeId = targetEntityTypeId,
        )
        val entityType = mock<EntityTypeEntity> {
            on { this.workspaceId } doReturn workspaceId
        }

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definitionEntity))
        whenever(entityTypeRepository.findById(targetEntityTypeId)).thenReturn(Optional.of(entityType))
        whenever(targetRuleRepository.findByRelationshipDefinitionId(defId)).thenReturn(listOf(rule))
        whenever(entityRelationshipRepository.countByDefinitionId(defId)).thenReturn(7)

        val result = service.removeTargetRule(workspaceId, defId, targetEntityTypeId, impactConfirmed = false)

        assertNotNull(result)
        assertEquals(defId, result!!.definitionId)
        assertEquals("Has Contacts", result.definitionName)
        assertEquals(7L, result.impactedLinkCount)
        assertTrue(result.deletesDefinition)

        // Should NOT have deleted anything yet
        verify(definitionRepository, never()).save(argThat<RelationshipDefinitionEntity> { deleted })
        verify(targetRuleRepository, never()).delete(any())
    }

    @Test
    fun `removeTargetRule - last rule with links - impactConfirmed true - deletes entire definition`() {
        val defId = UUID.randomUUID()
        val targetEntityTypeId = UUID.randomUUID()
        val definitionEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Has Contacts",
        )
        val rule = EntityFactory.createTargetRuleEntity(
            relationshipDefinitionId = defId,
            targetEntityTypeId = targetEntityTypeId,
        )
        val entityType = mock<EntityTypeEntity> {
            on { this.workspaceId } doReturn workspaceId
        }

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definitionEntity))
        whenever(entityTypeRepository.findById(targetEntityTypeId)).thenReturn(Optional.of(entityType))
        whenever(targetRuleRepository.findByRelationshipDefinitionId(defId)).thenReturn(listOf(rule))
        whenever(entityRelationshipRepository.countByDefinitionId(defId)).thenReturn(7)
        whenever(definitionRepository.save(any<RelationshipDefinitionEntity>())).thenAnswer { it.arguments[0] }

        val result = service.removeTargetRule(workspaceId, defId, targetEntityTypeId, impactConfirmed = true)

        assertNull(result)

        // Full definition deletion: soft-delete links, soft-delete definition, hard-delete rules
        verify(entityRelationshipRepository).softDeleteByDefinitionId(defId)
        verify(definitionRepository).save(argThat<RelationshipDefinitionEntity> { deleted })
        verify(targetRuleRepository).deleteByRelationshipDefinitionId(defId)
    }

    @Test
    fun `removeTargetRule - last rule no links - deletes definition without impact`() {
        val defId = UUID.randomUUID()
        val targetEntityTypeId = UUID.randomUUID()
        val definitionEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Has Contacts",
        )
        val rule = EntityFactory.createTargetRuleEntity(
            relationshipDefinitionId = defId,
            targetEntityTypeId = targetEntityTypeId,
        )
        val entityType = mock<EntityTypeEntity> {
            on { this.workspaceId } doReturn workspaceId
        }

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definitionEntity))
        whenever(entityTypeRepository.findById(targetEntityTypeId)).thenReturn(Optional.of(entityType))
        whenever(targetRuleRepository.findByRelationshipDefinitionId(defId)).thenReturn(listOf(rule))
        whenever(entityRelationshipRepository.countByDefinitionId(defId)).thenReturn(0)
        whenever(definitionRepository.save(any<RelationshipDefinitionEntity>())).thenAnswer { it.arguments[0] }

        val result = service.removeTargetRule(workspaceId, defId, targetEntityTypeId, impactConfirmed = false)

        assertNull(result)
        verify(definitionRepository).save(argThat<RelationshipDefinitionEntity> { deleted })
        verify(targetRuleRepository).deleteByRelationshipDefinitionId(defId)
    }

    @Test
    fun `removeTargetRule - multiple rules with links - impactConfirmed false - returns rule impact`() {
        val defId = UUID.randomUUID()
        val targetEntityTypeId = UUID.randomUUID()
        val otherTargetTypeId = UUID.randomUUID()
        val definitionEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Has Contacts",
        )
        val targetRule = EntityFactory.createTargetRuleEntity(
            relationshipDefinitionId = defId,
            targetEntityTypeId = targetEntityTypeId,
        )
        val otherRule = EntityFactory.createTargetRuleEntity(
            relationshipDefinitionId = defId,
            targetEntityTypeId = otherTargetTypeId,
        )
        val entityType = mock<EntityTypeEntity> {
            on { this.workspaceId } doReturn workspaceId
        }

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definitionEntity))
        whenever(entityTypeRepository.findById(targetEntityTypeId)).thenReturn(Optional.of(entityType))
        whenever(targetRuleRepository.findByRelationshipDefinitionId(defId)).thenReturn(listOf(targetRule, otherRule))
        whenever(entityRelationshipRepository.countByDefinitionIdAndTargetEntityTypeId(defId, targetEntityTypeId)).thenReturn(3)

        val result = service.removeTargetRule(workspaceId, defId, targetEntityTypeId, impactConfirmed = false)

        assertNotNull(result)
        assertEquals(defId, result!!.definitionId)
        assertEquals(3L, result.impactedLinkCount)
        assertFalse(result.deletesDefinition)

        // Should NOT have deleted anything yet
        verify(targetRuleRepository, never()).delete(any())
        verify(definitionRepository, never()).save(argThat<RelationshipDefinitionEntity> { deleted })
    }

    @Test
    fun `removeTargetRule - multiple rules with links - impactConfirmed true - deletes only rule and its links`() {
        val defId = UUID.randomUUID()
        val targetEntityTypeId = UUID.randomUUID()
        val otherTargetTypeId = UUID.randomUUID()
        val definitionEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Has Contacts",
        )
        val targetRule = EntityFactory.createTargetRuleEntity(
            relationshipDefinitionId = defId,
            targetEntityTypeId = targetEntityTypeId,
        )
        val otherRule = EntityFactory.createTargetRuleEntity(
            relationshipDefinitionId = defId,
            targetEntityTypeId = otherTargetTypeId,
        )
        val entityType = mock<EntityTypeEntity> {
            on { this.workspaceId } doReturn workspaceId
        }

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definitionEntity))
        whenever(entityTypeRepository.findById(targetEntityTypeId)).thenReturn(Optional.of(entityType))
        whenever(targetRuleRepository.findByRelationshipDefinitionId(defId)).thenReturn(listOf(targetRule, otherRule))
        whenever(entityRelationshipRepository.countByDefinitionIdAndTargetEntityTypeId(defId, targetEntityTypeId)).thenReturn(3)

        val result = service.removeTargetRule(workspaceId, defId, targetEntityTypeId, impactConfirmed = true)

        assertNull(result)

        // Only deletes the target rule and its links — definition survives
        verify(entityRelationshipRepository).softDeleteByDefinitionIdAndTargetEntityTypeId(defId, targetEntityTypeId)
        verify(targetRuleRepository).delete(targetRule)
        verify(definitionRepository, never()).save(argThat<RelationshipDefinitionEntity> { deleted })
    }

    @Test
    fun `removeTargetRule - multiple rules no links - deletes rule without impact`() {
        val defId = UUID.randomUUID()
        val targetEntityTypeId = UUID.randomUUID()
        val otherTargetTypeId = UUID.randomUUID()
        val definitionEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Has Contacts",
        )
        val targetRule = EntityFactory.createTargetRuleEntity(
            relationshipDefinitionId = defId,
            targetEntityTypeId = targetEntityTypeId,
        )
        val otherRule = EntityFactory.createTargetRuleEntity(
            relationshipDefinitionId = defId,
            targetEntityTypeId = otherTargetTypeId,
        )
        val entityType = mock<EntityTypeEntity> {
            on { this.workspaceId } doReturn workspaceId
        }

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definitionEntity))
        whenever(entityTypeRepository.findById(targetEntityTypeId)).thenReturn(Optional.of(entityType))
        whenever(targetRuleRepository.findByRelationshipDefinitionId(defId)).thenReturn(listOf(targetRule, otherRule))
        whenever(entityRelationshipRepository.countByDefinitionIdAndTargetEntityTypeId(defId, targetEntityTypeId)).thenReturn(0)

        val result = service.removeTargetRule(workspaceId, defId, targetEntityTypeId, impactConfirmed = false)

        assertNull(result)
        verify(targetRuleRepository).delete(targetRule)
        verify(definitionRepository, never()).save(argThat<RelationshipDefinitionEntity> { deleted })
    }

    @Test
    fun `removeTargetRule - no matching rule - throws NotFoundException`() {
        val defId = UUID.randomUUID()
        val targetEntityTypeId = UUID.randomUUID()
        val definitionEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Has Contacts",
        )
        val unrelatedRule = EntityFactory.createTargetRuleEntity(
            relationshipDefinitionId = defId,
            targetEntityTypeId = UUID.randomUUID(), // different type
        )
        val entityType = mock<EntityTypeEntity> {
            on { this.workspaceId } doReturn workspaceId
        }

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definitionEntity))
        whenever(entityTypeRepository.findById(targetEntityTypeId)).thenReturn(Optional.of(entityType))
        whenever(targetRuleRepository.findByRelationshipDefinitionId(defId)).thenReturn(listOf(unrelatedRule))

        assertThrows(riven.core.exceptions.NotFoundException::class.java) {
            service.removeTargetRule(workspaceId, defId, targetEntityTypeId, impactConfirmed = false)
        }
    }

    @Test
    fun `removeTargetRule - source entity type as target - throws IllegalArgumentException`() {
        val defId = UUID.randomUUID()
        val definitionEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Has Contacts",
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definitionEntity))

        assertThrows(IllegalArgumentException::class.java) {
            service.removeTargetRule(workspaceId, defId, sourceEntityTypeId, impactConfirmed = false)
        }
    }

    // ------ Read ------

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
        val result = service.getDefinitionsForEntityType(workspaceId, entityTypeId)

        assertTrue(result.isEmpty())
    }

    // ------ System Relationship Definitions ------

    @Test
    fun `getOrCreateSystemDefinition - creates ATTACHMENT when missing`() {
        val noteTypeId = UUID.randomUUID()
        val noteEntityType = EntityFactory.createEntityType(id = noteTypeId, workspaceId = workspaceId, key = "note")
        whenever(definitionRepository.findBySourceEntityTypeIdAndSystemType(noteTypeId, SystemRelationshipType.ATTACHMENT))
            .thenReturn(Optional.empty())
        whenever(entityTypeRepository.findById(noteTypeId)).thenReturn(Optional.of(noteEntityType))
        whenever(definitionRepository.save(any<RelationshipDefinitionEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as RelationshipDefinitionEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }

        val result = service.getOrCreateSystemDefinition(workspaceId, noteTypeId, SystemRelationshipType.ATTACHMENT)

        assertEquals(SystemRelationshipType.ATTACHMENT, result.systemType)
        assertEquals("Attachments", result.name)
        assertTrue(result.protected)
    }

    @Test
    fun `getOrCreateSystemDefinition - returns existing when present`() {
        val noteTypeId = UUID.randomUUID()
        val existingId = UUID.randomUUID()
        val existing = EntityFactory.createRelationshipDefinitionEntity(
            id = existingId,
            workspaceId = workspaceId,
            sourceEntityTypeId = noteTypeId,
            name = "Attachments",
            systemType = SystemRelationshipType.ATTACHMENT,
        )
        whenever(definitionRepository.findBySourceEntityTypeIdAndSystemType(noteTypeId, SystemRelationshipType.ATTACHMENT))
            .thenReturn(Optional.of(existing))

        val result = service.getOrCreateSystemDefinition(workspaceId, noteTypeId, SystemRelationshipType.ATTACHMENT)

        assertEquals(existingId, result.id)
        verify(definitionRepository, never()).save(any<RelationshipDefinitionEntity>())
    }

    /**
     * Regression test for r3166515144: getOrCreateSystemDefinition must reject existing
     * definitions whose `workspace_id` differs from the caller's `workspaceId`.
     *
     * Synthesises a definition row owned by a different workspace and asserts that the
     * lookup throws rather than returning the foreign row. @PreAuthorize would normally
     * stop this on the public path, but the unit test forces the path to lock in the
     * inner workspace guard so a future change to the lookup query does not silently
     * regress.
     */
    @Test
    fun `getOrCreateSystemDefinition - rejects existing row from a different workspace`() {
        val noteTypeId = UUID.randomUUID()
        val foreignWorkspaceId = UUID.randomUUID()
        val foreignDefinition = EntityFactory.createRelationshipDefinitionEntity(
            id = UUID.randomUUID(),
            workspaceId = foreignWorkspaceId,
            sourceEntityTypeId = noteTypeId,
            name = "Attachments",
            systemType = SystemRelationshipType.ATTACHMENT,
        )
        whenever(definitionRepository.findBySourceEntityTypeIdAndSystemType(noteTypeId, SystemRelationshipType.ATTACHMENT))
            .thenReturn(Optional.of(foreignDefinition))

        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            service.getOrCreateSystemDefinition(workspaceId, noteTypeId, SystemRelationshipType.ATTACHMENT)
        }
        verify(definitionRepository, never()).save(any<RelationshipDefinitionEntity>())
    }

    // ------ Readonly Guard Tests ------

    @Nested
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
    inner class ReadonlyGuards {

        /**
         * Verifies that createRelationshipDefinition throws IllegalArgumentException
         * when the source entity type is readonly.
         */
        @Test
        fun `createRelationshipDefinition - readonly source entity type - throws IllegalArgumentException`() {
            val sourceTypeId = UUID.randomUUID()
            val sourceEntityType = EntityFactory.createEntityType(
                id = sourceTypeId,
                workspaceId = workspaceId,
                readonly = true,
                sourceType = SourceType.INTEGRATION,
            )

            whenever(entityTypeRepository.findById(sourceTypeId)).thenReturn(Optional.of(sourceEntityType))

            val request = SaveRelationshipDefinitionRequest(
                key = "test_entity",
                name = "Has Contacts",
                iconType = IconType.LINK,
                iconColour = IconColour.NEUTRAL,
                cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY,
                targetRules = listOf(
                    SaveTargetRuleRequest(targetEntityTypeId = UUID.randomUUID(), inverseName = "Belongs To")
                ),
            )

            val exception = assertThrows(IllegalArgumentException::class.java) {
                service.createRelationshipDefinition(workspaceId, sourceTypeId, request)
            }

            assertTrue(exception.message!!.contains("readonly"))
            verify(definitionRepository, never()).save(any<RelationshipDefinitionEntity>())
        }
    }

    /**
     * Regression for r3172127159: @PreAuthorize on getOrCreateSystemDefinition must reject
     * callers whose JWT lacks the requested workspace's role authority. Pairs with the inner
     * workspace check from r3166515144 — the @PreAuthorize gate is the first stop, the inner
     * `require(def.workspaceId == workspaceId)` is the safety net once past authorization.
     */
    @Nested
    @WithUserPersona(
        userId = "11111111-1111-1111-1111-111111111111",
        email = "stranger@test.com",
        displayName = "Stranger",
        roles = [
            WorkspaceRole(
                workspaceId = "00000000-0000-0000-0000-000000000000",
                role = WorkspaceRoles.OWNER,
            ),
        ],
    )
    inner class SystemDefinitionAuthGuard {

        @Test
        fun `getOrCreateSystemDefinition - persona without workspace authority - throws AccessDeniedException`() {
            val noteTypeId = UUID.randomUUID()
            org.junit.jupiter.api.assertThrows<org.springframework.security.access.AccessDeniedException> {
                service.getOrCreateSystemDefinition(workspaceId, noteTypeId, SystemRelationshipType.ATTACHMENT)
            }
        }
    }
}
