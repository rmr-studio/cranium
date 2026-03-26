---
tags:
  - layer/model
  - component/active
  - architecture/component
Created: 2026-03-26
Domains:
  - "[[Catalog]]"
---
# CoreModelDefinition

Part of [[Core Model Definitions]]

## Purpose

Abstract base class for all core lifecycle model definitions. Each business-type model (SaaS Customer, DTC Order, etc.) extends this class as a Kotlin `object` singleton, defining the full schema for a single entity type: key, display names, icon, semantic group, lifecycle domain, identifier key, attributes, relationships, projection rules, and aggregation columns.

---

## Responsibilities

- Define the canonical schema for a single core entity type as a compile-time Kotlin object
- Declare typed attributes via `Map<String, CoreModelAttribute>` with schema type, data type, format, constraints, and semantic annotations
- Declare relationships via `List<CoreModelRelationship>` with cardinality and source/target model keys
- Declare optional projection accept rules and aggregation column definitions via `CoreModelProjection`
- Convert to `ResolvedEntityType` for consumption by the manifest catalog pipeline

---

## Dependencies

None. Core model definitions are pure Kotlin objects with no Spring injection or runtime dependencies.

## Used By

- [[CoreModelRegistry]] -- collects and validates all model definitions
- All model implementations in `lifecycle/models/` (e.g. B2C SaaS models, DTC E-commerce models)

---

## Key Logic

**Kotlin object singletons:**

Each concrete model is a Kotlin `object` extending `CoreModelDefinition`. Properties are declared inline as constructor arguments or overridden vals. Shared attributes across related models come from base objects (e.g. `CustomerBase`, `BillingEventBase`) composed via Kotlin `Map` `+` operator -- composition over inheritance.

**Supporting data types (defined alongside CoreModelDefinition):**

- `CoreModelAttribute` -- attribute definition with `SchemaType`, `DataType`, format string, constraints map, and `AttributeSemantics` for semantic annotation
- `CoreModelRelationship` -- relationship definition with cardinality, source/target model keys, and `toNormalized()` conversion to `NormalizedRelationship`
- `CoreModelProjection` -- projection accept rules and aggregation column definitions for future-use projection routing

---

## Public Methods

### `toResolvedEntityType(): ResolvedEntityType`

Converts the Kotlin definition to a `ResolvedEntityType` suitable for the manifest catalog pipeline. Sets `protected = true` on all core models, preventing user modification of the schema after catalog installation.

### `toNormalizedRelationships(): List<NormalizedRelationship>`

Converts the model's relationship definitions to `NormalizedRelationship` objects for relationship resolution during catalog population.

---

## Gotchas

- **Not Spring beans.** These are pure Kotlin `object` singletons. No dependency injection, no runtime instantiation, no lifecycle management by the Spring container.
- **Attribute key uniqueness.** The attributes map uses string keys that become the attribute `key` in the catalog. These must be unique within a single model definition.
- **Protected flag.** `toResolvedEntityType()` sets `protected = true` on all core models. This prevents users from modifying the schema of installed core entity types through the entity type management API.
- **Composition over inheritance.** Shared attributes come from base objects via Kotlin `Map` `+` operator, not from a class hierarchy. This keeps each model definition self-contained and explicit about its full attribute set.

---

## Related

- [[CoreModelRegistry]] -- registry that collects and validates all definitions
- [[CoreModelCatalogService]] -- service that triggers catalog population from these definitions
- [[ManifestUpsertService]] -- downstream persistence layer that receives the resolved output
- [[Core Model Definitions]] -- parent subdomain
