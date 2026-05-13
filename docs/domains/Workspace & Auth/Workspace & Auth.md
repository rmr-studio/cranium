---
tags:
  - architecture/domain
  - cranium/v1
Created: 2026-05-13
---
# Domain: Workspace & Auth

---

## Overview

Multi-tenant isolation, RLS, and JWT auth — **mostly carries over from the current system**; the pivot reuses it as-is ("isolation is done"). The one v1-specific change is a team-centred restructure: workspace members are mirrored into `Person` pages linked to platform identities (GitHub login → commit-author email), so the wiki's "who owns X" answers tie back to real workspace accounts. See [[overview]] §Repo↔runtime mapping and [[architecture-pivot]] §Reuse/Rework/Replace/Delete.

---

## Boundaries

### This Domain Owns

- Workspace + workspace-member + workspace-invite model and lifecycle.
- JWT decoding, `@PreAuthorize` checks, RLS policy enforcement (the `workspace_id` scoping every other domain relies on).
- The activity log + websocket/STOMP (generic, works — reused).
- The team-centred restructure: mirror workspace members into `Person` pages; bridge workspace-member identity → GitHub login → commit-author email (the key the `PersonPage` resolution uses — see [[page-kinds]]).
- The workspace's GitHub credentials store: the read-only PAT used by [[domains/Source Ingestion/Source Ingestion|Source Ingestion]] (invariant 6 — the GitHub App is separate, owned by [[domains/Surfaces/Surfaces|Surfaces]]).

### This Domain Does NOT Own

- Hosted-tier compliance (SSO/SAML/SCIM, audit log, retention, residency) — that's the paid tier, post-design-partner, out of v1 scope.
- The `pages` family — that's [[domains/Pages & Links Core/Pages & Links Core|Pages & Links Core]] (this domain provides the `workspace_id` scoping it uses).
- The GitHub App — that's [[domains/Surfaces/Surfaces|Surfaces]].

---

## Sub-Domains

| Component | Purpose | Type |
| --------- | ------- | ---- |
| Auth / token decode | JWT → security context; `@PreAuthorize`; reused | Service / Config |
| RLS policies | per-workspace row isolation in postgres; reused | Infra |
| Workspace + members + invites | tenant + membership lifecycle; reused | Service |
| Activity log + websocket/STOMP | generic event surface; reused | Service |
| Person-page mirroring | workspace member → `Person` page; identity bridge (GitHub login → commit-author email) | Service |
| GitHub PAT store | the read-only scan credential | Service |

---

## Flows

| Flow | Type | Description |
| ---- | ---- | ----------- |
| [[Flow - Auth & Authorization]] _(reused — see existing flow docs)_ | User-facing | JWT → `@PreAuthorize` → RLS chain |
| [[Flow - Invitation Acceptance]] _(reused)_ | User-facing | create invite → accept invite |
| [[Flow - Mirror Member to Person Page]] _(stub — flesh out during Phase 2)_ | Background | new/updated workspace member → upsert `Person` page → link to GitHub login → resolve to commit-author email |

---

## Data

### Owned Entities

| Entity | Purpose | Key Fields |
|---|---|---|
| `Workspace` | a tenant | reused |
| `WorkspaceMember` | membership | reused; gains a `Person` page link + GitHub-login bridge |
| `WorkspaceInvite` | pending invite | reused |
| `ActivityLog` | event trail | reused |

### Database Tables

| Table | Entity | Notes |
|---|---|---|
| `workspaces`, `workspace_members`, `workspace_invites` | as above | reused; RLS policies unchanged |
| `activity_log` | `ActivityLog` | reused |

---

## External Dependencies

| Dependency | Purpose | Failure Impact |
|---|---|---|
| Supabase JWKS (optional) | JWT signing keys | auth degraded if the configured provider is down |
| PostgreSQL (RLS) | isolation | hard down |

---

## Domain Interactions

### Depends On

| Domain | What We Need | How We Access |
|---|---|---|
| (none internal — this is the base layer) | | |

### Consumed By

| Domain | What They Need | How They Access |
|---|---|---|
| [[domains/Pages & Links Core/Pages & Links Core|Pages & Links Core]] | `workspace_id` scoping, RLS | Direct |
| [[domains/Source Ingestion/Source Ingestion|Source Ingestion]] | workspace scoping; the GitHub PAT | Direct |
| [[domains/Synthesis/Synthesis|Synthesis]] | workspace scoping (Temporal workers stamp the system user explicitly — no SecurityContext) | Direct |
| [[domains/Surfaces/Surfaces|Surfaces]] | auth, dashboard shell, RLS | Direct |
| [[domains/MCP Server/MCP Server|MCP Server]] | auth/RLS context for every query | Direct (in-process) |

---

## Key Decisions

| Decision | Summary |
|---|---|
| [[ADR-011]] | reuse strategy — keep the storage spine + infra (auth/workspace/RLS/JWT) intact; gut & rebuild the type/projection/routing/catalog machinery |
| _(pre-pivot ADRs still apply — auth/RLS design unchanged)_ | see `../decisions/` ADR-001…010 |

---

## Technical Debt

| Issue | Impact | Effort |
|---|---|---|
| Temporal workers have no SecurityContext — the system-user UUID must be stamped explicitly on synthesis/scan writes (the `jpa-auditing-temporal` pitfall) | Med | Low — a known pattern |
| Hosted-tier compliance (SSO/SAML/SCIM, audit log, retention) deferred — not a v1 gap, flagged so it isn't forgotten | — | post-design-partner |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-05-13 | Domain doc created — the pivot reuses this layer as-is; the only addition is mirroring workspace members into `Person` pages | [[architecture-pivot]] |
