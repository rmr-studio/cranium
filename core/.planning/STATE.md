---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: planning
stopped_at: Completed 05-02-PLAN.md
last_updated: "2026-03-31T09:46:28.230Z"
last_activity: 2026-03-28 — Roadmap created
progress:
  total_phases: 5
  completed_phases: 5
  total_plans: 10
  completed_plans: 10
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-28)

**Core value:** Match the entities that a human reviewer would recognize as the same real-world identity, even when the character-level representation differs significantly.
**Current focus:** Phase 1 - Signal-Type-Aware Normalization + Candidate Query Fixes

## Current Position

Phase: 1 of 5 (Signal-Type-Aware Normalization + Candidate Query Fixes)
Plan: 0 of TBD in current phase
Status: Ready to plan
Last activity: 2026-03-28 — Roadmap created

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**
- Total plans completed: 0
- Average duration: -
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**
- Last 5 plans: -
- Trend: -

*Updated after each plan completion*
| Phase 01 P01 | 3 | 1 tasks | 2 files |
| Phase 01 P02 | 15 | 2 tasks | 4 files |
| Phase 02 P01 | 67 | 2 tasks | 12 files |
| Phase 02 P02 | 2 | 1 tasks | 2 files |
| Phase 03 P01 | 3 | 2 tasks | 5 files |
| Phase 03 P02 | 12 | 2 tasks | 3 files |
| Phase 04 P01 | 1 | 1 tasks | 2 files |
| Phase 04 P02 | 11 | 2 tasks | 3 files |
| Phase 05-01 P01 | 10 | 2 tasks | 4 files |
| Phase 05 P02 | 5 | 2 tasks | 1 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Project: Private methods over strategy interface — 4 strategies don't justify a plugin system
- Project: Exact-digits query for PHONE — pg_trgm blocking can't match differently-formatted phones
- Project: Static FREE_EMAIL_DOMAINS skip-set — ~30 hardcoded domains, no query overhead
- Project: signal_type tag on semantic metadata — makes signal type first-class workspace config
- Project: matchSource field on CandidateMatch — default TRIGRAM for backward compat
- [Phase 01]: Hardcoded NAME_STOPWORDS and COMPANY_STOPWORDS as companion object Set constants in IdentityNormalizationService — no dynamic config needed
- [Phase 01]: COMPANY strips stopwords from end only; NAME strips from both start and end (position-aware)
- [Phase 01]: Country code strip targets only 11+ digit numbers starting with 1 (North America) — non-US numbers preserved
- [Phase 01]: Kotlin union for phone exact-digits (not SQL UNION) — simpler, dedup handled by mergeCandidates
- [Phase 01]: CANDIDATE_LIMIT bumped to 100 (was 50) to compensate for DISTINCT ON removal
- [Phase 01]: mergeCandidates groupBy key changed to (entityId, attributeId) to preserve multi-attribute entities
- [Phase 02-01]: fromColumnValue() chosen over valueOf() for CUSTOM->CUSTOM_IDENTIFIER mapping
- [Phase 02-01]: CUSTOM_IDENTIFIER as fallback when signal_type column is null (pre-existing rows)
- [Phase 02-01]: matchSource defaults to TRIGRAM for backward Temporal serialization compatibility
- [Phase 02]: Confidence gate checks DEFAULT_WEIGHTS base weight, not discounted MatchSignal.weight — prevents cross-type double-penalty
- [Phase 02]: Null candidateSignalType treated as same-type (no discount) for backward Temporal serialization compatibility
- [Phase 03]: NicknameExpander as Kotlin object (not Spring bean) — pure stateless lookup testable without Spring context
- [Phase 03]: TokenSimilarity uses overlap coefficient not Jaccard — returns 1.0 for subset containment (John vs John Smith)
- [Phase 03]: signal_type from semantic metadata overrides SchemaType for trigger signal routing — SchemaType has no NAME/COMPANY variants
- [Phase 03]: TEST-07 uses unusual hyphenation to force exact-digits path — trigram similarity below 0.3 threshold
- [Phase 04]: EmailMatcher as Kotlin object (not Spring bean) — pure stateless utility, same pattern as NicknameExpander
- [Phase 04]: FREE_EMAIL_DOMAINS as private Set with 34 entries — hardcoded constant, no query overhead
- [Phase 04]: split_part() over substring() POSIX regex for domain extraction in native SQL — simpler and reliable
- [Phase 04]: EMAIL_DOMAIN tiebreaker only wins over TRIGRAM on equal score — higher trigram score still wins
- [Phase 04]: Free-domain guard in findCandidates loop, not inside findEmailDomainCandidates — private method stays single-purpose
- [Phase 05-01]: dmetaphone computed via JDBC scalar queries per token — no Kotlin phonetic library, same algorithm both sides
- [Phase 05-01]: when(signalType) as expression enforces exhaustiveness at compile time — new MatchSignalType requires when branch
- [Phase 05-01]: Early return in findPhoneticCandidates when phoneticCodes is empty — prevents Hibernate empty-collection SQL error
- [Phase 05-02]: TEST-10 and TEST-12 as separate test methods — same trigger, different assertion focus (candidate matchSource vs signal set + composite score)
- [Phase 05-02]: Phone 9995551234 chosen for phonetic test entities — distinct from all other test fixtures to prevent EXACT_NORMALIZED cross-test interference

### Pending Todos

None yet.

### Blockers/Concerns

- Phase 5 introduces fuzzystrmatch PostgreSQL extension — requires discussion before adding (per CLAUDE.md rules). Already pre-approved in PROJECT.md constraints.
- CandidateMatch crosses Temporal activity boundaries — new fields (candidateSchemaType, matchSource) need Jackson-compatible defaults.

## Session Continuity

Last session: 2026-03-31T09:46:28.228Z
Stopped at: Completed 05-02-PLAN.md
Resume file: None
