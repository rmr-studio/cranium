---
tags:
  - wiki-layer-2
  - wiki-ingestion-pass-2
---

### Overview

A **cluster manifest** is the structural L2 object produced by Pass 2. It declares which sources belong to which cluster, the edges among them, and a summary of the Pass 1 labels across the membership. It is **purely mechanical** — no LLM call — and exists to give Pass 3 scope context when synthesizing atomic pages.

The prose community summary that retrieval reads (mechanism, hubs, sibling relationships, contradictions) is **not** Pass 2 — it's Pass 5, which runs after Pass 4 once the cross-link graph + hubs + contradictions exist. See [[5 - Aggregation and Indexing Community Summary]] for that pass.

Pass 2's deliberate scope: cluster the corpus from raw signals alone, fast and cheap, so Pass 3 has something to read.

---

### Inputs

- **Pass 1 labels** for every source (`domain_candidates`, `content_kind`, `kind_candidate`, `importance_score`, `standards_detected`).
- **Provenance graph** edges from Pass 0 (calls, imports, cites, mentions).
- **Workspace structure hints** (CODEOWNERS, directory tree, Slack channel topology, Notion workspace hierarchy) when graph signal is thin.

No L3 pages. No wikilink graph. Both come later — Pass 5 re-clusters when those arrive if signal materially diverged.

---

### Algorithm

1. **Seed assignments** from Pass 1 `domain_candidates`. Each source's top candidate becomes its initial cluster.
2. **Leiden community detection** over the provenance graph, refining the seed assignments. Edge weights from `edge_kind` (calls/imports = strong, mentions/cites = weak).
3. **Fallback** for sources with no graph signal: directory structure (code), workspace section (Notion), channel (Slack) as the cluster.
4. **Minimum cluster size guard** — clusters of size 1 either merge into the nearest neighbour or get `skip_summary = true`. Singletons survive in the registry but never get a Pass 5 prose summary.
5. **Cluster ID generation** — deterministic from `(workspace, dominant_domain, member_hash)` so re-runs are stable.
6. **Sibling detection** — for each cluster, identify clusters sharing ≥3 provenance edges across the boundary. These become `sibling_clusters` for Pass 3 context + Pass 4 bridge detection.

---

### Output schema

```yaml
ClusterManifest:
  cluster_id: <uuid-v5>
  dominant_domain: <string>            # "auth", "billing", "platform-infra"
  member_sources: [<source_id>, ...]
  member_count: <int>
  edge_set:                             # provenance edges contained within cluster
    - { from: <region>, to: <region>, kind: <enum> }
  sibling_clusters: [<cluster_id>]     # clusters sharing ≥3 boundary edges
  pass_1_label_summary:
    content_kinds: { CODE_KOTLIN: 23, DOC_MARKDOWN: 4, ... }
    kind_candidates: { feature: 8, sop: 3, ... }
    standards_observed: [ ... ]
  importance_density: <float>          # mean importance_score across members
  skip_summary: <bool>                  # singleton or otherwise excluded from Pass 5
  manifest_version: <int>
  created_at: <iso8601>
```

---

### Downstream consumers

- **Pass 3** reads `pass_1_label_summary` + `sibling_clusters` + `dominant_domain` as the cluster context block in the synthesis prompt. ("This page belongs to the auth cluster, which contains 8 features and 3 SOPs and borders the platform-infra cluster.")
- **Pass 4** uses `edge_set` + `sibling_clusters` to find candidate cross-cluster bridge pages and seed hub detection.
- **Pass 5** consumes the manifest as the primary input to the community summary prompt.
- **Retrieval** (before Pass 5 has summarised) — `member_sources` enables cluster-scoped queries via a degenerate "list members" response until prose summary exists.

---

### Invariants

- Every source admitted by Pass 1 belongs to exactly one cluster. Sources marked `skip_synthesis: true` still get cluster membership (they participate in domain accounting; they just don't synthesize into L3 pages or get summarised in Pass 5).
- `member_sources` and `edge_set` are immutable per `manifest_version`. Re-clustering bumps the version and writes a new manifest; old manifests retained for audit.
- `dominant_domain` must be one of the `domain_candidates` declared by Pass 1 across ≥40% of members. If no candidate dominates, the cluster is named after the directory/channel/section (fallback).
- **No LLM call in this pass.** Anything requiring judgment lives in Pass 1 (per-source) or Pass 5 (per-cluster prose).

---

### Re-clustering Triggers

Clusters are not immutable. Triggers for `manifest_version` bumps:

| Trigger | Condition | Action |
|---|---|---|
| New boundary source | Pass 1 marks a non-leaf as boundary in a cluster | Add to `member_sources`; re-run Leiden if member_count threshold crossed |
| Source removed | Source deleted in repo / archived | Drop from `member_sources`; if cluster falls below min size, merge |
| Provenance edge added | Code refactor adds new imports between previously-disjoint sources | Edge added to `edge_set`; Leiden may move membership |
| Domain re-labelling | Pass 1's `domain_candidates` shifts across cluster members | Re-seed Leiden, re-run |
| **Pass 4 wikilink convergence** | The L4 wikilink graph from Pass 4 materially diverges from the provenance graph for >20% of members | **Re-cluster using union(provenance, wikilink) edges** as a sophistication — produces a richer membership for Pass 5 |
| Periodic rebuild | Weekly scheduled job | Sanity re-run; manifest only bumps if material change |

The Pass-4-triggered re-cluster is the bridge between our mechanical-clustering choice (Pass 2 before Pass 3) and the literature default (Leiden over the wikilink graph after entity extraction — GraphRAG). On workspaces where the wikilink graph closely tracks the provenance graph, this re-cluster is a no-op. On workspaces where Slack threads, Notion docs, and code wikilink across structural boundaries, it produces meaningfully different clusters.

---

### Quality Invariants

1. **Cluster size band.** Healthy clusters sit in `[3, 50]` members. Larger → split. Smaller → merge or mark `skip_summary`.
2. **Cross-cluster edge ratio.** A "tight" cluster has <30% of its edges crossing cluster boundaries. If >50%, Leiden parameters need tuning.
3. **Singleton ratio.** No more than 10% of clusters should be singletons. Higher → boundary detection is over-fragmenting OR Pass 1 `domain_candidates` are too narrow.
4. **Stability across runs.** Re-running Pass 2 on unchanged inputs should produce identical `cluster_id`s. Non-determinism = bug.

---

### Cross-links

- Pass-level gating model → [[0.1 - Pass Routing & Eligibility]]
- Labels consumed as inputs → [[1.2 - Source Classifier & Clustering]]
- Source primitive → [[0. Source File]]
- What Pass 3 does with the manifest → [[3 - Atomic Page Synthesis]]
- Cross-domain edge materialisation that may trigger re-cluster → [[4 - Graph Linking Materialisation]]
- Hub detection that builds on manifest edges → [[4.1  - Hub Detection]]
- The prose-summary pass that consumes the manifest → [[5 - Aggregation and Indexing Community Summary]]
- Conceptual origin → [[graphrag-community-summaries]] (Dev) + [[hierarchical-rag-isomorphism]] (Meta)
- Drift loop that re-triggers re-clustering → [[6 - Linting and Drift Detection]]
