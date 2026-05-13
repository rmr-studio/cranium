---
tags:
  - architecture/design
Created: 2026-05-13
Updated: 2026-05-13
---
# Template Index — one-screen cheat sheet

Which template to copy, when. Files live under `docs/templates/`.

## Design templates (`docs/templates/Design/`)

| Template | Use this when… | Example (Cranium v1) |
|---|---|---|
| **Feature Design — Quick** | a contained backend component — one service, a status surface, an install flow, a migration convention. | `scan_runs Status Surface`, `GitHub Actions Release Pipeline`, `GitHub App Install Flow`, `Forward-Only Migration Path` |
| **Feature Design — Full** | a cross-cutting backend feature — multiple components, a data-model change, a workflow, a policy. | `Page Resolution Policy + Eval Suite`, `Synthesis Prompt + Insight Extraction`, `GitHub Scan & Source Parsers`, `Synthesis Temporal Workflow & Fan-out`, `MCP Server (core/ module)`, `PR Bot (GitHub App)` |
| **Frontend Feature Design — Quick** | a contained UI component — a badge, a panel, one widget. | a freshness/confidence badge component |
| **Frontend Feature Design — Full** | a cross-cutting UI feature — multiple components, real state management, interaction design. | `Web Graph Surface`, `Decision Queue UI` |
| **Page Design** | a single route/page composition — which features dock where, route params, navigation, layout. | `Page Reader`, `Page List`, `Page Settings` (the Phase-1d-renamed `apps/client` routes) |
| **Sub-Domain Plan** | building out a whole sub-domain — groups related feature designs, sets architecture + data flow + implementation sequence. | `OSS Self-Host Bundle` |

## Documentation templates (`docs/templates/Documentation/`)

| Template | Use this when… | Example (Cranium v1) |
|---|---|---|
| **Architecture Flow** | documenting how an event runs end-to-end — trigger → steps → data → failure modes → observability — and the design is settled. | the `flows/` docs once each phase ships and the design pass is done |
| **Architecture Flow — Quick** | documenting an end-to-end flow whose design is still genuinely unsettled — leaner: overview / trigger / steps / failure modes / components / related. | the current `flows/` stubs (GitHub Repo Scan, Batched LLM Synthesis, Page Resolution, PR Bot, MCP Query, Page Reader Render, Self-Host Quickstart) |
| **Component Overview** | documenting a single component — purpose, responsibilities, dependencies, public interface, data access, gotchas. | `GitHubScanService`, `PageResolutionService`, `SynthesisDispatcher`, the MCP module |
| **Component Overview — Quick** | a lightweight component note — purpose, responsibilities, key methods, gotchas. | smaller helpers / services |
| **Frontend Architecture** | documenting frontend architecture at the app/area level (routing, layout hierarchy, state strategy). | the `apps/client` shell post-Phase-1d |

## Picking between Quick and Full

- **Quick** = the design is contained, low-risk, or still in flux. Faster to write; upgrade to Full later if it grows.
- **Full** = cross-cutting, has a data-model change, has real failure modes / security / scale concerns, or feeds a `/plan-eng-review`.
- For flows: use **Quick** for stubs and unsettled designs; promote to the full **Architecture Flow** template once the phase ships and the design pass is done.

## Related

- [[README]] — the design-doc workflow + the Cranium v1 design backlog
- [[architecture-pivot]] — the canonical pivot spec
