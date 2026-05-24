---
tags:
  - wiki-layer-4
  - wiki-ingestion-pass-4
---

### Overview

**Pass 4** is where the wiki becomes a graph instead of a collection of independent pages. It converges signals from every earlier pass into a typed, materialised **cross-link graph** that connects:

- the same concept across different sources (Slack decision thread ↔ GitHub class ↔ design doc, all about one feature),
- pages within a cluster (intra-domain links),
- pages across clusters (cross-domain bridges),
- pages and the sources they cite (provenance back-references),
- pages and the L1 routing manifest (hub promotion),
- pages that contradict each other (contradiction queue).

This is the **L4 layer** of the engine ([[llm-wiki-engine-architecture]]) and the pass that the literature on knowledge graphs / GraphRAG / HippoRAG treats as the structural backbone of retrieval.

#### Interaction with Provenance Graph

The Pass 0 provenance graph supplies the mechanical edge backbone (URL mentions, PR-cites-issue, file-imports-file). Pass 4 reads it as one of five input signal streams and never modifies it. New edges Pass 4 materialises are written to the **L4 cross-link graph** — a separate structure that may overlap with provenance edges but encodes higher-level semantics (typed `edge_kind`, page-to-page rather than region-to-region).

### Why Pass 4 is necessary (and why earlier passes cannot do it alone)

Cross-domain linking is **distributed across passes by design** — each earlier pass contributes a partial signal that Pass 4 ratifies into a typed edge.

| Pass | Signal contribution | Why this signal alone is insufficient |
|---|---|---|
| Pass 0 | Mechanical URL/identifier edges (Slack msg → GitHub permalink, `Fixes #123` in PR description) | Misses every link expressed in natural language without a URL |
| Pass 1 | `soft_links` candidates from Haiku reading natural-language hints ("the auth class I was talking about") | Suggestions only — Haiku has no global view to confirm |
| Pass 2 | Implicit affinity via cluster membership | Doesn't materialise individual edges; only collective grouping |
| Pass 3 | `[[wikilinks]]` Sonnet writes into page bodies | Page-local view — Sonnet writing one page can't see if `[[auth-refresh]]` and `[[auth-token-refresh]]` are the same concept |

Pass 4 is the first pass with all five signal streams in one place: mechanical edges, soft-link candidates, cluster manifests, wikilinks, and inbound-link counts. The materialisation step is what makes the cross-domain trio — Slack decision + code class + design doc — actually findable as a triangle rather than three disconnected pages.

---

### The Cross-Domain Linking Mechanism

The canonical scenario the engine must handle:

> An engineer types in `#auth-eng` Slack: "we should rotate auth tokens via the new envelope-encryption approach instead of bcrypt." A week later, a PR ships `AuthTokenRotator.kt`. Months later, an SME writes a design doc `auth-token-refresh-design.md` covering the system. All three sources discuss the same underlying decision. Cranium must surface them together when an engineer asks "why do we rotate auth tokens?"

How each pass contributes:

**Pass 0** extracts:
- Slack message → GitHub URL edge (`auth-eng/p1234` mentions `github.com/.../AuthTokenRotator.kt`)
- Design doc → PR link in its frontmatter `referenced_prs:`

**Pass 1** classifies and emits:
- Slack thread label: `kind_candidate=decision`, `domain_candidates=["auth"]`, `soft_links=[{target_slug: "auth-token-rotation", kind: "decides_on", confidence: 0.6}]`
- Code file label: `kind_candidate=feature`, `domain_candidates=["auth"]`, `soft_links=[]`
- Design doc label: `kind_candidate=feature` or `decision`, `soft_links=[{target_slug: "auth-token-rotation", kind: "specifies"}]`

**Pass 2** clusters all three sources into the `auth` cluster manifest as members.

**Pass 3** synthesizes:
- L3 page `slack-auth-token-rotation-decision.md` with body wikilinking to `[[auth-token-rotation]]` (the abstract feature)
- L3 page `auth-token-rotator-module.md` wikilinking to `[[auth-token-rotation]]`
- L3 page `auth-token-refresh-design.md` wikilinking to `[[auth-token-rotation]]`

**Pass 4** sees the convergence:
- Three pages, three independent wikilinks, all pointing at `[[auth-token-rotation]]`
- Pass 1 soft links from Slack thread + design doc corroborate the wikilinks
- Pass 0 mechanical URL edge between Slack and code corroborates the topical relationship
- Verdict: **materialise a unifying L3 feature page** `[[auth-token-rotation]]` if it doesn't exist, then write typed edges:
  - `slack-auth-token-rotation-decision  DECIDES_ON  auth-token-rotation`
  - `auth-token-rotator-module  IMPLEMENTS  auth-token-rotation`
  - `auth-token-refresh-design  SPECIFIES  auth-token-rotation`
- Mark `auth-token-rotation` as a hub (3 typed inbound edges, spans 3 source types).

The triangle is now first-class in the graph. Retrieval can answer "why do we rotate auth tokens?" by surfacing `auth-token-rotation` as the entry point, then the three sources as supporting evidence — instead of returning three loose pages and forcing the reader to reconstruct the triangle themselves.

---

### Inputs

Pass 4 consumes five signal streams. Each is read from its own storage; Pass 4 holds no canonical state of its own beyond the materialised L4 graph.

| Input | Source | Used for |
|---|---|---|
| L0 mechanical edges | Pass 0 provenance graph | High-confidence URL/identifier-based edges (always promoted to L4) |
| `soft_links` candidates | Pass 1 `ClassifierLabel.soft_links` | Soft suggestions awaiting corroboration |
| Cluster manifests | Pass 2 | Cluster boundaries → bridge detection scope |
| Page wikilinks | Pass 3 page bodies | Page-to-page edges and slug resolution |
| Inbound link counts | aggregation across all pages | Hub detection (>10 inbound) |

---

### Algorithm

Pass 4 runs **per cluster**, with cross-cluster bridge handling at the end.

#### Step 1 — Slug resolution

Parse `[[wikilinks]]` from every Pass 3 page body. Normalize slugs (lowercase, trim, plural-stem). Build a slug-to-page index. For each wikilink target:

- **Resolved** — target slug matches an existing page. Edge materialised.
- **Aliased** — target slug matches a known alias (`auth-refresh` → `auth-token-refresh`). Alias table maintained in workspace config + LLM-suggested aliases reviewed by SME.
- **Dangling** — target slug matches no page. Collected for Step 4.

#### Step 2 — Soft-link promotion

For each Pass 1 `soft_link` candidate, check corroboration:

```
promotion_score =
    (1 if any Pass 3 wikilink in the source page matches the candidate target) +
    (1 if a Pass 0 mechanical edge connects the source to the target) +
    (1 if both sources are in the same Pass 2 cluster) +
    (candidate's Haiku confidence × 2)

promote if promotion_score ≥ 2.0
```

Promoted soft links become typed edges. Demoted soft links are discarded (not retained — they were Haiku suggestions, not assertions).

#### Step 3 — Typed edge classification

Every materialised edge gets a typed `edge_kind`. Haiku classifies based on:

- Source kinds at endpoints (Slack thread → code = likely `decides_on` or `discusses`; code → design doc = likely `implements` or `specifies`)
- Wikilink context (the sentence the wikilink appears in)
- Pass 1 labels on both endpoints

Edge kinds (extensible):

| `edge_kind` | Direction | Typical endpoint pair |
|---|---|---|
| `cites` | A → B | page → page (default) |
| `implements` | code → spec | code page → design/spec page |
| `specifies` | spec → impl | design page → code/feature page |
| `decides_on` | discussion → feature | Slack/meeting page → feature page |
| `discusses` | discussion → topic | Slack page → any page |
| `supersedes` | new → old | new ADR/page → old ADR/page |
| `refines` | sub → parent | sub-feature → parent feature |
| `contradicts` | A ↔ B | page ↔ page (symmetric) |
| `quotes` | A → B | page → source region |
| `references` | A → B | weak link (e.g. body mention) |

#### Step 4 — Convergent dangling resolution

Dangling wikilinks are grouped by normalized target slug. When ≥3 pages independently dangle on the same target, Pass 4 flags `[[<target>]]` as a **"create page" candidate** for the synthesis layer. This is how the engine discovers feature concepts that *should* have a page but don't — e.g. the `auth-token-rotation` example above before any page exists for it.

The create-page candidate is written with metadata:
- candidate slug
- declared kind from the wikilink context (e.g. all three references say `[[auth-token-rotation]]` followed by "(feature)")
- corroborating sources
- nominated `kind` for Pass 3 to use when synthesizing the missing page

A separate scheduled job picks high-corroboration candidates and queues them for Pass 3 synthesis (using their corroborating sources as the source set). This closes the loop: dangling wikilinks → create-page candidates → Pass 3 backfill → re-Pass-4 finds the now-resolved target.

#### Step 5 — Cross-cluster bridge detection

For each cluster, find pages whose materialised outgoing edges target pages in **other clusters**. These pages are **bridges**.

```
bridge_score(page) =
    (# of clusters this page links to) ×
    (mean importance_score of target pages)
```

Top-bridge pages per cluster are written to the cluster manifest's `bridges` field and surface in Pass 5's `cross_domain_links`. Bridges are also strong hub candidates.

#### Step 6 — Hub detection

A page becomes a **hub** when **any** of:
- `inbound_edge_count ≥ 10`
- `cluster_link_count ≥ 2` (page links to ≥2 distinct clusters)
- `convergent_dangling_count ≥ 5` (page is the target of ≥5 dangling wikilinks before Step 4 resolved them)

Hubs are:
- Promoted to L1 routing manifest as canonical route targets
- Marked for Opus-tier Pass 4 review (the Haiku-tier resolution above degrades gracefully; Opus is reserved for hubs + contradictions)
- Surfaced to retrieval rankers with hub-bonus weight

See [[4.1  - Hub Detection]] for full hub criteria, scoring, and edge cases.

#### Step 7 — Contradiction detection

When two pages cite the same source region but emit incompatible claims, Pass 4 emits a `contradicts` edge between them and queues the pair for SME review.

Detection heuristics:
- Pages citing identical `(source_id, region_id)` whose Pass 3 emitted output diverges semantically (Haiku-judged)
- Two pages tagged with the same `kind: decision` and overlapping `domain_candidates` whose stated choices conflict
- A new page whose claim contradicts an existing page's claim that has been "stable" (no edits) for >30 days

Contradictions are **never auto-resolved**. They surface to SME review queues with both pages + their cited regions + the divergent claims explicitly extracted.

---

### Output

Pass 4 writes to five storage targets:

```yaml
# 1. L4 cross-link graph (typed edges, persisted)
CrossLinkEdge:
  from_page: <page_id>
  to_page: <page_id> | <create_page_candidate_id>
  edge_kind: <enum>
  confidence: <float>
  corroborating_signals: [pass_0_edge | pass_1_soft_link | pass_3_wikilink]
  created_at: <iso8601>
  resolved_at: <iso8601>          # null if still dangling

# 2. Hub set (per cluster)
HubDesignation:
  cluster_id: <ref>
  primary_hub: <page_id>
  satellites: [<page_id>]
  hub_score: <float>

# 3. Cross-cluster bridges
ClusterBridge:
  source_cluster: <ref>
  target_cluster: <ref>
  bridge_pages: [<page_id>]
  bridge_strength: <float>

# 4. Create-page candidates queue
CreatePageCandidate:
  candidate_slug: <string>
  nominated_kind: <PageKind>
  corroborating_sources: [<source_id>]
  dangling_wikilink_count: <int>
  status: queued | scheduled | synthesized | rejected

# 5. Contradiction queue
ContradictionEntry:
  page_a: <page_id>
  page_b: <page_id>
  shared_source: <source_id>, <region_id>
  divergence_summary: <prose>
  assigned_sme: <user_id | null>
  status: open | reviewed | resolved
```

---

### Downstream consumers

- **Pass 5 (Community Summary)** — reads hubs, bridges, contradictions, and the typed-edge subgraph per cluster to populate the prose summary.
- **L1 routing manifest** — hubs auto-promoted as canonical route targets; aliases populate query-rewrite rules.
- **Retrieval (any global or multi-hop question)** — the L4 graph is the structural substrate retrieval traverses.
- **SME review surfaces** — contradiction queue + create-page candidates + dangling-wikilink digests.
- **Drift loop (L5)** — when a source region changes, the L4 back-references identify every page whose edges may need re-validation.

---

### Tier policy

Pass 4 has the **most aggressive tier discipline** of any pass because Opus is the cost dominator:

| Sub-step | Tier | Why |
|---|---|---|
| Slug resolution | mechanical | string matching |
| Soft-link promotion scoring | mechanical | arithmetic on corroboration signals |
| Typed edge classification (routine) | Haiku, batched | low-stakes labelling |
| Typed edge classification (hub pages) | Opus | hub edges are high-leverage; budget allows |
| Dangling resolution | mechanical (grouping) + Haiku (kind nomination) | cheap |
| Bridge detection | mechanical | graph traversal |
| Hub detection | mechanical | threshold checks |
| Contradiction detection on stable pages | Opus | high-stakes; must not false-positive |
| Contradiction detection on freshly-synthesized pages | Haiku | acceptable false-positive rate |

Workspace Opus budget is the first gate to fail under cost pressure. When exhausted:
1. Hub-page typed classification falls back to Haiku.
2. Contradiction detection falls back to Haiku.
3. If still over budget: Pass 4 reduces to "mechanical-only" mode — slug resolution + dangling grouping + hub thresholds — until the budget window resets.

---

### Quality Invariants

1. **No edge without corroboration.** Every L4 edge must have ≥1 corroborating signal from Pass 0/1/2/3. Edges Pass 4 invents without a signal trail are rejected at validation.
2. **Contradictions never auto-resolved.** Only SMEs close contradictions.
3. **Hub set < 10% of pages.** If higher, threshold tuning needed.
4. **Create-page candidates require ≥3 corroborating sources** before being queued for synthesis. Single-source dangling wikilinks become aliases or get discarded — never auto-create.
5. **Cross-cluster bridges are recorded but never auto-restructure clusters.** Pass 2 re-clustering is a separate decision (see [[2 - Cluster Summary]] re-clustering triggers).
6. **Aliases are workspace-config-controlled.** Pass 4 nominates aliases; humans approve via SME workflow. Auto-aliasing has been the source of GraphRAG production incidents elsewhere.

---

### Cross-domain examples beyond the auth scenario

| Scenario | Pass 4 detection | Edge kinds materialised |
|---|---|---|
| Slack debate decides to abandon Postgres logical replication; PR ships removal; ADR captures the decision | All three pages dangle on `[[postgres-logical-replication-decision]]`; create-page candidate fires; SME approves; Pass 3 synthesizes; Pass 4 re-runs and materialises edges | `decides_on`, `supersedes` (against old ADR), `discusses` |
| Notion runbook references a Linear ticket which references a PR which modifies a code file | Pass 0 catches the chain mechanically; Pass 4 stitches the four nodes with `references` edges and elevates the runbook → ticket → PR → code chain as a `flow` candidate | `references` × 3 |
| Two ADRs in the same cluster make incompatible recommendations about retry policy | Pass 4 contradiction detection (both kind=decision, overlap domain, divergent claims) | `contradicts` |
| GitHub Discussion thread evolves into a Notion design doc evolves into a code module | Pass 0 catches URLs; Pass 1 catches soft links; Pass 4 stitches with `discusses → specifies → implements` chain | `discusses`, `specifies`, `implements` |

---

### Cross-links

- Pass-level gating model → [[0.1 - Pass Routing & Eligibility]]
- Cluster manifest consumed (member set, sibling clusters) → [[2 - Cluster Summary]]
- Atomic pages consumed (wikilinks) → [[3 - Atomic Page Synthesis]]
- Hub detection sub-doc → [[4.1  - Hub Detection]]
- Prose summary downstream → [[5 - Aggregation and Indexing Community Summary]]
- Architecture this sits in → [[llm-wiki-engine-architecture]] (Dev)
- Knowledge-graph + PageRank inspiration → HippoRAG, GraphRAG (see [[graphrag-community-summaries]] Dev)
- Tier discipline this enforces → [[tiered-corpus-enrollment]] (Meta)
- Drift loop that re-triggers Pass 4 → [[6 - Linting and Drift Detection]]
