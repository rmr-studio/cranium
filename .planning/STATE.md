---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: planning
stopped_at: Completed 03.1-01-PLAN.md
last_updated: "2026-04-13T08:33:32.534Z"
progress:
  total_phases: 9
  completed_phases: 3
  total_plans: 17
  completed_plans: 14
  percent: 82
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

- **Phase:** 03.1 — Boot 3.5.3 → 4.0.5 Upgrade with Transitive Dep Bumps (in progress)
- **Plan:** 03.1-01 complete (compile-clean Boot 4 + Hibernate 7 dep bump). 03.1-02 (Spring Security 7 SecurityFilterChain migration), 03.1-03 (test harness sweep), 03.1-04 (closure + Nango smoke) still to execute.
- **Status:** Ready to plan next plan
- **Progress:** [████████░░] 82%

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
| Phase 03-postgres-adapter-schema-mapping P00 | 8min | 2 tasks | 14 files |
| Phase 03-postgres-adapter-schema-mapping P01 | 9 min | 2 tasks | 16 files |
| Phase 03-postgres-adapter-schema-mapping P02 | 11min | 3 tasks | 12 files |
| Phase 03-postgres-adapter-schema-mapping P03 | 10min | 3 tasks | 14 files |
| Phase 03.1-boot-3-5-3-4-0-x-upgrade-with-transitive-dep-bumps P01 | 10min | 3 tasks | 18 files |

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
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-00: Wave-0 entity shells carry the full field list (no JPA annotations yet) — plan 03-01 annotates + DDL in one sweep without touching factory call sites.
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-00: Factories live flat under service/util/factory/ (not a customsource/ subfolder) to match plan 03-00's declared files list and keep the Phase 3 factory surface greppable by test-class name.
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-00: Pre-existing riven.core.lifecycle.* imports in service/dev/ auto-fixed (Rule 3 blocking) — compileTestKotlin was red on postgres-ingestion HEAD before any Phase 3 scaffold existed; canonical package is riven.core.models.core.*.
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-00: 13 pre-existing DataConnectorConnectionServiceTest failures deferred to phase-scoped deferred-items.md — out-of-scope for Wave-0 scaffolding; narrowed-glob verification confirms every new @Disabled scaffold is BUILD SUCCESSFUL.
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-01: SHA-256 hex (64 chars) chosen over xxHash for schema-hash — JDK-standard MessageDigest, no new dependency, deterministic
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-01: FK from connector_(table|field)_mappings to data_connector_connections is ON DELETE CASCADE — mappings are connection-internal state with no historical value once the parent is hard-purged; soft-delete on the connection does not trigger cascade
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-01: Integration test applies FK CASCADE via JdbcTemplate mirroring production DDL — Hibernate ddl-auto=create-drop does not emit ON DELETE CASCADE from JPA annotations alone; declarative SQL in db/schema/04_constraints/ remains single source of truth
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-01: enumOptions != null is the SELECT signal in PgTypeMapper — caller resolves pg_enum rows; the pg type literal for a user-defined enum is user-supplied and cannot be enumerated in a when
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-01 Rule-3 blocking auto-fix: resolved unresolved Git merge-conflict markers in core/src/main/resources/application.yml around websocket block — SnakeYAML ScannerException blocked all integration-test bootstrap on postgres-ingestion HEAD
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-02: Pool keying by connectionId, not workspaceId — a workspace owns many connections, each gets its own HikariCP pool (name is historic)
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-02: HikariConfig.initializationFailTimeout = -1 so pool construction never eagerly connects; first getConnection() call surfaces any connection error for translation into AdapterAuthException/AdapterConnectionRefusedException
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-02: FK metadata exposed via introspectWithFkMetadata sibling method rather than extending Phase 1 IngestionAdapter contract — keeps Nango + future adapters from implementing a surface they do not use
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-02: Server-side Postgres cursor (autoCommit=false + fetchSize=props.defaultBatchSize) is mandatory for streaming — without it the JDBC driver buffers the entire ResultSet in memory
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-02: Cursor SQL casts comparison column with ::text so a single String parameter compares against any column type (uuid/bigint/text); timestamp cursors use dedicated ?::timestamptz variant; null cursor omits WHERE entirely
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-02: Pool eviction via direct service call (DataConnectorConnectionService.update credential branch + softDelete → poolManager.evict), not events. Cosmetic updates skip eviction
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-02 Rule-1 auto-fix: added initializationFailTimeout=-1 to HikariConfig so 6 pool unit tests can build against fake credentials without triggering PoolInitializationException
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-02 Rule-1 auto-fix: PK-fallback SQL omits WHERE when cursor is null and casts column with ::text when cursor is non-null — avoids 'operator does not exist: uuid > character varying' against UUID PKs
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-03: Save order validate -> persist-fields -> persist-table -> create/update EntityType -> create relationships -> mark published -> log activity; single @Transactional scope so any failure rolls back completely
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-03: Pending-relationship mechanism — FK metadata persists on the field row when target table unpublished; recipient's future Save materialises the relationship. No separate retry queue needed.
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-03: EntityTypeService not extended — direct EntityTypeRepository.save with sourceType=CONNECTOR + readonly=true. EntityTypeService.publishEntityType is tuned for user-driven CRUD with auto-Name attribute; CONNECTOR types are readonly with attributes driven from mapping columns
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-03: Cursor-index warning surfaced from BOTH GET /schema and POST /mapping (belt + suspenders per plan output spec) — GET probes chosen-or-auto-detected cursor column; Save probes request's isSyncCursor column
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-03: Workspace-mismatch 403 assertion at service-layer SpringBootTest, not MockMvc — standalone MockMvc does not load @PreAuthorize. Phase 2 02-04 lesson carried forward; controller test documents placement explicitly
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-03: Activity.DATA_CONNECTOR_CONNECTION reused for mapping Saves (already exists since Phase 2) — no new enum variant. details map carries connectionId + tableName
- [Phase 03-postgres-adapter-schema-mapping]: Plan 03-04 DEFERRED (2026-04-13, R1 decision): MAP-07 (LLM-assisted mapping suggestions) requires Spring AI 2.x multi-provider ChatModel abstraction (Anthropic default + OpenAI reasoning fallback), which only targets Spring Boot 4.x. Boot 4 upgrade scoped to new phase 03.5-boot4-upgrade. No code/build/dependency changes made; bookkeeping only. MAP-03/04/05 remain Complete via 03-03 manual selection — only the LLM pre-fill UX is deferred. Distinct env vars locked: ANTHROPIC_API_KEY (default) + OPENAI_REASONING_API_KEY (separate from embeddings' OPENAI_API_KEY) for independent rotation.
- [Phase 03.1]: Plan 01-01: hypersistence-utils-hibernate-71 (not -70) — artifact encodes Hibernate major; -71 targets 7.2.x to match Boot 4.0.5 BOM; -70 targets 7.0.x and hits IncompatibleClassChangeError on final getJavaTypeClass
- [Phase 03.1]: Plan 01-01: Resilience4j uniform 2.3.0 across core/annotations/spring — resilience4j-spring caps at 2.3.0 so no 2.4.0 promotion
- [Phase 03.1]: Plan 01-01: Boot 4 package relocations (Rule 3 auto-fix) — actuate.health -> health.contributor; autoconfigure.security.servlet -> security.autoconfigure; autoconfigure.domain.EntityScan -> persistence.autoconfigure.EntityScan
- [Phase 03.1]: Plan 01-01: Spring Security 7 GrantedAuthority.authority is @Nullable — null-safe accessors in OrganisationSecurity/WebSocketSecurityInterceptor/AuthTokenService; SecurityFilterChain migration deferred to Plan 02
- [Phase 03.1]: Plan 01-01: Temporal 1.34.0 starter POM still lists Boot 2.7.18 parent — accepted; Boot 4 BOM overrides at build time; runtime surprises are Plan 03/04 surface
- [Phase 03.1]: Plan 01-01: hibernate-vector pinned to 7.2.7.Final matching Boot 4.0.5 BOM hibernate.version exactly — avoids minor-drift ClassChangeError

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

### Roadmap Evolution

- Phase 03.1 inserted after Phase 3: Boot 3.5.3 → 4.0.x upgrade with transitive dep bumps (URGENT)

## Session Continuity

### Last Action
Completed Plan 03.1-01 (Spring Boot 3.5.3 → 4.0.5 + Hibernate 7.2.7 dep bump). Gradle plugin + core deps on Boot 4; Spring Security pins released to BOM (now 7.0.4); Temporal 1.34.0; SpringDoc 3.0.3; ShedLock 7.7.0; resilience4j-spring-boot3 dropped for direct resilience4j-core + -annotations + -spring at 2.3.0. Hypersistence switched to `hypersistence-utils-hibernate-71:3.15.2` (targets Hibernate 7.2.x) after `-70` hit IncompatibleClassChangeError against Boot 4.0.5's Hibernate 7.2.7. hibernate-vector pinned to 7.2.7.Final. Six Rule 3 compile-blocking auto-fixes: Boot 4 actuator/autoconfig package relocations (health → health.contributor, security autoconfigs split out of spring-boot-autoconfigure, EntityScan → spring-boot-persistence), Spring Security 7 GrantedAuthority.authority nullability, Reactor 3.7+ Mono<T : Any>, JUnit Jupiter 6 non-null ExtensionContext. Plus one Rule 1 bug (hypersistence -70 vs -71 artifact mismatch). `./gradlew compileKotlin compileTestKotlin` BUILD SUCCESSFUL; properties-migrator log clean (no renamed-key hits). SecurityFilterChain migration intentionally NOT touched — Plan 02 owns that.

### Previous Action
Deferred Plan 03-04 (NL-Assisted Mapping Suggestions) via R1 decision on 2026-04-13. No code, no build.gradle.kts edit, no Spring AI dependency. Bookkeeping artifacts only: 03-04-SUMMARY.md (status=deferred), STATE.md updated, ROADMAP.md updated. MAP-07 remains Pending in REQUIREMENTS.md; MAP-03/04/05 unchanged (Complete via 03-03). Reopen condition: phase 03.5-boot4-upgrade lands Boot 4.x, at which point 03-04-PLAN.md is rewritten against Spring AI 2.x ChatModel multi-provider coordinates (Anthropic default + OpenAI reasoning fallback). Earlier: Completed Plan 03-02 (PostgresAdapter + WorkspaceConnectionPoolManager). WorkspaceConnectionPoolManager caches one HikariDataSource per connectionId via ConcurrentHashMap.computeIfAbsent; HikariConfig uses initializationFailTimeout=-1 so pool construction never eagerly connects. PostgresAdapter @SourceTypeAdapter(SourceType.CONNECTOR) implements IngestionAdapter — syncMode=POLL, introspectSchema + introspectWithFkMetadata sibling method for plan 03-03 to consume ForeignKeyMetadata. PostgresFetcher builds server-side-cursor SQL (autoCommit=false + fetchSize) with cursor-or-PK-fallback variants; casts comparison column with ::text for cross-type UUID/bigint/text compatibility; null-cursor first fetch omits WHERE entirely. JDBC SQLState translator: 28xxx→AdapterAuthException, 57014→AdapterUnavailableException (timeout), 08xxx→AdapterConnectionRefusedException, else→AdapterUnavailableException. Pool eviction wired into DataConnectorConnectionService credential-update + softDelete branches (not cosmetic). 20 tests green (6 pool + 11 adapter against Testcontainers pgvector/pg16 + 3 new eviction tests + 13 previously-failing service tests unblocked by the added @MockitoBean poolManager).

### Next Action
Execute Plan 03.1-02 (Spring Security 7 SecurityFilterChain migration) — lambda-DSL strictness, current authorization API idioms on `core/src/main/kotlin/riven/core/configuration/auth/SecurityConfig.kt`. Compile-clean baseline from 03.1-01 makes this straightforward. Plans 03.1-03 (test harness sweep) and 03.1-04 (closure + Nango smoke) can run in parallel waves afterward. Phase 03.5 (reopen 03-04 against Spring AI 2.x) unblocks once 03.1-04 closes.

### Last session
- **Stopped at:** Completed 03.1-01-PLAN.md
- **Timestamp:** 2026-04-13T12:00:00Z

### Files of Record
- `.planning/PROJECT.md`
- `.planning/REQUIREMENTS.md`
- `.planning/ROADMAP.md`
- `.planning/STATE.md` (this file)
- `.planning/config.json`
- `.planning/codebase/ARCHITECTURE.md`
- `/home/jared/.claude/plans/composed-moseying-lagoon.md` (upstream CEO/Eng plan)
- `/home/jared/.gstack/projects/rmr-studio-riven/jared-main-eng-review-test-plan-20260412-140000.md` (upstream test plan)
