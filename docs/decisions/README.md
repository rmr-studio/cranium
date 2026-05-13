---
type: resource
Created: 2026-05-13
Updated: 2026-05-13
tags:
  - architecture/decision
---

# Architecture Decision Records — Index

ADRs for Cranium. ADR-011 … ADR-021 are the **2026-05 architecture pivot** decisions (D1–D10 + ER3 + the GitHub-only call) — see [[architecture-pivot]] for the flattened spec. ADR-001 … ADR-010 are pre-pivot; their status below reflects the pivot teardown.

> Numbering quirk: there are **two ADRs numbered 008** ("Integration-Based Entity Types as Readonly Materialized Views" and "Temporal for Integration Sync Orchestration"). Renumbering is deferred to avoid breaking inbound links; new ADRs start at 011.

| ADR | Title | Status | Summary |
|---|---|---|---|
| 001 | Nango as Integration Infrastructure | superseded → [[ADR-021 GitHub-Only v1, Slack and Notion as v1.1]] | v1 is GitHub-only; the Nango integration sync is deleted; HMAC-webhook pattern preserved for v1.1 connectors. |
| 002 | Separate Table for Semantic Metadata | superseded/reworked → [[ADR-013 Thin Per-Kind Frontmatter in Code, Loose Extras]] | No user-defined types; per-kind frontmatter is Kotlin on the sealed `Page` subclass; `SemanticGroup` machinery → `pages.classification`. |
| 003 | Single Discriminator Table for Metadata Targets | superseded/reworked → [[ADR-013 Thin Per-Kind Frontmatter in Code, Loose Extras]] | The `entity_type_semantic_metadata` discriminator table goes with the semantic-metadata layer it served. |
| 004 | Declarative-First Storage for Integration Mappings and Entity Templates | superseded → [[architecture-pivot]] | The manifest/catalog engine is deleted; page-kind + synthesis-contract definitions live in Kotlin, added via PR. |
| 005 | Strategy Pattern with Conditional Bean Selection for Storage Providers | accepted (still in force) | Pluggable file-storage backends via `@ConditionalOnProperty`; file pipeline not in v1 surface but kept. |
| 006 | HMAC-Signed Download Tokens for File Access | accepted (still in force) | HMAC-SHA256 signed download tokens; also the reference pattern for v1.1 connector webhook validation. |
| 007 | Magic Byte Content Validation via Apache Tika | accepted (still in force) | Server-side content-type verification via Tika magic bytes + per-domain allowlists. |
| 008 | Integration-Based Entity Types as Readonly Materialized Views | superseded → [[ADR-018 One pages Table Family for Synthesis Storage]] | Everything is a `Page`; raw artifacts → `source_entities`; the two-layer split survives, the readonly-materialized-view framing does not. |
| 008 | Temporal for Integration Sync Orchestration | partially superseded → [[ADR-014 Deterministic Source-Entity Creation plus Batched LLM Synthesis]] | Temporal stays (synthesis + page-resolution workflows reuse its patterns); the integration-sync workflow itself is deleted. |
| 009 | Unique Index Deduplication over Mapping Table | accepted (still in force) | Unique partial index instead of a mapping table; pattern reused for `source_entities` content-hash dedup + the page-resolution suggestion queue. |
| 010 | Webhook-Driven Connection Creation | superseded → [[ADR-021 GitHub-Only v1, Slack and Notion as v1.1]] | The integration-connection lifecycle goes with the Nango sync; v1 GitHub auth = PAT for scan, App for PR bot. |
| 011 | Reuse the Storage Spine + Infra; Gut & Rebuild the Type / Projection / Routing / Catalog Machinery (D1) | accepted | In-place rescope: keep storage spine + auth/RLS + Temporal + embeddings; delete the type/projection/manifest/sync machinery. |
| 012 | Single `page_links` Edge Table with an Optional In-Code Label Enum (D2) | accepted | One edge table, nullable `label` enum + `weight` + `source_entity_id`; backlinks = reverse query; no relationship definitions. |
| 013 | Thin Per-Kind Frontmatter Declared in Code; Loose Extras Stored, Not Validated (D3) | accepted | Per-kind frontmatter shape in Kotlin; extra LLM-extracted keys stored in `pages.frontmatter` jsonb (GIN), not validated; jsonb-only from Phase 1a. |
| 014 | Deterministic Source-Entity Creation + Batched LLM Synthesis Routing (D4) | accepted | Stage 1: deterministic parse → `source_entities` + cheap links. Stage 2: batched/coalesced LLM synthesis (the Temporal Synthesis Layer adapted). |
| 015 | Page Resolution Policy (D5) | accepted | Deterministic key lookup → fuzzy candidate-find+score → auto-link or `decision_queue`; human gate kept; ≥85%-precision eval suite is a CI gate; threshold is config. |
| 016 | Structural Collapse Before Behavioral Change — the Phasing Rule (D6) | accepted | Phase 1 = mechanical collapse (1a rename / 1b relationship collapse — gated / 1c taxonomy + re-embed / 1d `apps/client` collapse); Phase 2 = new pipeline. Bail criterion at week 3 of CC time. |
| 017 | Async Synthesis Materialization with On-Navigation Fill (D7) | accepted | Pages materialized async during ingestion (`stale` flag + backlog) + a non-blocking on-navigation high-priority refresh; no LLM on the request path. |
| 018 | One `pages` Table Family for Synthesis Storage (D10) | accepted | `pages` (node + article) + `page_history` + `page_links` + `source_entities` + `page_embeddings` + suggestion/merge/decision/source-event-queue/`scan_runs`; no `entity_synthesis*`; kinds = Kotlin sealed `Page`. |
| 019 | Full Architecture Pivot Before Securing a Design Partner (D9) | accepted | Deliberate founder override of office-hours + eng + outside-voice (all recommended wedge-first); bail criterion is the safety valve. |
| 020 | MCP Server as a Module Inside `core/` (Kotlin/Spring), Not a Separate Service (ER3) | accepted | MCP read-only query facade over `pages`/`page_links` + the compact `pages.body` projection, inside `core/`, reusing auth/RLS. |
| 021 | GitHub-Only v1; Slack and Notion/Confluence Are v1.1 Enrichment | accepted | v1 ships one hardcoded GitHub scanner behind a `SourceConnector` interface; no manifest engine; PAT for scan, App for PR bot. |

## Related

- [[architecture-pivot]] — the canonical pivot spec
- [[../readme]] — the `docs/` tree overview
- ADR template: `docs/templates/Decisions/Architecture Decision Record.md`
