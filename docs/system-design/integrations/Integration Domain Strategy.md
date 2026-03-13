---
tags:
  - architecture/integration
  - riven/strategy
Created: 2026-02-27
Updated: 2026-02-27
---
# Integration Domain Strategy & Entity Type Blueprint

## Summary

Strategic reference for integration domain prioritization and how each domain maps to Riven's entity type ecosystem.

**Scope:** Domain analysis, entity type blueprints (read-only integration types), strategic alignment with intelligence outcomes.

**Not covered:** API design, data flows, technical integration patterns. See [[Domain Integration]] for cross-domain interaction mechanics.

**Related:** [[Nango]], [[Knowledge Layer Sub-Agents]], [[Domain Integration]], [[Entities]], [[Knowledge]]

---

## Entity Type Architecture — The Two-Layer Model

Riven's integration model produces two distinct layers of entity types. This separation is the foundational architectural decision for how external data enters the system.

### Layer 1: Read-Only Integration Entity Types

Each integration produces **source-pure, read-only entity types** namespaced by provider.

- Types are namespaced: `STRIPE_INVOICE`, `STRIPE_SUBSCRIPTION`, `ZENDESK_TICKET`, `HUBSPOT_COMPANY`, `GMAIL_THREAD`
- Schema matches the provider's data model — faithful to the source
- Users cannot modify these types or their instances directly
- Attributes carry semantic classifications matching the source data (e.g., `STRIPE_SUBSCRIPTION.mrr` is quantitative, `ZENDESK_TICKET.severity` is categorical)
- Semantic metadata (definitions, classifications, tags) is pre-populated based on provider schema

### Layer 2: User-Defined Entity Types

Users create their own custom entity types and link them to integration entities via relationships.

- Custom types like `Customer`, `Account`, `Project` represent the user's mental model
- Cross-domain connections form here — a `Customer` links to `HUBSPOT_COMPANY`, `STRIPE_CUSTOMER`, `ZENDESK_USER`, `GMAIL_CONTACT`
- The user's custom model becomes their operational view; integration entities are the data sources
- Relationship cardinality enforced per Riven's existing system (e.g., Customer to STRIPE_CUSTOMER is one-to-one, Customer to ZENDESK_TICKET is one-to-many)

### Why This Matters for Intelligence

- **Data integrity & provenance** — Read-only types preserve exactly what Stripe said vs what Zendesk said. No data mixing at the storage layer.
- **User mental model** — User-defined types represent how the user thinks about their business, not how SaaS tools organize data.
- **Cross-domain reasoning** — The knowledge layer operates on the user's model, pulling data through relationships to integration types. Intelligence follows the user's structure, not the provider's.
- **Semantic grounding** — The knowledge layer reasons over semantic metadata from both layers, enabling queries like "show me customers where revenue is declining and support tickets are increasing."

---

## Strategic Alignment Analysis

Cross-reference of domain priorities against Riven's core intelligence outcomes. Phase 1 domains are sufficient to cover all seven outcomes. Phase 2 adds depth, not breadth.

| Intelligence Outcome | CRM | Revenue | Communication | Support | Marketing | Usage | Calendar |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| Churn prediction | x | x | x | x | | x | |
| Revenue risk | x | x | x | x | | | |
| Engagement monitoring | x | | x | x | | x | x |
| Customer health scoring | x | x | x | x | | x | |
| Contact identification | x | | x | | | | x |
| Response pattern analysis | | | x | x | | | |
| Acquisition channel analysis | x | | | | x | | |

**Key finding:** Phase 1 (CRM + Revenue + Communication + Support) covers all 7 intelligence outcomes. Phase 2 domains add depth to existing outcomes rather than enabling new ones.

---

## Domain Analysis — Phase 1: Core

### CRM (HubSpot, then Salesforce)

**Strategic rationale:** Provides the canonical customer/company data that user-defined types will reference. Most user models will start by linking their `Customer` to `HUBSPOT_COMPANY` and `HUBSPOT_CONTACT`.

**Read-only entity types:**

| Type | Key Attributes | Semantic Classification |
|---|---|---|
| `HUBSPOT_COMPANY` | company_name, industry, lifecycle_stage | identifier, categorical, categorical |
| `HUBSPOT_CONTACT` | email, contact_role, last_activity_date | identifier, categorical, temporal |
| `HUBSPOT_DEAL` | deal_value, close_date, stage | quantitative, temporal, categorical |
| `HUBSPOT_PIPELINE` | pipeline_name, stage_order | identifier, categorical |

**Internal relationships:**
- HUBSPOT_CONTACT to HUBSPOT_COMPANY — many-to-one
- HUBSPOT_DEAL to HUBSPOT_COMPANY — many-to-one

---

### Revenue & Billing (Stripe, then Paddle/Chargebee)

**Strategic rationale:** Quantifies every customer relationship. Without revenue data, intelligence is directional not quantified.

**Read-only entity types:**

| Type                  | Key Attributes                                    | Semantic Classification                          |
| --------------------- | ------------------------------------------------- | ------------------------------------------------ |
| `STRIPE_CUSTOMER`     | stripe_customer_id, email                         | identifier, identifier                           |
| `STRIPE_SUBSCRIPTION` | mrr, plan_tier, renewal_date, subscription_status | quantitative, categorical, temporal, categorical |
| `STRIPE_INVOICE`      | amount_due, due_date, payment_status              | quantitative, temporal, categorical              |
| `STRIPE_PAYMENT`      | amount_paid, payment_date, payment_method         | quantitative, temporal, categorical              |
| `STRIPE_PLAN`         | plan_name, price, billing_interval                | identifier, quantitative, categorical            |

**Internal relationships:**
- STRIPE_SUBSCRIPTION to STRIPE_CUSTOMER — many-to-one
- STRIPE_INVOICE to STRIPE_SUBSCRIPTION — many-to-one

---

### Customer Communication (Gmail, then Slack)

**Strategic rationale:** Where relationship signals live. Email frequency, response times, tone shifts, who's talking to whom.

**Read-only entity types:**

| Type | Key Attributes | Semantic Classification |
|---|---|---|
| `GMAIL_THREAD` | thread_subject, last_message_date, participant_count | freetext, temporal, quantitative |
| `GMAIL_MESSAGE` | response_time_hours, sent_at, from_address | quantitative, temporal, identifier |
| `GMAIL_CONTACT` | email_address, display_name | identifier, identifier |

**Internal relationships:**
- GMAIL_MESSAGE to GMAIL_THREAD — many-to-one

---

### Support (Intercom, then Zendesk)

**Strategic rationale:** Where problems surface first. Bridges revenue (is this high-value?) and communication (has anyone followed up?).

**Read-only entity types:**

| Type | Key Attributes | Semantic Classification |
|---|---|---|
| `INTERCOM_TICKET` | severity, resolution_time_hours, status, created_at, ticket_tags | categorical, quantitative, categorical, temporal, categorical |
| `INTERCOM_CONVERSATION` | description, started_at, status | freetext, temporal, categorical |
| `INTERCOM_CONTACT` | email, name, company_id | identifier, identifier, identifier |

**Internal relationships:**
- INTERCOM_CONVERSATION to INTERCOM_TICKET — one-to-one

---

### Cross-Domain Example: User-Defined Model

A user creates a `Customer` entity type and links it to all four Phase 1 domains:

```
Customer (user-defined)
  ├── HUBSPOT_COMPANY   (one-to-one)
  ├── STRIPE_CUSTOMER   (one-to-one)
  ├── INTERCOM_CONTACT  (one-to-one)
  └── GMAIL_CONTACT     (one-to-one)
```

The user then creates an `Account Health` entity type with custom quantitative attributes, linked one-to-one to `Customer`. The knowledge layer can now reason across all four domains through the user's model — revenue trends from Stripe, support load from Intercom, communication patterns from Gmail, lifecycle stage from HubSpot — all unified under the user's definition of "customer."

---

## Domain Analysis — Phase 2: Depth

### Marketing & Acquisition (Google Ads, then Meta Ads, LinkedIn Ads)

**Read-only entity types:** `GOOGLE_ADS_CAMPAIGN`, `GOOGLE_ADS_AD_GROUP`, `GOOGLE_ADS_PERFORMANCE`

Enables acquisition channel analysis and cost-per-acquisition by segment. Adds depth to customer health scoring and acquisition channel analysis.

### Product Usage & Analytics (Mixpanel/Segment or custom webhooks)

**Read-only entity types:** `MIXPANEL_EVENT`, `MIXPANEL_USER_PROFILE`, `MIXPANEL_COHORT`

Enables enriched churn prediction (usage drop + support spike + renewal approaching). Hardest to standardize — webhook/API support for custom usage data is critical. Adds depth to churn prediction, engagement monitoring, and customer health scoring.

### Calendar & Scheduling (Google Calendar, then Calendly)

**Read-only entity types:** `GOOGLE_CALENDAR_EVENT`, `CALENDLY_MEETING`

Enables meeting signal enrichment (cancelled QBRs, onboarding no-shows, declining meeting frequency). Adds depth to engagement monitoring and contact identification.

---

## Domain Analysis — Deferred

| Domain | Examples | Deprioritization Rationale |
|---|---|---|
| Accounting/Finance | Xero, QuickBooks | Cost-to-serve and margin calculations. Sensitive data, complex normalization. Post-launch. |
| Project Management | Linear, Jira, Asana | Internal bottleneck detection. Different buyer persona (engineering/ops lead vs founder). Post-launch. |
| Infrastructure/DevOps | AWS, Datadog, PagerDuty | Crosses into observability. Different product category entirely. Year-two. |
| E-commerce | Shopify, WooCommerce | Alternative revenue flavor for consumer businesses. Wait for audience signal. |
| Social/Community | Instagram, Discord, Twitter | Unstructured, API-limited, noisy. Poor effort-to-insight ratio. |

---

## References

- [[Domain Integration]] — Cross-domain interaction mechanics in Riven Core
- [[Entities]] — Entity type system, attributes, relationships, and validation
- [[Knowledge]] — Knowledge layer and semantic reasoning
- [[Knowledge Layer Sub-Agents]] — Proactive intelligence agents
- [[Nango]] — Integration infrastructure (ADR-001)
