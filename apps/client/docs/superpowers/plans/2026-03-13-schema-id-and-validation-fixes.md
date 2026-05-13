# SchemaType.Id Support & Validation Fixes Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-dcranium-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add full SchemaType.Id support (read-only auto-generated field with configurable prefix) and fix the options-merge bug where inherent attributeType constraints (e.g., Rating min/max) are silently ignored.

**Architecture:** Two changes share the same validation layer. (1) `buildFieldSchema` will merge inherent options from `attributeTypes[key].options` with per-instance `schema.options`, fixing Rating and enabling Id constraints. (2) SchemaType.Id is registered as a string type with `_protected: true`, skipped for editing in data tables, and rendered as a read-only placeholder in draft rows. Prefix editing lives in a new "Record ID" section of the configuration form, saved via the attribute definition mutation independently from the config mutation.

**Tech Stack:** TypeScript, Zod v3, React 19, react-hook-form v7, TanStack Query, shadcn/ui, Tailwind 4

---

## Task 1: Register SchemaType.Id in attributeTypes

**Files:**
- Modify: `lib/util/form/schema.util.ts:169` (add entry before closing brace)

- [ ] **Step 1: Add SchemaType.Id entry to attributeTypes**

In `lib/util/form/schema.util.ts`, add the Id entry after the Location entry (line 168):

```typescript
[SchemaType.Id]: {
    label: "Record ID",
    key: SchemaType.Id,
    type: DataType.String,
    icon: {
        type: IconType.Hash,
        colour: IconColour.Neutral,
    },
},
```

- [ ] **Step 2: Verify build passes**

Run: `npx tsc --noEmit`
Expected: No errors. The `Record<SchemaType, AttributeSchemaType>` type will now be satisfied since `SchemaType.Id` is included.

- [ ] **Step 3: Commit**

```bash
git add lib/util/form/schema.util.ts
git commit -m "feat: register SchemaType.Id in attributeTypes"
```

---

## Task 2: Implement options merge in buildFieldSchema

**Files:**
- Modify: `lib/util/form/entity-instance-validation.util.ts:39-196`
- Test: `lib/util/form/entity-instance-validation.util.test.ts`

- [ ] **Step 1: Write failing tests for inherent options merge**

Add a new `describe('inherent options merging')` block in `entity-instance-validation.util.test.ts` after the existing `buildFieldSchema` describe:

```typescript
describe('inherent options merging', () => {
  it('applies inherent Rating min/max when no per-instance options exist', () => {
    const schema = buildFieldSchema(makeAttr(SchemaType.Rating));
    // Rating has inherent { minimum: 1, maximum: 5 } from attributeTypes
    expect(schema.safeParse(0).success).toBe(false);
    expect(schema.safeParse(6).success).toBe(false);
    expect(schema.safeParse(1).success).toBe(true);
    expect(schema.safeParse(5).success).toBe(true);
    expect(schema.safeParse(3).success).toBe(true);
  });

  it('allows per-instance options to override inherent options', () => {
    const schema = buildFieldSchema(
      makeAttr(SchemaType.Rating, { options: { minimum: 0, maximum: 10 } }),
    );
    // Per-instance overrides inherent { minimum: 1, maximum: 5 }
    expect(schema.safeParse(0).success).toBe(true);
    expect(schema.safeParse(10).success).toBe(true);
    expect(schema.safeParse(11).success).toBe(false);
  });

  it('does not interfere when attributeType has no inherent options', () => {
    // Text has no inherent options — per-instance options should work unchanged
    const schema = buildFieldSchema(
      makeAttr(SchemaType.Text, { options: { minLength: 3 } }),
    );
    expect(schema.safeParse('ab').success).toBe(false);
    expect(schema.safeParse('abc').success).toBe(true);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx jest --no-coverage lib/util/form/entity-instance-validation.util.test.ts -t "inherent options merging"`
Expected: First two tests FAIL (Rating min/max not enforced)

- [ ] **Step 3: Implement the options merge**

In `lib/util/form/entity-instance-validation.util.ts`, modify `buildFieldSchema` (line 39-74):

Replace the function body to compute merged options and pass them to sub-functions:

```typescript
export function buildFieldSchema(schema: SchemaUUID): z.ZodTypeAny {
  const attributeType = attributeTypes[schema.key];
  const mergedOptions = { ...attributeType.options, ...schema.options };
  let fieldSchema: z.ZodTypeAny;

  // Base type mapping
  switch (attributeType.type) {
    case DataType.String:
      fieldSchema = buildStringSchema(schema, mergedOptions);
      break;
    case DataType.Number:
      fieldSchema = buildNumberSchema(schema, mergedOptions);
      break;
    case DataType.Boolean:
      fieldSchema = z.boolean();
      break;
    case DataType.Array:
      fieldSchema = buildArraySchema(schema, mergedOptions);
      break;
    case DataType.Object:
      fieldSchema = z.record(z.any());
      break;
    default:
      fieldSchema = z.any();
  }

  // Handle required/optional
  if (!schema.required) {
    fieldSchema = fieldSchema.optional().nullable();
  } else {
    fieldSchema = fieldSchema.refine((val) => val !== null && val !== undefined, {
      message: `${schema.label || 'Field'} is required`,
    });
  }

  return fieldSchema;
}
```

Then update the three sub-function signatures to accept `mergedOptions`:

**`buildStringSchema`** (line 79): Change signature to `function buildStringSchema(schema: SchemaUUID, mergedOptions: Partial<SchemaOptions>): z.ZodString` and replace `const options = schema.options;` (line 82) with `const options = mergedOptions;`

**`buildNumberSchema`** (line 157): Change signature to `function buildNumberSchema(schema: SchemaUUID, mergedOptions: Partial<SchemaOptions>): z.ZodNumber` and replace `const options = schema.options;` (line 163) with `const options = mergedOptions;`

**`buildArraySchema`** (line 180): Change signature to `function buildArraySchema(schema: SchemaUUID, mergedOptions: Partial<SchemaOptions>): z.ZodArray<any>` and replace `const options = schema.options;` (line 181) with `const options = mergedOptions;`

Import `SchemaOptions` if not already imported.

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx jest --no-coverage lib/util/form/entity-instance-validation.util.test.ts`
Expected: ALL pass including new inherent options tests

- [ ] **Step 5: Also update the old Rating test to assert proper enforcement**

Replace the existing Rating test (around line 405-411) that says "just verify the number schema is created":

```typescript
it('enforces inherent Rating min=1 max=5 constraints', () => {
  const schema = buildFieldSchema(makeAttr(SchemaType.Rating));
  expect(schema.safeParse(0).success).toBe(false);
  expect(schema.safeParse(6).success).toBe(false);
  expect(schema.safeParse(3).success).toBe(true);
});
```

- [ ] **Step 6: Run full test suite**

Run: `npx jest --no-coverage`
Expected: All tests pass

- [ ] **Step 7: Commit**

```bash
git add lib/util/form/entity-instance-validation.util.ts lib/util/form/entity-instance-validation.util.test.ts
git commit -m "fix: merge inherent attributeType options with per-instance schema options in buildFieldSchema"
```

---

## Task 3: Add SchemaType.Id validation and default tests

**Files:**
- Modify: `lib/util/form/entity-instance-validation.util.test.ts`
- Modify: `lib/util/form/test-fixtures/entity-type-fixtures.ts`

- [ ] **Step 1: Add Id fixture to entity-type-fixtures.ts**

Add after `edgeCaseEntityType` (line 329):

```typescript
// ---------------------------------------------------------------------------
// Fixture 5: Type with Id attribute — auto-generated record ID with prefix
// ---------------------------------------------------------------------------
export const entityTypeWithId: EntityType = {
  id: 'entity-type-with-id',
  key: 'tickets',
  version: 1,
  icon: { type: IconType.Ticket, colour: IconColour.Blue },
  name: { singular: 'Ticket', plural: 'Tickets' },
  _protected: false,
  identifierKey: 'title',
  semanticGroup: SemanticGroup.Support,
  sourceType: 'USER_CREATED' as EntityType['sourceType'],
  readonly: false,
  workspaceId: WORKSPACE_ID,
  entitiesCount: 0,
  relationships: [],
  columns: [
    { key: 'record_id', type: EntityPropertyType.Attribute, visible: true, width: 120 },
    { key: 'title', type: EntityPropertyType.Attribute, visible: true, width: 200 },
    { key: 'status', type: EntityPropertyType.Attribute, visible: true, width: 100 },
  ],
  schema: {
    key: SchemaType.Object,
    type: DataType.Object,
    icon: { type: IconType.Box, colour: IconColour.Neutral },
    required: false,
    unique: false,
    _protected: false,
    properties: {
      record_id: attr(SchemaType.Id, 'Record ID', DataType.String, {
        required: true,
        unique: true,
        _protected: true,
        options: { prefix: 'TKT' },
      }),
      title: attr(SchemaType.Text, 'Title', DataType.String, { required: true, unique: true }),
      status: attr(SchemaType.Select, 'Status', DataType.String, {
        options: { _enum: ['open', 'in_progress', 'closed'] },
      }),
    },
  },
};
```

- [ ] **Step 2: Write Id field validation tests**

Add to `entity-instance-validation.util.test.ts`, import `entityTypeWithId` and add test cases:

In `buildFieldSchema` describe, add a new `describe('Id fields')` block:

```typescript
describe('Id fields', () => {
  it('accepts any string value (backend-generated)', () => {
    const schema = buildFieldSchema(makeAttr(SchemaType.Id, { required: true, _protected: true }));
    expect(schema.safeParse('TKT-1').success).toBe(true);
    expect(schema.safeParse('ABC-99999').success).toBe(true);
  });

  it('accepts null when optional', () => {
    const schema = buildFieldSchema(makeAttr(SchemaType.Id));
    expect(schema.safeParse(null).success).toBe(true);
  });
});
```

In `getDefaultValueForSchema` describe, add:

```typescript
it('returns empty string for Id fields', () => {
  expect(getDefaultValueForSchema(makeAttr(SchemaType.Id))).toBe('');
});
```

In `buildZodSchemaFromEntityType` describe, add:

```typescript
it('includes Id attribute in schema for entityTypeWithId', () => {
  const schema = buildZodSchemaFromEntityType(entityTypeWithId);
  const keys = Object.keys(schema.shape);
  expect(keys).toContain('record_id');
  expect(keys).toContain('title');
  expect(keys).toContain('status');
});
```

In `buildDefaultValuesFromEntityType` describe, add:

```typescript
it('returns empty string default for Id fields', () => {
  const defaults = buildDefaultValuesFromEntityType(entityTypeWithId);
  expect(defaults['record_id']).toBe('');
});
```

- [ ] **Step 3: Run tests**

Run: `npx jest --no-coverage lib/util/form/entity-instance-validation.util.test.ts`
Expected: All pass

- [ ] **Step 4: Commit**

```bash
git add lib/util/form/entity-instance-validation.util.test.ts lib/util/form/test-fixtures/entity-type-fixtures.ts
git commit -m "test: add SchemaType.Id validation and fixture tests"
```

---

## Task 4: Skip edit config for Id fields in data table columns

**Files:**
- Modify: `components/feature-modules/entity/components/tables/entity-table-utils.tsx:356-382`
- Test: `components/feature-modules/entity/components/tables/entity-table-utils.test.ts`

- [ ] **Step 1: Write failing test**

Add to `entity-table-utils.test.ts`. Import `generateColumnsFromEntityType` (add to imports if not present), `SchemaType`, and create a test:

```typescript
import {
  generateColumnsFromEntityType,
  // ... existing imports
} from '@/components/feature-modules/entity/components/tables/entity-table-utils';
```

Add test (find or create an appropriate describe block):

```typescript
describe('generateColumnsFromEntityType', () => {
  it('does not create edit config for SchemaType.Id fields even when editing enabled', () => {
    const entityType: EntityType = {
      id: 'type-1',
      key: 'tickets',
      version: 1,
      icon: { type: IconType.Box, colour: IconColour.Neutral },
      name: { singular: 'Ticket', plural: 'Tickets' },
      _protected: false,
      identifierKey: 'title',
      semanticGroup: 'SUPPORT' as any,
      sourceType: 'USER_CREATED' as any,
      readonly: false,
      workspaceId: 'ws-1',
      entitiesCount: 0,
      relationships: [],
      columns: [
        { key: 'record_id', type: EntityPropertyType.Attribute, visible: true, width: 120 },
        { key: 'title', type: EntityPropertyType.Attribute, visible: true, width: 200 },
      ],
      schema: {
        key: SchemaType.Object,
        type: DataType.Object,
        icon: { type: IconType.Box, colour: IconColour.Neutral },
        required: false,
        unique: false,
        _protected: false,
        properties: {
          record_id: createMockSchema({ key: SchemaType.Id, type: DataType.String, _protected: true }),
          title: createMockSchema({ key: SchemaType.Text, type: DataType.String }),
        },
      },
    };

    const columns = generateColumnsFromEntityType(entityType, { enableEditing: true });
    const idColumn = columns.find((c) => c.accessorKey === 'record_id');
    const titleColumn = columns.find((c) => c.accessorKey === 'title');

    expect(idColumn).toBeDefined();
    expect(idColumn!.meta?.edit).toBeUndefined();
    expect(titleColumn).toBeDefined();
    expect(titleColumn!.meta?.edit).toBeDefined();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx jest --no-coverage components/feature-modules/entity/components/tables/entity-table-utils.test.ts -t "does not create edit config"`
Expected: FAIL — Id column currently gets an edit config

- [ ] **Step 3: Implement the skip**

In `entity-table-utils.tsx`, inside `generateColumnsFromEntityType`, find the edit config creation block (around line 358). Add a condition to skip edit config for Id fields:

```typescript
// Create edit config if editing is enabled (skip for Id fields — they are auto-generated)
const editConfig: ColumnEditConfig<EntityRow, any, any> | undefined =
  options?.enableEditing && schema.key !== SchemaType.Id
    ? {
        // ... existing edit config object
      }
    : undefined;
```

Add `SchemaType` to the imports from `@/lib/types/common` if not already present.

- [ ] **Step 4: Run test to verify it passes**

Run: `npx jest --no-coverage components/feature-modules/entity/components/tables/entity-table-utils.test.ts`
Expected: All pass

- [ ] **Step 5: Commit**

```bash
git add components/feature-modules/entity/components/tables/entity-table-utils.tsx components/feature-modules/entity/components/tables/entity-table-utils.test.ts
git commit -m "feat: skip edit config for SchemaType.Id fields in data table columns"
```

---

## Task 5: Render Id placeholder in draft rows

**Files:**
- Modify: `components/feature-modules/entity/components/tables/entity-draft-row.tsx:87-104`

- [ ] **Step 1: Add SchemaType.Id check in getElement**

In `entity-draft-row.tsx`, modify the `getElement` function (line 87-105). After the schema lookup (line 94-95), add the Id check:

```typescript
const getElement = (
  id: string,
  type: EntityType,
  property: EntityPropertyType,
  isFirstCell: boolean,
): ReactNode | null => {
  if (property === EntityPropertyType.Attribute) {
    const schema = entityType.schema.properties?.[id];
    if (!schema) return null;

    // Id fields are auto-generated by the backend — show read-only placeholder
    if (schema.key === SchemaType.Id) {
      return (
        <span className="px-2 text-sm italic text-muted-foreground">
          Auto-generated
        </span>
      );
    }

    return <EntityFieldCell attributeId={id} schema={schema} autoFocus={isFirstCell} />;
  }

  const relationship = type.relationships?.find((r) => r.id === id);
  if (property === EntityPropertyType.Relationship && relationship) {
    return <DraftEntityRelationshipPicker relationship={relationship} />;
  }

  return null;
};
```

Add `SchemaType` to the imports from `@/lib/types/entity` or `@/lib/types/common` (whichever is used in this file — check existing imports).

- [ ] **Step 2: Verify build passes**

Run: `npx tsc --noEmit`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add components/feature-modules/entity/components/tables/entity-draft-row.tsx
git commit -m "feat: render auto-generated placeholder for Id fields in draft rows"
```

---

## Task 6: Exclude SchemaType.Id from identifier candidates

**Files:**
- Modify: `components/feature-modules/entity/components/types/entity-type.tsx:80-84`

- [ ] **Step 1: Add Id exclusion to the identifier filter**

In `entity-type.tsx`, modify the `identifierKeys` useMemo filter (line 80-84):

```typescript
.filter(
  ([, attr]) =>
    attr.unique &&
    attr.required &&
    attr.key !== SchemaType.Id &&
    (attr.type === DataType.String || attr.type === DataType.Number),
)
```

Add `SchemaType` to the imports from `@/lib/types/common`.

- [ ] **Step 2: Verify build passes**

Run: `npx tsc --noEmit`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add components/feature-modules/entity/components/types/entity-type.tsx
git commit -m "feat: exclude SchemaType.Id from identifier candidates"
```

---

## Task 7: Add Record ID prefix section to configuration form

**Files:**
- Modify: `components/feature-modules/entity/components/forms/type/configuration-form.tsx`
- Modify: `components/feature-modules/entity/context/configuration-provider.tsx` (form schema + submit)
- Modify: `components/feature-modules/entity/stores/type/configuration.store.ts` (submit handler)

This task is the most complex. It involves:
1. Finding the Id attribute from the entity type
2. Adding a `prefix` field to the config form schema
3. Rendering a new "Record ID" section conditionally
4. Saving the prefix via the attribute definition mutation on form submit

- [ ] **Step 1: Add `idPrefix` to the config form schema**

In `configuration-provider.tsx`, extend the `entityTypeFormSchema` to include `idPrefix`:

```typescript
const entityTypeFormSchema = z
  .object({
    identifierKey: z.string().min(1, 'Identifier key is required').refine(isUUID),
    idPrefix: z.string().max(10, 'Prefix must be 10 characters or fewer').optional(),
    columnConfiguration: z.object({
      order: z.array(z.string()),
      overrides: z.record(
        z.string(),
        z.object({
          width: z.number().min(150).max(1000).optional(),
          visible: z.boolean().optional(),
        }),
      ),
    }),
  })
  .extend(baseEntityTypeFormSchema.shape);
```

- [ ] **Step 2: Initialize idPrefix from the Id attribute's options**

In the configuration provider's form initialization, find where `form.reset()` or `defaultValues` are set. Add logic to find the Id attribute and read its prefix:

```typescript
// Find Id attribute prefix for form initialization
const idAttribute = entityType.schema.properties
  ? Object.values(entityType.schema.properties).find((attr) => attr.key === SchemaType.Id)
  : undefined;
const idPrefix = idAttribute?.options?.prefix ?? '';
```

Include `idPrefix` in the form's default values.

- [ ] **Step 3: Add the Record ID section to configuration-form.tsx**

In `configuration-form.tsx`, add a helper to detect the Id attribute and render the section between Classification and Identity:

```typescript
// Find Id attribute in entity type schema
const idAttribute = useMemo(() => {
  if (!entityType?.schema?.properties) return undefined;
  return Object.entries(entityType.schema.properties).find(
    ([, attr]) => attr.key === SchemaType.Id,
  );
}, [entityType?.schema?.properties]);
```

Then add the JSX between the Classification separator and Identity section:

```tsx
{idAttribute && (
  <>
    <Separator />
    <div className="p-5">
      <p className="mb-3.5 text-xs font-medium tracking-widest text-muted-foreground/70 uppercase">
        Record ID
      </p>
      <FormField
        control={form.control}
        name="idPrefix"
        render={({ field }) => (
          <FormItem className="gap-1">
            <FormLabel className="text-sm font-medium text-muted-foreground">
              ID Prefix
            </FormLabel>
            <FormDescription className="text-xs italic">
              Records will be numbered as PREFIX-1, PREFIX-2, etc.
            </FormDescription>
            <FormControl>
              <Input
                placeholder="e.g., TKT"
                maxLength={10}
                className="w-full max-w-xs uppercase"
                {...field}
              />
            </FormControl>
            <FormMessage />
          </FormItem>
        )}
      />
    </div>
  </>
)}
```

The component will need access to `entityType`. Use `useConfigCurrentType()` from the configuration provider to get it.

- [ ] **Step 4: Wire prefix save in the submit handler**

In `configuration.store.ts`, the `handleSubmit` function (around line 133) currently calls the config mutation. After the config mutation, add the prefix save via the attribute definition mutation.

The prefix save should use `SaveTypeDefinitionRequest` to update the Id attribute's `options.prefix`. Use `Promise.all` to run both mutations, and invalidate entity type queries once after both complete.

Find the Id attribute's key from `entityType.schema.properties`, then construct the definition request:

```typescript
// Save prefix if entity type has an Id attribute
const idEntry = entityType.schema.properties
  ? Object.entries(entityType.schema.properties).find(([, attr]) => attr.key === SchemaType.Id)
  : undefined;

if (idEntry && values.idPrefix !== undefined) {
  const [attrId, attrSchema] = idEntry;
  // Save via attribute definition mutation with updated prefix
  await saveDefinition({
    index: undefined,
    definition: {
      type: EntityTypeRequestDefinition.SaveAttribute,
      id: attrId,
      key: entityType.key,
      schemaType: SchemaType.Id,
      label: attrSchema.label ?? 'Record ID',
      iconType: attrSchema.icon.type,
      iconColour: attrSchema.icon.colour,
      required: attrSchema.required,
      unique: attrSchema.unique,
      options: { ...attrSchema.options, prefix: values.idPrefix },
    },
  });
}
```

The exact mutation and request type depend on the existing `useSaveDefinitionMutation` hook — reference how `use-relationship-form.ts:191` uses it for the correct pattern.

- [ ] **Step 5: Verify build passes**

Run: `npx tsc --noEmit`
Expected: No errors

- [ ] **Step 6: Run full test suite**

Run: `npx jest --no-coverage`
Expected: All existing tests pass. New integration testing for the form is deferred to manual testing since configuration form tests require full provider setup.

- [ ] **Step 7: Commit**

```bash
git add components/feature-modules/entity/components/forms/type/configuration-form.tsx components/feature-modules/entity/context/configuration-provider.tsx components/feature-modules/entity/stores/type/configuration.store.ts
git commit -m "feat: add Record ID prefix section to entity type configuration form"
```

---

## Task 8: Final verification

- [ ] **Step 1: Run full test suite**

Run: `npx jest --no-coverage`
Expected: All tests pass

- [ ] **Step 2: Run lint**

Run: `npm run lint`
Expected: No lint errors

- [ ] **Step 3: Run build**

Run: `npm run build`
Expected: Successful build

- [ ] **Step 4: Manual verification checklist**

With the dev server running:
- [ ] Entity type with an Id attribute renders the Id column as read-only in the data table
- [ ] Draft row shows "Auto-generated" placeholder for Id field
- [ ] Id field does not appear in the identifier selector dropdown
- [ ] Configuration form shows "Record ID" section with prefix input when entity type has an Id attribute
- [ ] Configuration form hides "Record ID" section when entity type has no Id attribute
- [ ] Changing prefix and saving updates the Id attribute's options.prefix
- [ ] Rating fields enforce min=1, max=5 in the entity data table
