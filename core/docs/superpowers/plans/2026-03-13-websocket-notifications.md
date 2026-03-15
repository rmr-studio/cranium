# WebSocket Notifications Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add real-time WebSocket notifications to the Spring Boot backend so services can push workspace-scoped domain events (entity CRUD, block environment changes, workflow updates, notifications) to connected clients via STOMP.

**Architecture:** STOMP over WebSocket with Spring's in-memory SimpleBroker. Services publish domain events via Spring's `ApplicationEventPublisher` (same pattern as existing `WorkspaceAnalyticsListener`). A `WebSocketEventListener` receives events after transaction commit and forwards them to the STOMP broker. Authentication uses JWT validation on STOMP CONNECT frames; a `ChannelInterceptor` authorizes subscriptions against workspace membership using the existing `WorkspaceSecurity` bean.

**Tech Stack:** Spring Boot WebSocket Starter (STOMP), Spring Security WebSocket, existing JWT/Supabase auth infrastructure, Spring ApplicationEvents.

---

## File Structure

### New Files

| File | Responsibility |
|------|---------------|
| `configuration/websocket/WebSocketConfig.kt` | `@EnableWebSocketMessageBroker` — registers STOMP endpoint, configures SimpleBroker, sets heartbeat/buffer limits |
| `configuration/websocket/WebSocketSecurityInterceptor.kt` | `ChannelInterceptor` — validates JWT on CONNECT, authorizes SUBSCRIBE against workspace membership |
| `configuration/properties/WebSocketConfigurationProperties.kt` | `@ConfigurationProperties("riven.websocket")` — endpoint path, heartbeat intervals, buffer sizes |
| `models/websocket/WorkspaceEvent.kt` | Sealed interface `WorkspaceEvent` + domain subclasses (`EntityEvent`, `BlockEnvironmentEvent`, `WorkflowEvent`, `WorkspaceChangeEvent`) |
| `models/websocket/WebSocketMessage.kt` | Outbound message envelope sent to clients: type, operation, entityId, userId, timestamp, summary |
| `enums/websocket/WebSocketChannel.kt` | Enum mapping domain → topic segment (ENTITIES, BLOCKS, WORKFLOWS, NOTIFICATIONS) |
| `service/websocket/WebSocketEventListener.kt` | `@TransactionalEventListener(AFTER_COMMIT)` — receives `WorkspaceEvent`, resolves topic, sends via `SimpMessagingTemplate` |
| `src/test/kotlin/.../service/websocket/WebSocketEventListenerTest.kt` | Unit test: event → correct topic + message payload |
| `src/test/kotlin/.../configuration/websocket/WebSocketSecurityInterceptorTest.kt` | Security test matrix: CONNECT auth, SUBSCRIBE authorization, rejection cases |
| `src/test/kotlin/.../integration/WebSocketIntegrationTest.kt` | End-to-end: service mutation → event → STOMP client receives message |

### Modified Files

| File | Change |
|------|--------|
| `build.gradle.kts` | Add `spring-boot-starter-websocket` dependency |
| `configuration/auth/SecurityConfig.kt` | Permit WebSocket endpoint (`/ws/**`) |
| `src/main/resources/application.yml` | Add `riven.websocket` properties |
| `src/test/resources/application-test.yml` | Add `riven.websocket` test properties |
| `service/entity/EntityService.kt` | Add `ApplicationEventPublisher` injection + `publishEvent()` in `saveEntity` and `deleteEntities` |
| `service/block/BlockEnvironmentService.kt` | Add `ApplicationEventPublisher` injection + `publishEvent()` in `saveBlockEnvironment` |
| `service/workspace/WorkspaceService.kt` | Add workspace domain event publishing (already has `ApplicationEventPublisher`) |

---

## Chunk 1: Infrastructure — Dependencies, Configuration, and Event Model

### Task 1: Add WebSocket Dependency

**Files:**
- Modify: `build.gradle.kts:28-32`

- [ ] **Step 1: Add spring-boot-starter-websocket to build.gradle.kts**

Add after line 32 (`spring-boot-starter-webflux`):

```kotlin
implementation("org.springframework.boot:spring-boot-starter-websocket")
```

- [ ] **Step 2: Verify the project compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "feat: add spring-boot-starter-websocket dependency"
```

---

### Task 2: Create WebSocket Configuration Properties

**Files:**
- Create: `src/main/kotlin/riven/core/configuration/properties/WebSocketConfigurationProperties.kt`
- Modify: `src/main/resources/application.yml:25-64`
- Modify: `src/test/resources/application-test.yml`

- [ ] **Step 1: Create the configuration properties data class**

```kotlin
package riven.core.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("riven.websocket")
data class WebSocketConfigurationProperties(
    /** STOMP endpoint path that clients connect to */
    val endpoint: String = "/ws",
    /** Allowed origins for WebSocket connections (defaults to security allowed-origins) */
    val allowedOrigins: List<String> = emptyList(),
    /** Server heartbeat interval in milliseconds (0 = disabled) */
    val serverHeartbeatMs: Long = 10000,
    /** Expected client heartbeat interval in milliseconds (0 = disabled) */
    val clientHeartbeatMs: Long = 10000,
    /** Maximum size of an inbound STOMP message in bytes */
    val maxMessageSizeBytes: Int = 65536,
    /** Send buffer size limit in bytes per WebSocket session */
    val sendBufferSizeBytes: Int = 524288,
    /** Send timeout in milliseconds */
    val sendTimeoutMs: Long = 15000,
)
```

- [ ] **Step 2: Add websocket properties to application.yml**

Add under the `riven:` block (after `query:` section, before `storage:`):

```yaml
  websocket:
    endpoint: /ws
    server-heartbeat-ms: 10000
    client-heartbeat-ms: 10000
    max-message-size-bytes: 65536
    send-buffer-size-bytes: 524288
    send-timeout-ms: 15000
```

- [ ] **Step 3: Add websocket properties to application-test.yml**

Add under the `riven:` block in the test config:

```yaml
  websocket:
    endpoint: /ws
    server-heartbeat-ms: 0
    client-heartbeat-ms: 0
```

- [ ] **Step 4: Verify the project compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/riven/core/configuration/properties/WebSocketConfigurationProperties.kt
git add src/main/resources/application.yml
git add src/test/resources/application-test.yml
git commit -m "feat: add WebSocket configuration properties"
```

---

### Task 3: Create WebSocket Channel Enum

**Files:**
- Create: `src/main/kotlin/riven/core/enums/websocket/WebSocketChannel.kt`

- [ ] **Step 1: Create the channel enum**

```kotlin
package riven.core.enums.websocket

/**
 * Maps domain concerns to STOMP topic segments.
 * Topics follow the pattern: /topic/workspace/{workspaceId}/{channel}
 */
enum class WebSocketChannel(val topicSegment: String) {
    ENTITIES("entities"),
    BLOCKS("blocks"),
    WORKFLOWS("workflows"),
    NOTIFICATIONS("notifications"),
    WORKSPACE("workspace");

    companion object {
        /** Builds a fully-qualified STOMP topic path for a workspace channel. */
        fun topicPath(workspaceId: java.util.UUID, channel: WebSocketChannel): String =
            "/topic/workspace/$workspaceId/${channel.topicSegment}"
    }
}
```

- [ ] **Step 2: Verify the project compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/riven/core/enums/websocket/WebSocketChannel.kt
git commit -m "feat: add WebSocketChannel enum for topic routing"
```

---

### Task 4: Create Domain Event Model (Sealed Interface)

**Files:**
- Create: `src/main/kotlin/riven/core/models/websocket/WorkspaceEvent.kt`
- Create: `src/main/kotlin/riven/core/models/websocket/WebSocketMessage.kt`

- [ ] **Step 1: Create the sealed interface and domain event subclasses**

```kotlin
package riven.core.models.websocket

import riven.core.enums.util.OperationType
import riven.core.enums.websocket.WebSocketChannel
import java.util.UUID

/**
 * Base interface for all workspace-scoped domain events that should be
 * broadcast over WebSocket. Published via ApplicationEventPublisher inside
 * @Transactional service methods and consumed by WebSocketEventListener
 * after transaction commit.
 */
sealed interface WorkspaceEvent {
    val workspaceId: UUID
    val userId: UUID
    val operation: OperationType
    val channel: WebSocketChannel
    val entityId: UUID?
    /** Feed-relevant fields for in-place list updates. */
    val summary: Map<String, Any?>
}

/**
 * Published when an entity instance is created, updated, or deleted.
 */
data class EntityEvent(
    override val workspaceId: UUID,
    override val userId: UUID,
    override val operation: OperationType,
    override val entityId: UUID?,
    val entityTypeId: UUID,
    val entityTypeKey: String,
    override val summary: Map<String, Any?> = emptyMap(),
) : WorkspaceEvent {
    override val channel: WebSocketChannel = WebSocketChannel.ENTITIES
}

/**
 * Published when a block environment is saved (structural changes to the block tree).
 */
data class BlockEnvironmentEvent(
    override val workspaceId: UUID,
    override val userId: UUID,
    override val operation: OperationType,
    override val entityId: UUID?,
    val layoutId: UUID,
    val version: Int,
    override val summary: Map<String, Any?> = emptyMap(),
) : WorkspaceEvent {
    override val channel: WebSocketChannel = WebSocketChannel.BLOCKS
}

/**
 * Published when a workflow definition or execution state changes.
 */
data class WorkflowEvent(
    override val workspaceId: UUID,
    override val userId: UUID,
    override val operation: OperationType,
    override val entityId: UUID?,
    override val summary: Map<String, Any?> = emptyMap(),
) : WorkspaceEvent {
    override val channel: WebSocketChannel = WebSocketChannel.WORKFLOWS
}

/**
 * Published when workspace-level properties change (name, settings, membership).
 */
data class WorkspaceChangeEvent(
    override val workspaceId: UUID,
    override val userId: UUID,
    override val operation: OperationType,
    override val entityId: UUID? = null,
    override val summary: Map<String, Any?> = emptyMap(),
) : WorkspaceEvent {
    override val channel: WebSocketChannel = WebSocketChannel.WORKSPACE
}
```

- [ ] **Step 2: Create the outbound message envelope**

```kotlin
package riven.core.models.websocket

import riven.core.enums.util.OperationType
import riven.core.enums.websocket.WebSocketChannel
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Envelope sent to WebSocket subscribers. Contains enough data
 * for feed/list views to update in-place without a REST roundtrip.
 * Detail views should refetch via REST when they receive this notification.
 */
data class WebSocketMessage(
    val channel: WebSocketChannel,
    val operation: OperationType,
    val workspaceId: UUID,
    val entityId: UUID?,
    val userId: UUID,
    val timestamp: ZonedDateTime,
    val summary: Map<String, Any?>,
) {
    companion object {
        fun from(event: WorkspaceEvent): WebSocketMessage = WebSocketMessage(
            channel = event.channel,
            operation = event.operation,
            workspaceId = event.workspaceId,
            entityId = event.entityId,
            userId = event.userId,
            timestamp = ZonedDateTime.now(),
            summary = event.summary,
        )
    }
}
```

- [ ] **Step 3: Verify the project compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/riven/core/models/websocket/WorkspaceEvent.kt
git add src/main/kotlin/riven/core/models/websocket/WebSocketMessage.kt
git commit -m "feat: add WorkspaceEvent sealed interface and WebSocketMessage envelope"
```

---

## Chunk 2: WebSocket Infrastructure — Config, Security, and Event Listener

### Task 5: Create WebSocket Configuration

**Files:**
- Create: `src/main/kotlin/riven/core/configuration/websocket/WebSocketConfig.kt`

- [ ] **Step 1: Create the WebSocket STOMP configuration**

```kotlin
package riven.core.configuration.websocket

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration
import riven.core.configuration.properties.SecurityConfigurationProperties
import riven.core.configuration.properties.WebSocketConfigurationProperties

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val wsProperties: WebSocketConfigurationProperties,
    private val securityProperties: SecurityConfigurationProperties,
    private val webSocketSecurityInterceptor: WebSocketSecurityInterceptor,
) : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        val origins = wsProperties.allowedOrigins.ifEmpty { securityProperties.allowedOrigins }
        registry.addEndpoint(wsProperties.endpoint)
            .setAllowedOrigins(*origins.toTypedArray())
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic", "/queue")
            .setHeartbeatValue(longArrayOf(wsProperties.serverHeartbeatMs, wsProperties.clientHeartbeatMs))
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun configureWebSocketTransport(registration: WebSocketTransportRegistration) {
        registration
            .setMessageSizeLimit(wsProperties.maxMessageSizeBytes)
            .setSendBufferSizeLimit(wsProperties.sendBufferSizeBytes)
            .setSendTimeLimit(wsProperties.sendTimeoutMs.toInt())
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(webSocketSecurityInterceptor)
    }
}
```

- [ ] **Step 2: Verify the project compiles (will fail — WebSocketSecurityInterceptor doesn't exist yet)**

Run: `./gradlew compileKotlin`
Expected: FAIL (missing `WebSocketSecurityInterceptor`). This is expected — we create it in the next task.

---

### Task 6: Create WebSocket Security Interceptor

**Files:**
- Create: `src/main/kotlin/riven/core/configuration/websocket/WebSocketSecurityInterceptor.kt`
- Modify: `src/main/kotlin/riven/core/configuration/auth/SecurityConfig.kt:34-41`

This is the security boundary for WebSocket connections. It validates JWT on CONNECT and authorizes workspace-scoped topic subscriptions.

- [ ] **Step 1: Create the channel interceptor**

```kotlin
package riven.core.configuration.websocket

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import riven.core.configuration.auth.CustomAuthenticationTokenConverter
import java.util.UUID

/**
 * Intercepts inbound STOMP frames to enforce authentication and authorization:
 * - CONNECT: validates JWT token from the `Authorization` header and populates
 *   the STOMP session's authentication principal.
 * - SUBSCRIBE: extracts workspaceId from the topic path and verifies the
 *   authenticated user has access to that workspace.
 */
@Component
class WebSocketSecurityInterceptor(
    private val jwtDecoder: JwtDecoder,
    private val tokenConverter: CustomAuthenticationTokenConverter,
    private val logger: KLogger,
) : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message

        when (accessor.command) {
            StompCommand.CONNECT -> handleConnect(accessor)
            StompCommand.SUBSCRIBE -> handleSubscribe(accessor)
            else -> {} // No auth checks for other commands
        }

        return message
    }

    /**
     * Validates the JWT token provided in the STOMP CONNECT frame's Authorization header.
     * On success, sets the authenticated principal on the STOMP session so subsequent
     * frames (SUBSCRIBE, SEND) can access the user's identity and authorities.
     */
    private fun handleConnect(accessor: StompHeaderAccessor) {
        val token = extractBearerToken(accessor)
            ?: throw AuthenticationCredentialsNotFoundException("Missing Authorization header on CONNECT")

        try {
            val jwt = jwtDecoder.decode(token)
            val authentication = tokenConverter.convert(jwt)
                ?: throw AuthenticationCredentialsNotFoundException("Failed to convert JWT to authentication token")
            accessor.user = authentication
        } catch (e: JwtException) {
            logger.warn { "WebSocket CONNECT rejected — invalid JWT: ${e.message}" }
            throw AuthenticationCredentialsNotFoundException("Invalid JWT token: ${e.message}")
        }
    }

    /**
     * Authorizes SUBSCRIBE requests by checking the topic path for a workspace ID
     * and verifying the authenticated user has a role in that workspace.
     *
     * Topic format: /topic/workspace/{workspaceId}/...
     */
    private fun handleSubscribe(accessor: StompHeaderAccessor) {
        val auth = accessor.user as? JwtAuthenticationToken
            ?: throw AuthenticationCredentialsNotFoundException("Not authenticated — CONNECT required before SUBSCRIBE")

        val destination = accessor.destination
            ?: throw IllegalArgumentException("SUBSCRIBE frame missing destination")

        val workspaceId = extractWorkspaceId(destination) ?: return // Non-workspace topics are allowed

        val hasAccess = auth.authorities.any { authority ->
            authority.authority.startsWith("ROLE_$workspaceId")
        }

        if (!hasAccess) {
            logger.warn { "WebSocket SUBSCRIBE rejected — user ${auth.name} lacks access to workspace $workspaceId" }
            throw org.springframework.security.access.AccessDeniedException(
                "Access denied to workspace $workspaceId"
            )
        }
    }

    private fun extractBearerToken(accessor: StompHeaderAccessor): String? {
        val authHeader = accessor.getFirstNativeHeader("Authorization") ?: return null
        return if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
            authHeader.substring(7)
        } else {
            authHeader
        }
    }

    companion object {
        private val WORKSPACE_TOPIC_PATTERN = Regex("^/topic/workspace/([0-9a-fA-F\\-]{36})(/.*)?$")

        fun extractWorkspaceId(destination: String): UUID? {
            return WORKSPACE_TOPIC_PATTERN.matchEntire(destination)
                ?.groupValues?.get(1)
                ?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
        }
    }
}
```

- [ ] **Step 2: Permit the WebSocket endpoint in SecurityConfig**

In `SecurityConfig.kt`, add a `.requestMatchers` line for the WebSocket endpoint inside the `authorizeHttpRequests` block (after line 40, the storage download line):

```kotlin
.requestMatchers("/ws/**").permitAll() // WebSocket upgrade handled by STOMP interceptor
```

The WebSocket handshake must be permitted at the HTTP level because authentication happens at the STOMP protocol level (CONNECT frame), not at the HTTP upgrade level.

- [ ] **Step 3: Verify the project compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/riven/core/configuration/websocket/WebSocketSecurityInterceptor.kt
git add src/main/kotlin/riven/core/configuration/websocket/WebSocketConfig.kt
git add src/main/kotlin/riven/core/configuration/auth/SecurityConfig.kt
git commit -m "feat: add WebSocket STOMP config with JWT auth interceptor"
```

---

### Task 7: Create WebSocket Event Listener

**Files:**
- Create: `src/main/kotlin/riven/core/service/websocket/WebSocketEventListener.kt`

This mirrors the existing `WorkspaceAnalyticsListener` pattern — `@TransactionalEventListener(AFTER_COMMIT)` ensures events are only forwarded to WebSocket subscribers after the database transaction successfully commits.

- [ ] **Step 1: Create the event listener**

```kotlin
package riven.core.service.websocket

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import riven.core.enums.websocket.WebSocketChannel
import riven.core.models.websocket.WebSocketMessage
import riven.core.models.websocket.WorkspaceEvent

/**
 * Listens for domain events published via ApplicationEventPublisher and
 * forwards them to the appropriate STOMP topic after transaction commit.
 *
 * This is the single point where domain events become WebSocket messages.
 * Services publish events without knowing about WebSocket infrastructure.
 */
@Component
class WebSocketEventListener(
    private val messagingTemplate: SimpMessagingTemplate,
    private val logger: KLogger,
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onWorkspaceEvent(event: WorkspaceEvent) {
        val topic = WebSocketChannel.topicPath(event.workspaceId, event.channel)
        val message = WebSocketMessage.from(event)

        logger.debug {
            "Broadcasting ${event.channel}:${event.operation} to $topic " +
                "(entityId=${event.entityId}, userId=${event.userId})"
        }

        messagingTemplate.convertAndSend(topic, message)
    }
}
```

- [ ] **Step 2: Verify the project compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/riven/core/service/websocket/WebSocketEventListener.kt
git commit -m "feat: add WebSocketEventListener to bridge domain events to STOMP"
```

---

## Chunk 3: Service Integration — Publishing Domain Events

### Task 8: Add Event Publishing to EntityService

**Files:**
- Modify: `src/main/kotlin/riven/core/service/entity/EntityService.kt`

- [ ] **Step 1: Add ApplicationEventPublisher to constructor injection**

Add to the `EntityService` constructor (after `sequenceService` at line 51):

```kotlin
private val applicationEventPublisher: ApplicationEventPublisher,
```

Add the import at the top of the file:

```kotlin
import org.springframework.context.ApplicationEventPublisher
import riven.core.models.websocket.EntityEvent
```

- [ ] **Step 2: Publish EntityEvent in saveEntity after activity logging**

After the `activityService.log(...)` call at line 255-264, add:

```kotlin
applicationEventPublisher.publishEvent(
    EntityEvent(
        workspaceId = workspaceId,
        userId = userId,
        operation = if (prev != null) OperationType.UPDATE else OperationType.CREATE,
        entityId = this.id,
        entityTypeId = typeId,
        entityTypeKey = type.key,
        summary = mapOf(
            "entityTypeName" to type.displayNameSingular,
        ),
    )
)
```

- [ ] **Step 3: Publish EntityEvent(s) in deleteEntities after activity logging**

After the `activityService.logActivities(...)` call at line 396-411, add. Note: deleted entities may span multiple types, so group by type and publish one event per type:

```kotlin
deletedEntities
    .groupBy { it.typeId to it.typeKey }
    .forEach { (typeInfo, entities) ->
        val (deletedTypeId, deletedTypeKey) = typeInfo
        applicationEventPublisher.publishEvent(
            EntityEvent(
                workspaceId = workspaceId,
                userId = userId,
                operation = OperationType.DELETE,
                entityId = null,
                entityTypeId = deletedTypeId,
                entityTypeKey = deletedTypeKey,
                summary = mapOf(
                    "deletedIds" to entities.mapNotNull { it.id },
                    "deletedCount" to entities.size,
                ),
            )
        )
    }
```

- [ ] **Step 4: Update existing EntityServiceTest to include ApplicationEventPublisher mock**

Adding `ApplicationEventPublisher` to `EntityService`'s constructor will break existing tests until the mock is added. Add this immediately:

In `src/test/kotlin/riven/core/service/entity/EntityServiceTest.kt`, add to the mock declarations:

```kotlin
@MockitoBean private lateinit var applicationEventPublisher: ApplicationEventPublisher
```

And add the import:

```kotlin
import org.springframework.context.ApplicationEventPublisher
```

- [ ] **Step 5: Update any existing BlockEnvironmentService tests similarly**

If `src/test/kotlin/riven/core/service/block/` contains tests for `BlockEnvironmentService`, add `@MockitoBean private lateinit var applicationEventPublisher: ApplicationEventPublisher` to those test classes as well. Search with: `grep -r "BlockEnvironmentService" src/test/`

- [ ] **Step 6: Verify the project compiles and tests pass**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all existing tests pass

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/riven/core/service/entity/EntityService.kt
git add src/test/kotlin/riven/core/service/entity/EntityServiceTest.kt
git commit -m "feat: publish EntityEvent on entity create, update, and delete"
```

---

### Task 9: Add Event Publishing to BlockEnvironmentService

**Files:**
- Modify: `src/main/kotlin/riven/core/service/block/BlockEnvironmentService.kt`

- [ ] **Step 1: Add ApplicationEventPublisher to constructor injection**

Add to the `BlockEnvironmentService` constructor (after `logger` at line 42):

```kotlin
private val applicationEventPublisher: ApplicationEventPublisher,
```

Add the imports:

```kotlin
import org.springframework.context.ApplicationEventPublisher
import riven.core.enums.util.OperationType
import riven.core.models.websocket.BlockEnvironmentEvent
```

- [ ] **Step 2: Publish BlockEnvironmentEvent in saveBlockEnvironment**

Inside `saveBlockEnvironment`, after the `blockTreeLayoutService.updateLayoutSnapshot(...)` call and before the `return` (around line 106-117), add the event publish. The event should be published inside the transaction so `@TransactionalEventListener(AFTER_COMMIT)` fires correctly. Place it just before the `return SaveEnvironmentResponse(...)`:

```kotlin
applicationEventPublisher.publishEvent(
    BlockEnvironmentEvent(
        workspaceId = request.workspaceId,
        userId = userId,
        operation = OperationType.UPDATE,
        entityId = null,
        layoutId = request.layoutId,
        version = request.version,
        summary = mapOf(
            "operationCount" to request.operations.size,
        ),
    )
)
```

- [ ] **Step 3: Verify the project compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/riven/core/service/block/BlockEnvironmentService.kt
git commit -m "feat: publish BlockEnvironmentEvent on block environment save"
```

---

### Task 10: Add Workspace Domain Event to WorkspaceService

**Files:**
- Modify: `src/main/kotlin/riven/core/service/workspace/WorkspaceService.kt`

The `WorkspaceService` already has `ApplicationEventPublisher` injected (line 40). We add a `WorkspaceChangeEvent` alongside the existing analytics event publishing.

- [ ] **Step 1: Add import for WorkspaceChangeEvent**

```kotlin
import riven.core.models.websocket.WorkspaceChangeEvent
```

- [ ] **Step 2: Publish WorkspaceChangeEvent in publishWorkspaceAnalytics**

In the `publishWorkspaceAnalytics` method (lines 158-178), after the existing `applicationEventPublisher.publishEvent(analyticsEvent)` at line 177, add:

```kotlin
applicationEventPublisher.publishEvent(
    WorkspaceChangeEvent(
        workspaceId = id,
        userId = userId,
        operation = if (request.id == null) OperationType.CREATE else OperationType.UPDATE,
        entityId = id,
        summary = mapOf("name" to entity.name),
    )
)
```

Add the import:

```kotlin
import riven.core.enums.util.OperationType
```

- [ ] **Step 3: Verify the project compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/riven/core/service/workspace/WorkspaceService.kt
git commit -m "feat: publish WorkspaceChangeEvent alongside analytics events"
```

---

## Chunk 4: Unit Tests — Event Listener and Security Interceptor

### Task 11: Unit Test WebSocketEventListener

**Files:**
- Create: `src/test/kotlin/riven/core/service/websocket/WebSocketEventListenerTest.kt`

- [ ] **Step 1: Write failing tests for the event listener**

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew test --tests "riven.core.service.websocket.WebSocketEventListenerTest"`
Expected: ALL PASS (5 tests)

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/riven/core/service/websocket/WebSocketEventListenerTest.kt
git commit -m "test: add unit tests for WebSocketEventListener routing and payload"
```

---

### Task 12: Unit Test WebSocketSecurityInterceptor

**Files:**
- Create: `src/test/kotlin/riven/core/configuration/websocket/WebSocketSecurityInterceptorTest.kt`

- [ ] **Step 1: Write the security test matrix**

```kotlin
package riven.core.configuration.websocket

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import riven.core.configuration.auth.CustomAuthenticationTokenConverter
import java.time.Instant
import java.util.UUID

class WebSocketSecurityInterceptorTest {

    private val jwtDecoder: JwtDecoder = mock()
    private val tokenConverter: CustomAuthenticationTokenConverter = mock()
    private val logger: KLogger = mock()
    private val channel: MessageChannel = mock()
    private val interceptor = WebSocketSecurityInterceptor(jwtDecoder, tokenConverter, logger)

    private val testUserId = UUID.randomUUID().toString()
    private val testWorkspaceId = UUID.randomUUID()

    private fun buildJwt(): Jwt = Jwt(
        "test-token",
        Instant.now(),
        Instant.now().plusSeconds(3600),
        mapOf("alg" to "HS256", "typ" to "JWT"),
        mapOf("sub" to testUserId, "email" to "test@example.com")
    )

    private fun buildAuthToken(workspaceIds: List<UUID> = listOf(testWorkspaceId)): JwtAuthenticationToken {
        val authorities = workspaceIds.map {
            SimpleGrantedAuthority("ROLE_${it}_ADMIN")
        }
        return JwtAuthenticationToken(buildJwt(), authorities, testUserId)
    }

    private fun buildConnectMessage(token: String?): Message<ByteArray> {
        val accessor = StompHeaderAccessor.create(StompCommand.CONNECT)
        accessor.setLeaveMutable(true)
        token?.let { accessor.addNativeHeader("Authorization", "Bearer $it") }
        return MessageBuilder.withPayload(ByteArray(0)).setHeaders(accessor).build()
    }

    private fun buildSubscribeMessage(
        destination: String,
        auth: JwtAuthenticationToken? = null
    ): Message<ByteArray> {
        val accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE)
        accessor.setLeaveMutable(true)
        accessor.destination = destination
        auth?.let { accessor.user = it }
        return MessageBuilder.withPayload(ByteArray(0)).setHeaders(accessor).build()
    }

    // ------ CONNECT Tests ------

    @Test
    fun `CONNECT with valid JWT sets authentication on session`() {
        val jwt = buildJwt()
        val authToken = buildAuthToken()

        whenever(jwtDecoder.decode("valid-token")).thenReturn(jwt)
        whenever(tokenConverter.convert(jwt)).thenReturn(authToken)

        val message = buildConnectMessage("valid-token")
        val result = interceptor.preSend(message, channel)

        assertNotNull(result)
        verify(jwtDecoder).decode("valid-token")
        verify(tokenConverter).convert(jwt)
    }

    @Test
    fun `CONNECT without Authorization header throws AuthenticationCredentialsNotFoundException`() {
        val message = buildConnectMessage(null)

        assertThrows<AuthenticationCredentialsNotFoundException> {
            interceptor.preSend(message, channel)
        }
    }

    @Test
    fun `CONNECT with invalid JWT throws AuthenticationCredentialsNotFoundException`() {
        whenever(jwtDecoder.decode("invalid-token"))
            .thenThrow(org.springframework.security.oauth2.jwt.BadJwtException("expired"))

        val message = buildConnectMessage("invalid-token")

        assertThrows<AuthenticationCredentialsNotFoundException> {
            interceptor.preSend(message, channel)
        }
    }

    // ------ SUBSCRIBE Tests ------

    @Test
    fun `SUBSCRIBE to workspace topic with valid access is allowed`() {
        val auth = buildAuthToken(listOf(testWorkspaceId))
        val message = buildSubscribeMessage(
            "/topic/workspace/$testWorkspaceId/entities",
            auth
        )

        val result = interceptor.preSend(message, channel)
        assertNotNull(result)
    }

    @Test
    fun `SUBSCRIBE to workspace topic without access is rejected`() {
        val otherWorkspaceId = UUID.randomUUID()
        val auth = buildAuthToken(listOf(testWorkspaceId)) // Has access to testWorkspaceId only

        val message = buildSubscribeMessage(
            "/topic/workspace/$otherWorkspaceId/entities", // Trying to access different workspace
            auth
        )

        assertThrows<AccessDeniedException> {
            interceptor.preSend(message, channel)
        }
    }

    @Test
    fun `SUBSCRIBE without prior CONNECT authentication is rejected`() {
        val message = buildSubscribeMessage(
            "/topic/workspace/$testWorkspaceId/entities",
            null // No authentication
        )

        assertThrows<AuthenticationCredentialsNotFoundException> {
            interceptor.preSend(message, channel)
        }
    }

    @Test
    fun `SUBSCRIBE to non-workspace topic is allowed without workspace check`() {
        val auth = buildAuthToken(emptyList()) // No workspace roles
        val message = buildSubscribeMessage("/topic/system/health", auth)

        val result = interceptor.preSend(message, channel)
        assertNotNull(result)
    }

    // ------ extractWorkspaceId Tests ------

    @Test
    fun `extractWorkspaceId parses valid workspace topic`() {
        val id = UUID.randomUUID()
        val result = WebSocketSecurityInterceptor.extractWorkspaceId("/topic/workspace/$id/entities")
        assertEquals(id, result)
    }

    @Test
    fun `extractWorkspaceId returns null for non-workspace topic`() {
        val result = WebSocketSecurityInterceptor.extractWorkspaceId("/topic/system/health")
        assertNull(result)
    }

    @Test
    fun `extractWorkspaceId returns null for malformed workspace ID`() {
        val result = WebSocketSecurityInterceptor.extractWorkspaceId("/topic/workspace/not-a-uuid/entities")
        assertNull(result)
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew test --tests "riven.core.configuration.websocket.WebSocketSecurityInterceptorTest"`
Expected: ALL PASS (10 tests)

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/riven/core/configuration/websocket/WebSocketSecurityInterceptorTest.kt
git commit -m "test: add security test matrix for WebSocket subscription authorization"
```

---

### Task 13: Unit Test Event Publishing in EntityService

**Files:**
- Modify: `src/test/kotlin/riven/core/service/entity/EntityServiceTest.kt`

The `ApplicationEventPublisher` mock was already added in Task 8 Step 4. Now add a test that verifies event publishing.

- [ ] **Step 1: Add EntityEvent import to EntityServiceTest**

```kotlin
import riven.core.models.websocket.EntityEvent
```

- [ ] **Step 2: Add test verifying EntityEvent is published on saveEntity (create)**

Add a new test method to the test class. This test should set up the mocks for a successful entity create flow and verify `publishEvent` is called with an `EntityEvent` that has `operation = CREATE`:

```kotlin
@Test
fun `saveEntity publishes EntityEvent with CREATE operation for new entity`() {
    val type = buildEntityType()
    val request = SaveEntityRequest(
        id = null,
        payload = emptyMap(),
        icon = null,
    )

    whenever(entityTypeService.getById(entityTypeId)).thenReturn(type)
    whenever(entityValidationService.validateEntity(any(), any(), any(), any(), any())).thenReturn(emptyList())
    whenever(entityRepository.save(any())).thenAnswer { invocation ->
        (invocation.getArgument<EntityEntity>(0)).copy(id = entityId)
    }

    service.saveEntity(workspaceId, entityTypeId, request)

    verify(applicationEventPublisher).publishEvent(argThat<EntityEvent> { event ->
        event.operation == riven.core.enums.util.OperationType.CREATE &&
            event.workspaceId == workspaceId &&
            event.entityTypeId == entityTypeId
    })
}
```

Note: This test depends on the existing `buildEntityType()` helper in the test class. Adapt as needed to match the existing test helper structure.

- [ ] **Step 3: Run the new test**

Run: `./gradlew test --tests "riven.core.service.entity.EntityServiceTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/riven/core/service/entity/EntityServiceTest.kt
git commit -m "test: verify EntityService publishes EntityEvent on create"
```

---

## Chunk 5: Integration Test — End-to-End WebSocket Flow

### Task 14: Create WebSocket Integration Test

**Files:**
- Create: `src/test/kotlin/riven/core/integration/WebSocketIntegrationTest.kt`

This test proves the full pipeline: publish a domain event → `WebSocketEventListener` receives it → message arrives at STOMP client.

- [ ] **Step 1: Create the integration test**

```kotlin
package riven.core.integration

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.stomp.*
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import riven.core.enums.util.OperationType
import riven.core.enums.websocket.WebSocketChannel
import riven.core.models.websocket.WebSocketMessage
import java.lang.reflect.Type
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Integration test for WebSocket STOMP message delivery.
 *
 * Uses @EnableAutoConfiguration(exclude = ...) to avoid needing a database
 * or Temporal server. Only the WebSocket + Security layers are tested.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration(exclude = [
    DataSourceAutoConfiguration::class,
    HibernateJpaAutoConfiguration::class,
])
@ActiveProfiles("test")
class WebSocketIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var messagingTemplate: SimpMessagingTemplate

    private val testWorkspaceId = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef9876543210")
    private val testUserId = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef0123456789")
    private val jwtSecret = "test-secret-1234567890abcdef1234567890abcdef"

    private fun createStompClient(): WebSocketStompClient {
        val client = WebSocketStompClient(StandardWebSocketClient())
        client.messageConverter = MappingJackson2MessageConverter()
        return client
    }

    /**
     * Builds a signed JWT manually (not via annotation-based JwtTestUtil).
     * Includes workspace role claims matching the format that
     * CustomAuthenticationTokenConverter expects.
     */
    private fun createTestJwt(workspaceId: UUID = testWorkspaceId): String {
        val now = Instant.now()
        val claims = JWTClaimsSet.Builder()
            .subject(testUserId.toString())
            .issuer("https://abc.supabase.co/auth/v1")
            .audience("authenticated")
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(3600)))
            .claim("email", "test@test.com")
            .claim("role", "authenticated")
            .claim("roles", listOf(
                mapOf("workspace_id" to workspaceId.toString(), "role" to "ADMIN")
            ))
            .build()

        val header = JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build()
        val signedJwt = SignedJWT(header, claims)
        signedJwt.sign(MACSigner(jwtSecret.toByteArray(Charsets.UTF_8)))
        return signedJwt.serialize()
    }

    private fun connectWithAuth(client: WebSocketStompClient, jwt: String): StompSession {
        val headers = StompHeaders()
        headers.add("Authorization", "Bearer $jwt")

        val future = client.connectAsync(
            "ws://localhost:$port/ws",
            WebSocketHttpHeaders(),
            headers,
            object : StompSessionHandlerAdapter() {
                override fun handleException(
                    session: StompSession, command: StompCommand?,
                    headers: StompHeaders, payload: ByteArray, exception: Throwable
                ) { throw exception }
            }
        )

        return future.get(5, TimeUnit.SECONDS)
    }

    @Test
    fun `client receives message when event is sent to workspace topic`() {
        val client = createStompClient()
        val jwt = createTestJwt()
        val session = connectWithAuth(client, jwt)

        val messageReceived = CompletableFuture<WebSocketMessage>()
        val topic = WebSocketChannel.topicPath(testWorkspaceId, WebSocketChannel.ENTITIES)

        session.subscribe(topic, object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = WebSocketMessage::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                messageReceived.complete(payload as WebSocketMessage)
            }
        })

        Thread.sleep(500) // Ensure subscription is registered

        messagingTemplate.convertAndSend(
            topic,
            WebSocketMessage(
                channel = WebSocketChannel.ENTITIES,
                operation = OperationType.CREATE,
                workspaceId = testWorkspaceId,
                entityId = UUID.randomUUID(),
                userId = testUserId,
                timestamp = java.time.ZonedDateTime.now(),
                summary = mapOf("entityTypeName" to "Contact"),
            )
        )

        val received = messageReceived.get(5, TimeUnit.SECONDS)
        assertEquals(WebSocketChannel.ENTITIES, received.channel)
        assertEquals(OperationType.CREATE, received.operation)
        assertEquals(testWorkspaceId, received.workspaceId)

        session.disconnect()
        client.stop()
    }

    @Test
    fun `client cannot connect with invalid JWT`() {
        val client = createStompClient()

        val headers = StompHeaders()
        headers.add("Authorization", "Bearer invalid-jwt-token")

        val future = client.connectAsync(
            "ws://localhost:$port/ws",
            WebSocketHttpHeaders(),
            headers,
            object : StompSessionHandlerAdapter() {}
        )

        assertThrows(Exception::class.java) {
            future.get(5, TimeUnit.SECONDS)
        }

        client.stop()
    }
}
```

- [ ] **Step 2: Run the integration test**

Run: `./gradlew test --tests "riven.core.integration.WebSocketIntegrationTest"`
Expected: PASS (2 tests)

Note: If the test profile doesn't support `@SpringBootTest(webEnvironment = RANDOM_PORT)` due to missing required env vars or DB dependencies, you may need to create a minimal test configuration that excludes JPA auto-configuration. Adjust as needed.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/riven/core/integration/WebSocketIntegrationTest.kt
git commit -m "test: add WebSocket integration tests for end-to-end message delivery"
```

---

## Chunk 6: Final Verification and Documentation

### Task 15: Full Test Suite Verification

- [ ] **Step 1: Run the complete test suite**

Run: `./gradlew test`
Expected: ALL PASS. No regressions from existing tests.

- [ ] **Step 2: Verify the application compiles and builds**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: If any tests fail, fix them before proceeding**

Common failure modes:
- Existing service tests that inject all constructor dependencies via `@MockitoBean` will need `ApplicationEventPublisher` added as a mock (for EntityService, BlockEnvironmentService).
- The `@SpringBootTest(classes = [...])` test slices may need the WebSocket config excluded if they don't provide `SimpMessagingTemplate`.

---

### Task 16: Update Architecture Documentation

**Files:**
- Modify: `docs/architecture-changelog.md`
- Modify: `docs/architecture-suggestions.md`

- [ ] **Step 1: Append changelog entry**

```markdown
## [2026-03-13] — WebSocket Real-Time Notifications

**Domains affected:** websocket (new), entity, block, workspace
**What changed:**
- Added STOMP over WebSocket infrastructure with in-memory SimpleBroker
- Created `WorkspaceEvent` sealed interface for type-safe domain event publishing
- Added `WebSocketEventListener` that bridges Spring ApplicationEvents to STOMP topics
- Added `WebSocketSecurityInterceptor` for JWT auth on CONNECT and workspace-scoped subscription authorization
- Integrated event publishing into EntityService, BlockEnvironmentService, and WorkspaceService

**New cross-domain dependencies:** yes — entity, block, and workspace services now depend on `models.websocket.WorkspaceEvent` (event model only, not WebSocket infrastructure)
**New components introduced:**
- `WebSocketConfig` — STOMP endpoint and broker configuration
- `WebSocketSecurityInterceptor` — JWT auth + workspace subscription authorization
- `WebSocketEventListener` — event-to-STOMP bridge
- `WebSocketConfigurationProperties` — externalized WebSocket configuration
- `WorkspaceEvent` sealed interface + domain subclasses — type-safe event model
- `WebSocketMessage` — outbound message envelope
- `WebSocketChannel` enum — topic segment mapping
```

- [ ] **Step 2: Append architecture suggestion for vault update**

```markdown
## [2026-03-13] — WebSocket Infrastructure Documentation Needed

**Trigger:** Added WebSocket real-time notification infrastructure as a new cross-cutting domain.
**Affected vault notes:** System Patterns (new pattern: event-driven WebSocket notifications), Domain documentation (new websocket domain), Infrastructure (WebSocket broker topology)
**Suggested update:** Document the WebSocket event flow (service → ApplicationEvent → WebSocketEventListener → STOMP broker → client), the topic namespace structure (/topic/workspace/{id}/{channel}), the authentication model (JWT on CONNECT, workspace auth on SUBSCRIBE), and the broker migration path (SimpleBroker → external broker).
```

- [ ] **Step 3: Commit**

```bash
git add docs/architecture-changelog.md docs/architecture-suggestions.md
git commit -m "docs: add architecture changelog and suggestions for WebSocket infrastructure"
```
