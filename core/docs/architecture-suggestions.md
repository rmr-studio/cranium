# Architecture Suggestions

## 2026-03-08 — Auto-Generated Identifier (`ID` SchemaType) and Template Default Value Support

**Trigger:** The `time-entry` entity in the project-management manifest uses `"identifierKey": "description"` — a freetext field that is neither unique nor stable. Fixing this properly requires two pieces of infrastructure that do not yet exist.

**Affected vault notes:**
- `System Design/Entity Domain/` — entity creation flow, attribute types, schema options
- `System Design/System Patterns/` — schema validation pipeline, template installation

**Suggested update:**

### 1. New `ID` SchemaType (auto-generated identifier)

Add `ID` to the `SchemaType` enum as a first-class attribute type. Behavior:

- Auto-generates a prefixed sequential identifier on entity creation (e.g. `TE-00001`)
- Prefix defined in `SchemaOptions` (e.g. `options.prefix: "TE"`)
- Read-only after creation — users cannot edit the generated value
- Always unique within a workspace + entity type scope
- Generation strategy: query max existing value, increment, zero-pad
- Concurrency handling: retry on unique constraint violation or use a DB sequence

Manifest usage example:
```json
"reference-number": {
  "key": "ID",
  "label": "Reference",
  "type": "string",
  "options": { "prefix": "TE" }
}
```

This would allow `time-entry` (and any future entity type) to use a stable, unique, auto-generated identifier instead of relying on freetext fields.

### 2. Template default value support

`SchemaOptions.default` is currently hard-coded to `null` — defaults defined in manifests are never parsed or applied. Two gaps to close:

- **Parse defaults:** `TemplateInstallationService.parseSchemaOptions()` should read the `default` field from manifest attribute options and populate `SchemaOptions.default`.
- **Inject defaults:** `EntityService.saveEntity()` should inject attribute defaults before validation runs, so that attributes with configured defaults (e.g. a SELECT attribute defaulting to `"draft"`) are pre-populated on creation.

This applies to all attribute types, not just `ID`.
