---
tags:
  - layer/model
  - component/active
  - architecture/component
Created: 2026-03-26
Domains:
  - "[[riven/docs/system-design/domains/Catalog/Catalog]]"
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
- Declare projection accept rules via `projectionAccepts: List<ProjectionAcceptRule>` — specifies which integration entities route to this core model based on (LifecycleDomain, SemanticGroup) pairs
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

- `CoreModelAttribute` -- attribute definition with `SchemaType`, `DataType`, format string, `SchemaOptions` (replacing `AttributeOptions`) for validation constraints and default values, and `AttributeSemantics` for semantic annotation
- `CoreModelRelationship` -- relationship definition with cardinality, source/target model keys, and `toNormalized()` conversion to `NormalizedRelationship`
- `CoreModelProjection` -- projection accept rules and aggregation column definitions for future-use projection routing

**Default value declarations:**

Core model attributes now use the `DefaultValue` sealed class for default values:
- `DefaultValue.Static("active")` — literal value injected when entity is created without this attribute
- `DefaultValue.Dynamic(DynamicDefaultFunction.CURRENT_DATE)` — resolved at entity creation time to the current date

These are declared via `SchemaOptions(defaultValue = ...)` on each `CoreModelAttribute`. Static defaults are validated against the attribute schema at definition time. Dynamic defaults skip literal validation and are resolved by `EntityService.resolveDefault()` at runtime.

**Projection accept rules:**

Each core model can declare `projectionAccepts` — a list of `ProjectionAcceptRule` entries specifying which integration entity types should project into this core model. Rules match on `(LifecycleDomain, SemanticGroup)` pairs, making them source-agnostic — the same rule applies regardless of which integration the data came from. During template materialization, [[riven/docs/system-design/domains/Integrations/Enablement/TemplateMaterializationService]] reads these rules and installs corresponding [[ProjectionRuleEntity]] rows. The `relationshipName` field (typically `"source-data"`) specifies the name of the relationship definition linking integration → core entity types.

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
- [[riven/docs/system-design/domains/Catalog/Manifest Pipeline/ManifestUpsertService]] -- downstream persistence layer that receives the resolved output
- [[EntityProjectionService]] — Consumes projection rules at runtime
- [[riven/docs/system-design/domains/Integrations/Enablement/TemplateMaterializationService]] — Installs projection rules from these declarations
- [[Core Model Definitions]] -- parent subdomain

---

## Changelog

### 2026-03-29

- Added `projectionAccepts` parameter — declares which integration entities route to each core model via (LifecycleDomain, SemanticGroup) pairs
- `ProjectionAcceptRule` data class defined alongside CoreModelDefinition: `domain`, `semanticGroup`, `relationshipName`, `autoCreate`

### 2026-04-11

- `CoreModelAttribute` now uses `SchemaOptions` (from `riven.core.models.common.validation`) instead of `AttributeOptions` for constraint and default value configuration
- Default values migrated to `DefaultValue` sealed class: `Static` for literal values, `Dynamic` for runtime-computed values (e.g. `CURRENT_DATE`, `CURRENT_DATETIME`)
- All lifecycle model definitions updated to use new import paths and typed defaults
