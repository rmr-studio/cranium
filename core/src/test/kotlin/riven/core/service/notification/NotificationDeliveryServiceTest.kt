package cranium.core.service.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import cranium.core.enums.notification.NotificationReferenceType
import cranium.core.enums.notification.NotificationType
import cranium.core.enums.notification.ReviewPriority
import cranium.core.models.notification.Notification
import cranium.core.models.request.notification.CreateNotificationRequest
import cranium.core.service.util.BaseServiceTest
import cranium.core.service.util.factory.NotificationFactory
import java.time.ZonedDateTime
import java.util.UUID

@SpringBootTest(
    classes = [
        NotificationDeliveryService::class,
    ]
)
class NotificationDeliveryServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var notificationService: NotificationService

    @Autowired
    private lateinit var deliveryService: NotificationDeliveryService

    @Test
    fun `resolveByReference delegates to NotificationService`() {
        val referenceId = UUID.randomUUID()

        deliveryService.resolveByReference(
            NotificationReferenceType.WORKFLOW_STEP,
            referenceId,
        )

        verify(notificationService).resolveByReference(
            NotificationReferenceType.WORKFLOW_STEP,
            referenceId,
        )
    }

    @Test
    fun `createForWorkspace creates workspace-wide notification via NotificationService`() {
        val wsId = UUID.randomUUID()
        val content = NotificationFactory.informationContent(
            title = "Weekly Report",
            message = "Your report is ready.",
        )
        val notification = Notification(
            id = UUID.randomUUID(),
            workspaceId = wsId,
            userId = null,
            type = NotificationType.INFORMATION,
            content = content,
            referenceType = null,
            referenceId = null,
            resolved = false,
            resolvedAt = null,
            expiresAt = null,
            createdAt = null,
            updatedAt = null,
            createdBy = null,
            updatedBy = null,
        )

        whenever(notificationService.createInternalNotification(any())).thenReturn(notification)

        deliveryService.createForWorkspace(
            workspaceId = wsId,
            type = NotificationType.INFORMATION,
            content = content,
        )

        val captor = argumentCaptor<CreateNotificationRequest>()
        verify(notificationService).createInternalNotification(captor.capture())
        assertEquals(wsId, captor.firstValue.workspaceId)
        assertNull(captor.firstValue.userId)
        assertEquals(NotificationType.INFORMATION, captor.firstValue.type)
    }

    @Test
    fun `createForUser creates user-targeted notification via NotificationService`() {
        val wsId = UUID.randomUUID()
        val targetUserId = UUID.randomUUID()
        val referenceId = UUID.randomUUID()
        val content = NotificationFactory.reviewRequestContent(
            title = "Review Required",
            priority = ReviewPriority.HIGH,
        )
        val notification = Notification(
            id = UUID.randomUUID(),
            workspaceId = wsId,
            userId = targetUserId,
            type = NotificationType.REVIEW_REQUEST,
            content = content,
            referenceType = NotificationReferenceType.ENTITY_RESOLUTION,
            referenceId = referenceId,
            resolved = false,
            resolvedAt = null,
            expiresAt = null,
            createdAt = null,
            updatedAt = null,
            createdBy = null,
            updatedBy = null,
        )

        whenever(notificationService.createInternalNotification(any())).thenReturn(notification)

        deliveryService.createForUser(
            workspaceId = wsId,
            userId = targetUserId,
            type = NotificationType.REVIEW_REQUEST,
            content = content,
            referenceType = NotificationReferenceType.ENTITY_RESOLUTION,
            referenceId = referenceId,
        )

        val captor = argumentCaptor<CreateNotificationRequest>()
        verify(notificationService).createInternalNotification(captor.capture())
        assertEquals(targetUserId, captor.firstValue.userId)
        assertEquals(NotificationReferenceType.ENTITY_RESOLUTION, captor.firstValue.referenceType)
        assertEquals(referenceId, captor.firstValue.referenceId)
    }

    @Test
    fun `createForUser forwards expiresAt to CreateNotificationRequest`() {
        val wsId = UUID.randomUUID()
        val targetUserId = UUID.randomUUID()
        val expiresAt = ZonedDateTime.now().plusDays(7)
        val content = NotificationFactory.reviewRequestContent()
        val notification = Notification(
            id = UUID.randomUUID(),
            workspaceId = wsId,
            userId = targetUserId,
            type = NotificationType.REVIEW_REQUEST,
            content = content,
            referenceType = null,
            referenceId = null,
            resolved = false,
            resolvedAt = null,
            expiresAt = expiresAt,
            createdAt = null,
            updatedAt = null,
            createdBy = null,
            updatedBy = null,
        )

        whenever(notificationService.createInternalNotification(any())).thenReturn(notification)

        deliveryService.createForUser(
            workspaceId = wsId,
            userId = targetUserId,
            type = NotificationType.REVIEW_REQUEST,
            content = content,
            expiresAt = expiresAt,
        )

        val captor = argumentCaptor<CreateNotificationRequest>()
        verify(notificationService).createInternalNotification(captor.capture())
        assertEquals(expiresAt, captor.firstValue.expiresAt)
    }
}
