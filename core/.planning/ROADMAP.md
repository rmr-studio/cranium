# Roadmap: Multi-Strategy Identity Resolution Matching

## Overview

Five incremental phases replace the single-strategy pg_trgm pipeline with a multi-strategy candidate discovery system. Each phase delivers a working, tested matching capability on top of what came before. The pipeline starts able to match phone formatting variations and structural query bugs fixed, then gains scoring intelligence, then nickname and token matching, then email domain decomposition, and finally phonetic matching with full dispatch consolidation.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Signal-Type-Aware Normalization + Candidate Query Fixes** - Foundation normalization pipeline and structural query bug fixes that unblock all downstream phases (completed 2026-03-28)
- [x] **Phase 2: Scoring Improvements + Signal Classification** - Confidence gate, cross-type discounting, and signal_type tag that make scores trustworthy (completed 2026-03-29)
- [ ] **Phase 3: Nickname Lookup + Token-Set Similarity** - Name variant matching via nickname expansion and word-token overlap
- [ ] **Phase 4: Email Decomposition Strategy** - Domain-aware candidate discovery with local-part tokenization and free-domain exclusion
- [ ] **Phase 5: Phonetic Matching + Method Consolidation** - Phonetic name matching via dmetaphone and consolidated when() dispatch

## Phase Details

### Phase 1: Signal-Type-Aware Normalization + Candidate Query Fixes
**Goal**: The pipeline correctly normalizes values before matching and no longer hides multi-attribute matches behind DISTINCT ON
**Depends on**: Nothing (first phase)
**Requirements**: NORM-01, NORM-02, NORM-03, NORM-04, NORM-05, CAND-01, CAND-02, CAND-03, TEST-01
**Success Criteria** (what must be TRUE):
  1. A phone number with formatting ("+1 (555) 123-4567") matches an entity with digits-only ("5551234567") via exact-digits query
  2. Email plus-addresses (john+tag@example.com) normalize to base address before trigram matching
  3. Text with diacritics ("Jose Garcia") normalizes to plain ASCII before comparison
  4. Title/suffix stopwords (Dr., PhD, Inc.) are stripped before matching
  5. An entity with two matching attributes produces two candidate rows (not one collapsed by DISTINCT ON), and the requireNotNull replacement compiles cleanly
**Plans:** 2/2 plans complete
Plans:
- [ ] 01-01-PLAN.md — Create IdentityNormalizationService with signal-type-aware dispatch and unit tests
- [ ] 01-02-PLAN.md — Wire normalization into CandidateService, remove DISTINCT ON, add phone exact-digits query, fix requireNotNull

### Phase 2: Scoring Improvements + Signal Classification
**Goal**: Scores reflect signal quality — low-weight single signals are rejected and cross-type matches are appropriately discounted
**Depends on**: Phase 1
**Requirements**: SCOR-01, SCOR-02, SCOR-03, SCOR-04, SIGC-01, SIGC-02, SIGC-03, TEST-05, TEST-06
**Success Criteria** (what must be TRUE):
  1. A candidate matched on a single low-weight NAME signal is rejected by the confidence gate
  2. A candidate matched on a single EMAIL or PHONE signal (weight >= 0.85) is accepted
  3. A candidate matched on 2+ signals passes regardless of individual signal weights
  4. An EMAIL value matched against a NAME attribute receives a 0.5x score multiplier
  5. Signal type (NAME, COMPANY, PHONE, EMAIL, CUSTOM) is readable from semantic metadata and flows into MatchSignalType resolution
**Plans:** 2/2 plans complete
Plans:
- [ ] 02-01-PLAN.md — Model extensions, MatchSource enum, signal_type schema/JPA/cache, candidate query wiring
- [ ] 02-02-PLAN.md — Confidence gate + cross-type discount in scoring service with unit tests

### Phase 3: Nickname Lookup + Token-Set Similarity
**Goal**: NAME signal types produce candidates for known nickname pairs and for name inversions
**Depends on**: Phase 2
**Requirements**: UTIL-01, UTIL-02, CAND-04, TEST-02, TEST-03, TEST-07, TEST-08
**Success Criteria** (what must be TRUE):
  1. "William" triggers a candidate query that finds an entity stored as "Bill" (and vice versa)
  2. NicknameExpander.expand() returns all known variants for a given canonical name
  3. Token-set overlap correctly identifies "Smith John" and "John Smith" as equivalent via TokenSimilarity
  4. A pipeline integration test confirms William/Bill nickname match produces a merge suggestion
**Plans:** 2 plans
Plans:
- [ ] 03-01-PLAN.md — NicknameExpander and TokenSimilarity utility objects with unit tests
- [ ] 03-02-PLAN.md — Wire nickname query + token re-scoring into CandidateService, pipeline integration tests

### Phase 4: Email Decomposition Strategy
**Goal**: EMAIL signal types discover candidates with same domain and similar local parts, while free email domains are excluded to prevent candidate explosion
**Depends on**: Phase 3
**Requirements**: UTIL-03, UTIL-04, CAND-05, CAND-06, TEST-04, TEST-09, TEST-11
**Success Criteria** (what must be TRUE):
  1. "jsmith@acme.com" and "john.smith@acme.com" produce a match suggestion via the domain-aware query
  2. "jsmith@gmail.com" does NOT trigger the domain-aware query (gmail is in the free-domain skip set)
  3. EmailMatcher.tokenizeLocal("john.smith") returns ["john", "smith"]
  4. A pipeline integration test confirms email domain match produces a suggestion for a corporate domain
**Plans:** 2 plans
Plans:
- [ ] 02-01-PLAN.md — Model extensions, MatchSource enum, signal_type schema/JPA/cache, candidate query wiring
- [ ] 02-02-PLAN.md — Confidence gate + cross-type discount in scoring service with unit tests

### Phase 5: Phonetic Matching + Method Consolidation
**Goal**: NAME signal types discover phonetically similar candidates via dmetaphone, and all candidate query paths are consolidated into a single when(signalType) dispatch
**Depends on**: Phase 4
**Requirements**: CAND-07, CAND-08, DB-01, TEST-10, TEST-12
**Success Criteria** (what must be TRUE):
  1. "Smith" and "Smythe" produce a match suggestion via the phonetic query
  2. The fuzzystrmatch extension is present in the extensions schema file
  3. All candidate query strategies are dispatched from a single when(signalType) expression with private methods — no inline if-chains
  4. A pipeline integration test confirms phonetic match (Smith/Smythe) produces a suggestion
  5. A pipeline integration test confirms multi-strategy merge produces correct composite scores
**Plans:** 2 plans
Plans:
- [ ] 02-01-PLAN.md — Model extensions, MatchSource enum, signal_type schema/JPA/cache, candidate query wiring
- [ ] 02-02-PLAN.md — Confidence gate + cross-type discount in scoring service with unit tests

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Signal-Type-Aware Normalization + Candidate Query Fixes | 2/2 | Complete   | 2026-03-28 |
| 2. Scoring Improvements + Signal Classification | 2/2 | Complete   | 2026-03-29 |
| 3. Nickname Lookup + Token-Set Similarity | 0/2 | Not started | - |
| 4. Email Decomposition Strategy | 0/TBD | Not started | - |
| 5. Phonetic Matching + Method Consolidation | 0/TBD | Not started | - |
