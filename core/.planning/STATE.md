---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: planning
stopped_at: Completed 05-rest-api/05-01-PLAN.md
last_updated: "2026-03-18T08:39:02.999Z"
last_activity: 2026-03-16 — Roadmap created
progress:
  total_phases: 5
  completed_phases: 4
  total_plans: 13
  completed_plans: 12
  percent: 20
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-16)

**Core value:** When a user looks at any entity in their workspace, they can see every related entity from every connected tool — turning siloed integration data into a unified identity graph.
**Current focus:** Phase 1 — Infrastructure

## Current Position

Phase: 1 of 5 (Infrastructure)
Plan: 0 of TBD in current phase
Status: Ready to plan
Last activity: 2026-03-16 — Roadmap created

Progress: [██░░░░░░░░] 20%

## Performance Metrics

**Velocity:**
- Total plans completed: 0
- Average duration: —
- Total execution time: —

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**
- Last 5 plans: —
- Trend: —

*Updated after each plan completion*
| Phase 01-infrastructure P01 | 9 | 2 tasks | 11 files |
| Phase 01-infrastructure P02 | 35min | 2 tasks | 16 files |
| Phase 02-matching-pipeline P01 | 3min | 2 tasks | 10 files |
| Phase 02-matching-pipeline P03 | 12min | 1 tasks | 2 files |
| Phase 02-matching-pipeline P02 | 10min | 2 tasks | 4 files |
| Phase 02-matching-pipeline P04 | 13min | 2 tasks | 7 files |
| Phase 03-trigger-and-dispatch P01 | 4min | 2 tasks | 8 files |
| Phase 03-trigger-and-dispatch P02 | 7min | 2 tasks | 8 files |
| Phase 04-confirmation-and-clusters P01 | 12min | 2 tasks | 5 files |
| Phase 04-confirmation-and-clusters P02 | 8min | 1 tasks | 5 files |
| Phase 05-rest-api P01 | 4min | 2 tasks | 9 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Locked: Generic queue with job_type discriminator (INFRA-01) is the #1 prerequisite — must ship before any matching code
- Locked: GIN index must use `(value->>'value')` not `(value::text)` — wrong expression is silent failure
- Locked: Clusters form at confirmation only, never speculatively
- Locked: Signals stored as JSONB on match_suggestions, no separate table
- Locked: Canonical UUID ordering enforced via DB CHECK (source < target)
- [Phase 01-infrastructure]: workflow_definition_id is nullable on execution_queue; callers that need it non-null must check at call site (not in toModel())
- [Phase 01-infrastructure]: Dispatcher isolation enforced at SQL layer with AND job_type = 'WORKFLOW_EXECUTION' in both native queries
- [Phase 01-infrastructure]: Dedup partial unique index (workspace_id, entity_id, job_type) WHERE status='PENDING' prevents race-condition duplicate identity match jobs
- [Phase 01-infrastructure]: JSONB signals use Map<String, Any?> not kotlinx.serialization.json.JsonObject — Jackson cannot deserialize non-null kotlinx JsonObject from DB (MissingKotlinParameterException)
- [Phase 01-infrastructure]: Integration tests install pg_trgm + DB constraints in @BeforeAll — Hibernate ddl-auto:create-drop does not execute SQL schema files
- [Phase 01-infrastructure plan 00]: @EntityScan must be scoped to riven.core.entity.identity not riven.core.entity — broad scan causes uuid_generate_v4() DDL failures in test container
- [Phase 02-matching-pipeline]: signals is List<Map<String, Any?>> not Map — JSONB array allows multi-signal breakdown per suggestion; rejectionSignals stays flat JsonObject (snapshot at rejection time)
- [Phase 02-matching-pipeline]: MatchSignal.toMap() uses type.name (String) for JSONB serialization — avoids deserialization coupling when reading JSONB back from DB
- [Phase 02-matching-pipeline]: fromSchemaType() maps EMAIL/PHONE directly; all others fall to CUSTOM_IDENTIFIER — NAME/COMPANY are contextual derivations handled by scoring service heuristics
- [Phase 02-matching-pipeline]: ActivityService.logActivity requires non-null userId — activity logging skipped when userId=null for Temporal system calls
- [Phase 02-matching-pipeline]: DataIntegrityViolationException catch is the idempotency mechanism for duplicate pair prevention
- [Phase 02-matching-pipeline]: rejectionSignals snapshot stores signals list and confidenceScore map — enables re-suggestion context without joins
- [Phase 02-matching-pipeline]: EntityManager used over JdbcTemplate for native queries — handles UUID parameters directly without string conversion
- [Phase 02-matching-pipeline]: MINIMUM_SCORE_THRESHOLD=0.5 exposed as companion const on IdentityMatchScoringService for Plan 02-03 access without magic numbers
- [Phase 02-matching-pipeline]: IdentityMatchWorkflow.workflowId() companion derives identity-match-{entityId} — callers never hardcode the pattern
- [Phase 02-matching-pipeline]: rejectSuggestion sets deleted=true — findRejectedSuggestion requires deleted=true AND status=REJECTED to enable re-suggestion
- [Phase 03-trigger-and-dispatch]: Cache stores Set<UUID> of IDENTIFIER attribute IDs (not Boolean) — enables both hasIdentifierAttributes() and getIdentifierAttributeIds() without a second query
- [Phase 03-trigger-and-dispatch]: IdentityMatchTriggerListener placed in service.entity package — mirrors WorkspaceAnalyticsListener placement in service.analytics
- [Phase 03-trigger-and-dispatch]: On create, always enqueue if entity type has IDENTIFIER attributes even if newIdentifierAttributes is empty — entity type config is the signal, not current attribute values
- [Phase 03-trigger-and-dispatch]: WorkflowExecutionAlreadyStarted (not AlreadyStartedException) is the correct Temporal SDK class — io.temporal.client.WorkflowExecutionAlreadyStarted extends WorkflowException
- [Phase 03-trigger-and-dispatch]: publishIdentityMatchTriggerEvent() extracted as private method in EntityService — keeps saveEntity() readable and single-purpose
- [Phase 04-confirmation-and-clusters]: inSameCluster check placed before active/rejected checks in createOrResuggest — cluster membership is a cheaper skip condition
- [Phase 04-confirmation-and-clusters]: deleteByClusterId uses native hard-delete — identity_cluster_members is system-managed join table with no SoftDeletable, consistent with EntityAttributeRepository.hardDeleteByEntityId pattern
- [Phase 04-confirmation-and-clusters]: rejectSuggestion removed from IdentityMatchSuggestionService — IdentityConfirmationService is the single state machine owner for both confirm and reject transitions
- [Phase 04-confirmation-and-clusters]: Cluster merge tie-breaking favors source entity's cluster — source is in canonical (lower UUID) position, consistent with DB ordering constraint
- [Phase 05-rest-api]: SuggestionResponse excludes rejectionSignals — internal state not part of public API
- [Phase 05-rest-api]: countPendingForEntity uses native query with explicit deleted=false — @SQLRestriction does not apply to native queries

### Pending Todos

None yet.

### Blockers/Concerns

- Verify `EntitySavedEvent` exists in codebase before Phase 3, or add publishing as Phase 3 scope
- Verify ShedLock can accommodate a second scheduled lock for identity dispatcher without contention
- NotificationService stub contract not yet defined — stub locally in identity package if needed

## Session Continuity

Last session: 2026-03-18T08:39:02.996Z
Stopped at: Completed 05-rest-api/05-01-PLAN.md
Resume file: None
