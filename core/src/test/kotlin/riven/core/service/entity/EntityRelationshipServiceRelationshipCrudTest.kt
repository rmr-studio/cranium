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
import riven.core.entity.entity.EntityRelationshipEntity
import riven.core.entity.entity.RelationshipDefinitionEntity
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.SystemRelationshipType
import riven.core.enums.integration.SourceType
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.exceptions.ConflictException
import riven.core.exceptions.InvalidRelationshipException
import riven.core.exceptions.NotFoundException
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.entity.RelationshipDefinition
import riven.core.models.entity.RelationshipTargetRule
import riven.core.models.request.entity.AddRelationshipRequest
import riven.core.projection.entity.SemanticGroupProjection
import riven.core.models.request.entity.UpdateRelationshipRequest
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.type.EntityTypeRelationshipService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.entity.EntityFactory
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        EntityRelationshipServiceRelationshipCrudTest.TestConfig::class,
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
class EntityRelationshipServiceRelationshipCrudTest : BaseServiceTest() {

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
    private lateinit var entityTypeRelationshipService: EntityTypeRelationshipService

    @MockitoBean
    private lateinit var activityService: ActivityService

    @Autowired
    private lateinit var service: EntityRelationshipService

    private val sourceEntityId = UUID.randomUUID()
    private val targetEntityId = UUID.randomUUID()
    private val sourceEntityTypeId = UUID.randomUUID()
    private val targetEntityTypeId = UUID.randomUUID()
    private val fallbackDefId = UUID.randomUUID()
    private val typedDefId = UUID.randomUUID()

    private fun buildFallbackDefinitionEntity(): RelationshipDefinitionEntity {
        return EntityFactory.createRelationshipDefinitionEntity(
            id = fallbackDefId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Connected Entities",
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            allowPolymorphic = true,
            protected = true,
        ).copy(systemType = SystemRelationshipType.CONNECTED_ENTITIES)
    }

    private fun buildTypedDefinition(
        cardinality: EntityRelationshipCardinality = EntityRelationshipCardinality.MANY_TO_MANY,
        allowPolymorphic: Boolean = false,
        targetRules: List<RelationshipTargetRule> = emptyList(),
    ): RelationshipDefinition {
        return RelationshipDefinition(
            id = typedDefId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Typed Relationship",
            icon = riven.core.models.common.Icon(
                riven.core.enums.common.icon.IconType.LINK,
                riven.core.enums.common.icon.IconColour.NEUTRAL,
            ),
            allowPolymorphic = allowPolymorphic,
            cardinalityDefault = cardinality,
            protected = false,
            targetRules = targetRules,
            createdAt = null,
            updatedAt = null,
            createdBy = null,
            updatedBy = null,
        )
    }

    private fun mockSemanticGroupProjection(id: UUID, group: SemanticGroup): SemanticGroupProjection =
        object : SemanticGroupProjection {
            override fun getId(): UUID = id
            override fun getSemanticGroup(): SemanticGroup = group
        }

    @BeforeEach
    fun setup() {
        reset(
            entityRelationshipRepository,
            entityRepository,
            entityTypeRepository,
            definitionRepository,
            entityTypeRelationshipService,
            activityService,
        )

        // Default: resolve any entity type IDs to UNCATEGORIZED semantic group
        whenever(entityTypeRepository.findSemanticGroupsByIds(any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val ids = invocation.arguments[0] as Collection<UUID>
            ids.map { typeId -> mockSemanticGroupProjection(typeId, SemanticGroup.UNCATEGORIZED) }
        }
    }

    // ------ addRelationship (fallback) ------

    @Test
    fun `addRelationship - no definitionId - creates fallback relationship`() {
        val sourceEntity = EntityFactory.createEntityEntity(
            id = sourceEntityId,
            workspaceId = workspaceId,
            typeId = sourceEntityTypeId,
        )
        val targetEntity = EntityFactory.createEntityEntity(
            id = targetEntityId,
            workspaceId = workspaceId,
        )
        val fallbackDef = buildFallbackDefinitionEntity()
        val request = AddRelationshipRequest(
            targetEntityId = targetEntityId,
            semanticContext = "works with",
            linkSource = SourceType.USER_CREATED,
        )

        whenever(entityRepository.findByIdAndWorkspaceId(sourceEntityId, workspaceId)).thenReturn(Optional.of(sourceEntity))
        whenever(entityRepository.findByIdAndWorkspaceId(targetEntityId, workspaceId)).thenReturn(Optional.of(targetEntity))
        whenever(entityTypeRelationshipService.getOrCreateFallbackDefinition(workspaceId, sourceEntityTypeId))
            .thenReturn(fallbackDef)
        whenever(entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(sourceEntityId, targetEntityId, fallbackDefId))
            .thenReturn(emptyList())
        whenever(entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(targetEntityId, sourceEntityId, fallbackDefId))
            .thenReturn(emptyList())
        whenever(entityRelationshipRepository.save(any<EntityRelationshipEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as EntityRelationshipEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }

        val result = service.addRelationship(workspaceId, sourceEntityId, request)

        assertNotNull(result.id)
        assertEquals(sourceEntityId, result.sourceEntityId)
        assertEquals(targetEntityId, result.targetEntityId)
        assertEquals(fallbackDefId, result.definitionId)
        assertEquals("Connected Entities", result.definitionName)
        assertEquals("works with", result.semanticContext)
        assertEquals(SourceType.USER_CREATED, result.linkSource)

        verify(entityRelationshipRepository).save(argThat<EntityRelationshipEntity> {
            this.sourceId == sourceEntityId &&
                this.targetId == targetEntityId &&
                this.definitionId == fallbackDefId &&
                this.semanticContext == "works with" &&
                this.linkSource == SourceType.USER_CREATED
        })
    }

    @Test
    fun `addRelationship - no definitionId - bidirectional duplicate - throws ConflictException`() {
        val sourceEntity = EntityFactory.createEntityEntity(
            id = sourceEntityId,
            workspaceId = workspaceId,
            typeId = sourceEntityTypeId,
        )
        val targetEntity = EntityFactory.createEntityEntity(
            id = targetEntityId,
            workspaceId = workspaceId,
        )
        val fallbackDef = buildFallbackDefinitionEntity()
        val existingRel = EntityFactory.createRelationshipEntity(
            workspaceId = workspaceId,
            sourceId = sourceEntityId,
            targetId = targetEntityId,
            definitionId = fallbackDefId,
        )
        val request = AddRelationshipRequest(
            targetEntityId = targetEntityId,
            semanticContext = "duplicate",
        )

        whenever(entityRepository.findByIdAndWorkspaceId(sourceEntityId, workspaceId)).thenReturn(Optional.of(sourceEntity))
        whenever(entityRepository.findByIdAndWorkspaceId(targetEntityId, workspaceId)).thenReturn(Optional.of(targetEntity))
        whenever(entityTypeRelationshipService.getOrCreateFallbackDefinition(workspaceId, sourceEntityTypeId))
            .thenReturn(fallbackDef)
        whenever(entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(sourceEntityId, targetEntityId, fallbackDefId))
            .thenReturn(listOf(existingRel))

        assertThrows(ConflictException::class.java) {
            service.addRelationship(workspaceId, sourceEntityId, request)
        }

        verify(entityRelationshipRepository, never()).save(any())
    }

    @Test
    fun `addRelationship - no definitionId - reverse bidirectional duplicate - throws ConflictException`() {
        val sourceEntity = EntityFactory.createEntityEntity(
            id = sourceEntityId,
            workspaceId = workspaceId,
            typeId = sourceEntityTypeId,
        )
        val targetEntity = EntityFactory.createEntityEntity(
            id = targetEntityId,
            workspaceId = workspaceId,
        )
        val fallbackDef = buildFallbackDefinitionEntity()
        val request = AddRelationshipRequest(
            targetEntityId = targetEntityId,
            semanticContext = "reverse duplicate",
        )

        whenever(entityRepository.findByIdAndWorkspaceId(sourceEntityId, workspaceId)).thenReturn(Optional.of(sourceEntity))
        whenever(entityRepository.findByIdAndWorkspaceId(targetEntityId, workspaceId)).thenReturn(Optional.of(targetEntity))
        whenever(entityTypeRelationshipService.getOrCreateFallbackDefinition(workspaceId, sourceEntityTypeId))
            .thenReturn(fallbackDef)
        // Forward check returns empty — no forward duplicate
        whenever(entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(sourceEntityId, targetEntityId, fallbackDefId))
            .thenReturn(emptyList())
        // Reverse check returns existing — target→source already exists
        val reverseRel = EntityFactory.createRelationshipEntity(
            workspaceId = workspaceId,
            sourceId = targetEntityId,
            targetId = sourceEntityId,
            definitionId = fallbackDefId,
        )
        whenever(entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(targetEntityId, sourceEntityId, fallbackDefId))
            .thenReturn(listOf(reverseRel))

        assertThrows(ConflictException::class.java) {
            service.addRelationship(workspaceId, sourceEntityId, request)
        }

        verify(entityRelationshipRepository, never()).save(any())
    }

    @Test
    fun `addRelationship - missing source entity - throws NotFoundException`() {
        val request = AddRelationshipRequest(
            targetEntityId = targetEntityId,
            semanticContext = "test",
        )

        whenever(entityRepository.findByIdAndWorkspaceId(sourceEntityId, workspaceId)).thenReturn(Optional.empty())

        assertThrows(NotFoundException::class.java) {
            service.addRelationship(workspaceId, sourceEntityId, request)
        }
    }

    // ------ addRelationship (typed) ------

    @Test
    fun `addRelationship - with definitionId - creates typed relationship`() {
        val sourceEntity = EntityFactory.createEntityEntity(
            id = sourceEntityId,
            workspaceId = workspaceId,
            typeId = sourceEntityTypeId,
        )
        val targetEntity = EntityFactory.createEntityEntity(
            id = targetEntityId,
            workspaceId = workspaceId,
            typeId = targetEntityTypeId,
        )
        val targetRule = RelationshipTargetRule(
            id = UUID.randomUUID(),
            relationshipDefinitionId = typedDefId,
            targetEntityTypeId = targetEntityTypeId,
            semanticTypeConstraint = null,
            cardinalityOverride = null,
            inverseName = "Source Type",
            createdAt = null,
            updatedAt = null,
        )
        val definition = buildTypedDefinition(
            cardinality = EntityRelationshipCardinality.MANY_TO_MANY,
            targetRules = listOf(targetRule),
        )
        val request = AddRelationshipRequest(
            targetEntityId = targetEntityId,
            definitionId = typedDefId,
            semanticContext = "typed link",
        )

        whenever(entityRepository.findByIdAndWorkspaceId(sourceEntityId, workspaceId)).thenReturn(Optional.of(sourceEntity))
        whenever(entityRepository.findByIdAndWorkspaceId(targetEntityId, workspaceId)).thenReturn(Optional.of(targetEntity))
        whenever(entityTypeRelationshipService.getDefinitionById(workspaceId, typedDefId)).thenReturn(definition)
        whenever(entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(sourceEntityId, targetEntityId, typedDefId))
            .thenReturn(emptyList())
        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionId(sourceEntityId, typedDefId))
            .thenReturn(emptyList())
        whenever(entityRepository.findAllById(setOf(targetEntityId))).thenReturn(listOf(targetEntity))
        whenever(entityRelationshipRepository.save(any<EntityRelationshipEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as EntityRelationshipEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }

        val result = service.addRelationship(workspaceId, sourceEntityId, request)

        assertNotNull(result.id)
        assertEquals(typedDefId, result.definitionId)
        assertEquals("Typed Relationship", result.definitionName)
        assertEquals("typed link", result.semanticContext)
    }

    @Test
    fun `addRelationship - with definitionId - wrong target type - throws`() {
        val sourceEntity = EntityFactory.createEntityEntity(
            id = sourceEntityId,
            workspaceId = workspaceId,
            typeId = sourceEntityTypeId,
        )
        val wrongTypeId = UUID.randomUUID()
        val targetEntity = EntityFactory.createEntityEntity(
            id = targetEntityId,
            workspaceId = workspaceId,
            typeId = wrongTypeId,
        )
        val targetRule = RelationshipTargetRule(
            id = UUID.randomUUID(),
            relationshipDefinitionId = typedDefId,
            targetEntityTypeId = targetEntityTypeId,
            semanticTypeConstraint = null,
            cardinalityOverride = null,
            inverseName = "Source Type",
            createdAt = null,
            updatedAt = null,
        )
        val definition = buildTypedDefinition(targetRules = listOf(targetRule))
        val request = AddRelationshipRequest(
            targetEntityId = targetEntityId,
            definitionId = typedDefId,
        )

        whenever(entityRepository.findByIdAndWorkspaceId(sourceEntityId, workspaceId)).thenReturn(Optional.of(sourceEntity))
        whenever(entityRepository.findByIdAndWorkspaceId(targetEntityId, workspaceId)).thenReturn(Optional.of(targetEntity))
        whenever(entityTypeRelationshipService.getDefinitionById(workspaceId, typedDefId)).thenReturn(definition)
        whenever(entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(sourceEntityId, targetEntityId, typedDefId))
            .thenReturn(emptyList())
        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionId(sourceEntityId, typedDefId))
            .thenReturn(emptyList())
        whenever(entityRepository.findAllById(setOf(targetEntityId))).thenReturn(listOf(targetEntity))

        assertThrows(IllegalArgumentException::class.java) {
            service.addRelationship(workspaceId, sourceEntityId, request)
        }
    }

    @Test
    fun `addRelationship - with definitionId - enforces cardinality`() {
        val sourceEntity = EntityFactory.createEntityEntity(
            id = sourceEntityId,
            workspaceId = workspaceId,
            typeId = sourceEntityTypeId,
        )
        val existingTargetId = UUID.randomUUID()
        val targetEntity = EntityFactory.createEntityEntity(
            id = targetEntityId,
            workspaceId = workspaceId,
            typeId = targetEntityTypeId,
        )
        val existingTargetEntity = EntityFactory.createEntityEntity(
            id = existingTargetId,
            workspaceId = workspaceId,
            typeId = targetEntityTypeId,
        )
        val targetRule = RelationshipTargetRule(
            id = UUID.randomUUID(),
            relationshipDefinitionId = typedDefId,
            targetEntityTypeId = targetEntityTypeId,
            semanticTypeConstraint = null,
            cardinalityOverride = null,
            inverseName = "Source Type",
            createdAt = null,
            updatedAt = null,
        )
        // ONE_TO_ONE: source can have at most 1 target of this type
        val definition = buildTypedDefinition(
            cardinality = EntityRelationshipCardinality.ONE_TO_ONE,
            targetRules = listOf(targetRule),
        )
        val existingRel = EntityFactory.createRelationshipEntity(
            workspaceId = workspaceId,
            sourceId = sourceEntityId,
            targetId = existingTargetId,
            definitionId = typedDefId,
        )
        val request = AddRelationshipRequest(
            targetEntityId = targetEntityId,
            definitionId = typedDefId,
        )

        whenever(entityRepository.findByIdAndWorkspaceId(sourceEntityId, workspaceId)).thenReturn(Optional.of(sourceEntity))
        whenever(entityRepository.findByIdAndWorkspaceId(targetEntityId, workspaceId)).thenReturn(Optional.of(targetEntity))
        whenever(entityTypeRelationshipService.getDefinitionById(workspaceId, typedDefId)).thenReturn(definition)
        whenever(entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(sourceEntityId, targetEntityId, typedDefId))
            .thenReturn(emptyList())
        whenever(entityRelationshipRepository.findAllBySourceIdAndDefinitionId(sourceEntityId, typedDefId))
            .thenReturn(listOf(existingRel))
        whenever(entityRepository.findAllById(setOf(existingTargetId)))
            .thenReturn(listOf(existingTargetEntity))

        assertThrows(InvalidRelationshipException::class.java) {
            service.addRelationship(workspaceId, sourceEntityId, request)
        }
    }

    @Test
    fun `addRelationship - with definitionId - directional duplicate only - throws ConflictException`() {
        val sourceEntity = EntityFactory.createEntityEntity(
            id = sourceEntityId,
            workspaceId = workspaceId,
            typeId = sourceEntityTypeId,
        )
        val targetEntity = EntityFactory.createEntityEntity(
            id = targetEntityId,
            workspaceId = workspaceId,
        )
        val definition = buildTypedDefinition(allowPolymorphic = true)
        val existingRel = EntityFactory.createRelationshipEntity(
            workspaceId = workspaceId,
            sourceId = sourceEntityId,
            targetId = targetEntityId,
            definitionId = typedDefId,
        )
        val request = AddRelationshipRequest(
            targetEntityId = targetEntityId,
            definitionId = typedDefId,
        )

        whenever(entityRepository.findByIdAndWorkspaceId(sourceEntityId, workspaceId)).thenReturn(Optional.of(sourceEntity))
        whenever(entityRepository.findByIdAndWorkspaceId(targetEntityId, workspaceId)).thenReturn(Optional.of(targetEntity))
        whenever(entityTypeRelationshipService.getDefinitionById(workspaceId, typedDefId)).thenReturn(definition)
        whenever(entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(sourceEntityId, targetEntityId, typedDefId))
            .thenReturn(listOf(existingRel))

        assertThrows(ConflictException::class.java) {
            service.addRelationship(workspaceId, sourceEntityId, request)
        }

        // Verify only directional check (no reverse check for typed definitions)
        verify(entityRelationshipRepository, never())
            .findBySourceIdAndTargetIdAndDefinitionId(targetEntityId, sourceEntityId, typedDefId)
    }

    // ------ getRelationships ------

    @Test
    fun `getRelationships - returns all relationships for entity`() {
        val entity = EntityFactory.createEntityEntity(
            id = sourceEntityId,
            workspaceId = workspaceId,
            typeId = sourceEntityTypeId,
        )
        val fallbackRel = EntityFactory.createRelationshipEntity(
            workspaceId = workspaceId,
            sourceId = sourceEntityId,
            targetId = targetEntityId,
            definitionId = fallbackDefId,
            semanticContext = "connected",
        )
        val typedRel = EntityFactory.createRelationshipEntity(
            workspaceId = workspaceId,
            sourceId = sourceEntityId,
            targetId = UUID.randomUUID(),
            definitionId = typedDefId,
        )
        val fallbackDefEntity = buildFallbackDefinitionEntity()
        val typedDefEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = typedDefId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Typed Relationship",
        )

        whenever(entityRepository.findByIdAndWorkspaceId(sourceEntityId, workspaceId)).thenReturn(Optional.of(entity))
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(sourceEntityId, workspaceId))
            .thenReturn(listOf(fallbackRel, typedRel))
        whenever(definitionRepository.findAllById(listOf(fallbackDefId, typedDefId)))
            .thenReturn(listOf(fallbackDefEntity, typedDefEntity))

        val result = service.getRelationships(workspaceId, sourceEntityId)

        assertEquals(2, result.size)
        assertTrue(result.any { it.definitionName == "Connected Entities" })
        assertTrue(result.any { it.definitionName == "Typed Relationship" })
    }

    @Test
    fun `getRelationships - with definitionId filter - returns filtered results`() {
        val entity = EntityFactory.createEntityEntity(
            id = sourceEntityId,
            workspaceId = workspaceId,
            typeId = sourceEntityTypeId,
        )
        val typedRel = EntityFactory.createRelationshipEntity(
            workspaceId = workspaceId,
            sourceId = sourceEntityId,
            targetId = targetEntityId,
            definitionId = typedDefId,
        )
        val typedDefEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = typedDefId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Typed Relationship",
        )

        whenever(entityRepository.findByIdAndWorkspaceId(sourceEntityId, workspaceId)).thenReturn(Optional.of(entity))
        whenever(entityRelationshipRepository.findByEntityIdAndDefinitionId(sourceEntityId, typedDefId, workspaceId))
            .thenReturn(listOf(typedRel))
        whenever(definitionRepository.findAllById(listOf(typedDefId)))
            .thenReturn(listOf(typedDefEntity))

        val result = service.getRelationships(workspaceId, sourceEntityId, typedDefId)

        assertEquals(1, result.size)
        assertEquals("Typed Relationship", result[0].definitionName)
    }

    @Test
    fun `getRelationships - empty - returns empty list`() {
        val entity = EntityFactory.createEntityEntity(
            id = sourceEntityId,
            workspaceId = workspaceId,
            typeId = sourceEntityTypeId,
        )

        whenever(entityRepository.findByIdAndWorkspaceId(sourceEntityId, workspaceId)).thenReturn(Optional.of(entity))
        whenever(entityRelationshipRepository.findAllRelationshipsForEntity(sourceEntityId, workspaceId))
            .thenReturn(emptyList())

        val result = service.getRelationships(workspaceId, sourceEntityId)

        assertTrue(result.isEmpty())
    }

    // ------ updateRelationship ------

    @Test
    fun `updateRelationship - updates semantic context`() {
        val relationshipId = UUID.randomUUID()
        val relationship = EntityFactory.createRelationshipEntity(
            id = relationshipId,
            workspaceId = workspaceId,
            sourceId = sourceEntityId,
            targetId = targetEntityId,
            definitionId = fallbackDefId,
            semanticContext = "old context",
        )
        val fallbackDef = buildFallbackDefinitionEntity()
        val request = UpdateRelationshipRequest(semanticContext = "new context")

        whenever(entityRelationshipRepository.findByIdAndWorkspaceId(relationshipId, workspaceId))
            .thenReturn(Optional.of(relationship))
        whenever(definitionRepository.findById(fallbackDefId))
            .thenReturn(Optional.of(fallbackDef))
        whenever(entityRelationshipRepository.save(any<EntityRelationshipEntity>())).thenAnswer { invocation ->
            invocation.arguments[0] as EntityRelationshipEntity
        }

        val result = service.updateRelationship(workspaceId, relationshipId, request)

        assertEquals("new context", result.semanticContext)
        verify(entityRelationshipRepository).save(argThat<EntityRelationshipEntity> {
            this.semanticContext == "new context"
        })
    }

    @Test
    fun `updateRelationship - works on typed relationships too`() {
        val relationshipId = UUID.randomUUID()
        val relationship = EntityFactory.createRelationshipEntity(
            id = relationshipId,
            workspaceId = workspaceId,
            sourceId = sourceEntityId,
            targetId = targetEntityId,
            definitionId = typedDefId,
            semanticContext = "old context",
        )
        val typedDefEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = typedDefId,
            workspaceId = workspaceId,
            sourceEntityTypeId = sourceEntityTypeId,
            name = "Typed Relationship",
        )
        val request = UpdateRelationshipRequest(semanticContext = "updated typed context")

        whenever(entityRelationshipRepository.findByIdAndWorkspaceId(relationshipId, workspaceId))
            .thenReturn(Optional.of(relationship))
        whenever(definitionRepository.findById(typedDefId))
            .thenReturn(Optional.of(typedDefEntity))
        whenever(entityRelationshipRepository.save(any<EntityRelationshipEntity>())).thenAnswer { invocation ->
            invocation.arguments[0] as EntityRelationshipEntity
        }

        val result = service.updateRelationship(workspaceId, relationshipId, request)

        assertEquals("updated typed context", result.semanticContext)
        assertEquals("Typed Relationship", result.definitionName)
    }

    // ------ removeRelationship ------

    @Test
    fun `removeRelationship - soft-deletes the relationship`() {
        val relationshipId = UUID.randomUUID()
        val relationship = EntityFactory.createRelationshipEntity(
            id = relationshipId,
            workspaceId = workspaceId,
            sourceId = sourceEntityId,
            targetId = targetEntityId,
            definitionId = fallbackDefId,
            semanticContext = "to be deleted",
        )

        whenever(entityRelationshipRepository.findByIdAndWorkspaceId(relationshipId, workspaceId))
            .thenReturn(Optional.of(relationship))
        whenever(entityRelationshipRepository.save(any<EntityRelationshipEntity>())).thenAnswer { invocation ->
            invocation.arguments[0] as EntityRelationshipEntity
        }

        service.removeRelationship(workspaceId, relationshipId)

        verify(entityRelationshipRepository).save(argThat<EntityRelationshipEntity> {
            this.deleted && this.deletedAt != null
        })
    }

    @Test
    fun `removeRelationship - works on typed relationships`() {
        val relationshipId = UUID.randomUUID()
        val relationship = EntityFactory.createRelationshipEntity(
            id = relationshipId,
            workspaceId = workspaceId,
            sourceId = sourceEntityId,
            targetId = targetEntityId,
            definitionId = typedDefId,
        )

        whenever(entityRelationshipRepository.findByIdAndWorkspaceId(relationshipId, workspaceId))
            .thenReturn(Optional.of(relationship))
        whenever(entityRelationshipRepository.save(any<EntityRelationshipEntity>())).thenAnswer { invocation ->
            invocation.arguments[0] as EntityRelationshipEntity
        }

        service.removeRelationship(workspaceId, relationshipId)

        verify(entityRelationshipRepository).save(argThat<EntityRelationshipEntity> {
            this.deleted && this.deletedAt != null
        })
    }

}
