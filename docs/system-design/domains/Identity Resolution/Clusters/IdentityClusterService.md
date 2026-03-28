---
tags:
  - component/active
  - layer/service
  - architecture/component
  - domain/identity-resolution
Created: 2026-03-19
Domains:
  - "[[Identity Resolution]]"
Sub-Domains:
  - "[[Clusters]]"
---

# IdentityClusterService

## Purpose

Handles manual cluster mutation operations for the identity domain — adding entities to existing clusters and renaming clusters. Confirmation-driven cluster creation and merge logic lives in [[IdentityConfirmationService]]; this service covers the user-initiated manual additions and metadata updates.

## Responsibilities

**Owns:**
- Manually adding an entity to an existing cluster with relationship creation
- Renaming existing clusters
- Validation that an entity is not already in any cluster before adding
- Validation that the target member exists in the specified cluster
- Security-safe entity existence checks (returns 404 for both missing and wrong-workspace entities)

**Does NOT own:**
- Cluster creation, expansion, or merge from confirmation flow ([[IdentityConfirmationService]])
- Cluster reads or member enrichment ([[IdentityReadService]])
- Suggestion management

## Dependencies

| Dependency | Type | Purpose |
|---|---|---|
| IdentityClusterRepository | Injected | Cluster lookup by ID + workspace, persistence |
| IdentityClusterMemberRepository | Injected | Member lookup by entityId and by clusterId + entityId, persistence |
| EntityRelationshipService | Injected | Creates CONNECTED_ENTITIES relationship when adding a member |
| EntityService | Injected | Verifies entity exists in workspace |
| IdentityReadService | Injected | Returns enriched ClusterDetailResponse after member addition |
| ActivityService | Injected | Audit logging for add-member and rename operations |
| AuthTokenService | Injected | User ID retrieval from JWT |
| KLogger | Injected | Structured logging |

## Consumed By

| Consumer | Method | Context |
|---|---|---|
| IdentityController | addEntityToCluster | User-initiated manual member addition via REST API |
| IdentityController | renameCluster | User-initiated cluster rename via REST API |

## Public Interface

### addEntityToCluster

```kotlin
@Transactional
@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
fun addEntityToCluster(workspaceId: UUID, clusterId: UUID, request: AddClusterMemberRequest): ClusterDetailResponse
```

Manually adds an entity to an existing identity cluster. Verifies the entity exists in the workspace, is not already in any cluster, and that the target member is in the specified cluster. Creates a CONNECTED_ENTITIES relationship between the new entity and the target member, saves the membership, increments `memberCount`, and returns the enriched cluster detail.

**Parameters:**
- `workspaceId` — workspace scope
- `clusterId` — the cluster to add the entity to
- `request` — contains `entityId` (the entity to add) and `targetMemberId` (existing cluster member to create a relationship with)

**Throws:**
- `NotFoundException` if the cluster does not exist in the workspace, the entity does not exist or belongs to a different workspace, or the target member is not in the cluster.
- `ConflictException` if the entity is already a member of any cluster.

### renameCluster

```kotlin
@Transactional
@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
fun renameCluster(workspaceId: UUID, clusterId: UUID, request: RenameClusterRequest): IdentityCluster
```

Renames an identity cluster. Looks up the cluster by ID scoped to the workspace, updates the name, and logs the change.

**Throws:**
- `NotFoundException` if the cluster does not exist in the given workspace.

## Key Logic

### Add Entity to Cluster Flow

1. Retrieve cluster by ID + workspace (throws NotFoundException if missing)
2. Verify entity exists and belongs to the workspace — returns 404 for both missing and wrong-workspace to prevent information leakage
3. Check entity is not already in any cluster (throws ConflictException if duplicate)
4. Verify target member exists in the specified cluster (throws NotFoundException if missing)
5. Create CONNECTED_ENTITIES relationship via [[EntityRelationshipService]] with `linkSource = SourceType.IDENTITY_MATCH`
6. Save new `IdentityClusterMemberEntity` with `joinedBy` = current user
7. Increment cluster `memberCount` and save
8. Log activity with `action = "member_added"`
9. Delegate to [[IdentityReadService]] `getClusterDetail` to return the enriched response

### Rename Flow

1. Retrieve cluster by ID + workspace (throws NotFoundException if missing)
2. Update `name` field
3. Save and log activity with old and new name in details

## Data Access

| Table | Access Method | Query Type |
|---|---|---|
| identity_clusters | IdentityClusterRepository | `findByIdAndWorkspaceId`, `save` |
| identity_cluster_members | IdentityClusterMemberRepository | `findByEntityId`, `findByClusterIdAndEntityId`, `save` |

## Cross-Domain Dependencies

| Domain | Service | Direction | Purpose |
|---|---|---|---|
| Entities | [[EntityRelationshipService]] | Outbound | Creates CONNECTED_ENTITIES relationship on member addition |
| Entities | [[EntityService]] | Outbound | Verifies entity existence and workspace ownership |

## Error Handling

| Scenario | Behaviour |
|---|---|
| Cluster not found in workspace | Throws NotFoundException (via `findOrThrow` with `findByIdAndWorkspaceId`) |
| Entity not found or wrong workspace | Throws NotFoundException — same response for both to prevent leakage |
| Entity already in a cluster | Throws ConflictException with the existing cluster ID in the message |
| Target member not in cluster | Throws NotFoundException with target member and cluster IDs |

## Gotchas

- **Security-safe 404** — `verifyEntityInWorkspace` uses `EntityService.getEntitiesByIds()` and returns NotFoundException for both missing entities and entities belonging to a different workspace. This prevents an attacker from probing entity existence across workspaces.
- **Target member validation** — the `targetMemberId` in the request is the entity ID of an existing cluster member, not a cluster member row ID. The service verifies via `findByClusterIdAndEntityId`.
- **Return type differs between methods** — `addEntityToCluster` returns `ClusterDetailResponse` (enriched, via [[IdentityReadService]]), while `renameCluster` returns the `IdentityCluster` domain model directly.
