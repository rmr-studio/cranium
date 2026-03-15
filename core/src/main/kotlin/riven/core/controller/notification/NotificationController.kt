package riven.core.controller.notification

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.RestController
import riven.core.models.notification.Notification
import riven.core.models.request.notification.CreateNotificationRequest
import riven.core.models.response.notification.NotificationInboxResponse
import riven.core.service.notification.NotificationService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "notification")
class NotificationController(
    private val notificationService: NotificationService,
) {

    @Operation(summary = "Get notification inbox for the current user in a workspace")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Inbox retrieved"),
        ApiResponse(responseCode = "403", description = "Access denied"),
    )
    @GetMapping("/workspace/{workspaceId}")
    fun getInbox(
        @PathVariable workspaceId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") pageSize: Int,
    ): ResponseEntity<NotificationInboxResponse> =
        ResponseEntity.ok(notificationService.getInbox(workspaceId, cursor, pageSize))

    @Operation(summary = "Get unread notification count for the current user")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Count retrieved"),
        ApiResponse(responseCode = "403", description = "Access denied"),
    )
    @GetMapping("/workspace/{workspaceId}/unread-count")
    fun getUnreadCount(
        @PathVariable workspaceId: UUID,
    ): ResponseEntity<Long> =
        ResponseEntity.ok(notificationService.getUnreadCount(workspaceId))

    @Operation(summary = "Create a notification in a workspace")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Notification created"),
        ApiResponse(responseCode = "403", description = "Access denied"),
    )
    @PostMapping("/workspace/{workspaceId}")
    fun createNotification(
        @PathVariable workspaceId: UUID,
        @Valid @RequestBody request: CreateNotificationRequest,
    ): ResponseEntity<Notification> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            notificationService.createNotification(request.copy(workspaceId = workspaceId))
        )

    @Operation(summary = "Mark a single notification as read")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Marked as read"),
        ApiResponse(responseCode = "404", description = "Notification not found"),
    )
    @PostMapping("/workspace/{workspaceId}/{notificationId}/read")
    fun markAsRead(
        @PathVariable workspaceId: UUID,
        @PathVariable notificationId: UUID,
    ): ResponseEntity<Void> {
        notificationService.markAsRead(workspaceId, notificationId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Mark all notifications as read for the current user")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "All marked as read"),
    )
    @PostMapping("/workspace/{workspaceId}/read-all")
    fun markAllAsRead(
        @PathVariable workspaceId: UUID,
    ): ResponseEntity<Void> {
        notificationService.markAllAsRead(workspaceId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Delete (soft-delete) a notification")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Notification deleted"),
        ApiResponse(responseCode = "404", description = "Notification not found"),
    )
    @DeleteMapping("/workspace/{workspaceId}/{notificationId}")
    fun deleteNotification(
        @PathVariable workspaceId: UUID,
        @PathVariable notificationId: UUID,
    ): ResponseEntity<Void> {
        notificationService.deleteNotification(workspaceId, notificationId)
        return ResponseEntity.noContent().build()
    }
}
