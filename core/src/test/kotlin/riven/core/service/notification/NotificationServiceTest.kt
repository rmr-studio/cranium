package cranium.core.service.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.bean.override.mockito.MockitoBean
import cranium.core.configuration.auth.WorkspaceSecurity
import cranium.core.entity.notification.NotificationEntity
import cranium.core.entity.notification.NotificationReadEntity
import cranium.core.enums.activity.Activity
import cranium.core.enums.core.ApplicationEntityType
import cranium.core.enums.notification.NotificationReferenceType
import cranium.core.enums.notification.NotificationType
import cranium.core.enums.notification.ReviewPriority
import cranium.core.enums.util.OperationType
import cranium.core.enums.workspace.WorkspaceRoles
import cranium.core.exceptions.NotFoundException
import cranium.core.models.request.notification.CreateNotificationRequest
import cranium.core.models.websocket.NotificationEvent
import cranium.core.repository.notification.NotificationReadRepository
import cranium.core.repository.notification.NotificationRepository
import cranium.core.service.activity.ActivityService
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.util.BaseServiceTest
import cranium.core.service.util.SecurityTestConfig
import cranium.core.service.util.WithUserPersona
import cranium.core.service.util.WorkspaceRole
import cranium.core.service.util.factory.NotificationFactory
import java.time.ZonedDateTime
import java.util.UUID

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        NotificationService::class,
        NotificationServiceTest.EventPublisherConfig::class,
    ]
)
class NotificationServiceTest : BaseServiceTest() {

    @TestConfiguration
    class EventPublisherConfig {
        @Bean
        @Primary
        fun testEventPublisher(): ApplicationEventPublisher = Mockito.mock(ApplicationEventPublisher::class.java)
    }

    @MockitoBean
    private lateinit var notificationRepository: NotificationRepository

    @MockitoBean
    private lateinit var notificationReadRepository: NotificationReadRepository

    @MockitoBean
    private lateinit var activityService: ActivityService

    @Autowired
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @Autowired
    private lateinit var notificationService: NotificationService

    @BeforeEach
    fun resetEventPublisher() {
        Mockito.reset(applicationEventPublisher)
    }

    // ------ Create ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class CreateNotification {

        @Test
        fun `creates workspace-wide information notification`() {
            val request = CreateNotificationRequest(
                workspaceId = workspaceId,
                type = NotificationType.INFORMATION,
                content = NotificationFactory.informationContent(
                    title = "Weekly Report Ready",
                    message = "Your weekly analytics report is ready to view.",
                    sourceLabel = "Analytics Agent",
                ),
            )

            val savedEntity = NotificationFactory.createEntity(
                workspaceId = workspaceId,
                type = NotificationType.INFORMATION,
                content = request.content,
            )
            whenever(notificationRepository.save(any<NotificationEntity>())).thenReturn(savedEntity)

            val result = notificationService.createNotification(request)

            assertEquals(NotificationType.INFORMATION, result.type)
            assertEquals("Weekly Report Ready", result.content.title)
            assertNull(result.userId)

            val captor = argumentCaptor<NotificationEntity>()
            verify(notificationRepository).save(captor.capture())
            assertEquals(workspaceId, captor.firstValue.workspaceId)
            assertNull(captor.firstValue.userId)
        }

        @Test
        fun `rejects SYSTEM type via public API`() {
            val request = CreateNotificationRequest(
                workspaceId = workspaceId,
                type = NotificationType.SYSTEM,
                content = NotificationFactory.systemContent(),
            )

            assertThrows<IllegalArgumentException> {
                notificationService.createNotification(request)
            }
        }

        @Test
        fun `rejects REVIEW_REQUEST type via public API`() {
            val request = CreateNotificationRequest(
                workspaceId = workspaceId,
                type = NotificationType.REVIEW_REQUEST,
                content = NotificationFactory.reviewRequestContent(),
            )

            assertThrows<IllegalArgumentException> {
                notificationService.createNotification(request)
            }
        }

        @Test
        fun `logs activity on notification creation`() {
            val request = CreateNotificationRequest(
                workspaceId = workspaceId,
                type = NotificationType.INFORMATION,
                content = NotificationFactory.informationContent(),
            )

            val savedEntity = NotificationFactory.createEntity(
                workspaceId = workspaceId,
                type = NotificationType.INFORMATION,
                content = request.content,
            )
            whenever(notificationRepository.save(any<NotificationEntity>())).thenReturn(savedEntity)

            notificationService.createNotification(request)

            verify(activityService).logActivity(
                activity = eq(Activity.NOTIFICATION),
                operation = eq(OperationType.CREATE),
                userId = any(),
                workspaceId = eq(workspaceId),
                entityType = eq(ApplicationEntityType.NOTIFICATION),
                entityId = any(),
                timestamp = any(),
                details = any(),
            )
        }

        @Test
        fun `propagates expiresAt to entity`() {
            val expiresAt = ZonedDateTime.now().plusDays(7)
            val request = CreateNotificationRequest(
                workspaceId = workspaceId,
                type = NotificationType.INFORMATION,
                content = NotificationFactory.informationContent(),
                expiresAt = expiresAt,
            )

            val savedEntity = NotificationFactory.createEntity(
                workspaceId = workspaceId,
                type = NotificationType.INFORMATION,
                content = request.content,
                expiresAt = expiresAt,
            )
            whenever(notificationRepository.save(any<NotificationEntity>())).thenReturn(savedEntity)

            notificationService.createNotification(request)

            val captor = argumentCaptor<NotificationEntity>()
            verify(notificationRepository).save(captor.capture())
            assertEquals(expiresAt, captor.firstValue.expiresAt)
        }

        @Test
        fun `publishes NotificationEvent for WebSocket delivery on creation`() {
            val request = CreateNotificationRequest(
                workspaceId = workspaceId,
                type = NotificationType.INFORMATION,
                content = NotificationFactory.informationContent(),
            )

            val savedEntity = NotificationFactory.createEntity(
                workspaceId = workspaceId,
                content = request.content,
            )
            whenever(notificationRepository.save(any<NotificationEntity>())).thenReturn(savedEntity)

            notificationService.createNotification(request)

            val eventCaptor = argumentCaptor<NotificationEvent>()
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture())

            val event = eventCaptor.firstValue
            assertEquals(workspaceId, event.workspaceId)
            assertEquals(OperationType.CREATE, event.operation)
            assertEquals(NotificationType.INFORMATION, event.notificationType)
            assertEquals(savedEntity.id, event.entityId)
        }
    }

    // ------ Internal Create ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class CreateInternalNotification {

        @Test
        fun `creates privileged notification types via internal method`() {
            val targetUserId = UUID.randomUUID()
            val referenceId = UUID.randomUUID()

            val request = CreateNotificationRequest(
                workspaceId = workspaceId,
                userId = targetUserId,
                type = NotificationType.REVIEW_REQUEST,
                content = NotificationFactory.reviewRequestContent(
                    title = "Entity Link Detected",
                    message = "System detected a potential link between Entity A and Entity B.",
                    priority = ReviewPriority.HIGH,
                ),
                referenceType = NotificationReferenceType.ENTITY_RESOLUTION,
                referenceId = referenceId,
            )

            val savedEntity = NotificationFactory.createEntity(
                workspaceId = workspaceId,
                userId = targetUserId,
                type = NotificationType.REVIEW_REQUEST,
                content = request.content,
                referenceType = NotificationReferenceType.ENTITY_RESOLUTION,
                referenceId = referenceId,
            )
            whenever(notificationRepository.save(any<NotificationEntity>())).thenReturn(savedEntity)

            val result = notificationService.createInternalNotification(request)

            assertEquals(NotificationType.REVIEW_REQUEST, result.type)
            assertEquals(targetUserId, result.userId)
            assertEquals(NotificationReferenceType.ENTITY_RESOLUTION, result.referenceType)
            assertEquals(referenceId, result.referenceId)
        }

        @Test
        fun `allows SYSTEM type via internal method`() {
            val request = CreateNotificationRequest(
                workspaceId = workspaceId,
                type = NotificationType.SYSTEM,
                content = NotificationFactory.systemContent(),
            )

            val savedEntity = NotificationFactory.createEntity(
                workspaceId = workspaceId,
                type = NotificationType.SYSTEM,
                content = request.content,
            )
            whenever(notificationRepository.save(any<NotificationEntity>())).thenReturn(savedEntity)

            val result = notificationService.createInternalNotification(request)

            assertEquals(NotificationType.SYSTEM, result.type)
        }
    }

    // ------ Inbox ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class GetInbox {

        @Test
        fun `returns workspace-wide notifications for user`() {
            val notification = NotificationFactory.createEntity(
                workspaceId = workspaceId,
                userId = null,
                content = NotificationFactory.informationContent(title = "Workspace Notification"),
            ).also { it.createdAt = ZonedDateTime.now() }
            whenever(notificationRepository.findInbox(eq(workspaceId), eq(userId), any(), any(), any()))
                .thenReturn(listOf(notification))
            whenever(notificationReadRepository.findReadNotificationIds(eq(userId), any()))
                .thenReturn(emptySet())
            whenever(notificationRepository.countUnread(workspaceId, userId))
                .thenReturn(1L)

            val result = notificationService.getInbox(workspaceId, null, 20)

            assertEquals(1, result.notifications.size)
            assertEquals("Workspace Notification", result.notifications[0].content.title)
            assertEquals(false, result.notifications[0].read)
            assertEquals(1L, result.unreadCount)
        }

        @Test
        fun `marks notifications as read when read entry exists`() {
            val notificationId = NotificationFactory.DEFAULT_NOTIFICATION_ID
            val notification = NotificationFactory.createEntity(
                id = notificationId,
                workspaceId = workspaceId,
            ).also { it.createdAt = ZonedDateTime.now() }
            whenever(notificationRepository.findInbox(eq(workspaceId), eq(userId), any(), any(), any()))
                .thenReturn(listOf(notification))
            whenever(notificationReadRepository.findReadNotificationIds(eq(userId), any()))
                .thenReturn(setOf(notificationId))
            whenever(notificationRepository.countUnread(workspaceId, userId))
                .thenReturn(0L)

            val result = notificationService.getInbox(workspaceId, null, 20)

            assertEquals(true, result.notifications[0].read)
            assertEquals(0L, result.unreadCount)
        }

        @Test
        fun `returns nextCursor when page is full`() {
            val now = ZonedDateTime.now()
            val notifications = (1..3).map { i ->
                NotificationFactory.createEntity(
                    id = UUID.randomUUID(),
                    workspaceId = workspaceId,
                    content = NotificationFactory.informationContent(title = "Notification $i"),
                ).also { it.createdAt = now.minusMinutes(i.toLong()) }
            }
            whenever(notificationRepository.findInbox(eq(workspaceId), eq(userId), any(), any(), any()))
                .thenReturn(notifications)
            whenever(notificationReadRepository.findReadNotificationIds(eq(userId), any()))
                .thenReturn(emptySet())
            whenever(notificationRepository.countUnread(workspaceId, userId))
                .thenReturn(3L)

            val result = notificationService.getInbox(workspaceId, null, 3)

            assertNotNull(result.nextCursor)
        }

        @Test
        fun `returns null nextCursor when page is not full`() {
            val notification = NotificationFactory.createEntity(workspaceId = workspaceId)
                .also { it.createdAt = ZonedDateTime.now() }
            whenever(notificationRepository.findInbox(eq(workspaceId), eq(userId), any(), any(), any()))
                .thenReturn(listOf(notification))
            whenever(notificationReadRepository.findReadNotificationIds(eq(userId), any()))
                .thenReturn(emptySet())
            whenever(notificationRepository.countUnread(workspaceId, userId))
                .thenReturn(1L)

            val result = notificationService.getInbox(workspaceId, null, 20)

            assertNull(result.nextCursor)
        }

        @Test
        fun `returns empty inbox when no notifications exist`() {
            whenever(notificationRepository.findInbox(eq(workspaceId), eq(userId), any(), any(), any()))
                .thenReturn(emptyList())
            whenever(notificationRepository.countUnread(workspaceId, userId))
                .thenReturn(0L)

            val result = notificationService.getInbox(workspaceId, null, 20)

            assertEquals(0, result.notifications.size)
            assertNull(result.nextCursor)
            assertEquals(0L, result.unreadCount)
        }

        @Test
        fun `rejects pageSize below 1`() {
            assertThrows<IllegalArgumentException> {
                notificationService.getInbox(workspaceId, null, 0)
            }
        }

        @Test
        fun `rejects pageSize above 100`() {
            assertThrows<IllegalArgumentException> {
                notificationService.getInbox(workspaceId, null, 101)
            }
        }
    }

    // ------ Unread Count ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class GetUnreadCount {

        @Test
        fun `returns unread count from repository`() {
            whenever(notificationRepository.countUnread(workspaceId, userId)).thenReturn(5L)

            val count = notificationService.getUnreadCount(workspaceId)

            assertEquals(5L, count)
        }
    }

    // ------ Mark Read ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class MarkAsRead {

        @Test
        fun `creates read entry for notification`() {
            val notificationId = NotificationFactory.DEFAULT_NOTIFICATION_ID
            val notification = NotificationFactory.createEntity(id = notificationId, workspaceId = workspaceId)

            whenever(notificationRepository.findByIdAndWorkspaceId(notificationId, workspaceId))
                .thenReturn(notification)
            whenever(notificationReadRepository.save(any<NotificationReadEntity>()))
                .thenAnswer { it.arguments[0] }

            notificationService.markAsRead(workspaceId, notificationId)

            val captor = argumentCaptor<NotificationReadEntity>()
            verify(notificationReadRepository).save(captor.capture())
            assertEquals(userId, captor.firstValue.userId)
            assertEquals(notificationId, captor.firstValue.notificationId)
        }

        @Test
        fun `is idempotent -- swallows duplicate constraint violation`() {
            val notificationId = NotificationFactory.DEFAULT_NOTIFICATION_ID
            val notification = NotificationFactory.createEntity(id = notificationId, workspaceId = workspaceId)

            whenever(notificationRepository.findByIdAndWorkspaceId(notificationId, workspaceId))
                .thenReturn(notification)
            whenever(notificationReadRepository.save(any<NotificationReadEntity>()))
                .thenThrow(org.springframework.dao.DataIntegrityViolationException("duplicate"))

            notificationService.markAsRead(workspaceId, notificationId)

            verify(notificationReadRepository).save(any())
        }

        @Test
        fun `throws NotFoundException for unknown notification`() {
            val unknownId = UUID.randomUUID()
            whenever(notificationRepository.findByIdAndWorkspaceId(unknownId, workspaceId))
                .thenReturn(null)

            assertThrows<NotFoundException> {
                notificationService.markAsRead(workspaceId, unknownId)
            }
        }
    }

    // ------ Mark All Read ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class MarkAllAsRead {

        @Test
        fun `delegates to repository bulk insert`() {
            notificationService.markAllAsRead(workspaceId)

            verify(notificationReadRepository).markAllAsRead(workspaceId, userId)
        }
    }

    // ------ Resolve ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class ResolveByReference {

        @Test
        fun `marks all unresolved notifications for reference as resolved`() {
            val referenceId = UUID.randomUUID()
            val notification1 = NotificationFactory.createEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                referenceType = NotificationReferenceType.ENTITY_RESOLUTION,
                referenceId = referenceId,
            )
            val notification2 = NotificationFactory.createEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                referenceType = NotificationReferenceType.ENTITY_RESOLUTION,
                referenceId = referenceId,
            )

            whenever(
                notificationRepository.findUnresolvedByReference(
                    NotificationReferenceType.ENTITY_RESOLUTION,
                    referenceId,
                )
            ).thenReturn(listOf(notification1, notification2))
            whenever(notificationRepository.saveAll(any<List<NotificationEntity>>()))
                .thenAnswer { it.arguments[0] }

            notificationService.resolveByReference(
                NotificationReferenceType.ENTITY_RESOLUTION,
                referenceId,
            )

            val captor = argumentCaptor<List<NotificationEntity>>()
            verify(notificationRepository).saveAll(captor.capture())
            captor.firstValue.forEach { entity ->
                assertEquals(true, entity.resolved)
                assertNotNull(entity.resolvedAt)
            }
        }

        @Test
        fun `no-ops when no unresolved notifications exist for reference`() {
            whenever(
                notificationRepository.findUnresolvedByReference(any(), any())
            ).thenReturn(emptyList())

            notificationService.resolveByReference(
                NotificationReferenceType.WORKFLOW_STEP,
                UUID.randomUUID(),
            )

            verify(notificationRepository, never()).saveAll(any<List<NotificationEntity>>())
        }
    }

    // ------ Delete ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class DeleteNotification {

        @Test
        fun `soft-deletes notification and logs activity`() {
            val notificationId = NotificationFactory.DEFAULT_NOTIFICATION_ID
            val notification = NotificationFactory.createEntity(id = notificationId, workspaceId = workspaceId)

            whenever(notificationRepository.findByIdAndWorkspaceId(notificationId, workspaceId))
                .thenReturn(notification)
            whenever(notificationRepository.save(any<NotificationEntity>())).thenAnswer { it.arguments[0] }

            notificationService.deleteNotification(workspaceId, notificationId)

            val captor = argumentCaptor<NotificationEntity>()
            verify(notificationRepository).save(captor.capture())
            assertEquals(true, captor.firstValue.deleted)
            assertNotNull(captor.firstValue.deletedAt)

            verify(activityService).logActivity(
                activity = eq(Activity.NOTIFICATION),
                operation = eq(OperationType.DELETE),
                userId = any(),
                workspaceId = eq(workspaceId),
                entityType = eq(ApplicationEntityType.NOTIFICATION),
                entityId = eq(notificationId),
                timestamp = any(),
                details = any(),
            )
        }

        @Test
        fun `throws NotFoundException for unknown notification`() {
            val unknownId = UUID.randomUUID()
            whenever(notificationRepository.findByIdAndWorkspaceId(unknownId, workspaceId))
                .thenReturn(null)

            assertThrows<NotFoundException> {
                notificationService.deleteNotification(workspaceId, unknownId)
            }
        }
    }

    // ------ Security ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class WorkspaceAccessControl {

        @Test
        fun `getInbox denies access to other workspace`() {
            val otherWorkspaceId = UUID.randomUUID()

            assertThrows<org.springframework.security.authorization.AuthorizationDeniedException> {
                notificationService.getInbox(otherWorkspaceId, null, 20)
            }
        }

        @Test
        fun `getUnreadCount denies access to other workspace`() {
            val otherWorkspaceId = UUID.randomUUID()

            assertThrows<org.springframework.security.authorization.AuthorizationDeniedException> {
                notificationService.getUnreadCount(otherWorkspaceId)
            }
        }

        @Test
        fun `createNotification denies access to other workspace`() {
            val otherWorkspaceId = UUID.randomUUID()
            val request = CreateNotificationRequest(
                workspaceId = otherWorkspaceId,
                type = NotificationType.INFORMATION,
                content = NotificationFactory.informationContent(),
            )

            assertThrows<org.springframework.security.authorization.AuthorizationDeniedException> {
                notificationService.createNotification(request)
            }
        }

        @Test
        fun `markAsRead denies access to other workspace`() {
            val otherWorkspaceId = UUID.randomUUID()

            assertThrows<org.springframework.security.authorization.AuthorizationDeniedException> {
                notificationService.markAsRead(otherWorkspaceId, UUID.randomUUID())
            }
        }

        @Test
        fun `markAllAsRead denies access to other workspace`() {
            val otherWorkspaceId = UUID.randomUUID()

            assertThrows<org.springframework.security.authorization.AuthorizationDeniedException> {
                notificationService.markAllAsRead(otherWorkspaceId)
            }
        }

        @Test
        fun `deleteNotification denies access to other workspace`() {
            val otherWorkspaceId = UUID.randomUUID()

            assertThrows<org.springframework.security.authorization.AuthorizationDeniedException> {
                notificationService.deleteNotification(otherWorkspaceId, UUID.randomUUID())
            }
        }
    }
}
