---
tags:
  - component/active
  - layer/service
  - architecture/component
  - domain/identity-resolution
Created: 2026-03-19
Domains:
  - "[[riven/docs/system-design/domains/Identity Resolution/Identity Resolution]]"
Sub-Domains:
  - "[[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Identity Resolution/Clusters/Clusters]]"
---

# IdentityReadService

## Purpose

Read-only query service for the identity domain REST API. Exposes suggestion listing, cluster listing, cluster detail with enriched members, and pending match counts. All mutation operations live in [[IdentityConfirmationService]] and [[IdentityClusterService]]. No activity logging is performed since reads do not require an audit trail.

## Responsibilities

**Owns:**
- Listing non-deleted suggestions (PENDING + CONFIRMED) for a workspace
- Single suggestion retrieval with workspace scoping
- Listing cluster summaries for a workspace
- Cluster detail retrieval with batch-enriched member context
- Pending match count for a given entity

**Does NOT own:**
- Suggestion creation or scoring ([[riven/docs/system-design/domains/Identity Resolution/Matching Pipeline/IdentityMatchSuggestionService]])
- Suggestion confirmation or rejection ([[IdentityConfirmationService]])
- Cluster mutations ([[IdentityClusterService]])

## Dependencies

| Dependency | Type | Purpose |
|---|---|---|
| MatchSuggestionRepository | Injected | Suggestion queries by workspace and by ID |
| IdentityClusterRepository | Injected | Cluster queries by workspace and by ID + workspace |
| IdentityClusterMemberRepository | Injected | Member queries by cluster ID |
| EntityService | Injected | Batch entity lookup for member enrichment |
| AuthTokenService | Injected | User ID retrieval from JWT |
| KLogger | Injected | Structured logging |

## Consumed By

| Consumer | Method | Context |
|---|---|---|
| IdentityController | listSuggestions | REST API — list all workspace suggestions |
| IdentityController | getSuggestion | REST API — single suggestion detail |
| IdentityController | listClusters | REST API — list all workspace clusters |
| IdentityController | getClusterDetail | REST API — cluster detail with enriched members |
| IdentityController | getPendingMatchCount | REST API — pending suggestion count for an entity |
| [[IdentityClusterService]] | getClusterDetail | Returns enriched response after `addEntityToCluster` |

## Public Interface

### listSuggestions

```kotlin
@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
fun listSuggestions(workspaceId: UUID): List<SuggestionResponse>
```

Returns all non-deleted suggestions (PENDING and CONFIRMED) for the workspace. Rejected suggestions are invisible because they are soft-deleted and filtered by `@SQLRestriction`.

### getSuggestion

```kotlin
@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
fun getSuggestion(workspaceId: UUID, suggestionId: UUID): SuggestionResponse
```

Returns a single suggestion by ID. Performs an inline workspace ownership check after fetching by ID — throws NotFoundException if the suggestion belongs to a different workspace.

**Throws:** `NotFoundException` if the suggestion does not exist or belongs to a different workspace.

### listClusters

```kotlin
@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
fun listClusters(workspaceId: UUID): List<ClusterSummaryResponse>
```

Returns all non-deleted clusters for the workspace as summary items containing ID, name, member count, and creation timestamp.

### getClusterDetail

```kotlin
@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
fun getClusterDetail(workspaceId: UUID, clusterId: UUID): ClusterDetailResponse
```

Returns cluster metadata plus an enriched member list. Each member is mapped to a `ClusterMemberContext` with entity metadata (typeKey, sourceType, identifierKey) resolved via batch entity lookup.

**Throws:** `NotFoundException` if the cluster does not exist in the given workspace.

### getPendingMatchCount

```kotlin
@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
fun getPendingMatchCount(workspaceId: UUID, entityId: UUID): PendingMatchCountResponse
```

Returns the count of PENDING suggestions where the given entity is either the source or target. Used by the frontend to display badge counts on entity views.

## Key Logic

### Member Enrichment

`getClusterDetail` batch-loads entity data for all cluster members via `EntityService.getEntitiesByIds()` and maps each member to a `ClusterMemberContext`:

| Field | Source | Nullable |
|---|---|---|
| `entityId` | Cluster member row | No |
| `typeKey` | Entity lookup | Yes — null if entity soft-deleted since joining |
| `sourceType` | Entity lookup | Yes — null if entity soft-deleted since joining |
| `identifierKey` | Entity lookup | Yes — null if entity soft-deleted since joining |
| `joinedAt` | Cluster member row | No |

The entity map is keyed by entity ID. Members whose entities are missing from the map (soft-deleted) still appear in the response with null metadata fields rather than being filtered out.

### Workspace Scoping

- `listSuggestions`, `listClusters`, `getPendingMatchCount` — repository queries filter by `workspaceId` directly
- `getSuggestion` — fetches by ID then validates `workspaceId` match inline (throws NotFoundException on mismatch)
- `getClusterDetail` — uses `findByIdAndWorkspaceId` repository method

## Data Access

| Table | Access Method | Query Type |
|---|---|---|
| match_suggestions | MatchSuggestionRepository | `findByWorkspaceId`, `findById`, `countPendingForEntity` |
| identity_clusters | IdentityClusterRepository | `findByWorkspaceId`, `findByIdAndWorkspaceId` |
| identity_cluster_members | IdentityClusterMemberRepository | `findByClusterId` |

## Cross-Domain Dependencies

| Domain | Service | Direction | Purpose |
|---|---|---|---|
| Entities | [[riven/docs/system-design/domains/Entities/Entity Management/EntityService]] | Outbound | Batch entity lookup for member enrichment in `getClusterDetail` |

## Error Handling

| Scenario | Behaviour |
|---|---|
| Suggestion not found | Throws NotFoundException (via `findOrThrow`) |
| Suggestion belongs to different workspace | Throws NotFoundException with suggestion ID in message |
| Cluster not found in workspace | Throws NotFoundException (via `findOrThrow` with `findByIdAndWorkspaceId`) |

## Gotchas

- **getSuggestion uses inline workspace check** — unlike cluster queries which use `findByIdAndWorkspaceId`, suggestion retrieval fetches by ID first then validates workspace match. This is because `MatchSuggestionRepository` does not have a combined ID + workspace query method.
- **Soft-deleted entities still appear as members** — `enrichMembers` does not filter out members whose entities have been soft-deleted. They appear with null `typeKey`, `sourceType`, and `identifierKey` fields. The frontend should handle these gracefully.
- **No activity logging** — this service is read-only. All audit trail responsibility is in the mutation services.
- **Internal consumer** — [[IdentityClusterService]] calls `getClusterDetail` after `addEntityToCluster` to return an enriched response. This means `getClusterDetail` is both a REST API method and an internal service dependency.
