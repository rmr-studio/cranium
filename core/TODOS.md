# TODOs

## Identity Resolution — Deferred Work

### TODO-IR-001: Workspace-Level Identity Resolution Toggle

**What:** Add a workspace setting to enable/disable identity resolution matching.
**Why:** Some workspaces may not want matching noise (e.g., single-integration workspaces with no cross-type data).
**Pros:** User control over feature; reduces notification noise for irrelevant workspaces.
**Cons:** Requires workspace settings infrastructure (new column or table).
**Context:** Default is enabled for all workspaces. Toggle would skip the match trigger event listener for disabled workspaces. Could be a column on `workspace` table or a new `workspace_settings` table.
**Effort:** S
**Priority:** P2
**Depends on:** Core identity resolution matching engine

### TODO-IR-002: Configurable Match Signal Weights Per Workspace

**What:** Let workspace admins tune how much each signal type (email, phone, name, company) contributes to match confidence.
**Why:** Different industries weight signals differently — B2B cares about company+name, B2C cares about email+phone.
**Pros:** Higher match quality per workspace; reduces false positives/negatives.
**Cons:** Requires configuration UI; more complex scoring path.
**Context:** Initial implementation uses hardcoded weights (e.g., email=0.9, phone=0.85, name=0.5, company=0.3). This TODO adds a `match_rules` table with per-workspace signal weight overrides. Scoring service reads workspace config, falls back to defaults.
**Effort:** M
**Priority:** P2
**Depends on:** Core matching engine (TODO-IR baseline)

### TODO-IR-003: Transitive Match Discovery

**What:** When entity B joins a cluster containing A, re-scan cluster members' signals against B's other potential matches.
**Why:** If A↔B confirmed and B↔C has signals, C likely matches A too. Without this, users must manually discover A↔C.
**Pros:** Exponential relationship discovery from linear user effort; compound value of confirmations.
**Cons:** Risk of noisy cascading suggestions if thresholds are too low; needs circuit breaker.
**Context:** Post-confirmation hook in MatchConfirmationService triggers re-scan of cluster members. Identity cluster architecture already supports this — just needs a "re-scan cluster" step after confirmation. Add a max_cluster_size guard (e.g., 50) to prevent runaway cascades.
**Effort:** M
**Priority:** P2
**Depends on:** Core matching engine + identity clusters

### TODO-IR-004: Same-Type Duplicate Detection

**What:** Extend matching engine to detect potential duplicates within the SAME entity type.
**Why:** Data quality issue — integrations can sync duplicate records, users can manually create duplicates.
**Pros:** Addresses intra-type data quality, not just cross-type linking.
**Cons:** Different UX implications — same-type duplicates may need merge rather than just link. Separate review queue may be needed.
**Context:** Same matching engine, same signals. Remove the "different type" filter from candidate discovery query. Present in a separate "Potential Duplicates" section of the review UI to distinguish from cross-type matches.
**Effort:** S (engine changes) + M (UX differentiation)
**Priority:** P3
**Depends on:** Core matching engine

### TODO-IR-005: Auto-Confirm Matches Above Learned Threshold

**What:** Automatically confirm matches when confidence exceeds a workspace-learned threshold.
**Why:** Reduces manual review burden for high-confidence matches after the system has earned trust.
**Pros:** Dramatic reduction in manual review work; faster relationship building at scale.
**Cons:** Risk of false positive auto-links; requires statistical confidence in the threshold; needs user opt-in.
**Context:** Track confirmation rate by score bracket per workspace. When workspace consistently confirms >95% of matches above score X, offer to auto-confirm future matches above X. Requires: (1) historical confirmation rate tracking, (2) statistical significance check, (3) workspace opt-in setting, (4) auto-confirmed suggestions marked distinctly for audit.
**Effort:** L
**Priority:** P3
**Depends on:** Configurable weights (TODO-IR-002) + sufficient match volume

### TODO-IR-006: Batch Confirm Matches Above Threshold

**What:** "Confirm all matches above X% confidence" batch action endpoint.
**Why:** Power user feature for workspaces with many pending matches after initial integration connection.
**Pros:** Dramatically speeds up initial match review; good onboarding experience after connecting integrations.
**Cons:** Risk of bulk false positive confirmations; needs clear threshold UI.
**Context:** Backend batch endpoint: `POST /api/v1/identity/{workspaceId}/suggestions/batch-confirm` with `minScore` parameter. Confirms all PENDING suggestions above threshold, creates relationships, updates clusters. Returns count of confirmed matches.
**Effort:** S
**Priority:** P2
**Depends on:** Core matching engine + confirmation flow

### TODO-IR-007: Identity Resolution Dashboard

**What:** Workspace-level dashboard showing match funnel and trends over time.
**Why:** Operational visibility into how well identity resolution is working; shows ROI of the feature.
**Pros:** Helps admins tune thresholds; demonstrates feature value; identifies data quality issues.
**Cons:** Requires aggregate query endpoints + frontend charting.
**Context:** Backend endpoints for: match counts by status, score distribution histogram, time series of matches created/confirmed/rejected, top entity type pairs by match count. Frontend renders as funnel chart + trend lines.
**Effort:** M
**Priority:** P3
**Depends on:** Core matching engine + sufficient match data

### TODO-IR-008: Cross-Type Match Score Discounting

**What:** Introduce a scoring penalty or separate signal category for cross-type attribute matches (e.g., EMAIL value matched against a NAME attribute).
**Why:** Cross-type matching is valuable for identity resolution (e.g., `john.smith@gmail.com` ↔ `John Smith`), but without discounting, high-frequency tokens like "John" generate a flood of low-value suggestions across every `john@*` email in the workspace. Same-type matches should carry more weight than cross-type ones.
**Pros:** Reduces false positive suggestions; preserves cross-type matching capability; improves signal-to-noise ratio.
**Cons:** Requires design work on how to represent cross-type signals in the scoring model; may need tuning per signal type pair.
**Context:** Current implementation in `IdentityMatchCandidateService.runCandidateQuery()` searches all IDENTIFIER attributes regardless of type and stamps matches with the trigger's signal type. Options include: (1) cross-type weight multiplier (e.g., 0.5x), (2) dedicated `CROSS_TYPE` signal with its own default weight, (3) higher minimum similarity threshold for cross-type pairs. These are not mutually exclusive. Related to TODO-IR-002 (per-workspace weight configuration).
**Effort:** M
**Priority:** P2
**Depends on:** Core matching engine

---

## STALE connection detection

**What:** Implement STALE detection — connections with no recent sync activity should transition to STALE status.
**Why:** ConnectionStatus has a STALE state but nothing transitions to it. Connections that stop syncing (e.g., Nango sync disabled, provider API revoked) would remain in HEALTHY/DEGRADED indefinitely.
**Pros:** Users see accurate connection health; STALE connections surface in UI for investigation.
**Cons:** Needs a scheduled job or threshold check — adds infrastructure beyond the current event-dcranium model.
**Context:** STALE is already in the ConnectionStatus enum with valid transitions (STALE → SYNCING, STALE → DISCONNECTING, STALE → FAILED). Implementation options: (1) scheduled job that scans connections by lastSyncedAt, (2) check during health evaluation if syncState.updatedAt is older than threshold. The threshold value (e.g., 7 days) should be configurable.
**Depends on / blocked by:** Phase 4 health service must be complete first.

## Circuit breaker on consecutive sync failures

**What:** Add a guard in `NangoWebhookService.handleSyncEvent()` that checks `consecutiveFailureCount` or `ConnectionStatus == DEGRADED` before dispatching a Temporal workflow. Skip dispatch if threshold exceeded.
**Why:** Without this, Nango keeps sending sync webhooks and the system keeps spawning Temporal workflows that fail, consuming Temporal capacity and generating noisy logs.
**Pros:** Prevents unbounded failed workflow execution; surfaces connection issues more clearly.
**Cons:** Could mask transient failures that would self-resolve; needs a manual "retry" mechanism.
**Context:** `IntegrationSyncStateEntity.consecutiveFailureCount` and `IntegrationHealthService` DEGRADED threshold (>=3) already exist. The guard checks this state in `handleSyncEvent()` before `WorkflowClient.start()`. A workspace admin "Force Retry" action can reset the counter.
**Effort:** S
**Priority:** P1 (implement before production)
**Depends on:** Integration sync pipeline bug fixes (this branch)

## SYNC-01 through SYNC-07 traceability cleanup

**What:** Update SYNC-01 through SYNC-07 from "Pending" to "Complete" in the REQUIREMENTS.md traceability table.
**Why:** These requirements were implemented in Phase 3 but the traceability table wasn't updated. The table currently shows them as Pending, which is inaccurate.
**Pros:** Accurate project tracking; prevents confusion about what's done.
**Cons:** None — pure housekeeping.
**Context:** Phase 3 plans 03-01 and 03-02 implemented all SYNC requirements. The checkbox section correctly marks them, but the traceability table at the bottom of REQUIREMENTS.md still shows "Pending."
**Depends on / blocked by:** Nothing — can be done anytime.

## Backend

### Tighten RLS write policies on system-managed tables to service_role

**What:** Sweep all `db/schema/05_rls/*.sql` files where the table is system-managed (write-gated at app layer) and split the existing `FOR ALL TO authenticated` policies into a SELECT-only policy for `authenticated` plus a separate write policy scoped to `service_role`. Confirmed scope: `entity_connotation`, `entity_embeddings`, and any other tables whose own SQL header notes "system-write-only" or equivalent.

**Why:** Defense-in-depth. Today, write gating relies entirely on the application layer. If a workspace member ever obtained a direct DB connection as the `authenticated` role, they could mutate envelope/embedding rows. RLS should enforce the same intent the app already enforces.

**Pros:** Closes a defense-in-depth gap consistently across all system-managed tables. Aligns RLS with the file-level comments that already describe these tables as "system code paths only."

**Cons:** Requires confirming the backend Postgres role used by the app is `service_role` (or whatever role we choose for system writes). May involve deployment / connection-string work. Done piecemeal it diverges; must be done as one sweep.

**Context:** Raised by CodeRabbit on PR #195 against `entity_connotation_rls.sql`. Audited 05_rls/ at the time: every file uses `FOR ALL TO authenticated` and `service_role` appears nowhere. Decided to keep the existing pattern on PR #195 (added clarifying comment instead) and capture the broader change as a TODO so it can be done consistently across all system-managed tables in one pass.

**Effort:** M (human ~2-3 days for sweep + role/deployment work) / S (CC ~30 min for SQL changes; the role-switching piece needs a human)
**Priority:** P2
**Depends on:** Confirmation of backend Postgres role for system writes

---

### Migrate EntityQueryService to cursor-based pagination

**What:** Refactor EntityQueryService from LIMIT/OFFSET to cursor-based seek pagination using the shared `CursorPagination` utility.

**Why:** Offset pagination causes duplicate/skip bugs with infinite scroll (items shift when data changes between page loads). All data lists are infinite scroll.

**Context:** EntityQueryService currently uses `QueryPagination(limit, offset, orderBy)` with LIMIT/OFFSET SQL via `EntityQueryAssembler`. The `CursorPagination` utility (created in the workspace notes PR) provides `encodeCursor()`/`decodeCursor()` and `CursorPage<T>` response wrapper. Migration involves: changing `QueryPagination` model to accept cursor instead of offset, updating `EntityQueryAssembler` to generate `WHERE (sort_col < :cursorVal OR (sort_col = :cursorVal AND id < :cursorId))` instead of `OFFSET`, and updating frontend `useEntityQuery` hooks. The main complexity is that EntityQueryService supports user-defined `orderBy` columns — the cursor key must match the sort key, which means encoding the sort value (from `entity_attributes.value`) in the cursor.

**Depends on:** Workspace notes PR (CursorPagination utility must exist first).

---

## Frontend

### Handle null entityDisplayName in workspace notes list

**What:** Show a fallback (e.g., "Unnamed" or entity ID) when `WorkspaceNote.entityDisplayName` is null.

**Why:** The backend sub-SELECT for display name returns null if the entity's identifier attribute was deleted or never set. Rare edge case but prevents blank cells in the notes DataTable entity column.

**Context:** `WorkspaceNote.entityDisplayName` is `String?` (nullable). The frontend entity badge/column in the notes list and sidebar panel should show a sensible fallback. This also applies to the breadcrumb in the full-page editor route.

**Depends on:** Workspace notes backend PR delivering the `WorkspaceNote` model.

---

### Migrate to Redis-backed Bucket4j when scaling to multiple instances

**What:** Replace the Caffeine in-memory cache with a Redis-backed Bucket4j `ProxyManager` for distributed rate limiting.

**Why:** The current single-instance Caffeine cache works for one app instance, but scaling to multiple instances means each instance has its own independent rate limit state. A user could get 200 rpm per instance instead of 200 rpm total.

**Context:** Bucket4j supports Redis via `bucket4j-redis` (Lettuce or Jedis). The filter logic stays identical — only the bucket storage backend changes. Swap `Cache<String, Bucket>` for `ProxyManager<String>` in `RateLimitFilterConfiguration`.

**Depends on:** Multi-instance deployment (not yet planned).

---

### Configure Cloudflare rate-limiting rules for production

**What:** Set up two Cloudflare WAF rate-limiting rules: 60 req/10s per IP on `/api/*`, and 10 req/10s per IP on `/api/v1/webhooks/*`.

**Why:** Edge-level IP rate limiting is Layer 1 defense — stops abuse before it reaches the application. The stricter webhook rule accounts for Nango's defined cadence.

**Context:** Configured in Cloudflare dashboard, not code. Check which Cloudflare plan Cranium is on — free plan has limited rate-limiting rules.

**Depends on:** Production deployment with Cloudflare DNS configured.

---

## Strategic

### Entity Ingestion Pipeline — Remaining Work

**Priority:** P0 (blocks all integration testing)
**Effort:** S (human: ~2 days) / S (CC: ~2 hours)
**Status:** Pipeline implemented, 4 bugs fixed (syncModels mapping, transform parsing,
key mapping persistence, N+1 relationship definitions). Ready for E2E testing.

**What remains:**

- E2E testing with real HubSpot dev portal
- Capture real Nango payload fixtures as test data
- Manifest schema evolution tests (attribute add/remove/modify)
- Additional manifests (Stripe, Intercom, PostHog) after HubSpot E2E proven

**Context:** See eng review plan at `.claude/plans/sleepy-doodling-spark.md` and feature design at `docs/system-design/feature-design/1. Planning/Entity Ingestion Pipeline.md`.

---

### Backfill Projection for Unmatched Integration Entities

**Priority:** P2
**Effort:** M (human: ~1 week) / S (CC: ~30 min)
**Depends on:** Entity Ingestion Pipeline (P1 above)

**What:** When a user installs a new core model that matches existing (previously unmatched) integration entities, retroactively project those entities through the ingestion pipeline.

**Why:** If a workspace connects Zendesk before installing the Support Ticket core model, Zendesk tickets are created as integration entities with no projection. When the user later installs Support Ticket, those existing integration entities should be retroactively projected into the core type.

**Pros:** Completes the "opt-in" model for core entity types — users can connect integrations first, install models later, and everything connects automatically. Prevents data being silently "stuck" as hidden integration entities.

**Cons:** Needs a one-time Temporal workflow per core model installation. For large volumes (50k+ integration entities), this could be resource-intensive and needs pagination.

**Context:** Identified by outside voice during eng review 2026-03-27. The ingestion pipeline's classify → route → map → resolve steps apply equally to backfill — just triggered by core model installation rather than sync events.

---

### Custom Integration Builder - Direct Postgres, CSV, and Webhook Ingestion

**Priority:** P2
**Effort:** XL (human: ~6 weeks) / L (CC: ~4 hours)
**Depends on:** Entity Ingestion Pipeline (the 4-step classify → route → map → resolve pipeline must work first)

Per the SaaS Decline thesis, data sources will diversify beyond SaaS integrations. Users need to:

- Connect internal Postgres tables directly
- Import CSVs with schema inference
- Receive webhooks from custom internal systems
- Poll internal APIs

All of these produce entities classified by LifecycleDomain. Domain-based projection routing
handles them automatically — any SUPPORT-domain entity from any source routes to the SupportTicket
core model without additional configuration. See `docs/architecture-suggestions.md` for the
SUPPORT → SupportTicket routing decision and any related cross-domain dependency notes.

**Pros:** Directly addresses the SaaS Decline thesis. Domain-based routing makes this architecturally
clean. Positions Cranium as "operational data layer" not "SaaS connector."

**Cons:** Large scope. Requires UI for connection setup, schema inference, field mapping.
Each ingestion type has unique edge cases (Postgres connection pooling, CSV encoding, webhook auth).

**Context:** See the "SaaS Decline & Strategic Positioning" document in the philosophy vault
for the strategic thesis. The expanded ingestion model is defined there. Core model architecture
provides the foundation — domain-based routing means new ingestion types work without touching
core model code.

**Documentation tasks:**

- [ ] After each structural change, append an entry to `docs/architecture-changelog.md` (owner: implementer)
- [ ] When new inter-domain dependencies or responsibility changes are introduced, append suggestions to `docs/architecture-suggestions.md` with affected domains and link to the change PR (owner: implementer)

---

## Schema Reconciliation — Follow-Up Work

### TODO-SR-001: Relationship Reconciliation

**What:** When core models add or remove relationship definitions, propagate changes to existing workspaces.
**Why:** Same drift problem as attribute schema changes but for RelationshipDefinitionEntity. Currently, only attribute schemas are reconciled.
**Pros:** Completes the reconciliation story. Without this, workspaces miss new cross-type relationships added in future core model updates.
**Cons:** Different mechanics than attribute reconciliation (separate entity, separate materialization path). Needs its own diff/apply logic.
**Context:** Attribute reconciliation (SchemaReconciliationService) proves the pattern. Relationship reconciliation uses the same hash-and-compare approach but operates on RelationshipDefinitionEntity rows rather than JSONB schema fields. Flagged during CEO review temporal interrogation (2026-04-08).
**Effort:** M (human ~1 week) / S (CC ~30 min)
**Priority:** P2
**Depends on:** Schema reconciliation (attribute-level) must be implemented first

### TODO-SR-002: Schema Health Admin UI

**What:** Frontend page in workspace settings showing per-entity-type reconciliation status, pending changes, and "Apply Updates" button for breaking changes.
**Why:** Without a UI, breaking changes are invisible unless an admin proactively calls the schema-health API endpoint.
**Pros:** Workspace admins can see and act on pending schema updates. Surfaces the value of the reconciliation system.
**Cons:** Requires a new page in workspace settings + TanStack Query hooks for the two new endpoints.
**Context:** The backend API endpoints (GET /api/v1/workspace/{workspaceId}/schema-health, POST /api/v1/workspace/{workspaceId}/schema-reconcile) are built as part of schema reconciliation. This TODO covers the frontend only. CEO plan: ~/.gstack/projects/rmr-studio-cranium/ceo-plans/2026-04-08-schema-reconciliation.md
**Effort:** S (human ~2 days) / S (CC ~15 min)
**Priority:** P3
**Depends on:** Schema reconciliation API endpoints

### TODO-SR-003: Admin Notification for Pending Breaking Changes

**What:** When reconciliation detects breaking changes, send a notification to workspace admins via the notification system.
**Why:** Breaking changes set pendingSchemaUpdate=true on the entity type but the admin has no way to learn about this without checking the health endpoint.
**Pros:** Proactive visibility. Admins act on pending updates faster.
**Cons:** Requires notification system to be implemented first.
**Context:** The notification system is being built in a separate worktree. Once that ships, this TODO wires reconciliation events to workspace admin notifications. Could use an in-app notification or email depending on notification system capabilities.
**Effort:** S (human ~1 day) / S (CC ~10 min)
**Priority:** P2
**Depends on:** Schema reconciliation + notification system
