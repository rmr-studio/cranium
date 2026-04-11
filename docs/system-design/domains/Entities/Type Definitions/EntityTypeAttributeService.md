---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-02-08
Updated: 2026-03-09
Domains:
  - "[[Entities]]"
---
# EntityTypeAttributeService

Part of [[Type Definitions]]

## Purpose

Utility service for attribute schema operations including definition save/remove, unique constraint management, and column extraction.

---

## Responsibilities

- Save attribute definitions with breaking change validation
- Remove attribute definitions from type schema
- Extract unique attributes from entity payloads
- Check unique constraint conflicts (excluding current entity for updates)
- Save unique values to normalized table via native SQL
- Delete unique values when entities/types deleted
- Validate attribute definition constraints (unique requires STRING/NUMBER)

---

## Dependencies

- [[EntityValidationService]] — Breaking change detection and bulk entity validation
- `EntityRepository` — Fetch existing entities for validation
- `EntityUniqueValuesRepository` — Normalized unique constraint table
- [[EntityTypeSemanticMetadataService]] — Lifecycle hooks for attribute semantic metadata
- [[EntityAttributeService]] — Loading entity attributes during breaking change validation

## Used By

- [[EntityTypeService]] — Attribute definition management
- [[EntityService]] — Unique constraint enforcement during entity save

---

## Key Logic

**Save attribute definition:**

1. Validate attribute constraints (unique requires STRING or NUMBER type)
1b. Validate static default values — if the attribute has a `DefaultValue.Static`, the literal value is validated against the attribute's schema via `SchemaService`. `DefaultValue.Dynamic` defaults skip literal validation (they are resolved at entity creation time, not at definition time).
2. Upsert attribute into type's schema properties
3. Detect breaking changes via [[EntityValidationService]]
4. If breaking changes exist:
   - Fetch all existing entities of this type
   - Batch-load attributes via [[EntityAttributeService]].getAttributesForEntities()
   - Validate each against new schema using pre-loaded attributes
   - If any invalid: throw `SchemaValidationException` with sample errors
5. Apply schema update to type (caller saves entity)
6. If attribute is new: initialize semantic metadata via `semanticMetadataService.initializeForTarget(entityTypeId, workspaceId, ATTRIBUTE, attributeId)`

**Remove attribute definition (semantic metadata):**

When an attribute is removed, calls `semanticMetadataService.deleteForTarget(entityTypeId, ATTRIBUTE, attributeId)` to hard-delete the metadata record. Hard-delete (not soft-delete) prevents orphaned metadata for non-existent attributes.

**Readonly guards:**

Both `saveAttributeDefinition` and `removeAttributeDefinition` check `type.readonly` before proceeding. Integration-sourced entity types (readonly = true) cannot have their attributes modified through these methods.

**Unique constraint enforcement:**

- **Extract:** Filters payload for attributes marked `unique=true` in schema
- **Check:** Queries `entity_unique_values` table for conflicts (excludes current entity)
- **Save:** Uses native SQL DELETE + INSERT to bypass Hibernate tracking issues
- **Conflict:** Throws `UniqueConstraintViolationException` if duplicate found

**Native SQL rationale:**

- Unique values stored in normalized table separate from entity payload JSONB
- Native SQL avoids Hibernate session conflicts during entity save transaction
- DELETE + INSERT pattern ensures clean replacement of unique values

---

## Public Methods

### `saveAttributeDefinition(workspaceId, type, request)`

Validates and applies attribute definition to type schema. Throws `SchemaValidationException` if breaking changes would invalidate existing entities.

### `removeAttributeDefinition(type, attributeId)`

Removes attribute from type schema. Caller responsible for impact analysis and saving.

### `extractUniqueAttributes(type, payload): Map<UUID, EntityAttributePrimitivePayload?>`

Filters entity payload for attributes marked unique in schema. Returns map of fieldId -> primitive payload.

### `checkAttributeUniqueness(typeId, fieldId, value, excludeEntityId?)`

Checks for unique constraint conflicts. Throws `UniqueConstraintViolationException` if duplicate found.

ExcludeEntityId parameter allows updates (entity's own value doesn't conflict with itself).

### `saveUniqueValues(workspaceId, entityId, typeId, uniqueValues)`

Saves unique values to normalized table. Deletes existing values first, then inserts new ones via native SQL.

### `deleteEntities(workspaceId, ids): Int`

Deletes unique values for given entities. Returns count of deleted rows.

### `deleteType(workspaceId, typeId): Int`

Deletes all unique values for entity type. Returns count of deleted rows.

---

## Gotchas

- **No injected dependencies for pure utility methods:** Service is Spring bean but some methods are pure functions (extractUniqueAttributes)
- **Breaking change validation:** Queries ALL entities of type to validate schema changes. Could be slow for large entity sets (future optimization: sampling).
- **Native SQL requirement:** Unique value operations use native SQL to avoid Hibernate session conflicts during transactional entity saves
- **Unique type restriction:** Only STRING and NUMBER attributes can be marked unique (validated at definition save time)
- **Sample error limit:** Breaking change validation shows first 3 entity errors in exception message

---

## Related

- [[EntityTypeService]] — Primary consumer for definition management
- [[EntityService]] — Uses for unique constraint checks during save
- [[EntityValidationService]] — Breaking change detection
- [[Type Definitions]] — Parent subdomain

---

## Changelog

### 2026-03-09 — EntityAttributeService dependency for breaking change validation

- Added `EntityAttributeService` as a constructor dependency.
- Breaking change validation now loads attributes from normalized `entity_attributes` table via `entityAttributeService.getAttributesForEntities()` instead of reading from entity payload.
- Attributes passed to `entityValidationService.validateExistingEntitiesAgainstNewSchema()` via new `attributesByEntityId` parameter.

### 2025-07-17 — Readonly guards for integration-sourced entity types

- Added readonly guards on `saveAttributeDefinition` and `removeAttributeDefinition` to protect integration-sourced entity types.

### 2026-04-11 — Default value validation

- `saveAttributeDefinition` now validates `DefaultValue.Static` values against the attribute schema via SchemaService.
- `DefaultValue.Dynamic` defaults are not validated at definition time — they produce values at entity creation time.
