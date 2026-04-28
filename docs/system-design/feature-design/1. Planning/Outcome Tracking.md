---
tags:
  - priority/medium
  - status/draft
  - architecture/design
  - architecture/feature
Created: 2026-03-23
Updated: 2026-03-23
Domains:
  - "[[Action Primitives]]"
  - "[[Lifecycle Analytics]]"
---
# Quick Design: Outcome Tracking

## What & Why

Every action taken in Riven (segment push, tag application, alert resolution) currently disappears into a void — the operator never learns whether it worked. Outcome Tracking closes the feedback loop by snapshotting entity state at action time, then evaluating whether the situation improved within a configurable observation window. This is **correlation, not causal attribution** — "did the situation improve?" not "did my action cause it." The result: a return-visit mechanic ("did my action work?" brings you back tomorrow) and ROI evidence ("Riven helped manage $12K in at-risk ARR this month").

---

## Data Changes

**New Entities:**

| Entity | Purpose | Key Fields |
|---|---|---|
| `action_events` | Tracks every action with its targeted entities and outcome | `id`, `action_id`, `action_type` (SEGMENT_PUSH / TAG / ALERT_RESOLUTION / FLAG), `workspace_id`, `entity_ids` (UUID[]), `snapshot_fields` (JSONB), `observation_window_days` (int, default 30), `outcome_status` (PENDING / EVALUATED), `outcome_evaluated_at` (timestamp), `outcome_summary` (JSONB), `created_at` |
| `outcome_definitions` | Per-entity-type definition of qualifying re-engagement events | `id`, `workspace_id`, `entity_type_key`, `qualifying_events` (JSONB — e.g., purchase, login, status change from at-risk to active), `created_at` |

**New Fields:**

- `workspace_settings.default_observation_window_days` — workspace-level default (default: 30)

**`snapshot_fields` JSONB structure:**
- Stores only fields relevant to the outcome condition at action time, not full entity state
- Keyed by entity ID → field snapshot: `{ "entity-uuid-1": { "status": "at-risk", "arr": 1200 }, ... }`

**`outcome_summary` JSONB structure:**
- Aggregated results after evaluation: `{ "total": 23, "re_engaged": 4, "churned": 2, "unchanged": 17, "save_rate": 0.17, "arr_at_risk": 12000 }`

---

## Components Affected

- [[Action Primitives]] — every action primitive (tag, segment push, alert resolution) emits an `action_event` on execution
- [[Operations Queue]] — resolved queue items with outcomes surface as INFO items ("your action from Mar 15 had results")
- [[Ops Journal]] — action history entries link to outcome status; journal view shows outcome badge (pending / evaluated with summary)
- [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Analytics Views]] — aggregate outcome metrics feed into dashboard surfaces (save rate trends, ARR managed)
- [[riven/docs/system-design/feature-design/4. Completed/Notifications]] — optional notification when an outcome evaluation completes with notable results
- **Nightly Evaluation Job** — new scheduled job using existing [[riven/docs/system-design/infrastructure/Shedlock]] infrastructure

---

## API Changes

**New endpoints:**

- `GET /api/workspaces/{workspaceId}/outcomes` — list action events with outcome status, filterable by action_type, outcome_status, date range
- `GET /api/workspaces/{workspaceId}/outcomes/{actionEventId}` — detail view: per-entity breakdown of state changes
- `GET /api/workspaces/{workspaceId}/outcomes/summary` — aggregate outcome stats (total actions, average save rate, ARR managed) for dashboard surfaces
- `PUT /api/workspaces/{workspaceId}/settings/observation-window` — update default observation window
- `POST /api/workspaces/{workspaceId}/outcome-definitions` — create/update qualifying event definitions per entity type
- `GET /api/workspaces/{workspaceId}/outcome-definitions` — list outcome definitions

---

## Failure Handling

- **Snapshot capture failure:** If state snapshot fails at action time, log the error and still record the action event with `snapshot_fields = null` — the action is not blocked, but outcome tracking degrades gracefully to "no outcome data available"
- **Nightly job failure:** Shedlock prevents duplicate runs. Failed evaluations are retried on the next nightly cycle. Actions past their observation window that were never evaluated get marked `outcome_status = EXPIRED`
- **Entity deletion during observation window:** If an entity is deleted before evaluation, treat as "churned" in the outcome summary — the entity left the system
- **Missing outcome definition:** If no `outcome_definition` exists for an entity type, skip outcome evaluation for those entities and surface a prompt to configure qualifying events

---

## Gotchas & Edge Cases

- **Observation window overlap:** Multiple actions targeting the same entity within overlapping windows — each action gets its own independent evaluation. A re-engagement event counts for ALL open actions targeting that entity. This is intentional (correlation model, not attribution)
- **Bulk actions with large entity sets:** A segment push targeting 500+ entities creates a single `action_event` with a large `entity_ids` array and `snapshot_fields` JSONB. Consider chunking snapshot capture and evaluation for sets >1000
- **Qualifying event ambiguity:** "Re-engaged" must be clearly defined per entity type. Default definitions should ship for common lifecycle entity types (customer: purchase or status change from at-risk to active). Workspace operators can customize
- **Clock skew on nightly job:** Evaluation checks `created_at + observation_window_days <= now()`. Use UTC consistently. Actions created late in the day still get full window
- **Outcome summary is point-in-time:** Once evaluated, the outcome is frozen. Post-evaluation entity changes do not retroactively update the summary
- **First-run experience:** No outcomes exist until the user takes their first action AND the observation window elapses. Surface this in the UI — "Outcomes will appear after your first action's observation window closes"

---

## Tasks

- [ ] Design and migrate `action_events` table
- [ ] Design and migrate `outcome_definitions` table
- [ ] Add `default_observation_window_days` to workspace settings
- [ ] Implement snapshot capture — hook into [[Action Primitives]] execution path to emit `action_event` with state snapshot
- [ ] Build nightly outcome evaluation job (Shedlock-based)
  - [ ] Query open `action_events` where `created_at + observation_window_days <= now()` and `outcome_status = PENDING`
  - [ ] For each, check entity states against `outcome_definitions` qualifying events
  - [ ] Compute and persist `outcome_summary`
- [ ] Ship default `outcome_definitions` for standard lifecycle entity types
- [ ] Build outcome definition management UI (settings page)
- [ ] Build outcomes list API + detail API
- [ ] Build outcomes summary API for dashboard integration
- [ ] Wire outcome badges into [[Ops Journal]] action history entries
- [ ] Surface completed outcomes in [[Operations Queue]] as INFO items
- [ ] Build outcome detail view — per-entity breakdown showing before/after state

---

## Notes

- The observation window is workspace-level for Phase 1. Per-action override (e.g., "check this one in 7 days") is a natural Phase 2 extension
- Phase 2: predictive outcome modeling — use historical outcome data to estimate likely save rates before the window closes
- ROI evidence surfaces ("Riven helped manage $12K in at-risk ARR this month") are derived from `outcome_summary.arr_at_risk` aggregated across all evaluated actions in a time range — this is a read-side computation, not stored separately
- The correlation model is a deliberate product decision — causal attribution requires control groups and is out of scope. Frame outcomes as "what happened after your action" not "what your action caused"
- Related CEO Plan: [[2026-03-23-daily-usage-ops-cockpit]] — Concept 6
