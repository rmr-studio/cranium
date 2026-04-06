---
tags:
  - layer/utility
  - component/active
  - architecture/component
Created: 2026-03-26
Domains:
  - "[[Catalog]]"
---
# CoreModelRegistry

Part of [[Core Model Definitions]]

## Purpose

Kotlin `object` singleton that serves as the central registry for all core model sets. Collects all `CoreModelSet` instances (e.g. `B2C_SAAS_MODELS`, `DTC_ECOMMERCE_MODELS`), validates cross-references at first access with fail-fast semantics, and converts model sets to `ResolvedManifest` for catalog population.

---

## Responsibilities

- Maintain the canonical list of all registered `CoreModelSet` instances
- Validate all models lazily on first access -- check for duplicate keys, invalid relationship cross-references, and missing target models
- Provide lookup methods for individual model sets and model definitions by key
- Convert model sets to `ResolvedManifest` objects for consumption by [[ManifestUpsertService]]

---

## Dependencies

None. This is a static Kotlin `object` with no Spring injection or runtime dependencies.

## Used By

- [[CoreModelCatalogService]] -- iterates over all resolved manifests at boot time
- `BusinessType` enum -- resolves `templateKey` to a model set via `findModelSet()`

---

## Key Logic

**Lazy validation:**

The `allModels` property is computed lazily on first access. It flattens all models from all registered model sets and runs `validate()`. If any model has duplicate keys, invalid relationship references, or missing target models, validation fails immediately with an `IllegalStateException`. This ensures errors surface at boot time when [[CoreModelCatalogService]] triggers first access, rather than silently producing corrupt catalog data.

**Projection routing:**

`findModelsAccepting()` iterates all registered models and filters their `projectionAccepts` lists for matching (domain, group) pairs. This enables source-agnostic routing — when an integration entity type with `lifecycleDomain=BILLING` and `semanticGroup=TRANSACTION` is materialized, the registry finds all core models that accept that combination (e.g., OrderModel, SubscriptionModel).

**Model set structure:**

A `CoreModelSet` groups related [[CoreModelDefinition]] instances with additional cross-model relationships that span multiple entity types within the set. Each model set declares a `manifestKey` which becomes the `key` in `manifest_catalog` -- this is also what `BusinessType.templateKey` resolves to.

---

## Public Methods

### `findModelSet(manifestKey: String): CoreModelSet?`

Lookup a registered model set by its manifest key. Returns `null` if no set matches.

### `findModel(manifestKey: String, modelKey: String): CoreModelDefinition?`

Lookup a specific model definition within a model set by manifest key and model key. Returns `null` if either the set or model is not found.

### `toResolvedManifest(modelSet: CoreModelSet): ResolvedManifest`

Convert a single model set to a `ResolvedManifest` containing all resolved entity types, relationships, and metadata for catalog persistence.

### `allResolvedManifests(): List<ResolvedManifest>`

Convert all registered model sets to resolved manifests. This is the primary entry point used by [[CoreModelCatalogService]] during boot-time catalog population.

### `findModelsAccepting(domain: LifecycleDomain, group: SemanticGroup): List<Pair<CoreModelDefinition, ProjectionAcceptRule>>`

Find all core models whose `projectionAccepts` includes a rule matching the given (domain, group) pair. Returns the matching models along with the specific accept rule that matched. Used by [[TemplateMaterializationService]] during projection rule installation.

### `validate()`

Check all registered models for duplicate keys, invalid relationship references, and missing target models. Called lazily on first access to `allModels`. Throws `IllegalStateException` on validation failure.

---

## Gotchas

- **Validation is lazy.** Runs on first access to `allModels`, not at class loading time. If a model has invalid cross-references, the error surfaces at boot time when [[CoreModelCatalogService]] triggers access -- not earlier.
- **Cross-model relationships are validated separately.** `CoreModelSet` can declare relationships that span multiple entity types within the set. These are validated at the set level, not at the individual model level.
- **Manifest key mapping.** Model sets define `manifestKey` which becomes the `key` in `manifest_catalog`. This is the same key that `BusinessType.templateKey` resolves to, creating the link between the business type enum and the catalog entry.

---

## Related

- [[CoreModelDefinition]] -- the base class for individual model definitions
- [[CoreModelCatalogService]] -- the Spring service that consumes this registry at boot time
- [[ManifestUpsertService]] -- downstream persistence for resolved manifests
- [[TemplateMaterializationService]] — finds target core models for projection rule installation via `findModelsAccepting()`
- [[Core Model Definitions]] -- parent subdomain
