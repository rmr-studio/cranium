---
type: resource
Created: 2026-05-13
Updated: 2026-05-13
tags:
  - architecture/archived
  - cranium/v1
---
# Archived Domains — out of Cranium v1 scope

The architecture pivot (2026-05-13 — see [[architecture-pivot]] §Reuse/Rework/Replace/Delete) narrows Cranium to **the open team-context layer for AI-amplified engineering teams**: GitHub scan → domain/ownership/SME map + ADR index → web graph + MCP server + non-blocking PR comment, all over the `pages` family. Everything below was part of the pre-pivot product (or earlier explorations) and is **not** in v1. Three buckets: **dormant** (code left in place, not deleted, not extended), **deleted in Phase 1** (gone — the delete list is absolute), and **different product, later**.

---

## Dormant — workflow engine left in place, not deleted, not extended

These describe the agentic-orchestration layer. The DAG/workflow engine (nodes, triggers, action nodes, conditions) stays in the repo, untouched, and gets revisited if "agentic skills" land. Not part of any v1 surface.

| Old domain / doc | Status | Why out of v1 |
|---|---|---|
| Agentic Orchestration | dormant | Not in the v1 surface (graph + MCP + PR bot); the workflow engine is left dormant — don't delete, don't extend. |
| Agent Runtime (7-Plane) | dormant | The 7-plane agent architecture is out of v1; revisit when agentic skills land. |
| Sub-Agent Spawn Protocol | dormant | Same — depends on the agent runtime, which is dormant. |
| Agent Governance Layer | dormant | Governance over a runtime that isn't active in v1. |

---

## Deleted in Phase 1 — see [[architecture-pivot]] §Reuse/Rework/Replace/Delete

GitHub-only v1 = one hardcoded scanner, page kinds as a fixed Kotlin enum — none of the generic-type / manifest / multi-connector machinery is needed. Phase 1's delete list is **absolute**: nothing "kept just in case".

| Old domain / system | Replaced by / why gone |
|---|---|
| Integration Definition Catalog / Manifest System (`ManifestLoaderService`, dual Kotlin+JSON pipelines, `ManifestUpsertService`) | Page-kind definitions live in Kotlin; v1 has one connector. No manifest engine. |
| Custom Data Connectors | v1 = GitHub only; Slack/Notion are v1.1 (a small `SourceConnector` interface, no manifest engine). |
| Nango Integration Sync (3-pass Temporal workflow, `SchemaMappingService`, webhook HMAC, sync-state health) | Deleted for v1 — the HMAC-webhook-validation pattern is preserved in git history / a wiki page for the v1.1 connectors. |
| Entity Type System (User-Defined Schemas) — entity-type CRUD, `EntityTypeRole` 3-plane split | Page kinds are a fixed enum in code (add one via a PR, like a `CoreModelDefinition`). No runtime type registration. |
| Schema Mapping & Field Configuration | No user types ⇒ nothing to map; `EntityValidationService` JSON-schema-per-type + `detectSchemaBreakingChanges()` deleted. |
| Integration Setup / Connection UI | The screens go in Phase 1d when their backend dies in 1c. |
| Relationship Definition system (`RelationshipDefinitionEntity` + cardinality + semantic constraints + polymorphic `target_kind` + inverse-REFERENCE auto-rows + two-pass impact confirmation + `QueryDirection`) | Collapsed to `page_links` + a label enum ([[ADR-012]]); backlinks are reverse queries. |
| Projection Rule engine (`EntityProjectionService` + `ProjectionRuleEntity` + `(LifecycleDomain, SemanticGroup)` matcher + `TemplateMaterializationService`) | Replaced by the LLM synthesis workflow ([[ADR-014]] → [[domains/Synthesis/Synthesis|Synthesis]]). |

> Note: **Notes & Collaborative Knowledge** as a separate domain is gone — it **folds into Pages**: "everything is a page" (the `knowledge-entity-graduation` move, finished by the pivot). A note is just a `Page` of `source_type: USER`.

---

## Deferred — post-design-partner

| Old domain | Why out of v1 |
|---|---|
| Notifications | Deferred post-design-partner — not a v1 requirement; the `decision_queue` covers the "things needing attention" need for v1. |

---

## Different product, later — customer-analytics

The pre-pivot product was customer analytics for DTC/B2C-SaaS. That's a *different product*; it isn't v1, and it isn't a near-term roadmap item.

| Old domain / feature | Why out of v1 |
|---|---|
| Signal Detection / Decision Engine (customer-analytics) | Different product (CohortDriftEvent, CreativeFatigueEvent, ChurnRiskSignal, DiscountDependentSignal, the SignalDerivationWorkflow) — later. |
| Company Brain | Pre-pivot framing of the synthesis layer for a different product; superseded by the `pages` family + [[domains/Synthesis/Synthesis|Synthesis]]. |
| PostHog / Stripe / Dovetail closed loop | Customer-analytics ingestion — different product. |
| Roadmap convergence / outcome attribution | The customer-analytics → roadmap-convergence closed loop — premature even for that product; later. |
| DTC core-model expansion (23 models — campaigns, ad creatives, shipments, returns, reviews, …) | Customer-analytics domain models — not v1. |

---

## Where v1's domains live

[[domains/Pages & Links Core/Pages & Links Core|Pages & Links Core]] · [[domains/Source Ingestion/Source Ingestion|Source Ingestion]] · [[domains/Synthesis/Synthesis|Synthesis]] · [[domains/Page Resolution & Decision Queue/Page Resolution & Decision Queue|Page Resolution & Decision Queue]] · [[domains/Surfaces/Surfaces|Surfaces]] · [[domains/MCP Server/MCP Server|MCP Server]] · [[domains/OSS Packaging & Self-Host/OSS Packaging & Self-Host|OSS Packaging & Self-Host]] · [[domains/Workspace & Auth/Workspace & Auth|Workspace & Auth]] — and [[overview]] / [[page-kinds]] / [[architecture-pivot]].
