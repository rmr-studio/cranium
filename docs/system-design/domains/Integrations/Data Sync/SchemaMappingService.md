---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-18
Domains:
  - "[[Integrations]]"
---
# SchemaMappingService

Part of [[Data Sync]]

## Purpose

Generic mapping engine that transforms external JSON payloads into entity attribute payloads using declarative field mappings. Every sync from every integration passes through this service. It is resilient: partial failures produce partial results with structured health reports, not exceptions.

---

## Responsibilities

- Map external payloads to entity attributes using resolved field mapping definitions
- Apply four transform types: Direct, TypeCoercion, DefaultValue, and JsonPathExtraction
- Resolve target attribute string keys to workspace UUIDs via key mapping
- Extract values from nested payloads using dot-separated path traversal
- Coerce values between types (String, Number, Boolean, Date, Datetime)
- Provide default values when source fields are missing
- Extract nested values from map structures via JSON path
- Track field coverage (mapped/total ratio) for health reporting
- Isolate errors per field — one field failure does not prevent other fields from mapping
- Produce structured warnings (missing source fields) and errors (transform failures) for diagnostics

---

## Dependencies

- `KLogger` — Structured logging

## Used By

- Future sync processing services (Phase 3+) — Will call `mapPayload()` for each incoming record during data sync

---

## Key Logic

### Mapping flow

`mapPayload()` iterates all field mappings and processes each independently:

1. Resolve the target attribute UUID from `keyMapping` — error if not found
2. Extract the source value from the payload using `sourcePath` (supports dot-separated nested paths)
3. If source value is null and transform is not `DefaultValue`, check if the path exists — warning if missing
4. Apply the transform to produce the final value
5. Wrap in `EntityAttributePrimitivePayload` with the target `schemaType`

### Transform types

| Transform | Behavior |
|---|---|
| `Direct` | Passes the value through unchanged |
| `TypeCoercion` | Converts to target type: STRING, NUMBER, BOOLEAN, DATE, DATETIME. Validates format (ISO dates/datetimes). Errors on null or incompatible types |
| `DefaultValue` | Returns source value if present and non-null; falls back to configured default value |
| `JsonPathExtraction` | Extracts a nested value from a map using a dot-separated path. Produces warning (not error) if path segments are missing |

### Error isolation

Each field mapping is processed in isolation. Failures produce structured `MappingError` or `MappingWarning` records:

- **MappingError** — Transform failed (e.g. type coercion of incompatible value), key mapping not found
- **MappingWarning** — Source field not present in payload, nested path segment missing

Errors and warnings carry the `targetKey`, `sourcePath`, and descriptive message for diagnostics.

### Field coverage

The `MappingResult` includes a `FieldCoverage` with:

- `mapped` — Number of fields successfully mapped to attributes
- `total` — Total number of field mappings attempted
- `ratio` — Coverage ratio (0.0 to 1.0)

### Nested path extraction

Source paths support dot-separated segments (e.g. `"address.city"`) for traversing nested maps. Each segment is resolved against the current map level. Non-map intermediates or missing keys return null.

---

## Public Methods

### `mapPayload(externalPayload: Map<String, Any?>, fieldMappings: Map<String, ResolvedFieldMapping>, keyMapping: Map<String, UUID>): MappingResult`

Maps an external payload to entity attributes using field mappings and key mapping for UUID translation. Returns `MappingResult` with attributes, warnings, errors, and field coverage.

---

## Gotchas

> **No exceptions are thrown from `mapPayload()`.** All failures are captured as `MappingError` or `MappingWarning` entries in the result. Callers must check `errors` to detect failures.

> **TypeCoercion fails on null.** Coercing a null value throws a `TransformException` (captured as `MappingError`). Use `DefaultValue` transform if null-to-default is needed.

> **Date/Datetime coercion validates but preserves strings.** `LocalDate.parse()` and `OffsetDateTime.parse()` are called for validation, but the original string value is returned (not a parsed date object). Entity attributes store date values as ISO strings.

> **JsonPathExtraction treats missing paths as warnings, not errors.** Missing nested paths produce `MappingWarning` — the field is simply unmapped. Only non-map intermediates produce `MappingError`.

> **Internal exceptions are package-private.** `TransformException` and `MissingPathException` are private inner classes used for control flow within `processFieldMapping()`. They are never exposed to callers.

---

## Related

- `FieldTransform` — Sealed class defining the four transform types (Direct, TypeCoercion, DefaultValue, JsonPathExtraction)
- `ResolvedFieldMapping` — Resolved mapping with sourcePath, transform, targetSchemaType
- `MappingResult` — Result container with attributes, warnings, errors, coverage
- [[Data Sync]] — Parent subdomain

---

## Changelog

### 2026-03-18

- Initial implementation — mapping engine with 4 transform types, error isolation, field coverage reporting, and nested path extraction
