---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-14
Domains:
  - "[[Workspaces & Users]]"
---
# WebSocketEventListener

Part of [[Real-time Events]]

## Purpose

Single-method component that bridges Spring application events to WebSocket STOMP messages. Listens for all `WorkspaceEvent` subtypes via `@TransactionalEventListener(phase = AFTER_COMMIT)` and forwards them as `WebSocketMessage` envelopes to workspace-scoped STOMP topics.

---

## Key Details

- **Transaction safety:** `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` ensures events only fire after the publishing transaction commits successfully. If the transaction rolls back, no WebSocket message is sent.
- **Single listener method:** `onWorkspaceEvent(event: WorkspaceEvent)` handles all event types via the sealed interface. No per-type dispatch logic needed.
- **Topic resolution:** Uses `WebSocketChannel.topicPath(event.workspaceId, event.channel)` to construct the destination path.
- **Message conversion:** `WebSocketMessage.from(event)` maps event fields to the wire format, adding a `ZonedDateTime.now()` timestamp.
- **Delivery:** `SimpMessagingTemplate.convertAndSend(topic, message)` sends to all subscribers of the topic.

## Dependencies

- `SimpMessagingTemplate` — Spring's STOMP messaging template for sending to broker destinations
- `KLogger` — Debug-level logging of each broadcast (channel, operation, topic, entityId, userId)

---

## Related

- [[Real-time Events]] — Parent subdomain
- [[WorkspaceEvent]] — The sealed event interface this listener consumes
- [[WebSocketMessage]] — The message envelope this listener produces
- [[WebSocketSecurityInterceptor]] — Protects the topics this listener sends to
