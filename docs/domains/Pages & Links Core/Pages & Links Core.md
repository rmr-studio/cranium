---
tags:
  - architecture/domain
  - cranium/v1
Created: 2026-05-13
---
# Domain: Pages & Links Core

---

## Overview

The storage spine of Cranium v1. Owns the `pages` family — the table that is *both* the node and the rendered wiki article, the only relationship table (`page_links`), the append-on-change history, and the in-code sealed-class registry that gives each `kind` its frontmatter shape + synthesis contract. Every other domain reads and writes through this layer. Renamed and collapsed out of the old `entities` / `entity_attributes` / `entity_relationships` machinery — see [[architecture-pivot]] §Reuse/Rework/Replace/Delete and [[overview]].

---

## Boundaries

### This Domain Owns

- The `pages`, `page_history`, `page_links`, `page_embeddings` tables and their JPA entities + repositories.
- The `page_kind` enum and the Kotlin `sealed class Page` hierarchy (the 7 v1 subclasses — see [[page-kinds]]).
- Page CRUD (create / read / update / soft-delete) and the read API contract `apps/client` and the MCP server consume.
- Backlink reverse-query and time-travel (`?at=` / `?since=`) reads.
- The `GIN(jsonb_path_ops)` indexes on `frontmatter` / `aggregations`; the HNSW cosine index on `page_embeddings`; the `page_links(target_page_id) WHERE deleted = false` index.

### This Domain Does NOT Own

- *Writing* synthesis content (`body`, `aggregations`) — that's [[domains/Synthesis/Synthesis|Synthesis]], which uses targeted column updates only (invariant 1).
- Creating `source_entities` or deterministic links — that's [[domains/Source Ingestion/Source Ingestion|Source Ingestion]].
- Deciding *which* page an insight resolves to — that's [[domains/Page Resolution & Decision Queue/Page Resolution & Decision Queue|Page Resolution & Decision Queue]].
- Rendering the graph / reader UI — that's [[domains/Surfaces/Surfaces|Surfaces]].

---

## Sub-Domains

| Component | Purpose |
| --------- | ------- |
| Page CRUD service | create / read / update / soft-delete; enforces user-editable-column ownership |
| Page-kind sealed-class registry | per-kind frontmatter shape + `synthesisContract` + helpers |
| Backlink query service | reverse query over `page_links` |
| Page history / time-travel | `?at=` / `?since=` reads off `page_history` |
| `page_links` service | link upsert/soft-delete; label enum + weight + `source_entity_id` |

---

## Flows

| Flow | Type | Description |
| ---- | ---- | ----------- |
| [[Flow - Create Page]] _(stub — flesh out during Phase 1a)_ | User-facing | create page → attach frontmatter → link two pages → query backlinks (the `knowledge-entity-graduation` reader flows, asserted as the Phase 1 positive exit) |
| [[Flow - Time-Travel Read]] _(stub — Phase 1a)_ | Background | `?at=` / `?since=` against `page_history` |

---

## Data

### Owned Entities

| Entity | Purpose | Key Fields |
|---|---|---|
| `Page` | the node + the rendered article | `kind`, `title`, `slug`, `body`, `frontmatter` (jsonb), `aggregations` (jsonb), `source_type`, `classification`, `stale`, `confidence`, `content_hash`, `schema_version`, `last_fanout_trigger_hash`, `generated_at` |
| `PageLink` | the ONLY relationship | `source_page_id`, `target_page_id`, `label` (enum, nullable), `weight` (float, nullable), `source_entity_id` (FK, nullable), `deleted` |
| `PageHistory` | append-on-change | PK `(page_id, generated_at, schema_version, content_hash)` |
| `PageEmbedding` | semantic search | `vector(1536)` + `page_id` + `content_hash` |

### Database Tables

| Table | Entity | Notes |
|---|---|---|
| `pages` | `Page` | GIN on `frontmatter` + `aggregations`; renamed from `entities` (Phase 1a) |
| `page_links` | `PageLink` | renamed + collapsed from `entity_relationships` (Phase 1b); index `(target_page_id) WHERE deleted = false` |
| `page_history` | `PageHistory` | renamed from `entity_synthesis_history`; index `(page_id, generated_at DESC)` |
| `page_embeddings` | `PageEmbedding` | unchanged from `entity_embeddings`; HNSW cosine |

---

## External Dependencies

| Dependency | Purpose | Failure Impact |
|---|---|---|
| PostgreSQL (+ pgvector, RLS) | all storage | hard down — nothing works |

---

## Domain Interactions

### Depends On

| Domain | What We Need | How We Access |
|---|---|---|
| [[domains/Workspace & Auth/Workspace & Auth|Workspace & Auth]] | `workspace_id` scoping, RLS, JWT context | Direct |

### Consumed By

| Domain | What They Need | How They Access |
|---|---|---|
| [[domains/Synthesis/Synthesis|Synthesis]] | targeted writes to `body`/`aggregations`/`generated_at`/`content_hash`; new-page creation | Direct (repo) |
| [[domains/Source Ingestion/Source Ingestion|Source Ingestion]] | create deterministic `page_links` | Direct (repo) |
| [[domains/Page Resolution & Decision Queue/Page Resolution & Decision Queue|Page Resolution & Decision Queue]] | candidate-find over `page_embeddings`; key lookups | Direct (repo) |
| [[domains/Surfaces/Surfaces|Surfaces]] | page + backlink read API; graph edges = `page_links` | API |
| [[domains/MCP Server/MCP Server|MCP Server]] | `pages` + `page_links` repos + compact `body` projection | Direct (in-process, `core/`) |

---

## Key Decisions

| Decision | Summary |
|---|---|
| [[ADR-012]] | `page_links` as the only edge model — label enum, weight, `source_entity_id`; backlinks are reverse queries; no relationship definitions / cardinality / polymorphic `target_kind` / auto inverse rows |
| [[ADR-013]] | frontmatter as thin per-kind shape declared in code; extras stored not validated; jsonb-only (no normalized `page_fields`) |
| [[ADR-018]] | one `pages` table family — `pages.body` is the rendered article; `page_history` is the append-on-change log; no `entity_synthesis*` tables |
| [[ADR-016]] | phasing — Phase 1a pure rename, 1b relationship collapse, 1c taxonomy rework |

---

## Technical Debt

| Issue | Impact | Effort |
|---|---|---|
| Native-SQL grep after the 1a rename (`target_entity_id`→`target_id` scar) | High if missed | Low — a checklist |
| `pages.confidence` field exists; the de-emphasis UI is Phase 3 | Med | Med |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-05-13 | Domain created from the architecture pivot — replaces the old `entities` engine | [[architecture-pivot]] |
