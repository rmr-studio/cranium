# Requirements: Declarative Manifest Catalog and Consumption Pipeline

**Defined:** 2026-02-28
**Core Value:** Manifest files on disk are reliably loaded into a queryable database catalog on every application startup

## v1 Requirements

Requirements for this milestone. Each maps to roadmap phases.

### Database Foundation

- [x] **DB-01**: Catalog tables (6 tables) are created following existing `db/schema/` conventions with no RLS and no workspace scope
- [x] **DB-02**: JPA entity classes exist for all catalog tables following `IntegrationDefinitionEntity` pattern (no `AuditableEntity`, manual timestamps)
- [x] **DB-03**: Spring Data repository interfaces exist for all catalog entities
- [x] **DB-04**: `manifest_catalog` table has composite unique constraint `UNIQUE(key, type)` to prevent cross-type key collisions
- [ ] **DB-05**: `entity_types` table gains `source_manifest_key` (VARCHAR nullable) and `readonly` (BOOLEAN DEFAULT FALSE) columns

### Manifest Validation

- [x] **VAL-01**: JSON Schema files exist for model, template, and integration manifest formats
- [x] **VAL-02**: Manifest files are validated against JSON Schema at load time using existing `networknt` validator
- [x] **VAL-03**: Invalid manifests log a WARN with manifest key and validation errors, are skipped, and do not block application startup

### Manifest Loading

- [x] **LOAD-01**: Manifest loader runs on `ApplicationReadyEvent` and scans `models/`, `templates/`, `integrations/` directories from classpath
- [x] **LOAD-02**: Loading order is explicitly enforced in code: models → templates → integrations (not reliant on filesystem ordering)
- [x] **LOAD-03**: `$ref` resolution resolves shared model references in template manifests using an in-memory model lookup map (no DB reads during resolution)
- [x] **LOAD-04**: `extend` merge applies shallow additive semantics — new attributes added, existing base attributes preserved on key conflict, no deletion
- [x] **LOAD-05**: Relationship shorthand format (single `target` + `cardinality`) is normalized to full format (`targetRules[]` + `cardinalityDefault`) before persistence
- [x] **LOAD-06**: Relationships are validated: source/target keys exist in manifest's entity type set, cardinality is valid enum, shorthand and full format are mutually exclusive
- [x] **LOAD-07**: `protected` flag defaults to `true` for integration relationships and `false` for template relationships, inferred from directory context

### Catalog Persistence

- [x] **PERS-01**: Manifest upsert is idempotent — keyed on `manifest_catalog.key` + `type`, re-deployment produces identical catalog state
- [x] **PERS-02**: Child table reconciliation uses delete-then-reinsert within a per-manifest `@Transactional` boundary
- [x] **PERS-03**: Full reconciliation on startup — catalog entries for manifests no longer on disk are marked inactive (`active = false`)
- [x] **PERS-04**: Per-manifest transaction isolation — one bad manifest does not roll back the entire catalog load

### Catalog Query

- [ ] **QUERY-01**: `ManifestCatalogService` provides `getAvailableTemplates()` returning all active template manifests
- [ ] **QUERY-02**: `ManifestCatalogService` provides `getAvailableModels()` returning all active model manifests
- [ ] **QUERY-03**: `ManifestCatalogService` provides `getManifestByKey(key)` returning a single manifest with nested entity types and relationships
- [ ] **QUERY-04**: `ManifestCatalogService` provides `getEntityTypesForManifest(manifestId)` returning entity type definitions for a manifest

### Scaffolding

- [x] **SCAF-01**: Directory structure created for `models/`, `templates/`, `integrations/` with README authoring guidelines
- [x] **SCAF-02**: README documents manifest format, `$ref` syntax, `extend` semantics, relationship shorthand vs full format, and field mapping structure

### Testing

- [x] **TEST-01**: Unit tests cover `$ref` resolution — successful lookup, missing model warning, passthrough without `$ref`
- [x] **TEST-02**: Unit tests cover `extend` merge — attribute addition, semantic override, base preservation on key conflict, no-extend passthrough
- [x] **TEST-03**: Unit tests cover relationship normalization — shorthand to full conversion, format mutual exclusivity rejection
- [x] **TEST-04**: Unit tests cover relationship validation — key existence, cardinality enum, duplicate key detection
- [x] **TEST-05**: Test fixture manifests in `src/test/resources/` cover model, template (with `$ref` + `extend`), and integration patterns
- [ ] **TEST-06**: Integration tests verify full startup load cycle — fixtures loaded into catalog tables with correct data
- [ ] **TEST-07**: Integration tests verify idempotent reload — second startup produces identical catalog state
- [ ] **TEST-08**: Integration tests verify manifest removal reconciliation — removed manifest results in inactive catalog entry

## v2 Requirements

Deferred to future milestones. Tracked but not in current roadmap.

### Clone Service

- **CLONE-01**: CatalogCloneService clones template entity types, relationships, and semantic metadata into workspace tables
- **CLONE-02**: CatalogCloneService supports standalone model installation with key conflict detection
- **CLONE-03**: CatalogCloneService clones integration schemas as readonly entity types with protected relationships

### API Surface

- **API-01**: REST endpoints for catalog browsing (GET /api/v1/catalog/templates, /models, /{key})
- **API-02**: Model installation endpoint (POST /api/v1/workspaces/{id}/install-model)

### Guards

- **GUARD-01**: EntityTypeService and EntityTypeAttributeService reject mutations on readonly entity types
- **GUARD-02**: EntityTypeRelationshipService rejects modifications on protected relationship definitions from catalog

### Optimizations

- **OPT-01**: Checksum-based skip for unchanged manifests at load time
- **OPT-02**: Configurable manifest base path via application.yml for mounted volumes

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Hot reload / filesystem watching | Disproportionate complexity — manifests are infrastructure, changed at deploy time |
| Deep recursive `extend` merge | Ambiguous semantics, brittle when base models evolve, hard to document |
| Generic field mapping engine execution | Conflates catalog storage with runtime sync pipeline — separate feature |
| Custom transformation plugin registry | Belongs with the mapping engine, not the catalog |
| REST API controllers for catalog | No consumer exists yet — add with clone service |
| Integration with IntegrationConnectionService | Deferred to clone service milestone |
| Workspace creation flow integration | Deferred to clone service milestone |
| Per-integration Kotlin classes | ADR-004 explicitly rejects this — manifests replace code |
| Runtime admin API for custom definitions | Deferred per ADR-004 — manifest format must stabilize first |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| DB-01 | Phase 1 | Complete |
| DB-02 | Phase 1 | Complete |
| DB-03 | Phase 1 | Complete |
| DB-04 | Phase 1 | Complete |
| DB-05 | Phase 1 | Deferred (CONTEXT.md) |
| VAL-01 | Phase 1 | Complete |
| SCAF-01 | Phase 1 | Complete |
| SCAF-02 | Phase 1 | Complete |
| VAL-02 | Phase 2 | Complete |
| VAL-03 | Phase 2 | Complete |
| LOAD-01 | Phase 2 | Complete |
| LOAD-02 | Phase 2 | Complete |
| LOAD-03 | Phase 2 | Complete |
| LOAD-04 | Phase 2 | Complete |
| LOAD-05 | Phase 2 | Complete |
| LOAD-06 | Phase 2 | Complete |
| LOAD-07 | Phase 2 | Complete |
| PERS-01 | Phase 2 | Complete |
| PERS-02 | Phase 2 | Complete |
| PERS-03 | Phase 2 | Complete |
| PERS-04 | Phase 2 | Complete |
| TEST-01 | Phase 2 | Complete |
| TEST-02 | Phase 2 | Complete |
| TEST-03 | Phase 2 | Complete |
| TEST-04 | Phase 2 | Complete |
| TEST-05 | Phase 2 | Complete |
| QUERY-01 | Phase 3 | Pending |
| QUERY-02 | Phase 3 | Pending |
| QUERY-03 | Phase 3 | Pending |
| QUERY-04 | Phase 3 | Pending |
| TEST-06 | Phase 3 | Pending |
| TEST-07 | Phase 3 | Pending |
| TEST-08 | Phase 3 | Pending |

**Coverage:**
- v1 requirements: 33 total
- Mapped to phases: 33
- Unmapped: 0

---
*Requirements defined: 2026-02-28*
*Last updated: 2026-02-28 after roadmap creation*
