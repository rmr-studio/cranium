 ▐▛███▜▌   Claude Code v2.1.141
▝▜█████▛▘  Opus 4.7 · Claude Max
  ▘▘ ▝▝    ~/Documents/wiki/Dev

❯ /wiki-query The architecture in Cranium is receiving a monumental shift in    
the way 'Entities" are structured and portrayed. Moving away from notion style  
rows and going completely in on the LLM wiki structure, where every single      
entity is now a Page to build a massive ecosystem. This builds system domains,  
features, team ecosystems synced from github repositories, notion/confluence    
bases, slack threads etc. The major architectural concern woudl be that during  
initial scanning. A team may have extremely massive github repositories, or     
numerous repositories. To build an LLM wiki a scan of teh entire repository to  
understand internal system domains, features capabilities, "why" "what" " how". 
 Design structures, visions, standards etc. This would create an extremely      
large amount of pages and interlinking connections between these pages. Prior   
research has been done on karpathies LLM wiki, and extensiosn to handle high    
volumes. When the goal is to build a massive systrem and team ecosystrem for    
Cranium. How would you envison the architecture to avoid bloated LLM costs, and 
 a high level system vision to build this enigneering hive mind                 

  Searched for 1 pattern, read 8 files, listed 3 directories (ctrl+o to expand)

● No retrieval.yaml route matched. Query logged to
  Cranium/retrieval.suggested.log. Fell through to Meta wiki — turns out you've
  already done the scaling research there. Five pages directly answer this.

  ---
  Architecture vision: Cranium as engineering hive mind

  The pattern is settled. Five Meta pages encode it.

  Your "every entity = a Page" shift IS the Karpathy LLM-wiki pattern
  ([[llm-wiki-pattern-karpathy]]) applied to the org instead of a single
  project. The scaling concern you raised is the known failure mode — the
  [[karpathy-wiki-scale-cliff]] sits at ~500 pages flat, not millions. Hierarchy
   must come on early.

  Core architectural shape — three tiers + skip layer

  GraphRAG, RAPTOR, HippoRAG, and your current wiki are isomorphic
  ([[hierarchical-rag-isomorphism]]):

  L0  content-hash skip            ← cheapest; skipped pages cost $0
  L1  retrieval.yaml routing       ← deterministic; you already run this on
  Cranium
  L2  per-domain / per-repo index  ← LLM-built summaries
  L3  atomic pages (entity = page) ← the leaves

  Cranium's "team ecosystem" = L2 + L3. Each repo gets a community summary (à la
   GraphRAG Leiden clustering). Each domain/feature/decision/flow under it = a
  Page. Master catalog = L1 routing manifest, deterministic.
  Slack/Notion/Confluence ingest = additional sources hanging off the same
  pages, not parallel structures.

  Cost containment — stack the multipliers ([[ingest-cost-stacking]])

  total ≈ baseline × (1 − cache × 0.9)
                   × (1 − batch × 0.5)
                   × (1 − cascade)
                   × hash-skip-rate   ← dominant on resync

  30–100× steady-state reduction. Non-negotiables for Cranium scale:

  1. Content-hash skip first. SHA-256 the cited region (commit SHA + file path +
   line range). If unchanged → skip the LLM call entirely. This dominates resync
   workloads. Free.
  2. Prompt-cache discipline. Schema/instructions at the head, source content at
   the tail. ProjectDiscovery hit 7% → 84% cache rate by restructuring. ~90% off
   the prefix.
  3. Batch API for backfill. Initial repo scan = batch. Live sync = interactive.
   Two tracks. 50% flat off the batch lane.
  4. Model cascade. Haiku classifies + writes simple flow/SOP pages. Opus
  reserved for cross-link resolution + decision pages + multi-source synthesis.
  RouteLLM: 85% cost cut at 95% quality with only 14% Opus calls.

  Initial repo scan — the bounding problem

  Don't try to synthesize "everything" on first ingest. Tiered enrollment:

  ┌──────┬──────────────────────────────────────┬───────┬───────────────────┐
  │ Pass │             What it does             │ Model │    Cost lever     │
  ├──────┼──────────────────────────────────────┼───────┼───────────────────┤
  │ 0    │ Walk tree, content-hash every file,  │ none  │ —                 │
  │      │ build provenance graph               │       │                   │
  ├──────┼──────────────────────────────────────┼───────┼───────────────────┤
  │ 1    │ Classify each module: domain /       │ small │ batch + cache     │
  │      │ boundary / leaf (Haiku)              │       │                   │
  ├──────┼──────────────────────────────────────┼───────┼───────────────────┤
  │ 2    │ Generate L2 summaries per module     │ small │ batch + cache     │
  │      │ (Haiku)                              │       │                   │
  ├──────┼──────────────────────────────────────┼───────┼───────────────────┤
  │      │ Generate L3 pages only for boundary  │       │ cache prefix      │
  │ 3    │ modules + ADRs + entry-point flows   │ mixed │ shared across     │
  │      │                                      │       │ pages             │
  ├──────┼──────────────────────────────────────┼───────┼───────────────────┤
  │ 4    │ Cross-link resolution (Opus) —       │ large │ reserved          │
  │      │ agentic only for high-fan-out hubs   │       │                   │
  └──────┴──────────────────────────────────────┴───────┴───────────────────┘

  Pass 1–2 are O(repo) but small-model. Pass 3 is bounded by boundaries, not
  LOC. Pass 4 is bounded by hub pages. Skip Pass 4 for satellites; let lint
  catch missed links lazily.

  Where to spend agentic ([[agentic-retrieval-tradeoff]])

  15× tokens for +90% quality. Reserve for: cross-team architectural questions,
  "why does service X depend on Y", incident retros. Routine queries hit L1
  routing — deterministic, near-free.

  When to extend beyond Karpathy

  Past 5k pages per workspace (see scale-cliff table) — fold in GraphRAG-style
  community summaries generated by Leiden clustering over the wikilink graph
  itself. This is the move when an org grows past ~10 repos. LazyGraphRAG is the
   cheaper variant: build communities, but defer summary synthesis until
  query-time. KET-RAG benchmark shows $1.89 vs $20 ingest for comparable
  quality.

  Hive-mind layer — what makes this Cranium-shaped, not just a wiki

  The Karpathy pattern alone is read-only knowledge. The hive mind comes from
  closing the loop ([[closing-the-loop-requirements]]):

  1. Event store — every PR, Slack decision, Linear ticket, doc edit becomes a
  source-pointer (you have this).
  2. Attribution — pages cite the event(s) that birthed them. Hash-pinned.
  3. Continuous ingestion — PR-merge webhook triggers content-hash diff →
  re-ingests only changed pages. This is what makes the cost math work over
  time, not just at t=0.
  4. Automated adjustment — lint job catches contradictions and drift between
  Slack-stated reality and synthesized page.
  5. Write-back — agent posts back to Notion/Confluence/Slack with synthesized
  page when humans ask. Closes the loop.

  Layer 3 is the architectural pin — without it, the t=0 scan is impressive and
  the t+30d wiki is rot.

  Honest scale caveat ([[wiki-synthesis-vs-rag-retrieval]])

  No public head-to-head benchmark of synthesized Karpathy wikis vs vector RAG
  at million-doc scale. Above ~10k pages both sides extrapolate. Pragmatic call:
   wiki at the per-workspace level (probably <5k pages each), RAG over the union
   when cross-workspace queries are needed. Each Cranium customer = their own
  wiki ecosystem.

  ---
  Pages consulted

  - [[karpathy-wiki-scale-cliff]] (insight)
  - [[hierarchical-rag-isomorphism]] (framework)
  - [[ingest-cost-stacking]] (framework)
  - [[wiki-synthesis-vs-rag-retrieval]] (framework)
  - [[llm-wiki-pattern-karpathy]] (framework)
  - [[agentic-retrieval-tradeoff]] (decision)

  Coverage gaps

  - No Cranium-specific page on "entity = wiki page" architectural decision yet.
   The Meta pages give the pattern; the Cranium-side instantiation (how entities
   table relates to pages/ synthesis, what L2 community = "Team Ecosystem" means
   in your schema, how Slack/Notion sources hang off entity pages) isn't
  written.
  - No page on the initial-scan tiered-enrollment strategy (Pass 0–4 above)
  anywhere. This is reusable IP — applies to any "ingest a giant new corpus"
  job, not just Cranium.
  - No page on content-hash skip as a first-class technique (referenced in
  [[ingest-cost-stacking]] as [[content-hash-skip-resync]] but file doesn't
  exist).

  Promotion offer

  Three pages worth writing. Want me to /wiki-ingest any of:

  1. Wiki/Cranium/pages/entity-as-page-architecture.md [decision] — Cranium-side
   instantiation of the Karpathy pattern, naming your specific layers (entity ↔
  page, team ↔ L2 community, repo ↔ source bundle).
  2. Wiki/Meta/pages/tiered-corpus-enrollment.md [sop] — Pass 0–4 ingest
  strategy for giant new corpora. Generalizable.
  3. Wiki/Meta/pages/content-hash-skip-resync.md [sop] — fills the dead link in
  ingest-cost-stacking.

✻ Cooked for 1m 51s

❯ Would this even be feasible architecture for a moderately sized companies     
  with milliosn of lines of code. Deep confluence wikis, active slack channels  
  etc?                                                                          

● Honest answer: the codebase + Confluence half is feasible. The Slack half is 
  where it breaks. Different problem shapes, different solutions.

  Sizing the corpus realistically

  Million LOC ≠ million pages. Boundary modules are what matter.

  ┌─────────────────┬───────────┬──────────────┬───────────────────────────┐
  │     Source      │  Volume   │ Synthesized  │           Notes           │
  │                 │           │    pages     │                           │
  ├─────────────────┼───────────┼──────────────┼───────────────────────────┤
  │ 1M LOC, ~50     │           │              │ Boundaries + ADRs +       │
  │ repos           │ files     │ ~3k–10k      │ entry-point flows. Leaves │
  │                 │           │              │  don't get pages.         │
  ├─────────────────┼───────────┼──────────────┼───────────────────────────┤
  │ Confluence      │ 10k–50k   │              │ Most Confluence is rot;   │
  │ (mid-co)        │ docs      │ ~2k–5k       │ lint kills 60–80% as      │
  │                 │           │              │ superseded.               │
  ├─────────────────┼───────────┼──────────────┼───────────────────────────┤
  │ Slack (active,  │ 10M+      │              │ But only the decisions,   │
  │ 100+ channels)  │ msgs/yr   │ ~500–2k      │ not the messages. This is │
  │                 │           │              │  where it gets ugly.      │
  ├─────────────────┼───────────┼──────────────┼───────────────────────────┤
  │                 │ 20k–100k  │              │ Decisions + post-mortems  │
  │ Linear/Jira     │ tickets   │ ~200–500     │ only, not tickets         │
  │                 │           │              │ themselves.               │
  └─────────────────┴───────────┴──────────────┴───────────────────────────┘

  So total wiki ≈ 5k–18k pages. That's past the scale cliff but well inside
  GraphRAG-tier hierarchical territory (5k–50k band). Architecturally fine.

  The four real feasibility blockers

  1. Slack signal density is ~1%. Active channels are 99% chat noise, 1% durable
   decisions. Synthesizing every thread = lighting money on fire. Pattern that
  works: classify-then-synthesize. Haiku-tier classifier on every message ("is
  this a decision / question / chat?"), only ~1% pass to synthesis. Even then
  you need a thread-level unit, not message-level. This is still the most
  expensive lane per useful page extracted — by 5–10×.

     Realistic move: don't ingest live Slack. Ingest the channels labeled
  #architecture-decisions, #incidents, #rfc-*. Let humans flag durable threads
  with an emoji reaction (:wiki:). Most companies that try full-Slack ingest
  abandon it.

  2. Confluence is mostly stale, contradictory, or both. Lint will surface this
  immediately and it'll feel like the system is broken — it isn't, the docs are.
   You need a confidence weight on Confluence-sourced pages and an explicit
  "last verified against code" date. Otherwise the wiki becomes a polished
  version of the same lies.

  3. Cross-link explosion is real. 10k pages × ~8 inbound links each = 80k
  edges. Building the graph at ingest time is fine; maintaining it as code
  drifts is the bookkeeping cost. Two mitigations:
  - Lazy backlinking: links are computed query-time from the graph, not stored
  in page bodies.
  - Drift detector: weekly job re-hashes the cited source region, flags pages
  whose source changed >X% without page update. This is the single most 
  important background job in the whole system. Without it, you have a beautiful
   t=0 snapshot that rots.

  4. Initial scan wall-clock and cost. Concrete estimate, model-cascade
  discipline + batch + cache + skip:

  ┌────────────────────┬──────────┬────────────────┬───────────┬────────────┐
  │        Lane        │  Volume  │     Model      │ Est. cost │    Time    │
  ├────────────────────┼──────────┼────────────────┼───────────┼────────────┤
  │ Pass 0–2 (classify │ full     │ Haiku batch    │ $200–800  │ 1–3 days   │
  │  + L2 summary)     │ repos    │                │           │            │
  ├────────────────────┼──────────┼────────────────┼───────────┼────────────┤
  │ Pass 3 (atomic     │ ~5k      │ Sonnet batch   │ $1k–4k    │ 2–5 days   │
  │ pages, boundaries) │ pages    │                │           │            │
  ├────────────────────┼──────────┼────────────────┼───────────┼────────────┤
  │ Pass 4             │ ~500     │ Opus           │           │            │
  │ (cross-link, Opus, │ hubs     │ interactive    │ $300–1k   │ hours      │
  │  hub pages only)   │          │                │           │            │
  ├────────────────────┼──────────┼────────────────┼───────────┼────────────┤
  │ Confluence ingest  │ ~30k     │ Haiku + dedup  │ $400–1.5k │ 2–4 days   │
  │                    │ docs     │                │           │            │
  ├────────────────────┼──────────┼────────────────┼───────────┼────────────┤
  │ Slack (flagged     │ ~50k     │ Haiku classify │           │            │
  │ only)              │ threads  │  + Sonnet      │ $200–800  │ continuous │
  │                    │          │ synth          │           │            │
  ├────────────────────┼──────────┼────────────────┼───────────┼────────────┤
  │ Total t=0          │          │                │ ~$2k–8k   │ ~1–2 weeks │
  └────────────────────┴──────────┴────────────────┴───────────┴────────────┘

     Steady-state with content-hash skip dominating: probably $200–800/mo for a 
  200-engineer org. The math holds IF skip-rate stays >85%, which it does in
  practice — most code doesn't change weekly.

  Where it actually breaks

  - Monorepos with generated code. 90% of the LOC is protobuf/codegen output.
  Filter by .gitattributes linguist-generated first or the cost explodes.
  - Customer/PII data leaking into pages. Slack and tickets contain customer
  data. Synthesis means it gets embedded into pages forever. You need a
  redaction pass before ingest, and an audit trail. This is a legal blocker, not
   a technical one, in regulated industries.
  - The "who owns this page" problem. Karpathy's wiki has one human curator. A
  200-eng org has nobody. Lint findings need an owner, and owner inference from
  CODEOWNERS is shakier than it sounds.
  - Vendor lock-in on the embedding/model choice. Embedding dim is permanent.
  Pick wrong → re-embed everything later. Allow for it in the schema; you
  already have this for Cranium's embedding_provider.

  Honest scope recommendation

  Don't promise "full hive mind from day 1." Tier the rollout:

  1. Tier 1 (week 1–4): code + ADRs only. Highest signal, cleanest sources,
  hashable. This alone is genuinely useful — engineers query it daily. ~2k–5k
  pages.
  2. Tier 2 (month 2–3): Confluence + decision-tagged Slack channels. Now
  there's a system to ingest into; you're not bootstrapping and ingesting
  simultaneously.
  3. Tier 3 (month 4+): broader Slack via emoji flag, Linear post-mortems, 
  design docs. Only if Tier 1+2 proves out.

  Companies that try to ingest everything at once produce one impressive demo
  and zero sustained adoption. Tier 1 alone clears the "is this useful" bar.

  Bottom line

  Feasible at moderate scale (5k–20k synthesized pages) — yes. Not as a "scan
  everything and produce the wiki." As a continuously-ingesting, hash-gated, 
  tier-rolled-out, drift-detected system with explicit per-source confidence
  weighting and a strict "Slack is flagged-only" rule. The Cranium architecture
  you described is the right shape; the failure mode is treating Slack and
  Confluence as equivalent sources to code. They aren't.

  The pages on this aren't written yet — both the tier-rollout SOP and the
  source-confidence framework would be genuinely reusable. Want me to
  /wiki-ingest them?

✻ Cogitated for 42s

※ recap: Goal: design Cranium's shift to entity-as-wiki-page architecture at moderate-company scale (1M LOC, Confluence, Slack). Recommended tier-rolled rollout starting with code + ADRs only. Next: confirm whether to /wiki-ingest the tier-rollout SOP and source-confidence framework pages. (disable recaps in /config)

❯ What available algorithms/concepts systems that could be utilised by the      
  backend to essentially create this ecosystem is available online or would I   
  most likely need to do a bespoke implementation. I saw                        
  https://github.com/VectifyAI/PageIndex and was wondering if there was         
  impleemntatiosn online thaty could be used for this use case                  

● Mostly mosaic — no single OSS repo does the whole thing. PageIndex solves one 
  slice (long-doc TOC-building), not the system. Here's the honest
  layer-by-layer breakdown of what's actually available.

  Layer-by-layer OSS coverage

  L0 — Content-hash skip

  Bespoke. It's SHA-256 + a KV store. 200 lines of code. No library needed.

  L1 — Retrieval routing manifest

  Bespoke. Yours (retrieval.yaml) already exists. There's no library because the
   format is trivial; the value is the curated routes. Closest reference:
  Anthropic's progressive disclosure pattern + Glean's "people-aware" routing
  (commercial, opaque).

  L2 — Community summaries / hierarchical RAG

  This is where OSS is rich. All implement the same shape from
  [[hierarchical-rag-isomorphism]]:

  Repo: microsoft/graphrag
  What it does: Eager Leiden clustering → community summaries. Python,
    well-maintained.
  When to grab: Best-known baseline. Expensive to build ($33k → $33 cliff).
  ────────────────────────────────────────
  Repo: microsoft/lazygraphrag
  What it does: Same shape, query-time summary synthesis.
  When to grab: When build cost is the blocker. KET-RAG benchmark: ~10× cheaper
    than GraphRAG.
  ────────────────────────────────────────
  Repo: gusye1234/nano-graphrag
  What it does: ~1k LOC reimpl of GraphRAG.
  When to grab: Read this first — it's the spec in code.
  ────────────────────────────────────────
  Repo: HKUDS/LightRAG
  What it does: Dual-level (local/global) retrieval, simpler than GraphRAG.
  When to grab: Active, well-cited 2024 paper.
  ────────────────────────────────────────
  Repo: OSU-NLP-Group/HippoRAG (+ HippoRAG2)
  What it does: PersonalizedPageRank over an LLM-extracted KG.
  When to grab: Strong on multi-hop.
  ────────────────────────────────────────
  Repo: parthsarthi03/raptor
  What it does: Recursive abstractive tree. Original RAPTOR paper code.
  When to grab: Cleaner tree than community summaries; smaller scale.
  ────────────────────────────────────────
  Repo: VectifyAI/PageIndex
  What it does: TOC-tree of a single long doc.
  When to grab: Useful for the Confluence/PDF lane. Not corpus-level.

  PageIndex specifically: it builds a hierarchical table-of-contents over one 
  document, so a 500-page contract becomes navigable. Useful as a sub-routine
  inside the doc ingest lane. Not a substitute for GraphRAG-style cross-document
   community structure.

  L3 — Atomic page synthesis (the Karpathy "wiki" part)

  No off-the-shelf library. This is where you build. Closest building blocks:

  - LlamaIndex DocumentSummaryIndex / TreeIndex / PropertyGraphIndex —
  primitives for tree synthesis, not the curated-page pattern.
  - LangChain LLMGraphTransformer — entity/relation extraction.
  - Neo4j LLM Graph Builder — has a working extraction pipeline.
  - REBEL / mREBEL — supervised relation extraction (cheap, deterministic, no
  LLM).

  What's missing: the "atomic ≤300-word insight page with frontmatter kind,
  cross-links, and lint discipline" pattern is a prompt engineering problem, not
   a library gap. The Karpathy pattern is novel enough that no one has packaged
  it. You'd extend LlamaIndex's tree builder.

  Codebase understanding lane

  This is where it gets interesting — strong OSS:

  ┌─────────────────┬───────────────────────────────────────────────────────┐
  │      Tool       │                       Algorithm                       │
  ├─────────────────┼───────────────────────────────────────────────────────┤
  │ tree-sitter     │ Parse 40+ languages → AST. The foundation.            │
  ├─────────────────┼───────────────────────────────────────────────────────┤
  │                 │ PageRank over the symbol graph (defs ↔ refs) to pick  │
  │ aider's RepoMap │ most-important context. Read aider/repomap.py — 600   │
  │                 │ LOC, very copyable.                                   │
  ├─────────────────┼───────────────────────────────────────────────────────┤
  │ Sourcegraph     │ Industrial-grade semantic code indexes.               │
  │ SCIP/LSIF       │ Language-server-protocol-derived.                     │
  ├─────────────────┼───────────────────────────────────────────────────────┤
  │ Continue.dev    │ Open-source, chunking + embedding strategy worth      │
  │ codebase        │ reading.                                              │
  │ indexer         │                                                       │
  ├─────────────────┼───────────────────────────────────────────────────────┤
  │ claude-flow /   │ Repo-grokking heuristics in TypeScript.               │
  │ cline / Roo     │                                                       │
  ├─────────────────┼───────────────────────────────────────────────────────┤
  │ OpenAI Codex    │ Worth reading for prompt patterns.                    │
  │ CLI's repo scan │                                                       │
  └─────────────────┴───────────────────────────────────────────────────────┘

  aider's RepoMap is the closest free lunch for the "what's important in this 
  million-LOC repo" problem. It picks the boundary modules for you. Bolt this on
   the front of Pass 1 in the tier-rollout plan.

  Connectors (GitHub / Slack / Confluence / Notion)

  Commodity:

  - Onyx (formerly Danswer) — onyx-dot-app/onyx. OSS enterprise search. Already
  has GitHub, Slack, Confluence, Notion, GDrive, Jira connectors. If you want
  the most reusable thing in this whole list, this is it. You'd strip out their
  answer pipeline and keep the connector layer.
  - LlamaHub — 200+ data loaders.
  - LangChain document loaders — same idea, slightly worse curation.
  - Glean / Hebbia / Vectara — commercial reference implementations.

  Vector store + hybrid

  You already have pgvector. Alternatives:
  - Qdrant, Weaviate, LanceDB (file-based, nice for embedded use).
  - Vespa if you ever need hybrid BM25+vector at >100M scale.
  - bge-reranker-v2 / Cohere Rerank / Voyage rerank for the rerank stage.

  Cost-stack levers

  - Anthropic prompt caching — native API feature.
  - GPTCache — semantic LLM-response caching (separate from prefix caching).
  - vLLM / TGI — for self-hosted small-model cascade tier.
  - RouteLLM (lm-sys/RouteLLM) — the actual cascade router from the paper.
  Drop-in.

  Memory / persistence

  - Mem0 — agent memory layer, OSS, has the "extract facts → store → retrieve"
  pattern. Could inform the entity-as-page work.
  - Letta (formerly MemGPT) — hierarchical memory with eviction. Less applicable
   but related.

  What's bespoke (the actual moat)

  What you'd build yourself, in priority order:

  1. The compile-to-wiki-page prompt pipeline. Karpathy's pattern as prompts.
  ~10 prompt templates, one per kind. This is the highest-leverage bespoke
  piece.
  2. The drift detector / re-ingest loop. Hash diff → identify affected pages →
  re-synthesize. Your wiki-sync-diff skill is the prototype.
  3. Cross-source attribution model. One page cites code + Slack + ticket. No
  OSS does this cleanly.
  4. The lint pass (dead links, contradictions, kind violations). Bespoke;
  cheap.
  5. The orchestration glue (Temporal/Inngest workflows tying it together).
  Cranium already has the engine.

  Pragmatic stack recommendation

  If I were building this Monday:

  Connectors:     Onyx (strip the answer layer, keep connectors)
  Code AST/rank:  tree-sitter + aider's repo-map algorithm (port to Kotlin)
  Doc TOC:        PageIndex for long PDFs/Confluence pages
  L2 community:   nano-graphrag as reference impl → port to your schema
                  (or LightRAG if you want lower-friction)
  Reranker:       bge-reranker-v2 (self-host) or Cohere
  Vector:         pgvector (already have)
  Cache/cascade:  Anthropic native cache + RouteLLM
  Synthesis:      bespoke — this is the Cranium IP
  Drift loop:     bespoke — this is the other Cranium IP

  Where it actually stops being feasible OSS-only

  - Cross-source semantic linking (a Slack decision that's about a PR that's
  about an ADR). Nothing OSS does this well. Bespoke. Probably the hardest
  single problem in the system.
  - Confidence weighting per source (your Confluence-is-mostly-lies problem). No
   library. Bespoke; small.
  - The "owner inference" problem (who keeps this page alive). Hard. Probably
  needs a UI loop with humans, not pure algorithm.

  Honest summary

  ~60% of the plumbing exists OSS. Connectors + L2 community structures +
  code-rank + rerank + caching are all solved problems. ~40% — the Karpathy 
  synthesis half, the cross-source attribution, and the drift-loop — is bespoke,
   and that's where the actual Cranium product value lives.

  PageIndex is a useful sub-component, not a contender for the whole system.
  Onyx + nano-graphrag + aider's repo-map are the three repos worth reading in
  full before you write a line.

  Coverage gap + promotion offer

  No wiki page on OSS building-block inventory for LLM-wiki systems anywhere in
  the vault. Useful as a Wiki/Dev/pages/llm-wiki-oss-stack.md [decision] so this
   lookup doesn't have to be redone. Want me to /wiki-ingest it?

✻ Churned for 1m 14s

❯ If this was a progromattic approach, somethign that couldbe structured        
  through a postghres database and a springboot server. What would be the       
  structure for each layer and capability                                       

● Concrete schema + Spring Boot layout below. Built to fit Cranium's existing
  patterns (Temporal, pgvector, workspace RLS, JSONB-heavy). Six layers, each
  with its tables + services.

  Database schema (Postgres)

  Layer 0 — Source registry + content hashing

  -- Where every piece of source material lives + its hash
  create table source_pointers (
    id                uuid primary key default gen_random_uuid(),
    workspace_id      uuid not null,
    source_type       text not null,           -- 'github_file' | 'confluence' |
   'slack_thread' | 'notion_page' | 'linear_issue'
    source_uri        text not null,           -- canonical URI: 
  gh://org/repo/path@sha, confluence://space/page-id, slack://channel/thread-ts
    source_metadata   jsonb not null default '{}',
    content_hash      bytea not null,          -- sha256 of cited region
    byte_range        int8range,               -- if pointing at a chunk of a 
  file
    last_seen_at      timestamptz not null,
    last_changed_at   timestamptz not null,
    ingest_status     text not null default 'pending',  -- 'pending' | 
  'classified' | 'synthesized' | 'skipped' | 'failed'
    ingest_lane       text,                    -- 'code' | 'doc' | 'chat' | 
  'ticket'
    confidence_weight real default 1.0,        -- doc rot multiplier; 0.2 for 
  old Confluence, 1.0 for code
    unique(workspace_id, source_uri)
  );

  create index source_pointers_hash_idx on source_pointers (workspace_id,
  content_hash);
  create index source_pointers_status_idx on source_pointers (workspace_id,
  ingest_status) where ingest_status in ('pending', 'failed');

  -- Skip table — the cheapest lever in the whole system
  create table content_hash_ledger (
    workspace_id     uuid not null,
    content_hash     bytea not null,
    derived_page_ids uuid[] not null,          -- pages produced from this hash
    synthesized_at   timestamptz not null,
    model_used       text not null,
    primary key (workspace_id, content_hash)
  );

  Layer 1 — Routing manifest

  -- DB-backed routing (mirrors retrieval.yaml but mutable, per-workspace)
  create table retrieval_routes (
    id              uuid primary key default gen_random_uuid(),
    workspace_id    uuid not null,
    pattern_tokens  text[] not null,           -- ['identity', 'match']
    aliases         text[][] default '{}',     -- [['ingest', 'entitie'], 
  ['external', 'id']]
    load_paths      text[] not null,           -- page IDs OR source URIs
    why             text,
    match_count     bigint not null default 0,
    last_matched_at timestamptz,
    created_by      text,
    unique(workspace_id, pattern_tokens)
  );

  create index retrieval_routes_tokens_gin on retrieval_routes using gin
  (pattern_tokens);

  -- Fall-through log (what /wiki-routes-review reads)
  create table retrieval_suggestions (
    id              uuid primary key default gen_random_uuid(),
    workspace_id    uuid not null,
    query_text      text not null,
    query_tokens    text[] not null,
    occurred_at     timestamptz not null default now(),
    promoted_to_route uuid references retrieval_routes(id)
  );

  Layer 2 — Community / hierarchical summaries

  -- The GraphRAG-style community tree. Tier maps to your scale-cliff table.
  create table community_nodes (
    id              uuid primary key default gen_random_uuid(),
    workspace_id    uuid not null,
    parent_id       uuid references community_nodes(id),
    tier            int not null,              -- 0=root, 1=repo/domain, 
  2=feature/module, 3=submodule
    title           text not null,
    summary         text not null,             -- ≤500 words, LLM-generated
    summary_hash    bytea not null,            -- so we can detect when summary 
  itself drifts
    member_page_ids uuid[] not null default '{}',
    embedding       vector(1536),              -- pgvector — over the summary
    cluster_algo    text,                      -- 'leiden' | 'manual' | 
  'codeowners' | 'directory'
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
  );

  create index community_nodes_parent_idx on community_nodes (workspace_id,
  parent_id);
  create index community_nodes_embedding_hnsw on community_nodes using hnsw
  (embedding vector_cosine_ops);

  Layer 3 — Pages (the wiki entities)

  -- Each entity in Cranium that participates in the wiki IS a page row.
  -- If you already have an `entities` table, this is either a join table or a 
  discriminated subtype.
  create table wiki_pages (
    id              uuid primary key default gen_random_uuid(),
    workspace_id    uuid not null,
    slug            text not null,             -- 
  'how-identity-match-pipeline-runs'
    kind            text not null,             -- 'decision' | 'flow' | 
  'feature' | 'interaction' | 'sop' | 'insight' | 'framework'
    title           text not null,
    body            text not null,             -- ≤300 words markdown
    body_hash       bytea not null,            -- detect drift between 
  synthesized page + source hash
    tags            text[] default '{}',
    community_id    uuid references community_nodes(id),
    embedding       vector(1536),
    confidence      real not null default 1.0, -- weighted by source confidence
    last_verified_at timestamptz,              -- when drift detector last 
  confirmed alignment with source
    synthesis_model text,                      -- 'haiku' | 'sonnet' | 'opus'
    synthesis_cost_usd numeric(10,4),          -- for cost dashboards
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    unique(workspace_id, slug)
  );

  create index wiki_pages_kind_idx on wiki_pages (workspace_id, kind);
  create index wiki_pages_tags_gin on wiki_pages using gin (tags);
  create index wiki_pages_embedding_hnsw on wiki_pages using hnsw (embedding
  vector_cosine_ops);
  create index wiki_pages_drift_idx on wiki_pages (workspace_id,
  last_verified_at) where last_verified_at < now() - interval '30 days';

  -- Page → source provenance (many-to-many)
  create table wiki_page_sources (
    page_id           uuid not null references wiki_pages(id) on delete cascade,
    source_pointer_id uuid not null references source_pointers(id) on delete
  cascade,
    citation_role     text not null,           -- 'primary' | 'supporting' | 
  'contradicting'
    cited_at_hash     bytea not null,          -- hash of source content AT TIME
   OF synthesis (for drift detection)
    primary key (page_id, source_pointer_id)
  );

  create index wiki_page_sources_source_idx on wiki_page_sources
  (source_pointer_id);

  Cross-link graph

  -- Wikilinks as first-class edges. Lazy/computed if you prefer, but storing 
  makes graph queries fast.
  create table wiki_page_links (
    src_page_id     uuid not null references wiki_pages(id) on delete cascade,
    dst_page_id     uuid references wiki_pages(id) on delete set null,
    dst_slug        text not null,             -- preserved so dangling links 
  survive deletion
    link_kind       text not null default 'reference',  -- 'reference' | 
  'supersedes' | 'contradicts' | 'derived_from'
    created_at      timestamptz not null default now(),
    primary key (src_page_id, dst_slug)
  );

  create index wiki_page_links_dst_idx on wiki_page_links (dst_page_id) where
  dst_page_id is not null;
  create index wiki_page_links_dangling_idx on wiki_page_links (workspace_id,
  dst_slug) where dst_page_id is null;

  Ingest jobs + drift detection

  -- One row per Temporal workflow invocation. Mirrors your existing sync_state 
  pattern.
  create table ingest_jobs (
    id              uuid primary key default gen_random_uuid(),
    workspace_id    uuid not null,
    source_type     text not null,
    lane            text not null,             -- 'code' | 'doc' | 'chat' | 
  'ticket'
    pass            int not null,              -- 0=hash, 1=classify, 2=L2 
  summary, 3=L3 page, 4=cross-link
    status          text not null,
    source_count    int,
    skipped_count   int,                       -- the hash-skip win
    llm_calls       int,
    cost_usd        numeric(10,4),
    started_at      timestamptz,
    finished_at     timestamptz,
    temporal_workflow_id text,
    temporal_run_id text
  );

  -- Append-only drift log
  create table page_drift_events (
    id              uuid primary key default gen_random_uuid(),
    page_id         uuid not null references wiki_pages(id),
    detected_at     timestamptz not null default now(),
    old_source_hash bytea not null,
    new_source_hash bytea not null,
    resolved_at     timestamptz,
    resolution      text                        -- 'resynthesized' | 'no_op' | 
  'manually_edited' | 'page_retired'
  );

  create index page_drift_open_idx on page_drift_events (page_id) where
  resolved_at is null;

  Lint findings

  create table lint_findings (
    id              uuid primary key default gen_random_uuid(),
    workspace_id    uuid not null,
    page_id         uuid references wiki_pages(id) on delete cascade,
    finding_type    text not null,             -- 'dead_link' | 'orphan' | 
  'duplicate' | 'stale' | 'contradiction' | 'kind_violation'
    detail          jsonb not null,
    detected_at     timestamptz not null default now(),
    resolved_at     timestamptz
  );

  Spring Boot package layout

  Mirrors Cranium's existing service/{integration,entity,workflow} shape. New
  top-level domain: wiki.

  cranium.core.wiki
  ├── connector/                          ← L0 ingest sources
  │   ├── ConnectorRegistry.kt            ← discriminator on source_type
  │   ├── github/
  │   │   ├── GithubRepoConnector.kt
  │   │   ├── GithubFileWalker.kt
  │   │   └── TreeSitterAstService.kt     ← parse → symbols → import edges
  │   ├── confluence/
  │   │   └── ConfluencePageConnector.kt
  │   ├── slack/
  │   │   ├── SlackThreadConnector.kt
  │   │   └── SlackSignalClassifier.kt    ← cheap Haiku gate; throws away 99% of
   messages
  │   ├── notion/
  │   ├── linear/
  │   └── Connector.kt                    ← interface: discover() + fetch(uri) +
   hash(content)
  │
  ├── hash/                               ← L0 the skip lever
  │   ├── ContentHashService.kt
  │   └── ContentHashLedgerRepository.kt
  │
  ├── route/                              ← L1 routing manifest
  │   ├── RetrievalRouteRepository.kt
  │   ├── RouteMatcher.kt                 ← token-set subset, plural stemming
  (port of your YAML logic)
  │   ├── RouteSuggestionLogger.kt        ← writes to retrieval_suggestions
  │   └── RouteReviewService.kt           ← suggests new routes from suggestion
  log
  │
  ├── community/                          ← L2 hierarchical summaries
  │   ├── CommunityBuilder.kt             ← orchestrates Leiden / directory /
  codeowners clustering
  │   ├── LeidenClusteringService.kt      ← over the wikilink graph (call out to
   graph-tool or port from nano-graphrag)
  │   ├── CommunitySummaryService.kt      ← LLM compress N pages → 1 summary
  │   └── CommunityNodeRepository.kt
  │
  ├── page/                               ← L3 page synthesis
  │   ├── WikiPageRepository.kt
  │   ├── WikiPageSourceRepository.kt
  │   ├── WikiPageLinkRepository.kt
  │   ├── synthesis/
  │   │   ├── PageSynthesisService.kt     ← top-level dispatch by kind
  │   │   ├── DecisionPageSynthesizer.kt  ← one per kind, each owns its prompt
  template
  │   │   ├── FlowPageSynthesizer.kt
  │   │   ├── FeaturePageSynthesizer.kt
  │   │   ├── InteractionPageSynthesizer.kt
  │   │   ├── SopPageSynthesizer.kt
  │   │   ├── InsightPageSynthesizer.kt
  │   │   ├── FrameworkPageSynthesizer.kt
  │   │   └── PromptTemplateLoader.kt     ← stable prefix for cache hits
  │   └── linker/
  │       ├── CrossLinkResolver.kt        ← extracts [[wikilinks]] from body,
  persists wiki_page_links
  │       └── DanglingLinkScheduler.kt    ← retry resolution when new pages land
  │
  ├── retrieval/                          ← read-side
  │   ├── WikiQueryService.kt             ← L1 → L2 → L3 progressive disclosure
  │   ├── HybridRetriever.kt              ← pgvector + tsvector BM25 + rerank
  │   ├── RerankerClient.kt               ← bge-reranker self-host or Cohere
  │   └── ProgressiveContextAssembler.kt  ← under context-budget, descend tree
  │
  ├── ingestion/                          ← orchestration
  │   ├── IngestionOrchestrator.kt        ← entry: ingest(source_uri) → pick
  lane → enqueue Temporal
  │   ├── workflow/
  │   │   ├── IngestionWorkflow.kt        ← Temporal workflow def: Pass0..Pass4
  │   │   ├── Pass0HashActivity.kt        ← discover + hash; skip if ledger hit
  │   │   ├── Pass1ClassifyActivity.kt    ← Haiku batch
  │   │   ├── Pass2SummarizeActivity.kt   ← Haiku batch → community summary
  │   │   ├── Pass3SynthesizeActivity.kt  ← Sonnet batch → atomic pages
  │   │   ├── Pass4CrosslinkActivity.kt   ← Opus, reserved, agentic
  │   │   └── DriftCheckWorkflow.kt       ← scheduled — re-hashes cited regions
  │   └── lane/                           ← lane-specific tuning of the pipeline
  │       ├── CodeLane.kt                 ← runs aider-repomap-style PageRank
  first
  │       ├── DocLane.kt                  ← runs PageIndex-style TOC build first
  │       ├── ChatLane.kt                 ← runs SignalClassifier, drops 99%
  │       └── TicketLane.kt
  │
  ├── llm/                                ← cost-stack levers (these are the hot
   path)
  │   ├── CascadeRouter.kt                ← classify → pick Haiku/Sonnet/Opus
  (RouteLLM-style)
  │   ├── PromptCacheBuilder.kt           ← assembles head=stable / tail=dynamic
  │   ├── BatchSubmissionService.kt       ← Anthropic Message Batches / OpenAI
  Batch
  │   ├── BatchPollingActivity.kt         ← Temporal activity; 24h SLA
  │   ├── ModelClient.kt                  ← thin Anthropic/OpenAI wrapper
  │   └── TokenAccountingService.kt       ← writes ingest_jobs.cost_usd
  │
  ├── drift/                              ← keeping the wiki alive
  │   ├── DriftDetector.kt                ← scheduled: rehash sources, emit
  page_drift_events
  │   ├── DriftResolutionService.kt       ← decides: resynthesize / no-op /
  retire
  │   └── DriftPolicy.kt                  ← thresholds: how stale before
  re-synth
  │
  ├── lint/
  │   ├── LintService.kt
  │   ├── DeadLinkRule.kt
  │   ├── OrphanRule.kt
  │   ├── DuplicateRule.kt
  │   ├── StaleRule.kt
  │   ├── ContradictionRule.kt            ← LLM-judged; expensive; runs weekly
  │   └── KindViolationRule.kt
  │
  ├── api/
  │   ├── WikiQueryController.kt          ← GET /wiki/query?q=…
  │   ├── WikiPageController.kt           ← REST CRUD over pages (read mostly)
  │   ├── IngestionController.kt          ← POST /wiki/ingest (manual trigger)
  │   └── WikiAdminController.kt          ← drift queue, lint findings, route
  stats
  │
  └── event/
      ├── PageCreatedEvent.kt             ← extends WorkspaceEvent — STOMP
  broadcast
      ├── PageDriftedEvent.kt
      └── WikiEventListener.kt            ← AFTER_COMMIT hooks

  Temporal task queues

  Mirror your existing temporal-task-queue-isolation pattern:

  wiki.ingest.code        ← Pass 0–2 over repos; heavy batch
  wiki.ingest.doc         ← Confluence/Notion
  wiki.ingest.chat        ← Slack signal extraction
  wiki.synthesis          ← Pass 3 — Sonnet calls
  wiki.crosslink          ← Pass 4 — Opus, low-volume
  wiki.drift              ← scheduled hash diffs
  wiki.lint               ← weekly

  Separates latency profiles. Sonnet batch jobs don't starve interactive query
  work.

  Read path — query lifecycle

  GET /wiki/query?q="how does workflow queue avoid double-dispatch"
         │
         ▼
  WikiQueryService.query(q)
         │  1. RouteMatcher.match(q) → L1 route hit on "double dispatch"?
         │     yes → return load_paths (deterministic, ~10ms, ~$0)
         │     no  → continue + log to retrieval_suggestions
         │
         │  2. HybridRetriever
         │       │  a. embed(q) → pgvector ANN top-K (K=20)
         │       │  b. tsvector BM25 top-K
         │       │  c. RRF merge → top-30
         │       │  d. RerankerClient.rerank(q, top-30) → top-5
         │
         │  3. ProgressiveContextAssembler
         │       │  a. read L2 community summaries for top-5 pages
         │       │  b. read full page bodies if under budget
         │       │  c. expand wikilinks once (depth-1) if budget allows
         │
         └──► LLM synthesis → response + cited [[pages]]

  Write path — ingest lifecycle (one source)

  PR merged → webhook → IngestionOrchestrator
         │
         ▼
  For each changed file in PR:
    Pass 0 — ContentHashService.hash(file)
             if hash ∈ content_hash_ledger → SKIP (the win)
             else continue
         │
         ▼
    Pass 1 — Haiku via BatchSubmission
             classify: { domain, kind_candidate, boundary? }
             Pages only created for boundary files + ADRs + entry-point flows.
         │
         ▼
    Pass 2 — CommunitySummaryService
             rebuild affected community summary if member set changed
         │
         ▼
    Pass 3 — DecisionPageSynthesizer / FlowPageSynthesizer / …
             Sonnet batch, prompt-cached prefix.
             Output → wiki_pages + wiki_page_sources rows.
         │
         ▼
    Pass 4 — CrossLinkResolver
             parses [[links]] from body → wiki_page_links
             Opus only if hub page (>10 inbound links) or contradiction
  suspected.
         │
         ▼
    Update content_hash_ledger
    Emit PageCreatedEvent / PageDriftedEvent

  Cost-instrumented invariants

  Two things you'd want as hard service contracts, enforced at the
  TokenAccountingService:

  1. Pass 3 / Pass 4 cost ceiling per workspace per day. Trip → degrade
  gracefully (drop Pass 4, queue Pass 3 to batch). Prevents runaway bills.
  2. Skip-rate floor for resync. If skip-rate drops below 70% on a resync run,
  alert. Means hashing is broken (whitespace, line endings, etc.) and you're
  re-paying for unchanged content.

  What you'd build on top of existing Cranium

  You already have:
  - Temporal orchestration → IngestionWorkflow slots in
  - pgvector + embedding provider strategy → wiki_pages.embedding,
  community_nodes.embedding
  - Workspace RLS → applies to every wiki table
  - Workflow + queue isolation → new task queues fit the pattern
  - Domain events → PageCreatedEvent extends WorkspaceEvent for STOMP broadcast
  - Webhook → connection lifecycle → trigger for IngestionOrchestrator

  You'd add:
  - wiki package (above)
  - BatchSubmissionService + BatchPollingActivity (new; Anthropic Batch API)
  - CascadeRouter (new)
  - LeidenClusteringService (new; either JNI to graph-tool, or port from
  nano-graphrag in Python sidecar)
  - RerankerClient (new; self-host bge-reranker as separate JVM service, or
  Cohere)

  Coverage gap + promotion offer

  No page anywhere in the vault on "engine schema for LLM-wiki at scale" — this
  whole answer is reusable IP. Worth /wiki-ingest into two pages:

  1. Wiki/Cranium/pages/wiki-engine-schema.md [feature] — the Postgres schema +
  lane separation specific to Cranium's pattern set.
  2. Wiki/Dev/pages/llm-wiki-engine-architecture.md [framework] — generalized,
  codebase-agnostic version. Same shape, no Cranium specifics.

  Want me to write either or both?

✻ Brewed for 1m 37s

❯ would I use any off the shelf engine/algorithms or hosted services to perform 
   any oof thse actions?                                                        

● Buy-vs-build call per service. Going layer-by-layer through the package layout
   I just sketched.

  Verdict per service

  Service: connector/github/*
  Recommendation: Build thin, OSS guts
  Why: Use JGit for repo walking + GitHub REST/GraphQL via official SDK. Onyx's
    connector is OSS-readable but you're better with native JVM.
  ────────────────────────────────────────
  Service: connector/confluence/*
  Recommendation: Hosted
  Why: Confluence REST API directly. Don't roll page-parsing — use Apache Tika
    (already in Cranium for magic-byte) for content extraction.
  ────────────────────────────────────────
  Service: connector/slack/*
  Recommendation: Build, but use OAuth from Nango
  Why: You already have Nango for OAuth. Slack Web API for thread fetch is
    straightforward. Don't try to use a meta-connector layer here.
  ────────────────────────────────────────
  Service: connector/notion/*
  Recommendation: Nango sync scripts
  Why: Notion has a Nango integration. You already have the sync infra.
  ────────────────────────────────────────
  Service: connector/linear/*
  Recommendation: Nango
  Why: Same.
  ────────────────────────────────────────
  Service: TreeSitterAstService
  Recommendation: Off-the-shelf
  Why: tree-sitter native lib + tree-sitter-jvm (JNI bindings). Don't reinvent
    parsing. Covers 40+ languages.
  ────────────────────────────────────────
  Service: RepoMap / PageRank
  Recommendation: Port aider's algorithm
  Why: ~600 LOC of well-tested Python. Port to Kotlin using JGraphT for graph +
    PageRank. ~1 week.
  ────────────────────────────────────────
  Service: PageIndex long-doc TOC
  Recommendation: OSS lib via Python sidecar
  Why: VectifyAI's PageIndex. Spin up as a Python microservice — porting isn't
    worth it.
  ────────────────────────────────────────
  Service: LeidenClusteringService
  Recommendation: JGraphT or Python sidecar
  Why: JGraphT has Leiden in recent versions. If not, graph-tool in Python
    sidecar. Don't write your own community detection.
  ────────────────────────────────────────
  Service: embedding provider
  Recommendation: Hosted — Voyage AI for code, OpenAI text-embedding-3-large for
   
    prose
  Why: You already have embedding-provider-strategy. Voyage's voyage-code-3 is
    best-in-class for code embeddings — Anthropic uses it.
  ────────────────────────────────────────
  Service: pgvector storage
  Recommendation: Off-the-shelf
  Why: Already in Cranium. HNSW indexes. No reason to add Qdrant/Weaviate.
  ────────────────────────────────────────
  Service: RerankerClient
  Recommendation: Hosted — Cohere Rerank v3 or Voyage rerank-2
  Why: Self-hosting bge-reranker is a hardware decision. Cohere/Voyage are
  ~$2/1k
    searches. Use hosted unless you're at scale.
  ────────────────────────────────────────
  Service: CascadeRouter
  Recommendation: OSS — RouteLLM
  Why: lm-sys/RouteLLM is a drop-in. Python sidecar. Saves you 5–10× LLM cost.
    Worth the integration.
  ────────────────────────────────────────
  Service: PromptCacheBuilder
  Recommendation: Off-the-shelf — Anthropic native
  Why: Just use cache_control: ephemeral on the prompt prefix. No library.
  ────────────────────────────────────────
  Service: BatchSubmissionService
  Recommendation: Off-the-shelf — Anthropic Message Batches API
  Why: Native API. 50% off, 24h SLA. SDK supports it.
  ────────────────────────────────────────
  Service: BatchPollingActivity
  Recommendation: Build (thin)
  Why: Just a Temporal activity that polls /v1/messages/batches/{id}. ~50 LOC.
  ────────────────────────────────────────
  Service: ModelClient
  Recommendation: Anthropic JVM SDK
  Why: Don't build a wrapper around HTTP. Use the official SDK.
  ────────────────────────────────────────
  Service: TokenAccountingService
  Recommendation: Hosted — Helicone OR Langfuse
  Why: See below.
  ────────────────────────────────────────
  Service: LLM observability (eval, tracing, prompt versioning)
  Recommendation: Hosted — Langfuse (self-hostable) or LangSmith
  Why: Critical and not worth building. See below.
  ────────────────────────────────────────
  Service: cross-link resolver (parsing [[wikilinks]])
  Recommendation: Build
  Why: Regex. Trivial.
  ────────────────────────────────────────
  Service: synthesis prompts (one per kind)
  Recommendation: Build
  Why: This is the Cranium IP. No off-the-shelf prompts match the Karpathy
    pattern.
  ────────────────────────────────────────
  Service: drift detector
  Recommendation: Build
  Why: SHA-256 diff. Scheduled job. ~200 LOC.
  ────────────────────────────────────────
  Service: lint rules
  Recommendation: Build
  Why: Bespoke. Contradiction-check rule can use an LLM judge.
  ────────────────────────────────────────
  Service: KG extraction (entity + relation extraction from sources)
  Recommendation: Mix
  Why: REBEL for cheap deterministic relations. LangChain LLMGraphTransformer as

    Python sidecar for richer extraction.
  ────────────────────────────────────────
  Service: tokenizer for L1 routing
  Recommendation: Build, simple
  Why: Your retrieval.yaml tokenizer is already implemented. Don't drag Lucene
    in.
  ────────────────────────────────────────
  Service: hybrid retriever (BM25 + vector)
  Recommendation: Off-the-shelf — Postgres tsvector
  Why: Don't add Elasticsearch. Postgres to_tsvector + RRF in SQL is enough up
  to
    ~10M docs.
  ────────────────────────────────────────
  Service: RRF merge
  Recommendation: Build
  Why: 30 LOC.
  ────────────────────────────────────────
  Service: Temporal orchestration
  Recommendation: Off-the-shelf
  Why: Already in Cranium.

  Hosted services worth paying for

  Five things where hosting beats self-hosting even at scale:

  1. Anthropic Batch API + Prompt Caching (native)

  Stack levers from [[ingest-cost-stacking]]. Use both. No library, just the SDK
   flags. 90% off cache hits + 50% off batch = 95% combined discount on the
  steady-state ingest lane. Non-optional.

  2. Voyage AI (embeddings + reranker)

  Anthropic's recommended embedding partner. voyage-code-3 for code,
  voyage-3-large for prose, rerank-2.5 for the rerank stage. Pricing: ~$0.12/M
  tokens embed, ~$0.05/1k rerank. Compared to self-hosting bge-reranker on a
  GPU: hosted wins until you're doing >100k queries/day.

  Alternative: Cohere embed-v3 + rerank-v3 — slightly cheaper, slightly worse on
   code. Either is fine.

  3. Langfuse (LLM observability)

  This is the one most people skip and regret. You need:
  - Per-page synthesis trace (which prompt, which model, which sources, what
  came out)
  - Eval scoring (was the synthesized page actually good?)
  - Prompt versioning (so you can A/B kind-specific prompts)
  - Cost attribution per workspace per lane

  Langfuse is OSS + has a hosted tier. Self-host with Postgres if you want.
  LangSmith is the competitor, more polished but proprietary. Helicone is a
  third option, lighter, more focused on cost.

  Pick one early. Adding observability later = throwing away weeks of debugging
  data.

  4. Cohere or Voyage rerank (hosted)

  Already noted. Self-hosting reranker is fine but the operational cost (GPU
  instance, eval, model updates) is the kind of thing that distracts from
  product work. Pay $2/1k searches until you have a reason not to.

  5. RouteLLM or Martian Router (model cascade)

  RouteLLM is OSS (you self-host the router). Martian is a hosted alternative
  that does the routing for you and bills you one rate. For Cranium scale,
  RouteLLM-self-hosted is the move — Martian's margin compounds.

  Hosted services to skip

  ┌──────────────────────┬──────────────────────────────────────────────────┐
  │       Service        │                     Why skip                     │
  ├──────────────────────┼──────────────────────────────────────────────────┤
  │ Pinecone / Qdrant    │ You have pgvector. Adding a separate vector      │
  │ Cloud / Weaviate     │ store doubles operational surface for no benefit │
  │ Cloud                │  until ~50M vectors.                             │
  ├──────────────────────┼──────────────────────────────────────────────────┤
  │ Glean / Hebbia /     │ These are the competitors. Reading their         │
  │ Vectara              │ marketing for architecture clues is fine.        │
  ├──────────────────────┼──────────────────────────────────────────────────┤
  │                      │ Their memory pattern is interesting but you'd    │
  │ Mem0 Cloud           │ build the wrapper anyway. Mem0 OSS code is worth │
  │                      │  reading.                                        │
  ├──────────────────────┼──────────────────────────────────────────────────┤
  │ Onyx Cloud           │ The OSS version is the value. Self-host the      │
  │                      │ connector layer if you want it.                  │
  ├──────────────────────┼──────────────────────────────────────────────────┤
  │ Notion / Confluence  │                                                  │
  │ / Slack "AI"         │ Vendor-specific, won't generalize, expensive.    │
  │ features             │                                                  │
  ├──────────────────────┼──────────────────────────────────────────────────┤
  │ OpenAI Assistants    │ Lock-in. The wrong abstraction layer.            │
  │ API                  │                                                  │
  ├──────────────────────┼──────────────────────────────────────────────────┤
  │                      │ Useful for the doc lane specifically but         │
  │ Anthropic Files API  │ evaluate vs your own storage strategy. Not       │
  │                      │ load-bearing.                                    │
  └──────────────────────┴──────────────────────────────────────────────────┘

  Algorithm vs library cheat sheet

  For the bespoke parts, the algorithms you'll implement are well-described:

  ┌──────────────────────┬───────────────┬──────────────────────────────────┐
  │      Algorithm       │     Where     │          Reference impl          │
  ├──────────────────────┼───────────────┼──────────────────────────────────┤
  │ PageRank over symbol │ RepoMap /     │ aider repomap.py, JGraphT        │
  │  graph               │ boundary      │ PageRank                         │
  │                      │ detection     │                                  │
  ├──────────────────────┼───────────────┼──────────────────────────────────┤
  │ Leiden community     │ L2 hierarchy  │ nano-graphrag/_op.py, JGraphT    │
  │ detection            │               │ LeidenAlgorithm                  │
  ├──────────────────────┼───────────────┼──────────────────────────────────┤
  │ Reciprocal Rank      │ Hybrid        │                                  │
  │ Fusion (RRF)         │ retrieval     │ 30 LOC, see Microsoft's docs     │
  │                      │ merge         │                                  │
  ├──────────────────────┼───────────────┼──────────────────────────────────┤
  │ Maximal Marginal     │ Diversity in  │ Standard formula, ~50 LOC        │
  │ Relevance (MMR)      │ retrieval     │                                  │
  ├──────────────────────┼───────────────┼──────────────────────────────────┤
  │ HNSW                 │ Vector index  │ pgvector built-in                │
  ├──────────────────────┼───────────────┼──────────────────────────────────┤
  │ HyDE (Hypothetical   │ Query         │ Generate hypothetical answer     │
  │ Document Embeddings) │ expansion     │ with Haiku → embed that →        │
  │                      │               │ retrieve. ~100 LOC.              │
  ├──────────────────────┼───────────────┼──────────────────────────────────┤
  │ Content-hash skip    │ L0            │ SHA-256 + KV store. Trivial.     │
  ├──────────────────────┼───────────────┼──────────────────────────────────┤
  │ Token-set subset     │ L1 routing    │ Your retrieval.yaml matcher.     │
  │ matching             │               │ Done.                            │
  ├──────────────────────┼───────────────┼──────────────────────────────────┤
  │ LLM-as-judge (for    │ Lint pass     │ Pairwise comparison prompt. ~1   │
  │ lint contradictions) │               │ page of prompt.                  │
  ├──────────────────────┼───────────────┼──────────────────────────────────┤
  │ GraphRAG community   │ L2            │ nano-graphrag is the reference — │
  │ summary              │               │  1k LOC, readable.               │
  └──────────────────────┴───────────────┴──────────────────────────────────┘

❯   Connectors:     Nango (already have) + JGit + Tika + Slack Web API          
    Code parsing:   tree-sitter (JNI)                                           
    Code ranking:   aider repo-map port → JGraphT                               
    Doc TOC:        PageIndex (Python sidecar)                                  
    L2 clustering:  JGraphT Leiden (or nano-graphrag in Python sidecar)         
    Embeddings:     Voyage AI hosted (voyage-code-3 + voyage-3-large)           
    Vector:         pgvector (already have)                                     
    Rerank:         Voyage rerank-2.5 hosted                                    
    BM25:           Postgres tsvector                                           
    Cache + Batch:  Anthropic native flags                                      
    Cascade:        RouteLLM (Python sidecar)                                   
    LLM client:     Anthropic JVM SDK                                           
    Observability:  Langfuse self-hosted                                        
    Orchestration: Temporal (already have)                                      
    KG extract:     REBEL (Python sidecar) for cheap, LangChain                 
    LLMGraphTransformer when needed                                             
    Synthesis:     bespoke (the IP)                                             
    Drift loop:    bespoke (the IP)                                             


   this was the suggested stack. Are these all the concepts that make up the 3  
  layers of the LLM wiki? Can you describe and provide an overview for each     
  one. And what purpose each has in building this foundatiojn                   

● Quick framing first — there are two layer counts floating around:

  - Karpathy's 3 layers (the pattern): sources/ → pages/ (synthesized) →
  index.md (catalog).
  - Production engine 6 layers (per [[llm-wiki-engine-architecture]]): L0 hash →
   L1 routing → L2 community summaries → L3 atomic pages → L4 cross-link graph →
   L5 drift loop.

  The 6 are an operational expansion of the 3. The stack you quoted covers all 6
   plus the connector layer that feeds L0. Walking each tool below, grouped by
  the layer it serves.

  ---
  Connector / acquisition layer (feeds L0)

  The first job: pull raw bytes from external systems into the pipeline. Before
  any wiki layer exists.

  Nango

  What: OAuth + sync infrastructure as a service. Handles 200+ SaaS
  integrations.
  Role: Owns the "talk to Slack/Notion/Linear/Confluence" mechanics — OAuth
  dance, refresh tokens, rate limits, sync cursors. You write a small JS "sync
  script" per integration; Nango runs it on schedule.
  Why it's load-bearing: Cranium already uses it. Without it, you're rebuilding
  50 different OAuth flows and watching tokens rot.

  JGit

  What: Pure-Java Git implementation by Eclipse.
  Role: Walks GitHub repos without shelling out to git. Clone, read blobs,
  follow commits, diff revisions. Streams files into Pass 0.
  Why JVM-native matters: Embedded in the Spring service; no subprocess
  management, no shell injection surface.

  Apache Tika

  What: Content-extraction toolkit. Handles PDF, DOCX, HTML, Confluence storage
  format, 1000+ MIME types.
  Role: Strips formatting from Confluence/Notion/uploaded docs into plain text
  the synthesizer can read. Cranium already uses it for magic-byte content
  validation.
  What it doesn't do: Layout-aware extraction (tables, sectioning). For long
  PDFs see PageIndex.

  Slack Web API

  What: Slack's official REST API.
  Role: Fetch threads from flagged channels or :wiki:-reaction-tagged messages.
  Wraps via the Slack JVM SDK.
  Critical constraint: Don't pull all of Slack — see
  [[source-confidence-weighting]] for why signal density tanks the cost stack.

  ---
  L0 — Hash & skip

  Before any LLM call: have we already synthesized from this exact content?

  Content hash (SHA-256, bespoke)

  What: Hash the cited region of each source (heading section, function body,
  paragraph) — not the whole file.
  Role: The cheapest cost lever in the entire system. On resync, recompute
  hashes; if unchanged, skip the LLM call entirely. Across a typical resync,
  85–90% of sources skip.
  Documented in: [[content-hash-skip-resync]]. Not a library — ~200 LOC.

  ---
  L1 — Routing manifest

  Given a query, deterministically pick which pages to load before any
  retrieval.

  Token-set subset matcher (bespoke)

  What: Tokenize query + each declared pattern; check pattern-tokens ⊆
  query-tokens.
  Role: Routes ~80% of queries with zero LLM cost. Your
  Wiki/Cranium/retrieval.yaml is the canonical instance.
  Documented in: [[retrieval-routing-manifest]].
  Why deterministic-first matters: Anthropic's RAGRoute benchmark shows routing
  cuts total queries 77.5% at 95–99% recall.

  ---
  L2 — Community summaries

  Mid-tier LLM-built summaries between the master index and atomic pages. This
  is the GraphRAG layer.

  tree-sitter

  What: Incremental parser library covering 40+ languages (C, Rust, TypeScript,
  Kotlin, Python, etc.). Produces concrete syntax trees.
  Role: Turn source code into a structured AST so you can extract symbols
  (functions, classes, imports). Needed before any clustering decision can be
  made.
  Why this and not LLM parsing: Free. Deterministic. Microseconds vs. seconds.
  LLMs are bad at boundaries; tree-sitter is exact.

  aider's repo-map algorithm (port to JGraphT)

  What: Build a graph where nodes = code symbols, edges = "uses" or "defines"
  relationships. Run PageRank to score importance.
  Role: Picks the boundary modules — the small subset of code worth synthesizing
   into atomic L3 pages. In a 1M-LOC repo, this might surface 500–2000
  high-importance symbols.
  Why this is the magic: Stops you synthesizing a page for every leaf file.
  Boundary count, not LOC, bounds Pass 3 cost. Aider's repomap.py is ~600 LOC of
   well-tested Python — port to Kotlin via JGraphT.

  JGraphT

  What: Java graph library with PageRank, Dijkstra, Leiden community detection,
  etc.
  Role: Two uses — (1) PageRank for repo-map boundary detection, (2) Leiden
  clustering over the wikilink graph to discover communities for L2 summaries.

  Leiden algorithm (via JGraphT or nano-graphrag)

  What: Community-detection algorithm. Improvement over Louvain. Finds
  densely-connected node groups.
  Role: Group L3 pages into clusters; each cluster gets an L2 summary.
  GraphRAG's core trick.
  Alternative: Use directory structure or CODEOWNERS for cheap clustering — skip
   Leiden entirely for v1.

  nano-graphrag

  What: ~1k-LOC readable reimplementation of Microsoft GraphRAG in Python.
  Role: Reference impl for the entire L2 build pipeline (extract entities →
  build graph → cluster → summarize). Run it as a Python sidecar if you don't
  want to port to JVM.

  PageIndex (Python sidecar)

  What: VectifyAI's library that builds a hierarchical table-of-contents for a
  single long document.
  Role: For very long Confluence pages or PDFs (>50 pages), build an internal
  TOC before treating each section as a separate source. This is a per-doc L2 —
  different from cross-doc community L2.
  Why a sidecar: It's Python, no point porting; spin up as a microservice.

  ---
  L3 — Atomic page synthesis

  The Karpathy "pages" layer. One concept = one page. LLM-written.

  Anthropic JVM SDK + native flags (Batch + Cache)

  What: Anthropic's official Java/Kotlin SDK with cache_control: ephemeral
  (prefix caching) and Message Batches API (50% off, 24h SLA).
  Role: Every Pass 1/2/3 LLM call goes through this. Caching gives 90% off the
  stable prefix; batch gives another 50% off if it can wait.
  See: [[ingest-cost-stacking]] + [[prompt-cache-prefix-discipline]].

  RouteLLM (Python sidecar)

  What: LM-Sys's cascade router. Trains a classifier that predicts whether a
  query needs the big model or the cheap one.
  Role: Pass 1 + Pass 2 + simple Pass 3 pages → Haiku. Cross-link resolution +
  decision/framework pages → Sonnet. Hub pages with contradictions → Opus.
  RouteLLM benchmark: 85% cost cut at 95% quality with only 14% strong-model 
  calls.

  Voyage AI (voyage-code-3 + voyage-3-large)

  What: Anthropic's recommended embedding partner. voyage-code-3 is
  best-in-class for code, voyage-3-large for prose.
  Role: Embeds every L3 page body. Powers similarity search and hybrid
  retrieval.
  Alternative: OpenAI text-embedding-3-large, Cohere embed-v3 — Voyage is
  meaningfully better on code.

  pgvector

  What: Postgres extension for vector storage + HNSW index.
  Role: Stores embeddings for L2 community summaries AND L3 pages. ANN search at
   query time.
  Why not Pinecone/Qdrant: Cranium already has Postgres + pgvector. Adding a
  separate vector DB doubles operational surface for no benefit until 50M+
  vectors.

  Postgres tsvector (BM25 lexical)

  What: Built-in full-text search using GIN indexes.
  Role: The lexical half of hybrid retrieval. Catches exact-keyword matches that
   vector embeddings miss (e.g., specific function names, ADR numbers).
  Why hybrid not vector-only: [[hybrid-rerank-retrieval]] documents +15–30%
  recall, +10–30% precision over vector-alone.

  Voyage rerank-2.5

  What: Cross-encoder reranker. Rescores the top-K from retrieval.
  Role: After pgvector ANN + tsvector BM25 + Reciprocal Rank Fusion merge gives
  top-30, rerank to top-5. Cross-encoders are slower per pair but vastly more
  accurate than embedding similarity.
  Alternative: Cohere rerank-v3, bge-reranker-v2 (self-host).

  REBEL (Python sidecar)

  What: Supervised relation-extraction model. Given text, extracts (subject, 
  predicate, object) triples.
  Role: Cheap, deterministic knowledge-graph extraction from synthesized pages.
  Feeds the cross-link graph.
  Why supervised not LLM: Free at inference time once you have a GPU. Run over
  every L3 page during Pass 4 for ~$0.

  LangChain LLMGraphTransformer

  What: LLM-driven entity + relation extraction. Slower, more expensive, more
  flexible than REBEL.
  Role: Use when REBEL misses something specific (domain-specific entities,
  multi-hop relations). Reserve for high-value pages.

  ---
  L4 — Cross-link graph

  [[wikilinks]] as first-class edges between pages.

  This layer is all bespoke — there's no good library. ~300 LOC:

  - Regex extraction of [[slug]] from page bodies.
  - wiki_page_links table.
  - Dangling-link queue (link points at slug that doesn't exist yet).
  - Inbound-link count → "is this a hub page?" predicate (drives Pass 4
  escalation to Opus).

  ---
  L5 — Drift loop

  The wiki self-heals. The most-skipped, most-regretted layer.

  This is also all bespoke — ~400 LOC:

  - Scheduled DriftDetector job.
  - For each wiki_page_sources row: re-hash the cited region.
  - If hash changed → enqueue DriftCheckWorkflow.
  - Workflow decides: re-synthesize / no-op / retire.

  Without L5, every other lever above is wasted. Wiki rot dominates by month 2.

  ---
  Orchestration + observability (cross-cutting)

  These touch every layer.

  Temporal

  What: Durable workflow engine. Cranium already runs it for sync + workflow
  orchestration.
  Role: Drives the whole Pass 0–4 pipeline. Each pass is an Activity. Temporal
  handles retries, checkpoints, parallelism, recovery from crashes mid-ingest.
  Why critical at scale: A multi-day ingest run will fail somewhere. Temporal
  lets you resume from the last checkpoint instead of restarting.

  Langfuse (self-hosted OSS)

  What: LLM observability platform — traces, prompt versioning, eval scoring,
  cost attribution.
  Role: Every synthesis call logs (prompt, model, sources, output, cost, 
  latency). Lets you:
  - A/B test prompt templates per kind.
  - See cost breakdown per workspace per lane.
  - Eval synthesized pages against ground truth.
  - Debug "why did this page come out wrong" weeks later.
  Why this is the #1 thing people skip and regret: Without it, you have no way
  to know if a prompt change improved or regressed quality. You're flying blind
  on the most expensive part of the system.

  ---
  What's bespoke (the IP)

  Three things in your stack are explicitly not libraries — they're the Cranium
  product moat:

  Bespoke piece: Synthesis prompts (one per kind)
  What it is: Prompt templates for
    decision/flow/feature/sop/insight/framework/interaction — Karpathy-pattern
    atomic page synthesis
  Why it's not a library: Nobody has packaged this. The Karpathy ≤300-word +
    frontmatter-typed + cross-linked pattern is novel enough that no prompt
    library matches it.
  ────────────────────────────────────────
  Bespoke piece: Drift loop
  What it is: L5 — content-hash diff → re-synthesis queue → resolution policy
  Why it's not a library: Wiki-specific orchestration. ~400 LOC.
  ────────────────────────────────────────
  Bespoke piece: Cross-source attribution
  What it is: One synthesized page citing code + ADR + Slack thread + ticket
    together
  Why it's not a library: No off-the-shelf handles multi-source provenance with
    confidence weighting. Hardest single problem.

  ---
  Mapping back to the three Karpathy layers

  If you want to collapse the 6-layer engine back to Karpathy's 3:

  ┌────────────────────┬────────────────┬───────────────────────────────────┐
  │   Karpathy layer   │ Engine layers  │         Stack components          │
  ├────────────────────┼────────────────┼───────────────────────────────────┤
  │ Sources (raw       │ Connectors +   │ Nango, JGit, Tika, Slack API +    │
  │ pointers)          │ L0             │ hash ledger                       │
  ├────────────────────┼────────────────┼───────────────────────────────────┤
  │                    │                │ tree-sitter, repo-map, JGraphT,   │
  │ Pages              │ L2 + L3 + L4   │ Leiden, RouteLLM, Voyage embed,   │
  │ (synthesized)      │                │ REBEL, Anthropic SDK + bespoke    │
  │                    │                │ synthesis prompts                 │
  ├────────────────────┼────────────────┼───────────────────────────────────┤
  │ Index (catalog)    │ L1             │ Routing manifest matcher          │
  ├────────────────────┼────────────────┼───────────────────────────────────┤
  │ (meta-operations   │ L5 +           │ Drift loop + Temporal + Langfuse  │
  │ across all)        │ Orchestration  │                                   │
  └────────────────────┴────────────────┴───────────────────────────────────┘

  Karpathy's pattern is the shape. The 6-layer engine is what makes it survive
  at company scale instead of collapsing at the
  [[karpathy-wiki-scale-cliff|~500-page cliff]].

  ---
  Coverage gap

