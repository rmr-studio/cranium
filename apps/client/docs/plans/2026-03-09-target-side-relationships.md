# Target-Side Relationship Display & Editing — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Distinguish source-owned vs target-referenced relationships in entity type settings, showing inverse names and providing a restricted edit form for target-side relationships.

**Architecture:** Detect target-side relationships by comparing `relationship.sourceEntityTypeId` against the current entity type ID. Use the `inverseName` from the matching target rule as the display name. Add URL-param-driven auto-open for the edit modal so the "Edit source relationship" link can deep-link into the source entity's settings.

**Tech Stack:** React 19, Next.js 15 App Router, react-hook-form, zod, TanStack Table, lucide-react, Tailwind 4

---

## Task 1: Extend `EntityTypeAttributeRow` with Target-Side Fields

**Files:**
- Modify: `lib/types/entity/custom.ts:19-42`

**Step 1: Add target-side fields to the interface**

Add three optional fields after the existing `targetEntityTypeNames` field (line 38):

```typescript
export interface EntityTypeAttributeRow {
  // ... existing fields ...
  // Resolved target entity type names (for relationships)
  targetEntityTypeNames?: string[];
  // Target-side relationship metadata
  isTargetSide?: boolean;
  sourceEntityTypeId?: string;
  sourceEntityTypeKey?: string;
  // Semantic metadata fields ...
  classification?: SemanticAttributeClassification;
  definition?: string;
}
```

**Step 2: Commit**

```bash
git add lib/types/entity/custom.ts
git commit -m "feat: add target-side fields to EntityTypeAttributeRow"
```

---

## Task 2: Detect Target-Side Relationships in Table Hook

**Files:**
- Modify: `hooks/use-entity-type-table.tsx:151-178` (the `convertRelationshipToRow` function)
- Import: `ArrowDownLeft` from `lucide-react` (for icon indicator — used in Task 3)

**Step 1: Update `convertRelationshipToRow` to accept the current entity type and allEntityTypes**

The function currently takes only `relationship`. It needs `type.id` to detect target-side, and the `allEntityTypes` + `entityTypeNameLookup` are already in scope via closure.

Replace the `convertRelationshipToRow` function (lines 151-178) with:

```typescript
const convertRelationshipToRow = (
  relationship: RelationshipDefinition,
): EntityTypeAttributeRow => {
  const isTargetSide = relationship.sourceEntityTypeId !== type.id;

  if (isTargetSide) {
    // Find the target rule that references this entity type
    const matchingRule = relationship.targetRules?.find((rule) => {
      if (!rule.targetEntityTypeId) return false;
      // Look up entity type by ID to compare
      const targetType = allEntityTypes?.find((et) => et.id === rule.targetEntityTypeId);
      return targetType?.id === type.id;
    });

    const inverseName = matchingRule?.inverseName || relationship.name;

    // Resolve the source entity type name for the badge
    const sourceEntityName = entityTypeNameLookup.get(relationship.sourceEntityTypeId);
    const sourceEntityType = allEntityTypes?.find(
      (et) => et.id === relationship.sourceEntityTypeId,
    );

    return {
      id: relationship.id,
      label: inverseName,
      type: EntityPropertyType.Relationship,
      protected: relationship._protected,
      required: false,
      schemaType: 'RELATIONSHIP',
      additionalConstraints: [],
      icon: relationship.icon,
      cardinalityDefault: relationship.cardinalityDefault,
      targetRules: relationship.targetRules,
      allowPolymorphic: relationship.allowPolymorphic,
      targetEntityTypeNames: sourceEntityName ? [sourceEntityName] : [],
      isTargetSide: true,
      sourceEntityTypeId: relationship.sourceEntityTypeId,
      sourceEntityTypeKey: sourceEntityType?.key,
    };
  }

  // Source-side: original logic
  const targetNames = relationship.targetRules
    ?.map((rule) => {
      if (rule.targetEntityTypeId) {
        return entityTypeNameLookup.get(rule.targetEntityTypeId);
      }
      return undefined;
    })
    .filter((name): name is string => !!name);

  return {
    id: relationship.id,
    label: relationship.name || relationship.id,
    type: EntityPropertyType.Relationship,
    protected: relationship._protected,
    required: false,
    schemaType: 'RELATIONSHIP',
    additionalConstraints: [],
    icon: relationship.icon,
    cardinalityDefault: relationship.cardinalityDefault,
    targetRules: relationship.targetRules,
    allowPolymorphic: relationship.allowPolymorphic,
    targetEntityTypeNames: targetNames,
  };
};
```

**Step 2: Verify build**

Run: `npm run build`

**Step 3: Commit**

```bash
git add components/feature-modules/entity/hooks/use-entity-type-table.tsx
git commit -m "feat: detect target-side relationships and use inverse name in table"
```

---

## Task 3: Add Visual Indicator for Target-Side Relationships in Table

**Files:**
- Modify: `hooks/use-entity-type-table.tsx:70-105` (the `label` column cell renderer)

**Step 1: Add `ArrowDownLeft` import**

Add to the existing lucide-react imports at the top of the file. There are no existing lucide imports in this file, so add:

```typescript
import { ArrowDownLeft } from 'lucide-react';
```

**Step 2: Update the label column cell to show incoming arrow for target-side**

Replace the cell renderer in the `label` column (lines 75-104):

```typescript
cell: ({ row }) => {
  const { icon, label, type: rowType, targetEntityTypeNames, isTargetSide } = row.original;
  const isRelationship = rowType === EntityPropertyType.Relationship;

  return (
    <div className="flex items-center gap-2">
      {icon && (
        <div className="relative">
          <IconCell
            type={icon.type}
            colour={icon.colour}
            readonly
            className="size-4 shrink-0"
          />
          {isTargetSide && (
            <ArrowDownLeft className="absolute -bottom-1 -right-1 size-2.5 text-muted-foreground" />
          )}
        </div>
      )}
      <span className="font-medium">{label}</span>
      {isRelationship && targetEntityTypeNames && targetEntityTypeNames.length > 0 && (
        <div className="flex items-center gap-1">
          {targetEntityTypeNames.map((name) => (
            <Badge
              key={name}
              variant="outline"
              className="px-1.5 py-0 text-xs font-normal text-muted-foreground"
            >
              {name}
            </Badge>
          ))}
        </div>
      )}
    </div>
  );
},
```

**Step 3: Verify build**

Run: `npm run build`

**Step 4: Commit**

```bash
git add components/feature-modules/entity/hooks/use-entity-type-table.tsx
git commit -m "feat: add incoming arrow icon for target-side relationships in table"
```

---

## Task 4: Auto-Open Edit Modal via URL Param

**Files:**
- Modify: `components/types/entity-type.tsx:23-49` (add `edit` param reading, pass down)
- Modify: `components/types/entity-type-attributes.tsx:1-83` (accept param, auto-open modal)

**Step 1: Read `edit` param in `EntityTypeOverview`**

In `entity-type.tsx`, the component already reads `searchParams`. Add reading of the `edit` param and pass it down.

After line 28 (`const tabParam = searchParams.get('tab');`), add:

```typescript
const editParam = searchParams.get('edit');
```

Update the `EntityTypesAttributes` usage (line 142) to pass the edit param:

```tsx
<EntityTypesAttributes type={entityType} editDefinitionId={editParam ?? undefined} />
```

**Step 2: Accept and handle `editDefinitionId` in `EntityTypesAttributes`**

In `entity-type-attributes.tsx`, update the Props interface:

```typescript
interface Props {
  type: EntityType;
  editDefinitionId?: string;
}
```

Update the component signature:

```typescript
export const EntityTypesAttributes: FC<Props> = ({ type, editDefinitionId }) => {
```

Add imports at top:

```typescript
import { isRelationshipDefinition } from '@/lib/types/entity';
import { useRouter, usePathname, useSearchParams } from 'next/navigation';
```

Add router/params hooks inside the component (after existing hooks):

```typescript
const router = useRouter();
const pathname = usePathname();
const searchParams = useSearchParams();
```

Add an effect to auto-open the modal when `editDefinitionId` is present:

```typescript
useEffect(() => {
  if (!editDefinitionId) return;

  // Look up in schema properties
  const schemaEntry = type.schema.properties?.[editDefinitionId];
  if (schemaEntry) {
    setEditingAttribute({ id: editDefinitionId, schema: schemaEntry });
    setDialogOpen(true);
    // Clear the edit param from URL
    const params = new URLSearchParams(searchParams.toString());
    params.delete('edit');
    router.replace(`${pathname}?${params.toString()}`);
    return;
  }

  // Look up in relationships
  const relationship = type.relationships?.find((rel) => rel.id === editDefinitionId);
  if (relationship) {
    setEditingAttribute(relationship);
    setDialogOpen(true);
    // Clear the edit param from URL
    const params = new URLSearchParams(searchParams.toString());
    params.delete('edit');
    router.replace(`${pathname}?${params.toString()}`);
  }
}, [editDefinitionId, type]);
```

**Step 3: Verify build**

Run: `npm run build`

**Step 4: Commit**

```bash
git add components/feature-modules/entity/components/types/entity-type.tsx
git add components/feature-modules/entity/components/types/entity-type-attributes.tsx
git commit -m "feat: auto-open edit modal via URL edit param"
```

---

## Task 5: Detect Target-Side in Modal and Pass to Form

**Files:**
- Modify: `components/ui/modals/type/attribute-form-modal.tsx:23-110`

**Step 1: Detect target-side and pass props**

In `attribute-form-modal.tsx`, add target-side detection. After line 25 (`const isEditMode = ...`):

```typescript
const isTargetSide = useMemo(() => {
  if (!selectedAttribute || !isRelationshipDefinition(selectedAttribute)) return false;
  return selectedAttribute.sourceEntityTypeId !== type.id;
}, [selectedAttribute, type.id]);

const sourceEntityTypeKey = useMemo(() => {
  if (!isTargetSide || !selectedAttribute || !isRelationshipDefinition(selectedAttribute)) return undefined;
  // We need to resolve the source entity type key — pass allEntityTypes or resolve here
  return undefined; // Will be resolved via a prop or hook
}, [isTargetSide, selectedAttribute]);
```

Actually, the modal doesn't have access to `allEntityTypes`. We need to add the `useEntityTypes` hook here, or pass the key through the row data. Since `EntityTypeAttributeRow` now has `sourceEntityTypeKey`, we should thread it through.

**Better approach:** Update `EntityTypeDefinition` in `custom.ts` to optionally carry `sourceEntityTypeKey`, or pass it as a separate prop to the modal.

Update `entity-type-attributes.tsx` to store the `sourceEntityTypeKey` when setting up the edit. In the `onEdit` callback, the `EntityTypeDefinition` is created from `attributeLookup`. But for target-side relationships, we need the key.

Simplest approach: use `useEntityTypes` hook inside `attribute-form-modal.tsx` to resolve the source entity type key.

Update the modal. Add import:

```typescript
import { useEntityTypes } from '@/components/feature-modules/entity/hooks/query/type/use-entity-types';
import { useParams } from 'next/navigation';
```

Inside the component, add:

```typescript
const { workspaceId } = useParams<{ workspaceId: string }>();
const { data: allEntityTypes } = useEntityTypes(workspaceId);

const isTargetSide = useMemo(() => {
  if (!selectedAttribute || !isRelationshipDefinition(selectedAttribute)) return false;
  return selectedAttribute.sourceEntityTypeId !== type.id;
}, [selectedAttribute, type.id]);

const sourceEntityTypeKey = useMemo(() => {
  if (!isTargetSide || !selectedAttribute || !isRelationshipDefinition(selectedAttribute)) return undefined;
  return allEntityTypes?.find((et) => et.id === selectedAttribute.sourceEntityTypeId)?.key;
}, [isTargetSide, selectedAttribute, allEntityTypes]);
```

Update the `RelationshipForm` render (line 90-95) to pass target-side props:

```tsx
<RelationshipForm
  workspaceId={workspace.id}
  dialog={dialog}
  type={type}
  relationship={selectedAttribute as RelationshipDefinition | undefined}
  isTargetSide={isTargetSide}
  sourceEntityTypeKey={sourceEntityTypeKey}
/>
```

Also: when `isTargetSide`, don't show the attribute type dropdown (line 81-88). The `allowTypeSwitch` logic already handles this for existing relationships, so this should work as-is since target-side relationships are always existing.

**Step 2: Verify build**

Run: `npm run build` (will fail until Task 6 adds the props to RelationshipForm — that's expected)

**Step 3: Commit**

```bash
git add components/feature-modules/entity/components/ui/modals/type/attribute-form-modal.tsx
git commit -m "feat: detect target-side relationship in modal, pass to form"
```

---

## Task 6: Target-Side Relationship Form

**Files:**
- Modify: `components/forms/type/relationship/relationship-form.tsx:25-202`

**Step 1: Update props interface**

```typescript
interface RelationshipFormProps {
  workspaceId: string;
  type: EntityType;
  relationship?: RelationshipDefinition;
  dialog: DialogControl;
  isTargetSide?: boolean;
  sourceEntityTypeKey?: string;
}
```

Update destructuring (line 34-39):

```typescript
export const RelationshipForm: FC<RelationshipFormProps> = ({
  workspaceId,
  type,
  relationship,
  dialog,
  isTargetSide = false,
  sourceEntityTypeKey,
}) => {
```

**Step 2: Add imports for the info banner link**

```typescript
import { ArrowUpRight, Info } from 'lucide-react';
import Link from 'next/link';
```

**Step 3: Find the matching target rule's inverse name for display**

After the `useRelationshipForm` call (line 47-48), add:

```typescript
const matchingTargetRuleIndex = useMemo(() => {
  if (!isTargetSide || !relationship) return -1;
  return relationship.targetRules.findIndex((rule) => {
    const targetType = availableTypes.find((et) => et.id === rule.targetEntityTypeId);
    return targetType?.id === type.id;
  });
}, [isTargetSide, relationship, availableTypes, type.id]);
```

**Step 4: Render target-side form variant**

Replace the entire `return` block (lines 50-201) with logic that branches on `isTargetSide`:

```tsx
if (isTargetSide && relationship) {
  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-0">
        {/* Info banner */}
        <section className="px-6 py-4">
          <div className="flex items-start gap-3 rounded-lg border border-border bg-muted/50 p-3">
            <Info className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
            <div className="space-y-1 text-sm">
              <p className="text-muted-foreground">
                This relationship is defined on{' '}
                <span className="font-medium text-foreground">
                  {availableTypes.find((et) => et.id === relationship.sourceEntityTypeId)?.name
                    .plural ?? 'Unknown'}
                </span>
                .
              </p>
              {sourceEntityTypeKey && (
                <Link
                  href={`/dashboard/workspace/${workspaceId}/entity/${sourceEntityTypeKey}/settings?tab=attributes&edit=${relationship.id}`}
                  className="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline"
                  onClick={() => dialog.setOpen(false)}
                >
                  Edit source relationship
                  <ArrowUpRight className="size-3.5" />
                </Link>
              )}
            </div>
          </div>
        </section>

        <div className="border-t" />

        {/* Section 1: Read-only overview */}
        <section className="space-y-4 px-6 py-5">
          <h3 className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
            Relationship Overview
          </h3>

          <div className="space-y-3">
            <div>
              <span className="text-sm text-muted-foreground">Name</span>
              <p className="text-sm font-medium">{relationship.name}</p>
            </div>

            <div>
              <span className="text-sm text-muted-foreground">Cardinality</span>
              <div className="mt-1 flex items-center gap-2 text-sm">
                <span className="text-muted-foreground">
                  Each <span className="font-medium text-foreground">{type.name.singular}</span> can
                  link to{' '}
                  <span className="font-medium text-foreground">
                    {form.getValues('sourceLimit') === 'ONE' ? 'one' : 'many'}
                  </span>{' '}
                  {form.getValues('sourceLimit') === 'ONE' ? 'target' : 'targets'}
                </span>
              </div>
              <div className="flex items-center gap-2 text-sm">
                <span className="text-muted-foreground">
                  Each target can link to{' '}
                  <span className="font-medium text-foreground">
                    {form.getValues('targetLimit') === 'ONE' ? 'one' : 'many'}
                  </span>{' '}
                  {form.getValues('targetLimit') === 'ONE' ? type.name.singular : type.name.plural}
                </span>
              </div>
            </div>

            <div>
              <span className="text-sm text-muted-foreground">Target types</span>
              <div className="mt-1 flex flex-wrap gap-1">
                {relationship.targetRules.map((rule) => {
                  const targetType = availableTypes.find(
                    (et) => et.id === rule.targetEntityTypeId,
                  );
                  return (
                    <span
                      key={rule.id}
                      className="inline-flex items-center rounded-md border px-2 py-0.5 text-xs text-muted-foreground"
                    >
                      {targetType?.name.plural ?? 'Unknown'}
                    </span>
                  );
                })}
              </div>
            </div>
          </div>
        </section>

        <div className="border-t" />

        {/* Section 2: Editable inverse name */}
        <section className="space-y-4 px-6 py-5">
          <h3 className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
            Display on this entity
          </h3>

          {matchingTargetRuleIndex >= 0 && (
            <FormField
              control={form.control}
              name={`targetRules.${matchingTargetRuleIndex}.inverseName`}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Inverse name</FormLabel>
                  <FormDescription>
                    How this relationship appears when viewed from {type.name.plural}
                  </FormDescription>
                  <FormControl>
                    <Input placeholder={`E.g. ${type.name.plural}`} {...field} value={field.value ?? ''} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          )}
        </section>

        {/* Footer */}
        <footer className="flex justify-end gap-3 border-t px-6 py-4">
          <Button type="button" onClick={handleReset} variant="outline">
            Cancel
          </Button>
          <Button type="submit">Save</Button>
        </footer>
      </form>
    </Form>
  );
}

// Original source-side form (unchanged)
return (
  <Form {...form}>
    {/* ... existing form JSX unchanged ... */}
  </Form>
);
```

Note: Keep the entire existing source-side form JSX as-is in the else branch. The above just wraps it with a conditional.

**Step 5: Verify build**

Run: `npm run build`

**Step 6: Commit**

```bash
git add components/feature-modules/entity/components/forms/type/relationship/relationship-form.tsx
git commit -m "feat: target-side relationship form with restricted editing"
```

---

## Task 7: Verify End-to-End Flow

**Step 1: Manual verification checklist**

1. Create a relationship "Notes" on Companies targeting Notes entity type, with inverse name "Companies"
2. Navigate to Notes entity type settings → Attributes tab
3. Verify the relationship shows as "Companies" (inverse name) with a "Companies" badge and incoming arrow icon
4. Click edit on the relationship
5. Verify the form shows the info banner with "This relationship is defined on Companies"
6. Verify the "Edit source relationship" link navigates to Companies settings with the edit modal auto-opened
7. Verify the inverse name is editable
8. Verify forward name, cardinality, and target rules are read-only
9. Change the inverse name, save, verify it updates in the table

**Step 2: Run lint and build**

```bash
npm run lint
npm run build
```

**Step 3: Commit any fixes**

---

## File Summary

| # | File | Action |
|---|------|--------|
| 1 | `lib/types/entity/custom.ts` | Add 3 optional fields to `EntityTypeAttributeRow` |
| 2 | `hooks/use-entity-type-table.tsx` | Detect target-side, use inverse name, add arrow icon |
| 3 | `components/types/entity-type.tsx` | Read `edit` URL param, pass down |
| 4 | `components/types/entity-type-attributes.tsx` | Accept `editDefinitionId`, auto-open modal |
| 5 | `components/ui/modals/type/attribute-form-modal.tsx` | Detect target-side, resolve source key, pass to form |
| 6 | `components/forms/type/relationship/relationship-form.tsx` | Target-side form variant with restricted editing |
