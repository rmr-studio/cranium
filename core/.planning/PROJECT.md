# Declarative Manifest Catalog and Consumption Pipeline

## What This Is

A global catalog and manifest loading pipeline for the Riven Core backend that consumes declarative JSON manifest files (shared models, workspace templates, and integration schemas) and upserts them into queryable database tables on application startup. This provides the foundation for workspace template selection, standalone model installation, and integration schema provisioning — all driven by JSON files rather than code changes.

## Core Value

Manifest files on disk are reliably loaded into a queryable database catalog on every application startup, making predefined entity models, templates, and integration schemas available for downstream consumption (clone service, API endpoints) without per-integration code.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Database migration for 6 new catalog tables + 2 column additions to `entity_types`
- [ ] JPA entity classes for all catalog tables following existing project conventions
- [ ] ManifestLoaderService that runs on ApplicationReadyEvent, processes manifest directories in dependency order (models → templates → integrations)
- [ ] `$ref` resolution engine that resolves shared model references in template manifests
- [ ] `extend` merge logic (shallow, additive — new attributes added, existing untouched, no deletion semantics)
- [ ] Relationship format normalization (shorthand → full format with `targetRules[]`)
- [ ] Relationship validation (source/target key existence, cardinality enum validation, format mutual exclusivity)
- [ ] Idempotent upsert keyed on `manifest_catalog.key` + child unique constraints
- [ ] Full reconciliation — catalog entries for manifests no longer on disk are removed on startup
- [ ] JSON Schema files for model, template, and integration manifest validation
- [ ] JSON Schema validation at load time (invalid manifests log warning, are skipped, don't block startup)
- [ ] ManifestCatalogService (read-only query service) with `getAvailableTemplates()`, `getAvailableModels()`, `getManifestByKey()`, `getEntityTypesForManifest()`
- [ ] Directory scaffolding (`models/`, `templates/`, `integrations/`) with README and structural authoring guidelines
- [ ] Test fixture manifests in `src/test/resources/` covering model, template (with `$ref` + `extend`), and integration patterns
- [ ] Unit tests for `$ref` resolution, `extend` merge, relationship normalization, key validation
- [ ] Integration tests for full startup load cycle, idempotent re-load, manifest removal reconciliation

### Out of Scope

- CatalogCloneService (template clone, model install, integration clone into workspaces) — separate future work
- REST API controllers for catalog browsing (`GET /api/v1/catalog/*`) — service layer only for now
- `readonly` guard clauses on EntityTypeService/EntityTypeAttributeService — deferred to clone service phase
- Integration with IntegrationConnectionService for auto-clone on CONNECTED — deferred
- Workspace creation flow integration (template selection during onboarding) — deferred
- Model installation API endpoint (`POST /api/v1/workspaces/{id}/install-model`) — deferred
- Actual production manifest content (models, templates, integration manifests) — authored in separate worktrees
- Generic mapping engine for field mapping interpretation at sync runtime — separate feature
- Custom transformation plugin registry — separate feature

## Context

This feature implements the runtime storage and loading layer described in ADR-004 (Declarative-First Storage for Integration Mappings and Entity Templates). The existing codebase has:

- **`integration_definitions` table** — the closest precedent for a global catalog (no `workspace_id`, no RLS, no audit fields). The manifest catalog follows this same pattern.
- **Entity type schema is JSONB** — `Schema<UUID>` stores attributes as a UUID-keyed map. Catalog entity types use the same schema format with string keys (UUID assignment happens at clone time).
- **Relationship definitions** are workspace-scoped with `RelationshipDefinitionEntity` + `RelationshipTargetRuleEntity`. Catalog relationships mirror this structure but use VARCHAR key-based references instead of UUID FKs.
- **Semantic metadata** (`EntityTypeSemanticMetadataEntity`) is workspace-scoped. Catalog semantic metadata mirrors the same fields but references catalog entity types.
- **No manifest files exist yet** — directories, README, and structural guidelines are scaffolded as part of this work.

The codebase is a Spring Boot 3.5.3 / Kotlin 2.1.21 / Java 21 project with PostgreSQL (JSONB), Supabase auth, and the layered architecture pattern documented in CLAUDE.md.

## Constraints

- **Tech stack**: Must use existing Spring Boot / Kotlin / JPA stack. No new dependencies without discussion.
- **Database pattern**: Catalog tables are global (no `workspace_id`, no RLS) — follows `integration_definitions` precedent. No `AuditableEntity` base class (no `createdBy`/`updatedBy` — loader is system, not user).
- **Schema conventions**: SQL files go in `db/schema/` subdirectories per existing numbering (`00_extensions/` through `09_grants/`).
- **Loading order**: Models must load before templates (templates reference models via `$ref`). Integrations are independent.
- **Merge semantics**: Shallow merge only. Additive attributes. No deep recursive merge. No deletion semantics.
- **Key-based references**: Catalog relationships use VARCHAR keys, not UUID FKs. UUID resolution deferred to clone time.
- **Startup tolerance**: Invalid manifests are skipped with warning logs — must not prevent application startup.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Separate catalog tables (not reusing `entity_types`) | Catalog entries are global blueprints without workspace affiliation. Mixing with workspace-scoped `entity_types` would break RLS and conflate template availability with active usage. | — Pending |
| Key-based references in catalog relationships | Manifest definitions are self-contained by key. UUID resolution at clone time avoids circular dependency during loading and handles UUID instability across re-loads. | — Pending |
| Field mappings stay in catalog (not cloned) | Mappings are referenced at sync runtime by the generic mapping engine. Not user-editable, not workspace-owned. Duplicating adds complexity without value. | — Pending |
| Shared models as first-class catalog citizens | Enables standalone model installation outside template onboarding. ADR-004's compile-time-only treatment was too restrictive. | — Pending |
| JSON Schema validation at load time | Catches malformed manifests before they reach the database. CI can also validate manifest PRs. Replaces compile-time safety with boot-time + CI-time validation. | — Pending |
| Loader runs on every startup (idempotent) | Ensures catalog always reflects current manifest state. Checksum-based skip can optimize later if needed. | — Pending |

---
*Last updated: 2026-02-28 after initialization*
