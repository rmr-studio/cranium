---
tags:
  - layer/model
  - component/active
  - architecture/component
Created: 2026-03-14
Domains:
  - "[[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]]"
---
# WebSocketMessage

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/Real-time Events]]

## Purpose

Data class defining the JSON envelope sent to WebSocket subscribers. Contains minimal data for clients to update feed/list views in-place; detail views should refetch via REST API.

---

## Wire Format

```kotlin
data class WebSocketMessage(
    val channel: WebSocketChannel,       // e.g. ENTITIES, BLOCKS
    val operation: OperationType,        // CREATE, UPDATE, DELETE
    val workspaceId: UUID,
    val entityId: UUID?,                 // null for batch operations
    val userId: UUID,                    // user who performed the action
    val timestamp: ZonedDateTime,        // set at broadcast time, not mutation time
    val summary: Map<String, Any?>,      // domain-specific context
)
```

## Key Details

- **`from(event: WorkspaceEvent)` companion factory** converts any `WorkspaceEvent` subtype to this message format. Timestamp is set to `ZonedDateTime.now()` at broadcast time.
- **Lightweight by design:** The message signals what changed, not the full state. Clients use `channel` + `operation` + `entityId` to decide whether to refetch.
- **`summary` field** carries domain-specific context (e.g. `entityTypeName` for entities, `operationCount` for blocks). Clients can use this for toast notifications or inline updates without an API call.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/Real-time Events]] — Parent subdomain
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/WorkspaceEvent]] — Source events converted to this format
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/WebSocketEventListener]] — Creates and sends these messages
