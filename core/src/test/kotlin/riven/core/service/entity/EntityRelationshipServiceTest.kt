package cranium.core.service.entity

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import cranium.core.configuration.auth.WorkspaceSecurity
import cranium.core.entity.entity.EntityEntity
import cranium.core.entity.entity.EntityRelationshipEntity
import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.entity.EntityRelationshipCardinality
import cranium.core.enums.entity.SystemRelationshipType
import cranium.core.enums.workspace.WorkspaceRoles
import cranium.core.exceptions.InvalidRelationshipException
import cranium.core.models.common.Icon
import cranium.core.models.entity.RelationshipDefinition
import cranium.core.models.entity.RelationshipTargetRule
import cranium.core.enums.common.validation.SchemaType
import cranium.core.models.entity.payload.EntityAttributePrimitivePayload
import cranium.core.repository.entity.EntityRelationshipRepository
import cranium.core.repository.entity.EntityRepository
import cranium.core.repository.entity.RelationshipDefinitionRepository
import cranium.core.repository.entity.RelationshipTargetRuleRepository
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.util.BaseServiceTest
import cranium.core.service.util.WithUserPersona
import cranium.core.service.util.WorkspaceRole
import cranium.core.service.util.factory.entity.EntityFactory
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
    private lateinit var definitionRepository: RelationshipDefinitionRepository

    @MockitoBean
    private lateinit var targetRuleRepository: RelationshipTargetRuleRepository

    @MockitoBean
    private lateinit var entityTypeRelationshipService: cranium.core.service.entity.type.EntityTypeRelationshipService

    @MockitoBean
    private lateinit var activityService: cranium.core.service.activity.ActivityService

    @Autowired
    private lateinit var service: EntityRelationshipService

    private val sourceEntityId = UUID.randomUUID()
    private val sourceEntityTypeId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        reset(
            entityRelationshipRepository,
            entityRepository,
            definitionRepository,
            targetRuleRepository,
        )
    }

    // ------ Helper builders ------

    private fun buildDefinition(
        id: UUID = UUID.randomUUID(),
        sourceEntityTypeId: UUID = this.sourceEntityTypeId,
        cardinalityDefault: EntityRelationshipCardinality = EntityRelationshipCardinality.MANY_TO_MANY,
        targetRules: List<RelationshipTargetRule> = emptyList(),
    ) = RelationshipDefinition(
        id = id,
        workspaceId = workspaceId,
        sourceEntityTypeId = sourceEntityTypeId,
        name = "Test Relationship",
        icon = Icon(IconType.LINK, IconColour.NEUTRAL),
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
        targetEntityTypeId: UUID = UUID.randomUUID(),
        cardinalityOverride: EntityRelationshipCardinality? = null,
        inverseName: String = "Inverse",
    ) = RelationshipTargetRule(
        id = id,
        relationshipDefinitionId = definitionId,
        targetEntityTypeId = targetEntityTypeId,
        cardinalityOverride = cardinalityOverride,
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
        iconType = IconType.CIRCLE_DASHED,
        iconColour = IconColour.NEUTRAL,
    )

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
            cranium.core.service.util.factory.entity.EntityFactory.createRelationshipDefinitionEntity(
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

        val definition = buildDefinition(id = defId)

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

        val definition = buildDefinition(id = defId)

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
    fun `saveRelationships - with matching target rule - accepts target type`() {
        val defId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val targetTypeId = UUID.randomUUID()

        val rule = buildTargetRule(definitionId = defId, targetEntityTypeId = targetTypeId)
        val definition = buildDefinition(id = defId, targetRules = listOf(rule))
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
        val definition = buildDefinition(id = defId, targetRules = listOf(rule))
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

        val definition = buildDefinition(id = defId)

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
        whenever(entityRelationshipRepository.findInverseEntityLinksByTargetId(sourceEntityId, workspaceId))
            .thenReturn(emptyList())

        val result = service.findRelatedEntities(sourceEntityId, workspaceId)

        assertTrue(result.isEmpty())
        verify(entityRelationshipRepository).findEntityLinksBySourceId(sourceEntityId, workspaceId)
        verify(entityRelationshipRepository).findInverseEntityLinksByTargetId(sourceEntityId, workspaceId)
    }

    @Test
    fun `findRelatedEntities - includes inverse-visible links`() {
        val defId = UUID.randomUUID()
        val inverseDefId = UUID.randomUUID()

        val forwardProjection = mock<cranium.core.projection.entity.EntityLinkProjection> {
            on { getDefinitionId() } doReturn defId
            on { getId() } doReturn UUID.randomUUID()
            on { getworkspaceId() } doReturn workspaceId
            on { getSourceEntityId() } doReturn sourceEntityId
            on { getIconType() } doReturn "LINK"
            on { getIconColour() } doReturn "NEUTRAL"
            on { getTypeKey() } doReturn "company"
            on { getLabel() } doReturn "Acme Corp"
            on { getDirection() } doReturn "FORWARD"
            on { getSystemType() } doReturn null
        }

        val inverseProjection = mock<cranium.core.projection.entity.EntityLinkProjection> {
            on { getDefinitionId() } doReturn inverseDefId
            on { getId() } doReturn UUID.randomUUID()
            on { getworkspaceId() } doReturn workspaceId
            on { getSourceEntityId() } doReturn sourceEntityId
            on { getIconType() } doReturn "LINK"
            on { getIconColour() } doReturn "NEUTRAL"
            on { getTypeKey() } doReturn "employee"
            on { getLabel() } doReturn "Alice"
            on { getDirection() } doReturn "INVERSE"
            on { getSystemType() } doReturn SystemRelationshipType.SYSTEM_CONNECTION.name
        }

        whenever(entityRelationshipRepository.findEntityLinksBySourceId(sourceEntityId, workspaceId))
            .thenReturn(listOf(forwardProjection))
        whenever(entityRelationshipRepository.findInverseEntityLinksByTargetId(sourceEntityId, workspaceId))
            .thenReturn(listOf(inverseProjection))

        val result = service.findRelatedEntities(sourceEntityId, workspaceId)

        assertEquals(2, result.size)
        assertEquals(1, result.count { it.definitionId == defId })
        assertEquals(1, result.count { it.definitionId == inverseDefId })
    }

    @Test
    fun `findRelatedEntities - excludes inverse-invisible links`() {
        // Inverse-invisible links are excluded at the repository level (SQL filter).
        // This test verifies the service correctly delegates to both queries.
        whenever(entityRelationshipRepository.findEntityLinksBySourceId(sourceEntityId, workspaceId))
            .thenReturn(emptyList())
        whenever(entityRelationshipRepository.findInverseEntityLinksByTargetId(sourceEntityId, workspaceId))
            .thenReturn(emptyList()) // repo returns nothing for inverse-invisible

        val result = service.findRelatedEntities(sourceEntityId, workspaceId)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findRelatedEntities - merges forward and inverse under same definition`() {
        val defId = UUID.randomUUID()

        val forwardProjection = mock<cranium.core.projection.entity.EntityLinkProjection> {
            on { getDefinitionId() } doReturn defId
            on { getId() } doReturn UUID.randomUUID()
            on { getworkspaceId() } doReturn workspaceId
            on { getSourceEntityId() } doReturn sourceEntityId
            on { getIconType() } doReturn "LINK"
            on { getIconColour() } doReturn "NEUTRAL"
            on { getTypeKey() } doReturn "company"
            on { getLabel() } doReturn "Forward Entity"
            on { getDirection() } doReturn "FORWARD"
            on { getSystemType() } doReturn null
        }

        val inverseProjection = mock<cranium.core.projection.entity.EntityLinkProjection> {
            on { getDefinitionId() } doReturn defId
            on { getId() } doReturn UUID.randomUUID()
            on { getworkspaceId() } doReturn workspaceId
            on { getSourceEntityId() } doReturn sourceEntityId
            on { getIconType() } doReturn "LINK"
            on { getIconColour() } doReturn "NEUTRAL"
            on { getTypeKey() } doReturn "company"
            on { getLabel() } doReturn "Inverse Entity"
            on { getDirection() } doReturn "INVERSE"
            on { getSystemType() } doReturn SystemRelationshipType.SYSTEM_CONNECTION.name
        }

        whenever(entityRelationshipRepository.findEntityLinksBySourceId(sourceEntityId, workspaceId))
            .thenReturn(listOf(forwardProjection))
        whenever(entityRelationshipRepository.findInverseEntityLinksByTargetId(sourceEntityId, workspaceId))
            .thenReturn(listOf(inverseProjection))

        val result = service.findRelatedEntities(sourceEntityId, workspaceId)

        assertEquals(2, result.size)
        assertEquals(2, result.count { it.definitionId == defId })
    }

    // ------ System-bus workspace guard (regression for r3176253151) ------

    /**
     * Regression: replaceForDefinitionInternal must reject a sourceId whose owning workspace
     * does not match the supplied workspaceId. Without this guard, a system-bus caller passing
     * a foreign sourceId could sweep that workspace's relationship rows.
     */
    @Test
    fun `replaceForDefinitionInternal - foreign-workspace source - rejects and skips writes`() {
        val foreignWorkspaceId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val source = EntityFactory.createEntityEntity(id = sourceId, workspaceId = foreignWorkspaceId)
        whenever(entityRepository.findById(sourceId)).thenReturn(java.util.Optional.of(source))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.replaceForDefinitionInternal(
                workspaceId = workspaceId,
                sourceId = sourceId,
                definitionId = UUID.randomUUID(),
                targetIds = setOf(UUID.randomUUID()),
                linkSource = cranium.core.enums.integration.SourceType.USER_CREATED,
            )
        }
        assertTrue(ex.message!!.contains("not found in workspace"))

        verify(entityRelationshipRepository, never()).findAllBySourceIdAndDefinitionIdForUpdate(any(), any())
        verify(entityRelationshipRepository, never()).saveAll<EntityRelationshipEntity>(any())
        verify(entityRelationshipRepository, never())
            .deleteAllBySourceIdAndDefinitionIdAndTargetKindAndTargetIdIn(any(), any(), any(), any())
    }

    /**
     * Regression: clearAllOfKindForDefinition must reject a sourceId whose owning workspace
     * does not match the supplied workspaceId. Mirrors the replaceForDefinitionInternal guard.
     */
    @Test
    fun `clearAllOfKindForDefinition - foreign-workspace source - rejects and skips delete`() {
        val foreignWorkspaceId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val source = EntityFactory.createEntityEntity(id = sourceId, workspaceId = foreignWorkspaceId)
        whenever(entityRepository.findById(sourceId)).thenReturn(java.util.Optional.of(source))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.clearAllOfKindForDefinition(
                workspaceId = workspaceId,
                sourceId = sourceId,
                definitionId = UUID.randomUUID(),
                targetKind = cranium.core.enums.entity.RelationshipTargetKind.ATTRIBUTE,
            )
        }
        assertTrue(ex.message!!.contains("not found in workspace"))

        verify(entityRelationshipRepository, never())
            .deleteAllBySourceIdAndDefinitionIdAndTargetKind(any(), any(), any())
    }

}

