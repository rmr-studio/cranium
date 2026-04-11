---
Created: 2026-03-17
Domains:
  - "[[riven/docs/system-design/domains/Identity Resolution/Identity Resolution]]"
tags:
  - component/active
  - layer/entity
  - architecture/component
---

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Identity Resolution/Clusters/Clusters]]

# IdentityClusterMemberEntity

## Purpose

Join table linking entities to identity clusters. **Intentionally does NOT extend `AuditableSoftDeletableEntity`** — members are hard-deleted when clusters are merged or dissolved (system-managed lifecycle, not user-managed).

## Status

**Scaffolded — not yet functional.** Same as `IdentityClusterEntity`.

## Key Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID, PK | Primary key |
| `clusterId` | UUID, FK to identity_clusters | CASCADE delete |
| `entityId` | UUID, FK to entities | CASCADE delete |
| `joinedAt` | ZonedDateTime | When the entity joined the cluster (default NOW) |
| `joinedBy` | UUID? | User who confirmed the match |

## Database

- **Index:** `idx_identity_cluster_members_cluster` on `(cluster_id)`
- **UNIQUE index:** `idx_identity_cluster_members_entity` on `(entity_id)` — enforces one-cluster-per-entity invariant
- **CASCADE delete** on both FKs — members auto-removed when cluster or entity is deleted

## toModel()

Converts to `IdentityClusterMember` domain model.

## Gotchas

- **UNIQUE constraint on `entity_id`** means an entity can belong to at most one cluster at a time. Cluster merge operations must handle this constraint.

## Related

- [[IdentityClusterEntity]]
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Identity Resolution/Clusters/Clusters]]
