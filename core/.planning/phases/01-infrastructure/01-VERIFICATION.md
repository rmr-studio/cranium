---
phase: 01-infrastructure
verified: 2026-03-16T12:00:00Z
status: human_needed
score: 9/10 must-haves verified
human_verification:
  - test: "Connect to a PostgreSQL instance that has had the full schema applied and run: SELECT indexname, indexdef FROM pg_indexes WHERE indexname = 'idx_entity_attributes_trgm';"
    expected: "One row is returned showing a GIN index using gin_trgm_ops on entity_attributes with expression (value->>'value')"
    why_human: "The integration test (IdentityInfrastructureIntegrationTest) explicitly skips verifying this index at runtime because Hibernate ddl-auto:create-drop does not execute our schema SQL files. The SQL definition in entity_indexes.sql is correct, but the runtime-applied index on a real database has not been verified by an automated test."
---

# Phase 1: Infrastructure Verification Report

**Phase Goal:** The schema foundation, generic queue, and domain enums exist and are correct — no matching code can be written wrong because the database enforces the constraints.
**Verified:** 2026-03-16T12:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 1 | Existing workflow dispatch tests pass without modification after queue table rename and column additions | VERIFIED | ExecutionQueueGenericizationTest has 3 passing tests; `workflow_execution_queue` renamed to `execution_queue` in workflow.sql and all JPA/repository layers. No references to old table name remain in main source. |
| 2 | A duplicate PENDING identity match job for the same entity is silently skipped at the database level | VERIFIED | `uq_execution_queue_pending_identity_match` partial unique index on `execution_queue (workspace_id, entity_id, job_type) WHERE status = 'PENDING' AND entity_id IS NOT NULL` present in workflow_indexes.sql |
| 3 | SourceType.IDENTITY_MATCH exists as a valid enum value | VERIFIED | `SourceType.kt` contains `IDENTITY_MATCH`; `SourceTypeTest` passes confirming valueOf() and entries lookup |
| 4 | Workflow dispatcher only claims WORKFLOW_EXECUTION jobs, never identity match jobs | VERIFIED | Both native queries in `ExecutionQueueRepository` contain `AND job_type = 'WORKFLOW_EXECUTION'` (claimPendingExecutions and findStaleClaimedItems) |
| 5 | pg_trgm extension is enabled | VERIFIED | `extensions.sql` contains `create extension if not exists "pg_trgm"`. Integration test verifies extension installed at runtime via pg_extension catalog query. |
| 6 | GIN index exists on entity_attributes using (value->>'value') expression | PARTIAL | `entity_indexes.sql` has correct DDL: `USING GIN ((value->>'value') gin_trgm_ops) WHERE deleted = false`. Runtime verification against a real DB has NOT been automated — integration test comment explicitly declines to verify the index ("verified by the presence of the SQL file"). Needs human verification. |
| 7 | match_suggestions, identity_clusters, and identity_cluster_members tables exist with workspace-scoped indexes | VERIFIED | `identity.sql` contains all three tables with correct FKs, defaults, and soft-delete columns. `identity_indexes.sql` contains all workspace-scoped indexes. |
| 8 | Canonical UUID ordering is enforced by a CHECK constraint — inserting source > target is rejected | VERIFIED | `identity_constraints.sql` has `CHECK (source_entity_id < target_entity_id)`. Integration test `CHECK constraint rejects suggestion where source entity id is greater than target` passes. |
| 9 | Unique constraint on (workspace_id, source_entity_id, target_entity_id) prevents duplicate suggestion pairs | VERIFIED | `uq_match_suggestions_pair` partial unique index in `identity_constraints.sql`. Integration test `duplicate active suggestion for same entity pair is rejected` passes. |
| 10 | An entity can belong to at most one identity cluster | VERIFIED | `UNIQUE idx_identity_cluster_members_entity ON identity_cluster_members (entity_id)` in `identity_indexes.sql`. Integration test `entity can belong to at most one identity cluster` passes. |

**Score:** 9/10 truths verified (1 needs human validation for runtime index existence)

### Required Artifacts

#### Plan 01-01 Artifacts (INFRA-01, INFRA-02, INFRA-03)

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `db/schema/01_tables/workflow.sql` | execution_queue table with job_type, entity_id, nullable workflow_definition_id | VERIFIED | Table renamed from workflow_execution_queue; contains job_type VARCHAR(30) NOT NULL DEFAULT 'WORKFLOW_EXECUTION', entity_id UUID nullable FK, workflow_definition_id nullable |
| `db/schema/02_indexes/workflow_indexes.sql` | Dedup partial unique index uq_execution_queue_pending_identity_match | VERIFIED | Index present: `ON execution_queue (workspace_id, entity_id, job_type) WHERE status = 'PENDING' AND entity_id IS NOT NULL` |
| `src/main/kotlin/riven/core/enums/workflow/ExecutionJobType.kt` | Job type discriminator enum | VERIFIED | Enum with WORKFLOW_EXECUTION and IDENTITY_MATCH values, correct package |
| `src/main/kotlin/riven/core/enums/integration/SourceType.kt` | IDENTITY_MATCH source type value | VERIFIED | Contains IDENTITY_MATCH after WORKFLOW |
| `src/main/kotlin/riven/core/entity/workflow/ExecutionQueueEntity.kt` | Updated JPA entity with jobType, entityId, nullable workflowDefinitionId | VERIFIED | @Table("execution_queue"), jobType field with @Enumerated(STRING), entityId UUID?, workflowDefinitionId UUID? (nullable = true) |
| `src/main/kotlin/riven/core/repository/workflow/ExecutionQueueRepository.kt` | Native queries referencing execution_queue with job_type filter | VERIFIED | Both claimPendingExecutions and findStaleClaimedItems query `FROM execution_queue` with `AND job_type = 'WORKFLOW_EXECUTION'` |
| `src/main/kotlin/riven/core/models/workflow/engine/queue/ExecutionQueueRequest.kt` | Model with nullable workflowDefinitionId, jobType, entityId fields | VERIFIED | workflowDefinitionId: UUID? (nullable), jobType: ExecutionJobType, entityId: UUID? |

#### Plan 01-02 Artifacts (INFRA-04, INFRA-05, INFRA-06)

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `db/schema/00_extensions/extensions.sql` | pg_trgm extension enabled | VERIFIED | `create extension if not exists "pg_trgm"` on line 3 |
| `db/schema/01_tables/identity.sql` | match_suggestions, identity_clusters, identity_cluster_members | VERIFIED | All three tables with correct FKs, defaults, soft-delete where applicable |
| `db/schema/02_indexes/entity_indexes.sql` | pg_trgm GIN index on entity_attributes | VERIFIED (schema only) | `USING GIN ((value->>'value') gin_trgm_ops) WHERE deleted = false` — correct expression, not (value::text) |
| `db/schema/02_indexes/identity_indexes.sql` | Workspace-scoped indexes for identity tables | VERIFIED | idx_match_suggestions_workspace, source/target entity indexes, cluster workspace index, one-cluster-per-entity unique index |
| `db/schema/04_constraints/identity_constraints.sql` | CHECK constraint and unique pair index | VERIFIED | chk_match_suggestions_canonical_order CHECK (source_entity_id < target_entity_id) and uq_match_suggestions_pair partial unique index |
| `src/main/kotlin/riven/core/entity/identity/MatchSuggestionEntity.kt` | JPA entity for match_suggestions | VERIFIED | Extends AuditableSoftDeletableEntity, @Table("match_suggestions"), all fields with correct types, JSONB signals as Map<String,Any?>, toModel() using requireNotNull |
| `src/main/kotlin/riven/core/entity/identity/IdentityClusterEntity.kt` | JPA entity for identity_clusters | VERIFIED | Extends AuditableSoftDeletableEntity, @Table("identity_clusters"), workspaceId, name?, memberCount, toModel() |
| `src/main/kotlin/riven/core/entity/identity/IdentityClusterMemberEntity.kt` | JPA entity for identity_cluster_members (system join table) | VERIFIED | Does NOT extend AuditableSoftDeletableEntity, @Table("identity_cluster_members"), clusterId, entityId, joinedAt, joinedBy?, toModel() |
| `src/main/kotlin/riven/core/models/identity/MatchSuggestion.kt` | Domain model for match suggestions | VERIFIED | Data class with id, workspaceId, sourceEntityId, targetEntityId, status, confidenceScore, signals, rejectionSignals?, resolvedBy?, resolvedAt?, createdAt, updatedAt |
| `src/main/kotlin/riven/core/models/identity/IdentityCluster.kt` | Domain model for identity clusters | VERIFIED | Data class with id, workspaceId, name?, memberCount, createdAt, updatedAt |
| `src/main/kotlin/riven/core/models/identity/IdentityClusterMember.kt` | Domain model for identity cluster members | VERIFIED | Data class with id, clusterId, entityId, joinedAt, joinedBy? |
| `src/main/kotlin/riven/core/enums/identity/MatchSuggestionStatus.kt` | Suggestion state machine enum | VERIFIED | PENDING, CONFIRMED, REJECTED, EXPIRED with @JsonProperty annotations |

#### Plan 01-00 Test Scaffolds

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/test/kotlin/riven/core/service/workflow/ExecutionQueueGenericizationTest.kt` | 3 regression tests for queue genericization | VERIFIED | 3 enabled, passing tests using mockito-kotlin and ExecutionQueueFactory |
| `src/test/kotlin/riven/core/service/identity/IdentityInfrastructureIntegrationTest.kt` | 5 DB constraint integration tests | VERIFIED | 5 enabled tests against real PostgreSQL via Testcontainers; @EntityScan scoped to riven.core.entity.identity |
| `src/test/kotlin/riven/core/enums/integration/SourceTypeTest.kt` | 1 unit test for SourceType.IDENTITY_MATCH | VERIFIED | 1 enabled, passing test |
| `src/test/kotlin/riven/core/service/util/factory/workflow/ExecutionQueueFactory.kt` | Test factory for ExecutionQueueEntity | VERIFIED (referenced) | Used in ExecutionQueueGenericizationTest via createWorkflowExecutionJob() and createIdentityMatchJob() |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `db/schema/01_tables/workflow.sql` | `ExecutionQueueEntity.kt` | Table name and column mapping | VERIFIED | @Table("execution_queue") matches DDL; all columns (job_type, entity_id, workflow_definition_id) present and nullable status correct |
| `ExecutionQueueRepository.kt` | `db/schema/01_tables/workflow.sql` | Native SQL queries referencing table name | VERIFIED | Both native queries use `FROM execution_queue` |
| `ExecutionQueueEntity.kt` | `ExecutionQueueRequest.kt` | toModel() mapping | VERIFIED | toModel() passes jobType, entityId, workflowDefinitionId (nullable) — requireNotNull(id) used for ID |
| `db/schema/01_tables/identity.sql` | `MatchSuggestionEntity.kt` | Table-to-entity column mapping | VERIFIED | @Table("match_suggestions"), all columns mapped with correct types and nullability |
| `db/schema/04_constraints/identity_constraints.sql` | `db/schema/01_tables/identity.sql` | ALTER TABLE adds CHECK constraint | VERIFIED | ALTER TABLE match_suggestions ADD CONSTRAINT chk_match_suggestions_canonical_order CHECK (source_entity_id < target_entity_id) |
| `MatchSuggestionEntity.kt` | `MatchSuggestionStatus.kt` | Status enum field mapping | VERIFIED | @Enumerated(EnumType.STRING) val status: MatchSuggestionStatus |
| `MatchSuggestionEntity.kt` | `MatchSuggestion.kt` | toModel() mapping | VERIFIED | All fields mapped; requireNotNull for id, createdAt, updatedAt |
| `IdentityClusterEntity.kt` | `IdentityCluster.kt` | toModel() mapping | VERIFIED | All fields mapped correctly |
| `IdentityClusterMemberEntity.kt` | `IdentityClusterMember.kt` | toModel() mapping | VERIFIED | All fields mapped; requireNotNull for id |

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| INFRA-01 | 01-00, 01-01 | Generic execution queue with job_type discriminator | SATISFIED | execution_queue table, ExecutionJobType enum, ExecutionQueueEntity updated, dispatcher isolation via SQL filter |
| INFRA-02 | 01-00, 01-01 | Queue deduplication — skip enqueue if PENDING job exists for same entity | SATISFIED | uq_execution_queue_pending_identity_match partial unique index; integration test scaffold covers this behavior |
| INFRA-03 | 01-00, 01-01 | IDENTITY_MATCH added to SourceType enum | SATISFIED | SourceType.IDENTITY_MATCH present; SourceTypeTest passes |
| INFRA-04 | 01-00, 01-02 | pg_trgm extension enabled with partial GIN index on entity_attributes using (value->>'value') | PARTIAL | Extension verified at runtime (integration test). GIN index DDL correct in entity_indexes.sql but runtime existence on real DB needs human check. |
| INFRA-05 | 01-00, 01-02 | DB schema for identity tables with workspace-scoped indexes | SATISFIED | All three tables, all workspace-scoped indexes, one-cluster-per-entity unique index — all verified by integration tests |
| INFRA-06 | 01-00, 01-02 | Canonical UUID ordering enforced (source < target) with CHECK constraint and unique constraint | SATISFIED | CHECK constraint and uq_match_suggestions_pair present in identity_constraints.sql; both verified by passing integration tests |

All 6 requirement IDs declared across plans are accounted for. No orphaned requirements found — REQUIREMENTS.md lists only INFRA-01 through INFRA-06 for Phase 1, all claimed by these plans.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `IdentityInfrastructureIntegrationTest.kt` | 184-195 | Factory helpers build entities inline using constructor directly rather than dedicated factory class | Info | Low — these are local private methods within the test class itself, effectively serving as inline factories. The ExecutionQueueFactory follows the project pattern; identity tests could benefit from a dedicated IdentityFactory but this is not a blocker. |

No stub implementations, empty handlers, placeholder returns, TODO/FIXME blockers, or orphaned artifacts found.

### Human Verification Required

#### 1. GIN Index idx_entity_attributes_trgm Applied to Real Database

**Test:** After applying the full schema to a PostgreSQL database, run:
```sql
SELECT indexname, indexdef
FROM pg_indexes
WHERE indexname = 'idx_entity_attributes_trgm';
```
**Expected:** One row returned with indexdef containing `USING gin ((value ->> 'value'::text) gin_trgm_ops)` and the partial condition `WHERE (deleted = false)`.

**Why human:** The integration test for INFRA-04 only verifies that the `pg_trgm` extension is installed. The test explicitly notes that the GIN index on `entity_attributes` is "verified by the presence of the SQL file" rather than at runtime, because Hibernate `ddl-auto: create-drop` only manages JPA entity-mapped tables and does not execute our SQL schema files. The entity_attributes table is not an identity-domain entity, so it is not scoped into the `@EntityScan("riven.core.entity.identity")` context used in the integration test.

### Gaps Summary

No blocking gaps. All schema files are correct and substantive. All JPA entities follow project conventions (constructor injection, data classes, UUID PKs, requireNotNull for ID assertions, no `!!` usage). All toModel() methods are wired correctly. Both native queries in ExecutionQueueRepository filter by job_type. MatchSuggestionStatus, ExecutionJobType, and SourceType enums are all present and correct.

The single human verification item (GIN index runtime existence) is a test coverage gap, not an implementation gap — the DDL in `entity_indexes.sql` is correct and will create the right index when applied to a real database.

---

_Verified: 2026-03-16T12:00:00Z_
_Verifier: Claude (gsd-verifier)_
