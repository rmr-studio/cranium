# TODOS

## Custom Integration Builder - Direct Postgres, CSV, and Webhook Ingestion

**Priority:** P2
**Effort:** XL (human: ~6 weeks) / L (CC: ~4 hours)
**Depends on:** Smart Projection (domain-based projection routing must work first)

Per the SaaS Decline thesis, data sources will diversify beyond SaaS integrations. Users need to:
- Connect internal Postgres tables directly
- Import CSVs with schema inference
- Receive webhooks from custom internal systems
- Poll internal APIs

All of these produce entities classified by LifecycleDomain. Domain-based projection routing
(decided in CEO review 2026-03-20) handles them automatically — any SUPPORT-domain entity from
any source routes to the SupportTicket core model without additional configuration.

**Pros:** Directly addresses the SaaS Decline thesis. Domain-based routing makes this architecturally
clean. Positions Riven as "operational data layer" not "SaaS connector."

**Cons:** Large scope. Requires UI for connection setup, schema inference, field mapping.
Each ingestion type has unique edge cases (Postgres connection pooling, CSV encoding, webhook auth).

**Context:** See the "SaaS Decline & Strategic Positioning" document in the philosophy vault
for the strategic thesis. The expanded ingestion model is defined there. Core model architecture
(CEO plan 2026-03-20) provides the foundation — domain-based routing means new ingestion types
work without touching core model code.
