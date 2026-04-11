---
tags:
  - architecture/reference
  - domain/identity-resolution
Created: 2026-03-18
Domains:
  - "[[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Identity Resolution/Identity Resolution]]"
---

# Identity Resolution — FAQ

A reference for developers working in or around the Identity Resolution domain. Covers the end-to-end flow, key design decisions, and common questions that come up when reading the codebase.

---

## General

### What does Identity Resolution do?

It detects when two entities in the same workspace likely represent the same real-world identity (e.g. a Stripe customer and an Intercom user who share an email address), surfaces that match as a human-reviewable suggestion, and — once confirmed — groups them into a cluster linked by a CONNECTED_ENTITIES relationship.

### What doesn't it do?

It does **not** merge entities. Integration entities are readonly — their data stays as-is. A confirmed match creates a relationship between the two entities and adds them to an identity cluster. There is no field-level merge, no "golden record", and no write-back to integration sources.

### Where does the code live?

All source code is under `riven.core.{layer}.identity`:
- `service.identity` — pipeline services, cluster management, confirmation
- `entity.identity` — JPA entities (MatchSuggestionEntity, IdentityClusterEntity, IdentityClusterMemberEntity)
- `repository.identity` — Spring Data repositories
- `enums.identity` — MatchSuggestionStatus, MatchSignalType
- `models.identity` — domain models (MatchSuggestion, IdentityCluster, ScoredCandidate, etc.)

Temporal workflow/activity definitions live in the temporal package under the identity-specific interfaces and implementations.

---

## The Matching Pipeline

### How does matching get triggered?

When `EntityService` saves or updates an entity, it publishes an `IdentityMatchTriggerEvent` via Spring's `ApplicationEventPublisher`. An `@TransactionalEventListener(phase = AFTER_COMMIT)` picks this up after the transaction commits, checks whether the entity type has IDENTIFIER-classified attributes, and if so enqueues an IDENTITY_MATCH job.

### What are IDENTIFIER-classified attributes?

Attributes on an entity type whose semantic classification is `SemanticAttributeClassification.IDENTIFIER`. These are attributes that identify a real-world entity — email addresses, phone numbers, names, company names, custom identifiers. The classification is set on the entity type's semantic metadata, not on individual entity instances.

### What happens after a job is enqueued?

The `IdentityMatchDispatcherService` polls the execution queue every 5 seconds (with ShedLock to prevent duplicate processing across instances). It claims pending IDENTITY_MATCH jobs using `SELECT ... FOR UPDATE SKIP LOCKED`, then dispatches each to Temporal via `IdentityMatchQueueProcessorService` using `REQUIRES_NEW` transactions so that individual failures don't roll back the entire batch.

### What does the Temporal workflow do?

`IdentityMatchWorkflow` runs three activities sequentially:

1. **FindCandidates** — `IdentityMatchCandidateService` runs a two-phase pg_trgm query: the `%` operator leverages the GIN index for fast blocking, then `similarity()` refines the result set. Returns candidate matches per attribute.

2. **ScoreCandidates** — `IdentityMatchScoringService` computes a weighted average score per candidate entity pair. Each signal type has a weight (EMAIL=0.9, PHONE=0.85, NAME=0.5, COMPANY=0.3, CUSTOM_IDENTIFIER=0.7). Candidates below the threshold (0.5) are filtered out.

3. **PersistSuggestions** — `IdentityMatchSuggestionService` creates match suggestion rows with canonical UUID ordering. Handles idempotency (skips if active suggestion exists), re-suggestion (replaces a rejected suggestion if the new score is higher), and cluster-aware skipping (skips if both entities are already in the same cluster).

The workflow short-circuits after step 1 or 2 if there are no results.

### Why pg_trgm instead of Elasticsearch?

Stack simplicity. pg_trgm is built into PostgreSQL, requires no additional infrastructure, and provides adequate fuzzy matching for IDENTIFIER attributes. The two-phase query (GIN index blocking + similarity refinement) keeps performance acceptable. If matching volume or accuracy requirements grow significantly, this could be revisited.

### What is canonical UUID ordering?

Every match pair is stored with `source_entity_id < target_entity_id`. This is enforced both in application code (`canonicalOrder()` function) and by a DB CHECK constraint. It prevents storing the same pair as both (A,B) and (B,A), which would create duplicate suggestions.

### What happens if a matching job fails?

Temporal handles retries — each activity has a retry policy (3 attempts, exponential backoff from 1s to 10s). If the entire workflow fails, the queue item is released back to PENDING or marked FAILED after exhausting retries. Stale claimed items (stuck for >5 minutes) are recovered automatically by `recoverStaleItems()`.

---

## Match Suggestions

### What is a match suggestion?

A `MatchSuggestionEntity` row representing a candidate pair of entities that the pipeline believes may be the same real-world identity. It has a confidence score, a list of signals (stored as JSONB), and a status.

### What are the suggestion statuses?

| Status | Meaning |
|--------|---------|
| `PENDING` | Awaiting human review — the only status from which transitions are allowed |
| `CONFIRMED` | User accepted the match — cluster assigned, relationship created |
| `REJECTED` | User dismissed the match — signals snapshot stored for re-suggestion comparison |
| `EXPIRED` | Suggestion aged out (e.g. underlying entity changed significantly) |

### What happens when a suggestion is confirmed?

The `IdentityConfirmationService.confirmSuggestion` method:
1. Validates the suggestion is PENDING (throws ConflictException otherwise)
2. Sets status to CONFIRMED with resolvedBy/resolvedAt
3. Assigns both entities to an identity cluster (see Clusters section below)
4. Creates a CONNECTED_ENTITIES relationship between the two entities via `EntityRelationshipService`
5. Publishes a notification to all workspace members
6. Logs an activity entry for audit

### What happens when a suggestion is rejected?

The `IdentityConfirmationService.rejectSuggestion` method:
1. Validates the suggestion is PENDING (throws ConflictException otherwise)
2. Snapshots the current signals into `rejectionSignals`
3. Sets status to REJECTED with resolvedBy/resolvedAt
4. Soft-deletes the row (`deleted = true`)
5. Logs an activity entry — no notification, no cluster impact

### Why is a rejected suggestion soft-deleted?

The re-suggestion mechanism needs to find previously rejected suggestions for the same entity pair. `MatchSuggestionRepository.findRejectedSuggestion` queries `WHERE deleted = true AND status = 'REJECTED'`. Meanwhile, `findActiveSuggestion` queries `WHERE deleted = false`, so the rejected row is invisible to active suggestion checks. Soft-deletion keeps both query paths consistent.

### What is re-suggestion?

When the pipeline generates a new match for a previously rejected pair, it compares the new score against the rejected suggestion's score. If the new score is higher (meaning new/stronger evidence appeared), it soft-deletes the old rejected row and creates a fresh PENDING suggestion. If the score hasn't improved, it skips silently.

### Can a suggestion be un-confirmed?

Not in v1. Once confirmed, the suggestion is terminal. If a user confirmed by mistake, their recourse is to manually delete the CONNECTED_ENTITIES relationship via the entity relationship API. The cluster membership will remain (orphaned but harmless) until an unconfirm feature ships in v2.

---

## Identity Clusters

### What is an identity cluster?

A group of entities confirmed as representing the same real-world identity. Stored in the `identity_clusters` table with a denormalised `member_count`. Members are tracked in `identity_cluster_members` — a join table linking entity IDs to cluster IDs.

### How are entities assigned to clusters?

When a match suggestion is confirmed, `IdentityClusterService.assignToCluster` evaluates five cases:

| Case | Condition | Action |
|------|-----------|--------|
| 1 | Neither entity is clustered | Create a new cluster with both entities |
| 2 | Source entity is clustered, target is not | Add target to source's cluster |
| 3 | Target entity is clustered, source is not | Add source to target's cluster |
| 4 | Both in different clusters | Merge the smaller cluster into the larger one |
| 5 | Both already in the same cluster | No-op |

### How does cluster merge work?

When two clusters need to merge (case 4):
1. The smaller cluster's members are reassigned to the larger cluster (bulk UPDATE)
2. The larger cluster's `memberCount` is incremented by the smaller cluster's count
3. The smaller cluster is soft-deleted (preserved for audit history)
4. If member counts are equal, the cluster with the lower UUID survives (deterministic tie-breaker)

### Can an entity belong to multiple clusters?

No. A unique index on `identity_cluster_members(entity_id)` enforces that an entity can belong to at most one cluster. This is the database-level guarantee of the Union-Find invariant.

### How are clusters named?

Auto-generated from member entities' NAME-signal attributes (attributes classified with `MatchSignalType.NAME` — typically person names, emails). Distinct name values are joined with " / ". If no NAME attributes exist on any member, the fallback is the entity type name + truncated entity ID. On merge, the surviving cluster keeps its existing name.

### What happens to a cluster if one of its member entities is deleted?

The `identity_cluster_members` table has `ON DELETE CASCADE` on the `entity_id` FK, so the membership row is automatically removed by the database. The `memberCount` on the cluster will become stale — this is a known acceptable trade-off since entity deletion is rare and cluster functionality is still evolving.

---

## Relationships and Cross-Domain Integration

### What relationship is created on confirmation?

A CONNECTED_ENTITIES relationship between the two confirmed entities, created via `EntityRelationshipService.addRelationship`. The relationship uses:
- `linkSource = SourceType.IDENTITY_MATCH` — marks it as identity-resolution-created
- The fallback "Connected Entities" relationship definition on the source entity's type (auto-created per entity type)

This makes the identity link visible in the entity graph, query pipeline, and enrichment layer — it's a real relationship, not just cluster metadata.

### Does Identity Resolution depend on the Notification domain?

Yes. On confirmation, a notification is published to all workspace members via `NotificationService.createInternalNotification`. The notification uses:
- `type = NotificationType.REVIEW_REQUEST` with `priority = NORMAL`
- `referenceType = NotificationReferenceType.ENTITY_RESOLUTION`
- `referenceId = suggestionId` (links back to the confirmed suggestion)

Notification failure does **not** roll back the confirmation — it's wrapped in a try-catch and logged as a warning.

### Does Identity Resolution have REST endpoints?

Not yet. The pipeline is event-driven (entity save → Temporal workflow). Confirm/reject are service-layer methods without controllers. REST endpoints for suggestion review, cluster browsing, and confirmation/rejection are planned for Phase 5.

---

## Security and Multi-Tenancy

### How is workspace isolation enforced?

Two mechanisms:
- **Pipeline services** (candidate finding, scoring, suggestion persistence) have **no `@PreAuthorize`** because they run inside Temporal activities with no JWT context. Workspace isolation is enforced via explicit `WHERE workspace_id = :workspaceId` predicates in all queries.
- **Confirmation/rejection services** use `@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")` because they are user-initiated operations with a JWT in the security context.

### Who can confirm or reject a suggestion?

Any workspace member. There is no entity-owner-level permission check — identity resolution is treated as a workspace-level operation. The confirming user's ID is recorded as `resolvedBy` for audit purposes.

### Can matches happen across workspaces?

No. Workspace boundaries are security boundaries. All queries filter by `workspace_id`, and the candidate finding query only searches entities within the same workspace.

---

## Debugging and Operations

### How do I check if matching is running for a workspace?

1. Look for IDENTITY_MATCH jobs in the `execution_queue` table: `SELECT * FROM execution_queue WHERE job_type = 'IDENTITY_MATCH' AND workspace_id = ?`
2. Check Temporal UI for workflows on the `identity.match` task queue
3. Check application logs for `IdentityMatchDispatcherService` and `IdentityMatchQueueProcessorService` entries

### Why didn't a match get triggered after entity save?

Check these in order:
1. Does the entity type have IDENTIFIER-classified attributes? → `EntityTypeClassificationService` caches this
2. Was this an update where IDENTIFIER attribute values actually changed? → The trigger listener skips updates with unchanged identifier values
3. Is there already a PENDING job for this entity? → Queue deduplication skips duplicate enqueues
4. Is the dispatcher running? → Check ShedLock — only one instance polls at a time

### Why did a match suggestion not get created despite candidates existing?

1. Score below threshold (0.5) → `IdentityMatchScoringService` filters these out
2. Active suggestion already exists for this pair → idempotent skip in `persistSuggestions`
3. Both entities already in the same cluster → cluster-aware skip in `persistSuggestions`
4. Previously rejected with same or higher score → re-suggestion logic skips unless new score is strictly higher

### How do I see what signals contributed to a match?

The `signals` JSONB column on `match_suggestions` contains an array of signal objects, each with `type` (EMAIL, PHONE, NAME, etc.), `sourceValue`, `targetValue`, `similarity` (0.0-1.0), and `weight`. The `confidence_score` is the weighted average across all signals.

---

## Architecture Decisions Summary

| Decision | Why |
|----------|-----|
| Async matching via Temporal | Production-grade retries, observability, handles long-running scans. 2-5s latency is acceptable for background matching. |
| Clusters (not pairwise) | Transitive discovery: if A=B and B=C, then A=C. Scales to N entities per identity. |
| Relationship-only confirmation | Integration data stays pristine. No merge conflicts. Links are visible to query pipeline and enrichment. |
| Clusters at confirmation only | Simplifies suggestion service — no cleanup on rejection. Cluster logic isolated in ConfirmationService. |
| Signals as JSONB | Always read/written with parent suggestion. Never queried independently. Eliminates a table, JOIN, and repository. |
| Generic execution queue | Shared `execution_queue` table with `job_type` discriminator. Reused across workflow and identity match domains. |
| No auto-confirm | Requires learned thresholds. All matches are human-reviewed in v1. |
| No entity merge | Too complex for v1. Merge conflicts, field-level precedence, write-back to sources — all deferred. |
