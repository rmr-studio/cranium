# TODOS

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
