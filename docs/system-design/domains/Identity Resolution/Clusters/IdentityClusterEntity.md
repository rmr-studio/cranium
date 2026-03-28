---
Created: 2026-03-17
Domains:
  - "[[Identity Resolution]]"
tags:
  - component/active
  - layer/entity
  - architecture/component
---

Part of [[Clusters]]

# IdentityClusterEntity

## Purpose

Workspace-scoped container representing a group of entities confirmed as the same real-world identity. Extends `AuditableSoftDeletableEntity`. Table: `identity_clusters`.

## Status

**Scaffolded — not yet functional.** Entity and repository exist but no services or business logic have been implemented. This is placeholder infrastructure for the next phase of identity resolution.

## Key Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID, PK | Primary key |
| `workspaceId` | UUID, FK to workspaces | Workspace scope |
| `name` | String? | Optional human-readable cluster label |
| `memberCount` | Int | Denormalized count of cluster members |
| *(audit fields)* | via `AuditableSoftDeletableEntity` | createdAt, updatedAt, createdBy, updatedBy, deleted, deletedAt |

## Database

- **Index:** `idx_identity_clusters_workspace` on `(workspace_id) WHERE deleted = false`

## toModel()

Converts to `IdentityCluster` domain model.

## Related

- [[IdentityClusterMemberEntity]]
- [[Clusters]]
