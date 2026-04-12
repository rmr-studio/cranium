---
tags:
  - "#status/draft"
  - priority/critical
  - architecture/feature
  - architecture/frontend
  - domain/entity
Created: 2026-03-19
Updated:
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
blocked by:
  - "[[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/5. Backlog/Three-Tier Entity Model and Lifecycle Spine]]"
  - "[[riven/docs/system-design/feature-design/2. Planned/Identity Resolution System]]"
---
# Feature: Lifecycle Analytics Views

---

## 1. Overview

### Problem Statement

Riven's entity data tables show raw entity instances — individual customers, orders, tickets. But the operator's daily question isn't "show me customer #4,271." It's "which acquisition channel is worth my ad spend?" and "are newer cohorts healthier than older ones?" and "where is support effort disproportionate to revenue?"

These are aggregation questions across identity-resolved, cross-domain entity data. The answers exist in the connected entity model from the moment integrations sync — no AI, no pattern accumulation, no knowledge layer needed. But without pre-built views that surface these aggregations automatically, the operator has to know what to ask and how to query for it. The data is there. The visibility isn't.

### Proposed Solution

Three pre-built analytics views that populate automatically from synced entity data. Each view is a structured aggregation over lifecycle spine entity types and their relationships, rendered as a sortable, filterable table with entity-linked drill-through.

These are NOT the Phase 2 Lifecycle Operations Dashboard (which adds sub-agent signals, AI-generated insights, and metrics computation). These are simpler — structured SQL-expressible aggregations over the entity model, rendered as purpose-built UI components.

---

#### View 1: Channel Performance

**What it answers:** "Which acquisition channels are producing valuable customers, and which are wasting money?"

**Structure:**

| Acquisition Source | Customers | Revenue | Avg Order Value | Support Tickets | Avg Tickets/Customer | Churn Rate | Effective LTV |
|---|---|---|---|---|---|---|---|
| Instagram Ads | 142 | $48,200 | $78 | 89 | 0.63 | 18% | $265 |
| Google Search | 98 | $62,100 | $112 | 31 | 0.32 | 8% | $580 |
| Organic / Direct | 67 | $29,800 | $94 | 12 | 0.18 | 5% | $410 |
| Referral | 23 | $14,200 | $118 | 4 | 0.17 | 4% | $590 |

**How it's computed:**
- Rows: Distinct `Acquisition Source` entity instances
- Customer Count: Count of `Customer` entities related to each source via identity resolution
- Revenue: Sum of `Order.total` or `Billing Event.amount` for related customers
- Support Tickets: Count of `Support Interaction` entities for related customers
- Churn Rate: Count of customers with linked `Churn Event` / total customers
- Effective LTV: Revenue per customer minus proportional support cost (if support cost data available), otherwise Revenue / Customer Count

**Data path:**
```
Acquisition Source
  → (relationship) → Customer
    → (relationship) → Order / Subscription      → Revenue, AOV
    → (relationship) → Support Interaction        → Ticket count
    → (relationship) → Billing Event              → Billing status
    → (relationship) → Churn Event                → Churn flag
```

All relationships traversed via identity resolution. Every cell is clickable — links to the underlying entity list (e.g., click "142" to see the 142 Instagram customers).

**DTC vs. B2C SaaS differences:**
- DTC: Revenue from Orders, AOV = avg order total, LTV includes repeat purchase rate
- B2C SaaS: Revenue from Subscriptions, metrics include MRR, expansion/contraction

---

#### View 2: Cohort Overview

**What it answers:** "Are newer customer groups healthier than older ones? Where is the lifecycle breaking down?"

**Structure:**

| Cohort (Month) | Customers | Still Active | Churned | Churn Rate | Avg Support Tickets | Revenue | Revenue/Customer |
|---|---|---|---|---|---|---|---|
| 2026-01 | 45 | 31 | 14 | 31% | 2.1 | $12,400 | $276 |
| 2026-02 | 62 | 51 | 11 | 18% | 1.4 | $19,800 | $319 |
| 2026-03 | 78 | 74 | 4 | 5% | 0.8 | $22,100 | $283 |

**How it's computed:**
- Rows: Customers grouped by acquisition month (derived from `Customer.created_at` or first `Order`/`Subscription` date)
- Still Active: Customers without a linked `Churn Event`
- Churned: Customers with a linked `Churn Event`
- Support Tickets: Average `Support Interaction` count per customer in cohort
- Revenue: Sum of `Order.total` or `Billing Event.amount` for cohort

**Key insight this enables:** The operator immediately sees that January's cohort has 31% churn while March is at 5%. Combined with the Channel Performance view, they can investigate whether January's high churn correlates with a specific acquisition channel.

---

#### View 3: Support-Revenue Correlation

**What it answers:** "Where is support effort disproportionate to customer value? Which segments consume the most resources relative to their revenue?"

**Structure:**

| Segment | Customers | Total Tickets | Tickets/Customer | Revenue | Revenue/Ticket | Churn Rate |
|---|---|---|---|---|---|---|
| High Revenue, Low Support | 34 | 12 | 0.35 | $42,000 | $3,500 | 3% |
| High Revenue, High Support | 18 | 67 | 3.72 | $28,000 | $418 | 12% |
| Low Revenue, Low Support | 89 | 28 | 0.31 | $15,000 | $536 | 6% |
| Low Revenue, High Support | 41 | 124 | 3.02 | $8,200 | $66 | 28% |

**How it's computed:**
- Segments: Customers bucketed by revenue quartile (high/low) and support ticket count quartile (high/low)
- Revenue thresholds derived from the dataset's median values
- Support thresholds derived from the dataset's median ticket count
- Each cell drills through to the actual customer list for that segment

**Key insight this enables:** The "Low Revenue, High Support" segment — 41 customers consuming 124 tickets and producing only $8,200 in revenue with 28% churn — is immediately visible as a problem cohort. The operator can drill in to see their acquisition sources.

---

### View Interaction Patterns

All three views share common interaction patterns:

- **Sort by any column** — click column header to sort ascending/descending
- **Filter** — filter by date range, acquisition source, or custom attributes
- **Drill-through** — every count cell links to the underlying entity list. Click "142 customers" to see those 142 customers in the entity data table with full cross-domain columns.
- **Cross-view navigation** — from Channel Performance, click a channel's churn rate to see those churned customers. From there, click a customer to see their Churn Retrospective Timeline.
- **Export** — export any view or filtered subset as CSV

### Auto-population

Views populate automatically once integrations sync and identity resolution completes. No user configuration required. If a lifecycle domain has no data (e.g., no support tool connected), columns dependent on that domain show "—" with a tooltip: "Connect a support tool to see this metric."

### Success Criteria

- [ ] Channel Performance view populates automatically from synced Acquisition Source + Customer + Order/Subscription + Support Interaction + Billing Event + Churn Event data
- [ ] Cohort Overview view groups customers by acquisition month with lifecycle metrics
- [ ] Support-Revenue Correlation view segments customers by revenue/support quartiles
- [ ] All count cells link to underlying entity lists (drill-through)
- [ ] Views render with partial data — missing lifecycle domains show "—" with explanation
- [ ] Views load in <2s for workspaces with <50k entity instances
- [ ] Sortable by any column, filterable by date range and acquisition source
- [ ] Export to CSV for any view or filtered subset
- [ ] DTC and B2C SaaS variants use appropriate metrics (orders vs. subscriptions, AOV vs. MRR)

---

## Related Documents

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/5. Backlog/Three-Tier Entity Model and Lifecycle Spine]] — entity types these views aggregate over
- [[riven/docs/system-design/feature-design/2. Planned/Identity Resolution System]] — linking records across tools for cross-domain aggregation
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/5. Backlog/Churn Retrospective Timeline]] — drill-in from churn rate cells
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/feature-design/1. Planning/Lifecycle Operations Dashboard]] — Phase 2 evolution of these views with AI signals
- [[Launch Scope and Phasing]] — Layer 1 MVP component
- CEO Plan: Day 1 Value — Layer 1 MVP (2026-03-19)

---

## Changelog

| Date | Author | Change |
| ---- | ------ | ------------- |
| 2026-03-19 | | Initial skeleton from CEO plan review — Layer 1 MVP scope |
