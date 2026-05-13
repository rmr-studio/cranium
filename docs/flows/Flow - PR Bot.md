---
tags:
  - flow/planned
  - flow/integration
  - flow/user-facing
  - architecture/flow
Created: 2026-05-13
Updated: 2026-05-13
Critical: true
Domains:
  - "[[Domain - Surfaces]]"
---
# Flow: PR Bot

_(stub — Phase 2; design pass during Phase 2 — "PR Bot (GitHub App)" feature design. The "why"-prompt thin spike runs early in Phase 2's build order, E4.)_

> See [[architecture-pivot]] for the canonical spec. **Reframe-not-gate** — the PR bot never blocks, never says "Cranium recommends rejecting." Uses the **GitHub App** (the read-only PAT is for the scan only). v1 ships ONLY the two high-confidence signals below; fuzzy "this looks like a reimplementation of X" detection is **out of v1**.

---

## Overview

On PR open (GitHub App webhook), Cranium posts a single **non-blocking** comment that: (a) names the domain owner for the touched files and auto-requests them as a reviewer if they aren't already; (b) if a touched file is referenced by an ADR/Decision page, surfaces the "why" prompt — the engineer's reply auto-drafts an ADR PR, attributed to them, queued in `decision_queue` for architect review. GitHub-native presentation (collapsed `<details>`, suggested-reviewer UI, text-first). Never a block, never a check-run failure.

---

## Trigger

| Trigger Type | Source | Condition |
|---|---|---|
| Webhook | GitHub App `pull_request` event (opened/synchronize) | a PR is opened or updated on a repo where the Cranium GitHub App is installed |
| Webhook | GitHub App issue/PR comment event | the engineer replies to the "why" prompt comment |

**Entry Point:** `PrBotWebhookController` (HMAC-validated) → `PrBotService` (in `core/`, `github/` module — coordinate with [[Flow - GitHub Repo Scan]]'s scanner, both touch `github/`).

---

## Flow Steps (what runs end-to-end)

`PR opened (App webhook) → resolve touched files → look up Domain owner → post non-blocking comment naming the owner + auto-request reviewer → if a touched file references an ADR/Decision, include the "why" prompt → engineer replies → auto-draft an ADR PR, attributed, queued in decision_queue for architect review.`

1. **Receive + validate the webhook** (`PrBotWebhookController`) — HMAC-validate (the preserved `nango-webhook-hmac-validation` pattern); dedup by delivery id.
2. **Resolve touched files → pages** — map the PR's changed paths to `File`/`Domain` pages via `page_links` (the deterministic directory→Domain links from the scan).
3. **Signal 1 — owner not requested as reviewer:** look up the Domain page's owner (Person page); if that person isn't already on the PR's reviewer list, **auto-request** them and name them in the comment.
4. **Signal 2 — touched file references an ADR:** if any touched `File` page has a `references` link to an `ADR`/`Decision` page, include the "why" prompt in the comment (the change touches a file under a recorded decision — explain).
5. **Post one non-blocking comment** — GitHub-native: collapsed `<details>`, the named owner, the "why" prompt if applicable. Never a check-run failure; never "reject."
6. **Engineer replies to the "why" prompt** → `PrBotService` auto-drafts an **ADR PR** (a new `/docs/decisions/*.md` file), attributes it to the replying engineer, opens it, and queues a `decision_queue` entry for an architect to review/merge.

---

## Data Touched

- **Reads:** `pages` (File, Domain, Person, ADR/Decision), `page_links` (file→Domain, file→ADR `references`), GitHub PR/reviewer state via the App.
- **Writes (via the GitHub App):** a PR comment; a reviewer-request; (on reply) a new ADR PR. **Writes (Cranium DB):** `decision_queue` (the queued ADR-PR-for-architect-review entry); no writes to `pages` directly (the new ADR file is picked up by the next [[Flow - GitHub Repo Scan]] → [[Flow - Batched LLM Synthesis]] pass).

---

## Failure Modes

| Codepath | Failure | Detection | User-visible | Recovery |
|---|---|---|---|---|
| Owner lookup | blame says A, but A left the company → false-positive owner suggestion | partial — the freshness/confidence signal on the Domain page | engineer ignores or overrides; the override becomes a record | **warn-not-block by construction** — do NOT escalate to a block |
| Webhook | replay / out-of-order PR events | delivery-id dedup | none | idempotent — the comment is upserted, not duplicated |
| No owner / no ADR ref | the PR touches files with no resolved owner and no ADR link | n/a — just skip the signals | the bot posts a minimal comment or no comment | n/a |
| ADR-PR draft | the auto-drafted ADR PR fails to open (GitHub API down) | API error | the engineer's reply is acknowledged; retry | retry the PR draft; the `decision_queue` entry records the intent |
| GitHub App auth | install revoked / token expired | API 401 | the bot silently stops on that repo | re-install the App |

---

## Test Bar

- **PR bot E2E on the fixture repo** — open a PR on the committed fixture repo → assert the non-blocking comment names + auto-requests the owner; a touched-file-references-an-ADR → the "why" prompt appears; reply to it → an ADR PR is drafted, attributed to the replier, and queued in `decision_queue`.
- **GitHub App install flow** — mock GitHub webhook → the app receives + processes the install event **and** a `pull_request` event.

---

## Related

- [[architecture-pivot]] — canonical spec; the "v1 ships only high-confidence signals" scope cut
- [[Flow - GitHub Repo Scan]] — supplies the file→Domain→owner links; both touch `github/`
- [[Flow - Batched LLM Synthesis]] — picks up the auto-drafted ADR file on the next scan
- [[Domain - Surfaces]]
