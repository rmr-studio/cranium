# Phase 2 Context: Loader Pipeline

Phase goal: On every application startup, manifest files are scanned, validated, resolved, normalized, and upserted into the catalog database with full idempotency and per-manifest transaction isolation.

## Area 1: Missing Manifest Reconciliation

### Stale flag on manifest_catalog
- **Add `stale BOOLEAN NOT NULL DEFAULT false` to `manifest_catalog`.** Same pattern as `integration_definitions.stale`. No soft-delete columns — catalog entries are not user-owned resources.
- **Reconciliation mechanism:**
  1. Start of load cycle: `UPDATE manifest_catalog SET stale = true` (mark all existing entries stale)
  2. Each manifest loaded: upsert with `stale = false` and update `last_loaded_at`
  3. End of load cycle: anything still `stale = true` was not present on disk
- **Auto-recovery:** If a previously-stale manifest reappears on disk, the upsert sets `stale = false` automatically. No manual intervention needed. Seamless recovery, same as first load.
- **Child rows are NOT touched** when a parent goes stale. `ON DELETE CASCADE` only fires on actual row deletion. Stale entries keep their children intact for potential recovery.

### Catalog drives integration_definitions.stale
- When an integration manifest goes stale in `manifest_catalog`, the loader also sets `integration_definitions.stale = true` for the matching row (matched by `manifest_catalog.key = integration_definitions.slug` where `manifest_type = 'INTEGRATION'`).
- When an integration manifest is loaded (un-staled), the loader also sets `integration_definitions.stale = false`.
- **Single mechanism:** `manifest_catalog.stale` is the source of truth. `integration_definitions.stale` is kept in sync by the loader — not managed independently.

### Severity-based logging
- **Missing model referenced by non-stale template:** `WARN` with list of affected templates. Detected during $ref resolution before reconciliation runs.
- **Missing model not referenced by anything:** `INFO`, just went stale.
- **Missing template:** `INFO`, no downstream impact — existing workspaces are independent copies.
- **Missing integration:** `INFO` on catalog side. `integration_definitions.stale` sync handles the operational impact.

### Startup summary
- Single `INFO` log line at end of load cycle summarizing counts: `"Manifest load complete: X models, Y templates, Z integrations loaded. A stale. B skipped."`
- No per-manifest DEBUG lines. Summary only.

## Area 2: $ref Resolution Failure Behavior

### Skip entire template on unresolved $ref
- If any `$ref` in a template cannot resolve (model file missing, model failed validation and was skipped, typo in ref path), the **entire template manifest is skipped**.
- Log `WARN` with template key and the unresolved `$ref` path.
- Other templates are unaffected — per-manifest isolation.

### Failed templates are inserted as stale
- A template skipped due to unresolved `$ref` is still upserted into `manifest_catalog` with `stale = true`. This records that the manifest file exists but couldn't load.
- Child rows (entity types, relationships) are NOT inserted for stale-on-load entries.
- If the referenced model is later restored, the template will resolve and load normally on next startup (auto-recovery via upsert with `stale = false`).

### $ref scope is models only
- `$ref` always points to `models/` directory. Format: `"$ref": "models/<model-key>"`.
- Templates cannot reference other templates' entity types via `$ref`.
- No circular reference concern — models don't use `$ref`, and templates only reference models.

### Relationship key validation
- After all entity types are resolved (both `$ref` and inline), the loader validates that every relationship's `sourceEntityTypeKey` and `targetEntityTypeKey` (or `targetRules[].targetEntityTypeKey`) reference keys present in the manifest's resolved entity type set.
- If any relationship references a nonexistent key, the **entire manifest is skipped** with `WARN`. Same behavior as unresolved `$ref` — inserted as stale, no children persisted.
- This applies to both templates and integrations.

## Area 3: Field Mapping Format

### ADR-004 format stored as-is
- The loader persists the full ADR-004 field mapping format into `catalog_field_mappings.mappings` JSONB without interpreting it. The mapping engine (out of scope) will interpret the stored JSONB at runtime.
- JSON Schema validates the structural shape of mappings. The loader does not execute or simulate transforms.

### Map keyed by attribute, always full object
- The `mappings` JSONB is a map keyed by the target attribute key (manifest entity type attribute key).
- Every mapping value is an object with at least a `source` field. No string shorthand — always the full object form.
- Optional `transform` object for type coercion, value mapping, JSONPath extraction, plugin references, etc.

```json
{
  "entityTypeKey": "hubspot-contact",
  "mappings": {
    "email": { "source": "email" },
    "firstname": { "source": "first_name" },
    "deal_amount": {
      "source": "properties.amount",
      "transform": { "type": "typeCoercion", "to": "number" }
    }
  }
}
```

### Attribute key validation
- The loader validates that every mapping key exists in the target entity type's resolved attribute set.
- Invalid mapping keys cause the mapping entry to be skipped with `WARN`. The rest of the manifest still loads — field mapping validation does NOT cause the entire manifest to be skipped (unlike relationship or $ref failures).
- Rationale: field mappings are operational metadata, not structural. A missing attribute key is likely an authoring mistake, not a structural integrity issue.

### Field mappings are optional
- The `fieldMappings` section is optional in integration manifests. Integrations can define entity types without mappings.
- Mappings can be added later or handled by convention in the mapping engine.

### JSON Schema update required
- The existing `integration.schema.json` shows simple key-value mappings in its example. The schema must be updated to accept the ADR-004 object format: `{ "source": string, "transform"?: object }` per mapping entry.

## Deferred Ideas

- Configurable external manifest path (`riven.manifests.path`) for self-hosters — noted in Phase 1 CONTEXT but not required for Phase 2. Loader reads from classpath only.
- `deprecated` flag on catalog entries for manifests being phased out
- Per-manifest DEBUG log lines during load (only summary for now)

---
*Created: 2026-03-05 during Phase 2 discussion*
