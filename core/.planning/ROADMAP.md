# Roadmap: Identity Resolution

## Overview

Build the identity resolution domain bottom-up: schema and queue infrastructure first, then the matching pipeline in isolation, then live entity save triggers, then confirmation and cluster management, and finally the REST API. Each phase is fully testable before the next builds on it. The three highest-risk pitfalls (GIN index expression, workspace scoping, duplicate constraint) are baked into Phase 1 DDL so they cannot surface later with data in the tables.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Infrastructure** - Generic queue refactor, schema tables, pg_trgm index, and domain enums (completed 2026-03-15)
- [ ] **Phase 2: Matching Pipeline** - Scoring, candidate selection, Temporal workflow, and suggestion state machine
- [ ] **Phase 3: Trigger and Dispatch** - Entity save event listener and queue-to-Temporal dispatcher
- [ ] **Phase 4: Confirmation and Clusters** - Confirm/reject with Union-Find cluster management and relationship creation
- [x] **Phase 5: REST API** - Suggestion review, cluster browsing, and per-entity pending count endpoints (completed 2026-03-18)

## Phase Details

### Phase 1: Infrastructure
**Goal**: The schema foundation, generic queue, and domain enums exist and are correct — no matching code can be written wrong because the database enforces the constraints
**Depends on**: Nothing (first phase)
**Requirements**: INFRA-01, INFRA-02, INFRA-03, INFRA-04, INFRA-05, INFRA-06
**Success Criteria** (what must be TRUE):
  1. `execution_queue` table has a `job_type` discriminator column and existing workflow dispatch tests pass without modification
  2. A duplicate PENDING job for the same entity is silently skipped at the database level
  3. `pg_trgm` GIN index exists on `entity_attributes` using `(value->>'value')` and a live similarity query returns the expected row
  4. `match_suggestions` and `identity_clusters` tables exist with workspace-scoped indexes and the UNIQUE constraint on `(workspace_id, source_entity_id, target_entity_id)` enforced
  5. Canonical UUID ordering is enforced by a DB CHECK constraint — inserting a pair with `source > target` is rejected
**Plans**: 3 plans

Plans:
- [x] 01-00-PLAN.md — Wave 0 test scaffolds for queue genericization, identity infrastructure, and SourceType enum
- [x] 01-01-PLAN.md — Genericize execution queue with job_type discriminator, dedup index, and SourceType enum addition
- [x] 01-02-PLAN.md — Identity domain SQL tables, pg_trgm GIN index, JPA entities, domain models, and constraints

### Phase 2: Matching Pipeline
**Goal**: Given two entities and their IDENTIFIER-classified attributes, the system can find candidates, score them, and persist a match suggestion — testable end-to-end by calling the Temporal workflow directly
**Depends on**: Phase 1
**Requirements**: MATCH-02, MATCH-03, MATCH-04, MATCH-05, MATCH-06, SUGG-01, SUGG-02, SUGG-03, SUGG-04, SUGG-05
**Success Criteria** (what must be TRUE):
  1. A scoring function called with two sets of IDENTIFIER attributes returns a composite confidence score and per-signal breakdown matching the configured weights (EMAIL=0.9, PHONE=0.85, NAME=0.5, COMPANY=0.3, CUSTOM_IDENTIFIER=0.7)
  2. A two-phase pg_trgm candidate query returns only entities from the same workspace, different entity, not soft-deleted — entities from other workspaces never appear
  3. Running the Temporal matching workflow for an entity that has no candidates above 0.5 produces no suggestion
  4. Running the workflow for a matching pair creates a PENDING suggestion with signal breakdown stored as JSONB
  5. Submitting the same candidate pair twice creates exactly one suggestion (idempotent — duplicate silently skipped)
  6. Rejecting a suggestion stores the signal snapshot; when a stronger signal appears later the suggestion is re-created
**Plans**: 4 plans

Plans:
- [ ] 02-01-PLAN.md — Domain models, enums, signals type fix, repository queries, and test factory
- [ ] 02-02-PLAN.md — Candidate search (pg_trgm) and scoring services with unit tests
- [ ] 02-03-PLAN.md — Suggestion persistence service with idempotency, re-suggestion, and activity logging
- [ ] 02-04-PLAN.md — Temporal workflow/activities, worker registration, and pipeline integration test

### Phase 3: Trigger and Dispatch
**Goal**: Entity saves automatically trigger async identity matching without blocking the save transaction
**Depends on**: Phase 2
**Requirements**: MATCH-01
**Success Criteria** (what must be TRUE):
  1. Saving or updating an entity with IDENTIFIER-classified attributes enqueues a PENDING identity match job after the transaction commits — not during
  2. If a PENDING job already exists for that entity, no duplicate job is enqueued
  3. The queue dispatcher picks up IDENTITY_MATCH jobs and starts a Temporal workflow without interfering with WORKFLOW_EXECUTION job processing
**Plans**: 2 plans

Plans:
- [ ] 03-01-PLAN.md — Event class, IDENTIFIER classification cache, queue enqueue service, trigger listener
- [ ] 03-02-PLAN.md — Identity match dispatcher/processor, EntityService event publishing, @ConditionalOnProperty removal

### Phase 4: Confirmation and Clusters
**Goal**: A workspace member can confirm or reject a match suggestion, and confirmed matches form transitive identity clusters linked by entity relationships
**Depends on**: Phase 2
**Requirements**: CONF-01, CONF-02, CONF-03, CONF-04, CONF-05
**Success Criteria** (what must be TRUE):
  1. Confirming a suggestion creates a CONNECTED_ENTITIES relationship between the two entities with source=IDENTITY_MATCH
  2. Confirming a suggestion assigns both entities to an identity cluster — covering all four cases: neither clustered, one clustered, both in different clusters (merge), both in same cluster (no-op)
  3. When two clusters merge, all members of the smaller cluster move to the larger cluster and the empty cluster is soft-deleted
  4. Confirming an already-confirmed suggestion or rejecting an already-rejected suggestion throws ConflictException
  5. All state transitions (create, confirm, reject) are logged as activity entries
**Plans**: 2 plans

Plans:
- [ ] 04-01-PLAN.md — Repository queries, test factory extensions, and cluster-aware re-suggestion guard
- [ ] 04-02-PLAN.md — IdentityConfirmationService with confirm/reject flows, 5-case cluster management, and full test coverage

### Phase 5: REST API
**Goal**: Workspace members can review match suggestions, browse identity clusters, check pending match counts, manually add entities to clusters, and rename clusters via REST endpoints
**Depends on**: Phase 4
**Requirements**: API-01, API-02, API-03, API-04, API-05, API-06, API-07, API-08
**Success Criteria** (what must be TRUE):
  1. `GET /api/v1/identity/{workspaceId}/suggestions` returns suggestions for a workspace — a workspace member sees only their workspace's suggestions
  2. `GET /api/v1/identity/{workspaceId}/suggestions/{id}` returns a suggestion with full per-signal breakdown in the response
  3. `POST /suggestions/{id}/confirm` and `POST /suggestions/{id}/reject` transition suggestion state and return the updated suggestion
  4. `GET /api/v1/identity/{workspaceId}/clusters/{id}` returns the cluster with all member entity IDs listed
  5. `GET /api/v1/identity/{workspaceId}/entities/{id}/matches` returns the count of PENDING suggestions for that entity
**Plans**: 3 plans

Plans:
- [x] 05-01-PLAN.md — Response DTOs, repository query extensions, and IdentityReadService for all read operations
- [x] 05-02-PLAN.md — IdentityClusterService (manual add + rename), IdentityController wiring all 9 endpoints
- [ ] 05-03-PLAN.md — Gap closure: mark API-08 (notification stub) as complete (already implemented in Phase 4)

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Infrastructure | 3/3 | Complete   | 2026-03-15 |
| 2. Matching Pipeline | 2/4 | In Progress|  |
| 3. Trigger and Dispatch | 1/2 | In Progress|  |
| 4. Confirmation and Clusters | 1/2 | In Progress|  |
| 5. REST API | 3/3 | Complete   | 2026-03-18 |
