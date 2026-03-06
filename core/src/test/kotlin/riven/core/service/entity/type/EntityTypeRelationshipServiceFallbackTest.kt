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
import riven.core.entity.entity.RelationshipDefinitionEntity
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.SystemRelationshipType
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
import riven.core.service.util.factory.entity.EntityFactory
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        EntityTypeRelationshipServiceFallbackTest.TestConfig::class,
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
class EntityTypeRelationshipServiceFallbackTest : BaseServiceTest() {

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

    private val entityTypeId = UUID.randomUUID()

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

    // ------ createFallbackDefinition ------

    @Test
    fun `createFallbackDefinition - creates with correct properties`() {
        whenever(definitionRepository.save(any<RelationshipDefinitionEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as RelationshipDefinitionEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }

        val result = service.createFallbackDefinition(workspaceId, entityTypeId)

        assertNotNull(result.id)
        assertTrue(result.protected)
        assertTrue(result.allowPolymorphic)
        assertEquals(EntityRelationshipCardinality.MANY_TO_MANY, result.cardinalityDefault)
        assertEquals(SystemRelationshipType.CONNECTED_ENTITIES, result.systemType)
        assertEquals(workspaceId, result.workspaceId)
        assertEquals(entityTypeId, result.sourceEntityTypeId)
        assertEquals("Connected Entities", result.name)

        verify(definitionRepository).save(argThat<RelationshipDefinitionEntity> {
            this.protected &&
                allowPolymorphic &&
                cardinalityDefault == EntityRelationshipCardinality.MANY_TO_MANY &&
                systemType == SystemRelationshipType.CONNECTED_ENTITIES
        })
    }

    // ------ getOrCreateFallbackDefinition ------

    @Test
    fun `getOrCreateFallbackDefinition - returns existing when one already exists`() {
        val existingEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
            sourceEntityTypeId = entityTypeId,
            name = "Connected Entities",
            protected = true,
            allowPolymorphic = true,
        )

        whenever(
            definitionRepository.findBySourceEntityTypeIdAndSystemType(
                entityTypeId, SystemRelationshipType.CONNECTED_ENTITIES,
            )
        ).thenReturn(Optional.of(existingEntity))

        val result = service.getOrCreateFallbackDefinition(workspaceId, entityTypeId)

        assertEquals(existingEntity.id, result.id)
        verify(definitionRepository, never()).save(any())
    }

    @Test
    fun `getOrCreateFallbackDefinition - handles concurrent creation with retry`() {
        // First call: no existing definition
        whenever(
            definitionRepository.findBySourceEntityTypeIdAndSystemType(
                entityTypeId, SystemRelationshipType.CONNECTED_ENTITIES,
            )
        ).thenReturn(Optional.empty())
            .thenReturn(Optional.of(
                EntityFactory.createRelationshipDefinitionEntity(
                    id = UUID.randomUUID(),
                    workspaceId = workspaceId,
                    sourceEntityTypeId = entityTypeId,
                    name = "Connected Entities",
                    protected = true,
                )
            ))

        // Save throws DataIntegrityViolationException (concurrent insert)
        whenever(definitionRepository.save(any<RelationshipDefinitionEntity>()))
            .thenThrow(DataIntegrityViolationException("Duplicate key"))

        val result = service.getOrCreateFallbackDefinition(workspaceId, entityTypeId)

        assertNotNull(result.id)
        // Verify it attempted save, then retried with a read
        verify(definitionRepository).save(any())
        verify(definitionRepository, times(2))
            .findBySourceEntityTypeIdAndSystemType(
                entityTypeId, SystemRelationshipType.CONNECTED_ENTITIES,
            )
    }

    // ------ getFallbackDefinitionId ------

    @Test
    fun `getFallbackDefinitionId - returns UUID when fallback exists`() {
        val defId = UUID.randomUUID()
        val existingEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = defId,
            workspaceId = workspaceId,
            sourceEntityTypeId = entityTypeId,
            name = "Connected Entities",
            protected = true,
        )

        whenever(
            definitionRepository.findBySourceEntityTypeIdAndSystemType(
                entityTypeId, SystemRelationshipType.CONNECTED_ENTITIES,
            )
        ).thenReturn(Optional.of(existingEntity))

        val result = service.getFallbackDefinitionId(entityTypeId)

        assertEquals(defId, result)
    }

    @Test
    fun `getFallbackDefinitionId - returns null when no fallback exists`() {
        whenever(
            definitionRepository.findBySourceEntityTypeIdAndSystemType(
                entityTypeId, SystemRelationshipType.CONNECTED_ENTITIES,
            )
        ).thenReturn(Optional.empty())

        val result = service.getFallbackDefinitionId(entityTypeId)

        assertNull(result)
    }
}
