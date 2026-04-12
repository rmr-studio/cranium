---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: planning
stopped_at: Completed 02-04-PLAN.md
last_updated: "2026-04-12T23:08:34.990Z"
progress:
  total_phases: 8
  completed_phases: 2
  total_plans: 8
  completed_plans: 8
  percent: 100
---

# STATE

**Last updated:** 2026-04-12 (Phase 01 complete)

## Project Reference

- **Name:** Unified Data Ecosystem — Postgres Adapter
- **Core Value:** Any data source → unified entity model → trigger → action → measurement loop
- **Current Focus:** Phase 1 — Adapter Foundation (COMPLETE); Phase 2 next
- **Branch:** postgres-ingestion
- **Worktree:** /home/jared/dev/worktrees/postgres-ingestion

## Current Position

- **Phase:** 2 — Secure Connection Management (COMPLETE 5/5 plans)
- **Plan:** 02-04 complete (CustomSourceConnectionService + Controller + 6 REST endpoints); Phase 2 shipping-blockers all satisfied (CONN-01..05, SEC-01..03, SEC-05, SEC-06)
- **Status:** Ready to plan
- **Progress:** [██████████] 100%

```
[........] 0% (0/8 phases)
```

## Performance Metrics

| Metric | Value |
|--------|-------|
| v1 Requirements | 68 |
| Phases | 8 |
| Coverage | 68/68 (100%) |
| Granularity | standard |
| Parallelization | enabled |
| Phase 01-adapter-foundation P01 | 15min | 3 tasks | 8 files |
| Phase 01-adapter-foundation P02 | 5min | 3 tasks | 12 files |
| Phase 01-adapter-foundation P03 | 10min | 3 tasks | 5 files |
| Phase 02-secure-connection-management P00 | 1min | 2 tasks | 9 files |
| Phase 02-secure-connection-management P01 | 20min | 2 tasks | 7 files |
| Phase 02-secure-connection-management P02 | 4 min | 2 tasks | 8 files |
| Phase 02-secure-connection-management P03 | 9min | 2 tasks | 4 files |
| Phase 02-secure-connection-management P04 | 9min | 3 tasks | 15 files |

## Accumulated Context

### Decisions

- **Plan 01-01:** SyncMode lives under `models/ingestion/adapter/` (adapter capability, not persisted)
- **Plan 01-01:** Testcontainers Postgres used for `EntityTypeEntity` round-trip tests — H2 rejects jsonb, pgvector, and reserved column names (`key`, `value`) in the entity schema
- **Plan 01-01:** `SchemaIntrospectionResult` kept minimal; Phase 3 (PG-07) extends with PK/FK metadata
- [Phase 01-adapter-foundation]: Plan 01-02: NangoCallContext.workspaceId defaults to empty in Phase 1; Phase 4 orchestrator supplies real value
- [Phase 01-adapter-foundation]: Plan 01-02: @SourceTypeAdapter ships annotation-only; @Configuration registry factory lands in Plan 03 with NangoAdapter
- [Phase 01-adapter-foundation]: Plan 01-02: Sealed AdapterException hierarchy — Temporal do-not-retry uses FatalAdapterException::class.sealedSubclasses, not boolean flags
- [Phase 01-adapter-foundation]: Plan 01-03: Use positional any()/anyOrNull() Mockito matchers when stubbing Kotlin functions with default-value parameters — named-arg matchers misalign with synthetic overloads
- [Phase 01-adapter-foundation]: Plan 01-03: NangoAdapter registered but dormant — IntegrationSyncWorkflowImpl/ActivitiesImpl byte-identical, live sync path untouched until Phase 4 unification
- [Phase 01-adapter-foundation]: Plan 01-03: ProjectionPipelineIntegrationTestConfig excludes NangoAdapter from its ComponentScan because the Nango HTTP layer is intentionally omitted (same pattern as queue service excludes)
- [Phase 02-secure-connection-management]: Plan 02-00: @Disabled + placeholder() body pattern for Wave 0 scaffolds — keeps suite green until downstream plans populate
- [Phase 02-secure-connection-management]: Plan 02-00: CustomSourceConnectionEntityFactory landed as empty object; plan 02-01 must populate create() alongside entity creation
- [Phase 02-secure-connection-management]: Plan 02-01: @SQLRestriction must be declared on concrete entity, not only @MappedSuperclass — Hibernate 6 does not reliably propagate mapped-superclass SQLRestriction to derived queries. Matches project-wide pattern (WorkspaceEntity, EntityEntity, BlockEntity).
- [Phase 02-secure-connection-management]: Plan 02-01: ConnectionException sealed hierarchy is a sibling to Phase 1 AdapterException (not a subclass). All subtypes extend RuntimeException for @Transactional default rollback.
- [Phase 02-secure-connection-management]: Plan 02-01: CustomSourceConnectionModel omits encryptedCredentials/iv/keyVersion/password; service passes decrypted host/port/db/user/sslMode to toModel() at read time.
- [Phase 02-secure-connection-management]: Plan 02-01: Integration test hand-creates minimal workspaces table via JdbcTemplate (scans only customsource package) to avoid pulling WorkspaceInviteEntity→UserEntity chain into the test persistence unit.
- [Phase 02-secure-connection-management]: Plan 02-02: AES-256-GCM with 12-byte SecureRandom IV per call; keyVersion=1 reserved for rotation. AEADBadTagException -> DataCorruptionException (wrong-key and corruption indistinguishable at cipher layer).
- [Phase 02-secure-connection-management]: Plan 02-02: Global Logback TurboFilter on root LoggerContext covers third-party JDBC/HikariCP paths. Re-log-and-DENY rewrite pattern — stack trace loss on re-log accepted; service-layer exception sanitisation remains primary defence.
- [Phase 02-secure-connection-management]: Plan 02-02: RIVEN_CREDENTIAL_ENCRYPTION_KEY env var contract: base64-encoded 32-byte key, fail-fast on blank/non-base64/wrong-size at bean init.
- [Phase 02-secure-connection-management]: Plan 02-03: NameResolver seam on SsrfValidatorService enables DNS-rebinding tests without a fake DNS server; DefaultNameResolver @Component wraps InetAddress.getAllByName in production
- [Phase 02-secure-connection-management]: Plan 02-03: @Throws(UnknownHostException::class) on NameResolver.resolve — required for mockito-kotlin thenThrow to accept the checked exception (Kotlin default signature has no throws declaration)
- [Phase 02-secure-connection-management]: Plan 02-03: SsrfValidatorService.GENERIC_MESSAGE asserted verbatim in tests so accidental CIDR/category leakage is caught in CI; protects against error-message reconnaissance oracle
- [Phase 02-secure-connection-management]: Plan 02-03: IPv4-mapped IPv6 addresses unwrapped and re-checked rather than matched by /96 prefix — lets JVM-builtin isLoopbackAddress/isSiteLocalAddress short-circuit; covers ::ffff:* comprehensively
- [Phase 02-secure-connection-management]: Plan 02-03: ReadOnlyRoleVerifier uses DriverManager (never HikariCP); reflective test asserts no Hikari/DataSource references on the service class so a future @Autowire DataSource refactor gets caught
- [Phase 02-secure-connection-management]: Plan 02-03: SAVEPOINT probe uses CREATE TABLE (not INSERT) — rejects any schema-mutation capability; cheapest write that doesn't need a pre-existing target; pass iff fails with SQLState 42501
- [Phase 02-secure-connection-management]: Plan 02-03: Test role teardown uses PL/pgSQL DO + DROP OWNED BY <role> + DROP ROLE instead of DROP ROLE IF EXISTS — direct drop fails on re-runs with 'role cannot be dropped because some objects depend on it'
- [Phase 02-secure-connection-management]: Plan 02-04: CryptoException/DataCorruptionException at read-time NEVER propagate to HTTP — they transition ConnectionStatus to FAILED with a user-safe message. Credential failures are operational state, not API errors.
- [Phase 02-secure-connection-management]: Plan 02-04: update() credential-touching branch decrypts current payload, merges PATCH fields (preserving unset fields including password), re-runs gate chain on merged payload, re-encrypts with fresh IV. Cosmetic-only (name) path skips gates entirely via UpdateCustomSourceConnectionRequest.touchesCredentials().
- [Phase 02-secure-connection-management]: Plan 02-04: listByWorkspace isolates per-row decrypt failures — one bad row returns a FAILED model with [unavailable] fields, remaining rows hydrate normally. No list-level failure from a single corrupt credential.
- [Phase 02-secure-connection-management]: Plan 02-04: @PreAuthorize cross-workspace blocking asserted at the SpringBootTest service-layer test rather than MockMvc — standalone MockMvc does not load method security. Controller test focuses on wire format + bean-validation + ExceptionHandler mapping.
- [Phase 02-secure-connection-management]: Plan 02-04: SslMode uses libpq-canonical kebab strings (require/verify-ca/verify-full/prefer) via @JsonValue so JDBC driver consumes stored string directly; @JsonCreator throws IllegalArgumentException on unknown values (Jackson wraps in JsonMappingException).

### Key Decisions (from PROJECT.md)

- Two-layer data model (Source / Projection) via SourceType
- `IngestionAdapter` is the abstraction boundary; Postgres + Nango wrapper on day one
- Polling via Temporal scheduled workflow (not CDC) for v1
- `PostgresAdapter` bypasses `SchemaMappingService` (typed columns)
- Per-workspace cached HikariCP pool (maxPoolSize=2, idleTimeout=10m, maxLifetime=30m)
- Encrypted JSONB credentials (AES-256-GCM, env-var key)
- NangoAdapter thin wrapper created but not wired — `IntegrationSyncWorkflowImpl` unchanged
- `EntityTypeEntity.readonly` already exists — CUSTOM_SOURCE sets `readonly=true`

### Shipping Blockers (security)

- SSRF protection (blocklist + DNS-rebinding-safe resolved-IP check)
- Read-only role enforcement on connect
- No credentials in logs (KLogger redaction)

### Open Todos

- None yet (phase planning will populate)

### Blockers

- None

## Session Continuity

### Last Action
Completed Plan 01-03 (NangoAdapter + Registry). Phase 01 closes with: NangoAdapter @Component + @SourceTypeAdapter(INTEGRATION), SourceTypeAdapterRegistry @Configuration assembling Map<SourceType, IngestionAdapter>, full Nango→Adapter exception translation, 13 new tests + full build green (1,735 tests). Live Nango sync path untouched.

### Next Action
Begin Phase 02 planning (per ROADMAP.md).

### Last session
- **Stopped at:** Completed 02-04-PLAN.md
- **Timestamp:** 2026-04-12T07:43:39Z

### Files of Record
- `.planning/PROJECT.md`
- `.planning/REQUIREMENTS.md`
- `.planning/ROADMAP.md`
- `.planning/STATE.md` (this file)
- `.planning/config.json`
- `.planning/codebase/ARCHITECTURE.md`
- `/home/jared/.claude/plans/composed-moseying-lagoon.md` (upstream CEO/Eng plan)
- `/home/jared/.gstack/projects/rmr-studio-riven/jared-main-eng-review-test-plan-20260412-140000.md` (upstream test plan)
