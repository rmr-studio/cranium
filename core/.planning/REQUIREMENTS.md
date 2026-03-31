# Requirements: Multi-Strategy Identity Resolution Matching

**Defined:** 2026-03-28
**Core Value:** Match the entities that a human reviewer would recognize as the same real-world identity, even when the character-level representation differs significantly.

## v1 Requirements

Requirements for this milestone. Each maps to roadmap phases.

### Normalization

- [x] **NORM-01**: Phone values are normalized to digits-only before matching (strips formatting, optional country code removal)
- [x] **NORM-02**: Email values strip plus-addressing before matching (john+tag@example.com -> john@example.com)
- [x] **NORM-03**: Text values undergo Unicode NFKD decomposition to strip diacritical marks (Jose Garcia -> jose garcia)
- [x] **NORM-04**: Text values strip title/suffix stopwords before matching (Dr., PhD, Inc., LLC, Corp, etc.)
- [x] **NORM-05**: Normalization dispatches by signal type (PHONE gets digit normalizer, EMAIL gets email normalizer, others get text normalizer)

### Candidate Discovery

- [x] **CAND-01**: PHONE signal types run an exact-match-on-normalized-digits query alongside pg_trgm (catches formatting variations that trigrams miss)
- [x] **CAND-02**: DISTINCT ON (ea.entity_id) removed from candidate query so mergeCandidates handles dedup (stops hiding multi-attribute matches)
- [x] **CAND-03**: Existing `!!` on mergeCandidates line 187 replaced with requireNotNull (CLAUDE.md compliance)
- [x] **CAND-04**: NAME signal types run nickname expansion query (expands trigger name to known variants, queries for any match)
- [x] **CAND-05**: EMAIL signal types run domain-aware query (same domain candidates with local-part similarity scoring)
- [x] **CAND-06**: Free email domains (gmail, yahoo, outlook, ~30 total) are excluded from domain-aware query (prevents candidate explosion)
- [x] **CAND-07**: NAME signal types run phonetic query via dmetaphone() (catches phonetically similar names: Smith/Smythe)
- [x] **CAND-08**: All candidate query methods consolidated into when(signalType) dispatch with private methods (no strategy interface)

### Scoring

- [x] **SCOR-01**: Per-signal-type confidence gate rejects candidates with only 1 low-weight signal (requires 2+ signals OR 1 signal with weight >= 0.85)
- [x] **SCOR-02**: Cross-type score discounting applies 0.5x multiplier when candidate SchemaType differs from trigger SchemaType
- [x] **SCOR-03**: CandidateMatch model extended with candidateSchemaType field for cross-type detection
- [x] **SCOR-04**: CandidateMatch model extended with matchSource enum field (TRIGRAM, EXACT_NORMALIZED, NICKNAME, EMAIL_DOMAIN, PHONETIC) with TRIGRAM default

### Signal Classification

- [x] **SIGC-01**: signal_type tag added to entity_type_semantic_metadata for IDENTIFIER-classified attributes (NAME, COMPANY, PHONE, EMAIL, CUSTOM)
- [x] **SIGC-02**: MatchSignalType.fromSchemaType() updated to read signal_type tag from semantic metadata instead of only mapping SchemaType
- [x] **SIGC-03**: EntityTypeClassificationService cache updated to include signal_type tag alongside IDENTIFIER classification

### Matching Utilities

- [x] **UTIL-01**: NicknameExpander utility with ~150 bidirectional English name groups (William<->Bill, Robert<->Bob, etc.)
- [x] **UTIL-02**: TokenSimilarity utility computing overlap coefficient on word token sets
- [x] **UTIL-03**: EmailMatcher utility parsing emails into local+domain parts with similarity computation
- [x] **UTIL-04**: EmailMatcher includes local-part tokenization (john.smith -> {john, smith})

### Database

- [x] **DB-01**: fuzzystrmatch PostgreSQL extension added to db/schema/00_extensions/extensions.sql

### Testing

- [x] **TEST-01**: Unit tests for all normalization functions (phone digit extraction, diacritics, title stripping, email plus-addressing)
- [x] **TEST-02**: Unit tests for NicknameExpander (expand, areEquivalent, unknown names)
- [x] **TEST-03**: Unit tests for TokenSimilarity (partial overlap, identical, no overlap)
- [x] **TEST-04**: Unit tests for EmailMatcher (parse, similarity, tokenizeLocal)
- [x] **TEST-05**: Unit tests for scoring confidence gate (single NAME rejected, single EMAIL accepted, two signals accepted)
- [x] **TEST-06**: Unit tests for cross-type score discounting
- [x] **TEST-07**: Pipeline integration test: phone formatting variation produces match via exact-digits query
- [x] **TEST-08**: Pipeline integration test: nickname match produces suggestion (William<->Bill)
- [x] **TEST-09**: Pipeline integration test: email domain match produces suggestion (jsmith@acme.com<->john.smith@acme.com)
- [x] **TEST-10**: Pipeline integration test: phonetic match produces suggestion (Smith<->Smythe)
- [x] **TEST-11**: Pipeline integration test: free email domain (gmail.com) does NOT trigger domain strategy
- [x] **TEST-12**: Pipeline integration test: multi-strategy merge produces correct composite scores

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Advanced Matching

- **ADV-01**: Workspace-level matching strategy configuration (enable/disable strategies per workspace)
- **ADV-02**: Per-workspace signal weight overrides (TODO-IR-002)
- **ADV-03**: Non-English nickname tables (Spanish, German, French, etc.)
- **ADV-04**: Normalized value column on entity_attributes for faster GIN index matching
- **ADV-05**: Name parsing (first/last extraction) for better token matching
- **ADV-06**: Address normalization (USPS/RESO standards)

## Out of Scope

| Feature | Reason |
|---------|--------|
| MatchingStrategy interface / plugin system | Premature abstraction for 4 strategies. Use private methods. Extract interface when 5th+ strategy arrives. |
| ML-based matching / embeddings | Pure algorithmic approaches only. No model training infrastructure. |
| Distributed cache for nickname tables | Static in-memory map is sufficient at current scale. |
| Real-time strategy switching | All workspaces use same strategies. Per-workspace config deferred to v2. |
| Entity-type filtering in candidate query | Person vs Company type boundary filtering is a separate concern from signal-type matching. |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| NORM-01 | Phase 1 | Complete |
| NORM-02 | Phase 1 | Complete |
| NORM-03 | Phase 1 | Complete |
| NORM-04 | Phase 1 | Complete |
| NORM-05 | Phase 1 | Complete |
| CAND-01 | Phase 1 | Complete |
| CAND-02 | Phase 1 | Complete |
| CAND-03 | Phase 1 | Complete |
| TEST-01 | Phase 1 | Complete |
| SCOR-01 | Phase 2 | Complete |
| SCOR-02 | Phase 2 | Complete |
| SCOR-03 | Phase 2 | Complete |
| SCOR-04 | Phase 2 | Complete |
| SIGC-01 | Phase 2 | Complete |
| SIGC-02 | Phase 2 | Complete |
| SIGC-03 | Phase 2 | Complete |
| TEST-05 | Phase 2 | Complete |
| TEST-06 | Phase 2 | Complete |
| UTIL-01 | Phase 3 | Complete |
| UTIL-02 | Phase 3 | Complete |
| CAND-04 | Phase 3 | Complete |
| TEST-02 | Phase 3 | Complete |
| TEST-03 | Phase 3 | Complete |
| TEST-07 | Phase 3 | Complete |
| TEST-08 | Phase 3 | Complete |
| UTIL-03 | Phase 4 | Complete |
| UTIL-04 | Phase 4 | Complete |
| CAND-05 | Phase 4 | Complete |
| CAND-06 | Phase 4 | Complete |
| TEST-04 | Phase 4 | Complete |
| TEST-09 | Phase 4 | Complete |
| TEST-11 | Phase 4 | Complete |
| CAND-07 | Phase 5 | Complete |
| CAND-08 | Phase 5 | Complete |
| DB-01 | Phase 5 | Complete |
| TEST-10 | Phase 5 | Complete |
| TEST-12 | Phase 5 | Complete |

**Coverage:**
- v1 requirements: 37 total
- Mapped to phases: 37
- Unmapped: 0

---
*Requirements defined: 2026-03-28*
*Last updated: 2026-03-28 after initial definition*
