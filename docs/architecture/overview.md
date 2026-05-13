---
type: resource
Created: 2026-05-13
Updated: 2026-05-13
tags:
  - architecture/overview
  - cranium/v1
---
# Cranium v1 — Architecture Overview

> **Supersedes** the stale "6-layer stack" framing (System Context → Ingestion → Processing → Knowledge → Intelligence → Application). That described the customer-analytics product. Cranium v1 = **the open team-context layer for AI-amplified engineering teams**: point at a GitHub org → a living domain/ownership/SME map + an ADR/decision index → three surfaces. See [[architecture-pivot]] (2026-05-13) for the full pivot record and the locked decisions [[ADR-011]]–[[ADR-021]].

---

## Layer model (v1)

Three layers, one direction of flow:

```
GitHub org (read-only PAT scan; GitHub App for the PR bot only)
        │
        ▼
┌─ SOURCE LAYER ───────────────────────────────────────────────┐
│  Deterministic parsers turn raw GitHub artifacts into rows:   │
│    repo tree   → File   source_entities                       │
│    commit log  → Commit source_entities                       │
│    PR list     → PR     source_entities                       │
│    /docs/decisions/*.md → ADR source_entities                 │
│  + cheap deterministic page links:                            │
│    PR→files, commit→files, file→Domain page (by directory),   │
│    commit-author-email→Person page                            │
│  No LLM here. content_hash + skip-if-unchanged.               │
│  Every run logs parsed / skipped / synthesis_failed counts.   │
└──────────────────────────────┬───────────────────────────────┘
        new_source_events_queue │ (ShedLock + SKIP LOCKED dispatcher,
        dedup — enrichment.embed │  pattern; batched, coalesced by page)
        ▼
┌─ SYNTHESIS LAYER ────────────────────────────────────────────┐
│  Temporal workflow (isolated task queue), per batch:          │
│    a. read batch of source entities + context                 │
│    b. extract atomic insights                                 │
│    c. page resolution per insight:                            │
│         deterministic key → fuzzy candidate-find + score →    │
│         auto-link above threshold OR decision_queue suggestion│
│    d. emit page updates (frontmatter, prose, links) + new pages
│    e. coalesce by affected page; mark touched pages re-embed  │
│  Writes targeted columns only: body, aggregations,            │
│  generated_at, content_hash. Never a whole-row replace.       │
│  Fan-out: PR → Domain hub(s) + Person hub + Decision hub,     │
│  content-hash cycle-break (last_fanout_trigger_hash).         │
└──────────────────────────────┬───────────────────────────────┘
        ▼                       ▼                  ▼
┌─ SURFACES ───────────────────────────────────────────────────┐
│  Web graph   — d3/cytoscape/sigma; nodes actionable on click; │
│                decision queue alongside; re-scan on demand    │
│  Page reader — apps/client: entity/[key] → page reader,       │
│                entity/ → page list, .../settings → settings   │
│  MCP server  — module inside core/: who_owns / decisions_for /│
│                recent_changes / why_context (read-only)       │
│  PR bot      — GitHub App: non-blocking comment names owner + │
│                auto-requests reviewer; ADR-referencing file → │
│                "why" prompt → reply auto-drafts an ADR PR     │
└──────────────────────────────────────────────────────────────┘
```

The synthesis LLM call **never** sits on a request path. Pages materialize async during ingestion; navigating to a missing/very-stale page enqueues a high-priority refresh and serves the cached row (or a `_(generating…)_` placeholder) immediately. See [[ADR-017]].

---

## Data model — the `pages` family

One table family. No `entity_synthesis*` tables, no normalized `page_fields` table, no relationship-definition machinery. See [[ADR-018]].

### `pages` — the node AND the rendered article
| Column | Type | Notes |
|---|---|---|
| `id`, `workspace_id` | uuid | RLS-scoped |
| `kind` | enum | backed by a Kotlin sealed `Page` subclass — see [[page-kinds]] |
| `title`, `slug` | text | user-editable; off-limits to the synthesis writer |
| `body` | markdown | the rendered synthesis article (the wiki page) |
| `frontmatter` | jsonb | per-kind shape declared in code; extra extracted keys stored, not validated. **GIN(`jsonb_path_ops`)** |
| `aggregations` | jsonb | contract-derived + windowed rollups. **GIN** |
| `source_type` | enum | `USER \| DERIVED \| SOURCE` |
| `classification` | enum | engineering taxonomy (reworked `SemanticGroup`/`LifecycleDomain`/`entity_connotation`) |
| `stale` | bool | backlog dashboard signal |
| `confidence` | float | de-emphasis UI signal (E3) |
| `content_hash`, `schema_version` | text/int | trace + dirty tracking |
| `last_fanout_trigger_hash` | text | fan-out cycle-break |
| `generated_at`, `created_at`, `updated_at`, `deleted` | ts/bool | |

### `page_history` — append-on-change
PK `(page_id, generated_at, schema_version, content_hash)`; index `(page_id, generated_at DESC)`. Powers `?at=` / `?since=` time-travel reads. Stores `body`, `frontmatter`, `aggregations`, `sourceFacts`, narrative-section snapshot.

### `page_links` — the ONLY relationship table
| Column | Notes |
|---|---|
| `workspace_id`, `source_page_id`, `target_page_id` | |
| `label` | nullable enum: `contains` / `depends_on` / `owns` / `defines` / `references` / `mentions` |
| `weight` | nullable float — ranked overview sections |
| `source_entity_id` | nullable FK → `source_entities.id` — the source artifact that produced this link |
| `created_at`, `deleted` | |

Backlinks = a reverse query: `SELECT … WHERE target_page_id = :id`. Overview grouping = `JOIN source_entities ON source_entity_id … GROUP BY source_kind`. No cardinality, no semantic constraints, no polymorphic `target_kind`, no auto inverse rows. See [[ADR-012]]. Index: `page_links(target_page_id) WHERE deleted = false`.

### `source_entities` — Layer 0 raw artifacts
`id`, `workspace_id`, `source_kind` (`github_pr | commit | file | slack_thread | notion_page | adr`), `source_external_id`, `author_ref`, `source_timestamp`, `raw` (jsonb), `parsed_at`, `content_hash` (skip-if-unchanged).

### Supporting tables
- `page_embeddings` — `vector(1536)` + **HNSW cosine** index; row carries `page_id` + `content_hash`.
- `page_resolution_suggestions` — reuses the identity-match suggestion-row shape; feeds the decision queue.
- `page_merge_candidates` — reuses the identity-cluster shape.
- `decision_queue` — the office-hours surface. v1 suggestion types: `promote-to-Decision`, `assign/confirm-SME`, `stale-ADR`, `ambiguous-resolution`.
- `new_source_events_queue` — ShedLock + SKIP LOCKED dispatcher; feeds the synthesis Temporal workflow.
- `scan_runs` — per-run `parsed` / `skipped` / `synthesis_failed` counts + per-skip-reason rows. Closes the three "silent failure" risks (source-skip count, synthesis-failure count, scan-status honesty).

---

## Page kinds

v1 ships **7** kinds, each a Kotlin **sealed `Page` subclass**: `DomainPage`, `PersonPage`, `FilePage`, `DecisionPage`, `ADRPage`, `SystemPage`, `FlowPage`. Detail in [[page-kinds]].

Each subclass owns:
- **frontmatter shape** — the per-kind schema (the sealed object is the schema; jsonb is the storage).
- **`synthesisContract`** — `aggregations` + `windows` (7d/30d/90d) + `narrative sections` + `narrativeGenerator: TEMPLATED | LLM` + `bodyTokenBudget`.
- **per-kind helpers** — resolution-key derivation, link-emit rules, render hooks.

The Temporal Synthesis Layer's `riven.core.aggregation` engine + `NarrativeArcGenerator` (Templated default, $0; LLM opt-in per kind) consume the `synthesisContract` off the sealed object — no JSON manifest.

**Adding a kind** = a new sealed subclass (kind enum value + frontmatter shape + synthesis contract) **+** a migration if `classification` / `aggregations` shape changes **+** a re-render of existing rows of adjacent kinds if their contract changes. Not free — document the cost.

`FlowPage` is derived from **PR-cluster co-change only** (files that change together in PRs) — **not** from the call graph. It ships disabled if it turns out untrustworthy on the cranium dogfood.

`feature` / `interaction` / `sop` / `insight` kinds are explicitly **out of v1**.

---

## Locked invariants

1. **Targeted synthesis writes only.** The synthesis writer updates `body`, `aggregations`, `generated_at`, `content_hash` — never a whole-row replace. User-editable columns (`frontmatter` on `USER` pages, `title`, `slug`) are off-limits to it. (Lost-update guard for the one-table model.)
2. **No synthesis-LLM output bypasses the resolution-threshold / decision-queue gate.** (Prompt-injection-on-synth-content guard.)
3. **Page resolution keeps the human gate.** Ambiguous → `decision_queue`; never a silent LLM auto-merge. Decision pages: ADR-backed → keys on the ADR file path; no backing file → fuzzy-only, never auto-created, always via the decision queue.
4. **The auto-link threshold is config, not a constant.** The ≥85%-precision eval suite ships *before* the resolution policy and is a **CI gate**.
5. **Phase 1's delete list is absolute** — nothing "kept just in case".
6. **GitHub auth split** — read-only PAT for the scan; GitHub App **only** for the PR bot.

---

## What's reused vs. rebuilt

| Component | Verdict | Note |
|---|---|---|
| `entities` / `entity_attributes` / `entity_relationships` tables | **reworked → keep** | Rename → `pages` / `pages.frontmatter` (jsonb) / `page_links`. Storage spine already right-shaped. |
| Embeddings table + HNSW cosine index + pluggable embedding provider | **keep** | v1 needs semantic search; works. Re-embed once on the taxonomy rework. |
| Auth / workspace / RLS / JWT layer | **keep** | Isolation is done. |
| Temporal infra + workflow wiring + isolated task queues (`workflows.default`, `identity.match`, `enrichment.embed`) | **keep** | Synthesis + page-resolution pipelines reuse this shape. |
| Activity log, websocket/STOMP | **keep** | Generic, works. |
| Identity-match workflow + dispatcher + suggestion queue + identity clusters | **keep bones, rework policy** | New matching policy for page resolution ([[ADR-015]]). Entity-dedup → entity-linking; same harness, new scoring features. |
| `SemanticGroup` / `LifecycleDomain` / `entity_connotation` semantic-envelope machinery | **rework, NOT delete** | Repurpose as the engineering-content classification axis (domain / system / decision / contract / incident / person …). Drop the customer-journey enums; keep storage + JSONB-path query machinery. |
| Source-entity ↔ projected-page two-layer structure | **keep** | Source entities = Karpathy "raw source of truth"; synthesis pages = the LLM-maintained wiki. Two-layer split survives; the projection *mechanism* changes ([[ADR-014]]). |
| `RelationshipDefinitionEntity` + cardinality + semantic constraints + polymorphic `target_kind` + inverse-REFERENCE auto-rows + two-pass impact confirmation + `QueryDirection` threading | **delete** | Collapse to `page_links` + label enum ([[ADR-012]]). |
| `EntityValidationService` JSON-schema-per-type + `detectSchemaBreakingChanges()` + impact-confirmation flow | **delete** | No user types ⇒ nothing to break-change-detect ([[ADR-013]]). |
| Entity-type CRUD endpoints (`POST/PUT/DELETE /api/v1/entity/schema/*`), `EntityTypeRole` 3-plane split | **delete** | Page kinds are a fixed enum in code; add one via PR. |
| `EntityProjectionService` + `ProjectionRuleEntity` + `(LifecycleDomain, SemanticGroup)` matcher + `TemplateMaterializationService` | **replace** | → the [[ADR-014]] LLM synthesis workflow. |
| Manifest/catalog engine (`models/`, `templates/`, `integrations/`, `bundles/`, `ManifestLoaderService`, dual Kotlin+JSON pipelines, `ManifestUpsertService`) | **delete for v1** | GitHub-only v1 = one hardcoded scanner. Page-kind definitions live in Kotlin. |
| Nango integration sync (3-pass Temporal workflow, `SchemaMappingService`, webhook HMAC, sync-state health) | **delete for v1** | Slack/Notion are v1.1. Keep the HMAC-webhook-validation pattern in git history / one wiki page — v1.1 connectors will want it. |
| Workflow engine (DAG nodes, triggers, action nodes, conditions) | **out of scope, leave dormant** | Not in the v1 surface; don't delete, don't extend. Revisit when "agentic skills" land. |
| File pipeline (storage providers, HMAC download tokens, magic-byte validation) | **leave** | Not in the v1 surface; harmless to keep. |

---

## Repo ↔ runtime mapping

| Path | Runtime | Contents |
|---|---|---|
| `core/` | Kotlin / Spring backend | The whole backend: source parsers, synthesis Temporal workflow, page-resolution policy, the `pages`/`page_links` repos + REST API, **and the MCP server module** (read-only query facade — not a separate TS service; see [[ADR-020]]). |
| `apps/client` | Next.js app | Page reader / page list / page settings (the renamed `entity/[key]` routes), auth, dashboard shell; later the web graph + decision queue dock in here. |
| `apps/web` | Next.js marketing site | The landing/marketing site. **Untouched** by the pivot. |
| Postgres | infra | The `pages` family + RLS + ShedLock. |
| Temporal | infra | Synthesis + page-resolution + (dormant) workflow-engine queues. |

OSS self-host = `core` + `apps/client` + Postgres + Temporal in one `docker-compose` bundle. The MCP server is in `core`, so no extra process.

---

## Related
- [[page-kinds]] — per-kind detail
- [[architecture-pivot]] — the pivot plan + locked decisions ([[ADR-011]]–[[ADR-021]])
- [[domains/Pages & Links Core/Pages & Links Core|Pages & Links Core]] · [[domains/Source Ingestion/Source Ingestion|Source Ingestion]] · [[domains/Synthesis/Synthesis|Synthesis]] · [[domains/Page Resolution & Decision Queue/Page Resolution & Decision Queue|Page Resolution & Decision Queue]] · [[domains/Surfaces/Surfaces|Surfaces]] · [[domains/MCP Server/MCP Server|MCP Server]] · [[domains/OSS Packaging & Self-Host/OSS Packaging & Self-Host|OSS Packaging & Self-Host]] · [[domains/Workspace & Auth/Workspace & Auth|Workspace & Auth]]
- `../flows/`, `../designs/`, `../decisions/`
