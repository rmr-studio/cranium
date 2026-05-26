# Cranium

Cranium is the allignment substrate for AI-Augmented engineering teams. Infrastructure that embeds system design, team standards and interactions as context for agentic development and orchestration.

Derived from Karpathy's "LLM-maintained wiki" pattern, scaled to a team and grounded in a real codebases, system designs, conversations and team knowledge.

[In the process of restructuring architecture to support functionality]

## Features

- [] Self Adapting Knowledge Base
  - [] Active Signal Suggestions
- [] Team Ecosystem Managament
  - [] System Ownership
  - [] Subject Matter Expert Mapping
- [] System Architecture Management
  - [] Automatic PR Reviews to maintain system consistency
  - [] Live System Design Map
  - [] Architectural Decision Records
  - [] Codebase Quality Standards
- [] MCP Context Injection

## How it works

Three layers, one direction of flow:

```
GitHub org  ──                       ──┐         GitHub App (PR bot only)
                                       ▼
┌─ SOURCE LAYER ───────────────────────────────────────────────┐
│ Deterministic parsers — no LLM. content_hash + skip-if-      │
│ unchanged. Every run logs parsed / skipped / failed counts.  │
│   repo tree              → File   source_entities            │
│   commit log             → Commit source_entities            │
│   PR list                → PR     source_entities            │
│   /docs/decisions/*.md   → ADR    source_entities            │
│ Plus cheap deterministic page_links: PR→files, commit→files, │
│ file→Domain (by directory), commit-author-email→Person.      │
└──────────────────────────┬───────────────────────────────────┘
                           │  new_source_events_queue
                           │  (ShedLock + SKIP LOCKED dispatcher)
                           ▼
┌─ SYNTHESIS LAYER ────────────────────────────────────────────┐
│ Temporal workflow, batched and coalesced by page:            │
│   1. read batch of source entities + context                 │
│   2. extract atomic insights                                 │
│   3. page resolution: deterministic key → fuzzy candidate    │
│      score → auto-link above threshold OR decision_queue     │
│   4. emit page updates (frontmatter, body, links)            │
│   5. mark touched pages for re-embed                         │
│ Targeted column writes only (body, aggregations,             │
│ generated_at, content_hash). Never a whole-row replace.      │
│ The synthesis LLM never sits on a request path.              │
└──────────────────────────┬───────────────────────────────────┘
                           ▼
┌─ SURFACES ───────────────────────────────────────────────────┐
│ Web Platform                                                 |
│ MCP server                                                   │
│ PR bot                                                       │
└──────────────────────────────────────────────────────────────┘
```

