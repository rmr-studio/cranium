---
tags:
  - layer/model
  - component/active
  - architecture/component
Created: 2026-04-11
Domains:
  - "[[Entities]]"
---
# DefaultValue

Part of [[Validation]]

## Purpose

Sealed interface representing polymorphic attribute default values. Stored as a field on [[SchemaOptions]] and serialized to/from JSON using Jackson's `@JsonTypeInfo`/`@JsonSubTypes` discriminated union pattern. Supports two resolution strategies: literal values baked into the schema and dynamic values computed at entity creation time.

---

## Responsibilities

- Define the type hierarchy for attribute default values with Jackson polymorphic deserialization
- `Static(value: JsonValue)` -- a literal value injected as-is when an entity is created without an explicit value for the attribute
- `Dynamic(function: DynamicDefaultFunction)` -- a named function evaluated at entity creation time to produce the default value
- Provide an extensible sealed hierarchy for future default strategies (formulas, lookups, aggregations) without changing `SchemaOptions` or existing serialization

---

## Dependencies

- `DynamicDefaultFunction` -- enum of supported dynamic default functions (e.g. `CURRENT_DATE`, `CURRENT_DATETIME`)
- `JsonValue` -- type alias for `Any` used in JSON attribute values

## Used By

- [[SchemaOptions]] -- holds `defaultValue: DefaultValue?` as an optional attribute configuration field
- [[EntityValidationService]] -- reads `SchemaOptions.extractStaticDefault()` to validate static defaults against schema type constraints

---

## Key Logic

**Jackson polymorphic serialization:**

The `type` discriminator property determines which subtype is deserialized:

- `{"type": "static", "value": "some literal"}` deserializes to `DefaultValue.Static`
- `{"type": "dynamic", "function": "CURRENT_DATE"}` deserializes to `DefaultValue.Dynamic`

This allows `SchemaOptions` to be persisted as JSONB in PostgreSQL with the default value structure preserved across serialization boundaries.

---

## Public Methods

### `Static(value: JsonValue)`

Data class holding a literal default value. The `value` field accepts any JSON-serializable type (`String`, `Number`, `Boolean`, `List`, `Map`).

### `Dynamic(function: DynamicDefaultFunction)`

Data class holding a reference to a [[DynamicDefaultFunction]] enum value. The function is evaluated at entity creation time, not at schema definition time.

---

## Gotchas

- **`Static.value` is `JsonValue` (alias for `Any`):** There is no compile-time type safety on the literal value. Validation that the static default matches the attribute's `SchemaType` is performed by [[EntityValidationService]], not by this class.
- **Dynamic defaults are not validated at schema save time:** A `Dynamic` default only validates that the function enum value is recognized. Whether the function's output type is compatible with the attribute's schema type is checked at entity creation time.

---

## Related

- [[SchemaOptions]] -- parent configuration object that holds `DefaultValue`
- [[DynamicDefaultFunction]] -- enum of supported dynamic default functions
- [[EntityValidationService]] -- validates default values against schema type constraints
- [[Validation]] -- parent subdomain
