---
tags:
  - architecture/subdomain
  - domain/websocket
Created: 2026-03-14
Domains:
  - "[[Workspaces & Users]]"
---
# Subdomain: Real-time Events

## Overview

Provides WebSocket/STOMP infrastructure for broadcasting workspace-scoped domain events to connected clients in real-time. Services across the application publish domain events via Spring's `ApplicationEventPublisher`; a transactional event listener forwards them as STOMP messages to workspace-scoped topics after transaction commit.

**Event flow:** Domain services publish `WorkspaceEvent` subtypes inside `@Transactional` methods -> Spring's `ApplicationEventPublisher` queues the event -> `@TransactionalEventListener(AFTER_COMMIT)` fires after successful commit -> `WebSocketEventListener` converts the event to a `WebSocketMessage` envelope and sends it to `/topic/workspace/{workspaceId}/{channel}` via `SimpMessagingTemplate`.

**Security model:** The WebSocket endpoint (`/ws`) is permitted through Spring Security's HTTP filter chain (upgrade request only). Real authentication and authorization happen at the STOMP protocol level via `WebSocketSecurityInterceptor`: JWT validation on CONNECT, workspace membership verification on SUBSCRIBE.

**Broker:** Spring's simple in-memory STOMP broker with `/topic` and `/queue` destination prefixes. Heartbeat interval: 10s server/client (configurable via `riven.websocket.*` properties).

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[WebSocketSecurityInterceptor]] | JWT auth on CONNECT, workspace access control on SUBSCRIBE | Security Interceptor |
| [[WebSocketEventListener]] | Bridges domain events to STOMP messages after transaction commit | Service |
| WebSocketConfig | STOMP endpoint registration, broker configuration, transport limits, interceptor wiring | Configuration |
| WebSocketConfigurationProperties | Typed config for endpoint, heartbeat, message size, buffer, timeout (`riven.websocket.*`) | Properties |

**WebSocketChannel enum** (documented inline):
- `ENTITIES` (topic segment: `entities`) -- Entity instance CRUD events
- `BLOCKS` (topic segment: `blocks`) -- Block environment save events
- `WORKFLOWS` (topic segment: `workflows`) -- Workflow events (planned, not yet published)
- `NOTIFICATIONS` (topic segment: `notifications`) -- General notifications (planned)
- `WORKSPACE` (topic segment: `workspace`) -- Workspace-level change events

Provides `topicPath(workspaceId, channel)` companion function to construct fully qualified topic paths: `/topic/workspace/{workspaceId}/{topicSegment}`.

**WorkspaceEvent sealed interface** -- see [[WorkspaceEvent]] for the full event contract.

**WebSocketMessage data class** -- see [[WebSocketMessage]] for the wire format sent to subscribers.

## Topic Structure

All topics follow the pattern `/topic/workspace/{workspaceId}/{channel}`:

| Topic | Channel | Published By | Event Type |
|-------|---------|-------------|------------|
| `/topic/workspace/{id}/entities` | ENTITIES | [[EntityService]] | [[WorkspaceEvent\|EntityEvent]] |
| `/topic/workspace/{id}/blocks` | BLOCKS | BlockEnvironmentService | [[WorkspaceEvent\|BlockEnvironmentEvent]] |
| `/topic/workspace/{id}/workflows` | WORKFLOWS | (not yet published) | [[WorkspaceEvent\|WorkflowEvent]] |
| `/topic/workspace/{id}/notifications` | NOTIFICATIONS | (not yet published) | -- |
| `/topic/workspace/{id}/workspace` | WORKSPACE | [[WorkspaceService]] | [[WorkspaceEvent\|WorkspaceChangeEvent]] |

## Flows

| Flow | Type | Description |
| ---- | ---- | ----------- |
| [[Flow - Domain Event Broadcasting]] | Internal | Service -> ApplicationEventPublisher -> WebSocketEventListener -> STOMP topic |

---

## Technical Debt

| Issue | Impact | Effort |
| ----- | ------ | ------ |
| Simple in-memory broker | Not horizontally scalable -- messages lost if multiple instances run | Medium (swap to external STOMP broker like RabbitMQ) |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-03-14 | Subdomain created -- WebSocket/STOMP infrastructure with JWT security, event listener, 4 event types | WebSocket Notifications |
