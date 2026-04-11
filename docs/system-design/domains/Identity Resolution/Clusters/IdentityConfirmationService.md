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

# IdentityConfirmationService

## Purpose

Manages the human decision path for identity match suggestions — confirming and rejecting. Confirmation triggers a CONNECTED_ENTITIES relationship, 5-case cluster resolution (create, expand, or merge), activity logging, and a workspace-wide notification. Rejection transitions the suggestion to REJECTED with a signal snapshot for future re-suggestion context.

## Responsibilities

**Owns:**
- Confirming PENDING suggestions: relationship creation, cluster resolution, status transition, activity logging, notification broadcast
- Rejecting PENDING suggestions: signal snapshot to rejectionSignals, soft-delete, activity logging
- 5-case cluster resolution logic (create new, expand existing, merge two clusters)
- Cluster merge semantics: surviving cluster selection, member re-insertion preserving join metadata, dissolving cluster soft-delete
- Cluster naming from NAME signal extraction

**Does NOT own:**
- Suggestion creation or scoring ([[riven/docs/system-design/domains/Identity Resolution/Matching Pipeline/IdentityMatchSuggestionService]])
- Manual cluster mutations ([[IdentityClusterService]])
- Cluster and suggestion reads ([[IdentityReadService]])

## Dependencies

| Dependency | Type | Purpose |
|---|---|---|
| MatchSuggestionRepository | Injected | Suggestion lookup and persistence |
| IdentityClusterRepository | Injected | Cluster creation, lookup, merge, and soft-delete |
| IdentityClusterMemberRepository | Injected | Member lookup by entityId, creation, deletion during merge |
| EntityRelationshipService | Injected | Creates CONNECTED_ENTITIES relationship between confirmed entity pair |
| NotificationService | Injected | Publishes REVIEW_REQUEST broadcast notification on confirmation |
| ActivityService | Injected | Audit logging for confirm and reject operations |
| AuthTokenService | Injected | User ID retrieval from JWT |
| KLogger | Injected | Structured logging |

## Consumed By

| Consumer | Method | Context |
|---|---|---|
| IdentityController | confirmSuggestion | User-initiated confirmation via REST API |
| IdentityController | rejectSuggestion | User-initiated rejection via REST API |

## Public Interface

### confirmSuggestion

```kotlin
@Transactional
@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
fun confirmSuggestion(workspaceId: UUID, suggestionId: UUID): MatchSuggestion
```

Confirms a PENDING match suggestion. Creates a CONNECTED_ENTITIES relationship between the two entities, resolves the identity cluster using 5-case logic, transitions the suggestion to CONFIRMED, logs activity, and publishes a workspace-wide notification.

**Throws:**
- `NotFoundException` if the suggestion does not exist.
- `ConflictException` if the suggestion is not in PENDING status.
- `IllegalArgumentException` if the suggestion does not belong to the specified workspace.

### rejectSuggestion

```kotlin
@Transactional
@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
fun rejectSuggestion(workspaceId: UUID, suggestionId: UUID): MatchSuggestion
```

Rejects a PENDING match suggestion. Snapshots the current signals and confidence score into `rejectionSignals`, transitions to REJECTED, soft-deletes the row, and logs activity. The snapshot enables [[riven/docs/system-design/domains/Identity Resolution/Matching Pipeline/IdentityMatchSuggestionService]] to compare scores on future re-suggestion attempts.

**Throws:**
- `NotFoundException` if the suggestion does not exist.
- `ConflictException` if the suggestion is not in PENDING status.
- `IllegalArgumentException` if the suggestion does not belong to the specified workspace.

## Key Logic

### Confirmation Flow

1. Retrieve suggestion by ID, validate workspace ownership
2. Validate status is PENDING (throws ConflictException otherwise)
3. Create CONNECTED_ENTITIES relationship via [[riven/docs/system-design/domains/Entities/Entity Management/EntityRelationshipService]] with `linkSource = SourceType.IDENTITY_MATCH`
4. Resolve identity cluster using 5-case logic
5. Set status to CONFIRMED, record `resolvedBy` and `resolvedAt`
6. Log confirmation activity with cluster context
7. Publish REVIEW_REQUEST broadcast notification

### Rejection Flow

1. Retrieve suggestion by ID, validate workspace ownership
2. Validate status is PENDING (throws ConflictException otherwise)
3. Snapshot signals and confidence score into `rejectionSignals` JSONB field
4. Set status to REJECTED, record `resolvedBy` and `resolvedAt`
5. Soft-delete: set `deleted = true`, `deletedAt` = now
6. Log rejection activity

### 5-Case Cluster Resolution

`resolveCluster()` determines how to manage cluster membership when a suggestion is confirmed. The two entities involved may be in zero, one, or two existing clusters:

| Case | Source | Target | Action |
|---|---|---|---|
| 1 | Not clustered | Not clustered | Create new cluster with both entities as members |
| 2 | In cluster A | Not clustered | Add target to cluster A |
| 3 | Not clustered | In cluster B | Add source to cluster B |
| 4 | In cluster A | In cluster B | Merge smaller cluster into larger (source survives on tie) |
| 5 | In cluster C | In cluster C | No-op — both already in same cluster |

### Cluster Merge Semantics (Case 4)

The surviving cluster is the one with the higher `memberCount`. On a tie, the source entity's cluster survives.

Merge steps:
1. Hard-delete all members from the dissolving cluster
2. Re-insert them into the surviving cluster, preserving original `joinedAt` and `joinedBy` values
3. Increment surviving cluster's `memberCount` by the number of moved members
4. Soft-delete the dissolving cluster

### Cluster Naming

New clusters (Case 1) derive their display name from the NAME signal in the suggestion's signals list. The `sourceValue` field of the first signal with `type = "NAME"` is used. If no NAME signal exists, the cluster name is null.

### Notification

On confirmation, a REVIEW_REQUEST broadcast notification is published via `NotificationService`. The notification targets all workspace members (`userId = null`) and includes:
- Title: "Identity match confirmed"
- Message with entity display names and updated cluster member count
- `referenceType = ENTITY_RESOLUTION` with `referenceId` = suggestion ID for frontend navigation

Entity display names are resolved from the NAME signal's `sourceValue`/`targetValue` fields, falling back to a truncated entity UUID.

## Data Access

| Table | Access Method | Query Type |
|---|---|---|
| match_suggestions | MatchSuggestionRepository | JPA `findById` |
| identity_clusters | IdentityClusterRepository | JPA `findById`, `save` |
| identity_cluster_members | IdentityClusterMemberRepository | `findByEntityId`, `findByClusterId`, `deleteByClusterId`, `save` |

## Cross-Domain Dependencies

| Domain | Service | Direction | Purpose |
|---|---|---|---|
| Entities | [[riven/docs/system-design/domains/Entities/Entity Management/EntityRelationshipService]] | Outbound | Creates CONNECTED_ENTITIES relationship on confirmation |
| Notifications | `NotificationService` | Outbound | Broadcasts REVIEW_REQUEST notification on confirmation |

## Error Handling

| Scenario | Behaviour |
|---|---|
| Suggestion not found | Throws NotFoundException (via `findOrThrow`) |
| Suggestion not PENDING | Throws ConflictException with current status in message |
| Suggestion belongs to different workspace | Throws IllegalArgumentException via `require()` |
| Cluster not found during resolution | Throws NotFoundException (via `findOrThrow`) |

## Gotchas

- **Merge uses hard-delete + re-insert** — dissolving cluster members are hard-deleted and re-inserted into the surviving cluster rather than being updated in place. This preserves the original `joinedAt` and `joinedBy` metadata while changing the `clusterId`.
- **Rejection soft-deletes the suggestion** — unlike confirmation which keeps the row visible, rejection sets `deleted = true`. This means rejected suggestions are invisible to standard JPQL queries due to `@SQLRestriction("deleted = false")`. The re-suggestion logic in [[riven/docs/system-design/domains/Identity Resolution/Matching Pipeline/IdentityMatchSuggestionService]] uses native SQL to find them.
- **Notification is broadcast** — `userId = null` on the notification request targets all workspace members, not just the confirming user.
