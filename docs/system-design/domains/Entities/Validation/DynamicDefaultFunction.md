---
tags:
  - layer/model
  - component/active
  - architecture/component
Created: 2026-04-11
Domains:
  - "[[Entities]]"
---
# DynamicDefaultFunction

Part of [[Validation]]

## Purpose

Enum defining the set of named functions that produce default attribute values at entity instance creation time. Each value maps to a deterministic computation evaluated when a new entity is saved without an explicit value for the attribute. Referenced by [[DefaultValue|DefaultValue.Dynamic]] to specify which function to invoke.

---

## Responsibilities

- Enumerate all supported dynamic default computation functions
- Provide stable JSON serialization names via `@JsonProperty` for persistence in JSONB `SchemaOptions`
- Serve as the exhaustive set for `when` expressions that evaluate dynamic defaults at entity creation time

---

## Dependencies

None. This is a leaf enum with no dependencies beyond Jackson annotations.

## Used By

- [[DefaultValue|DefaultValue.Dynamic]] -- holds a `function: DynamicDefaultFunction` field that references one of these enum values
- [[SchemaOptions]] -- indirectly, via the `defaultValue: DefaultValue?` field

---

## Key Logic

**Enum values:**

| Value | JSON Property | Output |
| ----- | ------------- | ------ |
| `CURRENT_DATE` | `"CURRENT_DATE"` | ISO-8601 date at creation time (e.g. `2026-04-11`) |
| `CURRENT_DATETIME` | `"CURRENT_DATETIME"` | ISO-8601 datetime at creation time |

Both functions produce deterministic output relative to the moment the entity instance is created. They are not re-evaluated on entity updates.

---

## Public Methods

No methods -- enum values only.

---

## Gotchas

- **Output type must match the attribute's SchemaType:** `CURRENT_DATE` produces a date string and is intended for `SchemaType.DATE` attributes; `CURRENT_DATETIME` produces a datetime string for `SchemaType.DATETIME` attributes. Mismatches are caught at entity creation time by validation, not at schema definition time.
- **New entries require evaluation logic:** Adding a new enum value here also requires adding the corresponding evaluation branch wherever dynamic defaults are resolved at entity creation time.

---

## Related

- [[DefaultValue]] -- sealed interface that references this enum
- [[SchemaOptions]] -- attribute configuration holding default values
- [[Validation]] -- parent subdomain
