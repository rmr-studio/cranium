# Requirements: Identity Resolution

**Defined:** 2026-03-16
**Core Value:** When a user looks at any entity in their workspace, they can see every related entity from every connected tool — turning siloed integration data into a unified identity graph.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Infrastructure

- [x] **INFRA-01**: Generic execution queue with `job_type` discriminator (refactored from `workflow_execution_queue`)
- [x] **INFRA-02**: Queue deduplication — skip enqueue if PENDING job exists for same entity
- [x] **INFRA-03**: `IDENTITY_MATCH` added to `SourceType` enum
- [x] **INFRA-04**: pg_trgm extension enabled with partial GIN index on `entity_attributes` using `(value->>'value')`
- [x] **INFRA-05**: DB schema for `identity_clusters`, `identity_cluster_members`, and `match_suggestions` tables with workspace-scoped indexes
- [x] **INFRA-06**: Canonical UUID ordering enforced (source < target) with DB CHECK constraint and unique constraint on `(source_entity_id, target_entity_id, deleted)`

### Matching

- [x] **MATCH-01**: Entity save/update triggers async match job via `@TransactionalEventListener(AFTER_COMMIT)` with `@Transactional(REQUIRES_NEW)`
- [x] **MATCH-02**: Two-phase fuzzy candidate query using pg_trgm (`%` operator for GIN blocking, `similarity()` for refinement)
- [x] **MATCH-03**: Workspace-scoped candidate filtering (same workspace, different entity, not soft-deleted)
- [x] **MATCH-04**: Weighted confidence scoring from multiple signals (EMAIL=0.9, PHONE=0.85, NAME=0.5, COMPANY=0.3, CUSTOM_IDENTIFIER=0.7)
- [x] **MATCH-05**: Minimum score threshold of 0.5 — below threshold produces no suggestion
- [x] **MATCH-06**: Per-signal breakdown stored as JSONB on match_suggestions (type, sourceValue, targetValue, similarity, weight)

### Suggestions

- [x] **SUGG-01**: Match suggestion CRUD with PENDING → CONFIRMED / REJECTED / EXPIRED state machine
- [x] **SUGG-02**: Idempotent suggestion creation — duplicate pair (canonical ordering) silently skipped
- [x] **SUGG-03**: Rejection stores signal snapshot in `rejection_signals` JSONB column
- [x] **SUGG-04**: Re-suggestion on new/stronger signals — diff current signals against rejection snapshot
- [x] **SUGG-05**: Activity logging for all match state transitions (create, confirm, reject)

### Confirmation

- [x] **CONF-01**: Confirm creates CONNECTED_ENTITIES relationship via EntityRelationshipService with source=IDENTITY_MATCH
- [x] **CONF-02**: Identity cluster created/assigned at confirmation time (5 cases: neither clustered, one clustered, both in different clusters → merge, both in same cluster → no-op)
- [x] **CONF-03**: Cluster merge — move all members of smaller cluster into larger, soft-delete empty cluster
- [x] **CONF-04**: Reject transitions PENDING → REJECTED with resolvedBy and resolvedAt
- [x] **CONF-05**: Double-confirm and double-reject produce ConflictException

### API

- [x] **API-01**: `GET /api/v1/identity/{workspaceId}/suggestions` — list suggestions filterable by status, paginated
- [x] **API-02**: `GET /api/v1/identity/{workspaceId}/suggestions/{id}` — suggestion detail with signal breakdown
- [ ] **API-03**: `POST /api/v1/identity/{workspaceId}/suggestions/{id}/confirm` — confirm match
- [ ] **API-04**: `POST /api/v1/identity/{workspaceId}/suggestions/{id}/reject` — reject match
- [x] **API-05**: `GET /api/v1/identity/{workspaceId}/clusters` — list identity clusters
- [x] **API-06**: `GET /api/v1/identity/{workspaceId}/clusters/{id}` — cluster detail with member entities
- [x] **API-07**: `GET /api/v1/identity/{workspaceId}/entities/{id}/matches` — pending match count for entity
- [ ] **API-08**: Notification stub published on match events (no-op until notification domain ships)

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Configuration

- **CONFIG-01**: Per-workspace match weight configuration (overrides default signal weights)
- **CONFIG-02**: "Never match" blocklist rules (explicit pair exclusions)

### Analytics

- **ANALYTICS-01**: Match confidence histogram / distribution endpoint
- **ANALYTICS-02**: Match volume metrics per workspace

### Automation

- **AUTO-01**: Auto-confirm above learned threshold (requires confirmation/rejection telemetry)
- **AUTO-02**: Transitive matching (A↔B + B↔C → suggest A↔C)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Full entity merge/unmerge (golden record) | Integration entities are readonly; merge creates write conflicts and complex unmerge paths |
| ML-based learned scoring | Requires labelled training data volume that new workspaces don't have |
| Synchronous real-time matching | O(N) candidate scan would block entity saves; async is production-grade |
| Cross-workspace matching | Workspaces are security boundaries; cross-workspace violates tenancy model |
| Same-type duplicate detection | Different problem domain with different UX semantics |
| Visual identity graph explorer UI | Backend only; graph rendering is a frontend concern |
| Notification delivery infrastructure | Built separately in another worktree |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| INFRA-01 | Phase 1 | Complete |
| INFRA-02 | Phase 1 | Complete |
| INFRA-03 | Phase 1 | Complete |
| INFRA-04 | Phase 1 | Complete |
| INFRA-05 | Phase 1 | Complete |
| INFRA-06 | Phase 1 | Complete |
| MATCH-01 | Phase 3 | Complete |
| MATCH-02 | Phase 2 | Complete |
| MATCH-03 | Phase 2 | Complete |
| MATCH-04 | Phase 2 | Complete |
| MATCH-05 | Phase 2 | Complete |
| MATCH-06 | Phase 2 | Complete |
| SUGG-01 | Phase 2 | Complete |
| SUGG-02 | Phase 2 | Complete |
| SUGG-03 | Phase 2 | Complete |
| SUGG-04 | Phase 2 | Complete |
| SUGG-05 | Phase 2 | Complete |
| CONF-01 | Phase 4 | Complete |
| CONF-02 | Phase 4 | Complete |
| CONF-03 | Phase 4 | Complete |
| CONF-04 | Phase 4 | Complete |
| CONF-05 | Phase 4 | Complete |
| API-01 | Phase 5 | Complete |
| API-02 | Phase 5 | Complete |
| API-03 | Phase 5 | Pending |
| API-04 | Phase 5 | Pending |
| API-05 | Phase 5 | Complete |
| API-06 | Phase 5 | Complete |
| API-07 | Phase 5 | Complete |
| API-08 | Phase 5 | Pending |

**Coverage:**
- v1 requirements: 30 total
- Mapped to phases: 30
- Unmapped: 0

**Note:** REQUIREMENTS.md header previously stated 29 requirements. Actual count is 30 (INFRA 6 + MATCH 6 + SUGG 5 + CONF 5 + API 8).

---
*Requirements defined: 2026-03-16*
*Last updated: 2026-03-16 — traceability populated after roadmap creation*
