# Architecture Suggestions

## 2026-03-14 — Integration Enablement: Domain Dependency Update

**Trigger:** Integration Enablement feature adds Integration → Entity domain dependency.
**Affected vault notes:** System Design/Domain Boundaries, System Design/Dependency Map
**Suggested update:** The Integration domain now directly depends on EntityTypeService for soft-delete/restore operations during disable/enable. This should be reflected in the domain dependency map. Previously, Entity was only accessed via the Catalog domain's materialization service.

## 2026-03-12 — Onboarding Domain Documentation Stub Needed

**Trigger:** Introduced new `onboarding` domain with cross-domain orchestration service
**Affected vault notes:** `System Design/` — no existing Onboarding section
**Suggested update:** Create `System Design/Onboarding/` vault section documenting the onboarding flow, atomicity guarantees (Phase 1 transactional, Phase 2 best-effort), the Internal method pattern for bypassing `@PreAuthorize`, and the `onboardingCompletedAt` idempotency gate.

## 2026-03-08 — Auto-Generated Identifier (`ID` SchemaType) and Template Default Value Support — ✅ IMPLEMENTED 2026-03-09

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

## 2026-03-09 — Relationship Simplification May Affect System Design Docs

**Trigger:** Removed semantic group targeting, polymorphic toggle, and exclusion mechanism from entity relationships
**Affected vault notes:** Entity domain relationship documentation, any diagrams showing relationship targeting flow
**Suggested update:** Review and update relationship architecture documentation to reflect simplified model where all target rules are explicit (type ID based) and polymorphic behavior is system-only (CONNECTED_ENTITIES)
