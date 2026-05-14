---
tags:
  - architecture/decision
  - adr/accepted
Created: 2026-05-13
Updated: 2026-05-13
---
# ADR-021: V1 Properties

---

## Context

The pre-pivot system had a multi-source manifest/catalog engine + a Nango integration sync built to connect HubSpot/Salesforce/Stripe/Zendesk/Intercom/Gmail. The v1 wiki product needs source connectors too — GitHub for sure, and Slack + Notion/Confluence would make the domain/ownership map *trustworthy* rather than merely *plausible* (they validate ownership, supply route hints, give the synthesis LLM free text to route). The question: how many connectors ship in v1, and is the manifest engine the right shape for them?

---

## Decision

**v1 ships GitHub Slack and Confluence.** V1 builds a `SourceConnector` interface *shape* . The manifest/catalog engine is deleted (D1). The Nango HMAC-webhook-validation pattern is preserved (git history or one [[Wiki/Cranium]] page) — the v1.1 connectors will want it. **GitHub auth split:** a read-only PAT for the scan path; the GitHub App only for the PR bot (commenting).

---

## Rationale

- **GitHub is the wedge** — "point Cranium at a GitHub org → a living domain/ownership/SME map" is the v1 product; the codebase + PRs + commits + ADR files are enough to produce a map worth trusting.
- **Slack/Notion are *enrichment*, not foundation** — they make the map better, but the map exists without them; shipping them in v1 would delay the wedge.
- **No manifest engine for one connector** — GitHub-only means one hardcoded scanner; a manifest engine is machinery for a multi-connector world the v1.1+ roadmap will reach, not v1. Each future connector is a small hardcoded Kotlin implementation of the `SourceConnector` interface.
- **Preserve the HMAC pattern, not the code** — "nothing kept just in case" forbids leaving the Nango sync around; but the webhook-HMAC-validation pattern is genuinely reusable for v1.1, so it lives in git history or a wiki page.
- **PAT-for-scan, App-for-PR-bot** — "5 minutes to first value": paste a read-only PAT to scan; the GitHub App install flow is only where commenting requires it (Phase 4).

---

## Alternatives Considered

### Option 1: Ship GitHub + Slack + Notion in v1, keep the manifest engine

- **Pros:** A more "trustworthy" map at launch; the manifest engine is built.
- **Cons:** Delays the wedge; carries a multi-connector engine for a v1 that has one connector; "nothing kept just in case" forbids the dead manifest machinery.
- **Why rejected:** v1.1 enrichment ≠ v1 requirement; the wedge ships sooner without it.

### Option 2: GitHub-only but keep the manifest engine "for later"

- **Pros:** v1.1 connectors slot into existing machinery.
- **Cons:** Dead engine in v1; the discipline gate forbids it; the `SourceConnector` interface shape is enough scaffolding for later.
- **Why rejected:** Define the interface, delete the engine.

---

## Consequences

### Positive

- The wedge ships GitHub-only — fastest path to "point it at an org and see the map".
- One hardcoded scanner is simpler to build, test (fixture repo), and reason about than a manifest-driven one.
- "5 minutes to first value": PAT-paste for the scan; App install only for the PR bot.

### Negative

- The v1 map is "plausible" until Slack/Notion enrichment lands in v1.1 — a known limitation, surfaced via `pages.confidence` + the freshness indicator.
- The `SourceConnector` interface is speculative scaffolding (one implementation in v1) — accepted because it's cheap and shapes v1.1 cleanly.
- "Implement Slack connector" + "implement Notion connector" become P2 TODOs once the interface shape exists.

### Neutral

- The HMAC-webhook pattern survives in git history / a wiki page, not as kept-around code.

---

## Implementation Notes

- Phase 2: the GitHub scanner is a hardcoded Kotlin implementation behind a `SourceConnector` interface; the interface is the only "for later" scaffolding.
- Phase 1c deletes the Nango integration sync (3-pass workflow, `SchemaMappingService`, webhook controller) + the manifest/catalog engine; preserve the HMAC pattern note before deleting.
- GitHub auth: read-only PAT for the scan (Phase 2); GitHub App for the PR bot + the install flow (Phase 2 PR bot / Phase 4 install flow).

---

## Related

- [[architecture-pivot]]
- [[ADR-014 Deterministic Source-Entity Creation plus Batched LLM Synthesis]]
- [[ADR-001 Nango as Integration Infrastructure]]
- [[ADR-004 Declarative-First Storage for Integration Mappings and Entity Templates]]
- [[ADR-010 Webhook-Driven Connection Creation]]
