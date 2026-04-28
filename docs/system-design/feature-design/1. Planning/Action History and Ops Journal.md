---
tags:
  - priority/medium
  - status/draft
  - architecture/design
  - architecture/feature
Created: 2026-03-23
Updated: 2026-03-23
Domains:
  - "[[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back]]"
---
# Quick Design: Action History + Ops Journal

## What & Why

Riven currently has no memory of what the operator did yesterday. Actions taken from the [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Operations Dashboard|Operations Queue]] — pushing segments, creating monitoring rules, flagging entities — vanish the moment they complete. This means no operational audit trail, no way to answer "what did I do about that at-risk cohort last week?", and no foundation for [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Operations Dashboard|Outcome Tracking]] to display results alongside their originating actions.

The Ops Journal is a simple, append-only chronological log of all mutating operations performed in Riven. It serves two Phase 1 purposes:
- **Operational memory** — searchable history of every action taken, providing continuity between sessions
- **Outcome tracking integration** — journal entries link to `action_events` so the [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Operations Dashboard|Outcome Tracking]] system can display results next to the action that triggered them

Team visibility (multi-user activity feeds) is a natural Phase 2 extension when multi-user ships. This design does NOT accommodate it beyond including `user_id` on the record.

---

## Data Changes

**New Table: `ops_journal_entries`**

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK |
| `workspace_id` | UUID | FK → workspaces |
| `user_id` | UUID | FK → users |
| `action_type` | TEXT | Enum-like: `segment_push`, `csv_export`, `tag_applied`, `flag_applied`, `monitoring_rule_created`, `monitoring_rule_modified`, `alert_created`, `queue_item_resolved`, `queue_item_dismissed`, `queue_item_deferred` |
| `action_summary` | TEXT | Human-readable summary, e.g. "Pushed At-Risk (23) to Klaviyo 'Spring Retention'" |
| `entity_references` | UUID[] | Array of entity instance IDs involved in the action |
| `source_queue_item_id` | UUID (nullable) | FK → operations queue item that triggered this action, if any |
| `metadata` | JSONB | Action-specific payload — integration target, segment name, rule config, threshold values, entity count, etc. |
| `created_at` | TIMESTAMPTZ | Immutable — append-only store |

**Indexes:**
- `(workspace_id, created_at DESC)` — primary query path (chronological feed)
- `(workspace_id, action_type, created_at DESC)` — filtered by action type
- `(source_queue_item_id)` — join path for outcome tracking lookups

**No updates, no deletes.** This is an append-only event store. Entries are never modified after creation.

---

## Components Affected

- [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Operations Dashboard]] — Ops Journal feed rendered as a timeline view, likely a tab or sidebar panel within the operations cockpit
- [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back]] — every action primitive (tag, segment push, CSV export, alert creation) emits a journal entry on completion
- [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Operations Dashboard|Operations Queue]] — resolving, dismissing, or deferring a queue item emits a journal entry with `source_queue_item_id` linked
- [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Operations Dashboard|Monitoring Rules]] — rule creation and modification emit journal entries
- [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Operations Dashboard|Outcome Tracking]] — reads journal entries to display action context alongside tracked outcomes; joins `ops_journal_entries` ↔ `action_events` via shared entity references or direct FK

---

## API Changes

**New endpoints:**

`GET /api/v1/workspaces/{workspaceId}/journal`
- Returns paginated, reverse-chronological journal entries
- Query params: `actionType` (filter), `before` / `after` (cursor pagination), `limit`
- Response: array of journal entry DTOs with resolved entity names (not just IDs)

`GET /api/v1/workspaces/{workspaceId}/journal/{entryId}`
- Single entry detail — includes full metadata payload and linked outcome tracking results (if any)

**No write endpoint.** Journal entries are created server-side as a side effect of action execution. There is no user-facing create/update/delete API. This prevents tampering and keeps the journal as a system-of-record audit trail.

**Internal service interface:**

```
OpsJournalService.record(
  workspaceId, userId, actionType, actionSummary,
  entityReferences[], sourceQueueItemId?, metadata?
)
```

Called from within existing action handlers — not a separate HTTP call. Fire-and-forget with async persistence (action completion should never block on journal write).

---

## Failure Handling

- **Journal write fails:** Action still succeeds. Journal recording is best-effort — a failed journal write must never block or roll back the user's action. Log the failure for operational alerting, retry via dead-letter queue.
- **Entity references stale:** Entities may be deleted after journal entry creation. Journal entries store IDs; the read path gracefully handles missing entities (show "deleted entity" placeholder, not a 500).
- **Metadata schema drift:** `metadata` is JSONB with no enforced schema. Frontend must handle unknown/missing keys gracefully. Version the metadata shape via an internal `_version` key if needed.

---

## Gotchas & Edge Cases

- **What gets logged vs. what doesn't:** Only mutating operations — actions that change state in Riven or push data externally. Passive reads (viewing entity records, opening segments, browsing analytics) are NOT logged. This boundary must be clearly documented for developers adding new features.
- **Bulk actions:** A single bulk operation (e.g., tagging 50 entities) produces ONE journal entry with all 50 entity references, not 50 individual entries. The `action_summary` reflects the bulk nature ("Tagged 50 customers as 'At Risk'").
- **`entity_references` size:** For very large segment pushes (1000+ entities), storing all IDs in an array is wasteful. Cap `entity_references` at a reasonable limit (e.g., 100) and store the full count in `metadata.entity_count`. The journal entry summary still reads "Pushed 1,247 to Klaviyo" — the array is for linkage, not exhaustive enumeration.
- **Summary generation:** `action_summary` is generated server-side at write time, not computed on read. This means summaries remain accurate even if the referenced segment/rule is later renamed or deleted.
- **Timezone:** All `created_at` values stored as UTC. Frontend formats to user's local timezone.
- **Retention policy:** Not needed for Phase 1 (append-only, small volume). Revisit if journal grows beyond 100K entries per workspace — unlikely in Year 1.

---

## Tasks

- [ ] Create `ops_journal_entries` migration with indexes
- [ ] Implement `OpsJournalService` with async `record()` method
- [ ] Integrate journal recording into segment push / CSV export handlers
- [ ] Integrate journal recording into tag/flag application handlers
- [ ] Integrate journal recording into monitoring rule create/modify handlers
- [ ] Integrate journal recording into queue item resolution (acted/dismissed/deferred)
- [ ] Build `GET /journal` paginated endpoint with action type filtering
- [ ] Build journal timeline UI component (reverse-chronological feed)
- [ ] Wire journal entries to outcome tracking read path (display linked outcomes inline)
- [ ] Add dead-letter retry for failed journal writes

---

## Notes

- The journal is deliberately simple — no categories, no folders, no editing. Append-only by design. Complexity lives in the surfaces that read from it (outcome tracking, future AI summaries).
- Phase 2 extensions noted in the CEO plan: AI-generated summaries across journal entries, pattern detection ("you push at-risk segments every Tuesday — want to automate that?"). These require no schema changes — they read from the same append-only store.
- The `action_summary` field is the journal's most important UX element. Summaries should read like a human ops log, not like system events. "Pushed At-Risk (23) to Klaviyo 'Spring Retention'" — not "SEGMENT_EXPORT action completed for segment_id=abc123".

---

## Related Documents

- [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Operations Dashboard]] — primary consumer surface for the journal
- [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back]] — action primitives that emit journal entries
- [[riven/docs/system-design/feature-design/4. Completed/Three-Tier Entity Model and Lifecycle Spine]] — entity model referenced by journal entries
- CEO Plan: Daily Usage — Operations Cockpit Vision (2026-03-23)
