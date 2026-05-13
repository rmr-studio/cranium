---
tags:
  - adr/superseded
  - architecture/decision
Created: 2026-02-28
Updated: 2026-05-13
Superseded By: "[[architecture-pivot]]"
---

# ADR-004: Declarative-First Storage for Integration Mappings and Entity Templates

> **Superseded by the 2026-05 architecture pivot.** The manifest/catalog engine (`models/`, `templates/`, `integrations/`, `bundles/`, `ManifestLoaderService`, the dual Kotlin+JSON pipelines, `ManifestUpsertService`) is deleted in Phase 1c — GitHub-only v1 is one hardcoded scanner, not a manifest engine. Page-kind definitions (including each kind's synthesis contract) live in Kotlin as sealed `Page` subclasses, added via PR like a `CoreModelDefinition` — compile-time safety over contribution friction for rarely-changing core defs. The "core-models-kotlin-vs-json" call wins. See [[architecture-pivot]] §Reuse / Rework / Replace / Delete, [[ADR-011 Reuse Storage Spine, Rebuild Type and Projection Machinery]], [[ADR-018 One pages Table Family for Synthesis Storage]], and [[ADR-021 GitHub-Only v1, Slack and Notion as v1.1]].

---

## Context

The system requires two categories of reusable entity type definitions that must be stored statically and shared across workspaces:

1. **Data model templates** — pre-built entity type ecosystems (entity types, attributes, relationships, semantic metadata) that bootstrap a workspace for a specific business type (SaaS startup, DTC e-commerce, service business). Users select a template during workspace setup, and it gets synced into their environment where they can further edit and mould it as if they created it themselves. New templates will be continuously added to broaden coverage across business types.

2. **Integration entity type schemas and field mappings** — each integration source (HubSpot, Stripe, Zendesk, etc.) has its own set of entity models representing that tool's data inside the application (e.g., HubSpot Contact, Stripe Invoice, Zendesk Ticket). These are protected/readonly models used for data mapping during sync. Each integration also defines field-level mappings that tell the sync pipeline how to transform raw external payloads into entity attribute payloads.

Both categories share the same core requirement: definitions must be **statically stored**, **reusable across workspaces**, **version-controlled**, and **extensible by the community** without requiring deep application knowledge.

The existing architecture has an unresolved tension. [[2. Areas/2.1 Startup & Content/Cranium/2. System Design/decisions/ADR-001 Nango as Integration Infrastructure]] established that the integration catalog is database-stored to avoid redeployment when adding new integrations. But the [[cranium/docs/system-design/feature-design/5. Backlog/Integration Schema Mapping]] feature design implies per-integration code — each integration platform having "its own interface definition" for entity type templates and schema mapping guides. This effectively reintroduces the redeployment problem: every new integration or mapping change requires a code change, a build, and a release.

Additionally, the application is **self-hostable and open source**. This creates two specific extensibility requirements:

- **Community contributors** need to be able to add new integrations and templates without deep Kotlin/Spring Boot expertise
- **Self-hosters** need to be able to add custom integrations without forking the codebase

These requirements make the storage and packaging model a foundational architectural decision that shapes the entire contribution model.

---

## Decision

Adopt a **declarative-first storage model** with three layers:

### 1. Declarative Manifest Files (primary definition format)

All integration entity type schemas, field mappings, semantic metadata, and data model templates are defined as **JSON manifest files stored in the application repository**. These manifests are the single source of truth for what an integration or template contains.

The directory structure has three layers — **shared models**, **integration manifests**, and **template manifests**:

```
models/                          # shared entity type definitions (reusable across templates)
  customer.json                  # entity type schema + semantic metadata
  invoice.json
  communication.json
  subscription.json
  product.json
  support-ticket.json

integrations/                    # per-integration definitions
  hubspot/
    manifest.json                # entity types, field mappings, semantic metadata
  stripe/
    manifest.json
  zendesk/
    manifest.json

templates/                       # workspace bootstrapping bundles (compose from shared models)
  saas-startup/
    manifest.json                # references shared models + adds template-specific types
  dtc-ecommerce/
    manifest.json
  service-business/
    manifest.json
```

#### Shared Models (`models/`)

Common entity types that appear across multiple templates (e.g., Customer, Invoice, Communication) are defined once as **shared model files**. Each file is a self-contained entity type definition with attributes, validation rules, and semantic metadata.

Shared models are the base building blocks. They serve two purposes: they are referenced and composed by templates via `$ref`, and they are independently installable into workspaces as standalone entity types (see [[cranium/docs/system-design/feature-design/3. Active/Declarative Manifest Catalog and Consumption Pipeline]]). Each shared model is persisted in the manifest catalog on startup and available for workspace installation outside of template onboarding. Shared models do **not** declare relationships — they don't know what other models will be present in any given template. Relationships are declared at the composition layer (see below).

#### Template Composition via `$ref` with Merge

Templates declare their entity types as a mix of **references to shared models** (with optional overrides) and **template-specific inline definitions**:

```json
{
  "manifestVersion": "1.0",
  "key": "saas-startup",
  "name": "SaaS Startup",
  "entityTypes": [
    {
      "$ref": "models/customer",
      "extend": {
        "attributes": {
          "mrr": {
            "type": "number",
            "label": "Monthly Recurring Revenue",
            "semantics": {
              "definition": "Monthly recurring revenue in USD from this customer's active subscriptions",
              "classification": "quantitative"
            }
          }
        },
        "semantics": {
          "tags": ["saas", "revenue"]
        }
      }
    },
    { "$ref": "models/subscription" },
    { "$ref": "models/communication" },
    {
      "key": "churn-event",
      "name": "Churn Event",
      "attributes": {},
      "semantics": {}
    }
  ],
  "relationships": [
    {
      "key": "customer-to-subscription",
      "source": "customer",
      "target": "subscription",
      "name": "Subscriptions",
      "cardinality": "ONE_TO_MANY",
      "inverseVisible": true,
      "inverseName": "Customer",
      "icon": { "type": "CREDIT_CARD", "colour": "GREEN" },
      "semantics": {
        "definition": "Customer holds active subscriptions to the platform",
        "tags": ["revenue", "recurring"]
      }
    },
    {
      "key": "customer-financials",
      "source": "customer",
      "name": "Financial Records",
      "cardinalityDefault": "ONE_TO_MANY",
      "icon": { "type": "CREDIT_CARD", "colour": "GREEN" },
      "targetRules": [
        {
          "target": "subscription",
          "inverseVisible": true,
          "inverseName": "Customer"
        },
        {
          "target": "churn-event",
          "cardinalityOverride": "ONE_TO_MANY",
          "inverseVisible": true,
          "inverseName": "Churned Subscription"
        }
      ],
      "semantics": {
        "definition": "Customer's financial and lifecycle records",
        "tags": ["revenue", "lifecycle"]
      }
    },
    {
      "key": "subscription-to-churn",
      "source": "subscription",
      "target": "churn-event",
      "name": "Churn Events",
      "cardinality": "ONE_TO_MANY",
      "inverseVisible": true,
      "inverseName": "Subscription",
      "semantics": {
        "definition": "Subscription cancellation generates a churn event for analysis"
      }
    }
  ],
  "analyticalBriefs": [],
  "exampleQueries": []
}
```

**Merge semantics:**

- `$ref` resolves the shared model as the base definition
- `extend` performs a **shallow merge at each level**: top-level `attributes` are merged (new keys added, existing keys untouched), top-level `semantics` fields are merged (explicit values override, omitted values preserved from base)
- Attributes defined in `extend` are **additive** — they cannot remove or replace base attributes, only add new ones or override specific properties of existing ones
- If no `extend` is provided, the shared model is used as-is
- `$ref` without `extend` is the common case — most shared models are used verbatim

This keeps merge logic simple and predictable: base + additions, no deep recursive merge, no deletion semantics.

#### Integration Manifests

Integration manifests do **not** use `$ref` composition — each integration defines its own entity types inline because integration schemas represent a specific third-party tool's data model, not a shared business concept. HubSpot Contact and Salesforce Contact have structurally different field sets from their respective APIs.

Integration manifests **do** declare relationships between their own entity types (e.g., HubSpot Contact → HubSpot Deal) using the same `relationships` array format as templates. Relationships in integration manifests default to `protected: true` (system-managed, not user-deletable) since they represent the third-party platform's fixed data model. This default can be explicitly overridden with `"protected": false` if needed, but the common case is that integration relationships are structural and should not be modified by users.

Example integration manifest relationships (HubSpot):

```json
{
  "relationships": [
    {
      "key": "contact-to-company",
      "source": "hubspot-contact",
      "target": "hubspot-company",
      "name": "Company",
      "cardinality": "MANY_TO_ONE",
      "inverseVisible": true,
      "inverseName": "Contacts",
      "icon": { "type": "BUILDING_2", "colour": "BLUE" },
      "semantics": {
        "definition": "Contact is associated with a company in HubSpot",
        "tags": ["crm", "organization"]
      }
    },
    {
      "key": "contact-to-deal",
      "source": "hubspot-contact",
      "target": "hubspot-deal",
      "name": "Deals",
      "cardinality": "ONE_TO_MANY",
      "inverseVisible": true,
      "inverseName": "Contact",
      "icon": { "type": "HANDSHAKE", "colour": "GREEN" },
      "semantics": {
        "definition": "Contact is involved in one or more sales deals"
      }
    }
  ]
}
```

Note that `protected` is omitted — the manifest loader infers `true` from the `integrations/` directory context.

#### Relationship Ownership

Relationships are declared at the **composition layer** — the manifest that knows which entity types are present:

| Layer                                   | Declares relationships?                                          | Why                                                                                                                 |
| --------------------------------------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| Shared models (`models/`)               | No                                                               | Doesn't know what other models are present in the consuming template                                                |
| Template manifests (`templates/`)       | Yes — between all included entity types (both `$ref` and inline) | Knows the full composition; `source`/`target` reference entity type keys                                            |
| Integration manifests (`integrations/`) | Yes — between own entity types only                              | Knows its own model set                                                                                             |
| Runtime (identity resolution)           | Yes — cross-integration links                                    | Discovered dynamically via [[Connected Entities for READONLY Entity Types]], not statically declarable in manifests |

The `source` and `target` fields in a relationship reference entity type keys. The manifest loader validates that both ends of every declared relationship exist in the manifest's resolved entity type set. A relationship referencing a nonexistent key logs a warning and is skipped.

Cross-integration relationships (e.g., HubSpot Contact relates to Stripe Invoice) are **not** declared in manifests. These connections are discovered at runtime through identity resolution and handled by the catch-all connected entities mechanism, which does not require altering readonly schemas.

#### Relationship Manifest Reference

Relationships use a **dual-format** design — a shorthand for single-target definitions (most common) and a full format for multi-target or polymorphic definitions.

**Format detection:** Presence of `target` (string) → shorthand. Presence of `targetRules` (array) → full. Both present → validation error.

**Definition-level fields:**

| Field                | Shorthand | Full | Required       | Default                                        | Maps to                                                |
| -------------------- | :-------: | :--: | -------------- | ---------------------------------------------- | ------------------------------------------------------ |
| `key`                |     ✓     |  ✓   | Yes            | —                                              | Stable identifier for idempotent upsert                |
| `source`             |     ✓     |  ✓   | Yes            | —                                              | `sourceEntityTypeId` (resolved from entity type key)   |
| `name`               |     ✓     |  ✓   | No             | Auto-generated from target                     | `RelationshipDefinition.name`                          |
| `icon`               |     ✓     |  ✓   | No             | `{ "type": "LINK", "colour": "NEUTRAL" }`      | `iconType`, `iconColour`                               |
| `protected`          |     ✓     |  ✓   | No             | `false` for templates, `true` for integrations | `RelationshipDefinition.protected`                     |
| `cardinality`        |     ✓     |  —   | Shorthand: Yes | —                                              | `cardinalityDefault`                                   |
| `cardinalityDefault` |     —     |  ✓   | Full: Yes      | —                                              | `cardinalityDefault`                                   |
| `allowPolymorphic`   |     —     |  ✓   | No             | `false`                                        | `allowPolymorphic`                                     |
| `target`             |     ✓     |  —   | Shorthand: Yes | —                                              | Creates single `RelationshipTargetRule`                |
| `targetRules`        |     —     |  ✓   | Full: Yes      | —                                              | Creates multiple `RelationshipTargetRule` records      |
| `inverseVisible`     |     ✓     |  —   | No             | `false`                                        | Single rule's `inverseVisible`                         |
| `inverseName`        |     ✓     |  —   | No             | `null`                                         | Single rule's `inverseName`                            |
| `semantics`          |     ✓     |  ✓   | No             | —                                              | `EntityTypeSemanticMetadata` (targetType=RELATIONSHIP) |

**Target rule fields (full format `targetRules[]`):**

| Field                    | Required | Default | Maps to                                  |
| ------------------------ | -------- | ------- | ---------------------------------------- |
| `target`                 | Yes\*    | —       | `targetEntityTypeId` (resolved from key) |
| `semanticTypeConstraint` | No       | `null`  | `semanticTypeConstraint`                 |
| `cardinalityOverride`    | No       | `null`  | `cardinalityOverride`                    |
| `inverseVisible`         | No       | `false` | `inverseVisible`                         |
| `inverseName`            | No       | `null`  | `inverseName`                            |

\* Either `target` or `semanticTypeConstraint` must be specified.

**Semantics object:**

| Field        | Required | Maps to                                 |
| ------------ | -------- | --------------------------------------- |
| `definition` | No       | `EntityTypeSemanticMetadata.definition` |
| `tags`       | No       | `EntityTypeSemanticMetadata.tags`       |

`classification` is omitted — not applicable to relationships (only meaningful for attribute-level semantic metadata).

#### What a Manifest Defines

A manifest defines:

- **Entity type schemas** — attribute definitions, data types, validation rules (inline or via `$ref` to shared models with optional `extend`)
- **Field mappings** (integrations only) — source field to target attribute mappings with optional declarative transformations (type coercion, value mapping, default values, simple expressions)
- **Relationship definitions** — how entity types within the manifest connect to each other, using a dual-format schema (shorthand for single-target, full for multi-target/polymorphic). Definitions map 1:1 to `RelationshipDefinitionEntity` + `RelationshipTargetRuleEntity` records and support the full field set: name, icon, cardinality, inverse visibility, target rules, and semantic metadata. See **Relationship Manifest Reference** above for the complete schema.
- **Semantic metadata** — natural language definitions, attribute classifications, tags (per [[cranium/docs/system-design/feature-design/2. Planned/Semantic Metadata Foundation]])
- **Integration metadata** (integrations only) — which Nango provider key to use, sync direction, supported models

### 2. Database as Runtime Store

On application startup, a **manifest loader** scans the manifest directories, validates each manifest against a JSON Schema, and upserts the definitions into the database. The database is the runtime query surface — application code reads definitions from the database, not from the filesystem at request time.

This preserves the existing pattern established in [[cranium/docs/system-design/feature-design/3. Active/Integration Access Layer]] where integration definitions are queryable via standard JPA repositories. The manifests simply replace the Flyway seed migrations (V005) as the source of catalog data.

**Loading behavior:**

- Manifests are loaded on every application startup (idempotent upsert)
- Definitions from manifests are marked with a `source = 'MANIFEST'` discriminator
- The loader validates manifest structure before writing to the database
- Invalid manifests log a warning and are skipped — they do not prevent application startup
- Manifest-sourced definitions are treated as the canonical version — if a manifest changes between deployments, the database is updated to match

### 3. Generic Mapping Engine (code, deployed once)

A single **generic mapping engine** in code interprets the declarative field mappings at runtime. This engine handles:

- Simple field-to-field mapping (`source.email` → `target.email_address`)
- Type coercion (`string` → `number`, date format conversion)
- Value mapping (enum translation: HubSpot's `"subscriber"` → `"active"`)
- JSONPath extraction for nested source payloads
- Default values for missing fields
- Conditional mapping (if source field exists, map it; otherwise skip)

**No per-integration Kotlin classes are written for standard field mappings.** The generic engine + declarative manifest handles the vast majority of integration scenarios.

**Custom transformation plugins** are only written when an integration has behavior that genuinely cannot be expressed declaratively — for example, a transformation that requires calling an external API, performing complex computation, or handling a deeply non-standard payload structure. These are the exception, not the rule. When needed, they are registered as named transforms that manifests can reference:

```json
{
  "field": "deal_amount",
  "source": "properties.amount",
  "transform": { "type": "plugin", "name": "hubspot-currency-converter" }
}
```

---

## Rationale

- **Community contributions become JSON PRs, not Kotlin PRs.** A contributor adding a new integration writes a manifest file describing entity types and field mappings. They do not need to understand Spring Boot, JPA, or the service layer. The barrier drops from "can write Kotlin" to "can write JSON."
- **Self-hosters extend without forking.** A self-hoster drops a manifest file into the integrations directory and restarts. No code changes, no build toolchain, no merge conflicts with upstream.
- **Version control and review are natural.** Manifests are files in the repo — they go through standard Git workflow, PR review, and CI validation. Changes are diffable and auditable.
- **The redeployment problem is solved for definitions.** Adding a new integration or template requires adding a manifest file and restarting, not a code change and rebuild. The code (mapping engine) changes rarely; the data (manifests) changes frequently.
- **Startup validation catches errors early.** JSON Schema validation on startup ensures manifests are structurally valid before they reach the database. This replaces compile-time type safety with boot-time validation — a reasonable tradeoff given that manifests are validated in CI as well.
- **The database remains the runtime query surface.** Application code queries definitions via JPA repositories, matching the existing pattern. The manifest files are a packaging/authoring concern, not a runtime concern.

---

## Alternatives Considered

### Option 1: Pure In-Code (class per integration/template)

Each integration and template is defined as a Kotlin class implementing an interface (e.g., `IntegrationDefinitionProvider`, `TemplateProvider`). Entity type schemas, field mappings, and semantic metadata are expressed as code.

- **Pros:** Compile-time type safety. IDE support (autocomplete, refactoring). Mapping logic can use arbitrary Kotlin expressions. Testable with standard unit testing.
- **Cons:** Every new integration or template requires a code change, build, and deployment. Community contributors must understand Kotlin and the application's service layer. Self-hosters cannot extend without forking. The number of classes grows linearly with integrations — at 50+ integrations, the codebase becomes cluttered with near-identical boilerplate differing only in field names.
- **Why rejected:** The contribution model does not scale. The redeployment requirement directly contradicts the earlier decision (in [[2. Areas/2.1 Startup & Content/Cranium/2. System Design/decisions/ADR-001 Nango as Integration Infrastructure]]) to avoid per-integration code for commodity concerns. Field mappings are data, not behavior — expressing them as code is over-engineering.

### Option 2: Database-Only with SQL Seed Migrations

Definitions exist only as Flyway seed migration scripts. No manifest files on the filesystem. Community contributions are SQL INSERT scripts.

- **Pros:** Definitions live in the database from the start — no loader needed. Flyway ordering guarantees migration sequence. Matches the existing V005 seed pattern.
- **Cons:** SQL is a poor authoring format for complex nested structures (entity types with attributes with semantic metadata). Definitions are buried in migration scripts and hard to browse or diff as a whole. Community contributors must write SQL — higher barrier than JSON. Self-hosters must write SQL to add custom integrations. No way to "see" the full definition of an integration without reading across multiple migration files.
- **Why rejected:** Migration scripts are an implementation mechanism, not an authoring format. JSON manifests are human-readable, self-documenting, and diffable. The database is the right runtime store but the wrong authoring surface.

### Option 3: Runtime Admin API for Custom Definitions

Expose REST endpoints that allow self-hosters to register custom integrations and templates at runtime without touching the filesystem or writing SQL.

- **Pros:** Maximum flexibility for self-hosters. No restart required to add an integration. Could support a future "marketplace" of community integrations.
- **Cons:** Adds significant API surface (CRUD for definitions, mappings, templates). Requires authorization model for who can modify global definitions. Introduces state that is not version-controlled — definitions in the database diverge from what shipped with the application. Complicates upgrades (what happens when a new version ships a manifest that conflicts with a runtime-defined custom integration?).
- **Why rejected:** Deferred, not rejected permanently. The manifest-first approach solves the immediate extensibility needs. A runtime API can layer on top of it in a future phase if the community demands it. Building it now would be premature — the manifest format needs to stabilize first.

---

## Consequences

### Positive

- Community contributions for new integrations and templates are JSON file PRs — dramatically lower barrier than Kotlin code changes
- Self-hosters add custom integrations by dropping manifest files into the directory and restarting — no fork required
- New integrations and templates do not require code changes in most cases — only a manifest file addition
- Manifest files are version-controlled, diffable, and reviewable through standard Git workflows
- The generic mapping engine is written and tested once, then reused across all integrations — reduced maintenance surface
- Boot-time validation catches malformed manifests before they reach the database
- Existing database-backed query patterns ([[cranium/docs/system-design/feature-design/3. Active/Integration Access Layer]] JPA repositories) are preserved — the manifest loader is a write path change, not a read path change

### Negative

- No compile-time type safety for mapping definitions — a malformed manifest is caught at startup or CI time, not at compile time. Mitigated by JSON Schema validation and CI checks on manifest files.
- The generic mapping engine must be expressive enough to handle the majority of integration field mapping patterns. If the declarative format proves too limited, more integrations will require custom plugins, partially negating the benefit. Mitigated by designing the mapping DSL with sufficient expressiveness upfront (JSONPath, type coercion, value mapping, conditionals).
- Startup time increases proportionally with the number of manifests — each must be read, validated, and upserted. At the current scale (6-20 integrations, 3-5 templates) this is negligible. At 100+ integrations, the loader may need optimization (checksum-based skip for unchanged manifests).
- Manifest format becomes a de facto public API — breaking changes to the manifest schema affect all community-contributed manifests. Mitigated by versioning the manifest schema (`"manifestVersion": "1.0"`) and providing migration tooling if the schema evolves.

### Neutral

- The existing V005 seed migration for integration definitions is superseded by the manifest loader. V005 can be retained as a no-op migration for existing deployments or removed in a future cleanup.
- Integration entity type schemas defined in manifests follow the same structural conventions as user-created entity types — they use the same attribute definition format, relationship definition format, and semantic metadata shape. The only difference is the `readonly` flag and the `source` discriminator.
- Custom transformation plugins follow a registry pattern — they are registered by name in the application and referenced by name in manifests. This is analogous to the existing [[cranium/docs/system-design/domains/Workflows/Node Execution/WorkflowNodeConfigRegistry]] pattern used for workflow node types.

---

## Implementation Notes

- **Manifest JSON Schema:** Define a JSON Schema for both integration manifests and template manifests. Publish the schema in the repository (e.g., `schemas/integration-manifest.schema.json`) so contributors can validate locally before submitting PRs. The schema covers entity type definitions, field mappings, relationship definitions, semantic metadata, and integration-specific configuration.
- **Manifest Loader Service:** A Spring `@Component` that runs on application startup (via `@EventListener(ApplicationReadyEvent::class)` or `ApplicationRunner`). Scans the manifest directories, validates each file against the JSON Schema, and upserts definitions into the database. Uses `source = 'MANIFEST'` to distinguish manifest-loaded definitions from any future runtime-defined definitions. For template manifests, the loader resolves `$ref` references against shared model files first, applies `extend` merges, then writes the fully resolved entity type definitions to the database. Shared models are persisted in the manifest catalog as first-class entries (type = `MODEL`) alongside templates and integrations. During template loading, the loader still reads models into an in-memory lookup map for `$ref` resolution (the resolution is a load-time operation, not a runtime DB query). Template `$ref` resolution produces fully resolved entity types stored as separate rows under the template's catalog entry. A model may therefore appear in the catalog multiple times: once as its standalone MODEL entry, and once (potentially with `extend` overrides) under each template that references it. See [[cranium/docs/system-design/feature-design/3. Active/Declarative Manifest Catalog and Consumption Pipeline]] for the full catalog storage model and consumption pipeline.
- **Shared Model Resolution:** The loader reads `models/` first and holds them in memory as a lookup map keyed by model slug (filename without extension). When processing a template manifest, each `$ref` entry is resolved against this map. If a `$ref` references a model that does not exist, the loader logs a warning and skips that entity type. The `extend` merge applies shallow property merging: `extend.attributes` keys are merged into the base attributes map, `extend.semantics` fields override corresponding base fields. No deep recursive merge — each merge point is one level deep.
- **Generic Mapping Engine:** A stateless service that accepts a raw external payload (JSON) and a field mapping definition (from the manifest) and produces an entity attribute payload. The engine applies mappings sequentially: extract source value → apply transform → validate type → assign to target attribute. Transformations are a sealed class hierarchy: `DirectMapping`, `TypeCoercion`, `ValueMapping`, `JsonPathExtraction`, `DefaultValue`, `Conditional`, `PluginTransform`.
- **Custom Plugin Registry:** A Spring bean registry where custom transformation plugins register themselves by name. Manifests reference plugins by name string. If a manifest references an unregistered plugin, the manifest loader logs a warning and skips that specific mapping (not the entire manifest).
- **Relationship Loading:** The manifest loader processes relationships after entity types are resolved, using the following pipeline:
  - **Format normalization:** Shorthand relationships are normalized to the full internal format before processing. A shorthand definition with `target`, `cardinality`, `inverseVisible`, and `inverseName` is converted to a full definition with `cardinalityDefault` and a single-element `targetRules` array. This ensures the loader has a single code path for all relationship formats.
  - **Key-based upsert:** Relationships are matched by `(manifest_key, relationship_key)` for idempotent loading. On startup, existing manifest-sourced relationships not present in the current manifest are removed.
  - **Target resolution:** `source` and `target`/`targetRules[].target` values are entity type keys resolved against the manifest's resolved entity type set. Resolution happens after all entity types (both `$ref` and inline) have been processed.
  - **Semantic metadata creation:** Each relationship with a `semantics` object generates an `EntityTypeSemanticMetadata` record with `targetType = RELATIONSHIP` and `targetId` set to the relationship definition's ID.
  - **Default inference:** If `name` is omitted in shorthand format, derive from the target entity type's display name (e.g., target key `subscription` → name `Subscriptions`). In full format, `name` should be explicitly provided since the definition spans multiple targets.
  - **Protected default:** Relationships in `integrations/` directory manifests default to `protected: true`. Relationships in `templates/` directory manifests default to `protected: false`. Explicit `protected` values in the manifest override the directory-based default.
- **Relationship Validation:** The loader validates relationships before writing to the database:
  1. `source` references an entity type key present in the manifest's resolved entity type set
  2. `target` (shorthand) or each `targetRules[].target` references an entity type key present in the manifest
  3. `cardinality` (shorthand) or `cardinalityDefault` (full) is a valid `EntityRelationshipCardinality` enum value (`ONE_TO_ONE`, `ONE_TO_MANY`, `MANY_TO_ONE`, `MANY_TO_MANY`)
  4. No duplicate `key` values across relationships within the same manifest
  5. Shorthand and full format are mutually exclusive — a relationship cannot specify both `target` and `targetRules`
  6. Each target rule must specify at least one of `target` or `semanticTypeConstraint`
  7. `icon.type` and `icon.colour` must be valid `IconType` and `IconColour` enum values if specified
  8. Invalid relationships log a warning and are skipped — they do not prevent the rest of the manifest from loading
- **CI Validation:** A CI step validates all manifest files against the JSON Schema before merge. This provides the "compile-time" safety equivalent for declarative definitions.
- **Migration from V005:** The existing V005 seed migration data for the 6 initial integrations (HubSpot, Salesforce, Stripe, Zendesk, Intercom, Gmail) should be converted to manifest files. V005 itself can be left as a no-op or retained for backward compatibility with existing database state.

---

## Related

- [[cranium/docs/system-design/feature-design/5. Backlog/Integration Schema Mapping]]
- [[cranium/docs/system-design/feature-design/4. Completed/Predefined Integration Entity Types]]
- [[cranium/docs/system-design/feature-design/4. Completed/Semantic Metadata Baked Entity Data Model Templates]]
- [[cranium/docs/system-design/feature-design/_Sub-Domain Plans/Entity Integration Sync]]
- [[cranium/docs/system-design/feature-design/3. Active/Integration Access Layer]]
- [[2. Areas/2.1 Startup & Content/Cranium/2. System Design/decisions/ADR-001 Nango as Integration Infrastructure]]
- [[cranium/docs/system-design/feature-design/2. Planned/Semantic Metadata Foundation]]
- [[cranium/docs/system-design/domains/Workflows/Node Execution/WorkflowNodeConfigRegistry]]
- [[cranium/docs/system-design/feature-design/3. Active/Declarative Manifest Catalog and Consumption Pipeline]]
