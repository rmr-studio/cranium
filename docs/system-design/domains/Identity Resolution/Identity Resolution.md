---
tags:
  - architecture/domain
  - domain/identity-resolution
Created: 2026-03-17
Updated: 2026-03-27
---

# Identity Resolution

## Overview

Identity Resolution detects duplicate entities within a workspace using pg_trgm trigram similarity, weighted scoring, and human-reviewable match suggestions. The pipeline runs as a Temporal-orchestrated background process, currently triggered only via domain events (entity save/update) — there are no REST API endpoints.

## Boundaries

**Owns:**
- Candidate finding (pg_trgm blocking via GIN index)
- Scoring (weighted signals with configurable per-signal-type weights)
- Suggestion persistence (idempotent creation, re-suggestion, rejection)
- Cluster entities (scaffolded for future use)
- Temporal workflow orchestration for the matching pipeline

**Does NOT own:**
- Entity CRUD (Entities domain)
- Queue infrastructure (Workflows domain — ExecutionQueueEntity)
- Activity logging (Activity domain)
- User authentication

## Sub-Domains

| Sub-Domain | Scope | Status |
|---|---|---|
| [[Matching Pipeline]] | Candidate finding, scoring, suggestion management, queue dispatch, classification cache | Active |
| [[Clusters]] | Entity grouping into confirmed identity clusters | Scaffolded |
| [[Temporal Integration]] | Workflow and activity orchestration for the matching pipeline | Active |

## Flows

| Flow | Type | Summary |
|---|---|---|
| [[Flow - Identity Match Pipeline]] | Background | Entity save → event → queue → Temporal → candidates → scoring → suggestions |

## Data

### Owned Entities

| Entity | Table | Description |
|---|---|---|
| MatchSuggestionEntity | match_suggestions | Candidate pair for human review — JSONB signals, canonical UUID ordering, soft-deletable |
| IdentityClusterEntity | identity_clusters | Workspace-scoped cluster container with member count tracking, soft-deletable |
| IdentityClusterMemberEntity | identity_cluster_members | Join table linking entities to clusters — hard-deleted (not AuditableSoftDeletableEntity) |

## External Dependencies

| Dependency | Purpose |
|---|---|
| Temporal Server | Workflow orchestration — identity.match task queue, activity retry policies |
| PostgreSQL pg_trgm extension | Trigram similarity search for fuzzy candidate matching via GIN index |

## Domain Interactions

### Depends On

| Domain | Interaction |
|---|---|
| Entities | Queries `entity_attributes` and `entity_type_semantic_metadata` via native SQL for IDENTIFIER-classified attributes |
| Workflows | Uses `ExecutionQueueEntity` and queue infrastructure for IDENTITY_MATCH job dispatch |
| Activity | Uses `ActivityService` for audit logging of suggestion create/reject |

### Consumed By

Nothing currently — no REST API, no external consumers. Triggered internally via domain events published by EntityService.

## Service Summary

| Sub-Domain | Service | Purpose |
|---|---|---|
| Matching Pipeline | [[IdentityMatchCandidateService]] | pg_trgm candidate finding — two-phase GIN index blocking + similarity threshold |
| Matching Pipeline | [[IdentityMatchScoringService]] | Weighted scoring — pure computation, weighted average with configurable per-signal-type weights |
| Matching Pipeline | [[IdentityMatchSuggestionService]] | Suggestion persistence — idempotent creation, re-suggestion on improved score, rejection |
| Matching Pipeline | EntityTypeClassificationService | Cached IDENTIFIER attribute lookup for entity types using ConcurrentHashMap |
| Matching Pipeline | IdentityMatchQueueService | IDENTITY_MATCH job enqueueing with deduplication via partial unique index |
| Matching Pipeline | IdentityMatchDispatcherService | Scheduled queue polling (every 5s) with ShedLock distributed locking |
| Matching Pipeline | IdentityMatchQueueProcessorService | Per-item processing with REQUIRES_NEW transactions — dispatches to Temporal |
| Temporal Integration | IdentityMatchWorkflowImpl | Temporal workflow — deterministic orchestration of 3-activity pipeline with short-circuit |
| Temporal Integration | IdentityMatchActivitiesImpl | Temporal activities — thin delegation to CandidateService, ScoringService, SuggestionService |

## Key Decisions

| Decision | Rationale |
|---|---|
| pg_trgm for fuzzy matching | Keeps the stack simple — avoids introducing Elasticsearch or a separate search service. GIN index provides fast blocking. |
| Canonical UUID ordering (source < target) | Prevents duplicate pairs. Enforced both in application code and via DB CHECK constraint. |
| Soft-delete on rejection | Enables re-suggestion comparison — new scores can be compared against rejected scores to determine if a re-suggestion is warranted. |
| No @PreAuthorize annotations | Called from Temporal without JWT context. Workspace isolation enforced at query level in WHERE clauses. |
| Dedicated IDENTITY_MATCH queue type | Isolates identity match jobs from the default workflow queue, enabling independent polling and scaling. |

## Technical Debt

- Cluster entities are scaffolded but no services exist yet — pending future phase for confirmed match grouping.
- No REST API endpoints — suggestions can only be created via the pipeline, rejection endpoint not yet exposed.
- Fixed similarity threshold — not configurable per workspace.
- `MatchSignalType.fromSchemaType` only maps EMAIL/PHONE directly; all other IDENTIFIER-classified schema types map to CUSTOM_IDENTIFIER.

## Recent Changes

| Date | Change | Domains |
|---|---|---|
| 2026-03-17 | Initial identity resolution domain | Identity Resolution |
| 2026-03-27 | Integration with entity ingestion pipeline — ingestion-time identity resolution, cross-source matching, cluster auto-creation | Entity Ingestion Pipeline |

---

## Integration with Entity Ingestion Pipeline

Identity resolution is **Step 4** of the entity ingestion pipeline: Classify → Route → Map → **Resolve**. After a new entity is mapped from integration data and projected into a core entity type, the resolution step determines whether this data represents an already-known identity.

### Ingestion-Time Resolution

When the projection pipeline creates a new core entity from integration data, both the integration entity and the projected core entity are immediately added to the same identity cluster. This prevents the background matching pipeline from re-suggesting them as duplicates.

> [!info] Two resolution paths
> The new `IdentityResolutionService` (in `service.ingestion`) handles **synchronous ingestion-time matching** as part of the pipeline workflow. The existing `IdentityMatchCandidateService` continues to handle **background async matching** via Temporal for user-created entities and periodic re-evaluation.

### Cross-Source Identity Matching

When two integrations (e.g., Zendesk + HubSpot) sync contacts with the same email, identity resolution matches them to the same core Customer entity. This collapses N integration sources into a single user-facing hub entity.

Identity clusters are relationship graphs: one core entity linked to N integration entities. Each cluster represents a single real-world identity across all connected sources.

### References

- [[Entity Ingestion Pipeline]]
- [[Smart Projection Architecture]]
