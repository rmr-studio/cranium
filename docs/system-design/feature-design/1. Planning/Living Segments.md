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
# Quick Design: Living Segments

## What & Why

Every saved segment in Riven currently evaluates live on access and returns a count — but that count has no memory. The operator sees "47 at-risk customers" with no idea whether that was 42 last week or 55. Without delta tracking, segments are static filters, not operational units. Living Segments extends the existing [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back|segment infrastructure]] so that ALL saved segments automatically gain membership delta tracking, health indicators, and [[Operations Queue]] integration. This transforms segments from "a way to filter entities" into "a portfolio of populations I actively manage" — a core driver of the daily-usage thesis.

---

## Data Changes

**New Entities:**

| Entity | Purpose | Key Fields |
|---|---|---|
| `SegmentSnapshot` | Nightly membership state capture | `id`, `segment_id`, `snapshot_date`, `member_count`, `member_entity_ids` (JSONB array), `members_entered` (JSONB array), `members_exited` (JSONB array) |

**New/Modified Fields on existing entities:**

- `SegmentDefinition` — add `health_direction` enum (`GROWTH_IS_GOOD` / `GROWTH_IS_BAD` / `NEUTRAL`) — determines how growth/shrinkage is interpreted in the health indicator. Default: `NEUTRAL`. Set by the user when saving or editing a segment.
- `SegmentDefinition` — add `track_deltas` boolean, default `true` — all saved segments opt in by default

**Snapshot strategy:**
- Nightly batch during the existing data sync window
- Evaluate the segment query, capture the full member set
- Diff against previous snapshot to compute `members_entered` and `members_exited`
- Retain snapshots for rolling 90 days (configurable per workspace later), then compact to daily `member_count` only (drop `member_entity_ids` to save storage)

**Storage consideration:** For segments with <50k members (launch ceiling), storing `member_entity_ids` as a JSONB array is acceptable. If entity counts scale beyond this, move to a junction table (`segment_snapshot_members`) with `(snapshot_id, entity_instance_id)` pairs.

---

## Components Affected

- [[Data Sync Pipeline]] — add snapshot step after sync completes; snapshot job evaluates all saved segments and writes `SegmentSnapshot` rows
- [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back|SegmentDefinition]] — schema changes for `health_direction` and `track_deltas`
- [[Operations Queue]] — segment membership changes generate queue items with `source_type = segment_change`; item includes delta summary and link to segment view
- [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Analytics Views]] — segment cards and list views display delta indicators (`47 (+5, -2)`) and health badge (growing/shrinking + good/bad colour coding)
- [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Operations Dashboard]] — Morning Brief segment summary cards pull from latest snapshot diff
- [[Outcome Tracking]] — segment-level outcome tracking: snapshot entity states at action time, observe state changes within the observation window at the segment level
- Segment detail view — new "Changes" tab showing `members_entered` / `members_exited` as entity lists with timestamps

---

## API Changes

**New endpoints:**

- `GET /segments/{id}/snapshots` — paginated snapshot history (date, count, entered, exited)
- `GET /segments/{id}/snapshots/latest` — current snapshot with full diff detail
- `GET /segments/{id}/delta?period=7d` — computed delta summary for a given lookback period (default 7 days)

**Modified endpoints:**

- `GET /segments` — response now includes `latest_delta` object: `{ member_count, entered_count, exited_count, period, health_direction, health_status }` per segment
- `GET /segments/{id}` — response includes `health_direction` field and latest snapshot summary

---

## Failure Handling

- **Snapshot job fails mid-run:** Snapshot writes are per-segment within a transaction. A failure on one segment does not block others. Failed segments logged, retried on next nightly run. Missing snapshots produce no delta (UI shows "—" instead of a number).
- **Segment query times out during snapshot:** Apply the same query timeout as live segment evaluation. If a segment consistently times out, mark it as `snapshot_failed` and surface a warning on the segment card. Do not block the sync pipeline.
- **Queue item deduplication:** If a segment has an unresolved queue item from a previous snapshot, update the existing item's delta values rather than creating a duplicate. Dedup key: `segment_id + source_type`.
- **Data sync delayed or skipped:** Snapshots depend on fresh data. If the sync window is missed, skip snapshot generation for that night — stale snapshots are worse than no snapshot.

---

## Gotchas & Edge Cases

- **New segments have no history:** First snapshot has no previous state to diff against. Display "Tracking started — deltas available tomorrow" on the segment card.
- **Segment query changed by user:** If the user edits a segment's filters, the next snapshot will show a potentially large membership swing that is filter-change-driven, not lifecycle-driven. Consider flagging the first post-edit snapshot with a `filter_changed` marker so the UI can annotate the delta ("Segment definition changed").
- **Deleted entities:** Entities that are deleted between snapshots appear in `members_exited` but may no longer be resolvable. Handle gracefully — show entity ID with "deleted" badge.
- **Health direction ambiguity:** Not all segments have an obvious good/bad direction. Default to `NEUTRAL` so users are not confused by misleading health badges. Let users set direction explicitly.
- **Target states (Phase 2):** The data model should NOT include target state fields now. Target states ("get below 30 by Q2") are deferred — they add goal-tracking complexity that is not needed for the daily-usage thesis.
- **Snapshot storage growth:** At 100 segments x 90 days x ~50KB per snapshot = ~450MB per workspace. Acceptable at launch scale. The 90-day compaction policy (drop member IDs, keep counts) keeps long-term storage bounded.

---

## Tasks

- [ ] Add `health_direction` and `track_deltas` columns to `segment_definitions` table
- [ ] Create `segment_snapshots` table with migration
- [ ] Implement nightly snapshot job — runs after data sync, evaluates all `track_deltas=true` segments, writes snapshots with diffs
- [ ] Implement snapshot diff logic — compare current member set against previous snapshot, compute `members_entered` / `members_exited`
- [ ] Add snapshot compaction job — drop `member_entity_ids` from snapshots older than 90 days, retain `member_count`
- [ ] Build `GET /segments/{id}/snapshots`, `GET /segments/{id}/snapshots/latest`, `GET /segments/{id}/delta` endpoints
- [ ] Extend `GET /segments` and `GET /segments/{id}` responses with delta summary and health fields
- [ ] Integrate with [[Operations Queue]] — generate `segment_change` queue items when membership delta exceeds a threshold (e.g., any non-zero change, or configurable per-segment)
- [ ] Update segment list view UI — render delta badges (`+5`, `-2`) and health indicator (colour-coded growing/shrinking)
- [ ] Update segment detail view — add "Changes" tab with entered/exited entity lists
- [ ] Add `health_direction` selector to segment create/edit form
- [ ] Handle edge cases: new segments with no history, filter-change annotations, deleted entities in diffs

---

## Notes

- This is NOT a separate feature from segments — it extends [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back|existing segment infrastructure]]. No new domain boundary. The `SegmentSnapshot` entity lives in the same domain as `SegmentDefinition`.
- Snapshot timing must be coordinated with the data sync pipeline. Snapshots taken before sync completes will produce stale diffs. The snapshot job should be a post-sync hook, not a separate cron.
- Queue item generation threshold is an open question. Options: (a) any non-zero delta generates a queue item, (b) user-configurable threshold per segment, (c) only "interesting" deltas (>5% change). Start with (a) for simplicity — users can dismiss noisy items, and we learn from usage patterns.
- Segment-level [[Outcome Tracking]] means: when an action targets a segment (e.g., push to Klaviyo), the outcome tracker snapshots the segment's membership and states, then observes changes within the observation window at the segment level — not just per-entity.
- [[Delta-First Surfaces]] presentation (e.g., `47 (+5 this week, -2 resolved)`) is a cross-cutting concern. This design provides the data; the rendering is owned by the frontend component library.
