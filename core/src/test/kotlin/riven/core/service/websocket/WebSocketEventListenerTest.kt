package riven.core.service.websocket

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.messaging.simp.SimpMessagingTemplate
import riven.core.enums.util.OperationType
import riven.core.enums.websocket.WebSocketChannel
import riven.core.models.websocket.*
import io.github.oshai.kotlinlogging.KLogger
import java.util.UUID

class WebSocketEventListenerTest {

    private val messagingTemplate: SimpMessagingTemplate = mock()
    private val logger: KLogger = mock()
    private val listener = WebSocketEventListener(messagingTemplate, logger)

    private val workspaceId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @Test
    fun `entity event is sent to correct workspace entities topic`() {
        val entityId = UUID.randomUUID()
        val entityTypeId = UUID.randomUUID()
        val event = EntityEvent(
            workspaceId = workspaceId,
            userId = userId,
            operation = OperationType.CREATE,
            entityId = entityId,
            entityTypeId = entityTypeId,
            entityTypeKey = "contact",
            summary = mapOf("entityTypeName" to "Contact"),
        )

        listener.onWorkspaceEvent(event)

        verify(messagingTemplate).convertAndSend(
            eq("/topic/workspace/$workspaceId/entities"),
            any<WebSocketMessage>()
        )
    }

    @Test
    fun `block environment event is sent to correct workspace blocks topic`() {
        val layoutId = UUID.randomUUID()
        val event = BlockEnvironmentEvent(
            workspaceId = workspaceId,
            userId = userId,
            operation = OperationType.UPDATE,
            entityId = null,
            layoutId = layoutId,
            version = 5,
            summary = mapOf("operationCount" to 3),
        )

        listener.onWorkspaceEvent(event)

        verify(messagingTemplate).convertAndSend(
            eq("/topic/workspace/$workspaceId/blocks"),
            any<WebSocketMessage>()
        )
    }

    @Test
    fun `workflow event is sent to correct workspace workflows topic`() {
        val event = WorkflowEvent(
            workspaceId = workspaceId,
            userId = userId,
            operation = OperationType.UPDATE,
            entityId = UUID.randomUUID(),
            summary = emptyMap(),
        )

        listener.onWorkspaceEvent(event)

        verify(messagingTemplate).convertAndSend(
            eq("/topic/workspace/$workspaceId/workflows"),
            any<WebSocketMessage>()
        )
    }

    @Test
    fun `workspace change event is sent to correct workspace topic`() {
        val event = WorkspaceChangeEvent(
            workspaceId = workspaceId,
            userId = userId,
            operation = OperationType.UPDATE,
            entityId = workspaceId,
            summary = mapOf("name" to "My Workspace"),
        )

        listener.onWorkspaceEvent(event)

        verify(messagingTemplate).convertAndSend(
            eq("/topic/workspace/$workspaceId/workspace"),
            any<WebSocketMessage>()
        )
    }

    @Test
    fun `message payload contains correct fields from event`() {
        val entityId = UUID.randomUUID()
        val entityTypeId = UUID.randomUUID()
        val event = EntityEvent(
            workspaceId = workspaceId,
            userId = userId,
            operation = OperationType.CREATE,
            entityId = entityId,
            entityTypeId = entityTypeId,
            entityTypeKey = "company",
            summary = mapOf("entityTypeName" to "Company"),
        )

        listener.onWorkspaceEvent(event)

        val captor = argumentCaptor<WebSocketMessage>()
        verify(messagingTemplate).convertAndSend(any<String>(), captor.capture())

        val message = captor.firstValue
        assertEquals(WebSocketChannel.ENTITIES, message.channel)
        assertEquals(OperationType.CREATE, message.operation)
        assertEquals(workspaceId, message.workspaceId)
        assertEquals(entityId, message.entityId)
        assertEquals(userId, message.userId)
        assertEquals("Company", message.summary["entityTypeName"])
    }
}
