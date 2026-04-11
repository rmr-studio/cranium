---
tags:
  - layer/model
  - component/active
  - architecture/component
Created: 2026-03-14
Domains:
  - "[[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]]"
---
# WorkspaceEvent

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/Real-time Events]]

## Purpose

Sealed interface defining the contract for all domain events that can be broadcast over WebSocket. Each concrete implementation maps to a specific `WebSocketChannel` and carries domain-specific context alongside common fields.

---

## Sealed Interface

```kotlin
sealed interface WorkspaceEvent {
    val workspaceId: UUID
    val userId: UUID
    val operation: OperationType   // CREATE, UPDATE, DELETE
    val channel: WebSocketChannel  // determines STOMP topic segment
    val entityId: UUID?            // null for batch operations
    val summary: Map<String, Any?> // domain-specific context for client UI
}
```

## Concrete Event Types

| Event | Channel | Published By | Domain-Specific Fields |
|-------|---------|-------------|----------------------|
| `EntityEvent` | ENTITIES | [[riven/docs/system-design/domains/Entities/Entity Management/EntityService]] | `entityTypeId: UUID`, `entityTypeKey: String` |
| `BlockEnvironmentEvent` | BLOCKS | BlockEnvironmentService | `layoutId: UUID`, `version: Int` |
| `WorkflowEvent` | WORKFLOWS | (not yet published) | — |
| `WorkspaceChangeEvent` | WORKSPACE | [[riven/docs/system-design/domains/Workspaces & Users/Workspace Management/WorkspaceService]] | — |

## Publishing Pattern

Events are published inside `@Transactional` service methods via `applicationEventPublisher.publishEvent(...)` after the repository save completes. The `@TransactionalEventListener(AFTER_COMMIT)` in [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/WebSocketEventListener]] ensures the message is only sent if the transaction commits.

**EntityEvent examples:**
- **Create/Update:** Published in `EntityService.saveEntity()` with `operation = CREATE` or `UPDATE`, `entityId = saved entity ID`, `summary = { entityTypeName }`
- **Delete:** Published in `EntityService.deleteEntities()` grouped by type — `operation = DELETE`, `entityId = null`, `summary = { deletedIds, deletedCount }`

**BlockEnvironmentEvent:** Published in `BlockEnvironmentService.saveBlockEnvironment()` with `operation = UPDATE`, `summary = { operationCount }`

**WorkspaceChangeEvent:** Published in `WorkspaceService.saveWorkspaceTransactional()` with `operation = CREATE` or `UPDATE`, `entityId = workspaceId`, `summary = { name }`

**WorkflowEvent:** Defined but not yet published by any service. Placeholder for future workflow execution status events.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/Real-time Events]] — Parent subdomain
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/WebSocketEventListener]] — Consumes these events
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Real-time Events/WebSocketMessage]] — Wire format these events are converted to
