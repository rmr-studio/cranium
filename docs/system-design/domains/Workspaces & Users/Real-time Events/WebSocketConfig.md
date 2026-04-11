---
tags:
  - layer/configuration
  - component/active
  - architecture/component
Created: 2026-03-14
Domains:
  - "[[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]]"
---
# WebSocketConfig

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/Real-time Events]]

## Purpose

Spring `WebSocketMessageBrokerConfigurer` that registers the STOMP endpoint, configures the in-memory message broker with heartbeats, and applies transport limits. Wires [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/WebSocketSecurityInterceptor]] into the client inbound channel for STOMP-level auth.

---

## Responsibilities

- Register STOMP endpoint at the configured path (default `/ws`) with allowed origins
- Enable simple broker on `/topic` and `/queue` prefixes with configurable heartbeat intervals
- Set application destination prefix (`/app`) for @MessageMapping methods
- Apply transport limits: max message size, send buffer size, send timeout
- Register [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/WebSocketSecurityInterceptor]] on the client inbound channel

---

## Dependencies

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/WebSocketConfigurationProperties]] -- WebSocket-specific configuration (endpoint, heartbeats, transport limits)
- `SecurityConfigurationProperties` -- Fallback for allowed origins when WebSocket-specific origins are not set
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/WebSocketSecurityInterceptor]] -- STOMP-level JWT auth and workspace membership checks

## Used By

- Spring framework -- auto-detected via `@Configuration` + `@EnableWebSocketMessageBroker`

---

## Key Logic

**registerStompEndpoints:**
- Uses `wsProperties.allowedOrigins` if non-empty, otherwise falls back to `securityProperties.allowedOrigins`
- Registers single endpoint at `wsProperties.endpoint` (default `/ws`)

**configureMessageBroker:**
- Enables simple (in-memory) broker for `/topic` and `/queue` destinations
- Heartbeat: server sends every `serverHeartbeatMs` (default 10s), expects client every `clientHeartbeatMs` (default 10s)
- Heartbeat scheduling delegated to a dedicated `ThreadPoolTaskScheduler` (`ws-heartbeat-` thread prefix, pool size 1) — required for the simple broker to actually send heartbeat frames
- Application prefix `/app` for routed messages

**configureWebSocketTransport:**
- Validates `sendTimeoutMs` fits in Int range via `require()`
- Applies `maxMessageSizeBytes` (64KB default), `sendBufferSizeBytes` (512KB default), `sendTimeoutMs` (15s default)

**configureClientInboundChannel:**
- Registers [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/WebSocketSecurityInterceptor]] to intercept all inbound STOMP frames

---

## Gotchas

- **Origin fallback:** If `riven.websocket.allowed-origins` is empty, the config falls back to `riven.security.allowed-origins`. This means WebSocket origins track HTTP origins by default but can be overridden independently.
- **Simple broker limitations:** Uses Spring's in-memory simple broker, not a full message broker (RabbitMQ/ActiveMQ). Sufficient for single-instance deployments but does not support multi-instance broadcasting. Tracked as technical debt in [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/Real-time Events]].
- **No SockJS fallback:** The endpoint is registered without `.withSockJS()`, so clients must support native WebSocket connections.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/Real-time Events]] -- Parent subdomain
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/WebSocketSecurityInterceptor]] -- Registered on inbound channel for STOMP auth
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/WebSocketConfigurationProperties]] -- All tuneable values
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/WebSocketEventListener]] -- Sends messages through the broker configured here

---

## Changelog

### 2026-03-16 -- Heartbeat scheduler

- Added dedicated `ThreadPoolTaskScheduler` for broker heartbeats via `heartbeatScheduler()` private method
- Without a task scheduler, the simple broker configures heartbeat values but never sends heartbeat frames — connections drop silently after the client's expected interval

### 2026-03-14 -- Initial implementation

- STOMP endpoint registration with configurable path and allowed origins
- Simple broker on `/topic` and `/queue` with configurable heartbeats
- Transport limits (message size, buffer size, send timeout) from configuration properties
- Client inbound channel interceptor for STOMP-level security
