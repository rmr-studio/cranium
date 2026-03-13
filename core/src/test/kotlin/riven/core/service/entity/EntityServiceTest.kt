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
import riven.core.entity.entity.EntityTypeEntity
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.models.common.validation.Schema
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.models.entity.payload.EntityAttributeRequest
import riven.core.models.request.entity.SaveEntityRequest
import riven.core.repository.entity.EntityRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.type.EntityTypeAttributeService
import riven.core.service.entity.type.EntityTypeRelationshipService
import riven.core.service.entity.type.EntityTypeSequenceService
import riven.core.service.entity.type.EntityTypeService
import riven.core.enums.util.OperationType
import riven.core.models.websocket.EntityEvent
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        EntityServiceTest.TestConfig::class,
        EntityService::class,
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
class EntityServiceTest : BaseServiceTest() {

    @Configuration
    class TestConfig

    @MockitoBean private lateinit var entityRepository: EntityRepository
    @MockitoBean private lateinit var entityTypeService: EntityTypeService
    @MockitoBean private lateinit var entityRelationshipService: EntityRelationshipService
    @MockitoBean private lateinit var entityTypeRelationshipService: EntityTypeRelationshipService
    @MockitoBean private lateinit var entityValidationService: EntityValidationService
    @MockitoBean private lateinit var entityTypeAttributeService: EntityTypeAttributeService
    @MockitoBean private lateinit var entityAttributeService: EntityAttributeService
    @MockitoBean private lateinit var authTokenService: AuthTokenService
    @MockitoBean private lateinit var activityService: ActivityService
    @MockitoBean private lateinit var sequenceService: EntityTypeSequenceService
    @MockitoBean private lateinit var applicationEventPublisher: org.springframework.context.ApplicationEventPublisher

    @Autowired
    private lateinit var service: EntityService

    private val nameAttrId = UUID.randomUUID()
    private val statusAttrId = UUID.randomUUID()
    private val idAttrId = UUID.randomUUID()
    private val entityTypeId = UUID.randomUUID()
    private val entityId = UUID.randomUUID()

    private fun buildEntityType(
        properties: Map<UUID, Schema<UUID>>,
        identifierKey: UUID = nameAttrId,
    ): EntityTypeEntity {
        return EntityTypeEntity(
            id = entityTypeId,
            key = "task",
            displayNameSingular = "Task",
            displayNamePlural = "Tasks",
            iconType = IconType.CIRCLE_DASHED,
            iconColour = IconColour.NEUTRAL,
            semanticGroup = SemanticGroup.UNCATEGORIZED,
            identifierKey = identifierKey,
            workspaceId = workspaceId,
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = properties,
            ),
        )
    }

    @BeforeEach
    fun setUp() {
        reset(
            entityRepository, entityTypeService, entityRelationshipService,
            entityTypeRelationshipService, entityValidationService,
            entityTypeAttributeService, entityAttributeService,
            authTokenService, activityService, sequenceService,
        )

        whenever(authTokenService.getUserId()).thenReturn(userId)
        whenever(entityValidationService.validateEntity(any(), any(), any(), any(), any())).thenReturn(emptyList())
        whenever(entityTypeAttributeService.extractUniqueAttributes(any(), any())).thenReturn(emptyMap())
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), anyOrNull(), any(), any())).thenReturn(mock())
        whenever(entityRelationshipService.findRelatedEntities(any<UUID>(), any())).thenReturn(emptyMap())
    }

    @Test
    fun `saveEntity injects default value for attribute not provided in payload`() {
        val type = buildEntityType(
            properties = mapOf(
                nameAttrId to Schema(key = SchemaType.TEXT, type = DataType.STRING, label = "Name", required = true),
                statusAttrId to Schema(
                    key = SchemaType.SELECT, type = DataType.STRING, label = "Status",
                    options = Schema.SchemaOptions(
                        enum = listOf("draft", "active", "done"),
                        default = "draft",
                    ),
                ),
            ),
        )

        whenever(entityTypeService.getById(entityTypeId)).thenReturn(type)
        whenever(entityRepository.save(any<EntityEntity>())).thenAnswer {
            (it.arguments[0] as EntityEntity).copy(id = entityId)
        }

        val request = SaveEntityRequest(
            payload = mapOf(
                nameAttrId to EntityAttributeRequest(
                    payload = EntityAttributePrimitivePayload(value = "My Task", schemaType = SchemaType.TEXT)
                ),
            ),
        )

        service.saveEntity(workspaceId, entityTypeId, request)

        val captor = argumentCaptor<Map<UUID, EntityAttributePrimitivePayload>>()
        verify(entityAttributeService).saveAttributes(
            entityId = eq(entityId),
            workspaceId = eq(workspaceId),
            typeId = eq(entityTypeId),
            attributes = captor.capture(),
        )

        val savedAttrs = captor.firstValue
        assertTrue(savedAttrs.containsKey(statusAttrId), "Default attribute should be present")
        assertEquals("draft", savedAttrs[statusAttrId]?.value)
        assertEquals(SchemaType.SELECT, savedAttrs[statusAttrId]?.schemaType)
    }

    @Test
    fun `saveEntity does not override user-provided value with default`() {
        val type = buildEntityType(
            properties = mapOf(
                nameAttrId to Schema(key = SchemaType.TEXT, type = DataType.STRING, label = "Name", required = true),
                statusAttrId to Schema(
                    key = SchemaType.SELECT, type = DataType.STRING, label = "Status",
                    options = Schema.SchemaOptions(
                        enum = listOf("draft", "active", "done"),
                        default = "draft",
                    ),
                ),
            ),
        )

        whenever(entityTypeService.getById(entityTypeId)).thenReturn(type)
        whenever(entityRepository.save(any<EntityEntity>())).thenAnswer {
            (it.arguments[0] as EntityEntity).copy(id = entityId)
        }

        val request = SaveEntityRequest(
            payload = mapOf(
                nameAttrId to EntityAttributeRequest(
                    payload = EntityAttributePrimitivePayload(value = "My Task", schemaType = SchemaType.TEXT)
                ),
                statusAttrId to EntityAttributeRequest(
                    payload = EntityAttributePrimitivePayload(value = "active", schemaType = SchemaType.SELECT)
                ),
            ),
        )

        service.saveEntity(workspaceId, entityTypeId, request)

        val captor = argumentCaptor<Map<UUID, EntityAttributePrimitivePayload>>()
        verify(entityAttributeService).saveAttributes(
            entityId = eq(entityId),
            workspaceId = eq(workspaceId),
            typeId = eq(entityTypeId),
            attributes = captor.capture(),
        )

        assertEquals("active", captor.firstValue[statusAttrId]?.value)
    }

    @Test
    fun `saveEntity generates sequential ID for new entity with ID attribute`() {
        val type = buildEntityType(
            properties = mapOf(
                nameAttrId to Schema(key = SchemaType.TEXT, type = DataType.STRING, label = "Name", required = true),
                idAttrId to Schema(
                    key = SchemaType.ID, type = DataType.STRING, label = "Reference",
                    options = Schema.SchemaOptions(prefix = "TSK"),
                ),
            ),
        )

        whenever(entityTypeService.getById(entityTypeId)).thenReturn(type)
        whenever(entityRepository.save(any<EntityEntity>())).thenAnswer {
            (it.arguments[0] as EntityEntity).copy(id = entityId)
        }
        whenever(sequenceService.nextValue(entityTypeId, idAttrId)).thenReturn(1L)
        whenever(sequenceService.formatId("TSK", 1L)).thenReturn("TSK-1")

        val request = SaveEntityRequest(
            payload = mapOf(
                nameAttrId to EntityAttributeRequest(
                    payload = EntityAttributePrimitivePayload(value = "My Task", schemaType = SchemaType.TEXT)
                ),
            ),
        )

        service.saveEntity(workspaceId, entityTypeId, request)

        val captor = argumentCaptor<Map<UUID, EntityAttributePrimitivePayload>>()
        verify(entityAttributeService).saveAttributes(
            entityId = eq(entityId),
            workspaceId = eq(workspaceId),
            typeId = eq(entityTypeId),
            attributes = captor.capture(),
        )

        val savedAttrs = captor.firstValue
        assertTrue(savedAttrs.containsKey(idAttrId), "ID attribute should be auto-generated")
        assertEquals("TSK-1", savedAttrs[idAttrId]?.value)
        assertEquals(SchemaType.ID, savedAttrs[idAttrId]?.schemaType)
    }

    @Test
    fun `saveEntity does not regenerate ID on entity update`() {
        val type = buildEntityType(
            properties = mapOf(
                nameAttrId to Schema(key = SchemaType.TEXT, type = DataType.STRING, label = "Name", required = true),
                idAttrId to Schema(
                    key = SchemaType.ID, type = DataType.STRING, label = "Reference",
                    options = Schema.SchemaOptions(prefix = "TSK"),
                ),
            ),
        )

        val existingEntity = EntityEntity(
            id = entityId,
            workspaceId = workspaceId,
            typeId = entityTypeId,
            typeKey = "task",
            identifierKey = nameAttrId,
        )

        whenever(entityTypeService.getById(entityTypeId)).thenReturn(type)
        whenever(entityRepository.findById(entityId)).thenReturn(Optional.of(existingEntity))
        whenever(entityRepository.save(any<EntityEntity>())).thenAnswer { it.arguments[0] }
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(
            mapOf(
                nameAttrId to EntityAttributePrimitivePayload(value = "Old Task", schemaType = SchemaType.TEXT),
                idAttrId to EntityAttributePrimitivePayload(value = "TSK-1", schemaType = SchemaType.ID),
            )
        )

        val request = SaveEntityRequest(
            id = entityId,
            payload = mapOf(
                nameAttrId to EntityAttributeRequest(
                    payload = EntityAttributePrimitivePayload(value = "Updated Task", schemaType = SchemaType.TEXT)
                ),
                idAttrId to EntityAttributeRequest(
                    payload = EntityAttributePrimitivePayload(value = "TSK-1", schemaType = SchemaType.ID)
                ),
            ),
        )

        service.saveEntity(workspaceId, entityTypeId, request)

        verify(sequenceService, never()).nextValue(any(), any())
    }

    @Test
    fun `saveEntity preserves existing ID value on update even when not in payload`() {
        val type = buildEntityType(
            properties = mapOf(
                nameAttrId to Schema(key = SchemaType.TEXT, type = DataType.STRING, label = "Name", required = true),
                idAttrId to Schema(
                    key = SchemaType.ID, type = DataType.STRING, label = "Reference",
                    options = Schema.SchemaOptions(prefix = "TSK"),
                ),
            ),
        )

        val existingEntity = EntityEntity(
            id = entityId,
            workspaceId = workspaceId,
            typeId = entityTypeId,
            typeKey = "task",
            identifierKey = nameAttrId,
        )

        whenever(entityTypeService.getById(entityTypeId)).thenReturn(type)
        whenever(entityRepository.findById(entityId)).thenReturn(Optional.of(existingEntity))
        whenever(entityRepository.save(any<EntityEntity>())).thenAnswer { it.arguments[0] }
        whenever(entityAttributeService.getAttributes(entityId)).thenReturn(
            mapOf(
                nameAttrId to EntityAttributePrimitivePayload(value = "Old Task", schemaType = SchemaType.TEXT),
                idAttrId to EntityAttributePrimitivePayload(value = "TSK-1", schemaType = SchemaType.ID),
            )
        )

        val request = SaveEntityRequest(
            id = entityId,
            payload = mapOf(
                nameAttrId to EntityAttributeRequest(
                    payload = EntityAttributePrimitivePayload(value = "Updated Task", schemaType = SchemaType.TEXT)
                ),
            ),
        )

        service.saveEntity(workspaceId, entityTypeId, request)

        val captor = argumentCaptor<Map<UUID, EntityAttributePrimitivePayload>>()
        verify(entityAttributeService).saveAttributes(
            entityId = eq(entityId),
            workspaceId = eq(workspaceId),
            typeId = eq(entityTypeId),
            attributes = captor.capture(),
        )

        assertEquals("TSK-1", captor.firstValue[idAttrId]?.value)
        verify(sequenceService, never()).nextValue(any(), any())
    }

}
