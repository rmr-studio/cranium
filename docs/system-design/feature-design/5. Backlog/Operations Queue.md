---
tags:
  - priority/high
  - status/draft
  - architecture/design
  - architecture/feature
Created: 2026-03-23
Updated: 2026-03-23
Domains:
  - "[[Lifecycle Analytics]]"
  - "[[Action Primitives]]"
---
# Quick Design: Operations Queue

## What & Why

The [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Operations Dashboard|Morning Brief]] is currently a read-only dashboard. Operators see metrics but act elsewhere. The Operations Queue transforms it into a prioritized action inbox — items to clear, decisions to make, actions to take. This is the foundational daily-usage concept: queue fills overnight from live data, user clears items each morning, queue refills tomorrow. The inbox-zero mechanic is the daily habit engine that justifies $300/mo.

Queue items come from five source types:
- [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back|Threshold alerts]] firing
- Segment membership changes (customers entering/leaving saved segments)
- Churn events detected from billing data
- Monitoring rules firing (user-defined pattern watchers)
- Future: AI-surfaced anomalies (Phase 2 — `ai_insight` source type reserved in schema)

Each item carries: severity (`HIGH` / `MEDIUM` / `INFO`), context summary (what happened, why it matters, $ at risk), inline actions (view, push to tool, investigate, flag, dismiss), and resolution tracking.

---

## Data Changes

**New Entities:**

| Entity | Purpose | Key Fields |
|---|---|---|
| `queue_items` | Prioritized action queue | `id`, `workspace_id`, `source_type`, `source_reference_id`, `severity`, `title`, `context_summary`, `entity_references` (JSONB array), `available_actions` (JSONB array), `resolution_status`, `resolution_action_id`, `deferred_until`, `created_at`, `resolved_at`, `expires_at` |

**Field details:**

- `source_type` — enum: `alert` \| `segment_change` \| `churn_event` \| `monitoring_rule` \| `ai_insight`
  - `ai_insight` reserved for Phase 2 — not populated in Phase 1
- `source_reference_id` — FK to the originating alert, segment, churn event, or rule
- `severity` — enum: `HIGH` \| `MEDIUM` \| `INFO`
- `entity_references` — JSONB array of `{ entityTypeKey, entityInstanceId }` linking to affected entities
- `available_actions` — JSONB array of action descriptors (view, push, investigate, flag, dismiss) determined by source type
- `resolution_status` — enum: `PENDING` \| `ACTED_ON` \| `DISMISSED` \| `DEFERRED` \| `EXPIRED`
- `resolution_action_id` — nullable FK to [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back|Ops Journal]] action record when resolved via inline action
- `deferred_until` — nullable timestamp; when set, item re-queues after this time and resets the 14-day expiry clock
- `expires_at` — defaults to `created_at + 14 days`; auto-transition to `EXPIRED` when reached

**Deduplication:**
- Unique constraint on `workspace_id` + `source_type` + `source_reference_id` + `entity_reference_hash`
- If a customer enters at-risk twice in 24 hours, existing item is updated (timestamp, context refreshed) rather than duplicated

---

## Components Affected

- [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Operations Dashboard]] — Morning Brief becomes the queue's primary rendering surface; queue items replace or augment static metric cards
- [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back]] — alert firing now creates queue items; segment exports and tags serve as inline action resolutions
- [[riven/docs/system-design/feature-design/4. Completed/Notifications]] — queue item creation may trigger in-app / email notification depending on severity
- **Nightly batch job** — new scheduled job evaluates all queue sources (alerts, segment deltas, churn events, monitoring rules) and inserts/updates queue items
- **Queue API** — new endpoints for fetching, resolving, deferring, and dismissing items

---

## API Changes

**New endpoints:**

- `GET /api/workspaces/{id}/queue` — fetch pending queue items, sorted by severity then recency
  - Query params: `severity`, `source_type`, `status` (default: `PENDING`)
  - Returns items with hydrated entity references and available actions
- `PATCH /api/workspaces/{id}/queue/{itemId}/resolve` — resolve an item
  - Body: `{ resolution: "ACTED_ON" | "DISMISSED", action_id?: string }`
- `PATCH /api/workspaces/{id}/queue/{itemId}/defer` — defer an item
  - Body: `{ until: "tomorrow" | "3_days" | "1_week" }`
  - Sets `deferred_until`, resets `expires_at` to `deferred_until + 14 days`, sets status to `DEFERRED`
- `GET /api/workspaces/{id}/queue/summary` — lightweight count by severity for badge/header display
  - Returns: `{ high: number, medium: number, info: number, total: number }`

---

## Failure Handling

- **Batch job failure** — if the nightly evaluation job fails partway, items already written persist; job is idempotent via deduplication constraint so safe to retry
- **Source deletion** — if an alert definition or segment is deleted, orphaned queue items remain visible but actions linking back to the source gracefully show "source removed"
- **Deferred item re-queue** — a scheduled check (runs with nightly batch or more frequently in Phase 2) picks up items where `deferred_until < now()` and transitions them back to `PENDING`
- **Expiry** — nightly batch marks items where `expires_at < now()` as `EXPIRED`; expired items excluded from default query but retained for history

---

## Gotchas & Edge Cases

- **Empty queue state** — "All clear — check back tomorrow." Prompt to create first monitoring rule or save first segment. This is a feature for onboarding, not an edge case to hide.
- **Queue flood** — a misconfigured alert or volatile segment could generate dozens of items overnight. Consider a per-workspace cap (e.g., 100 pending items) with overflow summarized as "and N more..."
- **Phase 1 is nightly-only** — events occurring after the batch window appear the following morning. Users expecting real-time will need clear messaging. Intra-day refresh is Phase 2.
- **Entity reference staleness** — entity may be deleted or merged between queue creation and user action. Inline actions must validate entity existence before executing.
- **Deferred items accumulating** — users deferring items indefinitely creates noise. The 14-day expiry clock reset on defer prevents infinite deferral but still allows 14 more days per defer.
- **Cross-item dependencies** — multiple queue items may reference the same entity (e.g., churn event + alert trigger for same customer). Resolving one should not auto-resolve the other — they represent different signals.

---

## Tasks

- [ ] Design `queue_items` table schema and migration
- [ ] Implement nightly batch job: alert evaluation -> queue item creation
- [ ] Implement nightly batch job: segment membership delta detection -> queue item creation
- [ ] Implement nightly batch job: churn event detection -> queue item creation
- [ ] Implement deduplication logic (upsert on unique constraint)
- [ ] Implement expiry and deferred re-queue in nightly batch
- [ ] Build queue API endpoints (list, resolve, defer, summary)
- [ ] Build queue UI on Morning Brief surface — item cards with severity, context, inline actions
- [ ] Build empty state UI with onboarding prompt
- [ ] Wire inline actions to existing action primitives (view entity, push to integration, tag, flag)
- [ ] Link resolution to Ops Journal (create action record on `ACTED_ON`)
- [ ] Add queue item count badge to navigation

---

## Notes

- **This is the foundational concept** from the CEO Plan: Daily Usage — Operations Cockpit Vision (2026-03-23). All other accepted concepts (Delta-First Surfaces, Inline Actions, Monitoring Rules, Outcome Tracking, Living Segments, Ops Journal) compose around this queue.
- Monitoring Rules (Concept 5) is a separate feature design — it defines the rule engine. This doc covers the queue that receives rule output.
- The `ai_insight` source type is intentionally included in the Phase 1 schema to avoid a migration when Phase 2 ships the AI briefing pipeline.
- Queue items are intentionally NOT soft-deleted — resolved/expired items serve as operational history and feed into [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Operations Dashboard|Outcome Tracking]] analysis.

---

## Related Documents

- [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Operations Dashboard]] — Morning Brief surface that hosts the queue
- [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back]] — alert definitions and inline action targets
- [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Analytics Views]] — analytics surfaces that drive segment/alert creation feeding the queue
- [[riven/docs/system-design/feature-design/4. Completed/Notifications]] — delivery channel for high-severity queue items
- [[riven/docs/system-design/feature-design/4. Completed/Three-Tier Entity Model and Lifecycle Spine]] — entity types referenced by queue items
- CEO Plan: Daily Usage — Operations Cockpit Vision (2026-03-23)
