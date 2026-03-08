package riven.core.service.workflow

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
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
import riven.core.models.common.validation.Schema
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.EntityRelationshipService
import riven.core.service.entity.type.EntityTypeRelationshipService
import riven.core.service.workflow.state.EntityContextService
import riven.core.service.workflow.state.WorkflowNodeExpressionEvaluatorService
import java.time.ZonedDateTime
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        EntityContextServiceTest.TestConfig::class,
        EntityContextService::class
    ]
)
class EntityContextServiceTest {

    @Configuration
    class TestConfig

    @MockitoBean
    private lateinit var logger: KLogger

    @MockitoBean
    private lateinit var entityRepository: EntityRepository

    @MockitoBean
    private lateinit var entityTypeRepository: EntityTypeRepository

    @MockitoBean
    private lateinit var entityRelationshipService: EntityRelationshipService

    @MockitoBean
    private lateinit var entityTypeRelationshipService: EntityTypeRelationshipService

    @MockitoBean
    private lateinit var entityAttributeService: riven.core.service.entity.EntityAttributeService

    @Autowired
    private lateinit var entityContextService: EntityContextService

    private val workspaceId = UUID.randomUUID()

    @Test
    fun `buildContext with entity not found throws exception`() {
        val entityId = UUID.randomUUID()
        `when`(entityRepository.findById(entityId)).thenReturn(Optional.empty())

        val exception = assertThrows(IllegalArgumentException::class.java) {
            entityContextService.buildContext(entityId, workspaceId)
        }
        assertTrue(exception.message!!.contains("Entity not found"))
    }

    @Test
    fun `buildContextWithRelationships with simple entity returns correct map`() {
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val statusFieldId = UUID.randomUUID()
        val countFieldId = UUID.randomUUID()

        val schema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = mapOf(
                statusFieldId to Schema(key = SchemaType.TEXT, type = DataType.STRING, label = "status"),
                countFieldId to Schema(key = SchemaType.NUMBER, type = DataType.NUMBER, label = "count")
            )
        )

        val entityType = EntityTypeEntity(
            id = typeId,
            key = "task",
            displayNameSingular = "Task",
            displayNamePlural = "Tasks",
            iconType = IconType.CIRCLE,
            iconColour = IconColour.NEUTRAL,
            identifierKey = statusFieldId,
            workspaceId = workspaceId,
            protected = false,
            version = 1,
            schema = schema,
            columns = emptyList()
        ).apply {
            createdAt = ZonedDateTime.now()
            updatedAt = ZonedDateTime.now()
        }

        val entity = EntityEntity(
            id = entityId,
            workspaceId = workspaceId,
            typeId = typeId,
            typeKey = "task",
            identifierKey = statusFieldId,
            iconType = IconType.FILE,
            iconColour = IconColour.NEUTRAL
        ).apply {
            createdAt = ZonedDateTime.now()
            updatedAt = ZonedDateTime.now()
        }

        `when`(entityRepository.findById(entityId)).thenReturn(Optional.of(entity))
        `when`(entityTypeRepository.findById(typeId)).thenReturn(Optional.of(entityType))
        `when`(entityRelationshipService.findRelatedEntities(entityId, workspaceId)).thenReturn(emptyMap())
        `when`(entityAttributeService.getAttributes(entityId)).thenReturn(mapOf(
            statusFieldId to EntityAttributePrimitivePayload("active", SchemaType.TEXT),
            countFieldId to EntityAttributePrimitivePayload(42, SchemaType.NUMBER)
        ))

        val context = entityContextService.buildContextWithRelationships(entityId, workspaceId)

        assertNotNull(context)
        assertEquals(2, context.size)
        assertEquals("active", context["status"])
        assertEquals(42, context["count"])
    }

    @Test
    fun `buildContextWithRelationships with null values handles gracefully`() {
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val fieldId = UUID.randomUUID()

        val schema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = mapOf(
                fieldId to Schema(key = SchemaType.TEXT, type = DataType.STRING, label = "description")
            )
        )

        val entityType = EntityTypeEntity(
            id = typeId,
            key = "task",
            displayNameSingular = "Task",
            displayNamePlural = "Tasks",
            iconType = IconType.CIRCLE,
            iconColour = IconColour.NEUTRAL,
            identifierKey = fieldId,
            workspaceId = workspaceId,
            protected = false,
            version = 1,
            schema = schema,
            columns = emptyList()
        ).apply {
            createdAt = ZonedDateTime.now()
            updatedAt = ZonedDateTime.now()
        }

        val entity = EntityEntity(
            id = entityId,
            workspaceId = workspaceId,
            typeId = typeId,
            typeKey = "task",
            identifierKey = fieldId,
            iconType = IconType.FILE,
            iconColour = IconColour.NEUTRAL
        ).apply {
            createdAt = ZonedDateTime.now()
            updatedAt = ZonedDateTime.now()
        }

        `when`(entityRepository.findById(entityId)).thenReturn(Optional.of(entity))
        `when`(entityTypeRepository.findById(typeId)).thenReturn(Optional.of(entityType))
        `when`(entityRelationshipService.findRelatedEntities(entityId, workspaceId)).thenReturn(emptyMap())
        `when`(entityAttributeService.getAttributes(entityId)).thenReturn(mapOf(
            fieldId to EntityAttributePrimitivePayload(null, SchemaType.TEXT)
        ))

        val context = entityContextService.buildContextWithRelationships(entityId, workspaceId)

        assertNotNull(context)
        assertEquals(1, context.size)
        assertNull(context["description"])
    }

    @Test
    fun `expression evaluation with entity context works end-to-end`() {
        val entityId = UUID.randomUUID()
        val typeId = UUID.randomUUID()
        val statusFieldId = UUID.randomUUID()
        val countFieldId = UUID.randomUUID()

        val schema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = mapOf(
                statusFieldId to Schema(key = SchemaType.TEXT, type = DataType.STRING, label = "status"),
                countFieldId to Schema(key = SchemaType.NUMBER, type = DataType.NUMBER, label = "count")
            )
        )

        val entityType = EntityTypeEntity(
            id = typeId,
            key = "task",
            displayNameSingular = "Task",
            displayNamePlural = "Tasks",
            iconType = IconType.CIRCLE,
            iconColour = IconColour.NEUTRAL,
            identifierKey = statusFieldId,
            workspaceId = workspaceId,
            protected = false,
            version = 1,
            schema = schema,
            columns = emptyList()
        ).apply {
            createdAt = ZonedDateTime.now()
            updatedAt = ZonedDateTime.now()
        }

        val entity = EntityEntity(
            id = entityId,
            workspaceId = workspaceId,
            typeId = typeId,
            typeKey = "task",
            identifierKey = statusFieldId,
            iconType = IconType.FILE,
            iconColour = IconColour.NEUTRAL
        ).apply {
            createdAt = ZonedDateTime.now()
            updatedAt = ZonedDateTime.now()
        }

        `when`(entityRepository.findById(entityId)).thenReturn(Optional.of(entity))
        `when`(entityTypeRepository.findById(typeId)).thenReturn(Optional.of(entityType))
        `when`(entityRelationshipService.findRelatedEntities(entityId, workspaceId)).thenReturn(emptyMap())
        `when`(entityAttributeService.getAttributes(entityId)).thenReturn(mapOf(
            statusFieldId to EntityAttributePrimitivePayload("active", SchemaType.TEXT),
            countFieldId to EntityAttributePrimitivePayload(15, SchemaType.NUMBER)
        ))

        val context = entityContextService.buildContextWithRelationships(entityId, workspaceId)
        val expressionEvaluator = WorkflowNodeExpressionEvaluatorService()

        val statusExpression = riven.core.models.common.Expression.BinaryOp(
            riven.core.models.common.Expression.PropertyAccess(listOf("status")),
            riven.core.models.common.Operator.EQUALS,
            riven.core.models.common.Expression.Literal("active")
        )

        val countExpression = riven.core.models.common.Expression.BinaryOp(
            riven.core.models.common.Expression.PropertyAccess(listOf("count")),
            riven.core.models.common.Operator.GREATER_THAN,
            riven.core.models.common.Expression.Literal(10)
        )

        val andExpression = riven.core.models.common.Expression.BinaryOp(
            statusExpression,
            riven.core.models.common.Operator.AND,
            countExpression
        )

        val statusResult = expressionEvaluator.evaluate(statusExpression, context)
        val countResult = expressionEvaluator.evaluate(countExpression, context)
        val andResult = expressionEvaluator.evaluate(andExpression, context)

        assertTrue(statusResult as Boolean)
        assertTrue(countResult as Boolean)
        assertTrue(andResult as Boolean)
    }
}
