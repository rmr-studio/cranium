---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: planning
stopped_at: Completed 01-infrastructure-00-PLAN.md
last_updated: "2026-03-16T00:00:00.000Z"
last_activity: 2026-03-16 — Roadmap created
progress:
  total_phases: 5
  completed_phases: 0
  total_plans: 3
  completed_plans: 3
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

### Pending Todos

None yet.

### Blockers/Concerns

- Verify `EntitySavedEvent` exists in codebase before Phase 3, or add publishing as Phase 3 scope
- Verify ShedLock can accommodate a second scheduled lock for identity dispatcher without contention
- NotificationService stub contract not yet defined — stub locally in identity package if needed

## Session Continuity

Last session: 2026-03-15T23:08:51.870Z
Stopped at: Completed 01-infrastructure-02-PLAN.md
Resume file: None
