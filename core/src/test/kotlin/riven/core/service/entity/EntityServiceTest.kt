package riven.core.service.entity

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.entity.activity.ActivityLogEntity
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.EntitySelectType
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.entity.query.FilterOperator
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.models.common.validation.Schema
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.models.entity.payload.EntityAttributeRequest
import riven.core.models.entity.query.EntityQueryResult
import riven.core.models.entity.query.filter.FilterValue
import riven.core.models.entity.query.filter.QueryFilter
import riven.core.models.request.entity.DeleteEntityRequest
import riven.core.models.request.entity.SaveEntityRequest
import riven.core.repository.entity.EntityRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.query.EntityQueryService
import riven.core.service.entity.type.EntityTypeAttributeService
import riven.core.service.entity.type.EntityTypeRelationshipService
import riven.core.service.entity.type.EntityTypeSequenceService
import riven.core.service.entity.type.EntityTypeService
import riven.core.enums.util.OperationType
import riven.core.models.identity.IdentityMatchTriggerEvent
import riven.core.models.websocket.EntityEvent
import riven.core.service.identity.EntityTypeClassificationService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.entity.EntityFactory
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import org.springframework.security.access.AccessDeniedException
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        EntityServiceTest.TestConfig::class,
        EntityService::class,
    ]
)
@RecordApplicationEvents
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

    @MockitoBean private lateinit var entityTypeClassificationService: EntityTypeClassificationService
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
    @MockitoBean private lateinit var entityQueryService: EntityQueryService

    @Autowired
    private lateinit var service: EntityService

    @Autowired
    private lateinit var applicationEvents: ApplicationEvents

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
            entityTypeClassificationService,
            entityRepository, entityTypeService, entityRelationshipService,
            entityTypeRelationshipService, entityValidationService,
            entityTypeAttributeService, entityAttributeService,
            authTokenService, activityService, sequenceService,
            entityQueryService,
        )

        whenever(entityTypeClassificationService.getIdentifierAttributeIds(any())).thenReturn(emptySet())

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

        val existingEntity = EntityFactory.createEntityEntity(
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
    fun `saveEntity publishes EntityEvent with CREATE operation for new entity`() {
        val type = buildEntityType(
            properties = mapOf(
                nameAttrId to Schema(key = SchemaType.TEXT, type = DataType.STRING, label = "Name", required = true),
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

        val events = applicationEvents.stream(EntityEvent::class.java).toList()
        assertEquals(1, events.size)

        val event = events[0]
        assertEquals(workspaceId, event.workspaceId)
        assertEquals(userId, event.userId)
        assertEquals(OperationType.CREATE, event.operation)
        assertEquals(entityId, event.entityId)
        assertEquals(entityTypeId, event.entityTypeId)
        assertEquals("task", event.entityTypeKey)
    }

    @Test
    fun `saveEntity publishes EntityEvent with UPDATE operation for existing entity`() {
        val type = buildEntityType(
            properties = mapOf(
                nameAttrId to Schema(key = SchemaType.TEXT, type = DataType.STRING, label = "Name", required = true),
            ),
        )

        val existingEntity = EntityFactory.createEntityEntity(
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

        val events = applicationEvents.stream(EntityEvent::class.java).toList()
        assertEquals(1, events.size)

        val event = events[0]
        assertEquals(workspaceId, event.workspaceId)
        assertEquals(userId, event.userId)
        assertEquals(OperationType.UPDATE, event.operation)
        assertEquals(entityId, event.entityId)
        assertEquals(entityTypeId, event.entityTypeId)
        assertEquals("task", event.entityTypeKey)
    }

    @Test
    fun `deleteEntities publishes EntityEvent with DELETE operation`() {
        val deletedEntity = EntityFactory.createEntityEntity(
            id = entityId,
            workspaceId = workspaceId,
            typeId = entityTypeId,
            typeKey = "task",
            identifierKey = nameAttrId,
        )

        whenever(entityRepository.deleteByIds(any(), eq(workspaceId))).thenReturn(listOf(deletedEntity))
        whenever(entityRelationshipService.findByTargetIdIn(any())).thenReturn(emptyMap())

        val request = DeleteEntityRequest(
            type = EntitySelectType.BY_ID,
            entityIds = listOf(entityId),
        )
        service.deleteEntities(workspaceId, request)

        val events = applicationEvents.stream(EntityEvent::class.java).toList()
        assertEquals(1, events.size)

        val event = events[0]
        assertEquals(workspaceId, event.workspaceId)
        assertEquals(userId, event.userId)
        assertEquals(OperationType.DELETE, event.operation)
        assertNull(event.entityId)
        assertEquals(entityTypeId, event.entityTypeId)
        assertEquals("task", event.entityTypeKey)
        assertEquals(listOf(entityId), event.summary["deletedIds"])
        assertEquals(1, event.summary["deletedCount"])
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

        val existingEntity = EntityFactory.createEntityEntity(
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

    // ------ IdentityMatchTriggerEvent Tests ------

    @Test
    fun `saveEntity publishes IdentityMatchTriggerEvent with isUpdate=false on create`() {
        val type = buildEntityType(
            properties = mapOf(
                nameAttrId to Schema(key = SchemaType.TEXT, type = DataType.STRING, label = "Name", required = true),
            ),
        )

        whenever(entityTypeService.getById(entityTypeId)).thenReturn(type)
        whenever(entityRepository.save(any<EntityEntity>())).thenAnswer {
            (it.arguments[0] as EntityEntity).copy(id = entityId)
        }
        whenever(entityTypeClassificationService.getIdentifierAttributeIds(entityTypeId))
            .thenReturn(setOf(nameAttrId))

        val request = SaveEntityRequest(
            payload = mapOf(
                nameAttrId to EntityAttributeRequest(
                    payload = EntityAttributePrimitivePayload(value = "My Task", schemaType = SchemaType.TEXT)
                ),
            ),
        )

        service.saveEntity(workspaceId, entityTypeId, request)

        val events = applicationEvents.stream(IdentityMatchTriggerEvent::class.java).toList()
        assertEquals(1, events.size)

        val event = events[0]
        assertEquals(entityId, event.entityId)
        assertEquals(workspaceId, event.workspaceId)
        assertEquals(entityTypeId, event.entityTypeId)
        assertEquals(false, event.isUpdate)
        assertTrue(event.previousIdentifierAttributes.isEmpty())
        assertEquals("My Task", event.newIdentifierAttributes[nameAttrId])
    }

    @Test
    fun `saveEntity publishes IdentityMatchTriggerEvent with isUpdate=true and old and new values on update`() {
        val type = buildEntityType(
            properties = mapOf(
                nameAttrId to Schema(key = SchemaType.TEXT, type = DataType.STRING, label = "Name", required = true),
            ),
        )

        val existingEntity = EntityFactory.createEntityEntity(
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
            )
        )
        whenever(entityTypeClassificationService.getIdentifierAttributeIds(entityTypeId))
            .thenReturn(setOf(nameAttrId))

        val request = SaveEntityRequest(
            id = entityId,
            payload = mapOf(
                nameAttrId to EntityAttributeRequest(
                    payload = EntityAttributePrimitivePayload(value = "Updated Task", schemaType = SchemaType.TEXT)
                ),
            ),
        )

        service.saveEntity(workspaceId, entityTypeId, request)

        val events = applicationEvents.stream(IdentityMatchTriggerEvent::class.java).toList()
        assertEquals(1, events.size)

        val event = events[0]
        assertEquals(entityId, event.entityId)
        assertEquals(workspaceId, event.workspaceId)
        assertEquals(entityTypeId, event.entityTypeId)
        assertEquals(true, event.isUpdate)
        assertEquals("Old Task", event.previousIdentifierAttributes[nameAttrId])
        assertEquals("Updated Task", event.newIdentifierAttributes[nameAttrId])
    }

    @Test
    fun `saveEntity publishes IdentityMatchTriggerEvent with only IDENTIFIER attributes filtered`() {
        val nonIdentifierAttrId = UUID.randomUUID()
        val type = buildEntityType(
            properties = mapOf(
                nameAttrId to Schema(key = SchemaType.TEXT, type = DataType.STRING, label = "Name", required = true),
                nonIdentifierAttrId to Schema(key = SchemaType.TEXT, type = DataType.STRING, label = "Notes"),
            ),
        )

        whenever(entityTypeService.getById(entityTypeId)).thenReturn(type)
        whenever(entityRepository.save(any<EntityEntity>())).thenAnswer {
            (it.arguments[0] as EntityEntity).copy(id = entityId)
        }
        // Only nameAttrId is classified as IDENTIFIER, not nonIdentifierAttrId
        whenever(entityTypeClassificationService.getIdentifierAttributeIds(entityTypeId))
            .thenReturn(setOf(nameAttrId))

        val request = SaveEntityRequest(
            payload = mapOf(
                nameAttrId to EntityAttributeRequest(
                    payload = EntityAttributePrimitivePayload(value = "My Task", schemaType = SchemaType.TEXT)
                ),
                nonIdentifierAttrId to EntityAttributeRequest(
                    payload = EntityAttributePrimitivePayload(value = "Some note", schemaType = SchemaType.TEXT)
                ),
            ),
        )

        service.saveEntity(workspaceId, entityTypeId, request)

        val events = applicationEvents.stream(IdentityMatchTriggerEvent::class.java).toList()
        assertEquals(1, events.size)

        val event = events[0]
        assertTrue(event.newIdentifierAttributes.containsKey(nameAttrId))
        assertFalse(event.newIdentifierAttributes.containsKey(nonIdentifierAttrId))
    }

    // ------ Access Denied Tests ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@test.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "00000000-0000-0000-0000-000000000000",
                role = WorkspaceRoles.OWNER
            )
        ]
    )
    inner class UnauthorizedAccessTests {

        /**
         * Verifies that saveEntity rejects requests when the authenticated user
         * does not have access to the target workspace. The @PreAuthorize annotation
         * on the service method should trigger an AccessDeniedException before any
         * business logic executes.
         */
        @Test
        fun `saveEntity throws AccessDeniedException for unauthorized workspace`() {
            val request = SaveEntityRequest(
                payload = mapOf(
                    nameAttrId to EntityAttributeRequest(
                        payload = EntityAttributePrimitivePayload(value = "My Task", schemaType = SchemaType.TEXT)
                    ),
                ),
            )

            assertThrows(AccessDeniedException::class.java) {
                service.saveEntity(workspaceId, entityTypeId, request)
            }
        }

        /**
         * Verifies that deleteEntities rejects requests when the authenticated user
         * does not have access to the target workspace. The @PreAuthorize annotation
         * on the service method should trigger an AccessDeniedException before any
         * business logic executes.
         */
        @Test
        fun `deleteEntities throws AccessDeniedException for unauthorized workspace`() {
            val request = DeleteEntityRequest(
                type = EntitySelectType.BY_ID,
                entityIds = listOf(entityId),
            )
            assertThrows(AccessDeniedException::class.java) {
                service.deleteEntities(workspaceId, request)
            }
        }
    }

    // ------ Bulk Delete Tests ------

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
    inner class BulkDeleteEntities {

        private val typeId = UUID.randomUUID()

        @Test
        fun `BY_ID deletes entities and logs single bulk activity`() {
            val entity1 = EntityFactory.createEntityEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                typeId = typeId,
                typeKey = "task",
            )
            val entity2 = EntityFactory.createEntityEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                typeId = typeId,
                typeKey = "task",
            )
            val ids = listOf(
                requireNotNull(entity1.id),
                requireNotNull(entity2.id),
            )

            whenever(entityRelationshipService.findByTargetIdIn(any())).thenReturn(emptyMap())
            whenever(entityRepository.deleteByIds(any(), eq(workspaceId))).thenReturn(listOf(entity1, entity2))
            whenever(entityTypeAttributeService.deleteEntities(eq(workspaceId), any())).thenReturn(0)

            val request = DeleteEntityRequest(
                type = EntitySelectType.BY_ID,
                entityIds = ids,
            )

            val result = service.deleteEntities(workspaceId, request)

            assertEquals(2, result.deletedCount)

            val captor = argumentCaptor<List<ActivityLogEntity>>()
            verify(activityService).logActivities(captor.capture())
            assertEquals(1, captor.firstValue.size)
            assertEquals(OperationType.DELETE, captor.firstValue[0].operation)
            val details = captor.firstValue[0].details
            assertEquals(true, (details?.get("deletedIds") as? List<*>)?.isNotEmpty())
        }

        @Test
        fun `ALL mode with filter and excludeIds resolves IDs and excludes correctly`() {
            val entity1 = EntityFactory.createEntityEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                typeId = typeId,
                typeKey = "task",
            )
            val entity2 = EntityFactory.createEntityEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                typeId = typeId,
                typeKey = "task",
            )
            val entity3 = EntityFactory.createEntityEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                typeId = typeId,
                typeKey = "task",
            )
            val id1 = requireNotNull(entity1.id)
            val id2 = requireNotNull(entity2.id)
            val id3 = requireNotNull(entity3.id)

            val queryResult = EntityQueryResult(
                entities = listOf(
                    entity1.toModel(audit = false, relationships = emptyMap(), attributes = emptyMap()),
                    entity2.toModel(audit = false, relationships = emptyMap(), attributes = emptyMap()),
                    entity3.toModel(audit = false, relationships = emptyMap(), attributes = emptyMap()),
                ),
                hasNextPage = false,
                totalCount = null,
                projection = null,
            )

            runBlocking {
                whenever(entityQueryService.execute(any(), eq(workspaceId), any(), isNull(), eq(false)))
                    .thenReturn(queryResult)
            }

            whenever(entityRelationshipService.findByTargetIdIn(any())).thenReturn(emptyMap())
            whenever(entityRepository.deleteByIds(any(), eq(workspaceId))).thenReturn(listOf(entity1, entity2))
            whenever(entityTypeAttributeService.deleteEntities(eq(workspaceId), any())).thenReturn(0)

            val attrId = UUID.randomUUID()
            val request = DeleteEntityRequest(
                type = EntitySelectType.ALL,
                entityTypeId = typeId,
                filter = QueryFilter.Attribute(
                    attributeId = attrId,
                    operator = FilterOperator.EQUALS,
                    value = FilterValue.Literal("active"),
                ),
                excludeIds = listOf(id3),
            )

            val result = service.deleteEntities(workspaceId, request)

            assertEquals(2, result.deletedCount)

            // Verify deleteByIds was called with IDs that do NOT contain the excluded ID
            val idsCaptor = argumentCaptor<Array<UUID>>()
            verify(entityRepository).deleteByIds(idsCaptor.capture(), eq(workspaceId))
            val deletedIds = idsCaptor.firstValue.toList()
            assertFalse(deletedIds.contains(id3), "Excluded ID should not be in delete list")
        }

        @Test
        fun `empty result throws NotFoundException and does not log activity`() {
            whenever(entityRelationshipService.findByTargetIdIn(any())).thenReturn(emptyMap())
            whenever(entityRepository.deleteByIds(any(), eq(workspaceId))).thenReturn(emptyList())

            val request = DeleteEntityRequest(
                type = EntitySelectType.BY_ID,
                entityIds = listOf(UUID.randomUUID(), UUID.randomUUID()),
            )

            assertThrows(riven.core.exceptions.NotFoundException::class.java) {
                service.deleteEntities(workspaceId, request)
            }

            verify(activityService, never()).logActivities(any())
        }

        @Test
        fun `impacted entities are returned when relationships exist`() {
            val deletedEntity = EntityFactory.createEntityEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                typeId = typeId,
                typeKey = "task",
            )
            val impactedEntity = EntityFactory.createEntityEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                typeId = typeId,
                typeKey = "task",
            )
            val deletedId = requireNotNull(deletedEntity.id)
            val impactedId = requireNotNull(impactedEntity.id)

            val relationship = EntityFactory.createRelationshipEntity(
                workspaceId = workspaceId,
                sourceId = impactedId,
                targetId = deletedId,
            )

            whenever(entityRelationshipService.findByTargetIdIn(any()))
                .thenReturn(mapOf(deletedId to listOf(relationship)))
            whenever(entityRepository.deleteByIds(any(), eq(workspaceId)))
                .thenReturn(listOf(deletedEntity))
            whenever(entityTypeAttributeService.deleteEntities(eq(workspaceId), any())).thenReturn(0)
            whenever(entityRepository.findByIdInAndWorkspaceId(any(), eq(workspaceId)))
                .thenReturn(listOf(impactedEntity))
            whenever(entityRelationshipService.findRelatedEntities(any<Set<UUID>>(), eq(workspaceId)))
                .thenReturn(emptyMap())
            whenever(entityAttributeService.getAttributesForEntities(any()))
                .thenReturn(emptyMap())

            val request = DeleteEntityRequest(
                type = EntitySelectType.BY_ID,
                entityIds = listOf(deletedId),
            )

            val result = service.deleteEntities(workspaceId, request)

            assertNotNull(result.updatedEntities)
            val allUpdated = result.updatedEntities!!.values.flatten()
            assertTrue(allUpdated.any { it.id == impactedId })
        }

        @Test
        fun `BY_ID with more than 500 entities processes in multiple batches`() {
            val batchSize = 500
            val totalCount = 502
            val entities = (1..totalCount).map { _ ->
                EntityFactory.createEntityEntity(
                    id = UUID.randomUUID(),
                    workspaceId = workspaceId,
                    typeId = typeId,
                    typeKey = "task",
                )
            }
            val ids = entities.map { requireNotNull(it.id) }

            whenever(entityRelationshipService.findByTargetIdIn(any())).thenReturn(emptyMap())
            // First batch returns 500, second returns 2
            whenever(entityRepository.deleteByIds(any(), eq(workspaceId)))
                .thenReturn(entities.take(batchSize))
                .thenReturn(entities.drop(batchSize))
            whenever(entityTypeAttributeService.deleteEntities(eq(workspaceId), any())).thenReturn(0)

            val request = DeleteEntityRequest(
                type = EntitySelectType.BY_ID,
                entityIds = ids,
            )

            val result = service.deleteEntities(workspaceId, request)

            assertEquals(totalCount, result.deletedCount)

            // Verify deleteByIds called twice (once per batch)
            verify(entityRepository, times(2)).deleteByIds(any(), eq(workspaceId))

            // Verify cascade operations called for each batch
            verify(entityTypeAttributeService, times(2)).deleteEntities(eq(workspaceId), any())
            verify(entityAttributeService, times(2)).softDeleteByEntityIds(eq(workspaceId), any())
            verify(entityRelationshipService, times(2)).archiveEntities(any(), eq(workspaceId))
        }

        @Test
        fun `ALL mode with multi-page results resolves all pages`() {
            val page1Entities = (1..3).map { _ ->
                EntityFactory.createEntityEntity(
                    id = UUID.randomUUID(),
                    workspaceId = workspaceId,
                    typeId = typeId,
                    typeKey = "task",
                )
            }
            val page2Entities = (1..2).map { _ ->
                EntityFactory.createEntityEntity(
                    id = UUID.randomUUID(),
                    workspaceId = workspaceId,
                    typeId = typeId,
                    typeKey = "task",
                )
            }
            val allEntities = page1Entities + page2Entities

            val page1Result = EntityQueryResult(
                entities = page1Entities.map {
                    it.toModel(audit = false, relationships = emptyMap(), attributes = emptyMap())
                },
                hasNextPage = true,
                totalCount = null,
                projection = null,
            )
            val page2Result = EntityQueryResult(
                entities = page2Entities.map {
                    it.toModel(audit = false, relationships = emptyMap(), attributes = emptyMap())
                },
                hasNextPage = false,
                totalCount = null,
                projection = null,
            )

            runBlocking {
                whenever(entityQueryService.execute(any(), eq(workspaceId), any(), isNull(), eq(false)))
                    .thenReturn(page1Result)
                    .thenReturn(page2Result)
            }

            whenever(entityRelationshipService.findByTargetIdIn(any())).thenReturn(emptyMap())
            whenever(entityRepository.deleteByIds(any(), eq(workspaceId))).thenReturn(allEntities)
            whenever(entityTypeAttributeService.deleteEntities(eq(workspaceId), any())).thenReturn(0)

            val attrId = UUID.randomUUID()
            val request = DeleteEntityRequest(
                type = EntitySelectType.ALL,
                entityTypeId = typeId,
                filter = QueryFilter.Attribute(
                    attributeId = attrId,
                    operator = FilterOperator.EQUALS,
                    value = FilterValue.Literal("active"),
                ),
            )

            val result = service.deleteEntities(workspaceId, request)

            assertEquals(5, result.deletedCount)

            // Verify all 5 entity IDs were passed to deleteByIds
            val idsCaptor = argumentCaptor<Array<UUID>>()
            verify(entityRepository).deleteByIds(idsCaptor.capture(), eq(workspaceId))
            assertEquals(5, idsCaptor.firstValue.size)

            // Verify query service called twice (two pages)
            runBlocking {
                verify(entityQueryService, times(2)).execute(any(), eq(workspaceId), any(), isNull(), eq(false))
            }
        }

        @Test
        fun `impacted entity that is also being deleted is excluded from updated entities`() {
            val entityA = EntityFactory.createEntityEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                typeId = typeId,
                typeKey = "task",
            )
            val entityB = EntityFactory.createEntityEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                typeId = typeId,
                typeKey = "task",
            )
            val idA = requireNotNull(entityA.id)
            val idB = requireNotNull(entityB.id)

            // B has a relationship targeting A, so B would be "impacted" by A's deletion
            val relationship = EntityFactory.createRelationshipEntity(
                workspaceId = workspaceId,
                sourceId = idB,
                targetId = idA,
            )

            // But we're deleting BOTH A and B, so B should not appear in updatedEntities
            whenever(entityRelationshipService.findByTargetIdIn(any()))
                .thenReturn(mapOf(idA to listOf(relationship)))
            whenever(entityRepository.deleteByIds(any(), eq(workspaceId)))
                .thenReturn(listOf(entityA, entityB))
            whenever(entityTypeAttributeService.deleteEntities(eq(workspaceId), any())).thenReturn(0)

            val request = DeleteEntityRequest(
                type = EntitySelectType.BY_ID,
                entityIds = listOf(idA, idB),
            )

            val result = service.deleteEntities(workspaceId, request)

            assertEquals(2, result.deletedCount)
            // B is in the delete set, so it should be filtered out of impacted entities
            assertNull(result.updatedEntities, "Entities being deleted should not appear as impacted")
        }
    }

}
