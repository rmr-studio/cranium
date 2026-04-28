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
---
# Quick Design: Delta-First Surfaces

## What & Why

Delta-First Surfaces is a cross-cutting design principle, not a single feature. Every numeric value rendered across Riven surfaces shows **change over time**, not just current state. Static numbers don't change daily — deltas do. This is the difference between a dashboard the operator checks weekly and an operational cockpit they open every morning. Without deltas, the [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Operations Dashboard]] and [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Analytics Views]] look identical day-to-day. With deltas, every visit reveals what moved, what worsened, and what improved — making the Morning Brief worth checking before email.

---

## Data Changes

**New/Modified Entities:**

- **Segment Membership Snapshot** — new storage: captures the full membership set of each saved segment at snapshot time, enabling entry/exit delta computation
  - `segment_id`, `snapshot_date`, `entity_ids[]` (or a diff-based representation)
  - Snapshotted nightly during the data sync window
- **User Visit Timestamp** — new per-user, per-surface record tracking last visit time
  - `user_id`, `surface_key`, `last_visited_at`
  - Used to compute "new since your last visit" highlights on entity records

**New/Modified Fields:**

- Most metric deltas require **no new storage** — derivable from existing historical data by comparing current values against prior-period values (yesterday, last week, last month)
- Existing metric computation paths gain a `delta` output alongside the absolute value

---

## Components Affected

- [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Analytics Views]] — every metric cell gains delta annotation (e.g., `142 (+8 this week)`)
- [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Operations Dashboard]] — Morning Brief cards show change since last check (`47 at-risk (+5 since yesterday, -2 resolved)`)
- [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back]] — segment views surface membership deltas (who entered, who exited) not just current members
- [[riven/docs/system-design/feature-design/1. Planning/Dashboard Metrics Pre-computation Layer]] — pre-computation jobs must emit both absolute values and period-over-period deltas
- Entity record views — highlight NEW events since the user's last visit (requires per-user visit tracking)
- Segment detail views — membership change list showing entries/exits with timestamps

---

## API Changes

**Delta-enriched metric responses:**
- All metric endpoints gain optional `include_delta=true` query param (default true on dashboard surfaces)
- Response shape adds `delta` object alongside `value`:
  ```json
  {
    "value": 47,
    "delta": {
      "period": "1d",
      "change": 5,
      "resolved": 2,
      "direction": "up"
    }
  }
  ```

**New endpoints:**
- `GET /segments/:id/membership-changes?since=<date>` — returns entities that entered/exited the segment since the given date
- `POST /user/visit` — records a surface visit timestamp; `GET /user/visit/:surface_key` — retrieves last visit time
- `GET /entities/:id/events?since_last_visit=true` — returns events on an entity record that occurred after the user's last visit

---

## Failure Handling

- **Missing historical data** — if prior-period data doesn't exist (new workspace, first sync), deltas render as "—" with tooltip "Not enough history yet." No delta is better than a misleading delta.
- **Snapshot job failure** — if a nightly segment membership snapshot fails, the previous snapshot is preserved. Deltas show a stale comparison window with a "Last updated: [date]" indicator. Alert the ops team internally; never silently show wrong deltas.
- **Clock skew on visit timestamps** — use server-side timestamps exclusively for last-visit tracking, never client clocks.

---

## Gotchas & Edge Cases

- **Delta display formatting** — positive deltas on "bad" metrics (churn rate, at-risk count) should render red/negative sentiment; positive deltas on "good" metrics (revenue, retention) render green/positive. Direction semantics are metric-specific, not universal.
- **Segment membership snapshots at scale** — storing full membership sets nightly for large segments (10k+ members) needs a diff-based or bitset approach, not naive `entity_id[]` arrays. Evaluate storage cost early.
- **"Resolved" vs "exited"** — segment exit deltas should distinguish between entities that improved (left at-risk because they recovered) vs entities that churned (left at-risk because they're gone). Both are exits; the meaning is opposite.
- **First-time user experience** — new users have no visit history, so "new since last visit" would highlight everything. Default to showing the last 24 hours of events on first visit, then switch to visit-based tracking.
- **Delta period selection** — different surfaces benefit from different comparison windows. Morning Brief: since yesterday. Channel Performance: week-over-week. Cohort views: month-over-month. The period should be surface-appropriate with an option to toggle.
- **Batch timing dependency** — deltas are only as fresh as the last nightly sync. If sync runs at 2am and the operator checks at 9am, deltas reflect 2am state. Document this clearly in the UI.

---

## Tasks

- [ ] Design delta annotation component — reusable UI element showing `value (change direction)` with semantic coloring
- [ ] Implement metric delta computation in existing aggregation queries (compare current vs prior period)
- [ ] Build segment membership snapshot job — runs nightly post-sync, stores membership state per saved segment
- [ ] Build per-user visit timestamp tracking (write on surface load, read for "new since last visit")
- [ ] Add `membership-changes` endpoint for segment entry/exit deltas
- [ ] Update [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Analytics Views]] to render deltas on all metric cells
- [ ] Update Morning Brief cards to show delta-annotated values
- [ ] Add "new since last visit" event highlighting on entity record views
- [ ] Define metric-specific delta semantics (which direction is "good" vs "bad" per metric type)
- [ ] Evaluate segment snapshot storage strategy for large segments (diff-based vs full snapshot)

---

## Related Documents

- [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Analytics Views]] — primary consumer of delta annotations on metric tables
- [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Operations Dashboard]] — Morning Brief delta-enriched cards
- [[riven/docs/system-design/feature-design/1. Planning/Dashboard Metrics Pre-computation Layer]] — pre-computation must include delta values
- [[riven/docs/system-design/feature-design/5. Backlog/Action Primitives - Tags Segments Alerts Write-back]] — segment membership changes feed delta surfaces
- CEO Plan: Daily Usage — Operations Cockpit Vision (2026-03-23)

---

## Changelog

| Date | Author | Change |
| ---- | ------ | ------------- |
| 2026-03-23 | | Initial design from CEO plan — Concept 2: Delta-First Surfaces |
