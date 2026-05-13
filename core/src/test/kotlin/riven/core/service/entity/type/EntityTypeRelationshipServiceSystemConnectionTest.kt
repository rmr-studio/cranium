package cranium.core.service.entity.type

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import cranium.core.configuration.auth.WorkspaceSecurity
import cranium.core.entity.entity.RelationshipDefinitionEntity
import cranium.core.enums.entity.EntityRelationshipCardinality
import cranium.core.enums.entity.SystemRelationshipType
import cranium.core.enums.workspace.WorkspaceRoles
import cranium.core.repository.entity.EntityRelationshipRepository
import cranium.core.repository.entity.EntityTypeRepository
import cranium.core.repository.entity.RelationshipDefinitionRepository
import cranium.core.repository.entity.RelationshipTargetRuleRepository
import cranium.core.service.activity.ActivityService
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.entity.EntityTypeSemanticMetadataService
import cranium.core.service.util.BaseServiceTest
import cranium.core.service.util.WithUserPersona
import cranium.core.service.util.WorkspaceRole
import cranium.core.service.util.factory.entity.EntityFactory
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        EntityTypeRelationshipServiceSystemConnectionTest.TestConfig::class,
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
class EntityTypeRelationshipServiceSystemConnectionTest : BaseServiceTest() {

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

    private val entityTypeId = UUID.randomUUID()

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

    // ------ createSystemConnectionDefinition ------

    @Test
    fun `createSystemConnectionDefinition - creates with correct properties`() {
        val entityType = EntityFactory.createEntityType(id = entityTypeId, workspaceId = workspaceId)
        whenever(entityTypeRepository.findById(entityTypeId)).thenReturn(Optional.of(entityType))
        whenever(definitionRepository.save(any<RelationshipDefinitionEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as RelationshipDefinitionEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }

        val result = service.createSystemConnectionDefinition(workspaceId, entityTypeId)

        assertNotNull(result.id)
        assertTrue(result.protected)
        assertEquals(EntityRelationshipCardinality.MANY_TO_MANY, result.cardinalityDefault)
        assertEquals(SystemRelationshipType.SYSTEM_CONNECTION, result.systemType)
        assertEquals(workspaceId, result.workspaceId)
        assertEquals(entityTypeId, result.sourceEntityTypeId)
        assertEquals("Connected Entities", result.name)

        verify(definitionRepository).save(argThat<RelationshipDefinitionEntity> {
            this.protected &&
                cardinalityDefault == EntityRelationshipCardinality.MANY_TO_MANY &&
                systemType == SystemRelationshipType.SYSTEM_CONNECTION
        })
    }

    /**
     * Regression test: verifies that createSystemConnectionDefinition rejects an entity type
     * whose workspaceId does not match the requested workspaceId. Without the workspace
     * ownership check in createSystemConnectionDefinitionInternal, a caller could create system connection
     * definitions for entity types belonging to other workspaces.
     */
    @Test
    fun `createSystemConnectionDefinition - rejects entity type from different workspace`() {
        val otherWorkspaceId = UUID.randomUUID()
        val entityType = EntityFactory.createEntityType(id = entityTypeId, workspaceId = otherWorkspaceId)
        whenever(entityTypeRepository.findById(entityTypeId)).thenReturn(Optional.of(entityType))

        assertThrows(IllegalArgumentException::class.java) {
            service.createSystemConnectionDefinition(workspaceId, entityTypeId)
        }

        verify(definitionRepository, never()).save(any())
    }

    // ------ getOrCreateSystemConnectionDefinition ------

    @Test
    fun `getOrCreateSystemConnectionDefinition - returns existing when one already exists`() {
        val existingEntity = EntityFactory.createRelationshipDefinitionEntity(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
            sourceEntityTypeId = entityTypeId,
            name = "Connected Entities",
            protected = true,
        )

        whenever(
            definitionRepository.findBySourceEntityTypeIdAndSystemType(
                entityTypeId, SystemRelationshipType.SYSTEM_CONNECTION,
            )
        ).thenReturn(Optional.of(existingEntity))

        val result = service.getOrCreateSystemConnectionDefinition(workspaceId, entityTypeId)

        assertEquals(existingEntity.id, result.id)
        verify(definitionRepository, never()).save(any())
    }

    @Test
    fun `getOrCreateSystemConnectionDefinition - handles concurrent creation with retry`() {
        val entityType = EntityFactory.createEntityType(id = entityTypeId, workspaceId = workspaceId)
        whenever(entityTypeRepository.findById(entityTypeId)).thenReturn(Optional.of(entityType))

        // First call: no existing definition
        whenever(
            definitionRepository.findBySourceEntityTypeIdAndSystemType(
                entityTypeId, SystemRelationshipType.SYSTEM_CONNECTION,
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

        val result = service.getOrCreateSystemConnectionDefinition(workspaceId, entityTypeId)

        assertNotNull(result.id)
        // Verify it attempted save, then retried with a read
        verify(definitionRepository).save(any())
        verify(definitionRepository, times(2))
            .findBySourceEntityTypeIdAndSystemType(
                entityTypeId, SystemRelationshipType.SYSTEM_CONNECTION,
            )
    }

    // ------ getSystemConnectionDefinitionId ------

    @Test
    fun `getSystemConnectionDefinitionId - returns UUID when system connection exists`() {
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
                entityTypeId, SystemRelationshipType.SYSTEM_CONNECTION,
            )
        ).thenReturn(Optional.of(existingEntity))

        val result = service.getSystemConnectionDefinitionId(entityTypeId)

        assertEquals(defId, result)
    }

    @Test
    fun `getSystemConnectionDefinitionId - returns null when no system connection exists`() {
        whenever(
            definitionRepository.findBySourceEntityTypeIdAndSystemType(
                entityTypeId, SystemRelationshipType.SYSTEM_CONNECTION,
            )
        ).thenReturn(Optional.empty())

        val result = service.getSystemConnectionDefinitionId(entityTypeId)

        assertNull(result)
    }
}
