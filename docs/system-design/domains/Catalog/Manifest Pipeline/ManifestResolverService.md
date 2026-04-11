---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-03-06
Domains:
  - "[[Catalog]]"
---
# ManifestResolverService

Part of [[Manifest Pipeline]]

## Purpose

Pure in-memory transformation service that resolves scanned manifests into fully resolved manifests ready for persistence, handling `$ref` resolution, extend merging, relationship normalization, and field mapping validation.

---

## Responsibilities

- Resolve entity types per manifest type (MODEL, TEMPLATE, INTEGRATION)
- Look up `$ref` references against an in-memory model index for TEMPLATE manifests
- Apply extend merge (shallow additive) when templates override model fields
- Normalize relationships from shorthand or full format into a unified structure
- Validate relationship source/target keys against the manifest's entity type set
- Detect duplicate relationship keys
- Validate field mapping attribute keys against resolved entity type schemas
- Resolve `syncModels` mapping from integration manifests (Nango model name → entity type key)
- Degrade gracefully to stale manifests on any resolution failure
- Resolve bundle manifests into lightweight `ResolvedBundle` objects (template key list extraction, no entity type resolution)

---

## Dependencies

- `ObjectMapper` — JSON tree manipulation and type conversion
- `KLogger` — Warning-level logging for resolution failures and skipped mappings

## Used By

- [[ManifestLoaderService]] — Invokes resolver as part of the [[Flow - Manifest Loading Pipeline|manifest loading pipeline]]

---

## Key Logic

**Entity type resolution by manifest type:**

- **MODEL:** Parsed directly as a single entity type with `readonly=false`
- **TEMPLATE:** Iterates `entityTypes` array. Entries with `$ref` are resolved from the model index; entries without are parsed inline. Any unresolved `$ref` marks the entire manifest stale
- **INTEGRATION:** All entity types parsed with `readonly=true` by default
- **BUNDLE:** No entity type resolution — bundles are resolved separately via `resolveBundle()`

**$ref + extend merge:**

1. Strip `models/` prefix from `$ref` value to derive model key
2. Look up model JSON in the provided `modelIndex`
3. If `extend` block present, apply shallow additive merge:
   - **Scalar overrides:** `description`, `icon`, `semanticGroup`, `identifierKey` replace base values
   - **Attributes:** New keys added from extend; existing base keys preserved (base wins on conflict)
   - **Semantic tags:** Extend tags appended after base tags

**Relationship normalization:**

- **Shorthand format:** `targetEntityTypeKey` + `cardinality` fields produce a single-target-rule relationship
- **Full format:** `targetRules` array with per-rule `cardinalityOverride`, `inverseVisible`, `inverseName`
- **Mutual exclusivity:** A relationship node with both shorthand and `targetRules` fields returns null (marks manifest stale)
- **Protected default:** `true` for INTEGRATION manifests, `false` for TEMPLATE manifests
- **Full format cardinality default:** `ONE_TO_MANY` when no explicit cardinality is provided

**Relationship validation:**

- All `sourceEntityTypeKey` and `targetEntityTypeKey` values must exist in the manifest's resolved entity type key set
- Duplicate relationship keys cause validation failure

**Field mapping resolution:**

- Each mapping entry targets an `entityTypeKey` with a `mappings` object
- Each mapping key is validated against the target entity type's attribute key set
- Invalid keys are skipped with a WARN log rather than failing the manifest

**SyncModel resolution:**

Extracts the `syncModels` object from integration manifest JSON, producing a `Map<String, String>` mapping Nango sync model names (e.g. `"HubSpotContact"`) to entity type keys (e.g. `"contact"`). Returns an empty map if the field is absent. Only meaningful for INTEGRATION manifests — template and model manifests do not declare sync models.

**Graceful degradation:**

Any resolution failure (unresolved `$ref`, mutual exclusivity violation, invalid relationship keys) returns a `ResolvedManifest` with `stale=true` and empty entity types, relationships, and field mappings.

---

## Public Methods

### `resolveManifest(scanned: ScannedManifest, modelIndex: Map<String, JsonNode>): ResolvedManifest`

Resolves a single scanned manifest through all four phases: entity type resolution, relationship normalization, relationship validation, and field mapping resolution. Includes syncModel resolution for integration manifests. Returns a manifest with `stale=true` if any phase fails.

### `resolveBundle(scanned: ScannedManifest): ResolvedBundle`

Resolves a bundle manifest into a `ResolvedBundle`. Lightweight — extracts key, name, description, manifestVersion, and templateKeys list from the JSON. No entity type resolution; that happens at installation time via [[TemplateInstallationService]]. Requires `scanned.type == BUNDLE`.

---

## Gotchas

- **No repository dependencies:** This is a pure transformation service. The `modelIndex` is built externally and passed in — the resolver never fetches data itself
- **Base wins on attribute conflict:** When extending a model, if both base and extend define the same attribute key, the base definition is preserved. This is intentional to prevent templates from silently overriding model-defined schemas
- **Field mappings are lenient:** Unlike entity types and relationships, invalid field mapping keys do not mark the manifest stale — they are silently skipped with a warning. This allows partial field mappings to succeed
- **Full format cardinality:** Relationships using the full `targetRules` format always default to `ONE_TO_MANY`. Per-rule overrides are specified via `cardinalityOverride` on individual target rules
- **Shorthand requires both fields:** A shorthand relationship must have both `targetEntityTypeKey` and `cardinality` present. Missing either returns null for that relationship
- **Bundle resolution is trivial:** `resolveBundle()` does pure field extraction with no validation beyond the `require(type == BUNDLE)` check. Schema validation happens upstream in [[ManifestScannerService]]. Entity type resolution happens downstream at installation time.

---

## Related

- [[ManifestScannerService]] — Produces the `ScannedManifest` input
- [[ManifestUpsertService]] — Consumes the `ResolvedManifest` output for persistence
- [[ManifestLoaderService]] — Orchestrates the full pipeline
- [[Flow - Manifest Loading Pipeline]] — End-to-end flow
- [[Manifest Pipeline]] — Parent subdomain
