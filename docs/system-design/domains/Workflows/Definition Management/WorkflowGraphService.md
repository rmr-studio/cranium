---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-02-08
Updated: 2026-02-08
Domains:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
---
# WorkflowGraphService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Definition Management/Definition Management]]

## Purpose

Service for managing workflow graph structure (nodes and edges) with immutable versioning for config changes and cascade deletion for graph consistency.

---

## Responsibilities

- Create and update workflow nodes with config validation
- Implement immutable versioning: config changes create new node version, metadata updates in-place
- Create and delete workflow edges
- Cascade delete edges when nodes are deleted
- Retrieve complete workflow graphs with nodes and edges
- Migrate edge references when node versions change

---

## Dependencies

- `WorkflowNodeRepository` — Node persistence
- `WorkflowEdgeRepository` — Edge persistence
- `WorkflowDefinitionRepository` — Definition lookup for graph queries
- `WorkflowDefinitionVersionRepository` — Version lookup for node ID sets
- `NodeServiceProvider` — Config validation context
- `ActivityService` — Audit logging
- `AuthTokenService` — JWT user extraction

## Used By

- Workflow API controllers — REST endpoints for graph structure management

---

## Key Logic

**Immutable versioning pattern:**

- **Metadata updates (name, description):** Applied in-place to existing node
- **Config updates:** Creates new version
  1. Soft-delete old node version (`deleted=true`)
  2. Create new node with incremented version number
  3. Cascade update all edges referencing old node ID to new node ID
  4. Log edge migration count

**Cascade deletion:**

When deleting a node:
1. Find all edges where `sourceNodeId` or `targetNodeId` equals node ID
2. Soft-delete each connected edge
3. Log CASCADE_NODE_DELETE activity for each edge
4. Soft-delete the node

Maintains graph consistency (no orphaned edges).

**Config validation:**

Before saving node, calls `config.validate(nodeServiceProvider)`. Throws `IllegalArgumentException` if validation fails.

---

## Public Methods

### `createNode(workspaceId, request): WorkflowNode`

Creates new workflow node. Validates config before saving. Returns node model.

### `saveNode(workspaceId, request): SaveWorkflowNodeResponse`

Unified save method. If `request.id` is null, creates new node. Otherwise, updates existing node (metadata or config).

### `updateNode(id, workspaceId, request): WorkflowNode`

Updates node. If config changed, creates new version and migrates edges. If only metadata changed, updates in-place.

### `deleteNode(id, workspaceId)`

Soft-deletes node and all connected edges (cascade). Logs activity for each deletion.

### `createEdge(workspaceId, request): WorkflowEdge`

Creates workflow edge. Validates source and target nodes exist. Returns edge model.

### `deleteEdge(id, workspaceId)`

Soft-deletes workflow edge.

### `getWorkflowGraph(workflowDefinitionId, workspaceId): WorkflowGraph`

Retrieves complete graph for workflow definition. Fetches node IDs from workflow version, loads nodes and edges, returns `WorkflowGraph` with both.

---

## Gotchas

- **Immutable versioning complexity:** Config changes trigger version creation, edge migration, and old version soft-delete. Single update can touch multiple tables.
- **Edge migration:** When node config changes, edges automatically migrate to new node version. Frontend doesn't need to update edge references manually.
- **Cascade deletion activity logging:** Each deleted edge gets its own activity log entry with `reason: "CASCADE_NODE_DELETE"`. Can generate many logs for highly connected nodes.
- **Version number increments:** Each config change increments `node.version`. Version 1 → 2 → 3. Old versions remain in DB as soft-deleted records.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Definition Management/WorkflowDefinitionService]] — Manages workflow metadata
- [[riven/docs/system-design/domains/Workflows/Graph Coordination/WorkflowGraphValidationService]] — Validates graph structure
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Definition Management/Definition Management]] — Parent subdomain
