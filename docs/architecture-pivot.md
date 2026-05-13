---
type: resource
Created: 2026-05-13
Updated: 2026-05-13
tags:
  - architecture/decision
  - architecture/pivot
---

# Cranium v1 — Architecture Pivot (pages / links / LLM-synthesis)

> The canonical in-repo record of the 2026-05 architecture pivot. This is the flattened, repo-resident version of the gstack eng-review spec (`~/.gstack/projects/Cranium/jared-unknown-eng-review-architecture-20260512-193441.md`) plus its CEO, design-review, and re-review layers. The locked decisions are also captured one-per-ADR in [[docs/decisions/README]] (ADR-011 … ADR-021).

---

## Why

`~/dev/cranium` was built as a **generic typed-entity engine**: user-creatable `entity_types` with a 3-plane `EntityTypeRole`, per-type JSON schemas + breaking-change detection, a per-type-pair `RelationshipDefinition` system (cardinality, semantic constraints, bidirectional/origin/reference flags, polymorphic `target_kind`, auto inverse-REFERENCE rows), a deterministic projection-rule engine matching `(LifecycleDomain, SemanticGroup)`, a multi-source manifest/catalog engine, and a Nango integration sync. That was the right shape for the original customer-analytics product.

The v1 product is **a wiki of LLM-synthesized pages over an engineering codebase** — Karpathy's LLM-wiki pattern scaled to a team. Point Cranium at a GitHub org → a living domain/ownership/SME map + an ADR/decision index. That wants different machinery: a flat node-and-edge store, fixed page kinds in code, and an LLM synthesis pipeline — not a user-configurable type system. `knowledge-entity-graduation` already started the move (Notes/Glossary collapsed into a unified `entities` table). This pivot finishes it: rescope the generic engine into the synthesis wiki, keeping the storage spine and the infra, gutting the type/projection/routing/catalog/integration-sync machinery.

The pivot is a deliberate founder override of every advisor (office-hours, eng review, outside voice all recommended building the wedge on the current `entities` layer first and pivoting later). See [[#Locked decisions]] D9 and ADR-019, including the bail criterion.

---

## The v1 product

Cranium v1 = **the open team-context layer for AI-amplified engineering teams.** Point it at a GitHub org and in minutes get a living domain/ownership/SME map + an ADR/decision index + coverage gaps, delivered three ways: a **web graph** (understand the system — every node actionable on click), an **MCP server** (`who_owns(path)` / `decisions_for(domain)` / `recent_changes(domain)` / `why_context(diff)` — answer the in-context question without getting blocked), and a **non-blocking PR comment** (name the domain owner + auto-request them as reviewer; if a touched file is referenced by an ADR/Decision, surface the "why" prompt → engineer's reply auto-drafts an attributed ADR PR). Every derived signal — SME suggestion, "this ADR looks stale", "promote this thread to a Decision", ambiguous page-resolution — lands in a **decision/suggestion queue** that PMs and tech leads approve or reject. The queue is the spine that makes the graph a workflow instead of wallpaper.

GTM is open-source-led: ship the artifact, dogfood it publicly on the `~/dev/cranium` repo itself, get design-partner teams self-hosting, then upsell the paid tier (hosting + compliance: SSO/SAML/SCIM, audit log, retention, residency + org/team governance: org-scale cross-team graph, RBAC visibility — which is also the "this isn't surveillance" guarantee). v1 = **GitHub only**; Slack + Notion/Confluence are v1.1 enrichment that make the map *trustworthy* rather than *plausible*. v1 MCP = the **read side only** — MCP-as-router and "planning infrastructure" are out of v1. PR review is **reframe-not-gate**: warn, surface the "why", draft the ADR, attribute it, queue for architect review — never block.

---

## Target data model (D10 = B — one `pages` family)

One `pages` family. No `entity_synthesis*` tables, no `page_fields` normalized table, no relationship-definition machinery.

### `pages` — the node AND the rendered article (one table)

| Column | Notes |
|---|---|
| `id`, `workspace_id` | |
| `kind` | enum, backed by a Kotlin sealed `Page` subclass — `domain \| person \| file \| decision \| adr \| system \| flow` |
| `title`, `slug` | user-editable; off-limits to the synthesis writer |
| `body` (markdown) | the rendered synthesis article (was `pages.body` + `entity_synthesis.narrative`) |
| `frontmatter` (jsonb) | per-kind shape defined by the sealed Kotlin object; extra extracted keys stored, not validated; `GIN(jsonb_path_ops)` index. **jsonb-only from Phase 1a — no transitional `page_fields` table.** |
| `aggregations` (jsonb) | synthesis-contract-derived + windowed (was `entity_synthesis.aggregations`); GIN index |
| `source_type` | `USER \| DERIVED \| SOURCE` |
| `classification` | engineering taxonomy — the reworked `SemanticGroup`/`LifecycleDomain`/`entity_connotation` machinery |
| `confidence` (float) | populated from count/strength of backing `sourceFacts`; drives web-graph de-emphasis + decision-queue ordering (E3) |
| `stale` (bool), `content_hash`, `schema_version`, `last_fanout_trigger_hash` | freshness + cycle-break (`last_fanout_trigger_hash` breaks A→B→A fan-out) |
| `generated_at`, `created_at`, `updated_at`, `deleted` | |

**Synthesis write-path invariant:** targeted column updates only — `body`, `aggregations`, `generated_at`, `content_hash`. Never a whole-row replace. `frontmatter` on USER pages, `title`, `slug` are off-limits to the synthesis writer (lost-update guard for the one-table model).

### `page_history` — append-on-change (was `entity_synthesis_history`)

- PK `(page_id, generated_at, schema_version, content_hash)`; INDEX `(page_id, generated_at DESC)` for `?at=` / `?since=` time-travel.
- Columns: `body`, `frontmatter`, `aggregations`, `sourceFacts`, narrative-sections snapshot.

### `page_links` — the ONLY relationship table (D2)

| Column | Notes |
|---|---|
| `workspace_id`, `source_page_id`, `target_page_id` | |
| `label` (enum, nullable) | small in-code enum: `contains \| depends_on \| owns \| defines \| references \| mentions` |
| `weight` (float, nullable) | for ranked overview sections |
| `source_entity_id` (nullable, FK → `source_entities.id`) | the source artifact that produced this link |
| `created_at`, `deleted` | |

- Backlinks: `SELECT ... WHERE target_page_id = :id`. Overview grouping: `JOIN source_entities ON source_entity_id ... GROUP BY source_kind`. No cardinality, no semantic constraints, no polymorphic `target_kind`, no auto inverse rows.
- Index: `page_links(target_page_id) WHERE deleted = false`.

### `source_entities` — Layer 0: raw ingested artifacts

`id`, `workspace_id`, `source_kind` (`github_pr \| commit \| file \| slack_thread \| notion_page \| adr`), `source_external_id`, `author_ref`, `source_timestamp`, `raw` (jsonb), `parsed_at`, `content_hash` (skip re-emit when unchanged — E1).

### Remaining tables

| Table | Notes |
|---|---|
| `page_embeddings` | unchanged: `vector(1536)` + HNSW cosine; row carries `page_id` + `content_hash` for trace |
| `page_resolution_suggestions` | reuse the identity-match suggestion-row shape; feeds the decision queue |
| `page_merge_candidates` | reuse the identity-cluster shape |
| `decision_queue` | SME-assign / promote-to-Decision / stale-ADR / ambiguous-resolution |
| `new_source_events_queue` | ShedLock + `SKIP LOCKED` dispatcher; feeds the synthesis Temporal workflow (reuses the `enrichment.embed` pattern) |
| `scan_runs` | per-run `parsed_count` / `skipped_count` / `synthesis_failed_count` + `status` + per-skip-reason rows — closes the three silent-failure risks; "scan complete" is never allowed to lie |

---

## Page kinds (v1 = 7, locked)

v1 page kinds: **`Domain`, `Person`, `File`, `Decision`, `ADR`, `System`, `Flow`.** (`feature`, `interaction`, `sop`, `insight` from the Wiki/Cranium taxonomy are NOT in v1 — nothing auto-synthesizes them yet; add when Cranium ingests hand-written docs.) `System` ≠ `Domain`: a deployable unit vs a code area. `Flow` for v1 is derived from **PR-cluster co-change only** (NOT static call-graph analysis — that would produce garbage pages); if even the PR-cluster version isn't trustworthy on the cranium dogfood, `Flow` ships disabled.

Each kind is a **Kotlin sealed `Page` subclass** owning:

- **`frontmatterSchema`** — the thin per-kind frontmatter shape (Domain keys on directory path, Person on git commit-author-email, ADR on the ADR file path, …). Extra extracted keys stored in `pages.frontmatter`, not validated.
- **`synthesisContract`** — `aggregations` (which rollups: SUM/AVG/COUNT/RATIO/P50/P90/TOP_K/LATEST/ENUM_RULE/HISTOGRAM/SERIES — reused from the Temporal Synthesis Layer's `riven.core.aggregation` engine) + `windows` (7d/30d/90d + `WindowDeltaSet`) + narrative sections + `narrativeGenerator: TEMPLATED | LLM` (Templated default, $0; LLM opt-in per kind, Haiku + prompt caching) + `bodyTokenBudget` + truncation policy (E6 — MCP `why_context(diff)` injects into an IDE agent's context window, so the budget is a synthesis-contract decision).
- **per-kind helpers** — what the page represents, how it rolls up (File rolls up into Domain like the old Order→Customer rollup), what arc kinds it wants (engineering arcs: `OWNERSHIP_DRIFT`, `ADR_STALENESS`, `REVIEW_LOAD`, plus reusable `VOLUME` / `THEME_EVOLUTION` / `RECENCY` / `ANOMALY` / …).

**Adding a new kind** = a new sealed subclass (defines the `kind` enum value + frontmatter shape + synthesis contract + helpers) + a migration if the `classification`/`aggregations` shape changes + a re-render of existing rows of adjacent kinds if the shared contract changes. This cost is not free; it is paid via PR (like a `CoreModelDefinition`), not via a runtime registration API — compile-time safety beats contribution friction for rarely-changing core defs. An OSS self-hoster who wants a new kind submits a PR.

---

## Reuse / Rework / Replace / Delete

| Layer | Verdict | Notes |
|---|---|---|
| `entities` / `entity_attributes` / `entity_relationships` tables | **Reuse** | Rename → `pages` / `pages.frontmatter` (jsonb) / `page_links`. Storage spine is already right-shaped. |
| Embeddings table + HNSW cosine index + pluggable embedding provider | **Reuse** | v1 needs semantic search; works. |
| Auth / workspace / RLS / JWT layer | **Reuse** | Isolation is done. |
| Temporal infra + workflow wiring + isolated task queues (`workflows.default`, `identity.match`, `enrichment.embed`) | **Reuse** | Synthesis + page-resolution pipelines reuse this shape. |
| Activity log, websocket/STOMP | **Reuse** | Generic, works. |
| Identity-match workflow + dispatcher + suggestion queue + identity clusters | **Reuse bones, rework policy** | New matching policy for page resolution (D5). Entity-dedup → entity-linking; same harness, new scoring features. |
| `SemanticGroup` / `LifecycleDomain` / `entity_connotation` semantic-envelope machinery | **Rework, NOT delete** | Repurpose as the engineering-content classification axis (domain / system / decision / contract / incident / person, …) so the synthesis LLM can route free text. Drop the customer-journey enums; keep the storage + JSONB-path-query machinery. |
| Source-entity ↔ projected-page two-layer structure | **Reuse** | Source entities (PRs, commits, files, Slack threads, Notion pages, ADRs) = Karpathy "raw source of truth"; synthesis pages = the LLM-maintained wiki. The two-layer split survives; the projection *mechanism* changes (D4). |
| `RelationshipDefinitionEntity` + cardinality + semantic constraints + polymorphic `target_kind` + inverse-REFERENCE auto-rows + two-pass impact confirmation + `QueryDirection` threading | **Delete** | Collapse to `page_links` + label enum (D2). |
| `EntityValidationService` JSON-schema-per-type + `detectSchemaBreakingChanges()` + impact-confirmation flow | **Delete** | No user types ⇒ nothing to break-change-detect (D3). |
| Entity-type CRUD endpoints (`POST/PUT/DELETE /api/v1/entity/schema/*`), `EntityTypeRole` 3-plane split | **Delete** | Page kinds are a fixed enum in code; add one via PR. |
| `EntityProjectionService` + `ProjectionRuleEntity` + `(LifecycleDomain, SemanticGroup)` matcher + `TemplateMaterializationService` | **Replace** | → the D4 LLM synthesis workflow (adapted from the Temporal Synthesis Layer). |
| Manifest/catalog engine (`models/`, `templates/`, `integrations/`, `bundles/`, `ManifestLoaderService`, dual Kotlin+JSON pipelines, `ManifestUpsertService` hash-idempotency) | **Delete for v1** | GitHub-only v1 = one hardcoded scanner. Page-kind definitions live in Kotlin. |
| Nango integration sync (3-pass Temporal workflow, `SchemaMappingService`, webhook HMAC, sync-state health) | **Delete for v1** | Slack/Notion are v1.1. Preserve the HMAC-webhook-validation pattern (git history or one [[Wiki/Cranium]] page) — the v1.1 connectors will want it. |
| Workflow engine (DAG nodes, triggers, action nodes, conditions) | **Out of scope, leave dormant** | Not in the v1 surface; don't delete, don't extend. Revisit when "agentic skills" land. |
| File pipeline (storage providers, HMAC download tokens, magic-byte validation) | **Reuse (as-is)** | Not in the v1 surface; harmless to keep. ADR-005/006/007/009 stay in force. |

---

## Migration sequence

### Preconditions (both hard-gated; separate worktrees; do not let Phase 1 touch a table name until both are merged to `main`)

1. **enrichment-service PR2 Phase 3 complete + merged to `main`** — wires `EntityKnowledgeViewProjector`, deletes `SemanticTextBuilderService`, swaps to the structural snapshot, all against *current* table/enum names. An explicit prerequisite milestone with its own owner + done-criteria; the 3 in-flight worktrees (`enrichment-service`, `enrichment-service-2`, `enrichment-pipeline`) get consolidated/abandoned as part of landing it. If consolidating the worktrees isn't bounded in ~1–2 weeks, that's a signal to revisit — not push through.
2. **entity-connotation-pipeline plan complete + merged to `main`** (`~/.gstack/projects/rmr-studio-riven/ceo-plans/2026-04-18-entity-connotation-pipeline.md`) — the `entity_connotation` envelope (SENTIMENT/RELATIONAL/STRUCTURAL axes, Layer-4 predicate queries, lazy backfill) is already partly merged; Phase 1c reworks it, so it must settle first.

### Phase 1 — structural collapse (mechanical, no behavior change in 1a; gated behavior changes in 1b/1c)

- **Phase 1a — pure rename (suite stays green).** `entities`→`pages`, `entity_attributes`→folded into `pages.frontmatter` jsonb, `entity_relationships`→`page_links`. Introduce the `page_kind` enum + the sealed `Page` hierarchy + the per-kind frontmatter-shape registry; add `pages.confidence` + `last_fanout_trigger_hash` + `schema_version` columns; declare `bodyTokenBudget` on the contract. Grep **every** native SQL query (the `target_entity_id`→`target_id` scar — JPA mapping changed silently, native queries needed manual fix-up; this is a checklist, not a parenthetical). Suite green except tests of about-to-be-deleted machinery (don't delete those yet).
- **Phase 1b — relationship collapse + backlink rework (BEHAVIOR CHANGE — GATED).** Add `label` (nullable enum) + `weight` (nullable float) + `source_entity_id` to `page_links`; drop `target_kind`, `target_parent_id`, `link_source`, the relationship-definition FK. Backlinks change from auto-inverse-REFERENCE rows to reverse queries. **GATE: a backlink-parity IT — the reverse-query result must equal what the old inverse-REFERENCE rows returned, across the 6-case `EnrichmentBacklinkMatrixIT` matrix — must pass before 1b merges.** Then delete `RelationshipDefinitionEntity`, `EntityRelationshipService` definition logic, `EntityTypeRelationshipService`/`DiffService`, `SemanticGroupValidationService`, `QueryDirection` threading, two-pass impact confirmation, and the now-dead tests.
- **Phase 1c — taxonomy rework + the forced re-embed (BEHAVIOR CHANGE + MIGRATION; kept monolithic per founder choice).** Pre-req: the connotation-pipeline merged (precondition 2) **+ a dev-DB snapshot-restore dry-run** (prove the rollback works — the monolithic rollback unit makes this load-bearing). Rework `SemanticGroup`/`LifecycleDomain`/`entity_connotation` → engineering classification taxonomy (new enum values; keep the storage + JSONB-path machinery). Changing classification triggers full re-enrichment of every page — so 1c ships with a deliberate re-embed over the whole graph (dev-DB-grade: the only data is the dev DB, so `DELETE FROM page_embeddings; re-run` — not migration-grade ceremony; do it on purpose, not lazily — the graph is half-stale until it drains). Also in 1c: delete entity-type CRUD + `EntityValidationService` schema validation + `detectSchemaBreakingChanges()` + `EntityTypeRole`; delete the manifest/catalog engine + dual pipelines + `TemplateMaterializationService` + `ProjectionRuleEntity` + `EntityProjectionService`; delete the non-GitHub Nango sync (preserve the HMAC pattern in git history / a wiki page); rename identity-match suggestion/cluster tables → `page_resolution_suggestions` / `page_merge_candidates` (logic unchanged here; policy rewritten in Phase 2).
- **Phase 1d — `apps/client` collapse (parallel worktree alongside 1b/1c — disjoint: frontend vs backend).** `apps/client` is the dashboard app, NOT `apps/web` (the marketing site). Mirror the backend's 1b/1c split: **1d-rename** the entity reader/list/settings routes (`app/dashboard/workspace/[workspaceId]/entity/[key]/page.tsx`, `entity/page.tsx`, `entity/[key]/settings/page.tsx`) → page reader/list/settings (entity→page, since everything is a page); **1d-delete** the entity-TYPE management screens, relationship-definition editor, manifest/catalog UI, integration-sync UI when their backend dies in 1c. Keep auth + the dashboard shell. Test bar: a CRITICAL regression test that the renamed routes render frontmatter + backlinks against the renamed `pages`/`page_links` API; the `@cranium/client` Jest suite green except deleted-screen tests.

**Phase 1 positive exit assertion** (not just "suite stays green"): the `knowledge-entity-graduation` flows — create page → attach frontmatter → link two pages → query backlinks — work against the renamed schema.

**Bail criterion (D9 — the safety valve on the founder override):** if Phase 1 (1a+1b+1c) isn't *done and green* by ~week 3 of Claude Code time, **stop and reassess.** That's the signal the foundation-first bet is costing more than the clean-foundation conviction priced in, and the wedge-first path (build the GitHub-scan wedge on the current `entities` layer first) is back on the table. Not a re-litigation of D9 — the bail criterion the D9 choice should have come with.

### Phase 2 — the GitHub-scan + synthesis pipeline (the real engineering)

- **Stage 1 — deterministic source parse:** clone/list tree → File `source_entities`; commit log → Commit `source_entities`; PR list → PR `source_entities`; `/docs/decisions/*.md` → ADR `source_entities` (this repo's own ADRs are an ingestion source — Cranium dogfoods on `~/dev/cranium`); cheap deterministic links: PR→files, commit→files, thread→participants; deterministic page links: file→directory→Domain page, commit-author-email→Person page. Skip-if-unchanged via `source_entities.content_hash`. Page the scan; throttle the synthesis backlog drain; write `scan_runs` status rows.
- **Stage 2 — LLM synthesis (Temporal workflow, isolated queue, batched + coalesced over `new_source_events_queue`):** batch = time-windowed (~5 min) + size cap (~50 source-events), whichever fires first, coalesced by likely-affected page. Read batch + context → extract atomic insights → page resolution per insight → emit page updates (frontmatter, prose, links) + new pages → mark touched pages for re-embed. Adapt the Temporal Synthesis Layer for engineering: its machinery (`riven.core.aggregation`, narrative arcs, fan-out, dirty tracking, history/windows, compact/full projections, cost controls — Haiku + prompt caching, per-section `inputs_hash`) is domain-agnostic and reused as-is, consuming the per-kind Kotlin `synthesisContract`, writing into `pages`/`page_history` (not `entity_synthesis*`). Fan-out: a new PR → re-synth its source entity → fan out to the Domain hub(s) it touches + the Person hub (author) + any Decision hub the touched files reference; content-hash cycle-break; identity-cluster fan-out priority < direct-trigger priority (sheds load under burst). The fan-out IS the "every shipped change compounds into the next decision" loop.
- **GATE:** the **page-resolution eval suite** ships as a **CI gate with a ≥85%-precision floor** *before* the resolution policy. Golden set: (LLM-extracted concept → expected page or "new"). Cases: exact key match (file→Domain), alias match (`auth`/`authentication`/`authn`), embedding-only match, true-new, ambiguous (two plausible candidates → MUST hit `decision_queue`, MUST NOT auto-link). The auto-link threshold is config, not a code constant. Resolution policy: (1) deterministic key lookup first; (2) fuzzy fallback — name/alias/embedding-similarity candidate-find over `page_embeddings` HNSW + score; (3) auto-link above threshold, else queue a suggestion in the decision queue for a tech lead. Keep the human gate — no silent LLM auto-merge. Decision-page resolution: ADR-backed → keys on the ADR file path; no backing file → fuzzy-only, never auto-created, always via the decision queue. **No synthesis-LLM output bypasses the resolution-threshold / decision-queue gate** (prompt-injection-on-synth-content invariant).
- **Build order:** source parsers (deterministic, testable in isolation) → page-resolution eval suite + policy → the synthesis Temporal workflow → a thin **"why"-prompt spike** (PR comment on the fixture/dogfood repo → engineer reply → an attributed ADR PR drafted + queued — the demoable magic, de-risked early, before the web graph) → MCP server module in `core/` → web graph → PR bot. **GitHub auth:** read-only PAT for the scan; GitHub App only for the PR bot.
- **MCP server = a Kotlin module inside `core/`** (Spring) — a read-only query facade (`who_owns(path)` / `decisions_for(domain)` / `recent_changes(domain)` / `why_context(diff)`) over the `pages`/`page_links` repos + the compact `pages.body` projection, reusing auth/RLS directly. Not a separate TS service. Keeps the OSS self-host "one backend + one frontend + postgres + temporal" and keeps Phase 2's MCP lane independent of the parser lane.
- **Exit criterion:** dogfood-on-cranium — the real `~/dev/cranium` repo is scanned, its Domain/Person/File/Decision/ADR/System/Flow page graph is generated, and the founder uses the read-side MCP server in their own IDE against it. "Tests pass" is not "done."

### Phase 3 — the three surfaces (gated by the "Phase 3 design pass" TODO)

Web graph (node = `pages` row, edges = `page_links`, article = `pages.body`, freshness indicator = "synthesized N source-events ago / M not yet folded in"; monochrome + semantic-only palette — `--warning` = stale, `--edit` = ambiguous-resolution pending, `--destructive` = synthesis-failed; desktop-only — mobile gets the article reader + the decision queue; the article's backlinks list is the a11y-parallel view of the edges); the **decision queue** as an inbox not a dashboard (Linear Triage / GitHub "review requested" pattern, j/k/enter; empty = "you're caught up", a success state; ships SME-assign / promote-to-Decision / **deterministic stale-ADR** (E5) / ambiguous-resolution suggestion types); the page reader; the PR-bot comment copy (GitHub-native: collapsed `<details>`, suggested-reviewer UI, text-first). `pages.confidence` + the freshness indicator are the trust mechanism — load-bearing, not Phase-3.1-cuttable. Gate: data model stable post-Phase-2; full mockup-driven design pass runs at Phase-3 planning time.

### Phase 4 — OSS packaging + release + self-host quickstart

The GTM is open-source-led; v1 isn't distributable without this, so it's a phase, not a TODO. Foundation: the existing `docker-compose.yml` + Dockerfiles (`core`, `apps/client`).

- **GitHub Actions release pipeline** — build + test + publish on a version tag (the OSS package + container images; define target platforms for any published artifact).
- **Self-host bundle** — a hardened `docker-compose` that brings up `core` + `apps/client` + postgres + Temporal (MCP server is in `core` → no extra process). CI: `docker compose up` against the bundle → healthcheck passes ("5-minute to first value", automated).
- **"5-minute to first scan" quickstart** — README path: clone → `docker compose up` → register a read-only GitHub PAT → point at an org → first scan completes within the budget.
- **GitHub App install flow** — registration + the install/permissions flow for the PR bot. Test: mock GitHub webhook → app receives + processes an install + a PR event.
- **Flyway forward-only migrations** — needed before the first external self-hoster (exactly Phase 4's audience).

Not in Phase 4: the hosted/paid tier's deploy pipeline (separate, deferred until a design partner asks).

---

## Locked decisions

| ID | Decision | ADR |
|---|---|---|
| D1 | Reuse the storage spine + infra; gut & rebuild the type/projection/routing/catalog machinery | [[ADR-011 Reuse Storage Spine, Rebuild Type and Projection Machinery]] |
| D2 | Single `page_links` edge table + an optional in-code label enum; backlinks = reverse query | [[ADR-012 Single page_links Edge Table with In-Code Label Enum]] |
| D3 | Thin per-kind frontmatter declared in code; loose extra keys stored not validated | [[ADR-013 Thin Per-Kind Frontmatter in Code, Loose Extras]] |
| D4 | Deterministic source-entity creation + batched LLM synthesis routing | [[ADR-014 Deterministic Source-Entity Creation plus Batched LLM Synthesis]] |
| D5 | Page resolution — deterministic key → fuzzy candidate-find+score → auto-link or decision queue; keep the human gate | [[ADR-015 Page Resolution Policy]] |
| D6 | Structural collapse before behavioral change — phasing rule | [[ADR-016 Structural Collapse Before Behavioral Change]] |
| D7 | Synthesis pages materialized async + non-blocking on-navigation fill | [[ADR-017 Async Synthesis Materialization with On-Navigation Fill]] |
| D10 | One `pages` table family for synthesis storage | [[ADR-018 One pages Table Family for Synthesis Storage]] |
| D9 | Full architecture pivot before securing a design partner — deliberate override of the wedge-first recommendation | [[ADR-019 Full Pivot Before a Design Partner]] |
| ER3 | MCP server as a module inside `core/` (Kotlin/Spring), not a separate service | [[ADR-020 MCP Server as a Module Inside core]] |
| — | GitHub-only v1; Slack/Notion are v1.1 enrichment | [[ADR-021 GitHub-Only v1, Slack and Notion as v1.1]] |

(D8 = the outside-voice pass that produced the Phase-1 split + the PR2-Phase-3 strict gate + the `source_entity_id` rename — folded into D6/D1/D2 rather than its own ADR.)

---

## Documentation homes

| Home | Owns | Rule |
|---|---|---|
| **Repo `docs/`** (this repo) | Architecture, ADRs, design docs, flows. **Canonical.** ADRs in `docs/decisions/*.md` are themselves a Phase-2 ingestion source — Cranium dogfoods on this repo. | Every new architecture/ADR/design/flow doc goes here. |
| **Notion** | Roadmap / phase tracker / business strategy / GTM / brand. | Not architecture. |
| **`~/Documents/wiki/Cranium`** (the Obsidian Karpathy wiki) | Synthesized insight + decision rationale. | Cross-link from here; don't duplicate ADR text. |

Teardown rule: old Notion arch pages get **migrated-or-archived during the teardown, never edited in place.**

---

## Open questions

- **Demand conversion.** All current evidence is interest, not demand — 6 design-partner conversations, zero who have paid, asked for a pilot, or built around a prototype. Before/during the ~5–7 weeks of refactor: convert *one* conversation into a design partner who commits a real repo + 30 min/week. If all six say "interested but not now," that's the most important thing learned this month. (Mitigation isn't in this plan — it's the office-hours assignment; run it in parallel.)
- **The graph's retained-value problem.** Even with actionable nodes + the queue, does a force-directed codebase graph get opened in week 3? Needs a usage hypothesis and a fallback (push the important stuff via PR comment / MCP / a digest — don't rely on people visiting the graph).
- **Drift / parallel-reimplementation detection precision.** Out of v1, load-bearing long-term. What's the precision target, and how is it measured before turning it on?
- **Slack/Notion v1.1 sequencing.** Which one first, and what specifically does it unlock? ("Validate ownership / route hints" — make that concrete.)
- **OSS self-hosting friction / "5 minutes to first value".** A self-hosted thing with a GitHub App + ingestion pipeline + synthesis layer + MCP server + web UI is not a single binary. Does the friction undermine the distribution advantage? What's the concrete "5 minutes to first value" path?
- **Decision-page canonical identity** — resolved for v1 (ADR-backed → ADR file path; no backing file → fuzzy-only, never auto-created, always via the decision queue). Re-confirm holds once the resolution policy is built.
- **`Flow` trustworthiness on the cranium dogfood** — if the PR-cluster-co-change version doesn't produce trustworthy pages, `Flow` ships disabled and stays a stretch kind.
- **Doc-sync debt** — the Temporal Synthesis Layer Notion doc + the enrichment-PR2 "fans out to future consumers (synthesis layer, JSONB projection)" contract need updating to "writes into `pages`/`page_history`, not `entity_synthesis*`".

---

## Related

- Eng-review spec: `~/.gstack/projects/Cranium/jared-unknown-eng-review-architecture-20260512-193441.md`
- Office-hours design doc: `~/.gstack/projects/Cranium/jared-unknown-design-20260512-183238.md`
- Test plan: `~/.gstack/projects/Cranium/jared-unknown-eng-review-test-plan-20260512-211844.md`
- CEO plan: `~/.gstack/projects/Cranium/ceo-plans/2026-05-12-architecture-pivot.md`
- ADRs: [[docs/decisions/README]] (ADR-011 … ADR-021 are the pivot decisions; ADR-001 … ADR-010 are pre-pivot, status updated)
- [[docs/readme]]
