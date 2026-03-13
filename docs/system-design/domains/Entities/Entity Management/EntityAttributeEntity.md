---
tags:
  - layer/entity
  - component/active
  - architecture/component
Created: 2026-03-09
Updated: 2026-03-09
Domains:
  - "[[Entities]]"
---
# EntityAttributeEntity

Part of [[Entity Management]]

## Purpose

JPA entity representing a single normalized attribute value for an entity instance, stored in the `entity_attributes` table. Replaces the JSONB `payload` column that was previously on the `entities` table.

---

## Responsibilities

- Map to `entity_attributes` table with proper column types
- Store individual attribute values as JSONB
- Support soft-delete via `AuditableSoftDeletableEntity`
- Convert to domain payload via `toPrimitivePayload()`

---

## Dependencies

- `AuditableSoftDeletableEntity` — Base class for audit columns and soft-delete
- `SchemaType` — Enum for attribute data type classification
- `EntityAttributePrimitivePayload` — Domain model for attribute values

## Used By

- [[EntityAttributeRepository]] — Persistence layer
- [[EntityAttributeService]] — Service layer

---

## Key Logic

**Table structure:**

| Column | Type | Purpose |
|--------|------|---------|
| `id` | UUID (PK) | Auto-generated primary key |
| `entity_id` | UUID (FK) | Reference to parent entity |
| `workspace_id` | UUID | Workspace isolation |
| `type_id` | UUID | Reference to entity type |
| `attribute_id` | UUID | Reference to attribute in type schema |
| `schema_type` | VARCHAR(50) | Enum string of `SchemaType` |
| `value` | JSONB | The actual attribute value |
| `deleted` | BOOLEAN | Soft-delete flag |
| `deleted_at` | TIMESTAMP | Soft-delete timestamp |

**Indexes:**
- `idx_entity_attributes_entity_id` — Composite on `(entity_id, attribute_id)` for attribute lookups per entity
- `idx_entity_attributes_workspace` — On `workspace_id` for workspace-scoped queries

**`toPrimitivePayload()` conversion:**

Creates `EntityAttributePrimitivePayload` with `value` and `schemaType` from the entity fields.

**Design note — one row per attribute:**

Each attribute value for an entity instance is stored as a separate row. This enables:
- Indexed cross-entity queries via `AttributeSqlGenerator` EXISTS subqueries
- Per-attribute type tracking via `schema_type` column
- Future trigram indexing for fuzzy text search

---

## Gotchas

- **Null values not stored:** Attributes with null values are not persisted — `EntityAttributeService.saveAttributes()` filters them out. IS_NULL queries check for row absence.
- **JSONB value column:** The `value` column stores the raw attribute value as JSONB (strings are JSON strings, numbers are JSON numbers). Use `#>> '{}'` in SQL to extract as text.
- **Soft-delete filter:** `@SQLRestriction("deleted = false")` is inherited from `AuditableSoftDeletableEntity`, making soft-deleted rows invisible to Hibernate queries. Native SQL in the repository bypasses this when needed.

---

## Related

- [[EntityAttributeRepository]] — Repository for persistence
- [[EntityAttributeService]] — Service for CRUD operations
- [[Entity Management]] — Parent subdomain
