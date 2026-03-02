package riven.core.service.entity

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityRelationshipEntity
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.entity.SystemRelationshipType
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.exceptions.InvalidRelationshipException
import riven.core.models.common.Icon
import riven.core.models.entity.RelationshipDefinition
import riven.core.models.entity.RelationshipTargetRule
import riven.core.enums.common.validation.SchemaType
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.projection.entity.SemanticGroupProjection
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.entity.RelationshipTargetRuleRepository
import riven.core.service.auth.AuthTokenService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.entity.EntityFactory
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        EntityRelationshipServiceTest.TestConfig::class,
        EntityRelationshipService::class,
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
class EntityRelationshipServiceTest : BaseServiceTest() {

    @Configuration
    class TestConfig

    @MockitoBean
    private lateinit var entityRelationshipRepository: EntityRelationshipRepository

    @MockitoBean
    private lateinit var entityRepository: EntityRepository

    @MockitoBean
    private lateinit var entityTypeRepository: EntityTypeRepository

    @MockitoBean
    private lateinit var definitionRepository: RelationshipDefinitionRepository

    @MockitoBean
    private lateinit var targetRuleRepository: RelationshipTargetRuleRepository

    @MockitoBean
    private lateinit var entityTypeRelationshipService: riven.core.service.entity.type.EntityTypeRelationshipService

    @MockitoBean
    private lateinit var activityService: riven.core.service.activity.ActivityService

    @Autowired
    private lateinit var service: EntityRelationshipService

    private val sourceEntityId = UUID.randomUUID()
    private val sourceEntityTypeId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        reset(
            entityRelationshipRepository,
            entityRepository,
            entityTypeRepository,
            definitionRepository,
            targetRuleRepository,
        )

        // Default: resolve any entity type IDs to UNCATEGORIZED semantic group
        whenever(entityTypeRepository.findSemanticGroupsByIds(any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val ids = invocation.arguments[0] as Collection<UUID>
            ids.map { typeId -> mockSemanticGroupProjection(typeId, SemanticGroup.UNCATEGORIZED) }
        }
    }

    // ------ Helper builders ------

    private fun buildDefinition(
        id: UUID = UUID.randomUUID(),
        sourceEntityTypeId: UUID = this.sourceEntityTypeId,
        allowPolymorphic: Boolean = false,
        cardinalityDefault: EntityRelationshipCardinality = EntityRelationshipCardinality.MANY_TO_MANY,
        targetRules: List<RelationshipTargetRule> = emptyList(),
    ) = RelationshipDefinition(
        id = id,
        workspaceId = workspaceId,
        sourceEntityTypeId = sourceEntityTypeId,
        name = "Test Relationship",
        icon = Icon(IconType.LINK, IconColour.NEUTRAL),
        allowPolymorphic = allowPolymorphic,
        cardinalityDefault = cardinalityDefault,
        protected = false,
        targetRules = targetRules,
        createdAt = null,
        updatedAt = null,
        createdBy = null,
        updatedBy = null,
    )

    private fun buildTargetRule(
        id: UUID = UUID.randomUUID(),
        definitionId: UUID = UUID.randomUUID(),
        targetEntityTypeId: UUID? = UUID.randomUUID(),
        semanticTypeConstraint: SemanticGroup? = null,
        cardinalityOverride: EntityRelationshipCardinality? = null,
        inverseVisible: Boolean = false,
        inverseName: String? = null,
    ) = RelationshipTargetRule(
        id = id,
        relationshipDefinitionId = definitionId,
        targetEntityTypeId = targetEntityTypeId,
        semanticTypeConstraint = semanticTypeConstraint,
        cardinalityOverride = cardinalityOverride,
        inverseVisible = inverseVisible,
        inverseName = inverseName,
        createdAt = null,
        updatedAt = null,
    )

    private fun buildEntity(
        id: UUID = UUID.randomUUID(),
        typeId: UUID = UUID.randomUUID(),
        typeKey: String = "test_entity",
    ) = EntityEntity(
        id = id,
        workspaceId = workspaceId,
        typeId = typeId,
        typeKey = typeKey,
        identifierKey = UUID.randomUUID(),
        payload = mapOf(
            UUID.randomUUID().toString() to EntityAttributePrimitivePayload(value = "Test", schemaType = SchemaType.TEXT)
        ),
        iconType = IconType.CIRCLE_DASHED,
        iconColour = IconColour.NEUTRAL,
    )

    private fun mockSemanticGroupProjection(id: UUID, group: SemanticGroup): SemanticGroupProjection =
        object : SemanticGroupProjection {
            override fun getId(): UUID = id
            override fun getSemanticGroup(): SemanticGroup = group
        }

    // ------ Save: new links ------

    @Test
    fun `saveRelationships - new links - creates rows`() {
        val defId = UUID.randomUUID()
        val targetId1 = UUID.randomUUID()
        val targetId2 = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()

        val targetEntity1 = buildEntity(id = targetId1, typeId = targetTypeId)
        val targetEntity2 = buildEntity(id = targetId2, typeId = targetTypeId)

        val rule = buildTargetRule(definitionId = defId, targetEntityTypeId = targetTypeId)
        val definition = buildDefinition(id = defId, targetRules = listOf(rule))

        whenever(entityRelationshipRepository.findBySourceId(sourceEntityId)).thenReturn(emptyList())
        whenever(definitionRepository.findById(defId)).thenReturn(Optional.of(
            riven.core.service.util.factory.entity.EntityFactory.createRelationshipDefinitionEntity(
                id = defId, workspaceId = workspaceId, sourceEntityTypeId = sourceEntityTypeId,
            )
        ))
        whenever(targetRuleRepository.findByRelationshipDefinitionId(defId)).thenReturn(emptyList())
        whenever(entityRepository.findAllById(any<Collection<UUID>>())).thenReturn(listOf(targetEntity1, targetEntity2))
        whenever(entityRelationshipRepository.saveAll(any<List<EntityRelationshipEntity>>())).thenAnswer { it.arguments[0] }

        service.saveRelationships(
            id = sourceEntityId,
            workspaceId = workspaceId,
            definitionId = defId,
            definition = definition,
            targetIds = listOf(targetId1, targetId2),
        )

        verify(entityRelationshipRepository).saveAll(argThat<List<EntityRelationshipEntity>> { size == 2 })
    }

    @Test
    fun `saveRelationships - missing target entity - throws`() {
        val defId = UUID.randomUUID()
        val existingTargetId = UUID.randomUUID()
        val missingTargetId = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()

        val rule = buildTargetRule(definitionId = defId, targetEntityTypeId = targetTypeId)
        val definition = buildDefinition(id = defId, targetRules = listOf(rule))

        val existingEntity = buildEntity(id = existingTargetId, typeId = targetTypeId)

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(emptyList())
        // Only return one of the two requested entities
        whenever(entityRepository.findAllById(any<Collection<UUID>>())).thenReturn(listOf(existingEntity))

        assertThrows(IllegalArgumentException::class.java) {
            service.saveRelationships(
                id = sourceEntityId,
                workspaceId = workspaceId,
                definitionId = defId,
                definition = definition,
                targetIds = listOf(existingTargetId, missingTargetId),
            )
        }
    }

    // ------ Save: remove links ------

    @Test
    fun `saveRelationships - remove links - deletes rows`() {
        val defId = UUID.randomUUID()
        val existingTargetId = UUID.randomUUID()

        val existingRel = EntityRelationshipEntity(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
            sourceId = sourceEntityId,
            targetId = existingTargetId,
            definitionId = defId,
        )

        val definition = buildDefinition(id = defId, allowPolymorphic = true)

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(listOf(existingRel))

        service.saveRelationships(
            id = sourceEntityId,
            workspaceId = workspaceId,
            definitionId = defId,
            definition = definition,
            targetIds = emptyList(),
        )

        verify(entityRelationshipRepository).deleteAllBySourceIdAndDefinitionIdAndTargetIdIn(
            eq(sourceEntityId), eq(defId), any()
        )
    }

    // ------ Save: no change ------

    @Test
    fun `saveRelationships - no change - no operations`() {
        val defId = UUID.randomUUID()
        val targetId = UUID.randomUUID()

        val existingRel = EntityRelationshipEntity(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
            sourceId = sourceEntityId,
            targetId = targetId,
            definitionId = defId,
        )

        val definition = buildDefinition(id = defId, allowPolymorphic = true)

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(listOf(existingRel))

        service.saveRelationships(
            id = sourceEntityId,
            workspaceId = workspaceId,
            definitionId = defId,
            definition = definition,
            targetIds = listOf(targetId),
        )

        verify(entityRelationshipRepository, never()).saveAll(any<List<EntityRelationshipEntity>>())
        verify(entityRelationshipRepository, never()).deleteAllBySourceIdAndDefinitionIdAndTargetIdIn(any(), any(), any())
    }

    // ------ Cardinality enforcement ------

    @Test
    fun `saveRelationships - enforces cardinality ONE_TO_ONE - rejects second link`() {
        val defId = UUID.randomUUID()
        val targetId1 = UUID.randomUUID()
        val targetId2 = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()

        val rule = buildTargetRule(definitionId = defId, targetEntityTypeId = targetTypeId)
        val definition = buildDefinition(
            id = defId,
            cardinalityDefault = EntityRelationshipCardinality.ONE_TO_ONE,
            targetRules = listOf(rule),
        )

        val targetEntity1 = buildEntity(id = targetId1, typeId = targetTypeId)
        val targetEntity2 = buildEntity(id = targetId2, typeId = targetTypeId)

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(emptyList())
        whenever(entityRepository.findAllById(any<Collection<UUID>>())).thenReturn(listOf(targetEntity1, targetEntity2))

        assertThrows(InvalidRelationshipException::class.java) {
            service.saveRelationships(
                id = sourceEntityId,
                workspaceId = workspaceId,
                definitionId = defId,
                definition = definition,
                targetIds = listOf(targetId1, targetId2),
            )
        }
    }

    @Test
    fun `saveRelationships - enforces cardinality MANY_TO_ONE - allows multiple sources`() {
        val defId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()

        val rule = buildTargetRule(definitionId = defId, targetEntityTypeId = targetTypeId)
        val definition = buildDefinition(
            id = defId,
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_ONE,
            targetRules = listOf(rule),
        )

        val targetEntity = buildEntity(id = targetId, typeId = targetTypeId)

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(emptyList())
        whenever(entityRepository.findAllById(any<Collection<UUID>>())).thenReturn(listOf(targetEntity))
        whenever(entityRelationshipRepository.saveAll(any<List<EntityRelationshipEntity>>())).thenAnswer { it.arguments[0] }

        // MANY_TO_ONE means many sources → one target. For a single source, 1 target is valid.
        service.saveRelationships(
            id = sourceEntityId,
            workspaceId = workspaceId,
            definitionId = defId,
            definition = definition,
            targetIds = listOf(targetId),
        )

        verify(entityRelationshipRepository).saveAll(any<List<EntityRelationshipEntity>>())
    }

    // ------ Target type validation ------

    @Test
    fun `saveRelationships - polymorphic - accepts any target type`() {
        val defId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()

        val definition = buildDefinition(id = defId, allowPolymorphic = true)
        val targetEntity = buildEntity(id = targetId, typeId = targetTypeId)

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(emptyList())
        whenever(entityRepository.findAllById(any<Collection<UUID>>())).thenReturn(listOf(targetEntity))
        whenever(entityRelationshipRepository.saveAll(any<List<EntityRelationshipEntity>>())).thenAnswer { it.arguments[0] }

        service.saveRelationships(
            id = sourceEntityId,
            workspaceId = workspaceId,
            definitionId = defId,
            definition = definition,
            targetIds = listOf(targetId),
        )

        verify(entityRelationshipRepository).saveAll(any<List<EntityRelationshipEntity>>())
    }

    @Test
    fun `saveRelationships - non polymorphic - rejects unlisted target type`() {
        val defId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val allowedTypeId = UUID.randomUUID()
        val actualTypeId = UUID.randomUUID() // different from allowed

        val rule = buildTargetRule(definitionId = defId, targetEntityTypeId = allowedTypeId)
        val definition = buildDefinition(id = defId, allowPolymorphic = false, targetRules = listOf(rule))
        val targetEntity = buildEntity(id = targetId, typeId = actualTypeId)

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(emptyList())
        whenever(entityRepository.findAllById(any<Collection<UUID>>())).thenReturn(listOf(targetEntity))

        assertThrows(IllegalArgumentException::class.java) {
            service.saveRelationships(
                id = sourceEntityId,
                workspaceId = workspaceId,
                definitionId = defId,
                definition = definition,
                targetIds = listOf(targetId),
            )
        }
    }

    @Test
    fun `saveRelationships - cardinality override - uses rule override over default`() {
        val defId = UUID.randomUUID()
        val targetId1 = UUID.randomUUID()
        val targetId2 = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()

        // Definition defaults to MANY_TO_MANY, but rule overrides to ONE_TO_ONE for this type
        val rule = buildTargetRule(
            definitionId = defId,
            targetEntityTypeId = targetTypeId,
            cardinalityOverride = EntityRelationshipCardinality.ONE_TO_ONE,
        )
        val definition = buildDefinition(
            id = defId,
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            targetRules = listOf(rule),
        )

        val targetEntity1 = buildEntity(id = targetId1, typeId = targetTypeId)
        val targetEntity2 = buildEntity(id = targetId2, typeId = targetTypeId)

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(emptyList())
        whenever(entityRepository.findAllById(any<Collection<UUID>>())).thenReturn(listOf(targetEntity1, targetEntity2))

        // ONE_TO_ONE override should reject the second link (source-side: max 1 per type)
        assertThrows(InvalidRelationshipException::class.java) {
            service.saveRelationships(
                id = sourceEntityId,
                workspaceId = workspaceId,
                definitionId = defId,
                definition = definition,
                targetIds = listOf(targetId1, targetId2),
            )
        }
    }

    // ------ Cardinality: mixed types ------

    @Test
    fun `saveRelationships - mixed types - ONE_TO_ONE override only restricts that type`() {
        val defId = UUID.randomUUID()
        val typeA = UUID.randomUUID()
        val typeB = UUID.randomUUID()
        val targetA1 = UUID.randomUUID()
        val targetB1 = UUID.randomUUID()
        val targetB2 = UUID.randomUUID()

        // TypeA has ONE_TO_ONE override, TypeB uses MANY_TO_MANY default
        val ruleA = buildTargetRule(definitionId = defId, targetEntityTypeId = typeA, cardinalityOverride = EntityRelationshipCardinality.ONE_TO_ONE)
        val ruleB = buildTargetRule(definitionId = defId, targetEntityTypeId = typeB)
        val definition = buildDefinition(
            id = defId,
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            targetRules = listOf(ruleA, ruleB),
        )

        val entityA1 = buildEntity(id = targetA1, typeId = typeA)
        val entityB1 = buildEntity(id = targetB1, typeId = typeB)
        val entityB2 = buildEntity(id = targetB2, typeId = typeB)

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(emptyList())
        whenever(entityRepository.findAllById(any<Collection<UUID>>()))
            .thenReturn(listOf(entityA1, entityB1, entityB2))
        whenever(entityRelationshipRepository.findByTargetIdInAndDefinitionIdForUpdate(any(), eq(defId)))
            .thenReturn(emptyList())
        whenever(entityRelationshipRepository.saveAll(any<List<EntityRelationshipEntity>>())).thenAnswer { it.arguments[0] }

        // 1 TypeA + 2 TypeB should succeed (TypeA ONE_TO_ONE limit applies only to TypeA)
        service.saveRelationships(
            id = sourceEntityId,
            workspaceId = workspaceId,
            definitionId = defId,
            definition = definition,
            targetIds = listOf(targetA1, targetB1, targetB2),
        )

        verify(entityRelationshipRepository).saveAll(argThat<List<EntityRelationshipEntity>> { size == 3 })
    }

    // ------ ONE_TO_ONE target-side enforcement ------

    @Test
    fun `saveRelationships - ONE_TO_ONE - rejects target already linked by another source`() {
        val defId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()
        val otherSourceId = UUID.randomUUID()

        val rule = buildTargetRule(definitionId = defId, targetEntityTypeId = targetTypeId)
        val definition = buildDefinition(
            id = defId,
            cardinalityDefault = EntityRelationshipCardinality.ONE_TO_ONE,
            targetRules = listOf(rule),
        )

        val targetEntity = buildEntity(id = targetId, typeId = targetTypeId)

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(emptyList())
        whenever(entityRepository.findAllById(any<Collection<UUID>>()))
            .thenReturn(listOf(targetEntity))
        // Another source already links to this target
        whenever(entityRelationshipRepository.findByTargetIdInAndDefinitionIdForUpdate(any(), eq(defId)))
            .thenReturn(listOf(
                EntityRelationshipEntity(
                    id = UUID.randomUUID(),
                    workspaceId = workspaceId,
                    sourceId = otherSourceId,
                    targetId = targetId,
                    definitionId = defId,
                )
            ))

        assertThrows(InvalidRelationshipException::class.java) {
            service.saveRelationships(
                id = sourceEntityId,
                workspaceId = workspaceId,
                definitionId = defId,
                definition = definition,
                targetIds = listOf(targetId),
            )
        }
    }

    @Test
    fun `saveRelationships - MANY_TO_MANY - allows target linked by multiple sources`() {
        val defId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()

        val rule = buildTargetRule(definitionId = defId, targetEntityTypeId = targetTypeId)
        val definition = buildDefinition(
            id = defId,
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            targetRules = listOf(rule),
        )

        val targetEntity = buildEntity(id = targetId, typeId = targetTypeId)

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(emptyList())
        whenever(entityRepository.findAllById(any<Collection<UUID>>()))
            .thenReturn(listOf(targetEntity))
        whenever(entityRelationshipRepository.saveAll(any<List<EntityRelationshipEntity>>())).thenAnswer { it.arguments[0] }

        // MANY_TO_MANY should not check target uniqueness
        service.saveRelationships(
            id = sourceEntityId,
            workspaceId = workspaceId,
            definitionId = defId,
            definition = definition,
            targetIds = listOf(targetId),
        )

        verify(entityRelationshipRepository).saveAll(argThat<List<EntityRelationshipEntity>> { size == 1 })
        // Should NOT call findByTargetIdInAndDefinitionIdForUpdate for MANY_TO_MANY
        verify(entityRelationshipRepository, never()).findByTargetIdInAndDefinitionIdForUpdate(any(), any())
    }

    // ------ Diff logic ------

    @Test
    fun `saveRelationships - diff logic - adds new and removes old in same call`() {
        val defId = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()
        val targetA = UUID.randomUUID()
        val targetB = UUID.randomUUID()
        val targetC = UUID.randomUUID()

        // Existing: [A, B], Request: [B, C] → remove A, keep B, add C
        val existingRelA = EntityRelationshipEntity(
            id = UUID.randomUUID(), workspaceId = workspaceId,
            sourceId = sourceEntityId, targetId = targetA, definitionId = defId,
        )
        val existingRelB = EntityRelationshipEntity(
            id = UUID.randomUUID(), workspaceId = workspaceId,
            sourceId = sourceEntityId, targetId = targetB, definitionId = defId,
        )

        val rule = buildTargetRule(definitionId = defId, targetEntityTypeId = targetTypeId)
        val definition = buildDefinition(id = defId, targetRules = listOf(rule))

        val entityB = buildEntity(id = targetB, typeId = targetTypeId)
        val entityC = buildEntity(id = targetC, typeId = targetTypeId)

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(listOf(existingRelA, existingRelB))
        // Now returns all final targets (retained B + new C)
        whenever(entityRepository.findAllById(any<Collection<UUID>>())).thenReturn(listOf(entityB, entityC))
        whenever(entityRelationshipRepository.saveAll(any<List<EntityRelationshipEntity>>())).thenAnswer { it.arguments[0] }

        service.saveRelationships(
            id = sourceEntityId,
            workspaceId = workspaceId,
            definitionId = defId,
            definition = definition,
            targetIds = listOf(targetB, targetC),
        )

        // Should remove A
        verify(entityRelationshipRepository).deleteAllBySourceIdAndDefinitionIdAndTargetIdIn(
            eq(sourceEntityId), eq(defId), argThat<Collection<UUID>> { contains(targetA) && size == 1 }
        )
        // Should add C only (B is already present)
        verify(entityRelationshipRepository).saveAll(argThat<List<EntityRelationshipEntity>> {
            size == 1 && first().targetId == targetC
        })
    }

    @Test
    fun `saveRelationships - empty targetIds - removes all existing links`() {
        val defId = UUID.randomUUID()
        val targetA = UUID.randomUUID()
        val targetB = UUID.randomUUID()

        val existingRelA = EntityRelationshipEntity(
            id = UUID.randomUUID(), workspaceId = workspaceId,
            sourceId = sourceEntityId, targetId = targetA, definitionId = defId,
        )
        val existingRelB = EntityRelationshipEntity(
            id = UUID.randomUUID(), workspaceId = workspaceId,
            sourceId = sourceEntityId, targetId = targetB, definitionId = defId,
        )

        val definition = buildDefinition(id = defId, allowPolymorphic = true)

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(listOf(existingRelA, existingRelB))

        service.saveRelationships(
            id = sourceEntityId,
            workspaceId = workspaceId,
            definitionId = defId,
            definition = definition,
            targetIds = emptyList(),
        )

        verify(entityRelationshipRepository).deleteAllBySourceIdAndDefinitionIdAndTargetIdIn(
            eq(sourceEntityId), eq(defId), argThat<Collection<UUID>> { size == 2 }
        )
        verify(entityRelationshipRepository, never()).saveAll(any<List<EntityRelationshipEntity>>())
    }

    @Test
    fun `saveRelationships - cardinality with mixed add and remove - ONE_TO_ONE succeeds`() {
        val defId = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()
        val existingTarget = UUID.randomUUID()
        val newTarget = UUID.randomUUID()

        val rule = buildTargetRule(definitionId = defId, targetEntityTypeId = targetTypeId)
        val definition = buildDefinition(
            id = defId,
            cardinalityDefault = EntityRelationshipCardinality.ONE_TO_ONE,
            targetRules = listOf(rule),
        )

        val existingRel = EntityRelationshipEntity(
            id = UUID.randomUUID(), workspaceId = workspaceId,
            sourceId = sourceEntityId, targetId = existingTarget, definitionId = defId,
        )

        val newEntity = buildEntity(id = newTarget, typeId = targetTypeId)

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(listOf(existingRel))
        // Only newTarget is in the final state (existingTarget is being removed)
        whenever(entityRepository.findAllById(any<Collection<UUID>>())).thenReturn(listOf(newEntity))
        whenever(entityRelationshipRepository.findByTargetIdInAndDefinitionIdForUpdate(any(), eq(defId)))
            .thenReturn(emptyList())
        whenever(entityRelationshipRepository.saveAll(any<List<EntityRelationshipEntity>>())).thenAnswer { it.arguments[0] }

        // Remove 1, add 1 → total stays at 1, ONE_TO_ONE should succeed
        service.saveRelationships(
            id = sourceEntityId,
            workspaceId = workspaceId,
            definitionId = defId,
            definition = definition,
            targetIds = listOf(newTarget),
        )

        verify(entityRelationshipRepository).deleteAllBySourceIdAndDefinitionIdAndTargetIdIn(any(), any(), any())
        verify(entityRelationshipRepository).saveAll(argThat<List<EntityRelationshipEntity>> { size == 1 })
    }

    // ------ Regression: Bug 1 — retained targets count toward per-type cardinality ------

    @Test
    fun `saveRelationships - retained targets count toward per-type cardinality`() {
        val defId = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()
        val existingTarget = UUID.randomUUID()
        val newTarget = UUID.randomUUID()

        // ONE_TO_ONE override: max 1 target of this type
        val rule = buildTargetRule(
            definitionId = defId,
            targetEntityTypeId = targetTypeId,
            cardinalityOverride = EntityRelationshipCardinality.ONE_TO_ONE,
        )
        val definition = buildDefinition(
            id = defId,
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            targetRules = listOf(rule),
        )

        val existingRel = EntityRelationshipEntity(
            id = UUID.randomUUID(), workspaceId = workspaceId,
            sourceId = sourceEntityId, targetId = existingTarget, definitionId = defId,
        )

        val existingEntity = buildEntity(id = existingTarget, typeId = targetTypeId)
        val newEntity = buildEntity(id = newTarget, typeId = targetTypeId)

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(listOf(existingRel))
        // Returns both retained + new targets
        whenever(entityRepository.findAllById(any<Collection<UUID>>()))
            .thenReturn(listOf(existingEntity, newEntity))

        // Existing has 1 TypeA target, adding another TypeA → 2 of same type, exceeds ONE_TO_ONE
        assertThrows(InvalidRelationshipException::class.java) {
            service.saveRelationships(
                id = sourceEntityId,
                workspaceId = workspaceId,
                definitionId = defId,
                definition = definition,
                targetIds = listOf(existingTarget, newTarget),
            )
        }
    }

    // ------ Regression: Bug 2 — ONE_TO_MANY target-side enforcement ------

    @Test
    fun `saveRelationships - ONE_TO_MANY - rejects target already linked by another source`() {
        val defId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()
        val otherSourceId = UUID.randomUUID()

        val rule = buildTargetRule(definitionId = defId, targetEntityTypeId = targetTypeId)
        val definition = buildDefinition(
            id = defId,
            cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY,
            targetRules = listOf(rule),
        )

        val targetEntity = buildEntity(id = targetId, typeId = targetTypeId)

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(emptyList())
        whenever(entityRepository.findAllById(any<Collection<UUID>>()))
            .thenReturn(listOf(targetEntity))
        // Another source already links to this target
        whenever(entityRelationshipRepository.findByTargetIdInAndDefinitionIdForUpdate(any(), eq(defId)))
            .thenReturn(listOf(
                EntityRelationshipEntity(
                    id = UUID.randomUUID(),
                    workspaceId = workspaceId,
                    sourceId = otherSourceId,
                    targetId = targetId,
                    definitionId = defId,
                )
            ))

        // ONE_TO_MANY: each target can only be linked by one source
        assertThrows(InvalidRelationshipException::class.java) {
            service.saveRelationships(
                id = sourceEntityId,
                workspaceId = workspaceId,
                definitionId = defId,
                definition = definition,
                targetIds = listOf(targetId),
            )
        }
    }

    @Test
    fun `saveRelationships - ONE_TO_MANY - allows multiple targets from same source`() {
        val defId = UUID.randomUUID()
        val targetId1 = UUID.randomUUID()
        val targetId2 = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()

        val rule = buildTargetRule(definitionId = defId, targetEntityTypeId = targetTypeId)
        val definition = buildDefinition(
            id = defId,
            cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY,
            targetRules = listOf(rule),
        )

        val targetEntity1 = buildEntity(id = targetId1, typeId = targetTypeId)
        val targetEntity2 = buildEntity(id = targetId2, typeId = targetTypeId)

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(emptyList())
        whenever(entityRepository.findAllById(any<Collection<UUID>>()))
            .thenReturn(listOf(targetEntity1, targetEntity2))
        // Neither target is linked by another source
        whenever(entityRelationshipRepository.findByTargetIdInAndDefinitionIdForUpdate(any(), eq(defId)))
            .thenReturn(emptyList())
        whenever(entityRelationshipRepository.saveAll(any<List<EntityRelationshipEntity>>())).thenAnswer { it.arguments[0] }

        // ONE_TO_MANY: source can have many targets (no source-side limit)
        service.saveRelationships(
            id = sourceEntityId,
            workspaceId = workspaceId,
            definitionId = defId,
            definition = definition,
            targetIds = listOf(targetId1, targetId2),
        )

        verify(entityRelationshipRepository).saveAll(argThat<List<EntityRelationshipEntity>> { size == 2 })
    }

    // ------ Regression: Bug 3 — per-type default fallback, no cross-type total ------

    @Test
    fun `saveRelationships - per-type default fallback - different types each get their own limit`() {
        val defId = UUID.randomUUID()
        val typeA = UUID.randomUUID()
        val typeB = UUID.randomUUID()
        val targetA1 = UUID.randomUUID()
        val targetB1 = UUID.randomUUID()

        val ruleA = buildTargetRule(definitionId = defId, targetEntityTypeId = typeA)
        val ruleB = buildTargetRule(definitionId = defId, targetEntityTypeId = typeB)
        val definition = buildDefinition(
            id = defId,
            cardinalityDefault = EntityRelationshipCardinality.ONE_TO_ONE,
            targetRules = listOf(ruleA, ruleB),
        )

        val entityA1 = buildEntity(id = targetA1, typeId = typeA)
        val entityB1 = buildEntity(id = targetB1, typeId = typeB)

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(emptyList())
        whenever(entityRepository.findAllById(any<Collection<UUID>>()))
            .thenReturn(listOf(entityA1, entityB1))
        whenever(entityRelationshipRepository.findByTargetIdInAndDefinitionIdForUpdate(any(), eq(defId)))
            .thenReturn(emptyList())
        whenever(entityRelationshipRepository.saveAll(any<List<EntityRelationshipEntity>>())).thenAnswer { it.arguments[0] }

        // 1 TypeA + 1 TypeB with ONE_TO_ONE default = PASS (each type has 1, limit is 1 per type)
        service.saveRelationships(
            id = sourceEntityId,
            workspaceId = workspaceId,
            definitionId = defId,
            definition = definition,
            targetIds = listOf(targetA1, targetB1),
        )

        verify(entityRelationshipRepository).saveAll(argThat<List<EntityRelationshipEntity>> { size == 2 })
    }

    // ------ MANY_TO_ONE source-side enforcement ------

    @Test
    fun `saveRelationships - MANY_TO_ONE - rejects two targets of same type`() {
        val defId = UUID.randomUUID()
        val targetId1 = UUID.randomUUID()
        val targetId2 = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()

        val rule = buildTargetRule(definitionId = defId, targetEntityTypeId = targetTypeId)
        val definition = buildDefinition(
            id = defId,
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_ONE,
            targetRules = listOf(rule),
        )

        val targetEntity1 = buildEntity(id = targetId1, typeId = targetTypeId)
        val targetEntity2 = buildEntity(id = targetId2, typeId = targetTypeId)

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(emptyList())
        whenever(entityRepository.findAllById(any<Collection<UUID>>()))
            .thenReturn(listOf(targetEntity1, targetEntity2))

        // MANY_TO_ONE: source can only have 1 target per type
        assertThrows(InvalidRelationshipException::class.java) {
            service.saveRelationships(
                id = sourceEntityId,
                workspaceId = workspaceId,
                definitionId = defId,
                definition = definition,
                targetIds = listOf(targetId1, targetId2),
            )
        }
    }

    // ------ Read ------

    @Test
    fun `findRelatedEntities - forward - returns targets`() {
        whenever(entityRelationshipRepository.findEntityLinksBySourceId(sourceEntityId, workspaceId))
            .thenReturn(emptyList())
        whenever(entityRelationshipRepository.findInverseEntityLinksByTargetId(eq(sourceEntityId), eq(workspaceId), eq(SystemRelationshipType.CONNECTED_ENTITIES.name)))
            .thenReturn(emptyList())

        val result = service.findRelatedEntities(sourceEntityId, workspaceId)

        assertTrue(result.isEmpty())
        verify(entityRelationshipRepository).findEntityLinksBySourceId(sourceEntityId, workspaceId)
        verify(entityRelationshipRepository).findInverseEntityLinksByTargetId(eq(sourceEntityId), eq(workspaceId), eq(SystemRelationshipType.CONNECTED_ENTITIES.name))
    }

    @Test
    fun `findRelatedEntities - includes inverse-visible links`() {
        val defId = UUID.randomUUID()
        val inverseDefId = UUID.randomUUID()

        val forwardProjection = mock<riven.core.projection.entity.EntityLinkProjection> {
            on { getDefinitionId() } doReturn defId
            on { getId() } doReturn UUID.randomUUID()
            on { getworkspaceId() } doReturn workspaceId
            on { getSourceEntityId() } doReturn sourceEntityId
            on { getIconType() } doReturn "LINK"
            on { getIconColour() } doReturn "NEUTRAL"
            on { getTypeKey() } doReturn "company"
            on { getLabel() } doReturn "Acme Corp"
        }

        val inverseProjection = mock<riven.core.projection.entity.EntityLinkProjection> {
            on { getDefinitionId() } doReturn inverseDefId
            on { getId() } doReturn UUID.randomUUID()
            on { getworkspaceId() } doReturn workspaceId
            on { getSourceEntityId() } doReturn sourceEntityId
            on { getIconType() } doReturn "LINK"
            on { getIconColour() } doReturn "NEUTRAL"
            on { getTypeKey() } doReturn "employee"
            on { getLabel() } doReturn "Alice"
        }

        whenever(entityRelationshipRepository.findEntityLinksBySourceId(sourceEntityId, workspaceId))
            .thenReturn(listOf(forwardProjection))
        whenever(entityRelationshipRepository.findInverseEntityLinksByTargetId(eq(sourceEntityId), eq(workspaceId), eq(SystemRelationshipType.CONNECTED_ENTITIES.name)))
            .thenReturn(listOf(inverseProjection))

        val result = service.findRelatedEntities(sourceEntityId, workspaceId)

        assertEquals(2, result.size)
        assertTrue(result.containsKey(defId))
        assertTrue(result.containsKey(inverseDefId))
    }

    @Test
    fun `findRelatedEntities - excludes inverse-invisible links`() {
        // Inverse-invisible links are excluded at the repository level (SQL filter).
        // This test verifies the service correctly delegates to both queries.
        whenever(entityRelationshipRepository.findEntityLinksBySourceId(sourceEntityId, workspaceId))
            .thenReturn(emptyList())
        whenever(entityRelationshipRepository.findInverseEntityLinksByTargetId(eq(sourceEntityId), eq(workspaceId), eq(SystemRelationshipType.CONNECTED_ENTITIES.name)))
            .thenReturn(emptyList()) // repo returns nothing for inverse-invisible

        val result = service.findRelatedEntities(sourceEntityId, workspaceId)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findRelatedEntities - merges forward and inverse under same definition`() {
        val defId = UUID.randomUUID()

        val forwardProjection = mock<riven.core.projection.entity.EntityLinkProjection> {
            on { getDefinitionId() } doReturn defId
            on { getId() } doReturn UUID.randomUUID()
            on { getworkspaceId() } doReturn workspaceId
            on { getSourceEntityId() } doReturn sourceEntityId
            on { getIconType() } doReturn "LINK"
            on { getIconColour() } doReturn "NEUTRAL"
            on { getTypeKey() } doReturn "company"
            on { getLabel() } doReturn "Forward Entity"
        }

        val inverseProjection = mock<riven.core.projection.entity.EntityLinkProjection> {
            on { getDefinitionId() } doReturn defId
            on { getId() } doReturn UUID.randomUUID()
            on { getworkspaceId() } doReturn workspaceId
            on { getSourceEntityId() } doReturn sourceEntityId
            on { getIconType() } doReturn "LINK"
            on { getIconColour() } doReturn "NEUTRAL"
            on { getTypeKey() } doReturn "company"
            on { getLabel() } doReturn "Inverse Entity"
        }

        whenever(entityRelationshipRepository.findEntityLinksBySourceId(sourceEntityId, workspaceId))
            .thenReturn(listOf(forwardProjection))
        whenever(entityRelationshipRepository.findInverseEntityLinksByTargetId(eq(sourceEntityId), eq(workspaceId), eq(SystemRelationshipType.CONNECTED_ENTITIES.name)))
            .thenReturn(listOf(inverseProjection))

        val result = service.findRelatedEntities(sourceEntityId, workspaceId)

        assertEquals(1, result.size)
        assertEquals(2, result[defId]!!.size)
    }

    // ------ Semantic group matching ------

    @Test
    fun `saveRelationships - semantic rule matches target with matching group`() {
        val defId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()

        val targetEntity = buildEntity(id = targetId, typeId = targetTypeId)
        val rule = buildTargetRule(
            definitionId = defId,
            targetEntityTypeId = null,
            semanticTypeConstraint = SemanticGroup.CUSTOMER,
        )
        val definition = buildDefinition(id = defId, targetRules = listOf(rule))

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(emptyList())
        whenever(entityRepository.findAllById(any<Collection<UUID>>())).thenReturn(listOf(targetEntity))
        whenever(entityRelationshipRepository.saveAll(any<List<EntityRelationshipEntity>>())).thenAnswer { it.arguments[0] }

        // Override default: return entity type with CUSTOMER semantic group
        whenever(entityTypeRepository.findSemanticGroupsByIds(any())).thenReturn(
            listOf(mockSemanticGroupProjection(targetTypeId, SemanticGroup.CUSTOMER))
        )

        service.saveRelationships(
            id = sourceEntityId,
            workspaceId = workspaceId,
            definitionId = defId,
            definition = definition,
            targetIds = listOf(targetId),
        )

        verify(entityRelationshipRepository).saveAll(any<List<EntityRelationshipEntity>>())
    }

    @Test
    fun `saveRelationships - semantic rule rejects target with non-matching group`() {
        val defId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()

        val targetEntity = buildEntity(id = targetId, typeId = targetTypeId)
        val rule = buildTargetRule(
            definitionId = defId,
            targetEntityTypeId = null,
            semanticTypeConstraint = SemanticGroup.CUSTOMER,
        )
        val definition = buildDefinition(id = defId, targetRules = listOf(rule))

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(emptyList())
        whenever(entityRepository.findAllById(any<Collection<UUID>>())).thenReturn(listOf(targetEntity))

        // Target type has PRODUCT group, but rule requires CUSTOMER
        whenever(entityTypeRepository.findSemanticGroupsByIds(any())).thenReturn(
            listOf(mockSemanticGroupProjection(targetTypeId, SemanticGroup.PRODUCT))
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.saveRelationships(
                id = sourceEntityId,
                workspaceId = workspaceId,
                definitionId = defId,
                definition = definition,
                targetIds = listOf(targetId),
            )
        }
    }

    @Test
    fun `saveRelationships - UNCATEGORIZED target does not match semantic rules`() {
        val defId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()

        val targetEntity = buildEntity(id = targetId, typeId = targetTypeId)
        val rule = buildTargetRule(
            definitionId = defId,
            targetEntityTypeId = null,
            semanticTypeConstraint = SemanticGroup.CUSTOMER,
        )
        val definition = buildDefinition(id = defId, targetRules = listOf(rule))

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(emptyList())
        whenever(entityRepository.findAllById(any<Collection<UUID>>())).thenReturn(listOf(targetEntity))

        // Target type is UNCATEGORIZED — should not match semantic rules
        whenever(entityTypeRepository.findSemanticGroupsByIds(any())).thenReturn(
            listOf(mockSemanticGroupProjection(targetTypeId, SemanticGroup.UNCATEGORIZED))
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.saveRelationships(
                id = sourceEntityId,
                workspaceId = workspaceId,
                definitionId = defId,
                definition = definition,
                targetIds = listOf(targetId),
            )
        }
    }

    @Test
    fun `saveRelationships - exact type ID rule takes precedence over semantic match`() {
        val defId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()

        val targetEntity = buildEntity(id = targetId, typeId = targetTypeId)

        val typeIdRule = buildTargetRule(
            definitionId = defId,
            targetEntityTypeId = targetTypeId,
            cardinalityOverride = EntityRelationshipCardinality.ONE_TO_ONE,
        )
        val semanticRule = buildTargetRule(
            definitionId = defId,
            targetEntityTypeId = null,
            semanticTypeConstraint = SemanticGroup.CUSTOMER,
            cardinalityOverride = EntityRelationshipCardinality.MANY_TO_MANY,
        )
        val definition = buildDefinition(
            id = defId,
            targetRules = listOf(typeIdRule, semanticRule),
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
        )

        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionIdForUpdate(sourceEntityId, defId))
            .thenReturn(emptyList())
        whenever(entityRepository.findAllById(any<Collection<UUID>>())).thenReturn(listOf(targetEntity))
        whenever(entityRelationshipRepository.saveAll(any<List<EntityRelationshipEntity>>())).thenAnswer { it.arguments[0] }

        // Target has CUSTOMER group, so semantic rule would match — but type ID rule should take precedence
        whenever(entityTypeRepository.findSemanticGroupsByIds(any())).thenReturn(
            listOf(mockSemanticGroupProjection(targetTypeId, SemanticGroup.CUSTOMER))
        )

        // ONE_TO_ONE: target-side enforcement checks existing links
        whenever(entityRelationshipRepository.findByTargetIdInAndDefinitionIdForUpdate(any(), eq(defId)))
            .thenReturn(emptyList())

        service.saveRelationships(
            id = sourceEntityId,
            workspaceId = workspaceId,
            definitionId = defId,
            definition = definition,
            targetIds = listOf(targetId),
        )

        // Should succeed — type ID rule matched (ONE_TO_ONE with 1 target is valid)
        verify(entityRelationshipRepository).saveAll(any<List<EntityRelationshipEntity>>())
    }
}
