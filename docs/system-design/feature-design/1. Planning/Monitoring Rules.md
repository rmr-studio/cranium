---
tags:
  - priority/high
  - status/draft
  - architecture/design
  - architecture/feature
Created: 2026-03-23
Updated: 2026-03-23
Domains:
  - "[[Action Primitives]]"
  - "[[Lifecycle Analytics]]"
---
# Quick Design: Monitoring Rules

## What & Why

Monitoring Rules extend the existing [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back|threshold alert]] infrastructure from single-metric checks to cross-domain pattern matching. Phase 1 is **template-based** — a fixed set of pre-built rule templates that users parameterize (thresholds, segments, channels, time windows). NOT a freeform query builder (Phase 2+). Users assemble 5-10 rules to build a personalized morning routine. Rules evaluate nightly during the data sync window and fire items into the [[Operations Queue]]. This creates lock-in through customization — the more rules a user configures, the higher the switching cost.

---

## Data Changes

**New Entities:**

| Entity | Purpose | Key Fields |
|---|---|---|
| `monitoring_rules` | Rule instance created from a template | `id`, `workspace_id`, `template_id`, `name`, `parameters` (JSONB), `severity` (HIGH/MEDIUM/INFO), `enabled`, `created_by`, `created_at`, `updated_at`, `last_evaluated_at`, `last_fired_at`, `fire_count` |
| `monitoring_rule_templates` | Pre-built rule definitions shipped with the product | `id`, `slug`, `name`, `description`, `category`, `parameter_schema` (JSONB), `evaluation_query` (JSONB), `default_severity`, `created_at` |
| `monitoring_rule_events` | History of rule evaluations and firings | `id`, `rule_id`, `evaluated_at`, `fired` (boolean), `metric_value`, `threshold_value`, `queue_item_id` (nullable FK to ops queue) |

**Key Field Notes:**

- `parameters` — JSONB storing user-supplied values that slot into the template. Schema validated against `parameter_schema` on the template. Example: `{ "segment_id": "uuid", "growth_threshold": 10, "period_days": 7 }`
- `template_id` — FK to `monitoring_rule_templates`. Every Phase 1 rule is template-backed
- `fire_count` — denormalized counter for quick display ("this rule has fired 14 times")
- `last_evaluated_at` / `last_fired_at` — timestamps for rule health visibility

**Relationship to Existing Models:**

- Fired rules create items in the [[Operations Queue]] with `source_type = 'monitoring_rule'` and a reference back to `monitoring_rules.id`
- Templates may reference [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back|segments]] via `segment_id` parameter — reuses existing `SegmentDefinition` and `EntityQueryService`
- Extends but does NOT replace existing `AlertDefinition` / `AlertEvent` from threshold alerts — monitoring rules are a higher-level abstraction that can express threshold alerts plus cross-domain patterns

---

## Phase 1 Rule Templates

Shipped as seed data. Users parameterize, not author.

| Template | Category | Parameters | Evaluation Logic |
|---|---|---|---|
| Churn rate exceeds threshold | Churn | `threshold_pct` | Compute workspace churn rate, compare against threshold |
| Segment grows by N in period | Segment | `segment_id`, `growth_threshold`, `period_days` | Snapshot segment count, compare delta over period |
| Segment shrinks by N in period | Segment | `segment_id`, `shrink_threshold`, `period_days` | Inverse of above |
| Channel customers generate support tickets within N days | Cross-domain | `channel_id`, `ticket_window_days` | Join acquisition channel → customer → support tickets, filter by recency |
| Support ticket velocity increase | Velocity | `channel_id` (optional), `multiplier`, `period` | Compare current period ticket count to previous period, check if ratio exceeds multiplier |
| New churns from specific channel | Churn × Channel | `channel_id`, `min_count` | Count new churn events attributed to channel in last eval window |
| At-risk count exceeds threshold | Health | `threshold_count` | Count entities with at-risk status |

**Template parameter schema** uses JSON Schema format — enables frontend form generation from the template definition without hardcoding per-template UI.

---

## Components Affected

- [[Operations Queue]] — receives fired rule items as `source_type = 'monitoring_rule'`. Queue item context includes: rule name, metric value vs threshold, affected entity count, and deep link to relevant view
- [[Lifecycle Analytics]] — "Create monitoring rule" action available from analytics views (pre-fills template parameters from current view context)
- [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back|Segments]] — segment-based templates reference existing `SegmentDefinition`. Segment membership snapshots needed for delta computation (see [[Living Segments]])
- **Nightly Sync Job** — rule evaluation hooks into the existing scheduled job infrastructure (ShedLock). Runs after data sync completes so rules evaluate against fresh data
- **Notifications Domain** — rule firing creates ops queue item; no separate notification channel in Phase 1 (the queue IS the notification surface)

---

## API Changes

**New Endpoints:**

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/workspaces/:id/monitoring-rule-templates` | List available templates with parameter schemas |
| `GET` | `/api/v1/workspaces/:id/monitoring-rules` | List workspace rules (with last_evaluated, last_fired, fire_count) |
| `POST` | `/api/v1/workspaces/:id/monitoring-rules` | Create rule from template (template_id + parameters + severity) |
| `PATCH` | `/api/v1/workspaces/:id/monitoring-rules/:ruleId` | Update parameters, severity, or enabled state |
| `DELETE` | `/api/v1/workspaces/:id/monitoring-rules/:ruleId` | Delete rule |
| `GET` | `/api/v1/workspaces/:id/monitoring-rules/:ruleId/events` | Rule evaluation/firing history |

**Request body (create):**
```json
{
  "templateId": "segment-growth",
  "name": "At-Risk growing too fast",
  "parameters": {
    "segment_id": "uuid",
    "growth_threshold": 10,
    "period_days": 7
  },
  "severity": "HIGH"
}
```

**Validation:** `parameters` validated against the template's `parameter_schema` on create/update. 400 if invalid.

---

## Failure Handling

- **Rule evaluation failure** — log error, set `last_evaluated_at` to now, do NOT fire. Retry on next nightly cycle. After 3 consecutive failures, auto-disable the rule and create an INFO-level ops queue item: "Rule [name] disabled due to evaluation errors"
- **Stale segment reference** — if a rule references a deleted segment, evaluation fails gracefully. Rule auto-disabled with queue notification
- **Nightly job timeout** — rules evaluate sequentially per workspace with a per-rule timeout (30s). If a rule times out, skip it, log, and continue to the next. Total job timeout at the workspace level prevents runaway evaluation
- **Parameter drift** — if a template schema changes (app update), existing rules with incompatible parameters are flagged but not auto-deleted. Migration path: mark affected rules as `needs_update` and surface in the rules management UI

---

## Gotchas & Edge Cases

- **Duplicate firing** — rules must be idempotent per evaluation window. If the nightly job runs twice (crash recovery), the second run should detect that `last_evaluated_at` is already within the current window and skip
- **Rule explosion** — cap at 25 rules per workspace in Phase 1. Prevents nightly eval from becoming expensive. Soft limit, surfaced in UI
- **Segment count snapshots** — segment-growth templates need historical membership counts to compute deltas. This depends on [[Living Segments]] shipping segment membership snapshots. Without snapshots, segment-based templates fall back to "current count exceeds N" (threshold-style)
- **Template versioning** — templates are seed data shipped with the app. If a template's evaluation logic changes, existing rules using the old version need consideration. Use `template_version` field on `monitoring_rules` to track which version the rule was created against
- **Ordering and priority** — when multiple rules fire in one cycle, ops queue items should be ordered by severity, then by fire recency (newly firing rules surface above repeat-firing ones)
- **First evaluation** — a newly created rule has no baseline. First evaluation should establish the baseline metric value without firing, unless the condition is already met (in which case, fire immediately)

---

## Tasks

- [ ] Design and create `monitoring_rule_templates` and `monitoring_rules` tables (migration)
- [ ] Create `monitoring_rule_events` table for evaluation history
- [ ] Implement template registry — seed data for Phase 1 templates with parameter schemas
- [ ] Build `MonitoringRuleEvaluationService` — template-dispatched evaluation with per-template query logic
- [ ] Integrate evaluation into nightly sync job (post-data-sync hook, ShedLock)
- [ ] Wire fired rules to [[Operations Queue]] item creation (`source_type = 'monitoring_rule'`)
- [ ] CRUD API endpoints for monitoring rules
- [ ] Template listing endpoint with parameter schemas
- [ ] Rule event history endpoint
- [ ] Frontend: rules management view (list, create from template, edit parameters, enable/disable, delete)
- [ ] Frontend: template picker with dynamic parameter form (generated from JSON Schema)
- [ ] Frontend: "Create rule" action from analytics views (pre-fill parameters from context)
- [ ] Frontend: rule firing history view (per-rule event log)
- [ ] Validation: parameter schema enforcement on create/update
- [ ] Auto-disable logic for consecutive evaluation failures

---

## Notes

- Phase 2 expands to a freeform query builder where users compose rules without templates. The template-based architecture should keep the evaluation engine template-agnostic — templates define the query, the engine just runs it. This makes the query-builder transition a frontend + template-authoring concern, not an engine rewrite
- Consider shipping with 5-7 templates at launch and adding more based on usage patterns. Template additions are zero-downtime (insert seed data, no schema change)
- The "5-10 rules = personalized morning routine" positioning means the rules management UI should feel like configuring a personal assistant, not administering alert infrastructure. Language matters: "What should Riven watch for?" not "Configure alert rules"
- Cross-domain templates (channel × support tickets) are the differentiator vs. existing threshold alerts. These are only possible because of Riven's [[riven/docs/system-design/feature-design/4. Completed/Three-Tier Entity Model and Lifecycle Spine|entity model]] connecting data across tool boundaries
- Template `evaluation_query` stores a structured query representation, not raw SQL. This keeps evaluation portable and auditable. The `MonitoringRuleEvaluationService` translates template + parameters into entity queries via the existing [[riven/docs/system-design/domains/Entities/Querying/Querying]] pipeline where possible
