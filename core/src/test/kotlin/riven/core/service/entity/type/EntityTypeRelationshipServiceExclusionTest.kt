package riven.core.service.entity.type

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.entity.RelationshipDefinitionExclusionEntity
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.workspace.WorkspaceRoles
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
import riven.core.exceptions.NotFoundException
import riven.core.service.util.factory.entity.EntityFactory
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        EntityTypeRelationshipServiceExclusionTest.TestConfig::class,
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
class EntityTypeRelationshipServiceExclusionTest : BaseServiceTest() {

    @Configuration
    class TestConfig

    @MockitoBean
    private lateinit var definitionRepository: RelationshipDefinitionRepository

    @MockitoBean
    private lateinit var targetRuleRepository: RelationshipTargetRuleRepository

    @MockitoBean
    private lateinit var exclusionRepository: RelationshipDefinitionExclusionRepository

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
    private val targetEntityTypeId = UUID.randomUUID()

    private val targetEntityType = EntityFactory.createEntityType(
        id = targetEntityTypeId,
        workspaceId = workspaceId,
    )

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
        // Default: target entity type belongs to the same workspace
        whenever(entityTypeRepository.findById(targetEntityTypeId)).thenReturn(Optional.of(targetEntityType))
    }

    // ------ excludeEntityTypeFromDefinition ------

    @Test
    fun `excludeEntityTypeFromDefinition - explicit target rule - deletes rule instead of creating exclusion`() {
        val defId = UUID.randomUUID()
        val ruleId = UUID.randomUUID()
        val definition = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
        )
        val rule = EntityFactory.createTargetRuleEntity(
            id = ruleId,
            relationshipDefinitionId = defId,
            targetEntityTypeId = targetEntityTypeId,
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definition))
        whenever(targetRuleRepository.findByRelationshipDefinitionId(defId)).thenReturn(listOf(rule))
        whenever(entityRelationshipRepository.countByDefinitionIdAndTargetEntityTypeId(defId, targetEntityTypeId)).thenReturn(0)

        val result = service.excludeEntityTypeFromDefinition(workspaceId, defId, targetEntityTypeId, impactConfirmed = false)

        assertNull(result)
        verify(targetRuleRepository).delete(rule)
        verify(exclusionRepository, never()).save(any())
    }

    @Test
    fun `excludeEntityTypeFromDefinition - semantic match - creates exclusion record`() {
        val defId = UUID.randomUUID()
        val definition = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            allowPolymorphic = true,
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definition))
        whenever(targetRuleRepository.findByRelationshipDefinitionId(defId)).thenReturn(emptyList())
        whenever(entityRelationshipRepository.countByDefinitionIdAndTargetEntityTypeId(defId, targetEntityTypeId)).thenReturn(0)
        whenever(exclusionRepository.save(any<RelationshipDefinitionExclusionEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as RelationshipDefinitionExclusionEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }

        val result = service.excludeEntityTypeFromDefinition(workspaceId, defId, targetEntityTypeId, impactConfirmed = false)

        assertNull(result)
        verify(exclusionRepository).save(argThat<RelationshipDefinitionExclusionEntity> {
            relationshipDefinitionId == defId && entityTypeId == targetEntityTypeId
        })
        verify(targetRuleRepository, never()).delete(any())
    }

    @Test
    fun `excludeEntityTypeFromDefinition - explicit rule and semantic match - deletes rule and creates exclusion`() {
        val defId = UUID.randomUUID()
        val definition = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
        )
        val explicitRule = EntityFactory.createTargetRuleEntity(
            relationshipDefinitionId = defId,
            targetEntityTypeId = targetEntityTypeId,
        )
        val semanticRule = EntityFactory.createTargetRuleEntity(
            relationshipDefinitionId = defId,
            targetEntityTypeId = null,
            semanticTypeConstraint = SemanticGroup.CUSTOMER,
        )
        val entityType = EntityFactory.createEntityType(
            id = targetEntityTypeId,
            workspaceId = workspaceId,
            semanticGroup = SemanticGroup.CUSTOMER,
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definition))
        whenever(targetRuleRepository.findByRelationshipDefinitionId(defId)).thenReturn(listOf(explicitRule, semanticRule))
        whenever(entityRelationshipRepository.countByDefinitionIdAndTargetEntityTypeId(defId, targetEntityTypeId)).thenReturn(0)
        whenever(entityTypeRepository.findById(targetEntityTypeId)).thenReturn(Optional.of(entityType))
        whenever(exclusionRepository.save(any<RelationshipDefinitionExclusionEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as RelationshipDefinitionExclusionEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }

        val result = service.excludeEntityTypeFromDefinition(workspaceId, defId, targetEntityTypeId, impactConfirmed = false)

        assertNull(result)
        verify(targetRuleRepository).delete(explicitRule)
        verify(exclusionRepository).save(argThat<RelationshipDefinitionExclusionEntity> {
            relationshipDefinitionId == defId && entityTypeId == targetEntityTypeId
        })
    }

    @Test
    fun `excludeEntityTypeFromDefinition - explicit rule only no semantic match - deletes rule without exclusion`() {
        val defId = UUID.randomUUID()
        val definition = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
        )
        val explicitRule = EntityFactory.createTargetRuleEntity(
            relationshipDefinitionId = defId,
            targetEntityTypeId = targetEntityTypeId,
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definition))
        whenever(targetRuleRepository.findByRelationshipDefinitionId(defId)).thenReturn(listOf(explicitRule))
        whenever(entityRelationshipRepository.countByDefinitionIdAndTargetEntityTypeId(defId, targetEntityTypeId)).thenReturn(0)

        val result = service.excludeEntityTypeFromDefinition(workspaceId, defId, targetEntityTypeId, impactConfirmed = false)

        assertNull(result)
        verify(targetRuleRepository).delete(explicitRule)
        verify(exclusionRepository, never()).save(any())
    }

    @Test
    fun `excludeEntityTypeFromDefinition - with instance data - returns impact when not confirmed`() {
        val defId = UUID.randomUUID()
        val definition = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Related",
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definition))
        whenever(targetRuleRepository.findByRelationshipDefinitionId(defId)).thenReturn(emptyList())
        whenever(entityRelationshipRepository.countByDefinitionIdAndTargetEntityTypeId(defId, targetEntityTypeId)).thenReturn(5)

        val result = service.excludeEntityTypeFromDefinition(workspaceId, defId, targetEntityTypeId, impactConfirmed = false)

        assertNotNull(result)
        assertEquals(5L, result?.impactedLinkCount)
        assertEquals("Related", result?.definitionName)
        verify(exclusionRepository, never()).save(any())
    }

    @Test
    fun `excludeEntityTypeFromDefinition - with instance data confirmed - soft-deletes links and creates exclusion`() {
        val defId = UUID.randomUUID()
        val definition = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            allowPolymorphic = true,
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definition))
        whenever(entityRelationshipRepository.countByDefinitionIdAndTargetEntityTypeId(defId, targetEntityTypeId)).thenReturn(3)
        whenever(exclusionRepository.save(any<RelationshipDefinitionExclusionEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as RelationshipDefinitionExclusionEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }

        val result = service.excludeEntityTypeFromDefinition(workspaceId, defId, targetEntityTypeId, impactConfirmed = true)

        assertNull(result)
        verify(entityRelationshipRepository).softDeleteByDefinitionIdAndTargetEntityTypeId(defId, targetEntityTypeId)
        verify(exclusionRepository).save(any<RelationshipDefinitionExclusionEntity>())
    }

    @Test
    fun `excludeEntityTypeFromDefinition - source entity type - throws exception`() {
        val defId = UUID.randomUUID()
        val definition = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definition))

        assertThrows(IllegalArgumentException::class.java) {
            service.excludeEntityTypeFromDefinition(workspaceId, defId, sourceEntityTypeId, impactConfirmed = false)
        }
    }

    @Test
    fun `excludeEntityTypeFromDefinition - concurrent duplicate - handles gracefully`() {
        val defId = UUID.randomUUID()
        val definition = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            allowPolymorphic = true,
        )

        val uniqueViolation = DataIntegrityViolationException(
            "could not execute statement",
            org.hibernate.exception.ConstraintViolationException(
                "duplicate key value violates unique constraint \"uq_exclusion_def_type\"",
                java.sql.SQLException("uq_exclusion_def_type"),
                "uq_exclusion_def_type",
            ),
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definition))
        whenever(entityRelationshipRepository.countByDefinitionIdAndTargetEntityTypeId(defId, targetEntityTypeId)).thenReturn(0)
        whenever(exclusionRepository.save(any<RelationshipDefinitionExclusionEntity>()))
            .thenThrow(uniqueViolation)

        val result = service.excludeEntityTypeFromDefinition(workspaceId, defId, targetEntityTypeId, impactConfirmed = false)

        assertNull(result)
        verify(exclusionRepository).save(any<RelationshipDefinitionExclusionEntity>())
    }

    @Test
    fun `excludeEntityTypeFromDefinition - non-unique integrity violation - rethrows`() {
        val defId = UUID.randomUUID()
        val definition = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            allowPolymorphic = true,
        )

        val fkViolation = DataIntegrityViolationException(
            "could not execute statement",
            org.hibernate.exception.ConstraintViolationException(
                "insert or update on table violates foreign key constraint",
                java.sql.SQLException("fk_some_constraint"),
                "fk_some_constraint",
            ),
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definition))
        whenever(entityRelationshipRepository.countByDefinitionIdAndTargetEntityTypeId(defId, targetEntityTypeId)).thenReturn(0)
        whenever(exclusionRepository.save(any<RelationshipDefinitionExclusionEntity>()))
            .thenThrow(fkViolation)

        assertThrows(DataIntegrityViolationException::class.java) {
            service.excludeEntityTypeFromDefinition(workspaceId, defId, targetEntityTypeId, impactConfirmed = false)
        }
    }

    @Test
    fun `excludeEntityTypeFromDefinition - entity type from different workspace - throws NotFoundException`() {
        val defId = UUID.randomUUID()
        val otherWorkspaceEntityTypeId = UUID.randomUUID()
        val otherWorkspaceEntityType = EntityFactory.createEntityType(
            id = otherWorkspaceEntityTypeId,
            workspaceId = UUID.randomUUID(), // different workspace
        )
        val definition = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definition))
        whenever(entityTypeRepository.findById(otherWorkspaceEntityTypeId)).thenReturn(Optional.of(otherWorkspaceEntityType))

        assertThrows(NotFoundException::class.java) {
            service.excludeEntityTypeFromDefinition(workspaceId, defId, otherWorkspaceEntityTypeId, impactConfirmed = false)
        }
    }

    // ------ removeExclusion ------

    @Test
    fun `removeExclusion - deletes exclusion record and restores soft-deleted links`() {
        val defId = UUID.randomUUID()
        val definition = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
        )
        val exclusion = EntityFactory.createExclusionEntity(
            relationshipDefinitionId = defId,
            entityTypeId = targetEntityTypeId,
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definition))
        whenever(exclusionRepository.findByRelationshipDefinitionIdAndEntityTypeId(defId, targetEntityTypeId))
            .thenReturn(Optional.of(exclusion))

        service.removeExclusion(workspaceId, defId, targetEntityTypeId)

        verify(exclusionRepository).deleteByRelationshipDefinitionIdAndEntityTypeId(defId, targetEntityTypeId)
        verify(entityRelationshipRepository).restoreByDefinitionIdAndTargetEntityTypeId(defId, targetEntityTypeId)
    }

    @Test
    fun `removeExclusion - no exclusion exists - does not restore links`() {
        val defId = UUID.randomUUID()
        val definition = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definition))
        whenever(exclusionRepository.findByRelationshipDefinitionIdAndEntityTypeId(defId, targetEntityTypeId))
            .thenReturn(Optional.empty())

        service.removeExclusion(workspaceId, defId, targetEntityTypeId)

        verify(exclusionRepository, never()).deleteByRelationshipDefinitionIdAndEntityTypeId(any(), any())
        verify(entityRelationshipRepository, never()).restoreByDefinitionIdAndTargetEntityTypeId(any(), any())
    }

    @Test
    fun `removeExclusion - entity type from different workspace - throws NotFoundException`() {
        val defId = UUID.randomUUID()
        val otherWorkspaceEntityTypeId = UUID.randomUUID()
        val otherWorkspaceEntityType = EntityFactory.createEntityType(
            id = otherWorkspaceEntityTypeId,
            workspaceId = UUID.randomUUID(), // different workspace
        )
        val definition = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(definition))
        whenever(entityTypeRepository.findById(otherWorkspaceEntityTypeId)).thenReturn(Optional.of(otherWorkspaceEntityType))

        assertThrows(NotFoundException::class.java) {
            service.removeExclusion(workspaceId, defId, otherWorkspaceEntityTypeId)
        }
    }

    // ------ getDefinitionsForEntityType with exclusions ------

    @Test
    fun `getDefinitionsForEntityType - filters out excluded inverse definitions`() {
        val entityTypeId = UUID.randomUUID()
        val forwardDefId = UUID.randomUUID()
        val inverseDefId = UUID.randomUUID()
        val excludedDefId = UUID.randomUUID()

        val forwardDef = EntityFactory.createRelationshipDefinitionEntity(
            id = forwardDefId,
            workspaceId = workspaceId,
            sourceEntityTypeId = entityTypeId,
            name = "Forward",
        )
        val inverseDef = EntityFactory.createRelationshipDefinitionEntity(
            id = inverseDefId,
            workspaceId = workspaceId,
            sourceEntityTypeId = UUID.randomUUID(),
            name = "Inverse Visible",
        )
        val excludedDef = EntityFactory.createRelationshipDefinitionEntity(
            id = excludedDefId,
            workspaceId = workspaceId,
            sourceEntityTypeId = UUID.randomUUID(),
            name = "Excluded Inverse",
        )
        val inverseRule = EntityFactory.createTargetRuleEntity(
            relationshipDefinitionId = inverseDefId,
            targetEntityTypeId = entityTypeId,
        )
        val excludedRule = EntityFactory.createTargetRuleEntity(
            relationshipDefinitionId = excludedDefId,
            targetEntityTypeId = entityTypeId,
        )
        val exclusion = EntityFactory.createExclusionEntity(
            relationshipDefinitionId = excludedDefId,
            entityTypeId = entityTypeId,
        )

        whenever(definitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId))
            .thenReturn(listOf(forwardDef))
        whenever(targetRuleRepository.findByTargetEntityTypeId(entityTypeId))
            .thenReturn(listOf(inverseRule, excludedRule))
        whenever(definitionRepository.findAllById(any()))
            .thenReturn(listOf(inverseDef, excludedDef))
        whenever(exclusionRepository.findByEntityTypeId(entityTypeId))
            .thenReturn(listOf(exclusion))
        whenever(targetRuleRepository.findByRelationshipDefinitionIdIn(any()))
            .thenReturn(listOf(inverseRule, excludedRule))
        whenever(exclusionRepository.findByRelationshipDefinitionIdIn(any()))
            .thenReturn(listOf(exclusion))

        val result = service.getDefinitionsForEntityType(workspaceId, entityTypeId)

        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "Forward" })
        assertTrue(result.any { it.name == "Inverse Visible" })
        assertFalse(result.any { it.name == "Excluded Inverse" })
    }

    // ------ Cleanup on definition deletion ------

    @Test
    fun `deleteRelationshipDefinition - cleans up exclusions`() {
        val defId = UUID.randomUUID()
        val existingEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Related",
        )

        whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(existingEntity))
        whenever(entityRelationshipRepository.countByDefinitionId(defId)).thenReturn(0)
        whenever(definitionRepository.save(any())).thenAnswer { it.arguments[0] }

        service.deleteRelationshipDefinition(workspaceId, defId, impactConfirmed = false)

        verify(exclusionRepository).deleteByRelationshipDefinitionId(defId)
    }
}
