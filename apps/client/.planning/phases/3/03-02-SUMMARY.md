---
phase: 03-relationship-form
plan: 02
subsystem: entity/form/components
tags: [form, relationship, target-rules, collapsible, shadcn, useFormContext]
dependency_graph:
  requires: [03-01]
  provides: [RelationshipForm, TargetRuleItem, TargetRuleList]
  affects: [03-03 (modal integration will import RelationshipForm)]
tech_stack:
  added: []
  patterns: [useFormContext, Collapsible-radix, Select-radix, DropdownMenu-radix, Popover-Command-single-select]
key_files:
  created:
    - components/feature-modules/entity/components/forms/type/relationship/target-rule-item.tsx
    - components/feature-modules/entity/components/forms/type/relationship/target-rule-list.tsx
  modified:
    - components/feature-modules/entity/components/forms/type/relationship/relationship-form.tsx
decisions:
  - "Use ruleValues[index]?.id (form value) not field.id (RHF internal id) for isExistingRule detection — field.id is always a generated UUID"
  - "EntityTypeSingleSelect built inline in target-rule-item to keep single-file-per-component rule and avoid over-abstraction"
  - "TargetRuleList uses useFormContext to read ruleValues for isExistingRule check — avoids drilling form object as prop"
metrics:
  duration_minutes: 3
  tasks_completed: 2
  files_modified: 3
  completed_date: "2026-03-04"
---

# Phase 3 Plan 02: Relationship Form UI Components Summary

**One-liner:** Three component files composing the complete relationship creation/edit UI: collapsible target rule rows with entity-type/semantic-group selectors, a rule list container with add/remove, and the full form with 5-section CONTEXT.md layout.

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | Create target-rule-item.tsx and target-rule-list.tsx | df37cc019 | target-rule-item.tsx, target-rule-list.tsx |
| 2 | Rewrite relationship-form.tsx composing all form sections | 6c4f9b9d7 | relationship-form.tsx |

## What Was Built

### Task 1: Target Rule Components

**`target-rule-item.tsx` (357 lines):**
- `TargetRuleItem`: Individual rule row rendered by useFormContext (no prop-drilling)
- Inline `EntityTypeSingleSelect`: Popover+Command single-select with entity icon/initial badge, deduplication via `disabledKeys`, disabled/locked in edit mode when `isExistingRule=true`
- Header row with entity-type or semantic-group selector (controlled by watching `ruleType`) plus remove button (ghost/xs)
- Collapsible constraints section (Radix `data-[state=open]:rotate-90` CSS transition): cardinality override Select with "Default" placeholder and clear option, inverse visible Switch, inverse name Input with descriptions
- `SEMANTIC_GROUP_LABELS` and `CARDINALITY_LABELS` maps for human-friendly display

**`target-rule-list.tsx` (100 lines):**
- `TargetRuleList`: Container accepting `UseFieldArrayReturn` prop
- Disabled state renders dashed border with polymorphic message
- Active state renders rules map, empty state text, and "Add rule" DropdownMenu
- `isExistingRule` derived from `ruleValues[index]?.id` (form value `id` field, not RHF internal field id)

### Task 2: Relationship Form Rewrite

**`relationship-form.tsx` (238 lines):**
- Renamed `RelationshipAttributeForm` → `RelationshipForm`
- 5-section layout in CONTEXT.md order:
  1. Icon (IconSelector) + Name (Input) inline row with flex gap
  2. Semantic definition Textarea (resize-none, 2 rows, FormDescription)
  3. Source cardinality toggle + Target cardinality toggle + Allow all entity types switch (flex-wrap row)
  4. TargetRuleList (disabled when allowPolymorphic)
  5. Cancel/Submit footer (border-t, justify-end)
- Polymorphic toggle inline logic: ON caches rules then clears; OFF in create restores from cache; OFF in edit starts fresh
- `useEntityTypes(workspaceId)` for available types with `= []` fallback
- Cancel variant changed from `destructive` to `outline`
- All old concerns removed: overlap detection, bidirectional section, RelationshipLink panel, createFromSuggestion, originRelationshipId

## Decisions Made

| Decision | Rationale |
|----------|-----------|
| `ruleValues[index]?.id` for isExistingRule | `field.id` from useFieldArray is always a RHF-generated UUID; the form value `.id` is only set when populating from server data in edit mode |
| EntityTypeSingleSelect inlined in target-rule-item | Single-file-per-component rule; the select is tightly coupled to the rule item's form context and would not be reused elsewhere |
| TargetRuleList reads form context for ruleValues | Cleaner than passing values as separate prop; both list and item already depend on FormProvider being present |

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check: PASSED

- `components/feature-modules/entity/components/forms/type/relationship/target-rule-item.tsx`: FOUND (357 lines, > 80 minimum)
- `components/feature-modules/entity/components/forms/type/relationship/target-rule-list.tsx`: FOUND (100 lines, > 50 minimum)
- `components/feature-modules/entity/components/forms/type/relationship/relationship-form.tsx`: FOUND (238 lines, > 120 minimum)
- Commit df37cc019: FOUND
- Commit 6c4f9b9d7: FOUND
- TypeScript errors in 3 new files: 0
- `RelationshipForm` export (not `RelationshipAttributeForm`): VERIFIED
- `useRelationshipForm` called (not old hook): VERIFIED
- No old concerns (overlap, bidirectional, EntityRelationshipDefinition): VERIFIED
- No direct models/ imports: VERIFIED
