---
tags:
  - layer/configuration
  - component/active
  - architecture/component
Created: 2026-03-14
Domains:
  - "[[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]]"
---
# WebSocketConfigurationProperties

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/Real-time Events]]

## Purpose

`@ConfigurationProperties` data class that externalises all WebSocket/STOMP tuneable values under the `riven.websocket` prefix. Provides sensible defaults for endpoint path, heartbeat intervals, and transport limits.

---

## Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `endpoint` | `String` | `/ws` | STOMP endpoint path that clients connect to |
| `allowedOrigins` | `List<String>` | `emptyList()` | Allowed origins for WebSocket connections; falls back to security allowed-origins when empty |
| `serverHeartbeatMs` | `Long` | `10000` | Server-to-client heartbeat interval in ms (0 = disabled) |
| `clientHeartbeatMs` | `Long` | `10000` | Expected client-to-server heartbeat interval in ms (0 = disabled) |
| `maxMessageSizeBytes` | `Int` | `65536` | Maximum inbound STOMP message size (64KB) |
| `sendBufferSizeBytes` | `Int` | `524288` | Per-session send buffer size limit (512KB) |
| `sendTimeoutMs` | `Long` | `15000` | Send timeout in ms; validated to fit in `Int` range at startup |

---

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/WebSocketConfig]] -- Reads all properties to configure STOMP endpoint, broker, and transport

---

## Gotchas

- **`allowedOrigins` fallback:** An empty list does not mean "block all origins." `WebSocketConfig` falls back to `SecurityConfigurationProperties.allowedOrigins` when this list is empty.
- **`sendTimeoutMs` validation:** `WebSocketConfig.configureWebSocketTransport` calls `require()` to verify the value fits in `Int` range, since Spring's `setSendTimeLimit` takes an `Int`. Out-of-range values fail at startup.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/Real-time Events]] -- Parent subdomain
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/WebSocketConfig]] -- Sole consumer of these properties

---

## Changelog

### 2026-03-14 -- Initial implementation

- 7 properties under `riven.websocket` prefix with production-ready defaults
- Covers endpoint path, CORS origins, heartbeat intervals, and transport limits
