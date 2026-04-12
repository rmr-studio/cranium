# Phase 1: Adapter Foundation - Context

**Gathered:** 2026-04-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver a unified `IngestionAdapter` contract, supporting types (`RecordBatch`, `SyncMode`), the `SourceType.CUSTOM_SOURCE` enum value, and a `NangoAdapter` thin wrapper that delegates record fetching to the existing `NangoClientWrapper`. No runtime changes to live Nango sync — `IntegrationSyncWorkflowImpl` stays exactly as-is. No Postgres code, no connection entity, no orchestrator wiring (those are Phases 2–4).

</domain>

<decisions>
## Implementation Decisions

### Package placement
- Interface + types live under `riven/core/service/ingestion/adapter/`. New sub-package; keeps contract + concrete adapter impls together.
- Data classes (`RecordBatch`, `SyncMode`) live under `riven/core/models/ingestion/adapter/` to match existing models/* layout.
- `service/ingestion/` remains focused on `EntityProjectionService` + `IdentityResolutionService` — unchanged.
- `NangoAdapter` goes in `service/ingestion/adapter/nango/` (or flat in `service/ingestion/adapter/` — planner's call). `NangoClientWrapper` stays put under `service/integration/`.

### Adapter lifecycle + registry
- Adapters are **stateless Spring beans**, one per source type.
- Registry mechanism: Spring bean qualifier + `Map<SourceType, IngestionAdapter>` injection. `IngestionOrchestrator` (Phase 4) resolves adapter via map lookup by `SourceType`.
- Connection + credentials passed per call (not constructor-injected into adapter). Enables multi-tenant reuse of a single adapter bean.

### RecordBatch cursor encoding
- `nextCursor: String?` is **opaque and adapter-owned**. Each adapter chooses its internal representation (Postgres: ISO timestamp or PK-value string; Nango: its own sync token; future CDC: LSN; future CSV: row offset).
- Orchestrator treats cursor as a round-trip string — never parses, never interprets.
- `null` cursor on input = "start from the beginning." Adapter interprets per its strategy (e.g. Postgres omits `WHERE updated_at > ...`; Nango starts fresh sync).
- `null` cursor on output (combined with `hasMore=false`) = terminal state.

### Error signaling
- Failures raised as typed exceptions (no Result<T> wrapper). Matches existing Spring service pattern and integrates cleanly with Temporal activity retry semantics.
- Define sealed exception hierarchy (names indicative — planner finalizes):
  - `AdapterException` (abstract base)
  - `TransientAdapterException` (network blip, temporary unavailability) → retryable
  - `FatalAdapterException` (base for non-retryable)
    - `AdapterAuthException`
    - `AdapterConnectionRefusedException`
    - `AdapterSchemaDriftException`
    - `AdapterUnavailableException` (persistent)
- Temporal RetryPolicy configured with non-retryable exception type list = the `FatalAdapterException` subtypes. Transient subtype retries with backoff.

### NangoAdapter scope (Phase 1 stub)
- `fetchRecords(cursor, limit)`: **implemented**, delegates to `NangoClientWrapper.fetchRecords`. Translates Nango errors to the adapter exception hierarchy.
- `introspectSchema()`: throws `NotImplementedError` (or a typed `AdapterCapabilityNotSupportedException` — planner decides) with a clear message. Nango schema comes from integration manifests, not runtime introspection; no need in Phase 1.
- `syncMode()`: returns `SyncMode.PUSH`.
- `NangoAdapter` is `@Component` with qualifier tied to `SourceType.INTEGRATION` and registered in the adapter bean map. Not wired into the live sync path — `IntegrationSyncWorkflowImpl` continues to call `NangoClientWrapper` directly. Dead at runtime until a future unification phase pulls the trigger.

### SourceType enum change
- Add `CUSTOM_SOURCE` to `riven/core/enums/integration/SourceType.kt`.
- JPA enum persistence already configured via existing pattern (verify `@Enumerated(STRING)` usage on `EntityTypeEntity.sourceType`).
- Migration: no DB migration needed if persisted as STRING; planner verifies.

### Testing scope for Phase 1
- Unit tests: contract tests on the interface (via a fake adapter), `NangoAdapter.fetchRecords` delegation test with mocked `NangoClientWrapper`, adapter registry wiring test (`Map<SourceType, IngestionAdapter>` contains INTEGRATION).
- JPA test: `EntityTypeEntity` round-trips `SourceType.CUSTOM_SOURCE`.
- No integration tests — those start in Phase 2+.

### Claude's Discretion
- Exact sealed-exception class names and the package they live in (`adapter/exception/` vs flat).
- Whether `NangoAdapter` sub-package exists or whether the class sits flat in `service/ingestion/adapter/`.
- Naming of the `@Qualifier` values (e.g. `SourceTypeAdapter("INTEGRATION")` vs Spring's `@Qualifier("integration")`).
- KLogger usage in `NangoAdapter`.
- Whether `AdapterCapabilityNotSupportedException` is a distinct type or just a message on `FatalAdapterException`.

</decisions>

<specifics>
## Specific Ideas

- Live Nango runtime must not change. `IntegrationSyncWorkflowImpl` and `IntegrationSyncActivitiesImpl` remain untouched. `NangoAdapter` is a wrapper that enables a future unification without forcing one now.
- CEO plan explicitly says `NangoAdapter` delegates to `NangoClientWrapper`, NOT `IntegrationSyncActivitiesImpl`. The activities file contains orchestration logic that belongs in `IngestionOrchestrator` eventually — not in the adapter.
- Cursor opacity is an explicit design choice so CDC (LSN), CSV (offset), and webhook (sequence number) adapters can plug in later without reworking the core contract.

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `service/ingestion/EntityProjectionService.kt` — lives in the target package; shows the existing style for ingestion-layer services.
- `service/ingestion/IdentityResolutionService.kt` — ditto.
- `models/ingestion/ProjectionResult.kt`, `ResolutionResult.kt` — existing result types in the matching models package; our new `RecordBatch` + `SyncMode` follow the same naming/location pattern.
- `service/integration/NangoClientWrapper.kt` — `NangoAdapter.fetchRecords` delegates here.
- `enums/integration/SourceType.kt` — single-file enum; add `CUSTOM_SOURCE` value.

### Established Patterns
- Service layer packaged `riven/core/service/{domain}/`; domain models in `riven/core/models/{domain}/`; enums in `riven/core/enums/{domain}/`. Our new types fit cleanly.
- `@Component` + constructor injection for services. `@Qualifier` used elsewhere in the codebase for multi-impl beans (verify in planner).
- Data classes with JPA `@Entity` use `toModel()` conversions; our pure data classes (`RecordBatch`) don't need that — they are domain models, not JPA entities.
- Soft delete + audit via `AuditableEntity` + `SoftDeletable` — not relevant in Phase 1 (no new entities).

### Integration Points
- `EntityTypeEntity.sourceType` (existing field) must accept `CUSTOM_SOURCE`.
- `EntityTypeEntity.readonly` already exists (line 75 per Eng review) — not modified in Phase 1 but called out as downstream-relevant in Phase 3.
- Future `IngestionOrchestrator` (Phase 4) will inject the `Map<SourceType, IngestionAdapter>` produced here.
- Temporal activity wiring will consume the adapter contract in Phase 4.

</code_context>

<deferred>
## Deferred Ideas

- Wiring `NangoAdapter` into the live integration sync — Phase 4+ / dedicated unification phase (TODO captured in plan doc).
- `introspectSchema()` implementation for Nango — later phase once manifests are parsed at runtime.
- CDC / CSV / webhook adapter implementations — v2 roadmap.
- `IngestionOrchestrator` and `Map<SourceType, IngestionAdapter>` consumption — Phase 4.

</deferred>

---

*Phase: 01-adapter-foundation*
*Context gathered: 2026-04-12*
