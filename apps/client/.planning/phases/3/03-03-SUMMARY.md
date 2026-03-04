---
phase: 03-relationship-form
plan: 03
subsystem: entity/form/integration
tags: [form, relationship, modal, integration, cleanup]
dependency_graph:
  requires: [03-02]
  provides: [AttributeFormModal with RelationshipForm wired]
  affects: [attribute-form-modal.tsx, custom.ts]
tech_stack:
  added: []
  patterns: [modal-integration, dead-code-removal]
key_files:
  modified:
    - components/feature-modules/entity/components/ui/modals/type/attribute-form-modal.tsx
    - lib/types/entity/custom.ts
  deleted:
    - components/feature-modules/entity/components/forms/type/relationship/relationship-links.tsx
    - components/feature-modules/entity/components/forms/type/relationship/relationship-candidate.tsx
    - components/feature-modules/entity/components/forms/relationship-overlap-alert.tsx
    - components/feature-modules/entity/hooks/use-relationship-overlap-detection.ts
decisions:
  - "Replace EntityRelationshipDefinition with RelationshipDefinition in modal — EntityRelationshipDefinition was never in the entity barrel, RelationshipDefinition is"
  - "Remove overlap detection types from custom.ts — RelationshipOverlap, OverlapResolution, OverlapDetectionResult no longer have consumers after file deletions"
metrics:
  duration_minutes: 2
  tasks_completed: 2
  files_modified: 2
  files_deleted: 4
  completed_date: "2026-03-04"
---

# Phase 3 Plan 03: Modal Integration + Cleanup Summary

**One-liner:** Wired RelationshipForm into AttributeFormModal with corrected RelationshipDefinition types, deleted 4 deferred overlap detection files, removed 3 dead overlap types from custom.ts.

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | Update attribute-form-modal.tsx to use RelationshipForm with correct types | 7b4b1c8ff | attribute-form-modal.tsx |
| 2 | Delete deferred overlap detection files and clean up types | b0fe90f7f | 4 deleted, custom.ts modified |

## What Was Built

### Task 1: Modal Integration

Made 4 targeted changes to `attribute-form-modal.tsx`:

- Changed import from `RelationshipAttributeForm` to `RelationshipForm`
- Changed type import from `EntityRelationshipDefinition` to `RelationshipDefinition`
- Updated `Props` interface: `selectedAttribute?: EntityAttributeDefinition | RelationshipDefinition`
- Updated JSX cast: `selectedAttribute as RelationshipDefinition | undefined`

The `isRelationshipDefinition` guard was already correct — it checks `!('schema' in attribute)` which works for both old and new types.

**Type compatibility confirmed:** `EntityTypeDefinition.definition` in `custom.ts` is already typed as `EntityAttributeDefinition | RelationshipDefinition`, which aligns exactly with the updated modal Props.

### Task 2: Dead Code Deletion

Deleted 4 files supporting the overlap detection / bidirectional suggestion system (deferred to v2):

- `relationship-links.tsx` — overlap detection panel component
- `relationship-candidate.tsx` — bidirectional suggestion rows component
- `relationship-overlap-alert.tsx` — overlap alert component
- `use-relationship-overlap-detection.ts` — overlap detection hook

No dangling imports found after deletion — `relationship-form.tsx` was the only consumer, rewritten in Plan 02 without these imports.

Removed 3 overlap-related type definitions from `lib/types/entity/custom.ts`:
- `RelationshipOverlap`
- `OverlapResolution`
- `OverlapDetectionResult`

Preserved `EntityRelationshipCandidate` — still used by `use-relationship-candidates.ts`.

## Checkpoint: Awaiting Human Verification

Task 3 is a `checkpoint:human-verify` gate. The auto tasks are complete and committed. Human verification of the UI is required before marking this plan fully complete.

**Verification steps:**
1. Start dev server: `npm run dev`
2. Navigate to any entity type's schema configuration page
3. Click "Add attribute" to open the AttributeFormModal
4. Select "Relationship" from the type dropdown
5. Verify the form shows: icon picker, name input, semantic definition textarea, cardinality toggles, polymorphic toggle, target rules section
6. Click "Add rule" — verify dropdown offers "Entity Type" and "Semantic Group"
7. Add an entity type rule — verify entity type single-select works
8. Expand "Constraints" — verify cardinality override, inverse visible toggle, inverse name input
9. Toggle polymorphic ON/OFF — verify target rules section hides/restores
10. Fill in name, add a target rule, click "Add Relationship" — verify saves and appears in schema table
11. Close and reopen modal — verify clean/empty form

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check: PASSED

- `components/feature-modules/entity/components/ui/modals/type/attribute-form-modal.tsx`: FOUND
- `lib/types/entity/custom.ts`: FOUND (overlap types removed, EntityRelationshipCandidate preserved)
- `relationship-links.tsx`: DELETED
- `relationship-candidate.tsx`: DELETED
- `relationship-overlap-alert.tsx`: DELETED
- `use-relationship-overlap-detection.ts`: DELETED
- No remaining imports of deleted files: VERIFIED
- Commit 7b4b1c8ff: FOUND
- Commit b0fe90f7f: FOUND
- TypeScript errors in modified files: 0 (2 pre-existing errors in blocks feature are unrelated)
