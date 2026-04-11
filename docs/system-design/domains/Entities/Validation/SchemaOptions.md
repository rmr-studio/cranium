---
tags:
  - layer/model
  - component/active
  - architecture/component
Created: 2026-04-11
Domains:
  - "[[Entities]]"
---
# SchemaOptions

Part of [[Validation]]

## Purpose

Data class holding attribute-level configuration options shared across core model definitions (lifecycle manifests) and runtime entity type schemas. Carried unchanged from manifest definitions into persisted entity type schemas during template installation. Replaces the previous `AttributeOptions` class used by lifecycle models with a unified structure that supports both static and dynamic default values.

---

## Responsibilities

- Store optional validation constraints for entity type attributes: regex, enum lists, length ranges, numeric ranges, date ranges
- Store optional default value configuration via [[DefaultValue]] (supports both literal and dynamic defaults)
- Store optional ID prefix for `SchemaType.ID` attribute sequences
- Store enum sorting preference via `OptionSortingType`
- Provide `extractStaticDefault()` helper for validation code that needs only the literal default value

---

## Dependencies

- [[DefaultValue]] -- sealed interface for static and dynamic default values
- `OptionSortingType` -- enum for enum option sort order

## Used By

- Entity type schema properties -- each attribute in an `EntityTypeSchema` can reference a `SchemaOptions` instance
- Core model manifest definitions -- lifecycle model attributes carry `SchemaOptions` through the catalog pipeline
- [[EntityValidationService]] -- reads `SchemaOptions` fields to validate entity attribute values against constraints
- [[SchemaMappingService]] -- reads field mappings that reference schema types paired with these options

---

## Key Logic

**Fields:**

| Field | Type | Purpose |
| ----- | ---- | ------- |
| `defaultValue` | `DefaultValue?` | Static literal or dynamic function default; null means no default |
| `prefix` | `String?` | Prefix for `SchemaType.ID` auto-incrementing sequences (e.g. `"PROJ-"`) |
| `regex` | `String?` | Regex pattern the attribute value must match |
| `enum` | `List<String>?` | Exhaustive list of allowed string values |
| `enumSorting` | `OptionSortingType?` | Sort order for enum options in the UI |
| `minLength` | `Int?` | Minimum string length |
| `maxLength` | `Int?` | Maximum string length |
| `minimum` | `Double?` | Minimum numeric value |
| `maximum` | `Double?` | Maximum numeric value |
| `minDate` | `ZonedDateTime?` | Earliest allowed date/datetime |
| `maxDate` | `ZonedDateTime?` | Latest allowed date/datetime |

**`extractStaticDefault()` helper:**

Returns the literal value from `DefaultValue.Static`, or `null` for `DefaultValue.Dynamic` and when no default is configured. Used by validation logic that needs to check the default value against type constraints without handling dynamic resolution.

---

## Public Methods

### `extractStaticDefault(): Any?`

Extracts the static default value for validation purposes. Returns the literal `value` from `DefaultValue.Static`; returns `null` for `DefaultValue.Dynamic` (validated via enum, not value) or when `defaultValue` is null.

---

## Gotchas

- **Replaces `AttributeOptions`:** Code referencing the old `AttributeOptions` class should be migrated to `SchemaOptions`. The two are not interchangeable -- `SchemaOptions` adds `defaultValue` as a `DefaultValue` sealed type rather than a raw `Any?`.
- **`extractStaticDefault()` intentionally ignores dynamic defaults:** Dynamic defaults are validated by checking the `DynamicDefaultFunction` enum value, not by extracting a literal. Callers that need to handle dynamic defaults must pattern-match on `defaultValue` directly.
- **Nullable `ZonedDateTime` fields serialize to JSONB:** `minDate` and `maxDate` are `ZonedDateTime?` which Jackson serializes as ISO-8601 strings in JSONB. Deserialization requires `JavaTimeModule` to be registered on the `ObjectMapper`.
- **All fields are nullable with null defaults:** A `SchemaOptions()` with no arguments is valid and represents an attribute with no constraints and no default value. This is the common case for integration-sourced attributes.

---

## Related

- [[DefaultValue]] -- polymorphic default value type stored in `defaultValue` field
- [[DynamicDefaultFunction]] -- enum of dynamic default functions referenced by `DefaultValue.Dynamic`
- [[EntityValidationService]] -- consumes `SchemaOptions` for attribute validation
- [[Validation]] -- parent subdomain
