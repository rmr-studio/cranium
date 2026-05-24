---
tags:
  - wiki-layer-2
  - wiki-ingestion-pass-5
---

### Overview

**Pass 5** writes the prose **community summary** per cluster — the L2 mid-tier retrieval surface that answers global questions ("what does the auth domain do?", "what are the recurring patterns across `apps/billing`?") without dragging the model through every atomic page in scope.

Pass 5 runs **after Pass 4**, not after Pass 3. The summary needs hubs, cross-cluster bridges, and contradictions — all Pass 4 outputs. Without them the summary is structurally incomplete: it can list pages but can't say "page X is the canonical entry point; pages Y and Z are satellites; this cluster bridges to billing via the auth-token-refresh hub."

This is the L2 layer of [[llm-wiki-engine-architecture]] (Dev) and the same shape as GraphRAG community summaries ([[graphrag-community-summaries]], Dev). Karpathy's flat `index.md` is its closest informal cousin — see [[karpathy-wiki-scale-cliff]] (Meta) for why Pass 5 multi-level summarisation matters above ~500 pages.

---

### Where Pass 5 sits

```
Pass 0 → Pass 1 → Pass 2 (manifest) → Pass 3 (pages) → Pass 4 (cross-link graph) → Pass 5 (summary)
                  ↑ mechanical                                                      ↑ Haiku
                  ↑ structural Layer 2                                              ↑ prose Layer 2
```

Pass 2 produced the *structural* part of L2 (cluster manifest). Pass 5 produces the *prose* part of L2 (community summary). Together they form a complete L2.

---

### Inputs

- **Cluster manifest** from Pass 2 (`member_sources`, `sibling_clusters`, `dominant_domain`, `pass_1_label_summary`)
- **L3 pages** whose primary source is a cluster member (Pass 3)
- **L4 cross-link graph** for cluster members (Pass 4): hubs, cross-cluster bridges, materialised typed edges, contradictions
- **Pass 1 labels** for non-page-producing members (utility sources that exist but didn't synthesize)
- **Sibling cluster summaries** (one-hop, when summarising a hub-adjacent cluster — gives sibling-relationship context)

---

### Algorithm

1. **Resolve membership**: pages whose `sources[].source_id ∈ cluster.member_sources` + Pass 1 labels for non-page members.
2. **Pull Pass 4 outputs** for this cluster: hub set (pages with >10 inbound links), cross-cluster bridge edges, contradictions queued during Pass 4.
3. **Render Haiku prompt**: cluster manifest + page bodies (truncated to first 200 words each) + hub designation + bridge edges + contradictions + sibling cluster names.
4. **Structured output**: `{ mechanism, member_inventory, hub_designation, sibling_relationships, standards_summary, open_contradictions, cross_domain_links }`.
5. **Validate against schema**. Retry with stricter prompt on failure.
6. **Write to cluster index** (`<cluster_id>/index.md` or equivalent storage). Set `ClusterManifest.has_summary = true`.

---

### Output schema

```yaml
CommunitySummary:
  cluster_id: <ref>
  mechanism: <prose, ≤200 words>          # what the cluster does end-to-end
  member_inventory: <prose, ≤150 words>   # which pages exist + their roles
  hub_designation:
    primary_hub: <page_id>                 # the canonical entry point
    satellites: [<page_id>]                # supporting pages
  sibling_relationships: <prose, ≤100 words>  # how this cluster bridges to its siblings
  standards_summary: <prose, ≤50 words>   # adoption rates from Pass 1 labels
  open_contradictions: [<page_ref>]       # carried forward from Pass 4
  cross_domain_links: [<edge_summary>]    # notable cross-cluster bridges
  generated_at: <iso8601>
  generated_from_pages: [<page_id>]
  generated_from_labels: [<source_id>]    # non-page members
  summary_version: <int>
```

---

### Eager vs Lazy Mode

Pass 5 is the **first pass where lazy execution is a sensible default**. Earlier passes are construction-time prerequisites for everything that follows; Pass 5 produces retrieval surface area whose value only matters when someone queries.

| Mode | Run trigger | Cost profile | Latency profile |
|---|---|---|---|
| **Eager** | After Pass 4 affects ≥1 page in cluster | High indexing cost, low query cost | Fast query response |
| **Lazy** | First time a global query routes to this cluster | Low indexing cost, query pays | Slower first query, fast on repeat |
| **Hybrid** | Eager for hub clusters; lazy for satellites | Moderate both ways | Fast for popular queries |

Choose lazy when: query volume per cluster is low, corpus churns frequently, workspace is still onboarding. Choose eager when: query volume is high, corpus is stable, query latency matters.

Both modes write to the same `CommunitySummary` storage and read the same `ClusterManifest` + Pass 4 outputs. The lazy mode just defers the Haiku call to the moment of first query, then caches the result. See [[lazy-graphrag-tradeoff]] (Dev) for the cost numbers — LazyGraphRAG: 700× cheaper queries, 0.1% of GraphRAG indexing cost.

**Hybrid is the recommended default for Cranium**: eager on hubs (where retrieval traffic concentrates and latency matters), lazy on satellites.

---

### Multi-Level Summaries (RAPTOR-style)

Above the [[karpathy-wiki-scale-cliff]] (~500 pages), single-level community summaries collapse — too many clusters at one tier overwhelm the global index. RAPTOR's recursive abstractive processing addresses this with a **tree of summaries**:

```
Workspace index ──┐
                  ├── super-cluster summary (auth + identity + sessions)
                  │         ├── cluster summary (auth)
                  │         ├── cluster summary (identity)
                  │         └── cluster summary (sessions)
                  └── super-cluster summary (billing + subscriptions + payouts)
                            ├── cluster summary (billing)
                            ├── cluster summary (subscriptions)
                            └── cluster summary (payouts)
```

Each super-cluster summary is written by Pass 5 with the constituent cluster summaries as input, recursing until the root index fits in budget. Same Haiku call, different input. Same output schema, different `summary_version` namespace.

Trigger: workspace crosses 500 pages OR 30 clusters. Below those thresholds, single-level Pass 5 suffices.

See [[hierarchical-rag-isomorphism]] (Meta) for the broader pattern — GraphRAG / RAPTOR / HippoRAG / Karpathy wikis all collapse to this tree-of-summaries shape.

---

### Downstream consumers

- **Retrieval (global questions)** — community summary is the first context layer; atomic pages are pulled only if the summary points to them. Average 9–43× token reduction at query time (GraphRAG benchmarks).
- **L1 routing manifest** — community-summary-keyed routes (`load: cluster=<id>` resolves to the summary, not the atomic pages).
- **SME review queue** — `open_contradictions` surfaces flagged pages with assigned SMEs.
- **Onboarding surfaces** — `hub_designation.primary_hub` populates "start here" entries in the domain index.
- **Drift loop** — when Pass 5 emits a summary that significantly diverges from the prior version (>30% prose delta), the loop signals an L5 alert for SME review.

---

### Quality Invariants

1. **Summary length cap.** 500 words hard. Summaries that want more are evidence the cluster is too big.
2. **Hub designation required.** Every non-singleton cluster must declare a `primary_hub` from Pass 4 output. If Pass 4 declared no hubs, Pass 5 nominates the highest-`importance_score` page; if no page exists, the field is null and the cluster is flagged for SME review.
3. **Standards adoption coverage.** Summary must report standards adoption for ≥80% of cluster members; gaps surface as quality flag.
4. **Contradictions carried forward, not invented.** `open_contradictions` is copied from Pass 4 output; Pass 5 never invents new contradictions. If the LLM tries to, the validator strips them.
5. **Cross-domain links sourced from Pass 4.** `cross_domain_links` must reference edges that exist in the L4 graph. Hallucinated cross-domain claims are rejected at validation.
6. **Stability across runs on unchanged inputs.** Re-running Pass 5 over the same manifest + pages + Pass 4 outputs should produce semantically equivalent summaries (token-level differences acceptable; structural / claim-level differences flagged).

---

### Re-trigger Conditions

| Trigger | Action |
|---|---|
| Cluster manifest version bumped (Pass 2 re-ran) | Pass 5 eligible if eager; invalidate cache if lazy |
| L3 page in cluster edited | Pass 5 eligible (page-level drift can shift the summary) |
| L4 cross-link graph changed for cluster members | Pass 5 eligible (hubs / bridges / contradictions shifted) |
| Sibling cluster summary changed | Pass 5 eligible for clusters that link to it (one-hop cascade) |
| Periodic refresh | Configurable: default 30 days; bumps `summary_version` even if content unchanged so SMEs see a fresh review prompt |

Pass 5 caches its output keyed by `(cluster_id, manifest_version, pages_hash, l4_subgraph_hash)`. Re-runs hit the cache when no input dimension shifted.

---

### Cross-links

- Pass-level gating model → [[0.1 - Pass Routing & Eligibility]]
- Structural manifest consumed → [[2 - Cluster Summary]]
- Cross-link graph + hubs consumed → [[4 - Graph Linking Materialisation]], [[4.1  - Hub Detection]]
- Atomic pages consumed → [[3 - Atomic Page Synthesis]]
- Eager-vs-lazy economics → [[lazy-graphrag-tradeoff]] (Dev)
- Multi-level hierarchy → [[hierarchical-rag-isomorphism]] (Meta)
- Conceptual origin → [[graphrag-community-summaries]] (Dev) + [[llm-wiki-pattern-karpathy]] (Meta)
- Scale cliff this addresses → [[karpathy-wiki-scale-cliff]] (Meta)
- Drift loop that re-triggers Pass 5 → [[6 - Linting and Drift Detection]]
