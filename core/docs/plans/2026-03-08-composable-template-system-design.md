# Composable Template System Design

**Date:** 2026-03-08
**Status:** Approved

## Problem

The template system uses monolithic templates (saas-startup, dtc-ecommerce, service-business) where each template defines all entity types inline. This prevents reuse — the same "customer" entity type is duplicated across all three templates. It also prevents users from installing just the parts they need (e.g., CRM without SaaS-specific entities).

## Design

### Three-Layer Manifest Hierarchy

- **Models** (shared entity types) — `customer.json`, `invoice.json`, `support-ticket.json`, `communication.json`
- **Templates** (composable domain packages) — `crm`, `billing`, `support`, `saas`, `ecommerce`, `project-management`. Each defines entity types (inline or via `$ref` to models) and relationships.
- **Bundles** (curated template collections) — `saas-startup`, `dtc-ecommerce`, `service-business`. Each is a list of template keys installed together as one atomic operation.

### Manifest Layer Changes

**Scanner** — Add `scanBundles()` scanning `manifests/bundles/*/manifest.json`, validated against `bundle.schema.json`.

**Resolver** — Add `resolveBundle()` producing `ResolvedBundle` (key, name, description, templateKeys list). No entity type resolution — bundles reference template keys only.

**Loader** — Add bundle loading phase after templates, before reconciliation. Bundle upsert persists `template_keys` JSONB on `ManifestCatalogEntity`.

**Upsert** — Handle `BUNDLE` type: persist catalog entry with `templateKeys` JSONB, no child entity types or relationships.

### Entity/Repository Changes

**ManifestCatalogEntity** — Add `templateKeys: List<String>?` field mapped to the `template_keys` JSONB column.

### Catalog Query Changes

**ManifestCatalogService** — Add `getAvailableBundles()` returning `List<BundleDetail>` and `getBundleByKey(key)` returning `BundleDetail`. Both filter `stale = false`.

### Installation Changes

**TemplateInstallationService.installBundle()** — New public method:

1. Resolve bundle from catalog, get template key list
2. Check `workspace_template_installations` for already-installed templates, partition into skip/install sets
3. For skipped templates, look up their entity types by key+workspaceId to build an ID map for relationship resolution
4. For templates to install, fetch each `ManifestDetail` from catalog
5. Merge all entity types across install-set templates, deduplicate by key
6. Create entity types (single pass, reuse existing private helpers)
7. Merge ID map: newly created + looked-up from skipped templates
8. Create all relationships across all templates (using merged ID map)
9. Apply all semantic metadata
10. Record each installed template in `workspace_template_installations`
11. Return response with installed/skipped breakdown

**Idempotency for single template install** — Add skip-if-installed check to `installTemplate()` so it returns a meaningful response instead of throwing on re-install.

### Controller Changes

**TemplateController** — Add two endpoints:

- `GET /api/v1/templates/bundles` — list available bundles
- `POST /api/v1/templates/{workspaceId}/install-bundle` — install bundle into workspace

### Response Models

**BundleInstallationResponse:**

- `bundleKey`, `bundleName`
- `templatesInstalled: List<String>`, `templatesSkipped: List<String>`
- `entityTypesCreated: Int`, `relationshipsCreated: Int`
- `entityTypes: List<CreatedEntityTypeSummary>`

### Deletions

Remove old monolithic templates:

- `src/main/resources/manifests/templates/saas-startup/`
- `src/main/resources/manifests/templates/dtc-ecommerce/`
- `src/main/resources/manifests/templates/service-business/`

## Cross-Template Entity Type Resolution

When installing a bundle like saas-startup = [crm, billing, support, saas]:

- CRM installs: customer, communication
- Billing installs: invoice (+ customer-invoices relationship where source=customer)
- Support installs: support-ticket (+ customer-support-tickets relationship)
- SaaS installs: subscription, plan, feature-usage, churn-event (+ relationships to customer)

The "customer" entity type is owned by CRM. Other templates define relationships that reference customer as a source without including it. Bundle installation flattens all templates, deduplicates entity types by key, installs them in one pass, then creates all relationships using the merged ID map.

## Idempotency

Tracked per template in `workspace_template_installations`. If a user installs CRM standalone then later installs saas-startup, CRM is skipped but its entity type IDs are looked up so that billing/support/saas relationships can reference the existing customer entity type.
