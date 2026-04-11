---
tags:
  - layer/configuration
  - component/active
  - architecture/component
Created: 2026-03-14
Domains:
  - "[[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]]"
---
# WebSocketSecurityInterceptor

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/Real-time Events]]

## Purpose

STOMP-level security interceptor that enforces JWT authentication on CONNECT frames and workspace membership authorization on SUBSCRIBE frames. Bridges the same JWT/authority system used for HTTP requests into the WebSocket protocol layer.

---

## Responsibilities

- Validate JWT bearer token on STOMP CONNECT and set the authenticated principal on the session
- Extract workspace ID from SUBSCRIBE destination topics via regex
- Verify the authenticated user has a `ROLE_{workspaceId}_*` authority for the target workspace
- Reject unauthenticated SUBSCRIBE attempts
- Allow non-workspace topics without workspace checks

---

## Dependencies

- `JwtDecoder` -- Spring Security JWT decoder, validates and parses the bearer token
- `CustomAuthenticationTokenConverter` -- Converts decoded JWT into `JwtAuthenticationToken` with workspace role authorities
- `KLogger` -- Structured logging for rejected connections and subscriptions

## Used By

- `WebSocketConfig` -- Registers this interceptor on the client inbound channel via `configureClientInboundChannel`

---

## Key Logic

**CONNECT handling:**

1. Extract `Authorization` header from STOMP native headers
2. Strip `Bearer ` prefix to get raw token
3. Decode via `jwtDecoder.decode(token)` -- throws on invalid/expired JWT
4. Convert to `JwtAuthenticationToken` via `tokenConverter.convert(jwt)`
5. Set `accessor.user = authentication` -- makes principal available for subsequent frames

**SUBSCRIBE handling:**

1. Retrieve authenticated principal from `accessor.user` -- must be `JwtAuthenticationToken` (set during CONNECT)
2. Extract destination from SUBSCRIBE frame
3. Parse workspace ID from destination via `WORKSPACE_TOPIC_PATTERN` regex: `^/topic/workspace/([0-9a-fA-F\-]{36})(/.*)$`
4. If no workspace ID in destination (non-workspace topic), allow without checks
5. Check if any authority starts with `ROLE_{workspaceId}` -- if not, reject with `AccessDeniedException`

**Other STOMP commands** (SEND, DISCONNECT, etc.) pass through without checks.

---

## Public Methods

### `preSend(message, channel): Message<*>?`

`ChannelInterceptor` hook. Routes to `handleConnect` or `handleSubscribe` based on STOMP command.

### `extractWorkspaceId(destination): UUID?` (companion)

Static utility. Parses workspace UUID from a topic destination string. Returns `null` for non-workspace topics or malformed UUIDs.

---

## Gotchas

- **HTTP-level permitAll:** The `/ws/**` endpoint is permitted in `SecurityConfig`'s HTTP filter chain. This is intentional -- the HTTP layer only handles the WebSocket upgrade handshake. Real auth happens here at the STOMP level.
- **Authority format dependency:** Workspace access check relies on authority strings starting with `ROLE_{workspaceId}`. This format is produced by `CustomAuthenticationTokenConverter` -- changes there would break WebSocket authorization.
- **No DISCONNECT cleanup:** The interceptor does not track connected sessions or handle cleanup on disconnect. Spring's simple broker handles session lifecycle.
- **Regex UUID validation:** `extractWorkspaceId` uses regex capture + `UUID.fromString` with try/catch. Malformed UUIDs in topic paths return `null` (treated as non-workspace topics).

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/Real-time Events]] -- Parent subdomain
- [[riven/docs/system-design/flows/Auth & Authorization]] -- HTTP-level JWT and authority infrastructure this interceptor builds on
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/WebSocketEventListener]] -- The component that sends messages to topics this interceptor protects
- `WebSocketConfig` -- Registers this interceptor

---

## Changelog

### 2026-03-14 -- Initial implementation

- STOMP CONNECT: JWT bearer token validation via `JwtDecoder` + `CustomAuthenticationTokenConverter`
- STOMP SUBSCRIBE: workspace membership check via `ROLE_{workspaceId}_*` authority matching
- Regex-based workspace ID extraction from topic destinations
- Reuses same JWT decoder and token converter as HTTP request pipeline
