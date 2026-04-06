package riven.core.service.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.entity.entity.EntityTypeSemanticMetadataEntity
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.identity.MatchSignalType
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.exceptions.NotFoundException
import riven.core.models.common.validation.Schema
import riven.core.models.request.entity.type.BulkSaveSemanticMetadataRequest
import riven.core.models.request.entity.type.SaveSemanticMetadataRequest
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.EntityTypeSemanticMetadataRepository
import riven.core.service.auth.AuthTokenService
import riven.core.service.identity.EntityTypeClassificationService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import java.util.Optional
import java.util.UUID

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        EntityTypeSemanticMetadataService::class,
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
class EntityTypeSemanticMetadataServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var repository: EntityTypeSemanticMetadataRepository

    @MockitoBean
    private lateinit var entityTypeRepository: EntityTypeRepository

    @MockitoBean
    private lateinit var classificationService: EntityTypeClassificationService

    @Autowired
    private lateinit var service: EntityTypeSemanticMetadataService

    private lateinit var testEntityTypeId: UUID
    private lateinit var testEntityType: EntityTypeEntity

    @BeforeEach
    fun setup() {
        reset(repository, entityTypeRepository, classificationService)

        testEntityTypeId = UUID.randomUUID()
        testEntityType = createTestEntityType(testEntityTypeId, workspaceId)

        // Default: entity type belongs to workspace
        whenever(entityTypeRepository.findById(testEntityTypeId)).thenReturn(Optional.of(testEntityType))
    }

    // ------ Read operations ------

    @Test
    fun `getForEntityType - returns metadata when exists`() {
        val metadataEntity = createTestMetadata(
            entityTypeId = testEntityTypeId,
            targetType = SemanticMetadataTargetType.ENTITY_TYPE,
            targetId = testEntityTypeId,
            definition = "A customer entity",
        )
        whenever(
            repository.findByEntityTypeIdAndTargetTypeAndTargetId(
                testEntityTypeId, SemanticMetadataTargetType.ENTITY_TYPE, testEntityTypeId
            )
        ).thenReturn(Optional.of(metadataEntity))

        val result = service.getForEntityType(workspaceId, testEntityTypeId)

        assertNotNull(result)
        assertEquals("A customer entity", result.definition)
        assertEquals(testEntityTypeId, result.entityTypeId)
        assertEquals(SemanticMetadataTargetType.ENTITY_TYPE, result.targetType)
    }

    @Test
    fun `getForEntityType - throws NotFoundException when entity type not found`() {
        whenever(entityTypeRepository.findById(testEntityTypeId)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.getForEntityType(workspaceId, testEntityTypeId)
        }
    }

    @Test
    fun `getForEntityType - throws when entity type belongs to different workspace`() {
        val differentWorkspaceEntityType = createTestEntityType(testEntityTypeId, UUID.randomUUID())
        whenever(entityTypeRepository.findById(testEntityTypeId)).thenReturn(Optional.of(differentWorkspaceEntityType))

        assertThrows<IllegalArgumentException> {
            service.getForEntityType(workspaceId, testEntityTypeId)
        }
    }

    @Test
    fun `getAttributeMetadata - returns all attribute metadata for entity type`() {
        val attrId1 = UUID.randomUUID()
        val attrId2 = UUID.randomUUID()
        val metadataList = listOf(
            createTestMetadata(entityTypeId = testEntityTypeId, targetType = SemanticMetadataTargetType.ATTRIBUTE, targetId = attrId1),
            createTestMetadata(entityTypeId = testEntityTypeId, targetType = SemanticMetadataTargetType.ATTRIBUTE, targetId = attrId2),
        )
        whenever(
            repository.findByEntityTypeIdAndTargetType(testEntityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
        ).thenReturn(metadataList)

        val result = service.getAttributeMetadata(workspaceId, testEntityTypeId)

        assertEquals(2, result.size)
        result.forEach { assertEquals(SemanticMetadataTargetType.ATTRIBUTE, it.targetType) }
    }

    @Test
    fun `getRelationshipMetadata - returns all relationship metadata for entity type`() {
        val relId = UUID.randomUUID()
        val metadataList = listOf(
            createTestMetadata(entityTypeId = testEntityTypeId, targetType = SemanticMetadataTargetType.RELATIONSHIP, targetId = relId),
        )
        whenever(
            repository.findByEntityTypeIdAndTargetType(testEntityTypeId, SemanticMetadataTargetType.RELATIONSHIP)
        ).thenReturn(metadataList)

        val result = service.getRelationshipMetadata(workspaceId, testEntityTypeId)

        assertEquals(1, result.size)
        assertEquals(SemanticMetadataTargetType.RELATIONSHIP, result.first().targetType)
    }

    // ------ Mutations ------

    @Test
    fun `upsertMetadata - creates new metadata when none exists`() {
        val targetId = UUID.randomUUID()
        val request = SaveSemanticMetadataRequest(
            definition = "Customer name",
            classification = SemanticAttributeClassification.IDENTIFIER,
            tags = listOf("pii", "required"),
        )

        whenever(
            repository.findByEntityTypeIdAndTargetTypeAndTargetId(
                testEntityTypeId, SemanticMetadataTargetType.ATTRIBUTE, targetId
            )
        ).thenReturn(Optional.empty())

        val savedEntity = createTestMetadata(
            entityTypeId = testEntityTypeId,
            targetType = SemanticMetadataTargetType.ATTRIBUTE,
            targetId = targetId,
            definition = request.definition,
            classification = request.classification,
            tags = request.tags,
        )
        whenever(repository.save(any<EntityTypeSemanticMetadataEntity>())).thenReturn(savedEntity)

        val result = service.upsertMetadata(workspaceId, testEntityTypeId, SemanticMetadataTargetType.ATTRIBUTE, targetId, request)

        verify(repository).save(argThat { entity ->
            entity.definition == "Customer name" &&
                entity.classification == SemanticAttributeClassification.IDENTIFIER &&
                entity.tags == listOf("pii", "required") &&
                entity.targetType == SemanticMetadataTargetType.ATTRIBUTE &&
                entity.targetId == targetId
        })
        assertEquals("Customer name", result.definition)
        assertEquals(SemanticAttributeClassification.IDENTIFIER, result.classification)
        verify(classificationService).invalidate(testEntityTypeId)
    }

    @Test
    fun `upsertMetadata - updates existing metadata with full replacement (PUT semantics)`() {
        val targetId = UUID.randomUUID()
        val existingEntity = createTestMetadata(
            entityTypeId = testEntityTypeId,
            targetType = SemanticMetadataTargetType.ATTRIBUTE,
            targetId = targetId,
            definition = "Old definition",
            classification = SemanticAttributeClassification.FREETEXT,
            tags = listOf("old-tag"),
        )

        whenever(
            repository.findByEntityTypeIdAndTargetTypeAndTargetId(
                testEntityTypeId, SemanticMetadataTargetType.ATTRIBUTE, targetId
            )
        ).thenReturn(Optional.of(existingEntity))

        val updatedEntity = createTestMetadata(
            entityTypeId = testEntityTypeId,
            targetType = SemanticMetadataTargetType.ATTRIBUTE,
            targetId = targetId,
            definition = "New definition",
            classification = SemanticAttributeClassification.CATEGORICAL,
            tags = listOf("new-tag"),
        )
        whenever(repository.save(any<EntityTypeSemanticMetadataEntity>())).thenReturn(updatedEntity)

        val request = SaveSemanticMetadataRequest(
            definition = "New definition",
            classification = SemanticAttributeClassification.CATEGORICAL,
            tags = listOf("new-tag"),
        )

        val result = service.upsertMetadata(workspaceId, testEntityTypeId, SemanticMetadataTargetType.ATTRIBUTE, targetId, request)

        // Verify all fields replaced (PUT semantics)
        verify(repository).save(argThat { entity ->
            entity.definition == "New definition" &&
                entity.classification == SemanticAttributeClassification.CATEGORICAL &&
                entity.tags == listOf("new-tag")
        })
        assertEquals("New definition", result.definition)
        assertEquals(SemanticAttributeClassification.CATEGORICAL, result.classification)
        verify(classificationService).invalidate(testEntityTypeId)
    }

    @Test
    fun `bulkUpsertAttributeMetadata - creates and updates multiple attributes`() {
        val attr1Id = UUID.randomUUID()
        val attr2Id = UUID.randomUUID()

        // attr1 is existing, attr2 is new
        val existingAttr1 = createTestMetadata(
            entityTypeId = testEntityTypeId,
            targetType = SemanticMetadataTargetType.ATTRIBUTE,
            targetId = attr1Id,
            definition = "Old definition for attr1",
        )

        whenever(
            repository.findByEntityTypeIdAndTargetType(testEntityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
        ).thenReturn(listOf(existingAttr1))

        val savedEntities = listOf(
            createTestMetadata(entityTypeId = testEntityTypeId, targetType = SemanticMetadataTargetType.ATTRIBUTE, targetId = attr1Id, definition = "Updated attr1"),
            createTestMetadata(entityTypeId = testEntityTypeId, targetType = SemanticMetadataTargetType.ATTRIBUTE, targetId = attr2Id, definition = "New attr2"),
        )
        whenever(repository.saveAll(any<List<EntityTypeSemanticMetadataEntity>>())).thenReturn(savedEntities)

        val requests = listOf(
            BulkSaveSemanticMetadataRequest(targetId = attr1Id, definition = "Updated attr1"),
            BulkSaveSemanticMetadataRequest(targetId = attr2Id, definition = "New attr2"),
        )

        val result = service.bulkUpsertAttributeMetadata(workspaceId, testEntityTypeId, requests)

        assertEquals(2, result.size)
        verify(repository).saveAll(argThat<List<EntityTypeSemanticMetadataEntity>> { entities ->
            entities.size == 2 &&
                entities.any { it.targetId == attr1Id } &&
                entities.any { it.targetId == attr2Id }
        })
        verify(classificationService).invalidate(testEntityTypeId)
    }

    // ------ Lifecycle hooks ------

    @Test
    fun `initializeForEntityType - creates metadata for entity type and all initial attributes`() {
        val attr1Id = UUID.randomUUID()
        val attr2Id = UUID.randomUUID()

        whenever(repository.saveAll(any<List<EntityTypeSemanticMetadataEntity>>())).thenAnswer { invocation ->
            invocation.getArgument(0) as List<EntityTypeSemanticMetadataEntity>
        }

        service.initializeForEntityType(
            entityTypeId = testEntityTypeId,
            workspaceId = workspaceId,
            attributeIds = listOf(attr1Id, attr2Id),
        )

        // Verify saveAll called with 1 entity type record + 2 attribute records = 3 total
        verify(repository).saveAll(argThat<List<EntityTypeSemanticMetadataEntity>> { entities ->
            entities.size == 3 &&
                entities.any {
                    it.targetType == SemanticMetadataTargetType.ENTITY_TYPE && it.targetId == testEntityTypeId
                } &&
                entities.any {
                    it.targetType == SemanticMetadataTargetType.ATTRIBUTE && it.targetId == attr1Id
                } &&
                entities.any {
                    it.targetType == SemanticMetadataTargetType.ATTRIBUTE && it.targetId == attr2Id
                }
        })
    }

    @Test
    fun `initializeForTarget - creates single metadata record for new attribute`() {
        val attributeId = UUID.randomUUID()
        val savedEntity = createTestMetadata(
            entityTypeId = testEntityTypeId,
            targetType = SemanticMetadataTargetType.ATTRIBUTE,
            targetId = attributeId,
        )
        whenever(repository.save(any<EntityTypeSemanticMetadataEntity>())).thenReturn(savedEntity)

        service.initializeForTarget(
            entityTypeId = testEntityTypeId,
            workspaceId = workspaceId,
            targetType = SemanticMetadataTargetType.ATTRIBUTE,
            targetId = attributeId,
        )

        verify(repository).save(argThat { entity ->
            entity.entityTypeId == testEntityTypeId &&
                entity.targetType == SemanticMetadataTargetType.ATTRIBUTE &&
                entity.targetId == attributeId &&
                entity.definition == null &&
                entity.classification == null &&
                entity.tags.isEmpty()
        })
    }

    @Test
    fun `deleteForTarget - hard deletes metadata for removed attribute`() {
        val attributeId = UUID.randomUUID()

        service.deleteForTarget(testEntityTypeId, SemanticMetadataTargetType.ATTRIBUTE, attributeId)

        verify(repository).hardDeleteByTarget(
            eq(testEntityTypeId),
            eq(SemanticMetadataTargetType.ATTRIBUTE),
            eq(attributeId),
        )
        verify(classificationService).invalidate(testEntityTypeId)
    }

    @Test
    fun `softDeleteForEntityType - soft deletes all metadata for an entity type`() {
        whenever(repository.softDeleteByEntityTypeId(testEntityTypeId)).thenReturn(3)

        service.softDeleteForEntityType(testEntityTypeId)

        verify(repository).softDeleteByEntityTypeId(eq(testEntityTypeId))
        verify(classificationService).invalidate(testEntityTypeId)
    }

    @Test
    fun `upsertMetadata - explicit signalType overrides existing value`() {
        val targetId = UUID.randomUUID()
        val existingEntity = createTestMetadata(
            entityTypeId = testEntityTypeId,
            targetType = SemanticMetadataTargetType.ATTRIBUTE,
            targetId = targetId,
            classification = SemanticAttributeClassification.IDENTIFIER,
        ).apply { signalType = MatchSignalType.CUSTOM_IDENTIFIER }

        whenever(
            repository.findByEntityTypeIdAndTargetTypeAndTargetId(
                testEntityTypeId, SemanticMetadataTargetType.ATTRIBUTE, targetId
            )
        ).thenReturn(Optional.of(existingEntity))

        val updatedEntity = createTestMetadata(
            entityTypeId = testEntityTypeId,
            targetType = SemanticMetadataTargetType.ATTRIBUTE,
            targetId = targetId,
            classification = SemanticAttributeClassification.IDENTIFIER,
        ).apply { signalType = MatchSignalType.EMAIL }
        whenever(repository.save(any<EntityTypeSemanticMetadataEntity>())).thenReturn(updatedEntity)

        val request = SaveSemanticMetadataRequest(
            classification = SemanticAttributeClassification.IDENTIFIER,
            signalType = MatchSignalType.EMAIL,
        )

        service.upsertMetadata(workspaceId, testEntityTypeId, SemanticMetadataTargetType.ATTRIBUTE, targetId, request)

        verify(repository).save(argThat { entity ->
            entity.signalType == MatchSignalType.EMAIL
        })
        verify(classificationService).invalidate(testEntityTypeId)
    }

    @Test
    fun `upsertMetadata - omitted signalType preserves existing value`() {
        val targetId = UUID.randomUUID()
        val existingEntity = createTestMetadata(
            entityTypeId = testEntityTypeId,
            targetType = SemanticMetadataTargetType.ATTRIBUTE,
            targetId = targetId,
            classification = SemanticAttributeClassification.IDENTIFIER,
        ).apply { signalType = MatchSignalType.PHONE }

        whenever(
            repository.findByEntityTypeIdAndTargetTypeAndTargetId(
                testEntityTypeId, SemanticMetadataTargetType.ATTRIBUTE, targetId
            )
        ).thenReturn(Optional.of(existingEntity))

        val updatedEntity = createTestMetadata(
            entityTypeId = testEntityTypeId,
            targetType = SemanticMetadataTargetType.ATTRIBUTE,
            targetId = targetId,
            classification = SemanticAttributeClassification.IDENTIFIER,
        ).apply { signalType = MatchSignalType.PHONE }
        whenever(repository.save(any<EntityTypeSemanticMetadataEntity>())).thenReturn(updatedEntity)

        val request = SaveSemanticMetadataRequest(
            classification = SemanticAttributeClassification.IDENTIFIER,
            // signalType omitted — should preserve PHONE
        )

        service.upsertMetadata(workspaceId, testEntityTypeId, SemanticMetadataTargetType.ATTRIBUTE, targetId, request)

        verify(repository).save(argThat { entity ->
            entity.signalType == MatchSignalType.PHONE
        })
        verify(classificationService).invalidate(testEntityTypeId)
    }

    // ------ restoreForEntityType ------

    @Test
    fun `restoreForEntityType - throws UnsupportedOperationException`() {
        assertThrows<UnsupportedOperationException> {
            service.restoreForEntityType(testEntityTypeId)
        }
    }

    // ------ toModel mapping ------

    @Test
    fun `toModel - maps all entity fields to domain model`() {
        val id = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val entity = EntityTypeSemanticMetadataEntity(
            id = id,
            workspaceId = workspaceId,
            entityTypeId = testEntityTypeId,
            targetType = SemanticMetadataTargetType.ATTRIBUTE,
            targetId = targetId,
            definition = "Test definition",
            classification = SemanticAttributeClassification.IDENTIFIER,
            tags = listOf("pii", "required"),
        )

        val model = entity.toModel()

        assertEquals(id, model.id)
        assertEquals(workspaceId, model.workspaceId)
        assertEquals(testEntityTypeId, model.entityTypeId)
        assertEquals(SemanticMetadataTargetType.ATTRIBUTE, model.targetType)
        assertEquals(targetId, model.targetId)
        assertEquals("Test definition", model.definition)
        assertEquals(SemanticAttributeClassification.IDENTIFIER, model.classification)
        assertEquals(listOf("pii", "required"), model.tags)
    }

    // ------ @PreAuthorize enforcement ------

    @Test
    fun `getForEntityType - throws AccessDeniedException for unauthorized workspace`() {
        val unauthorizedWorkspaceId = UUID.randomUUID()

        assertThrows<org.springframework.security.access.AccessDeniedException> {
            service.getForEntityType(unauthorizedWorkspaceId, testEntityTypeId)
        }
    }

    @Test
    fun `upsertMetadata - throws AccessDeniedException for unauthorized workspace`() {
        val unauthorizedWorkspaceId = UUID.randomUUID()
        val request = SaveSemanticMetadataRequest(definition = "test")

        assertThrows<org.springframework.security.access.AccessDeniedException> {
            service.upsertMetadata(unauthorizedWorkspaceId, testEntityTypeId, SemanticMetadataTargetType.ATTRIBUTE, UUID.randomUUID(), request)
        }
    }

    // ------ Workspace verification ------

    @Test
    fun `upsertMetadata - rejects when entity type does not belong to workspace`() {
        val differentWorkspaceEntityType = createTestEntityType(testEntityTypeId, UUID.randomUUID())
        whenever(entityTypeRepository.findById(testEntityTypeId)).thenReturn(Optional.of(differentWorkspaceEntityType))

        val request = SaveSemanticMetadataRequest(definition = "some definition")

        assertThrows<IllegalArgumentException> {
            service.upsertMetadata(workspaceId, testEntityTypeId, SemanticMetadataTargetType.ATTRIBUTE, UUID.randomUUID(), request)
        }
    }

    // ------ Helper factories ------

    private fun createTestEntityType(id: UUID, owningWorkspaceId: UUID): EntityTypeEntity {
        val attrId = UUID.randomUUID()
        return EntityTypeEntity(
            id = id,
            key = "test_entity",
            displayNameSingular = "Test Entity",
            displayNamePlural = "Test Entities",
            workspaceId = owningWorkspaceId,
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    attrId to Schema(
                        key = SchemaType.TEXT,
                        type = DataType.STRING,
                        required = true,
                    )
                )
            ),
            columnConfiguration = riven.core.models.entity.configuration.ColumnConfiguration(order = listOf(attrId)),
            identifierKey = attrId,
        )
    }

    private fun createTestMetadata(
        id: UUID = UUID.randomUUID(),
        entityTypeId: UUID = testEntityTypeId,
        workspaceId: UUID = this.workspaceId,
        targetType: SemanticMetadataTargetType = SemanticMetadataTargetType.ATTRIBUTE,
        targetId: UUID = UUID.randomUUID(),
        definition: String? = null,
        classification: SemanticAttributeClassification? = null,
        tags: List<String> = emptyList(),
    ): EntityTypeSemanticMetadataEntity {
        return EntityTypeSemanticMetadataEntity(
            id = id,
            workspaceId = workspaceId,
            entityTypeId = entityTypeId,
            targetType = targetType,
            targetId = targetId,
            definition = definition,
            classification = classification,
            tags = tags,
        )
    }
}
