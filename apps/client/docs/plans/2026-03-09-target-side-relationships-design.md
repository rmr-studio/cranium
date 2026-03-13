# Target-Side Relationship Display & Editing

## Problem

When entity type A defines a relationship targeting entity type B, entity type B's attributes table shows the relationship with A's forward name instead of the inverse name. The edit form also shows the source-side perspective with no distinction.

Example: Companies defines a "Notes" relationship targeting Notes. On the Notes settings page, the relationship appears as "Notes" instead of the inverse name (e.g., "Companies"). The edit form shows the full source-side form.

## Design

### 1. Table Display

In `use-entity-type-table.tsx`, detect target-side relationships by comparing `relationship.sourceEntityTypeId !== type.id`.

For target-side relationships:
- Use `inverseName` from the matching target rule as the row label
- Show the source entity type name as the badge (instead of target type names)
- Add an incoming arrow icon (e.g., `ArrowDownLeft`) to visually distinguish from source-owned relationships

New fields on `EntityTypeAttributeRow`: `isTargetSide`, `sourceEntityTypeId`, `sourceEntityTypeKey`.

### 2. Auto-Open Edit Modal via URL

Add an `edit` query param to the entity type settings page. When present, the page auto-opens the AttributeFormModal for the definition matching that ID. The param is cleared after opening.

Flow: `entity-type.tsx` reads `edit` param -> passes to `entity-type-attributes.tsx` -> auto-triggers modal open.

### 3. Target-Side Relationship Form

When editing a target-side relationship, the form switches to a restricted mode:

**Read-only fields:**
- Forward relationship name
- Icon
- Cardinality (source/target limits)
- Target rules list

**Editable fields:**
- Inverse name (promoted to primary field in naming section)
- Cardinality override (per target rule)

**Additional UI:**
- Info banner: "This relationship is defined on {SourceEntity}. Edit source relationship ->"
- Link navigates to: `/dashboard/workspace/${workspaceId}/entity/${sourceEntityKey}/settings?tab=attributes&edit=${relationshipId}`

### Files Changed

- `lib/types/entity/custom.ts`
- `hooks/use-entity-type-table.tsx`
- `components/types/entity-type.tsx`
- `components/types/entity-type-attributes.tsx`
- `components/ui/modals/type/attribute-form-modal.tsx`
- `components/forms/type/relationship/relationship-form.tsx`
- `hooks/form/type/use-relationship-form.ts`
