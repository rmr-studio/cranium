# Phase 3: Postgres Adapter & Schema Mapping - Context

**Gathered:** 2026-04-13
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver the `PostgresAdapter` (JDBC, `IngestionAdapter` implementation), a per-workspace cached HikariCP pool infrastructure, Postgres schema introspection via `INFORMATION_SCHEMA`, and a column-to-attribute mapping subsystem that persists user mappings, produces readonly `CUSTOM_SOURCE` `EntityTypeEntity` rows, creates best-effort FK-inferred `RelationshipDefinitionEntity` rows, and surfaces NL-assisted suggestions and a sync-cursor index warning.

**In scope:** `PostgresAdapter.introspectSchema()` + `fetchRecords(cursor, limit)` (`updated_at` cursor or PK-inserts-only fallback); `WorkspaceConnectionPoolManager` (pool lifecycle); `CustomSourceSchemaInferenceService` (GET `/schema`); `CustomSourceFieldMappingService` (persist mappings, create EntityTypes, infer FK relationships); NL suggestion service (Spring AI, schema-only input, schema-hash cache); Postgres type → `SchemaType` conversion; drift detection via stored schema hash.

**Out of scope:** Temporal sync workflow wiring (Phase 4); per-record sync processing pipeline (Phase 4); projection + identity resolution runs (Phase 5); health service (Phase 6); mapping UI (Phase 7); any SaaS-integration path change.

Phase 3 ships the adapter + mapping machinery; Phase 4 schedules and runs it.

</domain>

<decisions>
## Implementation Decisions

### NL-assisted mapping (MAP-07)

- **Provider: Spring AI abstraction.** Pulls in Spring AI starter. Default model chosen by planner (Anthropic or OpenAI — planner's call based on existing `OpenAiEmbeddingProvider` key availability + cost/quality). Abstraction deliberately chosen for swap-friendliness; starter dependency must go through the "discuss new dependency" gate with the user before `build.gradle.kts` edit.
- **Input: schema only.** Ship table name, column name, pg data type, nullable flag, is-PK, is-FK. No sample rows. No `pg_stats`. Zero PII leaves the user's database. Planner must ensure this is enforced by the service API, not by convention.
- **Output: LifecycleDomain suggestion, SemanticGroup suggestion, identifier-column suggestion, cursor-column suggestion per table.** Per-column attribute-name suggestion allowed. Response shape is structured (Spring AI structured output / function-calling), not free text.
- **Suggestion UX: inline per-column ghost text.** The mapping UI pre-fills dropdowns with LLM suggestions greyed-out. User click = accept, user edit = override. No modal, no accept-all dialog.
- **Caching: per-(connection, table) cached by schema hash.** Hash = stable hash over `(tableName, [(columnName, pgType, nullable)] sorted)`. Suggestions persist in DB (new table, planner names it) keyed by this hash. Re-query LLM only when hash changes. Cache is connection-scoped (no cross-connection reuse).
- **Failure behavior: degrade silently to empty suggestions.** LLM timeout / 5xx / rate limit = no ghost text, mapping UI still fully usable. Never block a save on LLM availability.

### Mapping persistence + drift

- **Shape: `CustomSourceFieldMappingEntity` — one row per (connection, table, column).** Columns: workspace scoping, `connectionId`, `tableName`, `columnName`, `pgDataType`, `nullable`, `isPrimaryKey`, `isForeignKey`, `fkTargetTable` / `fkTargetColumn` (nullable), mapped `attributeName`, mapped `schemaType`, flags `isIdentifier`, `isSyncCursor`, `isMapped`, `stale: Boolean`, and `AuditableEntity` + `SoftDeletable` columns (user-facing persisted state). Queryable, auditable, composable with per-column metadata.
- **Parent entity: a `CustomSourceTableMappingEntity` (one row per (connection, table))** holding table-level config: `lifecycleDomain` (or `UNCATEGORIZED`), `semanticGroup` (or `CUSTOM`), `entityTypeId` (nullable until Save), `schemaHash`, `lastIntrospectedAt`, `published: Boolean`. Links to `CustomSourceFieldMappingEntity` rows. Planner may combine or split — key constraint: both the table-level and the per-column state must be queryable.
- **Save model: explicit Save per table.** User fills the mapping UI, clicks Save. Service transactionally: validates mapping → creates `EntityTypeEntity` (`sourceType=CUSTOM_SOURCE`, `readonly=true`) → creates attribute definitions per mapped column → creates best-effort `RelationshipDefinitionEntity` rows for FKs where both ends are mapped → marks `published=true`. Unpublished tables are not synced (Phase 4 reads `published=true`).
- **Drift detection: schema hash comparison at introspection time.** Every `/schema` call computes a fresh hash and compares to stored `schemaHash` per mapped table. Mismatch → surface drift in the response payload.
- **Drift behavior: flag + require user confirm.**
  - Added columns: visible as unmapped rows in mapping UI with a "N new columns" banner. Sync ignores them until user maps and re-Saves.
  - Dropped columns: matching `CustomSourceFieldMappingEntity` rows are marked `stale=true`. Sync skips `stale=true` columns. Existing entity attribute definitions remain (to preserve historical entities); no data is deleted.
  - User must explicitly re-Save the table mapping to acknowledge drift; re-Save updates the stored `schemaHash`.
- **Drift detection is introspection-time only in Phase 3.** Phase 6 layers sync-time drift on top (table-drop → DEGRADED). No per-sync schema re-query is in Phase 3's scope.

### Identifier / cursor / PK semantics

- **Three separate, conceptually distinct selectors** in the mapping UI and persisted as three independent flags on `CustomSourceFieldMappingEntity`:
  - **Identifier** (`isIdentifier=true`) — the value used by `IdentityResolutionService` as `externalId` / cross-source matching key (e.g. `email` on a `customers` table). Drives Phase 5 cross-source projection.
  - **PK** (`isPrimaryKey=true`) — populated automatically from `pg_constraint.contype='p'` at introspection. Used for per-record upsert resolution (`sourceType` + `externalId = PK-value` fallback when no identifier chosen). Not user-editable; auto-detected, shown as read-only in UI.
  - **Sync cursor** (`isSyncCursor=true`) — single column per table for `WHERE cursor > :lastSyncCursor`. Auto-detected from column-name heuristic (`updated_at` / `modified_at` / `last_modified` of a timestamp type) and pre-selected; user overrides. If none chosen, adapter falls back to PK-based inserts-only strategy (PG-04).
- **Identifier semantics confirmed:** identifier ≠ PK by default. Identifier is the cross-source match key; PK is the intra-source upsert key. They can be the same column (e.g. both email) but are captured as independent flags so Phase 5 reasoning stays clean.
- **Cursor default: auto-detect `updated_at` / `modified_at` / `last_modified`** (case-insensitive, timestamp/timestamptz type). Pre-selected as `isSyncCursor=true` at introspection. User can override to any timestamp column OR unset (PK fallback path).
- **Index warning (MAP-06): warn, allow Save.** At Save time, query `pg_indexes` for the chosen cursor column. If no index found, response includes `cursorIndexWarning: { column, suggestedDdl: "CREATE INDEX ... ON table(column)" }`. UI renders yellow banner. Save proceeds. Rationale: user owns their DB; our cost is bounded by `statement_timeout`, streaming `fetchSize`, bounded batch, and `max_concurrent_syncs=3`. User's DB is the real victim, not ours.
- **Cost guardrails to be shipped in the adapter (not optional):**
  - JDBC `statement_timeout` per query (planner picks value; suggest 5 min).
  - Streaming `ResultSet` with `fetchSize` set (never `getAll` into memory).
  - Bounded batch (`fetchRecords` honors `limit` strictly; planner picks default 5000).
  - Pool already capped at `maxPoolSize=2` per workspace.
- **Non-unique identifier policy:** deferred; not needed for Phase 3 (Phase 5 identity resolution handles the ambiguity). Phase 3 does NOT enforce identifier uniqueness at Save time.

### Type coverage + pool lifecycle

- **Postgres type → `SchemaType` mapping (canonical, planner formalizes as a `PgTypeMapper`):**
  - `text`, `varchar`, `char`, `citext` → `TEXT`
  - `int`, `int2`, `int4`, `int8`, `numeric`, `decimal`, `real`, `float4`, `float8`, `money` → `NUMBER`
  - `bool`, `boolean` → `CHECKBOX`
  - `date` → `DATE`
  - `timestamp`, `timestamptz`, `time`, `timetz` → `DATETIME`
  - `uuid` → `ID` when also the table PK, else `TEXT` (planner's heuristic)
  - Postgres `enum` (user-defined) → `SELECT` with options populated from `pg_enum` at introspection
  - `email`-named text columns or columns with email check constraint → `EMAIL` (NL suggestion may upgrade; planner decides if heuristic lands in Phase 3 or is purely LLM-driven)
  - `jsonb`, `json` → `OBJECT` (store raw JSON structure; future-ready for deep analysis)
  - `array` types (any `_text`, `_int4`, `int[]` etc) → `OBJECT` (JSON-array dump preserving structure)
  - `bytea` → `OBJECT` (base64-encoded dump inside a JSON object; not `FILE_ATTACHMENT` — we do not persist to file storage)
  - PostGIS `geometry` / `geography` → `LOCATION`
  - Any unrecognized / custom composite / domain types → `OBJECT` (JSON representation of the Postgres driver's returned value)
- **Value conversion at `fetchRecords`:** adapter returns typed `JsonValue` wrapped in `EntityAttributePrimitivePayload` per SchemaType. For `OBJECT`-mapped columns the value is a `JsonValue` object/array, not a stringified dump — downstream consumers get real JSON, not string-containing-JSON. For `LOCATION`, planner picks the JSON shape (`{lat, lng}` from PostGIS `ST_AsGeoJSON` projection or similar — confirm at plan time).
- **`EntityAttributePrimitivePayload` is the sole target.** Per PG-05, `SchemaMappingService` is bypassed for Postgres. The adapter produces payloads directly.
- **Pool owner: `WorkspaceConnectionPoolManager` service.** New singleton Spring bean under `service/connector/` (or `service/ingestion/adapter/postgres/` — planner picks). Holds `ConcurrentHashMap<UUID, HikariDataSource>` keyed by `connectionId`. Builds pool on first use with `maxPoolSize=2`, `idleTimeout=10m`, `maxLifetime=30m`, `connectionTimeout` (planner picks ~10s), plus `statement_timeout` via JDBC connection property. Exposes `getPool(connectionId)` + `evict(connectionId)` + `evictAll()`.
- **Pool eviction triggers:**
  - `PATCH /connections/{id}` changes any of `{host, port, database, user, password, sslMode}` → evict old pool (call `HikariDataSource.close()`) → lazy rebuild on next use.
  - Soft-delete `DELETE /connections/{id}` → evict pool immediately.
  - Idle eviction: Hikari's own `idleTimeout=10m` trims idle connections; the pool map entry stays (pool self-refills on next use). Planner may add an explicit LRU eviction if memory pressure matters later — not required for Phase 3.
  - Application shutdown: `@PreDestroy` on the manager closes all pools.
- **PostgresAdapter is stateless** (per Phase 1). It receives a `CustomSourceConnectionEntity` (or a decrypted credential + connectionId) per call, asks the manager for a pool, runs the query, returns. No instance state.
- **Introspection uses the cached pool** (`/schema` warms it; sync reuses it). Phase 2's `/test` and RO re-verification continue to use short-lived JDBC — Phase 3 does not retrofit them.
- **Credential flow:** `PostgresAdapter` / introspection service fetches `CustomSourceConnectionEntity` → calls `CredentialEncryptionService.decrypt()` → passes plaintext `CredentialPayload` to `WorkspaceConnectionPoolManager.getPool(connectionId, credentials)`. Manager caches the pool; decryption happens once per pool lifetime. Plaintext credentials never persist beyond pool construction.

### FK inference (PG-07)

- **Source:** `pg_constraint` where `contype='f'` joined `pg_attribute` + `pg_class`, scoped to user-accessible schemas.
- **Created only when both ends are mapped-and-published.** If FK target table has no published mapping, the FK is stored as metadata on the field mapping (`fkTargetTable`, `fkTargetColumn`) but no `RelationshipDefinitionEntity` is created. Re-Save of the target table attempts to build the pending relationship.
- **Composite FKs: skip in v1.** Log + surface in mapping response as "unsupported composite FK" note. Not blocking.
- **Cardinality hint:** FK column `NOT NULL` → required, nullable → optional. If FK column has a UNIQUE constraint → one-to-one; else one-to-many. Planner confirms the mapping to existing `RelationshipDefinitionEntity` cardinality fields.
- **Re-introspection after upstream DDL:** new FKs detected on Save become new relationships; removed FKs mark the matching relationship as `stale=true` (not deleted) to preserve historical relationship data. Matches the drift behavior for columns.

### Testing scope for Phase 3

- **Unit tests:**
  - `PgTypeMapper` per-type coverage (every mapping above, including `OBJECT` fallback, `LOCATION` for geometry, enum → SELECT with options).
  - `PostgresAdapter.fetchRecords` with mocked `DataSource`: cursor path (`WHERE updated_at > ?`), PK fallback path, limit/pagination, `OBJECT` value round-trip, empty result.
  - `WorkspaceConnectionPoolManager` pool caching, eviction on PATCH, eviction on delete, `@PreDestroy`.
  - Schema-hash computation determinism (column order independence).
  - NL suggestion cache hit/miss on hash match.
  - Drift detection: added column, dropped column, type change surfaced.
  - FK inference: both-mapped → relationship created; one-mapped → pending metadata only; composite FK → skipped.
- **Integration (Testcontainers Postgres):**
  - `/schema` end-to-end against a real seeded schema including enums, jsonb, array, uuid, PostGIS `geometry` (if container supports it — planner decides on PostGIS image).
  - Full mapping → Save → EntityType creation → FK relationship creation → re-introspection after `ALTER TABLE` (add column, drop column, add FK).
  - HikariCP pool against real Postgres, `statement_timeout` enforced.
  - `fetchRecords` with `updated_at` cursor against 10k+ rows, verifying streaming `fetchSize` prevents OOM on larger runs.
  - RO role (from Phase 2) exercised — introspection queries succeed against a grant-only role.
- **Controller (MockMvc):** workspace scoping, `/schema` returns hashed schema with drift indicator, mapping Save returns 201 with created `entityTypeId`, cursor-index-warning payload present when column unindexed.
- **NL suggestion tests:** mocked Spring AI client, cache hit avoids call, failure returns empty suggestions without breaking mapping.
- **Factories:** `CustomSourceTableMappingEntityFactory`, `CustomSourceFieldMappingEntityFactory` added under `service/util/factory/`. All entity construction in tests goes through factories (per project rule).

### Claude's Discretion

- Exact package placement of new services (`service/ingestion/adapter/postgres/`, `service/connector/pool/`, `service/customsource/mapping/` — planner chooses consistent with existing `service/connector/` and `service/ingestion/adapter/` split).
- Precise Spring AI model choice (Anthropic vs OpenAI) and starter artifact — planner surfaces as a dependency-approval question before editing `build.gradle.kts`.
- Exact schema-hash algorithm (SHA-256 of canonicalized JSON vs xxHash etc). Planner picks deterministic stable choice.
- `statement_timeout` default (suggest 5 min), `connectionTimeout` default (suggest 10s), batch size default (suggest 5000).
- Whether `PgTypeMapper` is a single class or a registry of per-type handlers. Single class is fine for v1.
- Geometry JSON shape (`ST_AsGeoJSON` projection vs `{type, coordinates}` vs driver default) — planner confirms against PostGIS availability.
- Exact structure of the NL suggestion cache table or whether suggestions live on `CustomSourceTableMappingEntity` as JSONB.
- Whether the cursor-index warning is computed on `/schema` (cheap, always shown) or only on Save (more targeted). Both acceptable; planner's call.
- Whether `CustomSourceFieldMappingEntity` and `CustomSourceTableMappingEntity` are separate tables or a single table with a discriminator — planner picks the cleaner JPA shape.

</decisions>

<specifics>
## Specific Ideas

- Spring AI is a **new dependency**. Per `core/CLAUDE.md` "Always Perform List": any new dependency must be discussed before adding. Planner must surface the exact artifact + version + provider choice as an explicit confirmation step, not a silent `build.gradle.kts` edit.
- jsonb → `OBJECT` and arrays → `OBJECT` keep the structured value (not stringified). This unlocks Phase 5 projection logic that may want to reach into nested JSON for identity or field provenance. Stringification would lose that and force re-parse.
- PostGIS `geometry` → `LOCATION` is the right call given Riven's `SchemaType.LOCATION` is Notion-like (lat/lng). Complex polygons will lose fidelity — acceptable v1 trade-off. If the user has rich GIS data, they can remap to `OBJECT` via override.
- bytea → `OBJECT` dump (base64 inside JSON), **not** `FILE_ATTACHMENT`. `FILE_ATTACHMENT` implies Riven-managed file storage lifecycle which does not apply to read-only source data.
- PK auto-detection (not user choice) eliminates a class of confused UX where users pick "identifier" and wonder whether that's the upsert key. Identifier = cross-source match; PK = intra-source upsert — the distinction matters for Phase 5.
- Cursor-index warning is a cost-communication mechanism, not a protection mechanism. Protection = adapter-side guardrails (timeout, fetchSize, batch, concurrency cap). The guardrails ship regardless of user's indexing.
- Auto-detection of `updated_at` name heuristic is a pragmatic UX move — Mac's DB almost certainly uses `updated_at` or `last_modified`. Saves a click without taking away control.
- Spring AI's structured output (Pydantic-style JSON schema) is load-bearing for the "LLM returns clean suggestions" contract. Freeform text would require a parser and invite brittleness.
- The drift flow "flag + confirm, never auto-apply" matches the phase 2 philosophy of never silently mutating security-relevant state. Schema is treated with the same caution.

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets

- `service/ingestion/adapter/IngestionAdapter.kt` + `AdapterCallContext.kt` — Phase 1 contract. `PostgresAdapter` implements this interface.
- `service/ingestion/adapter/SourceTypeAdapterRegistry.kt` + `SourceTypeAdapter` annotation — Phase 1 registry. `PostgresAdapter` registers under `SourceType.CUSTOM_SOURCE`.
- `service/ingestion/adapter/exception/` — Phase 1 `AdapterException` sealed tree. Postgres errors map to `TransientAdapterException` (network, timeout) vs `FatalAdapterException` subtypes (auth, schema drift, unavailable).
- `entity/connector/DataConnectorConnectionEntity.kt` — Phase 2 entity. `PostgresAdapter` consumes these (plus decrypted credentials) to build pools.
- `service/connector/CredentialEncryptionService.kt` — Phase 2. Decrypts credential payload before pool construction.
- `service/connector/SsrfValidatorService.kt` — Phase 2. Reused if any runtime hostname revalidation is needed on pool rebuild (planner decides — not strictly required if revalidation happens at PATCH time before pool eviction).
- `service/connector/ReadOnlyRoleVerifierService.kt` — Phase 2. Called pre-sync by Phase 4; not directly consumed in Phase 3 but introspection assumes RO already verified at create/update.
- `configuration/connector/LogRedactionConfiguration.kt` — Phase 2. JDBC URLs from HikariCP and Postgres driver exceptions will pass through this scrubber automatically.
- `enums/connector/SslMode.kt` — Phase 2. Pool construction reads from decrypted credential.
- `models/entity/payload/EntityAttributePayload.kt` — `EntityAttributePrimitivePayload(value: JsonValue, schemaType: SchemaType)` is the target for every row value.
- `enums/common/validation/SchemaType.kt` — the 17-value enum (TEXT, OBJECT, NUMBER, CHECKBOX, DATE, DATETIME, RATING, PHONE, EMAIL, URL, CURRENCY, PERCENTAGE, SELECT, MULTI_SELECT, FILE_ATTACHMENT, LOCATION, ID). Full type mapping above hits this enum.
- `enums/entity/LifecycleDomain.kt` + `enums/entity/semantics/SemanticGroup.kt` — enum values for mapping-time selectors.
- `entity/entity/EntityTypeEntity.kt` — target of Save: creates rows with `sourceType=CUSTOM_SOURCE`, `readonly=true`.
- `entity/entity/RelationshipDefinitionEntity.kt` — target for FK-inferred relationships.
- `service/enrichment/provider/OpenAiEmbeddingProvider.kt` — precedent for an LLM-provider service + env-var key. Spring AI starter may or may not subsume this pattern — planner evaluates whether to align.
- `service/schema/SchemaService.kt` — validates JSON payloads against schemas. Relevant if planner wants to schema-validate `OBJECT`-mapped jsonb values, but Phase 3 bypasses schema mapping per PG-05, so likely not called.

### Established Patterns

- Service layer: `service/{domain}/`, entities: `entity/{domain}/`, models: `models/{domain}/`, enums: `enums/{domain}/`. `customsource` is the likely domain slug (confirm with Phase 2 naming).
- `@ConfigurationProperties` + env var binding for any new config (e.g. Spring AI key, default batch size, default statement_timeout).
- JPA entity ↔ model via `toModel()`; factories in `service/util/factory/` for all test construction.
- `@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")` on every service method accessing workspace-scoped data.
- `ServiceUtil.findOrThrow { ... }` for single-entity lookups.
- Controller is thin; service enforces auth + business logic.
- Typed exceptions from the `AdapterException` tree (Phase 1) + potential new `MappingException` tree under `exceptions/customsource/` if planner needs mapping-specific failures that don't fit existing trees.

### Integration Points

- **Phase 2 → Phase 3:** `DataConnectorConnectionEntity` (Phase 2) is the input. Entity is fetched, credentials decrypted, pool constructed.
- **Phase 3 → Phase 4:** `PostgresAdapter` registered in `SourceTypeAdapterRegistry` is resolved by `IngestionOrchestrator` at sync time. `WorkspaceConnectionPoolManager.getPool` is called by the `fetchRecords` Temporal activity. `CustomSourceTableMappingEntity.published=true` tells the orchestrator which tables to sync. The per-mapping `SchemaType` + identifier + cursor flags become the sync contract.
- **Phase 3 → Phase 5:** Generated `EntityTypeEntity(sourceType=CUSTOM_SOURCE, readonly=true)` rows + `isIdentifier` flag feed identity resolution + projection.
- **Phase 3 → Phase 6:** Schema hash + `stale` flags on `CustomSourceFieldMappingEntity` feed health evaluation (table-drop → DEGRADED).
- **Phase 3 → Phase 7:** `/schema` GET, mapping Save POST, NL suggestions GET (or embedded in `/schema`) are the API contracts for the Phase 7 mapping UI. Response includes drift indicator + cursor-index warning.

### Gaps (Phase 3 builds these)

- **No existing Postgres JDBC adapter** — `PostgresAdapter` is new.
- **No existing HikariCP-managed per-workspace pool infrastructure** — `WorkspaceConnectionPoolManager` is new, distinct from the app's own JPA pool.
- **No existing Postgres type → `SchemaType` mapper** — `PgTypeMapper` is new.
- **No existing LLM client beyond `OpenAiEmbeddingProvider`** — Spring AI integration is new. Dependency-approval gate required.
- **No existing schema-hash / drift-detection primitive** — new. Applies only to CUSTOM_SOURCE for now.
- **No existing FK-to-`RelationshipDefinitionEntity` inference code path** — new.
- **No existing `CustomSourceFieldMappingEntity` / `CustomSourceTableMappingEntity`** — new tables + SQL DDL under `db/schema/` per the declarative-schema rule.

</code_context>

<deferred>
## Deferred Ideas

- **Composite FK inference** — v2 extension; Phase 3 skips composite FKs with a note.
- **Non-unique identifier handling / dedup policy** — Phase 5 territory; identity resolution decides.
- **LLM input with sample rows or `pg_stats`** — revisit if schema-only suggestions prove too weak. Would require an explicit PII-opt-in per connection.
- **Sync-time drift detection** — Phase 6; Phase 3 is introspection-time only.
- **Explicit LRU eviction for pool map** — only if pool count per instance becomes a memory concern. Not needed v1.
- **Dynamic pool resizing** — `maxPoolSize=2` is fixed per spec.
- **Geometry fidelity beyond `{lat, lng}`** — complex polygons lose detail in `LOCATION`. User can remap to `OBJECT` if needed. Revisit if GIS-heavy users show up.
- **`FILE_ATTACHMENT` for bytea** — would need a lifecycle to extract bytes to Riven's file storage; out of scope.
- **Schema reconciliation for CUSTOM_SOURCE** — PROJECT.md explicitly out-of-scope ("no drift yet"). Our drift detection here is simpler than the full reconciliation planned for user-edited entity types.

</deferred>

---

*Phase: 03-postgres-adapter-schema-mapping*
*Context gathered: 2026-04-13*
