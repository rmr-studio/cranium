---
tags:
  - adr/accepted
  - architecture/decision
Created: 2026-03-16
Updated: 2026-05-14
---

# ADR-001: Sync Infrastructure Through Nango

## Context

Cranium ingests data from many third-party SaaS providers (GitHub, Confluence, Slack, Notion, Google Drive, Jira, etc.). Each provider exposes the same set of infrastructure problems before any product-specific schema mapping can begin:

- **OAuth 2.0 authorization** — per-provider client registration, consent URL construction, callback handling, scope management, PKCE for public clients, token exchange
- **Token lifecycle** — secure storage of access and refresh tokens, refresh-before-expiry, error handling for revoked or expired refresh tokens, re-consent flows
- **Rate limiting** — per-provider rate limits (Slack tiered, GitHub primary + secondary, Google quotas, Jira IP-based), backoff strategies, retry-after handling
- **Sync cursors** — incremental sync state per resource type (delta tokens, ETags, since-timestamps, page tokens, change tokens), durable storage, resumability after failure
- **Webhook reception** — signature verification per provider (HMAC variants, JWT, shared secret), payload normalization, deduplication of redelivered events

None of these problems differentiate Cranium. They are commodity integration infrastructure that every SaaS-aggregating product solves the same way. Building them in-house means writing and maintaining ~weeks of engineering per provider before any value-producing work begins (schema mapping, identity resolution, source-of-truth tracking). Worse, the maintenance burden compounds: each new provider adds another rate-limit policy, another OAuth quirk, another webhook signature scheme to keep current as vendors change them.

Nango is a managed integration platform that provides exactly this commodity layer: hosted OAuth, encrypted token storage with auto-refresh, per-provider rate-limit policies, cursor persistence inside sync scripts, and webhook verification — exposed via a unified SDK and dashboard.

---

## Decision

Use Nango as the sole integration infrastructure layer for all third-party data syncing. Specifically:

- **OAuth flows** run through Nango's hosted Connect UI; Cranium never sees client secrets or authorization codes
- **Tokens** are stored in Nango's encrypted vault and never persisted in Cranium's database — Cranium stores only the opaque `connection_id` returned by Nango
- **Refresh** is fully managed by Nango; Cranium calls `nango.proxy(...)` and Nango injects a valid access token on every outbound request
- **Rate limits** are enforced inside Nango's proxy and sync runners according to per-provider policies that Nango maintains
- **Sync cursors** are persisted by Nango as part of each sync script's state (`nango.setLastSyncDate`, `nango.getMetadata`/`setMetadata`, cursor variables in script context)
- **Webhooks** are received by Nango first, verified against per-provider signatures, then forwarded to Cranium as a normalized webhook with a Nango-issued HMAC signature that Cranium verifies

Cranium owns everything downstream of the normalized record: schema mapping, entity creation, identity resolution, provenance tracking, projection, search.

---

## Rationale

- **Commodity vs. differentiating** — OAuth, refresh, rate limits, and cursor bookkeeping are solved problems. Cranium's value is the synthesis layer (entities, pages, projections), not the plumbing
- **Per-provider engineering avoided** — Nango's catalog covers every V1 provider (GitHub, Confluence, Slack) and the V1.1 roadmap (Notion, Google Drive, Jira) with maintained OAuth configs, rate-limit policies, and sync templates. In-house builds would be ~1–2 weeks per provider just to reach parity
- **Smaller credential blast radius** — tokens never enter Cranium's database. A Cranium breach exposes only opaque connection IDs, useless without Nango access
- **Single SDK surface** — `nango.proxy()` and `nango.get/setMetadata()` replace N different REST wrappers, N token-refresh implementations, and N rate-limit handlers
- **Webhook normalization** — Nango unifies HMAC, JWT, and shared-secret verification into a single signed forward to Cranium, collapsing N verification implementations into one
- **Maintained provider drift** — when GitHub rotates a token format or Slack changes a rate-limit policy, Nango absorbs the change; Cranium stays unchanged

---

## Alternatives Considered

### Option 1: Build OAuth + Sync Infrastructure In-House

Implement per-provider OAuth clients, token storage with auto-refresh, rate-limit policies, cursor persistence, and webhook verification directly in the Cranium backend.

- **Pros:** Zero vendor dependency. Full control over token storage encryption and key management. No external SaaS cost. Can optimize sync runners specifically for Cranium's projection model.
- **Cons:** ~1–2 weeks of engineering per provider before any product work. Ongoing maintenance burden as providers change OAuth scopes, rate-limit policies, webhook signatures. Token storage and rotation introduce security obligations (encryption at rest, key rotation, audit logging) that scale with provider count. Cursor persistence needs durable per-resource state across restarts.
- **Why rejected:** The engineering and maintenance cost dwarfs the SaaS subscription cost, and none of this work differentiates the product. Time spent here is time not spent on the synthesis layer.

### Option 2: Use a Lower-Level OAuth Library (e.g., Spring Security OAuth, Hydra) + In-House Sync Layer

Adopt an OAuth client library for token handling but build the sync orchestration (cursors, rate limits, webhooks) in-house.

- **Pros:** Reduces OAuth-specific work. Library is well-maintained. No vendor lock-in for the auth layer.
- **Cons:** OAuth is only ~30% of the integration problem. Token refresh, rate limiting, cursor persistence, and webhook verification still need to be built per provider. Library handles the protocol; per-provider quirks (Slack's tiered rate limits, GitHub's secondary rate limits, Google's quota families, Jira's IP-based limits) still require custom code.
- **Why rejected:** Solving 30% of the problem still leaves the long tail of per-provider quirks. Nango's value is the maintained catalog of those quirks, not the OAuth protocol implementation itself.

### Option 3: Per-Provider Managed SDKs (Octokit, Slack SDK, Atlassian SDK, etc.)

Use each provider's official SDK directly, with in-house token management glue.

- **Pros:** First-party SDKs are typically well-documented and feature-complete. Direct vendor relationship.
- **Cons:** N different SDK surfaces, N different error models, N different rate-limit conventions. Token storage and refresh still must be built per provider. No webhook normalization. Onboarding a new provider means learning another SDK. The integration surface area in Cranium grows linearly with provider count.
- **Why rejected:** Per-SDK fragmentation defeats the purpose of a unified ingestion layer. Cranium's downstream code (schema mapping, entity creation) wants one shape of input, not N.

### Option 4: Competing Integration Platforms (Merge.dev, Paragon, Workato Embedded)

Use a different unified integration platform with similar OAuth + sync capabilities.

- **Pros:** Same value proposition as Nango. Some competitors offer pre-built schema unification (Merge.dev's "unified APIs").
- **Cons:** Unified APIs flatten provider-specific richness that Cranium needs for high-fidelity entity extraction. Pricing scales aggressively with connection count. Less control over the sync script — proprietary DSL vs. Nango's TypeScript scripts. Smaller open-source posture; weaker migration story.
- **Why rejected:** Nango's TypeScript sync scripts give Cranium provider-specific fidelity (raw payloads, custom resource types) without paying for opinionated unification that would have to be undone downstream. Nango is also open-source-licensed for self-host as an escape hatch.

---

## Consequences

### Positive

- Per-provider integration cost drops from ~1–2 weeks of engineering to writing a sync script (typically <1 day) plus schema mapping
- Tokens never enter Cranium's database — credential breach blast radius is bounded to opaque connection IDs
- Refresh, rate-limit backoff, and cursor persistence are not Cranium's problem to debug or maintain
- Webhook verification is centralized — one HMAC check on the Nango-to-Cranium forward instead of N per-provider schemes
- Provider drift (rotated token formats, changed rate limits, new webhook signatures) is absorbed by Nango without Cranium changes
- Single SDK surface (`nango.proxy`, `nango.get/setMetadata`) replaces N per-provider HTTP wrappers

### Negative

- **Hard vendor lock-in.** Migrating off Nango means rebuilding OAuth, token storage, refresh, rate limits, cursor persistence, and webhook verification per provider. Every additional provider deepens the dependency. "What happens if Nango disappears" is a business-continuity risk, not a technical one. Mitigations: Nango is open-source (self-host is an escape hatch), and the sync scripts are portable TypeScript that can run outside Nango with a compatibility shim
- Recurring SaaS cost that scales with connection count and sync volume
- Sync script execution happens in Nango's runtime — debugging requires Nango's logs and dashboards, not Cranium's observability stack (mitigated by forwarding sync metrics into Cranium via the webhook channel)
- New providers depend on Nango's catalog. A provider not in the catalog requires a "custom integration" definition in Nango, which is more work than a maintained one

### Neutral

- Sync scripts live in the Nango configuration repo (or Nango dashboard), not in the Cranium monorepo — a small operational split that requires keeping the two in sync via tagging or CI integration
- Cranium's `integration_connections` table stores only the Nango `connection_id` plus Cranium-side metadata (workspace, status, last sync time mirrored from webhooks) — schema stays thin

---

## Implementation Notes

- **Connection model:** `integration_connections` table stores `(workspace_id, provider, nango_connection_id, status, last_sync_at)`. No tokens, no client secrets, no refresh tokens
- **Connection creation:** Driven by Nango's auth webhook only — see [[ADR-010 Webhook-Driven Connection Creation]] (superseded for v1 but the pattern returns in v1.1)
- **Outbound API calls:** Always through `nango.proxy({ providerConfigKey, connectionId, endpoint, method, ... })`. No direct HTTP calls to provider APIs from Cranium
- **Cursor persistence:** Each sync script uses `nango.getMetadata()` / `nango.setMetadata()` for incremental state (delta tokens, since-timestamps, page cursors). Cranium never sees raw cursors
- **Webhook flow:** Provider to Nango (verified by provider-specific signature) to Cranium (verified by Nango's HMAC on a shared secret). Cranium's webhook handler is provider-agnostic for verification; provider-specific logic begins only after the signature check passes
- **Rate-limit handling:** Delegated to Nango's proxy. Cranium-side retry logic exists only for Nango-level errors (5xx, timeouts), not provider rate-limit errors
- **Escape hatch:** Sync scripts are TypeScript with a thin Nango SDK surface. If Nango must be replaced, the scripts can be ported to a self-hosted Nango instance or to a custom runner with a shim implementing `proxy`, `get/setMetadata`, and `triggerAction`

---

## Related

- [[nango-integration-infrastructure]] — wiki page summary of this decision (gotcha + tradeoff form)
- [[nango-sync-script-semantics]] — how sync scripts use cursors and metadata
- [[temporal-sync-orchestration]] — how Cranium-side sync orchestration interacts with Nango-side scripts
- [[how-integration-connection-lifecycle]] — connection state machine that sits on top of Nango connections
- [[how-integration-sync-pipeline-runs]] — end-to-end sync flow from Nango webhook to entity creation
- [[ADR-010 Webhook-Driven Connection Creation]] — connection lifecycle driven by Nango auth webhook (superseded by ADR-021 for v1)
- [[ADR-021 V1 Properties Github, Confluence and Slack]] — V1 scope that defers the full Nango sync pipeline in favor of read-only PAT for GitHub
- [[architecture-pivot]] — 2026-05 pivot context; this ADR remains the long-term direction even though v1 ships without it
