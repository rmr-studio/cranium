# Manifest Authoring Guide

## Overview

Manifests are declarative JSON files that define entity types, relationships, and field mappings for the Cranium platform. They are loaded into the manifest catalog on every application startup and serve as blueprints for workspace data models.

There are three manifest types:

- **Model** -- A single reusable entity type definition (e.g., `customer.json`). Models are the atomic building blocks that templates compose.
- **Template** -- A workspace bootstrapping bundle that assembles entity types (via `$ref` to models or inline definitions) and defines relationships between them.
- **Integration** -- Similar to a template but represents an external data source (HubSpot, Salesforce, etc.) with optional field mappings. Integration entity types are typically read-only.

## Directory Structure

```text
src/main/resources/manifests/
├── schemas/                         # JSON Schema validation files
│   ├── model.schema.json
│   ├── template.schema.json
│   └── integration.schema.json
├── models/                          # Shared entity type definitions (flat files)
│   ├── customer.json
│   └── product.json
├── templates/                       # Workspace bootstrapping bundles (directory per template)
│   └── saas-startup/
│       └── manifest.json
├── integrations/                    # Per-integration definitions (directory per integration)
│   └── hubspot/
│       └── manifest.json
└── README.md                        # This file
```

- **Models:** Flat files in `models/`. Key is derived from the filename without extension (e.g., `customer.json` has key `customer`).
- **Templates:** One directory per template in `templates/`. Key is derived from the directory name. Each contains a `manifest.json`.
- **Integrations:** One directory per integration in `integrations/`. Key is derived from the directory name and must match the corresponding `integration_definitions.slug`. Each contains a `manifest.json`.

## Key Naming Convention

All manifest keys must match the pattern `^[a-z][a-z0-9-]*$`:

- Starts with a lowercase letter
- Contains only lowercase letters, digits, and hyphens
- Examples: `customer`, `saas-startup`, `hubspot`, `sales-pipeline`

## Model Format

A model defines a single entity type. It does NOT contain `entityTypes` arrays or `relationships` -- those belong to templates and integrations.

```json
{
  "manifestVersion": "1.0",
  "key": "customer",
  "name": "Customer",
  "displayName": {
    "singular": "Customer",
    "plural": "Customers"
  },
  "icon": {
    "type": "USERS",
    "colour": "BLUE"
  },
  "semanticGroup": "CUSTOMER",
  "identifierKey": "email",
  "attributes": {
    "email": {
      "key": "EMAIL",
      "type": "string",
      "format": "email",
      "label": "Email Address",
      "required": true,
      "unique": true,
      "semantics": {
        "definition": "Primary contact email",
        "classification": "IDENTIFIER"
      }
    },
    "name": {
      "key": "TEXT",
      "type": "string",
      "label": "Full Name",
      "required": true
    }
  },
  "semantics": {
    "definition": "A person or organization that purchases products or services",
    "tags": ["b2b", "crm"]
  }
}
```

## Template Format

A template composes entity types and defines relationships between them. Entity types can be referenced from models or defined inline.

```json
{
  "manifestVersion": "1.0",
  "key": "saas-startup",
  "name": "SaaS Startup",
  "description": "A workspace template for SaaS businesses with customers, subscriptions, and products",
  "entityTypes": [
    { "$ref": "models/customer" },
    {
      "$ref": "models/product",
      "extend": {
        "attributes": {
          "mrr": { "key": "CURRENCY", "type": "number", "label": "MRR" }
        }
      }
    },
    {
      "key": "subscription",
      "name": "Subscription",
      "displayName": { "singular": "Subscription", "plural": "Subscriptions" },
      "semanticGroup": "TRANSACTION",
      "attributes": {
        "status": {
          "key": "SELECT",
          "type": "string",
          "label": "Status",
          "options": { "enum": ["active", "cancelled", "past_due"] }
        },
        "start_date": {
          "key": "DATE",
          "type": "string",
          "format": "date",
          "label": "Start Date"
        }
      }
    }
  ],
  "relationships": [
    {
      "key": "customer-subscriptions",
      "sourceEntityTypeKey": "customer",
      "targetEntityTypeKey": "subscription",
      "name": "Subscriptions",
      "cardinality": "ONE_TO_MANY"
    }
  ]
}
```

## `$ref` Syntax

The `$ref` field references a model file:

- Format: `"$ref": "models/<model-key>"`
- Resolves to: `models/<model-key>.json` in the manifests directory
- The referenced model must exist in the `models/` directory
- The referenced model is used as the base entity type definition

Example: `"$ref": "models/customer"` resolves to `models/customer.json`.

## `extend` Semantics

When a template references a model via `$ref`, it can optionally include an `extend` block to customize the entity type:

```json
{
  "$ref": "models/customer",
  "extend": {
    "semanticGroup": "CUSTOMER",
    "identifierKey": "email",
    "attributes": {
      "loyalty_tier": {
        "key": "SELECT",
        "type": "string",
        "label": "Loyalty Tier",
        "options": { "enum": ["bronze", "silver", "gold", "platinum"] }
      }
    },
    "semantics": {
      "tags": ["loyalty-program"]
    }
  }
}
```

**Merge rules:**

- **Shallow additive merge.** New attributes are added to the model's attributes.
- **Existing attributes from the referenced model are NOT overwritten.** If the model defines an `email` attribute and the `extend` block also defines `email`, the model's version takes precedence.
- **No deletion semantics.** You cannot remove inherited attributes from a model.
- **`semantics.tags` are appended, not replaced.** If the model has tags `["crm"]` and the extend adds `["loyalty-program"]`, the result is `["crm", "loyalty-program"]`.
- **Scalar overrides** (`semanticGroup`, `identifierKey`, `icon`, `description`) replace the model's value if specified.

## Integration Format

An integration manifest is similar to a template but includes integration-specific features:

```json
{
  "manifestVersion": "1.0",
  "key": "hubspot",
  "name": "HubSpot",
  "description": "HubSpot CRM integration entities",
  "entityTypes": [
    {
      "key": "hubspot-contact",
      "name": "HubSpot Contact",
      "displayName": { "singular": "Contact", "plural": "Contacts" },
      "semanticGroup": "CUSTOMER",
      "readonly": true,
      "attributes": {
        "email": {
          "key": "EMAIL",
          "type": "string",
          "format": "email",
          "label": "Email",
          "required": true
        },
        "firstname": { "key": "TEXT", "type": "string", "label": "First Name" },
        "lastname": { "key": "TEXT", "type": "string", "label": "Last Name" }
      }
    }
  ],
  "fieldMappings": [
    {
      "entityTypeKey": "hubspot-contact",
      "mappings": {
        "email": { "source": "email" },
        "firstname": { "source": "first_name" },
        "lastname": { "source": "last_name" }
      }
    }
  ]
}
```

Key differences from templates:

- Entity types are always defined inline (no `$ref`)
- Entity types typically have `"readonly": true`
- The `key` must match the corresponding `integration_definitions.slug`
- Optional `fieldMappings` section maps external provider field names to entity type attribute keys

## Relationship Formats

Relationships can be defined in two formats.

### Shorthand Format (Single Target)

For simple relationships with a single target entity type:

```json
{
  "key": "customer-subscriptions",
  "sourceEntityTypeKey": "customer",
  "targetEntityTypeKey": "subscription",
  "name": "Subscriptions",
  "cardinality": "ONE_TO_MANY"
}
```

Both `targetEntityTypeKey` and `cardinality` are specified directly on the relationship.

### Full Format (Multiple Targets / Polymorphic)

For relationships that target multiple entity types or need per-target configuration:

```json
{
  "key": "customer-interactions",
  "sourceEntityTypeKey": "customer",
  "name": "Interactions",
  "allowPolymorphic": true,
  "targetRules": [
    {
      "targetEntityTypeKey": "email",
      "cardinalityOverride": "ONE_TO_MANY",
      "inverseVisible": true,
      "inverseName": "Customer"
    },
    {
      "targetEntityTypeKey": "meeting",
      "cardinalityOverride": "ONE_TO_MANY",
      "inverseVisible": true,
      "inverseName": "Customer"
    }
  ]
}
```

**The two formats are mutually exclusive.** A relationship uses either:

- `targetEntityTypeKey` + `cardinality` (shorthand), OR
- `targetRules` array (full format)

Never both.

## Attribute Schema Structure

Each attribute in the `attributes` map has the following fields:

| Field       | Type                     | Required | Description                                                                                                                                                       |
| ----------- | ------------------------ | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `key`       | string (SchemaType enum) | Yes      | UI control type: TEXT, OBJECT, NUMBER, CHECKBOX, DATE, DATETIME, RATING, PHONE, EMAIL, URL, CURRENCY, PERCENTAGE, SELECT, MULTI_SELECT, FILE_ATTACHMENT, LOCATION |
| `type`      | string (DataType enum)   | Yes      | Data type: string, number, boolean, object, array, null                                                                                                           |
| `label`     | string                   | No       | Human-readable label                                                                                                                                              |
| `format`    | string (DataFormat enum) | No       | Validation format: date, date-time, email, phone-number, currency, uri, percentage                                                                                |
| `required`  | boolean                  | No       | Whether the attribute is required                                                                                                                                 |
| `unique`    | boolean                  | No       | Whether values must be unique across entities                                                                                                                     |
| `protected` | boolean                  | No       | Whether the attribute is system-protected                                                                                                                         |
| `icon`      | object                   | No       | Icon with `type` (Lucide name) and `colour`                                                                                                                       |
| `options`   | object                   | No       | Validation options: `default`, `regex`, `enum`, `enumSorting`, `minLength`, `maxLength`, `minimum`, `maximum`                                                     |
| `semantics` | object                   | No       | Semantic metadata (see below)                                                                                                                                     |

## Semantics

Semantic metadata provides machine-readable context for AI features and data classification.

### Entity Type Semantics

```json
{
  "definition": "A person or organization that purchases products or services",
  "classification": "IDENTIFIER",
  "tags": ["b2b", "crm"]
}
```

### Attribute Semantics

Same structure as entity type semantics:

```json
{
  "definition": "Primary contact email address",
  "classification": "IDENTIFIER",
  "tags": ["contact-info"]
}
```

### Relationship Semantics

Relationships have `definition` and `tags` but **no `classification`** (classification is only meaningful for attributes):

```json
{
  "definition": "Links a customer to their subscriptions",
  "tags": ["billing"]
}
```

**Classification values** (SemanticAttributeClassification enum):

- `IDENTIFIER` -- Uniquely identifies an entity (e.g., email, employee ID)
- `CATEGORICAL` -- Groups entities into discrete categories (e.g., industry, status)
- `QUANTITATIVE` -- Numeric measurement (e.g., revenue, salary)
- `TEMPORAL` -- Date or time value (e.g., created_at, founded_year)
- `FREETEXT` -- Unstructured text (e.g., description, notes)
- `RELATIONAL_REFERENCE` -- Foreign-key-like reference to another entity

## Validation

All manifests are validated against the corresponding JSON Schema files in `schemas/` during application startup:

- `models/*.json` are validated against `schemas/model.schema.json`
- `templates/*/manifest.json` are validated against `schemas/template.schema.json`
- `integrations/*/manifest.json` are validated against `schemas/integration.schema.json`

Invalid manifests are skipped with warning logs that include the manifest key and validation errors. The application starts successfully with all remaining valid manifests loaded.
