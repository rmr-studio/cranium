---
tags:
  - "#status/draft"
  - priority/critical
  - architecture/feature
  - architecture/frontend
  - domain/entity
  - domain/integrations
  - domain/notifications
Created: 2026-03-19
Updated:
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
  - "[[riven/docs/system-design/domains/Integrations/Integrations]]"
  - "[[riven/docs/system-design/feature-design/4. Completed/Notifications]]"
blocked by:
  - "[[riven/docs/system-design/feature-design/4. Completed/Three-Tier Entity Model and Lifecycle Spine]]"
  - "[[riven/docs/system-design/feature-design/2. Planned/Identity Resolution System]]"
---
# Feature: Action Primitives — Tags, Segments, Alerts, Write-back

---

## 1. Overview

### Problem Statement

Riven's MVP delivers lifecycle visibility — analytics views, unified customer profiles, cross-domain queries, and the churn retrospective timeline. But every insight ends at a dead end. The operator sees that Instagram customers churn at 18%, then closes Riven and opens Meta Ads Manager. They see a customer is at risk, then switches to Gorgias to flag them. Every action happens outside Riven.

Products where users only *see* things retain 3-5x worse than products where users *do* things (per UserOnboard.com analysis of 500+ SaaS products). Intercom's founding lesson: the insight was the hook, the action was the product.

Without action endpoints, Riven becomes a monthly report that costs $200/mo. With them, it becomes the starting point of every lifecycle decision — a tool the operator opens every morning.

### Proposed Solution

Four action primitives that close the insight-to-action gap. These are NOT workflow automations or sub-agents — they're lightweight, entity-model-native actions that bridge seeing a problem and doing something about it.

---

#### Primitive 1: Tags and Flags

**What:** Apply named tags to any entity instance from any view — entity detail, data table, analytics view, churn retrospective, or query result.

**Examples:**
- Flag a customer as "At Risk" from the churn retrospective
- Tag a group of customers as "Q1 Instagram Cohort" from the channel performance view
- Mark an acquisition source as "Under Review" from the analytics view

**How it works:**
- Tags are stored as entity attributes — a `tags` array on the entity instance
- System-suggested tags: "At Risk", "High Value", "Needs Follow-up", "Under Review" — pre-populated but user-editable
- Custom tags: user-defined, workspace-scoped
- Tags are visible across all views — a customer tagged "At Risk" shows the flag in the entity data table, the customer detail view, and the analytics views
- Bulk tagging: select multiple entities from any list view and apply a tag in one action

**Write-back (optional per integration):**
- If the connected integration supports writes via Nango (e.g., HubSpot tags, Shopify customer tags), tags sync back to the source tool
- Write-back is best-effort — if the integration doesn't support writes, the tag lives in Riven only
- Write-back status shown on the tag: "Synced to HubSpot" or "Riven only"

**Data model:**
- Tags stored in `entity_attributes` as a multi-value attribute with `schemaType=TAG`
- Tag definitions stored at workspace level — shared vocabulary across all entity types
- No new table needed — uses existing normalized attribute storage

---

#### Primitive 2: Segments

**What:** Save any filtered entity view as a named, live-updating segment. Segments are saved queries that re-evaluate as data changes.

**Examples:**
- "Instagram customers with >3 support tickets who are still active"
- "Customers acquired in Q1 with no orders in the last 30 days"
- "High-revenue customers from Google Ads"
- "Churned customers from the November cohort"

**How it works:**
- A segment is a persisted entity query (using the existing query pipeline) with a name and optional description
- Created from any filtered view: apply filters in a data table or analytics view, click "Save as Segment"
- Segments are live — they re-evaluate on access, reflecting current entity data
- Segment membership count shown in a segments list view
- Segments can be used as:
  - A scoped view (click to see matching entities)
  - An export source (push to integration or CSV)
  - An alert scope (monitor this segment's count or metric)

**Data model:**

| Entity | Purpose | Key Fields |
|---|---|---|
| SegmentDefinition | Persisted segment query | id, workspaceId, name, description, entityTypeKey, queryFilter (JSONB), createdBy, createdAt |

- `queryFilter` stores the filter AST in the same format as the existing entity query pipeline
- No new query infrastructure — segments execute through the existing `EntityQueryService`
- Segment counts are computed on access (live), not materialized — acceptable for <50k entity instances per workspace at launch

---

#### Primitive 3: Threshold Alerts

**What:** "Notify me when [metric] crosses [threshold]." Simple numeric checks on entity count or aggregation queries, evaluated on a schedule.

**Examples:**
- "Alert when Instagram customer churn rate exceeds 10% this month"
- "Alert when support tickets from Q1 cohort exceed 20 in a week"
- "Alert when new customer count from Google Ads drops below 5 this week"
- "Alert when a segment's size changes by more than 20%"

**How it works:**
- An alert is: a segment or entity query + a metric (count, sum, average) + a comparison operator (>, <, =, change %) + a threshold value + a check frequency (hourly, daily, weekly)
- Evaluation: a scheduled job runs the query, computes the metric, compares to threshold
- If threshold is crossed: create a notification (in-app, and optionally email)
- Alert state: ACTIVE, TRIGGERED (threshold crossed), RESOLVED (no longer crossing)
- Alert history: log of when the alert was triggered and what the value was

**Data model:**

| Entity | Purpose | Key Fields |
|---|---|---|
| AlertDefinition | Alert configuration | id, workspaceId, name, segmentId (nullable), queryFilter (JSONB, nullable), metric (COUNT/SUM/AVG), operator (GT/LT/EQ/CHANGE_PCT), threshold, frequency (HOURLY/DAILY/WEEKLY), enabled |
| AlertEvent | Alert trigger history | id, alertId, triggeredAt, metricValue, thresholdValue, status (TRIGGERED/RESOLVED) |

- If `segmentId` is set, the alert monitors that segment. If `queryFilter` is set directly, the alert runs an ad-hoc query.
- Evaluation job runs via existing scheduled job infrastructure (ShedLock for distributed locking, same pattern as workflow queue dispatch)
- Notifications delivered via the [[riven/docs/system-design/feature-design/4. Completed/Notifications]] domain (in-app + email)

---

#### Primitive 4: Segment Export and Write-back

**What:** Push any segment or filtered entity list to a connected integration or export as CSV.

**Examples:**
- Push "At Risk Instagram Customers" segment to a Klaviyo list for a retention campaign
- Export "High Support, Low Revenue" segment as CSV for a team meeting
- Push "Q1 Churned Customers" to a HubSpot list for a win-back sequence
- Push "High Value Active Customers" to Klaviyo for a loyalty campaign

**How it works:**
- From any segment or filtered view: "Export" button with options:
  - **CSV Download** — always available, exports visible columns
  - **Push to Integration** — available when a connected integration supports list/segment writes via Nango
- Integration write targets depend on Nango's write capabilities per provider:
  - Klaviyo: create/update list with customer emails
  - HubSpot: create/update contact list
  - Mailchimp: create/update audience segment
  - Other integrations: CSV export as fallback
- Push is a point-in-time operation (not continuous sync). The user pushes when they want to act. Future: continuous sync could be added as a segment option.
- Push history: log of when a segment was pushed, to which integration, and how many entities

**Data model:**

| Entity | Purpose | Key Fields |
|---|---|---|
| SegmentExportEvent | Export/push history | id, segmentId, exportType (CSV/INTEGRATION), integrationId (nullable), entityCount, exportedAt, exportedBy |

- Write-back executed via Nango's write API — Riven sends the entity data (mapped to the integration's schema via existing field mappings), Nango handles the API call
- Error handling: if write fails, show error to user with retry option. Partial failures (some entities pushed, some failed) logged per-entity.

---

### How the Primitives Work Together

The power is in the composition:

```
  ANALYTICS VIEW: Channel Performance
       │
       │  "Instagram churn is 18% — click to see customers"
       │
       ▼
  ENTITY LIST: 142 Instagram customers
       │
       │  Filter: churned = false AND support_tickets > 2
       │
       ▼
  SAVE AS SEGMENT: "Instagram At-Risk Customers" (34 matches)
       │
       ├──→ TAG all 34 as "At Risk" (syncs to HubSpot)
       │
       ├──→ SET ALERT: "Notify me if this segment exceeds 40"
       │
       └──→ PUSH TO KLAVIYO: retention email campaign
```

The operator went from insight (channel performance) to action (tagged, alerted, pushed to email tool) without leaving Riven. This is the daily workflow loop that drives retention.

---

### Infrastructure Requirements

| Requirement | Notes |
|---|---|
| **Nango write-back API** | Need to confirm which integrations support writes via Nango. MVP can ship with CSV export for all + write-back for supported integrations. |
| **Scheduled job for alerts** | Uses existing ShedLock infrastructure from workflow queue dispatch. New job type: `AlertEvaluationJob`. |
| **Notification delivery** | In-app notifications + email. Notifications domain may need to be built or extended. |
| **Tag attribute type** | New `schemaType=TAG` in entity attributes. Multi-value. Workspace-scoped tag vocabulary. |
| **Segment persistence** | New `segment_definitions` table. Lightweight — stores query filter as JSONB. |

### Success Criteria

- [ ] Users can tag any entity from any view (detail, table, analytics, retrospective, query result)
- [ ] Bulk tagging: select multiple entities and apply a tag in one action
- [ ] Tags are visible across all views — tagged entities show their flags everywhere
- [ ] Tags sync back to integrations that support writes (best-effort, status visible)
- [ ] Users can save any filtered view as a named segment
- [ ] Segments are live — re-evaluate on access with current data
- [ ] Segment count is visible in a segments list view
- [ ] Users can create threshold alerts on segments or ad-hoc queries
- [ ] Alerts evaluate on schedule (hourly/daily/weekly) and produce notifications
- [ ] Alert history shows when thresholds were crossed and what the values were
- [ ] Users can export any segment or filtered view as CSV
- [ ] Users can push segments to supported integrations (Klaviyo, HubSpot) via Nango write-back
- [ ] Push history shows what was pushed, when, and to where
- [ ] Partial data handling: write-back failures are logged and retryable per-entity

---

## Related Documents

- [[riven/docs/system-design/feature-design/1. Planning/Lifecycle Analytics Views]] — primary surface where actions are triggered from
- [[riven/docs/system-design/feature-design/5. Backlog/Churn Retrospective Timeline]] — tag/flag customers directly from the retrospective
- [[riven/docs/system-design/feature-design/4. Completed/Three-Tier Entity Model and Lifecycle Spine]] — entity types being tagged and segmented
- [[riven/docs/system-design/feature-design/2. Planned/Identity Resolution System]] — cross-tool entity linking for segment scoping
- [[riven/docs/system-design/domains/Integrations/Integrations]] — Nango write-back capability
- [[riven/docs/system-design/feature-design/4. Completed/Notifications]] — alert delivery
- [[riven/docs/system-design/domains/Entities/Querying/Querying]] — existing query pipeline reused for segment evaluation
- [[Launch Scope and Phasing]] — Layer 1 MVP component
- CEO Plan: Day 1 Value — Layer 1 MVP (2026-03-19)

---

## Changelog

| Date | Author | Change |
| ---- | ------ | ------------- |
| 2026-03-19 | | Initial skeleton from expert panel feedback on insight-to-action gap |
