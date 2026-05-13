---
tags:
  - architecture/domain
  - cranium/v1
Created: 2026-05-13
---
# Domain: OSS Packaging & Self-Host

---

## Overview

The GTM is open-source-led, so v1 isn't actually distributable without this — it's a phase, not a TODO. Owns the docker-compose self-host bundle (`core` + `apps/client` + postgres + Temporal — the MCP server is in `core`, so no extra process), the GitHub Actions release pipeline (build + test + publish the OSS package + container images on a version tag), the GitHub App install/permissions flow for the PR bot, the forward-only migration path (needed before the first external self-hoster), and the "5-minute to first scan" quickstart. See [[overview]] §Repo↔runtime mapping and [[architecture-pivot]] §Phase 4.

---

## Boundaries

### This Domain Owns

- The hardened `docker-compose` self-host bundle (`core` + `apps/client` + postgres + Temporal); CI job: `docker compose up` → healthcheck passes.
- The GitHub Actions release pipeline — build + test + publish on a version tag (the OSS package + the container images); target platforms defined for any published artifact.
- The GitHub App registration + install/permissions flow for the PR bot (the one piece that genuinely needs an install UX).
- The forward-only DB migration path (Flyway/Liquibase) — needed before the first external self-hoster, which is exactly this domain's audience.
- The "5-minute to first scan" quickstart: clone → `docker compose up` → register a read-only GitHub PAT → point at an org → first scan completes within budget (cites the Phase-2 scan-paging/throttling note for what "first value fast" means).

### This Domain Does NOT Own

- The hosted/paid tier's deploy pipeline — separate, deferred until a design partner asks.
- The PR bot's *behaviour* — that's [[domains/Surfaces/Surfaces|Surfaces]] (this domain owns the *install flow*).
- The scan's rate-limit/throttling logic — that's [[domains/Source Ingestion/Source Ingestion|Source Ingestion]] (this domain cites it for the quickstart budget).

---

## Sub-Domains

| Component | Purpose | Type |
| --------- | ------- | ---- |
| docker-compose self-host bundle | `core` + `apps/client` + postgres + Temporal; healthcheck-verified in CI | Infra |
| GitHub Actions release pipeline | build + test + publish OSS package + container images on a version tag | CI/CD |
| GitHub App install flow | registration + install/permissions for the PR bot; mock-webhook test | Backend (`core/`) + GitHub App |
| Forward-only migrations | Flyway/Liquibase; the first external self-hoster needs this | Infra |
| "5-min to first scan" quickstart | README path + the automated CI version of it | Docs + CI |

### Integrations

| Component | External System |
|---|---|
| GitHub App install flow | GitHub App registry / OAuth install |
| Release pipeline | GitHub Actions; container registry |

---

## Flows

| Flow | Type | Description |
| ---- | ---- | ----------- |
| [[Flow - Release on Tag]] _(stub — flesh out during Phase 4)_ | Background (CI) | push a version tag → build + test + publish the OSS package + container images |
| [[Flow - Self-Host First Scan]] _(stub — Phase 4)_ | User-facing | clone → `docker compose up` → register a read-only PAT → point at an org → first scan within budget |
| [[Flow - Install the PR Bot]] _(stub — Phase 4)_ | User-facing | install the GitHub App → grant permissions → app receives install + PR events |

---

## Data

### Owned Entities

None — this domain is infra/CI/docs. The forward-only migration scripts live alongside `core/`'s schema.

---

## External Dependencies

| Dependency | Purpose | Failure Impact |
|---|---|---|
| GitHub Actions + container registry | release pipeline | no release artifacts published |
| GitHub App registry | PR bot install | self-hosters can't enable the PR bot; the rest still works |
| Docker / docker-compose | the self-host bundle | self-host path broken |

---

## Domain Interactions

### Depends On

| Domain | What We Need | How We Access |
|---|---|---|
| [[domains/Source Ingestion/Source Ingestion|Source Ingestion]] | the scan-throttling note (defines the "5-min" budget) | Direct (doc reference) |
| [[domains/Surfaces/Surfaces|Surfaces]] | the PR bot it installs | Direct |
| [[domains/Pages & Links Core/Pages & Links Core|Pages & Links Core]] | the schema the forward-only migrations migrate | Direct |

### Consumed By

| Domain | What They Need | How They Access |
|---|---|---|
| (OSS self-hosters) | a working `docker compose up` + a quickstart + the PR-bot install | Docs / CI artifacts |

---

## Key Decisions

| Decision | Summary |
|---|---|
| [[ADR-016]] | phasing — OSS packaging is Phase 4 (after the surfaces); v1 isn't distributable without it |
| [[ADR-019]] | pivot-before-design-partner — the OSS artifact is the GTM, so its packaging is a phase not a footnote |

---

## Technical Debt

| Issue | Impact | Effort |
|---|---|---|
| OSS self-hosting friction — App + ingestion + synthesis + MCP + web UI is not a single binary; does the friction undermine the distribution advantage? Needs the "5 min to first value" path to actually land | High | Med |
| DX review for the self-hoster onboarding not yet run — consider before v1 release | Med | — |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-05-13 | Domain created from the architecture pivot — Phase 4: OSS packaging + release + self-host quickstart + GitHub App install flow | [[architecture-pivot]] |
