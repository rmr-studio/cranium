---
tags:
  - priority/high
  - status/draft
  - architecture/design
  - architecture/feature
Created: 2026-02-10
Updated: 2026-02-28
Domains:
  - "[[Integrations]]"
  - "[[Entities]]"
  - "[[Knowledge]]"
Sub-Domain: "[[Entity Integration Sync]]"
---
# Quick Design: Predefined Integration Entity Types

## What & Why

Every integration source needs its own set of entity types to represent that tool's data model inside the application (e.g., HubSpot Contact, Stripe Invoice, Zendesk Ticket). These entity types are **readonly/protected** — they exist to provide a stable schema for data mapping during sync and cannot be modified by workspace users. They are defined declaratively in integration manifest files per [[ADR-004 Declarative-First Storage for Integration Mappings and Entity Templates]] and loaded into the database on startup. Each workspace that connects an integration gets these entity types synced into their environment automatically.

---

## Data Changes

**New/Modified Entities:**

- `entity_types` table gains a `source` column (`TEXT`, nullable, default `NULL`) to distinguish manifest-loaded readonly types (`source = 'MANIFEST'`) from user-created types (`source = NULL`)
- `entity_types` table gains a `readonly` column (`BOOLEAN`, default `FALSE`) to prevent modification of integration-derived entity types
- `entity_types` table gains a `manifest_slug` column (`TEXT`, nullable) referencing which integration manifest defined this type — used by the manifest loader for idempotent upsert

**New/Modified Fields:**

- Integration manifest files define entity type schemas using the same attribute definition format as user-created entity types (JSON schema with attribute keys, data types, validation rules)
- Semantic metadata for each entity type and attribute is included in the manifest and loaded alongside the schema per [[Semantic Metadata Foundation]]

---

## Components Affected

- [[EntityTypeService]] — must respect `readonly` flag, rejecting mutations on manifest-sourced entity types
- [[EntityTypeAttributeService]] — must reject attribute modifications on readonly entity types
- [[EntityTypeRelationshipService]] — must reject relationship modifications on readonly entity types
- [[EntityTypeSemanticMetadataService]] — semantic metadata for readonly types is loaded from manifests; user edits to semantic metadata on readonly types should be allowed (semantic metadata is interpretive, not structural)
- Manifest Loader Service (new) — scans `integrations/` directory, validates manifests, upserts entity types and semantic metadata into database

---

## API Changes

- Existing entity type CRUD endpoints return `403` when attempting to modify a readonly entity type
- `GET` endpoints for entity types include the `readonly` and `source` fields in the response so the frontend can disable editing UI for integration-derived types

---

## Failure Handling

- If a manifest defines an entity type that conflicts with a user-created entity type (name collision within the same workspace), the manifest loader logs a warning and skips that type — user-created types always take precedence
- If a manifest is structurally invalid, the loader skips the entire manifest with a warning — other manifests continue loading normally
- If the manifest loader fails entirely (e.g., filesystem permission error), the application starts normally with whatever definitions are already in the database from previous runs

---

## Gotchas & Edge Cases

- **Readonly vs. semantic metadata:** Entity type schema (attributes, relationships) is readonly for integration types, but semantic metadata should remain user-editable. A user might want to refine the semantic description of "HubSpot Contact" to better match their business context without altering the structural schema.
- **Cross-integration relationships:** See [[Connected Entities for READONLY Entity Types]] — integration entity types need a mechanism for forming relationships with entity types from other integrations during identity resolution, without modifying the readonly schema.
- **Manifest updates between versions:** When a manifest changes between application versions (e.g., a new attribute added to the HubSpot Contact schema), the loader upserts the updated definition. Existing entity instances are not retroactively modified — only the type definition changes. New sync operations will use the updated schema.
- **Workspace scoping:** Integration entity types are loaded as global definitions but instantiated per-workspace when an integration is connected. The manifest loader creates the type definitions; the connection flow clones them into the workspace.

---

## Tasks

- [ ] Add `source`, `readonly`, and `manifest_slug` columns to `entity_types` table
- [ ] Update [[EntityTypeService]], [[EntityTypeAttributeService]], [[EntityTypeRelationshipService]] to enforce readonly constraint
- [ ] Implement manifest loader for integration entity type schemas
- [ ] Define JSON Schema for integration manifest entity type section
- [ ] Update entity type API responses to include `readonly` and `source` fields

---

## Notes

- This feature is tightly coupled with [[Integration Schema Mapping]] — the same manifest file defines both the entity type schemas (this feature) and the field mappings (that feature)
- The manifest format and loader service are shared with [[Semantic Metadata Baked Entity Data Model Templates]] — template manifests use the same entity type definition structure, just without the integration-specific mapping section

---

## Related

- [[ADR-004 Declarative-First Storage for Integration Mappings and Entity Templates]]
- [[Integration Schema Mapping]]
- [[Connected Entities for READONLY Entity Types]]
- [[Entity Integration Sync]]
- [[Semantic Metadata Foundation]]

---

## Changelog

| Date | Author | Change |
| ---- | ------ | ------------- |
| 2026-02-10 | | Initial stub |
| 2026-02-28 | | Filled in design aligned with ADR-004 declarative-first approach |