# TODOS

## P2 — Connotation Backfill Observability Metric
**What:** Add Micrometer metric `riven.connotation.entities_pending_backfill_count` per workspace per entity type. Counts entities with no `entity_connotation` row (or stale `envelopeVersion`) so operators can monitor lazy-backfill progress.
**Why:** When the connotation envelope ships (Phase A of `2026-04-18-entity-connotation-pipeline.md`), existing entities have no `entity_connotation` row. They populate lazily on next enrichment. Layer 4 predicate queries (e.g. `WHERE axes.SENTIMENT.sentiment < -0.5`) silently exclude pre-deploy entities until they re-enrich. Without this metric, query coverage gaps are invisible.
**Pros:** Operator-facing visibility into lazy-backfill progress; alertable; dashboard signal for Layer 4 query coverage gaps.
**Cons:** Adds Micrometer instrumentation surface (~30 min CC); minor.
**Context:** Phase A of the entity connotation pipeline introduces a sibling system-managed `entity_connotation` table that holds the per-entity semantic envelope (SENTIMENT + RELATIONAL + STRUCTURAL axes). Layer 4 Milestone C reads `axes.SENTIMENT.sentiment` via BTREE-indexed JSONB path expressions. Backfill is lazy by design — no proactive enqueue at Phase A deploy. This metric tells operators when backfill has plateaued so they can confirm Layer 4 query coverage is stable.
**Depends on:** Phase A of entity-connotation-pipeline plan merged.

## P2 — Frontend: Source Attribution UI
**What:** Integration-sourced notes should show a source badge (e.g., HubSpot icon), lock icon for readonly, and disabled edit/delete buttons.
**Why:** Users currently can't distinguish integration notes from user-created notes. Without visual cues, they'll try to edit readonly notes and hit a 403.
**Pros:** Clear UX for mixed note sources, prevents confusion.
**Cons:** Requires integration icon assets and conditional rendering in note components.
**Context:** NoteEntity now has `sourceType` (USER/INTEGRATION), `sourceIntegrationId`, and `readonly` fields. API returns these in the Note model. Frontend needs to consume them.
**Depends on:** Note embedding pipeline shipping (backend).

## P2 — Frontend: Multi-Entity Note Context Display
**What:** Workspace notes list should show all attached entities for entity-spanning notes, not just one.
**Why:** With the join table migration, notes can be attached to multiple entities. The current WorkspaceNote enrichment returns entity context but the frontend breadcrumb only shows one entity.
**Pros:** Full context for notes attached to contact + deal + ticket.
**Cons:** UI needs to handle variable-length entity lists gracefully (2-5 entities per note).
**Context:** Note model now returns `entityIds: List<UUID>` instead of `entityId: UUID`. WorkspaceNote enrichment updated for multi-entity context.
**Depends on:** Entity-spanning notes migration shipping (backend).

## P3 — Performance: EXPLAIN ANALYZE Join Table Queries
**What:** Run EXPLAIN ANALYZE on entity-scoped note queries after join table migration to verify index usage and query cost.
**Why:** All entity-scoped queries now JOIN through note_entity_attachments instead of direct WHERE entity_id. Need to confirm the idx_note_attachments_entity index is used.
**Pros:** Catches performance regressions early.
**Cons:** One-time task, low effort.
**Context:** Queries affected: findByEntityIdAndWorkspaceId, searchByEntityIdAndWorkspaceId, findEntityContext. Index on note_entity_attachments(entity_id) should make this fast.
**Depends on:** Entity-spanning notes migration deployed to a database with realistic data volume.

## P2 — Unify IntegrationSyncWorkflow via IngestionOrchestrator
**What:** Migrate IntegrationSyncActivitiesImpl (801 lines) to delegate record-processing to IngestionOrchestrator through NangoAdapter. Eliminates split path between custom-source and Nango pipelines.
**Why:** Two-layer data plan ships NangoAdapter as thin wrapper but never wires it. Leaves parallel scaffolding that drifts over time.
**Pros:** Single record-processing pipeline; one place to fix bugs in mapping/upsert/resolution/projection.
**Cons:** Touches hot path used by all existing integrations; needs careful staged rollout (feature flag + shadow mode).
**Context:** See `.planning/` two-layer data model. IngestionOrchestrator introduced for CustomSourceSyncWorkflow but Nango pipeline stays as-is. NangoAdapter created in plan specifically to enable this future migration.
**Depends on:** Custom source Postgres adapter shipped and validated with Mac.

## P2 — Schema Drift Detection for Custom Sources
**What:** Detect and surface user DB schema changes (added/removed/renamed columns) between syncs.
**Why:** Two-layer plan Open Question #2 defers to "next introspection surfaces unmapped column, user decides." No proactive detection of removed or renamed columns.
**Pros:** User sees schema changes before they break sync; prevents silent data loss when a source column disappears.
**Cons:** Needs periodic introspection separate from record sync; UX for "3 columns removed, remap?" is non-trivial.
**Context:** Existing schema-reconciliation pattern (project_schema_reconciliation.md) applies to workspace entity types. Custom source drift is similar but origin is external DB, not core model changes. CustomSourceSchemaInferenceService already introspects; extend to diff against stored SourceSchema.
**Depends on:** PostgresAdapter + SchemaInferenceService shipped.

## P1 — OnboardingFirstInsightService (DTC trust moment)
**What:** Post-Shopify-connect service runs one canned cross-domain query and renders an "insight card" on the onboarding step. Library of 5 canned queries: shipping-delay-support-overlap, repeat-customer-discount-dependency, high-return-SKU, ad-creative-to-cohort-overlap, packaging-complaint-cluster. Needs LOADING / EMPTY (<3 results fallback) / ERROR / PARTIAL / SUCCESS states on the card.
**Why:** Converts "data pipe" feeling into "AI analyst" feeling in 60 seconds. Positioning-critical — the first 60s of post-connect UX is where "proactive AI signals" gets believed or doesn't.
**Pros:** Trust moment. Delivers on the D2C repositioning story at first contact.
**Cons:** Shopify first-sync is async (minutes). Needs a state-delivery mechanism: WebSocket push (existing WorkspaceEvent pattern) or polling endpoint. Without it the card can stall at LOADING.
**Context:** Deferred from the 2026-04-22 DTC ecom repositioning plan. Canned-query library lives as Kotlin code (5 query builders in OnboardingFirstInsightService), not DB or prompt templates — keeps it testable and versionable. Requires `riven.onboarding.first_insight.hit_rate` metric to track whether canned queries match real brand data.
**Depends on:** DTC core-model expansion (23 models) shipped + at least one real Shopify adapter.

## P1 — Signal Layer + SignalDerivationWorkflow
**What:** Add 4 signal models — CohortDriftEvent, CreativeFatigueEvent, ChurnRiskSignal, DiscountDependentSignal — as first-class entities in a new signal layer. Scaffolding: Temporal workflow `SignalDerivationWorkflow` with 1/day schedule per workspace, activity stubs per signal type, feature flag `riven.signals.derivation.enabled` (default false), metrics `riven.signal.derivation.{duration,success_rate}`. Real derivation logic follows per-signal in subsequent PRs.
**Why:** This is the product's "proactive AI behavioural signals" positioning made concrete as data. Without signals, DTC repositioning ships a shape without substance.
**Pros:** First-class queryable entities that agents can reason over. Foundation for the "I traced it to 87 comments + 12 tickets" platonic-ideal insight.
**Cons:** Signal models straddle user-facing (consumed by workspace) and system-produced (written from Temporal). Audit context pitfall `jpa-auditing-temporal` applies — Temporal workers have no SecurityContext, system user UUID must be stamped explicitly. Schedule registration path (boot scan, workspace-creation hook, admin endpoint) needs decision. Jitter needed or all workspaces fire at 00:00 UTC simultaneously.
**Context:** Deferred from the 2026-04-22 DTC ecom repositioning plan. Needs its own plan with scheduler decisions + audit-context design + derivation math per signal.
**Depends on:** DTC core-model expansion shipped.

## P1 — SocialMention PII / GDPR policy
**What:** Policy document covering PII handling for SocialMention, SocialComment, and ProductReview models — ingesting third-party user content (handles, emails, review text) from people who never signed consent with the brand. Includes GDPR/CCPA delete-request surface and identity-resolution-bridging-SocialMention-to-Customer-via-handle (re-identification-adjacent).
**Why:** Gates Meta/Instagram/TikTok adapter ship. Ingesting non-consented PII without a policy is a company-level liability.
**Pros:** Protects the company. Gives adapter engineers clear rules.
**Cons:** Legal review required. May land at "don't bridge SocialMention to Customer without workspace-level opt-in," which would gate the relationship declaration itself.
**Context:** The SocialMention model and its relationship to Customer (via identity resolution) were shipped with the core-model expansion. The adapter that populates them is deferred. Policy must land before the adapter ships, not before the model.
**Depends on:** Nothing in code — a doc/policy task.

## P3 — N+1 in TemplateMaterializationService.installProjectionRules
**What:** `installProjectionRules` at `TemplateMaterializationService.kt:455-467` calls `projectionRuleRepository.existsByWorkspaceAndSourceAndTarget(...)` per (source entity type × core model match) pair. Worst case with 23 catalog types × ~20 projection targets = ~460 DB round-trips on workspace manifest install. Batch the existence check (one query over the full source set).
**Why:** Pre-existing N+1, worsened by DTC catalog expansion (9→23 models).
**Pros:** One-time workspace-install path that currently takes a few hundred round-trips reduces to single-digit queries.
**Cons:** Not hot-path, runs once per workspace. Low urgency.
**Context:** Identified during plan-eng-review for the DTC ecom repositioning. The loop lives at lines 455-467 in `core/src/main/kotlin/riven/core/service/integration/materialization/TemplateMaterializationService.kt`.
**Depends on:** Nothing — independent refactor.

## P2 — CollectionCohortMirror + CohortModel (future synthesis layer)
**What:** Cohorts conceptualized as a "synthesis layer" on top of Customer — not a first-class model. Explore whether Shopify Collections (product groupings) should auto-derive customer-cohort views via post-purchase analysis, or whether cohorts belong purely as query-derived entities built by agents.
**Why:** Original plan's "Collection → Cohort mirror" mapped products to customer groups, which is semantically muddy. But the underlying insight — that brand-curated product sets can reveal customer segments — is real.
**Pros:** Gets to the "VIP tier 2 customers" insight without requiring manual cohort creation.
**Cons:** Needs a design doc. "Synthesis layer" is not a shipped concept — needs definition.
**Context:** Deferred from the 2026-04-22 DTC ecom repositioning plan. User's guidance: "Cohorts will be a part of the synthesis layer of customers, not a model by itself."
**Depends on:** Synthesis layer design.

## P3 — ReturnReasonClassifier (async LLM classifier)
**What:** LLM-backed classifier for Return entity reason text → fixed taxonomy (DAMAGE, WRONG_SIZE, QUALITY, EXPECTATION_MISMATCH, SHIPPING_DELAY, CHANGED_MIND, OTHER). Run as post-materialization Temporal activity, not inline with ingestion. Cache by content hash. Fall back to OTHER on LLM failure.
**Why:** Unlocks cohort-level return-reason analysis. Native Shopify return reasons are free-text and low-fidelity.
**Pros:** Structured return reasons queryable across cohorts + time.
**Cons:** Latency/availability coupling if run inline. Must be async. Needs Postgres content-hash cache table.
**Context:** Deferred from the 2026-04-22 DTC ecom repositioning plan (was E4 in original scope). Classifier prompt version should be part of cache key.
**Depends on:** Return model shipped (this PR), Temporal activity pattern, cache infrastructure.

## P2 — Reinstate DevSeed for DTC catalog
**What:** Rebuild the dev-only seed pipeline (DevSeedController + DevSeedService + DevSeedDataGenerator + DevSeedConfigurationProperties + DevSeedResponse) keyed on the DTC model set. Seed realistic mock entities for all 23 DTC entity types and their relationships. Toggle via `riven.dev.seed.enabled=true`.
**Why:** DevSeed was ripped during the DTC repositioning because the old implementation hardcoded B2C_SAAS model keys (`"subscription"`, `"feature-usage-event"`) in `DevSeedDataGenerator.kt:120-125`. Rebuilding is faster than migrating. Dev UX suffers without realistic mock data — running the app locally against an empty catalog makes frontend + flow work painful.
**Pros:** Fast local iteration on the frontend (realistic data shape + volume), integration-test seed path, demoable dev environment.
**Cons:** 500-600 lines of new Kotlin across the service/generator. Per-model generator functions needed for the 23 DTC types (campaigns, ad creatives, shipments, returns, reviews, etc.). Test coverage.
**Context:** Deletion happened in the 2026-04-22 DTC repositioning PR. No backwards-compat concern — there are no external callers besides the (also-deleted) client `DevApi.ts`. New implementation should be keyed on the single DTC manifest (no business-type branching) and can drop the `templateKey` path variable in the reinstall endpoint since there's only one template. Add per-domain generator helpers (marketing, social, fulfillment, commerce) matching the directory layout.
**Depends on:** DTC catalog expansion shipped (this PR).

## P3 — Observability for DTC catalog expansion
**What:** Metrics and alerts for the DTC catalog surface: `riven.onboarding.first_insight.hit_rate` (when first-insight ships), `riven.signal.derivation.{duration,success_rate}` per signal type per workspace, `riven.classifier.return_reason.{cache_hit_rate,llm_error_rate}`, alert on inert signal worker staying inert >30 days.
**Why:** Positioning-critical for "proactive AI signals" claim. Without metrics, there's no accountability for whether the canned queries actually match real brand data.
**Pros:** Release accountability + product north-star signal visibility.
**Cons:** Needs meters registered per Micrometer pattern. Each signal ships with its metric surface.
**Context:** Deferred from 2026-04-22 DTC ecom plan. Ships incrementally with each feature (first-insight, signal derivation, classifier).
**Depends on:** The features themselves.
