 ▐▛███▜▌   Claude Code v2.1.141
▝▜█████▛▘  Opus 4.7 · Claude Max
  ▘▘ ▝▝    ~/Documents/wiki/Dev

❯ /wiki-query in an LLM wiki. L2 cluster entities are represented through the   
use of community summaries? Which act as groupings of information and source    
files? Is this close                                                            

  Searched for 1 pattern, read 2 files, listed 1 directory (ctrl+o to expand)

● Close, but mix-and-match. Refining:

  Your statement: "L2 cluster entities are represented through community
  summaries, which act as groupings of information and source files."

  Accurate version:
  - L2 = community summaries (correct).
  - They group pages (L3 atomic concept nodes), not source files. Source files
  sit below pages — they're pointers in sources/ that pages cite.
  ([[llm-wiki-engine-architecture]])
  - The "entities" being clustered are the pages themselves. Clustering runs
  over the wikilink graph between pages (Leiden, GraphRAG-style) — or cheaper
  proxies like directory structure / CODEOWNERS.
  ([[graphrag-community-summaries]])
  - A summary is an LLM-generated mid-tier abstraction over a cluster of pages,
  queried before atomic pages get retrieved — cuts query-time tokens 9–43× on
  global questions. ([[graphrag-community-summaries]])
  - Summaries are hierarchical: community → super-community → root. Isomorphic
  to a hand-curated index.md. ([[hierarchical-rag-isomorphism]])

  Tightened mental model:

  ▎ L2 = LLM-built mid-tier summaries over clusters of L3 atomic pages, where 
  ▎ clusters come from running Leiden (or a proxy) on the L4 wikilink graph. 
  ▎ Source files live at the layer below pages, cited by pages — not clustered 
  ▎ directly.

  The "and source files" part is the slip — sources are inputs to pages, not
  co-equal nodes in the cluster.

  Pages consulted: [[graphrag-community-summaries]] (insight),
  [[llm-wiki-engine-architecture]] (framework), [[hierarchical-rag-isomorphism]]
   (framework via index).

  No retrieval.yaml in Dev wiki — fell through to index scan. Logging query.

● Bash(echo "$(date '+%Y-%m-%d %H:%M') | in an LLM wiki. L2 cluster entities are
       represented through the use of community summaries? Which act as
      groupings of informat…)
  ⎿  (No output)

● No coverage gap — wiki answered directly. Skip promotion (answer already lives
   in the three cited pages).

✻ Cooked for 35s

❯ L2 refers to L3 which refers to L1. What is the flow when it comes to         
  actualyl creating the LLM wiki. A pipeline would consume source material      
  creating the pages in L1. Another pipeline would ingest source files to       
  continuosly build or aggregate synthesis pages (L3), and then another         
  pipeline would continuously ingest and form wikilinks and indexed sumarries   
  of clusters for L2?                                                           

  Searched for 1 pattern, read 2 files (ctrl+o to expand)

● Layer numbers off. Correct mapping ([[llm-wiki-engine-architecture]]):

  - L0 = hash + skip ledger (not LLM)
  - L1 = routing manifest (query→page paths, deterministic)
  - L2 = community summaries (mid-tier, LLM-built over clusters of L3)
  - L3 = atomic pages (the leaves — synthesis output, ≤300 words, typed by kind)
  - L4 = cross-link graph (wikilinks as edges)
  - L5 = drift loop (rehash, detect, resynth)

  So pages = L3, wikilinks/clusters = L4 feeding L2, routing = L1. Sources sit
  below L3.

  Actual build flow — one pipeline, five sequential passes 
  ([[tiered-corpus-enrollment]])

  Not three parallel pipelines. One pipeline, tiered passes — each pass bounded
  by a different dimension, each unlocks the next:

  sources/  →  Pass 0  →  Pass 1  →  Pass 2  →  Pass 3  →  Pass 4  →  L5
  (forever)
              hash       classify   cluster+   synth      cross-link   drift
  loop
              no LLM     Haiku      summarize  L3 pages   resolve
                                    → L2       Sonnet     Opus, hubs
                                                          only

  Pass 0 — walk tree, SHA-256 every cited region, build provenance graph. Seeds
  L0. No LLM. ([[content-hash-skip-resync]])

  Pass 1 — Haiku-tier classifier per source: {lane, kind_candidate, is_boundary,
   importance}. PageRank over symbol graph picks boundaries. Cheap, batched.

  Pass 2 — cluster sources (Leiden over wikilink graph, OR directory structure,
  OR CODEOWNERS), summarize each cluster ≤500 words. This produces L2.
  Haiku-tier, batched. Tens of dollars.

  Pass 3 — Sonnet-tier synthesizes L3 atomic pages — but only for boundaries +
  ADRs + entry flows, never leaves. Bounded by boundary count, not LOC. Cost
  dominator: $1–4k for mid-co. ([[graphrag-community-summaries]])

  Pass 4 — Opus runs only on hub pages (>10 inbound links) to resolve
  [[wikilinks]], kill dangling slugs, flag supersession. Populates L4.

  L5 drift loop runs forever after: rehash cited regions → mismatch enqueues
  re-synth of just-affected pages → bubbles up to re-cluster just-affected L2
  communities. The thing most implementations skip and most regret.

  Two key inversions from your mental model

  1. L2 doesn't get built before L3 — L3 pages are the members of L2 clusters.
  You need pages first to cluster them. (Pass 2 summarizes raw source clusters
  as a scaffold for Pass 3; the durable L2 is over L3.)
  2. No continuous ingest of source → L2 directly. Sources flow into L3 via
  synthesis; L2 is a derivative over L3 + L4 graph. Wikilinks are emitted by L3
  synthesis, then L4 is built from them, then L2 clusters use L4.

  Cost discipline

  Stack three multipliers per pass: prompt cache prefix (~90% off), Batch API
  (~50% off), model cascade (Haiku→Sonnet→Opus). 30–100× steady-state reduction
  vs naive. ([[ingest-cost-stacking]])

  Pages consulted: [[llm-wiki-engine-architecture]] (framework),
  [[tiered-corpus-enrollment]] (sop), [[ingest-cost-stacking]] (framework),
  [[graphrag-community-summaries]] (insight). No coverage gap.

✻ Cooked for 36s

❯ but if l2 produces clusters of pages, where pages are derived from sources.   
  Would you not need the produce pages from L3 before L2? or would the drift    
  loop be the part where L2 is then built from newly generared L3               

● Sharp catch. You're right — and the engine docs gloss this. Two phases of L2:

  Phase A — Bootstrap L2 (Pass 2 of initial ingest)

  L3 pages don't exist yet, so there's no wikilink graph to cluster over. Pass 2
   clusters raw sources using cheap proxies:

  - Directory structure (free, structural)
  - CODEOWNERS / team boundaries (organizational)
  - Symbol/citation graph from Pass 0 (mechanical)
  - Manual seed clusters

  LLM summarizes each cluster ≤500 words. This is a scaffold L2 — its job is to
  give Pass 3 scope-narrowing context so synthesis prompts stay tight. Without
  it, Pass 3 reads too much per page and costs explode
  ([[tiered-corpus-enrollment]] "Common failures: trying to skip Pass 2").

  Phase B — Durable L2 (after Pass 3, maintained by L5)

  Once L3 pages exist and L4 wikilink graph is populated (Pass 4), re-run
  clustering — this time Leiden over the wikilink graph between pages. This is
  the real GraphRAG-shape L2 ([[graphrag-community-summaries]]).

  The scaffold gets thrown away or merged into the durable version. Same data
  structure, different cluster input.

  Drift loop's role

  [[llm-wiki-engine-architecture]] L5: when a cited region's hash changes:
  1. Re-synth the affected L3 page(s)
  2. New page emits possibly-different [[wikilinks]] → L4 edges update
  3. Affected L2 community gets re-summarized (just that community, not the
  whole tree)

  So yes — drift loop is exactly where ongoing L2 maintenance happens.
  Re-cluster + re-summarize, scoped to the touched community.
  ([[graphrag-community-summaries]] notes the high cost of "re-running Leiden +
  re-summarizing communities" on every update — drift loop scopes it so you
  re-summarize one community, re-run Leiden only when topology shifts
  materially.)

  Cleaner mental model

  Initial ingest:
    sources → cluster-by-proxy → scaffold L2 → synth L3 → emit L4 → real L2
  (over L4)

  Steady state (L5):
    source change → re-hash → re-synth touched L3 → patch L4 → re-summarize
  touched L2

  The two-phase nature is exactly the inversion you spotted. Scaffold L2 is a
  bootstrap hack to break the chicken-and-egg; the real L2 lives over L3+L4 and
  is maintained by L5.

  Worth promoting? This distinction isn't explicit in any current page —
  tiered-corpus-enrollment shows the scaffold without flagging it as scaffold,
  graphrag-community-summaries shows the durable form without flagging the
  bootstrap. Want me to ingest a new page like l2-bootstrap-vs-durable (kind:
  insight) to lock it in?

  Pages consulted: [[tiered-corpus-enrollment]] (sop),
  [[llm-wiki-engine-architecture]] (framework), [[graphrag-community-summaries]]
   (insight). Coverage gap flagged: bootstrap-vs-durable L2 distinction is
  implicit, not stated.

✻ Crunched for 23s

❯ ok so at the start it will skaffold the intended cluster of SOURCES, and then 
   when pages are generated it will generate clusters of pages based on which   
  pages reference or are associated with those sources initially clustered      

● Close, but one more wrinkle — durable clusters aren't inherited from source
  clusters, they're recomputed from page edges.

  The mechanism

  Scaffold L2 (bootstrap):
  cluster_A = {source1, source2, source3}  ← grouped by
  directory/CODEOWNERS/symbol graph
  cluster_B = {source4, source5}

  Pass 3 generates pages. A page synthesized from source2 might emit wikilinks
  like [[page-from-source5]] — because the concept lives across the source
  boundary even though the files don't.

  Durable L2 (real): Leiden runs over the page→page wikilink graph. The
  community boundaries get redrawn based on how pages actually reference each
  other:

  community_X = {page_from_s1, page_from_s2, page_from_s5}  ← s5 jumped
  clusters!
  community_Y = {page_from_s3, page_from_s4}

  A page can end up in a different community than its source's scaffold cluster,
   because the wikilink graph encodes conceptual proximity, not file proximity.

  Why this matters

  This is the whole point of GraphRAG over directory-based grouping. Directory
  structure tells you which files sit next to each other on disk. The wikilink
  graph tells you which concepts the LLM (or human) decided are actually related
   when synthesizing. They often disagree — and the disagreements are where the
  interesting cross-cutting concepts live ([[graphrag-community-summaries]]
  "multi-hop reasoning where the right path runs through entity relationships,
  not text similarity").

  The scaffold is a cheap prior for Pass 3 cost control. The durable L2 is a
  learned posterior from how synthesis actually wired up the graph.

  Concrete example

  Sources cluster A = auth/*.ts files. Sources cluster B = billing/*.ts files.

  Pass 3 generates [[refresh-token-rotation]] (from auth/) and
  [[subscription-renewal]] (from billing/). Both reference
  [[clock-skew-handling]] (from a utils source). All three wikilink to each
  other heavily.

  Durable L2 might pull these three into one community: "time-sensitive state
  transitions" — crossing the auth/billing directory boundary entirely. The
  scaffold could never have found this; only the wikilink graph reveals it.

  Restating your model with the correction

  ▎ At start: scaffold cluster of sources (cheap proxy).
  ▎ After pages exist: re-cluster the pages based on how they wikilink to each 
  ▎ other — not by inheriting their source's cluster membership.
  ▎ The scaffold's job was just to make Pass 3 affordable. After Pass 3, throw 
  ▎ it away and trust the page graph.

  Drift loop then maintains the durable form: when a page changes, re-emit its
  wikilinks, patch L4 edges, re-run Leiden on the affected subgraph,
  re-summarize the touched community.

  Pages consulted: [[graphrag-community-summaries]] (insight),
  [[llm-wiki-engine-architecture]] (framework). Same gap as before —
  bootstrap-vs-durable distinction still unwritten.

✻ Cogitated for 20s

※ recap: Goal: understand the LLM wiki build pipeline. We've mapped layers L0–L5 and clarified that L2 is bootstrapped from source clusters then rebuilt from page wikilinks. Next: decide whether to promote a new page capturing the bootstrap-vs-durable L2 distinction. (disable recaps in /config)