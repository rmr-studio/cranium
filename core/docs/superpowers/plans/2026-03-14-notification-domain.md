# Notification Domain Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a workspace-scoped notification domain that supports informational, review-request, and system notifications with typed content, reference-based navigation, per-user read tracking, cursor-paginated inbox, resolution lifecycle, and real-time WebSocket delivery via the existing STOMP infrastructure.

**Architecture:** Two-service split — `NotificationService` owns CRUD, inbox queries, read-state, and resolution; `NotificationDeliveryService` listens for domain events and translates them into notifications. Notifications are workspace-scoped with optional user targeting. Content uses a Kotlin sealed class hierarchy (`NotificationContent`) serialized as JSONB via Jackson polymorphic typing. Read state is tracked in a `notification_reads` join table for per-user granularity. Real-time delivery integrates with the existing `WorkspaceEvent` → `WebSocketEventListener` → STOMP pipeline by publishing `NotificationEvent` instances. Cursor-based pagination on `createdAt` for the inbox.

**Tech Stack:** Spring Boot 3.5.3, Kotlin 2.1.21, Spring Data JPA + Hibernate (JSONB via Hypersistence), Jackson polymorphic serialization, existing STOMP WebSocket infrastructure, JUnit 5 + mockito-kotlin.

---

## File Structure

### New Files

| File | Responsibility |
|------|---------------|
| `db/schema/01_tables/notification.sql` | `notifications` + `notification_reads` table definitions |
| `db/schema/02_indexes/notification_indexes.sql` | Composite indexes for inbox query, read-state lookups |
| `enums/notification/NotificationType.kt` | `INFORMATION`, `REVIEW_REQUEST`, `SYSTEM` |
| `enums/notification/NotificationReferenceType.kt` | Domain entity types a notification can reference |
| `enums/notification/ReviewPriority.kt` | `LOW`, `NORMAL`, `HIGH`, `URGENT` |
| `enums/notification/SystemSeverity.kt` | `INFO`, `WARNING`, `ERROR` |
| `models/notification/NotificationContent.kt` | Sealed class hierarchy — typed content per notification type |
| `models/notification/Notification.kt` | Domain model returned by `toModel()` |
| `models/notification/NotificationInboxItem.kt` | Inbox projection — notification + read state |
| `models/request/notification/CreateNotificationRequest.kt` | Request DTO for notification creation |
| `models/response/notification/NotificationInboxResponse.kt` | Paginated inbox response with cursor + unread count |
| `models/websocket/NotificationEvent.kt` | `WorkspaceEvent` subclass for WebSocket delivery |
| `entity/notification/NotificationEntity.kt` | JPA entity — extends `AuditableSoftDeletableEntity` |
| `entity/notification/NotificationReadEntity.kt` | JPA entity — simple join table, no audit/soft-delete |
| `repository/notification/NotificationRepository.kt` | JPQL queries for inbox, unread count, reference lookup |
| `repository/notification/NotificationReadRepository.kt` | Read-state queries |
| `service/notification/NotificationService.kt` | CRUD, inbox, read-state, resolution, WebSocket publishing |
| `service/notification/NotificationDeliveryService.kt` | Domain event listener → notification creation |
| `controller/notification/NotificationController.kt` | REST endpoints for inbox, mark-read, delete |
| `test/.../service/util/factory/NotificationFactory.kt` | Test data factory |
| `test/.../service/notification/NotificationServiceTest.kt` | Unit + security tests |
| `test/.../service/notification/NotificationDeliveryServiceTest.kt` | Event translation tests |

> All source paths are relative to `src/main/kotlin/riven/core/`. Test paths relative to `src/test/kotlin/riven/core/`.

### Modified Files

| File | Change |
|------|--------|
| `enums/activity/Activity.kt` | Add `NOTIFICATION` enum value |
| `enums/core/ApplicationEntityType.kt` | Add `NOTIFICATION` enum value |

---

## Chunk 1: Foundation — Schema, Enums, Models, Entities, Repositories

### Task 1: Database Schema

**Files:**
- Create: `db/schema/01_tables/notification.sql`
- Create: `db/schema/02_indexes/notification_indexes.sql`

- [ ] **Step 1: Create the notifications and notification_reads tables**

Create `db/schema/01_tables/notification.sql`:

```sql
-- ============================================================
-- notifications — workspace-scoped notification inbox entries
-- ============================================================
CREATE TABLE IF NOT EXISTS "notifications"
(
    "id"             UUID PRIMARY KEY             NOT NULL DEFAULT uuid_generate_v4(),
    "workspace_id"   UUID                         NOT NULL REFERENCES public.workspaces (id) ON DELETE CASCADE,
    "user_id"        UUID                         REFERENCES public.users (id) ON DELETE SET NULL,
    "type"           VARCHAR(50)                  NOT NULL CHECK (type IN ('INFORMATION', 'REVIEW_REQUEST', 'SYSTEM')),
    "content"        JSONB                        NOT NULL DEFAULT '{}'::jsonb,
    "reference_type" VARCHAR(50),
    "reference_id"   UUID,
    "resolved"       BOOLEAN                      NOT NULL DEFAULT FALSE,
    "resolved_at"    TIMESTAMP WITH TIME ZONE,
    "expires_at"     TIMESTAMP WITH TIME ZONE,
    "created_at"     TIMESTAMP WITH TIME ZONE     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at"     TIMESTAMP WITH TIME ZONE     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "created_by"     UUID,
    "updated_by"     UUID,
    "deleted"        BOOLEAN                      NOT NULL DEFAULT FALSE,
    "deleted_at"     TIMESTAMP WITH TIME ZONE
);

-- ============================================================
-- notification_reads — per-user read tracking for notifications
-- ============================================================
CREATE TABLE IF NOT EXISTS "notification_reads"
(
    "id"              UUID PRIMARY KEY             NOT NULL DEFAULT uuid_generate_v4(),
    "user_id"         UUID                         NOT NULL REFERENCES public.users (id) ON DELETE CASCADE,
    "notification_id" UUID                         NOT NULL REFERENCES public.notifications (id) ON DELETE CASCADE,
    "read_at"         TIMESTAMP WITH TIME ZONE     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notification_reads_user_notification UNIQUE ("user_id", "notification_id")
);
```

- [ ] **Step 2: Create composite indexes**

Create `db/schema/02_indexes/notification_indexes.sql`:

```sql
-- Inbox query: filter by workspace + optional user targeting (partial: only non-deleted)
DROP INDEX IF EXISTS idx_notifications_workspace_user;
CREATE INDEX IF NOT EXISTS idx_notifications_workspace_user
    ON public.notifications (workspace_id, user_id) WHERE deleted = false;

-- Inbox query: expiry filtering
DROP INDEX IF EXISTS idx_notifications_workspace_expires;
CREATE INDEX IF NOT EXISTS idx_notifications_workspace_expires
    ON public.notifications (workspace_id, expires_at);

-- Cursor pagination: workspace + created_at ordering (partial: only non-deleted)
DROP INDEX IF EXISTS idx_notifications_workspace_created;
CREATE INDEX IF NOT EXISTS idx_notifications_workspace_created
    ON public.notifications (workspace_id, created_at DESC) WHERE deleted = false;

-- Resolution lookup: find notifications by reference to mark resolved
DROP INDEX IF EXISTS idx_notifications_reference;
CREATE INDEX IF NOT EXISTS idx_notifications_reference
    ON public.notifications (reference_type, reference_id);

-- Read-state join: look up read status for a notification
DROP INDEX IF EXISTS idx_notification_reads_notification;
CREATE INDEX IF NOT EXISTS idx_notification_reads_notification
    ON public.notification_reads (notification_id);
```

- [ ] **Step 3: Commit**

```bash
git add db/schema/01_tables/notification.sql db/schema/02_indexes/notification_indexes.sql
git commit -m "feat(notification): add notifications and notification_reads schema"
```

---

### Task 2: Notification Enums

**Files:**
- Create: `src/main/kotlin/riven/core/enums/notification/NotificationType.kt`
- Create: `src/main/kotlin/riven/core/enums/notification/NotificationReferenceType.kt`
- Create: `src/main/kotlin/riven/core/enums/notification/ReviewPriority.kt`
- Create: `src/main/kotlin/riven/core/enums/notification/SystemSeverity.kt`
- Modify: `src/main/kotlin/riven/core/enums/activity/Activity.kt`
- Modify: `src/main/kotlin/riven/core/enums/core/ApplicationEntityType.kt`

- [ ] **Step 1: Create NotificationType enum**

```kotlin
package riven.core.enums.notification

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "NotificationType",
    description = "Category of notification — drives frontend rendering template.",
    enumAsRef = true,
)
enum class NotificationType {
    @JsonProperty("INFORMATION")
    INFORMATION,

    @JsonProperty("REVIEW_REQUEST")
    REVIEW_REQUEST,

    @JsonProperty("SYSTEM")
    SYSTEM,
}
```

- [ ] **Step 2: Create NotificationReferenceType enum**

```kotlin
package riven.core.enums.notification

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "NotificationReferenceType",
    description = "Domain entity type that a notification references for navigation.",
    enumAsRef = true,
)
enum class NotificationReferenceType {
    @JsonProperty("ENTITY_RESOLUTION")
    ENTITY_RESOLUTION,

    @JsonProperty("WORKFLOW_STEP")
    WORKFLOW_STEP,

    @JsonProperty("WORKFLOW_DEFINITION")
    WORKFLOW_DEFINITION,

    @JsonProperty("ENTITY")
    ENTITY,

    @JsonProperty("ENTITY_TYPE")
    ENTITY_TYPE,

    @JsonProperty("WORKSPACE")
    WORKSPACE,
}
```

- [ ] **Step 3: Create ReviewPriority enum**

```kotlin
package riven.core.enums.notification

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "ReviewPriority",
    description = "Priority level for review request notifications.",
    enumAsRef = true,
)
enum class ReviewPriority {
    @JsonProperty("LOW")
    LOW,

    @JsonProperty("NORMAL")
    NORMAL,

    @JsonProperty("HIGH")
    HIGH,

    @JsonProperty("URGENT")
    URGENT,
}
```

- [ ] **Step 4: Create SystemSeverity enum**

```kotlin
package riven.core.enums.notification

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "SystemSeverity",
    description = "Severity level for system notifications.",
    enumAsRef = true,
)
enum class SystemSeverity {
    @JsonProperty("INFO")
    INFO,

    @JsonProperty("WARNING")
    WARNING,

    @JsonProperty("ERROR")
    ERROR,
}
```

- [ ] **Step 5: Add NOTIFICATION to Activity enum**

In `src/main/kotlin/riven/core/enums/activity/Activity.kt`, add `NOTIFICATION` after `ONBOARDING`:

```kotlin
    ONBOARDING,
    NOTIFICATION
```

- [ ] **Step 6: Add NOTIFICATION to ApplicationEntityType enum**

In `src/main/kotlin/riven/core/enums/core/ApplicationEntityType.kt`, add `NOTIFICATION` after `FILE`:

```kotlin
    FILE,
    NOTIFICATION
```

- [ ] **Step 7: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/riven/core/enums/notification/ src/main/kotlin/riven/core/enums/activity/Activity.kt src/main/kotlin/riven/core/enums/core/ApplicationEntityType.kt
git commit -m "feat(notification): add notification enums and update Activity/ApplicationEntityType"
```

---

### Task 3: Sealed Class and Domain Models

**Files:**
- Create: `src/main/kotlin/riven/core/models/notification/NotificationContent.kt`
- Create: `src/main/kotlin/riven/core/models/notification/Notification.kt`
- Create: `src/main/kotlin/riven/core/models/notification/NotificationInboxItem.kt`
- Create: `src/main/kotlin/riven/core/models/request/notification/CreateNotificationRequest.kt`
- Create: `src/main/kotlin/riven/core/models/response/notification/NotificationInboxResponse.kt`
- Create: `src/main/kotlin/riven/core/models/websocket/NotificationEvent.kt`

- [ ] **Step 1: Create NotificationContent sealed class**

This is the polymorphic content payload stored as JSONB. Jackson's `@JsonTypeInfo` embeds a `type` discriminator in the JSON for deserialization. Each subclass carries only the fields relevant to its notification category.

```kotlin
package riven.core.models.notification

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import riven.core.enums.notification.ReviewPriority
import riven.core.enums.notification.SystemSeverity

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(NotificationContent.Information::class, name = "INFORMATION"),
    JsonSubTypes.Type(NotificationContent.ReviewRequest::class, name = "REVIEW_REQUEST"),
    JsonSubTypes.Type(NotificationContent.System::class, name = "SYSTEM"),
)
sealed class NotificationContent {
    abstract val title: String
    abstract val message: String

    data class Information(
        override val title: String,
        override val message: String,
        val sourceLabel: String? = null,
    ) : NotificationContent()

    data class ReviewRequest(
        override val title: String,
        override val message: String,
        val contextSummary: String? = null,
        val priority: ReviewPriority = ReviewPriority.NORMAL,
    ) : NotificationContent()

    data class System(
        override val title: String,
        override val message: String,
        val severity: SystemSeverity = SystemSeverity.INFO,
    ) : NotificationContent()
}
```

- [ ] **Step 2: Create Notification domain model**

```kotlin
package riven.core.models.notification

import riven.core.enums.notification.NotificationReferenceType
import riven.core.enums.notification.NotificationType
import java.time.ZonedDateTime
import java.util.UUID

data class Notification(
    val id: UUID,
    val workspaceId: UUID,
    val userId: UUID?,
    val type: NotificationType,
    val content: NotificationContent,
    val referenceType: NotificationReferenceType?,
    val referenceId: UUID?,
    val resolved: Boolean,
    val resolvedAt: ZonedDateTime?,
    val expiresAt: ZonedDateTime?,
    val createdAt: ZonedDateTime?,
    val updatedAt: ZonedDateTime?,
    val createdBy: UUID?,
    val updatedBy: UUID?,
)
```

- [ ] **Step 3: Create NotificationInboxItem projection**

This combines notification data with per-user read state for inbox display.

```kotlin
package riven.core.models.notification

import riven.core.enums.notification.NotificationReferenceType
import riven.core.enums.notification.NotificationType
import java.time.ZonedDateTime
import java.util.UUID

data class NotificationInboxItem(
    val id: UUID,
    val type: NotificationType,
    val content: NotificationContent,
    val referenceType: NotificationReferenceType?,
    val referenceId: UUID?,
    val resolved: Boolean,
    val read: Boolean,
    val createdAt: ZonedDateTime,
)
```

- [ ] **Step 4: Create CreateNotificationRequest DTO**

```kotlin
package riven.core.models.request.notification

import riven.core.enums.notification.NotificationReferenceType
import riven.core.enums.notification.NotificationType
import riven.core.models.notification.NotificationContent
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Internal DTO for notification creation — used by both the REST controller
 * and NotificationDeliveryService. The workspaceId is included here (rather
 * than as a separate parameter) so that the delivery service can construct
 * a complete request from domain event data without needing a path variable.
 */
data class CreateNotificationRequest(
    val workspaceId: UUID,
    val userId: UUID? = null,
    val type: NotificationType,
    val content: NotificationContent,
    val referenceType: NotificationReferenceType? = null,
    val referenceId: UUID? = null,
    val expiresAt: ZonedDateTime? = null,
)
```

- [ ] **Step 5: Create NotificationInboxResponse**

```kotlin
package riven.core.models.response.notification

import riven.core.models.notification.NotificationInboxItem
import java.time.ZonedDateTime

data class NotificationInboxResponse(
    val notifications: List<NotificationInboxItem>,
    val nextCursor: ZonedDateTime?,
    val unreadCount: Long,
)
```

- [ ] **Step 6: Create NotificationEvent for WebSocket delivery**

This implements `WorkspaceEvent` so the existing `WebSocketEventListener` automatically broadcasts it to `/topic/workspace/{wsId}/notifications`.

```kotlin
package riven.core.models.websocket

import riven.core.enums.notification.NotificationType
import riven.core.enums.util.OperationType
import riven.core.enums.websocket.WebSocketChannel
import java.util.UUID

/**
 * Published when a notification is created, resolved, or deleted.
 * The existing WebSocketEventListener broadcasts this to the NOTIFICATIONS channel.
 */
data class NotificationEvent(
    override val workspaceId: UUID,
    override val userId: UUID,
    override val operation: OperationType,
    override val entityId: UUID?,
    val notificationType: NotificationType,
    override val summary: Map<String, Any?> = emptyMap(),
) : WorkspaceEvent {
    override val channel: WebSocketChannel = WebSocketChannel.NOTIFICATIONS
}
```

- [ ] **Step 7: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/riven/core/models/notification/ src/main/kotlin/riven/core/models/request/notification/ src/main/kotlin/riven/core/models/response/notification/ src/main/kotlin/riven/core/models/websocket/NotificationEvent.kt
git commit -m "feat(notification): add sealed content hierarchy, domain models, and WebSocket event"
```

---

### Task 4: JPA Entities

**Files:**
- Create: `src/main/kotlin/riven/core/entity/notification/NotificationEntity.kt`
- Create: `src/main/kotlin/riven/core/entity/notification/NotificationReadEntity.kt`

- [ ] **Step 1: Create NotificationEntity**

User-facing, workspace-scoped entity with soft-delete and audit columns. The `content` column uses Hypersistence `JsonBinaryType` for JSONB storage of the `NotificationContent` sealed class — Jackson's `@JsonTypeInfo` discriminator handles polymorphic deserialization automatically.

```kotlin
package riven.core.entity.notification

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.Type
import riven.core.entity.util.AuditableSoftDeletableEntity
import riven.core.enums.notification.NotificationReferenceType
import riven.core.enums.notification.NotificationType
import riven.core.models.notification.Notification
import riven.core.models.notification.NotificationContent
import java.time.ZonedDateTime
import java.util.UUID

@Entity
@Table(name = "notifications")
@SQLRestriction("deleted = false")
data class NotificationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Column(name = "user_id")
    val userId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    val type: NotificationType,

    @Type(JsonBinaryType::class)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    val content: NotificationContent,

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type")
    val referenceType: NotificationReferenceType? = null,

    @Column(name = "reference_id", columnDefinition = "uuid")
    val referenceId: UUID? = null,

    @Column(name = "resolved", nullable = false)
    var resolved: Boolean = false,

    @Column(name = "resolved_at")
    var resolvedAt: ZonedDateTime? = null,

    @Column(name = "expires_at")
    val expiresAt: ZonedDateTime? = null,
) : AuditableSoftDeletableEntity() {

    fun toModel(): Notification {
        val id = requireNotNull(this.id) { "NotificationEntity ID cannot be null when converting to model" }
        return Notification(
            id = id,
            workspaceId = this.workspaceId,
            userId = this.userId,
            type = this.type,
            content = this.content,
            referenceType = this.referenceType,
            referenceId = this.referenceId,
            resolved = this.resolved,
            resolvedAt = this.resolvedAt,
            expiresAt = this.expiresAt,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            createdBy = this.createdBy,
            updatedBy = this.updatedBy,
        )
    }
}
```

- [ ] **Step 2: Create NotificationReadEntity**

System-managed join table — no audit columns, no soft-delete. Tracks per-user read state for notifications. The unique constraint prevents duplicate read entries.

```kotlin
package riven.core.entity.notification

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime
import java.util.UUID

@Entity
@Table(
    name = "notification_reads",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_notification_reads_user_notification",
            columnNames = ["user_id", "notification_id"]
        )
    ]
)
data class NotificationReadEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "notification_id", nullable = false)
    val notificationId: UUID,

    @Column(name = "read_at", nullable = false)
    val readAt: ZonedDateTime = ZonedDateTime.now(),
)
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/riven/core/entity/notification/
git commit -m "feat(notification): add NotificationEntity and NotificationReadEntity"
```

---

### Task 5: Repositories

**Files:**
- Create: `src/main/kotlin/riven/core/repository/notification/NotificationRepository.kt`
- Create: `src/main/kotlin/riven/core/repository/notification/NotificationReadRepository.kt`

- [ ] **Step 1: Create NotificationRepository**

The inbox query uses two queries (notifications + batch read-state lookup) rather than a single JPQL LEFT JOIN. This avoids fighting JPQL constructor expressions with JSONB columns while still preventing N+1 — both queries are O(1) regardless of page size.

```kotlin
package riven.core.repository.notification

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import riven.core.entity.notification.NotificationEntity
import riven.core.enums.notification.NotificationReferenceType
import java.time.ZonedDateTime
import java.util.UUID

interface NotificationRepository : JpaRepository<NotificationEntity, UUID> {

    /**
     * Fetches a page of inbox notifications for a user within a workspace.
     * Returns notifications that are either workspace-wide (userId IS NULL)
     * or targeted to the specific user. Filters out expired notifications.
     * Uses cursor-based pagination on createdAt.
     */
    @Query(
        """
        SELECT n FROM NotificationEntity n
        WHERE n.workspaceId = :workspaceId
        AND (n.userId IS NULL OR n.userId = :userId)
        AND (n.expiresAt IS NULL OR n.expiresAt > CURRENT_TIMESTAMP)
        AND n.createdAt < :cursor
        ORDER BY n.createdAt DESC
        """
    )
    fun findInbox(
        @Param("workspaceId") workspaceId: UUID,
        @Param("userId") userId: UUID,
        @Param("cursor") cursor: ZonedDateTime,
        pageable: Pageable,
    ): List<NotificationEntity>

    /**
     * Counts unread notifications for a user's inbox. A notification is unread
     * if no matching row exists in notification_reads for this user.
     */
    @Query(
        """
        SELECT COUNT(n) FROM NotificationEntity n
        WHERE n.workspaceId = :workspaceId
        AND (n.userId IS NULL OR n.userId = :userId)
        AND (n.expiresAt IS NULL OR n.expiresAt > CURRENT_TIMESTAMP)
        AND n.id NOT IN (
            SELECT nr.notificationId FROM NotificationReadEntity nr
            WHERE nr.userId = :userId
        )
        """
    )
    fun countUnread(
        @Param("workspaceId") workspaceId: UUID,
        @Param("userId") userId: UUID,
    ): Long

    /**
     * Finds all unresolved notifications referencing a specific domain entity.
     * Used by NotificationDeliveryService to mark notifications resolved
     * when the referenced entity's action is completed.
     */
    @Query(
        """
        SELECT n FROM NotificationEntity n
        WHERE n.referenceType = :referenceType
        AND n.referenceId = :referenceId
        AND n.resolved = false
        """
    )
    fun findUnresolvedByReference(
        @Param("referenceType") referenceType: NotificationReferenceType,
        @Param("referenceId") referenceId: UUID,
    ): List<NotificationEntity>

    /**
     * Finds a notification by ID scoped to a workspace.
     * Used for single-notification operations (mark read, delete).
     */
    fun findByIdAndWorkspaceId(id: UUID, workspaceId: UUID): NotificationEntity?
}
```

- [ ] **Step 2: Create NotificationReadRepository**

```kotlin
package riven.core.repository.notification

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import riven.core.entity.notification.NotificationReadEntity
import java.util.UUID

interface NotificationReadRepository : JpaRepository<NotificationReadEntity, UUID> {

    /**
     * Returns the set of notification IDs that the user has already read.
     * Used to enrich inbox results with read state in a single batch query.
     */
    @Query(
        """
        SELECT nr.notificationId FROM NotificationReadEntity nr
        WHERE nr.userId = :userId AND nr.notificationId IN :notificationIds
        """
    )
    fun findReadNotificationIds(
        @Param("userId") userId: UUID,
        @Param("notificationIds") notificationIds: Collection<UUID>,
    ): Set<UUID>

    fun existsByUserIdAndNotificationId(userId: UUID, notificationId: UUID): Boolean

    /**
     * Bulk insert read entries for marking all notifications as read.
     * Uses native SQL INSERT ... ON CONFLICT DO NOTHING to handle idempotency.
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO notification_reads (id, user_id, notification_id, read_at)
            SELECT uuid_generate_v4(), :userId, n.id, CURRENT_TIMESTAMP
            FROM notifications n
            WHERE n.workspace_id = :workspaceId
            AND (n.user_id IS NULL OR n.user_id = :userId)
            AND (n.expires_at IS NULL OR n.expires_at > CURRENT_TIMESTAMP)
            AND n.deleted = false
            AND n.id NOT IN (
                SELECT nr.notification_id FROM notification_reads nr WHERE nr.user_id = :userId
            )
            ON CONFLICT (user_id, notification_id) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun markAllAsRead(
        @Param("workspaceId") workspaceId: UUID,
        @Param("userId") userId: UUID,
    )
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/riven/core/repository/notification/
git commit -m "feat(notification): add NotificationRepository and NotificationReadRepository"
```

---

### Task 6: Test Factory

**Files:**
- Create: `src/test/kotlin/riven/core/service/util/factory/NotificationFactory.kt`

- [ ] **Step 1: Create NotificationFactory**

Provides builder methods for creating test notification entities and content with sensible defaults. All IDs and timestamps are deterministic for assertion stability.

```kotlin
package riven.core.service.util.factory

import riven.core.entity.notification.NotificationEntity
import riven.core.entity.notification.NotificationReadEntity
import riven.core.enums.notification.NotificationReferenceType
import riven.core.enums.notification.NotificationType
import riven.core.enums.notification.ReviewPriority
import riven.core.enums.notification.SystemSeverity
import riven.core.models.notification.NotificationContent
import java.time.ZonedDateTime
import java.util.UUID

object NotificationFactory {

    val DEFAULT_NOTIFICATION_ID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")

    fun createEntity(
        id: UUID? = DEFAULT_NOTIFICATION_ID,
        workspaceId: UUID,
        userId: UUID? = null,
        type: NotificationType = NotificationType.INFORMATION,
        content: NotificationContent = informationContent(),
        referenceType: NotificationReferenceType? = null,
        referenceId: UUID? = null,
        resolved: Boolean = false,
        resolvedAt: ZonedDateTime? = null,
        expiresAt: ZonedDateTime? = null,
    ): NotificationEntity = NotificationEntity(
        id = id,
        workspaceId = workspaceId,
        userId = userId,
        type = type,
        content = content,
        referenceType = referenceType,
        referenceId = referenceId,
        resolved = resolved,
        resolvedAt = resolvedAt,
        expiresAt = expiresAt,
    )

    val DEFAULT_READ_ID: UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901")
    val DEFAULT_READ_AT: ZonedDateTime = ZonedDateTime.parse("2026-01-15T10:30:00Z")

    fun createReadEntity(
        id: UUID? = DEFAULT_READ_ID,
        userId: UUID,
        notificationId: UUID,
        readAt: ZonedDateTime = DEFAULT_READ_AT,
    ): NotificationReadEntity = NotificationReadEntity(
        id = id,
        userId = userId,
        notificationId = notificationId,
        readAt = readAt,
    )

    fun informationContent(
        title: String = "Test Notification",
        message: String = "This is a test notification.",
        sourceLabel: String? = null,
    ): NotificationContent.Information = NotificationContent.Information(
        title = title,
        message = message,
        sourceLabel = sourceLabel,
    )

    fun reviewRequestContent(
        title: String = "Review Required",
        message: String = "An item requires your review.",
        contextSummary: String? = "Entity A may be linked to Entity B",
        priority: ReviewPriority = ReviewPriority.NORMAL,
    ): NotificationContent.ReviewRequest = NotificationContent.ReviewRequest(
        title = title,
        message = message,
        contextSummary = contextSummary,
        priority = priority,
    )

    fun systemContent(
        title: String = "System Notice",
        message: String = "A system event has occurred.",
        severity: SystemSeverity = SystemSeverity.INFO,
    ): NotificationContent.System = NotificationContent.System(
        title = title,
        message = message,
        severity = severity,
    )
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/riven/core/service/util/factory/NotificationFactory.kt
git commit -m "feat(notification): add NotificationFactory for test data"
```

---

## Chunk 2: NotificationService — TDD

### Task 7: NotificationService — Create + Activity Logging

**Files:**
- Create: `src/test/kotlin/riven/core/service/notification/NotificationServiceTest.kt`
- Create: `src/main/kotlin/riven/core/service/notification/NotificationService.kt`

- [ ] **Step 1: Write failing tests for notification creation**

```kotlin
package riven.core.service.notification

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.notification.NotificationEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.notification.NotificationReferenceType
import riven.core.enums.notification.NotificationType
import riven.core.enums.notification.ReviewPriority
import riven.core.enums.util.OperationType
import riven.core.models.notification.NotificationContent
import riven.core.models.request.notification.CreateNotificationRequest
import riven.core.models.websocket.NotificationEvent
import riven.core.repository.notification.NotificationReadRepository
import riven.core.repository.notification.NotificationRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.NotificationFactory
import riven.core.enums.workspace.WorkspaceRoles
import java.util.UUID

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        NotificationService::class,
    ]
)
class NotificationServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var notificationRepository: NotificationRepository

    @MockitoBean
    private lateinit var notificationReadRepository: NotificationReadRepository

    @MockitoBean
    private lateinit var activityService: ActivityService

    @MockitoBean
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @Autowired
    private lateinit var notificationService: NotificationService

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
        fun `creates user-targeted review request notification with reference`() {
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

            val result = notificationService.createNotification(request)

            assertEquals(NotificationType.REVIEW_REQUEST, result.type)
            assertEquals(targetUserId, result.userId)
            assertEquals(NotificationReferenceType.ENTITY_RESOLUTION, result.referenceType)
            assertEquals(referenceId, result.referenceId)
        }

        @Test
        fun `logs activity on notification creation`() {
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
                type = NotificationType.REVIEW_REQUEST,
                content = NotificationFactory.reviewRequestContent(),
                expiresAt = expiresAt,
            )

            val savedEntity = NotificationFactory.createEntity(
                workspaceId = workspaceId,
                type = NotificationType.REVIEW_REQUEST,
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

            val captor = argumentCaptor<NotificationEvent>()
            verify(applicationEventPublisher).publishEvent(captor.capture())
            assertEquals(workspaceId, captor.firstValue.workspaceId)
            assertEquals(OperationType.CREATE, captor.firstValue.operation)
            assertEquals(NotificationType.INFORMATION, captor.firstValue.notificationType)
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "riven.core.service.notification.NotificationServiceTest"`
Expected: FAIL — `NotificationService` class does not exist.

- [ ] **Step 3: Implement NotificationService — create method**

```kotlin
package riven.core.service.notification

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.notification.NotificationEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.util.OperationType
import riven.core.models.notification.Notification
import riven.core.models.request.notification.CreateNotificationRequest
import riven.core.models.websocket.NotificationEvent
import riven.core.repository.notification.NotificationReadRepository
import riven.core.repository.notification.NotificationRepository
import riven.core.service.activity.ActivityService
import riven.core.service.activity.log
import riven.core.service.auth.AuthTokenService

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val notificationReadRepository: NotificationReadRepository,
    private val activityService: ActivityService,
    private val authTokenService: AuthTokenService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val logger: KLogger,
) {

    // ------ Create ------

    /**
     * Creates a notification and broadcasts it via WebSocket.
     * Notifications are workspace-scoped; optionally targeted to a specific user.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#request.workspaceId)")
    @Transactional
    fun createNotification(request: CreateNotificationRequest): Notification {
        val userId = authTokenService.getUserId()

        val entity = NotificationEntity(
            workspaceId = request.workspaceId,
            userId = request.userId,
            type = request.type,
            content = request.content,
            referenceType = request.referenceType,
            referenceId = request.referenceId,
            expiresAt = request.expiresAt,
        )

        val saved = notificationRepository.save(entity)
        val notificationId = requireNotNull(saved.id) { "NotificationEntity must have a non-null id after save" }

        logCreationActivity(userId, saved)
        publishNotificationEvent(saved, OperationType.CREATE, userId)

        logger.info { "Notification created: type=${saved.type} workspace=${saved.workspaceId} target=${saved.userId ?: "all"}" }

        return saved.toModel()
    }

    // ------ Private Helpers ------

    private fun logCreationActivity(userId: java.util.UUID, entity: NotificationEntity) {
        val notificationId = requireNotNull(entity.id)
        activityService.log(
            activity = Activity.NOTIFICATION,
            operation = OperationType.CREATE,
            userId = userId,
            workspaceId = entity.workspaceId,
            entityType = ApplicationEntityType.NOTIFICATION,
            entityId = notificationId,
            "type" to entity.type.name,
            "targetUserId" to entity.userId?.toString(),
            "referenceType" to entity.referenceType?.name,
            "referenceId" to entity.referenceId?.toString(),
        )
    }

    private fun publishNotificationEvent(entity: NotificationEntity, operation: OperationType, userId: java.util.UUID) {
        val notificationId = requireNotNull(entity.id)
        applicationEventPublisher.publishEvent(
            NotificationEvent(
                workspaceId = entity.workspaceId,
                userId = userId,
                operation = operation,
                entityId = notificationId,
                notificationType = entity.type,
                summary = mapOf(
                    "title" to entity.content.title,
                    "targetUserId" to entity.userId?.toString(),
                ),
            )
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "riven.core.service.notification.NotificationServiceTest"`
Expected: PASS — all 4 create tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/riven/core/service/notification/NotificationService.kt src/test/kotlin/riven/core/service/notification/NotificationServiceTest.kt
git commit -m "feat(notification): add NotificationService.createNotification with TDD"
```

---

### Task 8: NotificationService — Inbox Query + Unread Count

**Files:**
- Modify: `src/test/kotlin/riven/core/service/notification/NotificationServiceTest.kt`
- Modify: `src/main/kotlin/riven/core/service/notification/NotificationService.kt`

- [ ] **Step 1: Write failing tests for inbox query and unread count**

Add these nested classes to `NotificationServiceTest`:

```kotlin
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
            )
            whenever(notificationRepository.findInbox(eq(workspaceId), eq(userId), any(), any()))
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
            )
            whenever(notificationRepository.findInbox(eq(workspaceId), eq(userId), any(), any()))
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
            whenever(notificationRepository.findInbox(eq(workspaceId), eq(userId), any(), any()))
                .thenReturn(notifications)
            whenever(notificationReadRepository.findReadNotificationIds(eq(userId), any()))
                .thenReturn(emptySet())
            whenever(notificationRepository.countUnread(workspaceId, userId))
                .thenReturn(3L)

            val result = notificationService.getInbox(workspaceId, null, 3)

            assertNotNull(result.nextCursor)
            assertEquals(notifications.last().createdAt, result.nextCursor)
        }

        @Test
        fun `returns null nextCursor when page is not full`() {
            val notification = NotificationFactory.createEntity(workspaceId = workspaceId)
            whenever(notificationRepository.findInbox(eq(workspaceId), eq(userId), any(), any()))
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
            whenever(notificationRepository.findInbox(eq(workspaceId), eq(userId), any(), any()))
                .thenReturn(emptyList())
            whenever(notificationRepository.countUnread(workspaceId, userId))
                .thenReturn(0L)

            val result = notificationService.getInbox(workspaceId, null, 20)

            assertEquals(0, result.notifications.size)
            assertNull(result.nextCursor)
            assertEquals(0L, result.unreadCount)
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
```

Also add these missing imports to the test file:

```kotlin
import java.time.ZonedDateTime
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "riven.core.service.notification.NotificationServiceTest"`
Expected: FAIL — `getInbox` and `getUnreadCount` methods don't exist.

- [ ] **Step 3: Implement inbox query and unread count**

Add these methods to `NotificationService`:

```kotlin
    // ------ Read Operations ------

    /**
     * Returns a cursor-paginated inbox for the current user within a workspace.
     * Combines notification data with per-user read state in two batch queries.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getInbox(workspaceId: UUID, cursor: ZonedDateTime?, pageSize: Int): NotificationInboxResponse {
        val userId = authTokenService.getUserId()
        val effectiveCursor = cursor ?: ZonedDateTime.now()

        val notifications = notificationRepository.findInbox(
            workspaceId = workspaceId,
            userId = userId,
            cursor = effectiveCursor,
            pageable = PageRequest.of(0, pageSize),
        )

        val readIds = fetchReadIds(userId, notifications)

        val items = notifications.map { it.toInboxItem(isRead = it.id in readIds) }
        val nextCursor = if (items.size == pageSize) notifications.last().createdAt else null
        val unreadCount = countUnread(workspaceId, userId)

        return NotificationInboxResponse(
            notifications = items,
            nextCursor = nextCursor,
            unreadCount = unreadCount,
        )
    }

    /**
     * Returns the number of unread notifications for the current user's inbox.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getUnreadCount(workspaceId: UUID): Long {
        val userId = authTokenService.getUserId()
        return countUnread(workspaceId, userId)
    }

    // ------ Private Helpers ------

    private fun countUnread(workspaceId: UUID, userId: UUID): Long =
        notificationRepository.countUnread(workspaceId, userId)

    private fun fetchReadIds(userId: UUID, notifications: List<NotificationEntity>): Set<UUID> =
        if (notifications.isNotEmpty()) {
            notificationReadRepository.findReadNotificationIds(
                userId = userId,
                notificationIds = notifications.mapNotNull { it.id },
            )
        } else {
            emptySet()
        }

    private fun NotificationEntity.toInboxItem(isRead: Boolean): NotificationInboxItem =
        NotificationInboxItem(
            id = requireNotNull(this.id),
            type = this.type,
            content = this.content,
            referenceType = this.referenceType,
            referenceId = this.referenceId,
            resolved = this.resolved,
            read = isRead,
            createdAt = requireNotNull(this.createdAt),
        )
```

Add these imports to `NotificationService`:

```kotlin
import org.springframework.data.domain.PageRequest
import riven.core.models.notification.NotificationInboxItem
import riven.core.models.response.notification.NotificationInboxResponse
import java.time.ZonedDateTime
import java.util.UUID
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "riven.core.service.notification.NotificationServiceTest"`
Expected: PASS — all inbox and unread count tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/riven/core/service/notification/NotificationService.kt src/test/kotlin/riven/core/service/notification/NotificationServiceTest.kt
git commit -m "feat(notification): add inbox query and unread count with TDD"
```

---

### Task 9: NotificationService — Read State, Resolution, and Soft Delete

**Files:**
- Modify: `src/test/kotlin/riven/core/service/notification/NotificationServiceTest.kt`
- Modify: `src/main/kotlin/riven/core/service/notification/NotificationService.kt`

- [ ] **Step 1: Write failing tests for mark-read, mark-all-read, resolve, and delete**

Add these nested classes to `NotificationServiceTest`:

```kotlin
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
            whenever(notificationReadRepository.existsByUserIdAndNotificationId(userId, notificationId))
                .thenReturn(false)
            whenever(notificationReadRepository.save(any<NotificationReadEntity>()))
                .thenAnswer { it.arguments[0] }

            notificationService.markAsRead(workspaceId, notificationId)

            val captor = argumentCaptor<NotificationReadEntity>()
            verify(notificationReadRepository).save(captor.capture())
            assertEquals(userId, captor.firstValue.userId)
            assertEquals(notificationId, captor.firstValue.notificationId)
        }

        @Test
        fun `is idempotent — does not duplicate read entry`() {
            val notificationId = NotificationFactory.DEFAULT_NOTIFICATION_ID
            val notification = NotificationFactory.createEntity(id = notificationId, workspaceId = workspaceId)

            whenever(notificationRepository.findByIdAndWorkspaceId(notificationId, workspaceId))
                .thenReturn(notification)
            whenever(notificationReadRepository.existsByUserIdAndNotificationId(userId, notificationId))
                .thenReturn(true)

            notificationService.markAsRead(workspaceId, notificationId)

            verify(notificationReadRepository, org.mockito.kotlin.never()).save(any())
        }

        @Test
        fun `throws NotFoundException for unknown notification`() {
            val unknownId = UUID.randomUUID()
            whenever(notificationRepository.findByIdAndWorkspaceId(unknownId, workspaceId))
                .thenReturn(null)

            assertThrows<riven.core.exceptions.NotFoundException> {
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

            verify(notificationRepository, org.mockito.kotlin.never()).saveAll(any<List<NotificationEntity>>())
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

            assertThrows<riven.core.exceptions.NotFoundException> {
                notificationService.deleteNotification(workspaceId, unknownId)
            }
        }
    }
```

Add this import to the test file:

```kotlin
import riven.core.entity.notification.NotificationReadEntity
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "riven.core.service.notification.NotificationServiceTest"`
Expected: FAIL — `markAsRead`, `markAllAsRead`, `resolveByReference`, `deleteNotification` methods don't exist.

- [ ] **Step 3: Implement read-state, resolution, and delete methods**

Add these methods to `NotificationService`:

```kotlin
    // ------ Read State Mutations ------

    /**
     * Marks a single notification as read for the current user.
     * Idempotent — no-ops if already read.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional
    fun markAsRead(workspaceId: UUID, notificationId: UUID) {
        val userId = authTokenService.getUserId()

        val notification = notificationRepository.findByIdAndWorkspaceId(notificationId, workspaceId)
            ?: throw NotFoundException("Notification not found: $notificationId")

        if (notificationReadRepository.existsByUserIdAndNotificationId(userId, notificationId)) {
            return
        }

        notificationReadRepository.save(
            NotificationReadEntity(userId = userId, notificationId = notificationId)
        )
    }

    /**
     * Marks all visible notifications in the workspace as read for the current user.
     * Uses a bulk native SQL insert with ON CONFLICT DO NOTHING for idempotency.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional
    fun markAllAsRead(workspaceId: UUID) {
        val userId = authTokenService.getUserId()
        notificationReadRepository.markAllAsRead(workspaceId, userId)
    }

    // ------ Resolution ------

    /**
     * Marks all unresolved notifications referencing the given entity as resolved.
     * Called by NotificationDeliveryService when a domain event indicates
     * the referenced action has been completed (e.g., entity link approved,
     * workflow step completed).
     */
    @Transactional
    fun resolveByReference(referenceType: NotificationReferenceType, referenceId: UUID) {
        val unresolvedNotifications = notificationRepository.findUnresolvedByReference(referenceType, referenceId)

        if (unresolvedNotifications.isEmpty()) {
            logger.debug { "No unresolved notifications for $referenceType:$referenceId" }
            return
        }

        val now = ZonedDateTime.now()
        unresolvedNotifications.forEach { notification ->
            notification.resolved = true
            notification.resolvedAt = now
        }

        notificationRepository.saveAll(unresolvedNotifications)
        logger.info { "Resolved ${unresolvedNotifications.size} notifications for $referenceType:$referenceId" }
    }

    // ------ Delete ------

    /**
     * Soft-deletes a notification. Logs activity for audit trail.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional
    fun deleteNotification(workspaceId: UUID, notificationId: UUID) {
        val userId = authTokenService.getUserId()

        val notification = notificationRepository.findByIdAndWorkspaceId(notificationId, workspaceId)
            ?: throw NotFoundException("Notification not found: $notificationId")

        notification.markDeleted()
        notificationRepository.save(notification)

        activityService.log(
            activity = Activity.NOTIFICATION,
            operation = OperationType.DELETE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.NOTIFICATION,
            entityId = notificationId,
            "type" to notification.type.name,
        )
    }
```

Add these imports to `NotificationService`:

```kotlin
import riven.core.entity.notification.NotificationReadEntity
import riven.core.enums.notification.NotificationReferenceType
import riven.core.exceptions.NotFoundException
import riven.core.models.common.markDeleted
```

> Note: Check the exact import path for `markDeleted` — it's an extension function on `SoftDeletable`. It may be `riven.core.models.common.markDeleted` or located in the `SoftDeletable.kt` file.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "riven.core.service.notification.NotificationServiceTest"`
Expected: PASS — all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/riven/core/service/notification/NotificationService.kt src/test/kotlin/riven/core/service/notification/NotificationServiceTest.kt
git commit -m "feat(notification): add read-state, resolution, and soft-delete with TDD"
```

---

## Chunk 3: Delivery Service, Controller, and Security Tests

### Task 10: NotificationDeliveryService

**Files:**
- Create: `src/test/kotlin/riven/core/service/notification/NotificationDeliveryServiceTest.kt`
- Create: `src/main/kotlin/riven/core/service/notification/NotificationDeliveryService.kt`

- [ ] **Step 1: Write failing tests for event-driven notification creation**

The delivery service listens for domain events and translates them into notifications. Since the source features (identity resolution, workflow human-in-the-loop) are WIP, we test the event listener structure and a sample translation.

```kotlin
package riven.core.service.notification

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.enums.notification.NotificationReferenceType
import riven.core.enums.notification.NotificationType
import riven.core.enums.notification.ReviewPriority
import riven.core.enums.util.OperationType
import riven.core.models.notification.Notification
import riven.core.models.notification.NotificationContent
import riven.core.models.request.notification.CreateNotificationRequest
import riven.core.service.util.factory.NotificationFactory
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

@SpringBootTest(
    classes = [
        NotificationDeliveryService::class,
    ]
)
class NotificationDeliveryServiceTest {

    @MockitoBean
    private lateinit var notificationService: NotificationService

    @MockitoBean
    private lateinit var logger: KLogger

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
        val workspaceId = UUID.randomUUID()
        val content = NotificationFactory.informationContent(
            title = "Weekly Report",
            message = "Your report is ready.",
        )
        val notification = Notification(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
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
        )

        whenever(notificationService.createNotification(any())).thenReturn(notification)

        deliveryService.createForWorkspace(
            workspaceId = workspaceId,
            type = NotificationType.INFORMATION,
            content = content,
        )

        val captor = argumentCaptor<CreateNotificationRequest>()
        verify(notificationService).createNotification(captor.capture())
        assertEquals(workspaceId, captor.firstValue.workspaceId)
        assertNull(captor.firstValue.userId)
        assertEquals(NotificationType.INFORMATION, captor.firstValue.type)
    }

    @Test
    fun `createForUser creates user-targeted notification via NotificationService`() {
        val workspaceId = UUID.randomUUID()
        val targetUserId = UUID.randomUUID()
        val referenceId = UUID.randomUUID()
        val content = NotificationFactory.reviewRequestContent(
            title = "Review Required",
            priority = ReviewPriority.HIGH,
        )
        val notification = Notification(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
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
        )

        whenever(notificationService.createNotification(any())).thenReturn(notification)

        deliveryService.createForUser(
            workspaceId = workspaceId,
            userId = targetUserId,
            type = NotificationType.REVIEW_REQUEST,
            content = content,
            referenceType = NotificationReferenceType.ENTITY_RESOLUTION,
            referenceId = referenceId,
        )

        val captor = argumentCaptor<CreateNotificationRequest>()
        verify(notificationService).createNotification(captor.capture())
        assertEquals(targetUserId, captor.firstValue.userId)
        assertEquals(NotificationReferenceType.ENTITY_RESOLUTION, captor.firstValue.referenceType)
        assertEquals(referenceId, captor.firstValue.referenceId)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "riven.core.service.notification.NotificationDeliveryServiceTest"`
Expected: FAIL — `NotificationDeliveryService` class does not exist.

- [ ] **Step 3: Implement NotificationDeliveryService**

```kotlin
package riven.core.service.notification

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import riven.core.enums.notification.NotificationReferenceType
import riven.core.enums.notification.NotificationType
import riven.core.models.notification.Notification
import riven.core.models.notification.NotificationContent
import riven.core.models.request.notification.CreateNotificationRequest
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Translates domain events into notifications and manages resolution.
 * Source services publish domain events via ApplicationEventPublisher;
 * this service listens and creates the appropriate notification.
 *
 * Event listener methods for specific domain events (identity resolution,
 * workflow human-in-the-loop) will be added as those features are implemented.
 */
@Service
class NotificationDeliveryService(
    private val notificationService: NotificationService,
    private val logger: KLogger,
) {

    // ------ Notification Creation ------

    /** Creates a workspace-wide notification visible to all members. */
    fun createForWorkspace(
        workspaceId: UUID,
        type: NotificationType,
        content: NotificationContent,
        referenceType: NotificationReferenceType? = null,
        referenceId: UUID? = null,
        expiresAt: ZonedDateTime? = null,
    ): Notification {
        return notificationService.createNotification(
            CreateNotificationRequest(
                workspaceId = workspaceId,
                type = type,
                content = content,
                referenceType = referenceType,
                referenceId = referenceId,
                expiresAt = expiresAt,
            )
        )
    }

    /** Creates a notification targeted to a specific user within a workspace. */
    fun createForUser(
        workspaceId: UUID,
        userId: UUID,
        type: NotificationType,
        content: NotificationContent,
        referenceType: NotificationReferenceType? = null,
        referenceId: UUID? = null,
        expiresAt: ZonedDateTime? = null,
    ): Notification {
        return notificationService.createNotification(
            CreateNotificationRequest(
                workspaceId = workspaceId,
                userId = userId,
                type = type,
                content = content,
                referenceType = referenceType,
                referenceId = referenceId,
                expiresAt = expiresAt,
            )
        )
    }

    // ------ Resolution ------

    /**
     * Marks all unresolved notifications referencing the given entity as resolved.
     * Call this when a domain event indicates the referenced action is complete.
     */
    fun resolveByReference(referenceType: NotificationReferenceType, referenceId: UUID) {
        notificationService.resolveByReference(referenceType, referenceId)
        logger.info { "Resolved notifications for $referenceType:$referenceId" }
    }

    // ------ Domain Event Listeners ------
    // Add @TransactionalEventListener methods here as domain features are implemented.
    // Example:
    //
    // @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // fun onEntityResolutionDetected(event: EntityResolutionDetectedEvent) {
    //     createForWorkspace(
    //         workspaceId = event.workspaceId,
    //         type = NotificationType.REVIEW_REQUEST,
    //         content = NotificationContent.ReviewRequest(
    //             title = "Entity Link Detected",
    //             message = "...",
    //             priority = ReviewPriority.NORMAL,
    //         ),
    //         referenceType = NotificationReferenceType.ENTITY_RESOLUTION,
    //         referenceId = event.resolutionId,
    //     )
    // }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "riven.core.service.notification.NotificationDeliveryServiceTest"`
Expected: PASS — all 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/riven/core/service/notification/NotificationDeliveryService.kt src/test/kotlin/riven/core/service/notification/NotificationDeliveryServiceTest.kt
git commit -m "feat(notification): add NotificationDeliveryService with TDD"
```

---

### Task 11: NotificationController

**Files:**
- Create: `src/main/kotlin/riven/core/controller/notification/NotificationController.kt`

- [ ] **Step 1: Create the controller**

Thin controller delegating to `NotificationService`. All business logic and access control is in the service layer.

```kotlin
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
import org.springframework.web.bind.annotation.RestController
import riven.core.models.notification.Notification
import riven.core.models.request.notification.CreateNotificationRequest
import riven.core.models.response.notification.NotificationInboxResponse
import riven.core.service.notification.NotificationService
import java.time.ZonedDateTime
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
        @RequestParam(required = false) cursor: ZonedDateTime?,
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

    @Operation(summary = "Create a notification")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Notification created"),
        ApiResponse(responseCode = "403", description = "Access denied"),
    )
    @PostMapping
    fun createNotification(
        @RequestBody request: CreateNotificationRequest,
    ): ResponseEntity<Notification> =
        ResponseEntity.status(HttpStatus.CREATED).body(notificationService.createNotification(request))

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
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/riven/core/controller/notification/NotificationController.kt
git commit -m "feat(notification): add NotificationController REST endpoints"
```

---

### Task 12: Security Tests

**Files:**
- Modify: `src/test/kotlin/riven/core/service/notification/NotificationServiceTest.kt`

- [ ] **Step 1: Add security tests to NotificationServiceTest**

These tests verify that `@PreAuthorize` blocks unauthorized workspace access. Add this nested class:

```kotlin
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

            assertThrows<org.springframework.security.access.AccessDeniedException> {
                notificationService.getInbox(otherWorkspaceId, null, 20)
            }
        }

        @Test
        fun `getUnreadCount denies access to other workspace`() {
            val otherWorkspaceId = UUID.randomUUID()

            assertThrows<org.springframework.security.access.AccessDeniedException> {
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

            assertThrows<org.springframework.security.access.AccessDeniedException> {
                notificationService.createNotification(request)
            }
        }

        @Test
        fun `markAsRead denies access to other workspace`() {
            val otherWorkspaceId = UUID.randomUUID()

            assertThrows<org.springframework.security.access.AccessDeniedException> {
                notificationService.markAsRead(otherWorkspaceId, UUID.randomUUID())
            }
        }

        @Test
        fun `markAllAsRead denies access to other workspace`() {
            val otherWorkspaceId = UUID.randomUUID()

            assertThrows<org.springframework.security.access.AccessDeniedException> {
                notificationService.markAllAsRead(otherWorkspaceId)
            }
        }

        @Test
        fun `deleteNotification denies access to other workspace`() {
            val otherWorkspaceId = UUID.randomUUID()

            assertThrows<org.springframework.security.access.AccessDeniedException> {
                notificationService.deleteNotification(otherWorkspaceId, UUID.randomUUID())
            }
        }
    }
```

- [ ] **Step 2: Run the full test suite**

Run: `./gradlew test --tests "riven.core.service.notification.*"`
Expected: PASS — all tests across both test classes pass.

- [ ] **Step 3: Run the full project test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — no regressions.

- [ ] **Step 4: Verify full project compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/test/kotlin/riven/core/service/notification/NotificationServiceTest.kt
git commit -m "feat(notification): add workspace access control security tests"
```

---

## Post-Implementation Checklist

After all tasks are complete, verify:

- [ ] `./gradlew build` succeeds (compilation + all tests)
- [ ] `./gradlew test` shows all notification tests passing
- [ ] All notification enums use `@JsonProperty` for serialization
- [ ] `NotificationEntity.toModel()` maps all fields correctly
- [ ] `@PreAuthorize` is on every workspace-scoped service method
- [ ] Activity logging exists for create and delete mutations
- [ ] `NotificationEvent` integrates with existing `WebSocketEventListener`
- [ ] Soft-delete uses `markDeleted()` extension, not manual field setting
- [ ] Cursor pagination returns `nextCursor = null` when page is not full
- [ ] `markAllAsRead` uses native SQL with `ON CONFLICT DO NOTHING` for idempotency
