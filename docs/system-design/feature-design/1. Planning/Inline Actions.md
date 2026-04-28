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
# Quick Design: Inline Actions

## What & Why

Every analytical surface in Riven currently ends at a dead end — the operator sees a problem, then leaves to act on it in another tool. Inline Actions embed contextual action buttons directly into insight surfaces so the operator can act at the moment of understanding. This transforms Riven from "a place I look at data" into "a place I manage my customer lifecycle." Completed inline actions resolve items in the [[Operations Queue]], closing the daily operational loop. The feature extends the existing [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back|Action Primitives]] (tags, segments, alerts, write-back) by surfacing them in-context rather than requiring navigation to separate action destinations.

---

## Data Changes

**New Entities:**

| Entity | Purpose | Key Fields |
|---|---|---|
| `InlineActionDefinition` | Registered action types available per surface | `id`, `actionKey` (enum), `label`, `surfaceType`, `targetEntityType`, `requiredParams` (JSONB), `enabled` |
| `InlineActionExecution` | Execution log for every inline action taken | `id`, `workspaceId`, `actionDefinitionId`, `executedBy`, `executedAt`, `targetEntityIds` (JSONB), `params` (JSONB), `status` (PENDING/SUCCESS/FAILED/PARTIAL), `queueItemId` (nullable FK to `OperationsQueueItem`), `error` (nullable) |

**Modified Entities:**

- `OperationsQueueItem` — add `resolvedByActionId` (nullable FK to `InlineActionExecution`) to link queue resolution to the action that resolved it
- `SegmentExportEvent` — add `inlineActionExecutionId` (nullable FK) to trace exports triggered via inline actions vs. standalone exports

**Phase 1 Action Keys:**

| Key | Label | Target | Delegates To |
|---|---|---|---|
| `PUSH_TO_KLAVIYO` | Push to Klaviyo | Segment / entity list | [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back\|Segment Export and Write-back]] via Nango |
| `EXPORT_CSV` | Export CSV | Segment / entity list | Existing CSV export pipeline |
| `FLAG_ALL` | Flag all | Entity list | [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back\|Tags and Flags]] — bulk tag application |
| `TAG_SOURCE` | Tag source as… | Entity instance | Tags primitive — single entity tagging with predefined labels |
| `CREATE_MONITORING_RULE` | Create monitoring rule | Segment / metric | [[Monitoring Rules]] — pre-fills rule template from context |
| `VIEW_AFFECTED_CUSTOMERS` | View affected customers | Segment / query | Navigation action — opens scoped entity data table |
| `PUSH_SEGMENT` | Push segment | Segment | Segment Export — creates or updates segment then pushes |
| `SET_ALERT` | Set alert | Segment / metric | [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back\|Threshold Alerts]] — pre-fills from context |

---

## Components Affected

- [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Analytics Views]] — action bar rendered below each analytics card; actions contextual to the view (e.g., channel performance surfaces `PUSH_TO_KLAVIYO`, `FLAG_ALL`)
- [[riven/docs/system-design/feature-design/5. Backlog/Churn Retrospective Timeline]] — retrospective insights render `TAG_SOURCE`, `CREATE_MONITORING_RULE` actions
- [[riven/docs/system-design/feature-design/5. Backlog/Knowledge Layer Sub-Agents]] / AI Knowledge Base panel — surfaces `VIEW_AFFECTED_CUSTOMERS`, `PUSH_SEGMENT`, `SET_ALERT` beneath AI-generated insights
- [[Operations Queue]] — queue items embed inline action buttons; completing an action marks the queue item resolved
- [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Operations Dashboard]] — Morning Brief cards surface contextual actions matching their insight type
- [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back]] — all four primitives become delegates; inline actions call through to existing tag, segment, alert, and write-back services
- **Frontend: `InlineActionBar` component** — reusable component that accepts a surface context and renders available actions as button group
- **Frontend: `ActionExecutionModal`** — confirmation/parameter modal before executing destructive or external actions (Klaviyo push, bulk flag)

---

## API Changes

**New endpoints:**

```
GET  /api/v1/workspaces/{id}/inline-actions?surfaceType={type}&entityType={key}
  → Returns available actions for a given surface context

POST /api/v1/workspaces/{id}/inline-actions/execute
  Body: { actionKey, targetEntityIds?, segmentId?, params, queueItemId? }
  → Executes the action, logs to InlineActionExecution, optionally resolves queue item
  → Returns: { executionId, status, resultSummary }

GET  /api/v1/workspaces/{id}/inline-actions/history?limit=50&offset=0
  → Paginated execution history (feeds into [[Action History + Ops Journal]])
```

**Modified endpoints:**

- `PATCH /api/v1/workspaces/{id}/operations-queue/{itemId}/resolve` — accept optional `resolvedByActionId` to link resolution to an inline action execution

---

## Failure Handling

- **Integration write failures (Klaviyo, HubSpot):** execution status set to `FAILED` or `PARTIAL`; user sees error toast with retry button; partial failures log per-entity results in `InlineActionExecution.error` JSONB
- **Bulk action timeouts:** actions targeting >500 entities execute async via existing job queue; UI shows progress indicator; execution record updates on completion
- **Queue item already resolved:** if another user resolves the queue item before action completes, action still executes but `queueItemId` link is skipped — action logged standalone
- **Unavailable integrations:** `PUSH_TO_KLAVIYO` disabled (greyed out with tooltip) when Klaviyo integration is not connected or Nango write is unhealthy
- **Stale context:** if underlying data changed between surface render and action execution (e.g., segment membership shifted), action executes against current data — confirmation modal shows live count, not cached count

---

## Gotchas & Edge Cases

- **Action availability varies by surface:** not every action makes sense on every view — `InlineActionDefinition.surfaceType` gates which actions render where; avoid action sprawl by limiting Phase 1 to the 8 defined action keys above
- **Bulk vs. single entity targeting:** some actions operate on segments (Push to Klaviyo), others on individual entities (Tag source) — the `InlineActionBar` component must distinguish and adapt its UI
- **Duplicate executions:** user double-clicks "Push to Klaviyo" — debounce at the UI level + idempotency check on the backend (same actionKey + targetEntityIds + <60s = reject)
- **Write-back scope:** inline actions delegate to existing write-back infrastructure — if Nango doesn't support writes for a given integration, the action falls back to CSV export with clear messaging
- **Phase 2 boundary:** multi-step playbooks (e.g., "tag + push + set alert" as a single composed action) are explicitly Phase 2 — Phase 1 actions are atomic, one-click primitives
- **Operations Queue coupling:** not all inline actions originate from queue items — actions triggered from analytics views or entity tables are standalone; only queue-embedded actions resolve queue items
- **Permission model:** all inline actions inherit workspace-level permissions — no per-action RBAC in Phase 1

---

## Tasks

- [ ] Define `InlineActionDefinition` and `InlineActionExecution` schema + migrations
- [ ] Add `resolvedByActionId` FK to `OperationsQueueItem`
- [ ] Implement `InlineActionService` — action registry, execution dispatcher, delegation to existing primitives
- [ ] Implement execution endpoints (`GET` available actions, `POST` execute, `GET` history)
- [ ] Build `InlineActionBar` frontend component — accepts surface context, fetches available actions, renders button group
- [ ] Build `ActionExecutionModal` — confirmation step with live entity count, parameter inputs where needed
- [ ] Wire `InlineActionBar` into [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Analytics Views]] cards
- [ ] Wire `InlineActionBar` into [[riven/docs/system-design/feature-design/5. Backlog/Churn Retrospective Timeline]] insight panels
- [ ] Wire `InlineActionBar` into [[Operations Queue]] item cards with queue resolution on completion
- [ ] Wire `InlineActionBar` into AI Knowledge Base panel
- [ ] Integrate execution history with [[Action History + Ops Journal]]
- [ ] Add idempotency guard for duplicate execution prevention
- [ ] Add async execution path for bulk actions (>500 entities) via job queue

---

## Notes

- Inline Actions are a **presentation and orchestration layer** — they do not introduce new action capabilities. Every action delegates to an existing primitive from [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back]] or [[Monitoring Rules]]. The value is contextual placement, not new functionality.
- The `InlineActionBar` component is the primary reuse surface — designed once, embedded everywhere. New surfaces automatically get actions by declaring their `surfaceType`.
- [[Outcome Tracking]] hooks into `InlineActionExecution` records — every execution is a trackable action with entity-level state snapshots for measuring results.
- Phase 2 adds **multi-step playbooks** — composed sequences of atomic actions (tag → push → set alert) triggered as a single user intent. The `InlineActionExecution` schema supports this via a future `playbookId` FK without schema changes.
- See [[CEO Plan - Daily Usage Ops Cockpit|CEO Plan: Daily Usage — Operations Cockpit Vision]] Concept 3 for strategic framing.
