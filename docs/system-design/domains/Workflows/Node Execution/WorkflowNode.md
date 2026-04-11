---
tags:
  - layer/model
  - component/active
  - architecture/component
Domains:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
Created: 2026-02-08
Updated: 2026-02-08
---
# WorkflowNode

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Node Execution]]

## Purpose

Runtime DTO that wraps `WorkflowNodeConfig` with entity metadata (id, workspaceId, key) for execution — bridges persistence layer and execution layer by providing guaranteed non-null ID and workspace context for security checks.

---

## Responsibilities

- Provide guaranteed non-null ID for persisted nodes (entities have nullable IDs before persistence)
- Delegate `execute()` to underlying config for node execution
- Carry workspace context (workspaceId) for security validation
- Expose node type and version from config
- Bridge three-layer architecture: WorkflowNodeConfig (pure config) → WorkflowNodeEntity (JPA) → WorkflowNode (runtime DTO)

---

## Dependencies

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/WorkflowNodeConfig]] — Wraps the config for execution delegation
- `WorkflowDataStore` — Passed through to config execution
- `NodeServiceProvider` — Passed through to config execution

## Used By

- [[riven/docs/system-design/domains/Workflows/Execution Engine/WorkflowGraphCoordinationService]] — Executes nodes via `execute()` during DAG traversal
- [[riven/docs/system-design/domains/Workflows/Graph Coordination/WorkflowGraphTopologicalSorterService]] — Sorts nodes for execution order
- [[riven/docs/system-design/domains/Workflows/Graph Coordination/WorkflowGraphQueueManagementService]] — Tracks node readiness in queue system

---

## Key Logic

**Three-layer architecture pattern:**

1. **WorkflowNodeConfig** — Pure configuration and execution logic (no ID)
   - Exists before persistence
   - Serializable to JSONB without entity metadata
   - Contains only business logic and configuration

2. **WorkflowNodeEntity** — JPA entity for persistence (nullable ID)
   - Database representation with @Entity annotations
   - ID is nullable because it's generated on save
   - Contains entity lifecycle metadata (createdAt, updatedAt)

3. **WorkflowNode** (this class) — Runtime DTO with guaranteed non-null ID
   - Created only from persisted entities via `toExecutableNode()`
   - ID is guaranteed non-null (entity must be saved)
   - Provides execution context (workspaceId for security)

**Why this exists:**

`WorkflowNodeConfig` can't have an ID because:
- IDs are DB-generated during persistence
- Config objects are created before saving to the database
- Node configuration needs to be serializable to JSONB without the entity's ID

Solution: `WorkflowNode` wraps the config with entity metadata after persistence.

**Execution delegation:**

```kotlin
fun execute(dataStore, inputs, services): NodeOutput =
    config.execute(dataStore, inputs, services)
```

Simple delegation to underlying config. All execution logic lives in `WorkflowNodeConfig` implementations.

**Security context:**

Before execution, services can check:

```kotlin
if (node.workspaceId != currentWorkspaceId) {
    throw SecurityException("Node does not belong to workspace")
}
```

---

## Public Methods

### `execute(dataStore: WorkflowDataStore, inputs: JsonObject, services: NodeServiceProvider): NodeOutput`

Execute this node with given datastore and resolved inputs. Delegates to underlying `WorkflowNodeConfig.execute()`. Returns typed `NodeOutput` representing execution result. Throws exceptions on execution failure.

---

## Fields

- `id: UUID` — Database-generated node ID (non-null, from persisted entity)
- `workspaceId: UUID` — Workspace this node belongs to (for security checks)
- `key: String` — Unique identifier within workflow (human-readable, e.g., "node1", "httpRequest")
- `name: String` — Human-readable display name
- `description: String?` — Optional description of node's purpose
- `config: WorkflowNodeConfig` — Underlying configuration with execution logic

**Computed properties:**
- `type: WorkflowNodeType` — Delegated from config.type
- `version: Int` — Delegated from config.version

---

## Gotchas

- **Created only from persisted entities:** Cannot construct `WorkflowNode` until entity is saved (ID must exist). Use `WorkflowNodeEntity.toExecutableNode()` after persistence.
- **ID vs key distinction:** `id` is UUID from database (globally unique, opaque). `key` is human-readable string (unique within workflow, e.g., "node1", "sendEmail").
- **Immutable DTO:** Data class is immutable. Changes to config require updating entity and creating new `WorkflowNode` instance.
- **Workspace isolation:** `workspaceId` enables multi-tenant security. Always validate workspace matches current user's context before execution.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/WorkflowNodeConfig]] — Underlying configuration interface
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/WorkflowNodeEntity]] — JPA entity for persistence
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Node Execution]] — Parent subdomain
