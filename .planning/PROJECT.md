# Unified Data Ecosystem — Source Layer + Postgres Adapter

## What This Is

Transition Riven from a SaaS-only integration platform to a unified data ecosystem that ingests data from arbitrary sources (SaaS integrations via Nango, user-owned Postgres databases, and future CSV/webhook/CDC adapters) into a two-layer data model. Source Layer holds readonly external data; Projection Layer is the user-facing working surface. First adapter beyond Nango is Postgres, driven by validated user Mac (Head of Product at Badho) whose operational data lives in Postgres.

## Core Value

Any data source → unified entity model → trigger → action → measurement loop. The Postgres adapter is the first proof that the adapter pattern works for non-SaaS sources and that the two-layer model accommodates source data that doesn't map cleanly to core lifecycle domains.

## Requirements

### Validated

<!-- Shipped and confirmed — inferred from brownfield codebase. -->

- ✓ SaaS integration ingestion via Nango — existing
- ✓ Entity type system with workspace scoping — existing
- ✓ Core lifecycle domain models (CUSTOMER, SUPPORT_TICKET, etc.) — existing
- ✓ SchemaMappingService for field mapping + transforms — existing
- ✓ IdentityResolutionService (already source-type-agnostic) — existing
- ✓ EntityProjectionService with ProjectionAcceptRule — existing
- ✓ ConnectionStatus enum + IntegrationHealthService pattern — existing
- ✓ Temporal workflow infrastructure (IntegrationSyncWorkflowImpl) — existing
- ✓ SourceType enum values: INTEGRATION, IMPORT, API, WORKFLOW, TEMPLATE, USER_CREATED — existing
- ✓ AuditableEntity + SoftDeletable base pattern — existing

### Active

<!-- v1 scope — Postgres adapter milestone. -->

- [ ] Two-layer model (Source Layer / Projection Layer) formalized via SourceType
- [ ] IngestionAdapter interface with RecordBatch + SyncMode contract
- [ ] PostgresAdapter implementation (JDBC, polling, cursor pagination)
- [ ] CustomSourceConnectionEntity with encrypted credentials
- [ ] SSRF protection on connection creation (blocklist + resolved-IP check)
- [ ] Read-only role enforcement on connection verification
- [ ] Credential redaction in logs
- [ ] Schema introspection via INFORMATION_SCHEMA
- [ ] Column → attribute mapping UI flow
- [ ] FK constraint introspection → auto relationships
- [ ] NL-assisted column mapping (LLM suggests domain/group/identifier)
- [ ] IngestionOrchestrator service (per-record idempotent post-fetch pipeline)
- [ ] CustomSourceSyncWorkflow (Temporal) with fetch/process/health activities
- [ ] EntityProjectionService trigger generalized to any source type
- [ ] CustomSourceHealthService reusing ConnectionStatus pattern
- [ ] NangoAdapter thin wrapper (created, not wired — enables future unification)
- [ ] Source Data sidebar UI separating source-layer vs projection-layer types
- [ ] Per-workspace cached HikariCP pool (maxPoolSize=2)
- [ ] Sync-cursor column index warning at mapping step
- [ ] Test coverage for all failure modes in registry (SSRF, RO, credential, sync, health)

### Out of Scope

- CDC adapter (logical replication) — TODO-CS-002, P2, deferred post-validation
- CSV adapter — no current demand
- Webhook adapter — no current demand
- Cross-source identity resolution (source↔source direct linking) — TODO-CS-001, P2
- Full IntegrationSyncWorkflow migration to IngestionOrchestrator — unify later
- Schema reconciliation for custom sources — no drift yet
- Aggregation columns on custom source entities — depends on separate feature
- Credential key rotation strategy — security review track
- SSH tunnel / agent-based connection topology — direct TCP only for MVP
- DELETE detection via polling — deferred to CDC adapter

## Context

- Validated user: Mac (Badho, B2B FMCG marketplace). Postgres-based ops data. Blocked from using Riven because no DB connector.
- Competitive landscape crowded on data unification + NL query (Triple Whale, Luca, Polar, CamelAI). Differentiator is trigger → action → measurement loop.
- Prototype build (Python/FastAPI/Streamlit on Mac's DB) runs in parallel and feeds learnings back into this architecture.
- Brownfield: codebase already mapped at `.planning/codebase/`. Backend is Spring Boot 3.5.3 + Kotlin, layered with domain-scoped packages. Frontend Next.js 15 App Router.
- Source plan docs:
  - `/home/jared/.claude/plans/composed-moseying-lagoon.md` (CEO + Eng reviewed plan)
  - `~/.gstack/projects/rmr-studio-riven/jared-main-eng-review-test-plan-20260412-140000.md` (test matrix)

## Constraints

- **Tech stack:** Kotlin + Spring Boot 3.5.3 (backend), Next.js 15 (frontend), Temporal (async), JPA/Hibernate + Postgres (internal), HikariCP (JDBC pool)
- **Security:** SSRF protection is shipping blocker. Read-only role enforcement is shipping blocker. No credentials in logs ever.
- **Connection topology:** Direct TCP only (user whitelists Riven IP). SSH tunnel / agent deferred.
- **Encryption:** AES-256-GCM, app-level key from env var, encrypted JSONB on CustomSourceConnectionEntity.
- **Concurrency:** max_concurrent_syncs per workspace configurable, default 3.
- **Per-connection pool:** HikariCP, maxPoolSize=2, idleTimeout=10m, maxLifetime=30m, per-workspace cached.
- **Backward compat:** Existing IntegrationSyncWorkflowImpl must continue working unchanged. NangoAdapter is additive wrapper, not migration.
- **Idempotency:** IngestionOrchestrator.process() is per-record idempotent (Temporal retry-safe).

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Two-layer data model (Source / Projection) | Integration entities become visible; custom sources get first-class treatment; standalone source entities stay usable without core-model projection | — Pending |
| IngestionAdapter interface as abstraction boundary | Postgres and Nango (and future CSV/webhook/CDC) share common contract; enables per-source adapter without coupling | — Pending |
| Polling via Temporal scheduled workflow (not CDC) for v1 | Simplest; matches existing pattern; works on any Postgres ≥9; CDC deferred | — Pending |
| PostgresAdapter bypasses SchemaMappingService | Postgres columns are already typed; mapping ceremony is redundant; SchemaMappingService remains for Nango JSON payloads | — Pending (Eng review) |
| Per-workspace cached HikariCP pool | Avoid per-sync setup cost; cap at 2 connections; not mixed with app pool | — Pending (Eng review) |
| Encrypted JSONB credentials on entity | Simplest; defers Supabase vault migration to security review | — Pending |
| NangoAdapter thin wrapper created but not wired | Unblocks future unification without forcing migration now | — Pending |
| `EntityTypeEntity.readonly` already exists — CUSTOM_SOURCE sets true | No schema change needed | — Pending (Eng review) |
| Split orchestration path (existing Nango unchanged) | Risk minimization; TODO captured for later unify | — Pending (Eng review) |

---
*Last updated: 2026-04-12 after initialization*
