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
#EntityValidationService

Part of [[Validation]]

## Purpose

Schema validation for entity save operations and breaking change detection for entity type modifications.

---

## Responsibilities

- Validate entity payload against type schema
- Detect breaking schema changes (field removal, type changes, required additions)
- Validate existing entities against proposed schema changes
- Aggregate validation errors for better developer UX
- Track sample errors for impact analysis reporting

---

## Dependencies

- `SchemaService` — Core JSON schema validation engine
- `EntityRelationshipRepository` — Relationship entity validation (TODO)

## Used By

- [[EntityService]] — Validates entities during save operations
- [[EntityTypeAttributeService]] — Validates schema changes before applying

---

## Key Logic

**Entity validation:**

- Uses `SchemaService.validate()` with `ValidationScope.STRICT`
- Only validates ATTRIBUTE properties (relationships validated separately)
- Accepts optional `attributes: Map<UUID, EntityAttributePrimitivePayload>` parameter for pre-extracted attribute data
- Converts attributes map to `Map<String, Any?>` (UUID keys → string keys, payload values extracted) for schema engine
- Returns list of error strings (empty if valid)

**Breaking change detection:**

1. **Field removed:** Breaking if field was required
2. **Field type changed:** Always breaking (e.g., STRING → NUMBER)
3. **Required field added:** Breaking (no default value for existing entities)
4. **Required flag added:** Breaking (existing entities may have null)
5. **Unique flag added:** Breaking (existing entities may have duplicates)

TODO comments indicate future optimization: Query existing data to auto-apply non-breaking changes when safe.

**Bulk validation:**

- Validates list of entities against new schema
- Accepts optional `attributesByEntityId: Map<UUID, Map<UUID, EntityAttributePrimitivePayload>>` for pre-loaded attribute data
- Looks up each entity's attributes from the map (falling back to empty map if not present)
- Tracks valid count, invalid count
- Collects first 10 sample errors for UI display
- Returns `EntityTypeValidationSummary` for impact analysis

---

## Public Methods

### `validateEntity(entity, entityType, attributes?): List<String>`

Validates entity against its type schema using pre-extracted attributes map. The `attributes` parameter defaults to `emptyMap()` — callers must provide the attribute data from the normalized `entity_attributes` table.

### `validateRelationshipEntity(entityId, relationships): List<String>`

Validates relationship entity constraints (required relationships, type matching). Currently TODO/unimplemented.

### `detectSchemaBreakingChanges(oldSchema, newSchema): List<EntityTypeSchemaChange>`

Compares two schemas and identifies breaking changes. Each change includes type, path, description, and breaking flag.

### `validateExistingEntitiesAgainstNewSchema(entities, newSchema, attributesByEntityId?): EntityTypeValidationSummary`

Runs new schema against all existing entities to determine impact. Accepts optional `attributesByEntityId` map for pre-loaded attribute data from the normalized table. Returns summary with counts and sample errors.

---

## Gotchas

- **Aggregation over fail-fast:** Validates all fields and collects errors rather than stopping at first failure. Better UX for developers fixing validation issues.
- **Sample error limit:** Only captures first 10 errors to avoid memory issues with large entity sets
- **STRICT validation:** Uses `ValidationScope.STRICT` mode — all schema rules enforced
- **Future optimization:** TODO comments for smarter breaking change detection based on actual data (e.g., "required" addition is non-breaking if all entities already have values)
- **Relationship validation incomplete:** `validateRelationshipEntity()` method stubbed with TODO

---

## Related

- [[EntityService]] — Primary consumer for instance validation
- [[EntityTypeAttributeService]] — Uses for schema change validation
- [[Validation]] — Parent subdomain

---

## Changelog

### 2026-03-09 — Attributes parameter for normalized table support

- `validateEntity()` now accepts optional `attributes: Map<UUID, EntityAttributePrimitivePayload>` parameter. Validation reads from this map instead of entity payload.
- `validateExistingEntitiesAgainstNewSchema()` now accepts optional `attributesByEntityId` parameter for batch validation with pre-loaded attribute data.
