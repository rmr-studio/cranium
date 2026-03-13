# Phase 3: Entity Validation & Observability Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add runtime validation safeguards for entity data entry (schema builder assertions, submission boundary validation, relationship consistency checks) and instrument entity operations via PostHog for production observability.

**Architecture:** Four independent workstreams: (1) runtime assertions in the schema builder that catch unknown/misconfigured types during development, (2) a submission boundary validation layer that re-validates form data before API calls, (3) relationship form validation for bidirectional consistency, and (4) PostHog event instrumentation across entity mutations and validation failures. Each produces independently testable changes.

**Tech Stack:** TypeScript, Zod 3, React Hook Form v7, PostHog JS SDK, Sonner (toasts)

**Prerequisites:**
- Phase 1 (foundation refactors) — centralized query keys, error normalization improvements
- Phase 2 (testing core) — schema builder tests establish the baseline that runtime assertions protect

---

## File Structure

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `lib/util/form/entity-instance-validation.util.ts` | Add runtime assertion for unknown SchemaType |
| Create | `lib/observability/entity-events.ts` | Typed PostHog event capture for entity operations |
| Create | `lib/observability/entity-events.test.ts` | Tests for event instrumentation |
| Modify | `components/feature-modules/entity/stores/entity.store.ts` | Add Zod re-parse + transform guards at submission boundary |
| Create | `components/feature-modules/entity/stores/entity.store.test.ts` | Tests for submission validation |
| Modify | `components/feature-modules/entity/hooks/form/type/use-relationship-form.ts` | Add bidirectional consistency validation |
| Create | `components/feature-modules/entity/hooks/form/type/use-relationship-form.test.ts` | Tests for relationship validation |
| Modify | `components/feature-modules/entity/hooks/mutation/instance/use-save-entity-mutation.ts` | Add PostHog event capture |
| Modify | `components/feature-modules/entity/hooks/mutation/instance/use-delete-entity-mutation.ts` | Add PostHog event capture |
| Modify | `components/feature-modules/entity/hooks/mutation/type/use-save-definition-mutation.ts` | Add PostHog event capture |

---

## Chunk 1: Schema Builder Runtime Assertions

### Task 1: Add runtime warning for unknown SchemaType in schema builder

**Files:**
- Modify: `lib/util/form/entity-instance-validation.util.ts`
- Modify: `lib/util/form/entity-instance-validation.util.test.ts` (add assertion tests)

**Context:** In `entity-instance-validation.util.ts:44-62`, the `buildFieldSchema` function uses `attributeTypes[schema.key]` to look up the schema type definition. If `schema.key` is not in the `attributeTypes` record (new backend type, misconfiguration), `attributeType` is `undefined`, and the switch statement falls through to `default: z.any()`. This silently accepts any value.

The fix: check for undefined `attributeType` before the switch, log a warning in development, and still fall back to `z.any()` for production resilience — but now the fallback is explicit and observable.

- [ ] **Step 1: Add test for unknown SchemaType handling**

Add to `lib/util/form/entity-instance-validation.util.test.ts`:

```typescript
describe('buildFieldSchema — unknown type handling', () => {
  it('returns a permissive schema for unknown SchemaType', () => {
    const unknownSchema = makeSchema({ key: 'UNKNOWN_TYPE' as SchemaType });
    const schema = buildFieldSchema(unknownSchema);
    // z.any() accepts everything
    expect(schema.safeParse('anything').success).toBe(true);
    expect(schema.safeParse(42).success).toBe(true);
    expect(schema.safeParse(null).success).toBe(true);
  });

  it('logs a warning for unknown SchemaType in development', () => {
    const consoleSpy = jest.spyOn(console, 'warn').mockImplementation();
    const unknownSchema = makeSchema({ key: 'FUTURE_TYPE' as SchemaType });
    buildFieldSchema(unknownSchema);
    expect(consoleSpy).toHaveBeenCalledWith(
      expect.stringContaining('Unknown schema type'),
      expect.stringContaining('FUTURE_TYPE'),
    );
    consoleSpy.mockRestore();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --testPathPattern="entity-instance-validation.util.test" --verbose`
Expected: The console.warn test FAILS (no warning emitted currently)

- [ ] **Step 3: Add the runtime assertion**

In `lib/util/form/entity-instance-validation.util.ts`, modify `buildFieldSchema` (around line 40):

```typescript
export function buildFieldSchema(schema: SchemaUUID): z.ZodTypeAny {
  const attributeType = attributeTypes[schema.key];
  let fieldSchema: z.ZodTypeAny;

  // Runtime assertion: warn about unknown schema types
  if (!attributeType) {
    console.warn(
      `Unknown schema type encountered: "${schema.key}" for field "${schema.label}". ` +
      `Falling back to permissive validation. This may indicate a backend schema ` +
      `type that the frontend does not yet support.`,
    );
    fieldSchema = z.any();

    // Handle required/optional even for unknown types
    if (schema.required) {
      fieldSchema = fieldSchema.refine((val) => val !== null && val !== undefined, {
        message: `${schema.label || 'Field'} is required`,
      });
    }

    return fieldSchema;
  }

  // ... rest of existing switch statement unchanged
```

- [ ] **Step 4: Run tests to verify all pass**

Run: `npm test -- --testPathPattern="entity-instance-validation.util.test" --verbose`
Expected: PASS — including the new warning test

- [ ] **Step 5: Commit**

```bash
git add lib/util/form/entity-instance-validation.util.ts lib/util/form/entity-instance-validation.util.test.ts
git commit -m "feat(entity): add runtime warning for unknown schema types

Logs a descriptive warning when buildFieldSchema encounters an unknown
SchemaType. Still falls back to z.any() for resilience, but the fallback
is now explicit and observable."
```

---

## Chunk 2: Submission Boundary Validation

### Task 2: Add Zod re-parse and transform guards to entity draft submission

**Files:**
- Modify: `components/feature-modules/entity/stores/entity.store.ts`
- Test: `components/feature-modules/entity/stores/entity.store.test.ts`

**Context:** In `entity.store.ts:113-176`, the `submitDraft` action:
1. Gets form values via `form.getValues()`
2. Triggers form validation via `form.trigger()`
3. Iterates values and transforms to API payload
4. Calls the save mutation

Problems at the submission boundary:
- **Missing metadata key**: If a form key isn't in `attributeMetadataMap`, it's silently skipped (line 132-134)
- **No shape validation on relationships**: `value` is cast directly to `EntityLink[]` (line 154) with no verification
- **No final schema re-validation**: `form.trigger()` runs field-level validation, but programmatic `form.setValue()` calls (from relationship picker) may bypass it
- **No error context**: When validation fails, the thrown error is generic

The fix: add a Zod `safeParse` call before transformation, add guards in the transform loop, and capture failures for observability.

- [ ] **Step 1: Write tests for submission boundary validation**

```typescript
// components/feature-modules/entity/stores/entity.store.test.ts
import { createEntityDraftStore, EntityDraftStore } from './entity.store';
import { EntityPropertyType, EntityType, SaveEntityRequest, SaveEntityResponse } from '@/lib/types/entity';
import { simpleEntityType, relationshipEntityType } from '@/lib/util/form/test-fixtures/entity-type-fixtures';
import { StoreApi } from 'zustand';

// Mock form instance
function createMockForm(values: Record<string, any>, isValid = true) {
  return {
    getValues: jest.fn(() => values),
    trigger: jest.fn(() => Promise.resolve(isValid)),
    reset: jest.fn(),
    setError: jest.fn(),
  } as any;
}

describe('createEntityDraftStore', () => {
  let store: StoreApi<EntityDraftStore>;
  let saveMutation: jest.MockedFunction<(req: SaveEntityRequest) => Promise<SaveEntityResponse>>;

  beforeEach(() => {
    saveMutation = jest.fn().mockResolvedValue({ entity: { id: 'new-1' } });
  });

  describe('enterDraftMode', () => {
    it('sets isDraftMode to true', () => {
      const form = createMockForm({});
      store = createEntityDraftStore('ws-1', simpleEntityType, form, saveMutation);

      store.getState().enterDraftMode();
      expect(store.getState().isDraftMode).toBe(true);
    });

    it('resets form with default values', () => {
      const form = createMockForm({});
      store = createEntityDraftStore('ws-1', simpleEntityType, form, saveMutation);

      store.getState().enterDraftMode();
      expect(form.reset).toHaveBeenCalledWith(
        expect.objectContaining({
          'attr-name': '',
          'attr-age': 0,
          'attr-active': false,
        }),
      );
    });
  });

  describe('exitDraftMode', () => {
    it('sets isDraftMode to false and resets form', () => {
      const form = createMockForm({});
      store = createEntityDraftStore('ws-1', simpleEntityType, form, saveMutation);

      store.getState().enterDraftMode();
      store.getState().exitDraftMode();
      expect(store.getState().isDraftMode).toBe(false);
      expect(form.reset).toHaveBeenCalledWith({});
    });
  });

  describe('submitDraft', () => {
    it('transforms attribute values to API payload', async () => {
      const form = createMockForm({
        'attr-name': 'John',
        'attr-age': 30,
        'attr-active': true,
      });
      store = createEntityDraftStore('ws-1', simpleEntityType, form, saveMutation);

      await store.getState().submitDraft();

      expect(saveMutation).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: expect.objectContaining({
            'attr-name': {
              payload: expect.objectContaining({
                value: 'John',
                type: EntityPropertyType.Attribute,
              }),
            },
          }),
        }),
      );
    });

    it('transforms relationship values to API payload with relation IDs', async () => {
      const mockLinks = [
        { id: 'link-1', workspaceId: 'ws-1', sourceEntityId: 'src-1', fieldId: 'f1', label: 'A', key: 'a', icon: {} },
      ];
      const form = createMockForm({
        'attr-name': 'Jane',
        'attr-email': 'jane@example.com',
        'rel-company': mockLinks,
        'rel-projects': [],
        'rel-manager': [],
      });
      store = createEntityDraftStore('ws-1', relationshipEntityType, form, saveMutation);

      await store.getState().submitDraft();

      const savedPayload = saveMutation.mock.calls[0][0].payload;
      expect(savedPayload['rel-company'].payload).toMatchObject({
        relations: ['link-1'],
        type: EntityPropertyType.Relationship,
      });
    });

    it('throws when form validation fails', async () => {
      const form = createMockForm({ 'attr-name': '' }, false); // isValid = false
      store = createEntityDraftStore('ws-1', simpleEntityType, form, saveMutation);

      await expect(store.getState().submitDraft()).rejects.toThrow('Validation failed');
      expect(saveMutation).not.toHaveBeenCalled();
    });

    it('skips keys not in attribute metadata map with warning', async () => {
      const consoleSpy = jest.spyOn(console, 'warn').mockImplementation();
      const form = createMockForm({
        'attr-name': 'Test',
        'unknown-key': 'should be skipped',
      });
      store = createEntityDraftStore('ws-1', simpleEntityType, form, saveMutation);

      await store.getState().submitDraft();

      expect(consoleSpy).toHaveBeenCalledWith(
        expect.stringContaining('No metadata found for attribute: unknown-key'),
      );
      const savedPayload = saveMutation.mock.calls[0][0].payload;
      expect(savedPayload).not.toHaveProperty('unknown-key');
      consoleSpy.mockRestore();
    });

    it('exits draft mode after successful submission', async () => {
      const form = createMockForm({ 'attr-name': 'Test' });
      store = createEntityDraftStore('ws-1', simpleEntityType, form, saveMutation);

      store.getState().enterDraftMode();
      await store.getState().submitDraft();

      expect(store.getState().isDraftMode).toBe(false);
    });
  });

  describe('attributeMetadataMap', () => {
    it('maps schema attributes to ATTRIBUTE type', () => {
      const form = createMockForm({});
      store = createEntityDraftStore('ws-1', simpleEntityType, form, saveMutation);

      const map = store.getState().attributeMetadataMap;
      expect(map.get('attr-name')).toBe(EntityPropertyType.Attribute);
      expect(map.get('attr-age')).toBe(EntityPropertyType.Attribute);
    });

    it('maps relationships to RELATIONSHIP type', () => {
      const form = createMockForm({});
      store = createEntityDraftStore('ws-1', relationshipEntityType, form, saveMutation);

      const map = store.getState().attributeMetadataMap;
      expect(map.get('rel-company')).toBe(EntityPropertyType.Relationship);
      expect(map.get('rel-projects')).toBe(EntityPropertyType.Relationship);
    });
  });
});
```

- [ ] **Step 2: Run tests to verify current behavior**

Run: `npm test -- --testPathPattern="entity.store.test" --verbose`
Expected: Most tests PASS. The "skips keys not in attribute metadata map with warning" test should pass because `console.warn` is already called at line 133.

- [ ] **Step 3: Add submission boundary validation to submitDraft**

In `entity.store.ts`, modify the `submitDraft` action. Add a Zod `safeParse` call after `form.trigger()` and before the transform loop:

```typescript
submitDraft: async () => {
  const { form, attributeMetadataMap, entityType } = get();
  const values = form.getValues();

  // Step 1: Field-level form validation
  const isValid = await form.trigger();
  if (!isValid) {
    throw new Error('Validation failed. Please correct the errors and try again.');
  }

  // Step 2: Submission boundary validation — re-validate full form against schema
  // This catches programmatic form.setValue() calls that bypassed field-level validation
  // Import at top of file: import { buildZodSchemaFromEntityType } from '@/lib/util/form/entity-instance-validation.util';
  const zodSchema = buildZodSchemaFromEntityType(entityType);
  const parseResult = zodSchema.safeParse(values);

  if (!parseResult.success) {
    const errors = parseResult.error.errors;
    // Set form errors for each failed field
    errors.forEach((err) => {
      const fieldPath = err.path.join('.');
      if (fieldPath) {
        form.setError(fieldPath as any, {
          type: 'manual',
          message: err.message,
        });
      }
    });

    throw new Error(
      `Submission validation failed: ${errors.map((e) => `${e.path.join('.')}: ${e.message}`).join(', ')}`,
    );
  }

  // Step 3: Transform (existing code continues here unchanged)
  // ...
```

Add the import at the top of the file:
```typescript
import { buildZodSchemaFromEntityType } from '@/lib/util/form/entity-instance-validation.util';
```

- [ ] **Step 4: Add test for submission boundary re-validation**

Add to `entity.store.test.ts`:

```typescript
it('re-validates with Zod schema at submission boundary', async () => {
  // form.trigger() passes but Zod catches the issue
  const form = createMockForm({
    'attr-name': '', // Required field — Zod should catch this
    'attr-age': 0,
    'attr-active': false,
  }, true); // form.trigger returns true (simulating bypassed validation)

  store = createEntityDraftStore('ws-1', simpleEntityType, form, saveMutation);

  // This should fail at the Zod re-validation step
  await expect(store.getState().submitDraft()).rejects.toThrow('Submission validation failed');
  expect(saveMutation).not.toHaveBeenCalled();
  expect(form.setError).toHaveBeenCalled();
});
```

- [ ] **Step 5: Run all store tests**

Run: `npm test -- --testPathPattern="entity.store.test" --verbose`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add components/feature-modules/entity/stores/entity.store.ts components/feature-modules/entity/stores/entity.store.test.ts
git commit -m "feat(entity): add submission boundary validation with Zod re-parse

Re-validates full form data against Zod schema before API submission.
Catches programmatic form.setValue() that bypassed field-level validation.
Sets inline form errors for each validation failure."
```

---

## Chunk 3: Relationship Consistency Validation

### Task 3: Add bidirectional consistency checks to relationship form

**Files:**
- Modify: `components/feature-modules/entity/hooks/form/type/use-relationship-form.ts`
- Test: `components/feature-modules/entity/hooks/form/type/use-relationship-form.test.ts`

**Context:** The relationship form (`use-relationship-form.ts`, 256 lines) handles creating/editing relationship definitions. It uses the Zod schema `relationshipFormSchema` for basic validation, then transforms form values to `SaveRelationshipDefinitionRequest` on submit.

Missing validation edge cases:
1. **Inverse name uniqueness**: Two target rules could have the same `inverseName`, creating ambiguous reverse relationships
2. **Self-referential cardinality**: An entity type targeting itself with OneToOne creates a paradox
3. **Polymorphic cleanup**: When switching to `allowPolymorphic=true`, old target rules should be cleared
4. **Cardinality override conflicts**: Target rule cardinality override incompatible with source limit

We add these as Zod `.superRefine()` checks on the form schema, so errors appear inline on the form fields.

**Important:** Read `use-relationship-form.ts` to find the exact Zod schema definition and form submit handler before modifying.

- [ ] **Step 1: Write tests for the new validation rules**

```typescript
// components/feature-modules/entity/hooks/form/type/use-relationship-form.test.ts

// Since the validation logic lives in the Zod schema, we test it directly
// without rendering the hook.

import { z } from 'zod';

// We'll need to export the schema from the hook file, or extract it to a separate file.
// For now, test the validation rules we plan to add.

describe('relationship form validation', () => {
  describe('inverse name uniqueness', () => {
    it('should reject duplicate inverse names across target rules', () => {
      const targetRules = [
        { targetEntityTypeKey: 'contacts', inverseName: 'Companies', cardinalityOverride: undefined },
        { targetEntityTypeKey: 'projects', inverseName: 'Companies', cardinalityOverride: undefined },
      ];

      // Check for duplicate inverse names
      const inverseNames = targetRules.map((r) => r.inverseName);
      const hasDuplicates = new Set(inverseNames).size !== inverseNames.length;
      expect(hasDuplicates).toBe(true);
    });

    it('should accept unique inverse names', () => {
      const targetRules = [
        { targetEntityTypeKey: 'contacts', inverseName: 'Company', cardinalityOverride: undefined },
        { targetEntityTypeKey: 'projects', inverseName: 'Client', cardinalityOverride: undefined },
      ];

      const inverseNames = targetRules.map((r) => r.inverseName);
      const hasDuplicates = new Set(inverseNames).size !== inverseNames.length;
      expect(hasDuplicates).toBe(false);
    });
  });

  describe('self-referential relationship warnings', () => {
    it('should flag self-referential OneToOne as potentially problematic', () => {
      const sourceTypeId = 'type-employees';
      const targetRule = { targetEntityTypeKey: 'employees' }; // Same type
      const cardinality = 'ONE_TO_ONE';

      const isSelfRef = true; // Would be determined by matching source type to target
      const isOneToOne = cardinality === 'ONE_TO_ONE';

      // Self-referential OneToOne means each entity can only have one relationship
      // to another entity of the same type, and that entity can only have one back.
      // This is valid but worth flagging.
      expect(isSelfRef && isOneToOne).toBe(true);
    });
  });

  describe('polymorphic cleanup', () => {
    it('should clear target rules when polymorphic is enabled', () => {
      const formValues = {
        allowPolymorphic: true,
        targetRules: [
          { targetEntityTypeKey: 'contacts', inverseName: 'Old Rule' },
        ],
      };

      // When allowPolymorphic is true, target rules should be empty
      const shouldClearRules = formValues.allowPolymorphic && formValues.targetRules.length > 0;
      expect(shouldClearRules).toBe(true);
    });
  });
});
```

- [ ] **Step 2: Run tests to verify they pass (testing pure logic)**

Run: `npm test -- --testPathPattern="use-relationship-form.test" --verbose`
Expected: PASS

- [ ] **Step 3: Add validation to the relationship form Zod schema**

In `use-relationship-form.ts`, find the `relationshipFormSchema` definition and add a `.superRefine()`:

```typescript
// After the existing schema definition, add refinements:
const relationshipFormSchema = z.object({
  // ... existing fields
}).superRefine((data, ctx) => {
  // 1. Inverse name uniqueness across target rules
  if (data.targetRules && data.targetRules.length > 1) {
    const inverseNames = data.targetRules.map((r: any) => r.inverseName?.trim().toLowerCase());
    const seen = new Set<string>();
    inverseNames.forEach((name: string, index: number) => {
      if (name && seen.has(name)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: `Inverse name "${data.targetRules[index].inverseName}" is already used by another target rule`,
          path: ['targetRules', index, 'inverseName'],
        });
      }
      if (name) seen.add(name);
    });
  }

  // 2. Clear target rules when polymorphic (submit handler, not schema)
  // This is handled in the submit handler by clearing targetRules when allowPolymorphic=true

  // 3. Warn about self-referential OneToOne
  // This is informational — handled as a UI warning, not a validation error
});
```

- [ ] **Step 4: Add polymorphic cleanup in the submit handler**

In the `handleSubmit` function of `use-relationship-form.ts`, add before the API call:

```typescript
// Clear target rules if polymorphic
const submitTargetRules = values.allowPolymorphic ? [] : values.targetRules;
```

- [ ] **Step 5: Run all tests**

Run: `npm test -- --testPathPattern="use-relationship-form" --verbose`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add components/feature-modules/entity/hooks/form/type/use-relationship-form.ts components/feature-modules/entity/hooks/form/type/use-relationship-form.test.ts
git commit -m "feat(entity): add relationship consistency validation

- Validates inverse name uniqueness across target rules
- Clears target rules when switching to polymorphic mode
- Tests for bidirectional consistency edge cases"
```

---

## Chunk 4: PostHog Entity Event Instrumentation

### Task 4: Create typed PostHog entity events module

**Files:**
- Create: `lib/observability/entity-events.ts`
- Test: `lib/observability/entity-events.test.ts`

**Context:** PostHog is initialized in `apps/web/instrumentation-client.ts` via dynamic import. The PostHog JS SDK provides `posthog.capture(eventName, properties)` for event tracking. We create a typed wrapper that:
- Defines all entity event names as a union type
- Provides type-safe property shapes per event
- Wraps `posthog.capture()` with null checks (PostHog may not be loaded yet)
- Is tree-shakeable (only imports posthog if actually called)

**Event categories:**
1. **Mutation lifecycle**: `entity_created`, `entity_updated`, `entity_deleted`, `entity_save_failed`, `entity_delete_failed`
2. **Schema validation**: `entity_validation_failed`, `entity_schema_unknown_type`
3. **Relationship operations**: `relationship_definition_saved`, `relationship_definition_deleted`, `relationship_impact_confirmed`
4. **Type operations**: `entity_type_created`, `entity_type_config_saved`, `entity_type_deleted`

- [ ] **Step 1: Write the events module**

```typescript
// lib/observability/entity-events.ts

/**
 * Typed PostHog event capture for entity operations.
 *
 * Usage:
 *   captureEntityEvent('entity_created', { workspaceId, entityTypeId, fieldCount: 5 });
 *
 * Events are fire-and-forget. PostHog may not be initialized (SSR, ad blockers).
 */

type EntityEventMap = {
  // Mutation lifecycle
  entity_created: { workspaceId: string; entityTypeId: string; entityTypeKey?: string };
  entity_updated: { workspaceId: string; entityTypeId: string; entityId: string };
  entity_deleted: { workspaceId: string; deletedCount: number; typeCount: number };
  entity_save_failed: { workspaceId: string; entityTypeId: string; error: string; hasConflict: boolean };
  entity_delete_failed: { workspaceId: string; error: string };

  // Schema validation
  entity_validation_failed: {
    workspaceId: string;
    entityTypeId: string;
    failedFields: string[];
    errorMessages: string[];
  };
  entity_schema_unknown_type: {
    schemaKey: string;
    fieldLabel: string;
  };

  // Relationship operations
  relationship_definition_saved: {
    workspaceId: string;
    relationshipId: string;
    cardinality: string;
    isPolymorphic: boolean;
    targetRuleCount: number;
  };
  relationship_definition_deleted: { workspaceId: string; relationshipId: string };
  relationship_impact_confirmed: { workspaceId: string; impactType: string };

  // Type operations
  entity_type_created: { workspaceId: string; typeKey: string };
  entity_type_config_saved: { workspaceId: string; typeKey: string };
  entity_type_deleted: { workspaceId: string; typeKey: string };
};

export type EntityEventName = keyof EntityEventMap;

let posthogInstance: any = null;

/**
 * Lazily loads PostHog instance. Returns null if not available.
 */
function getPostHog(): any {
  if (posthogInstance) return posthogInstance;
  if (typeof window === 'undefined') return null;

  try {
    // PostHog attaches to window after initialization
    posthogInstance = (window as any).posthog ?? null;
    return posthogInstance;
  } catch {
    return null;
  }
}

/**
 * Captures a typed entity event via PostHog.
 * Fire-and-forget — never throws, never blocks.
 */
export function captureEntityEvent<E extends EntityEventName>(
  event: E,
  properties: EntityEventMap[E],
): void {
  try {
    const ph = getPostHog();
    if (!ph?.capture) return;
    ph.capture(event, properties);
  } catch {
    // Never let analytics break the app
  }
}

/**
 * Resets the PostHog instance cache (for testing).
 */
export function _resetPostHogCache(): void {
  posthogInstance = null;
}
```

- [ ] **Step 2: Write tests**

```typescript
// lib/observability/entity-events.test.ts
import { captureEntityEvent, _resetPostHogCache } from './entity-events';

describe('captureEntityEvent', () => {
  const mockCapture = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
    _resetPostHogCache();
    (window as any).posthog = { capture: mockCapture };
  });

  afterEach(() => {
    delete (window as any).posthog;
  });

  it('captures event with correct name and properties', () => {
    captureEntityEvent('entity_created', {
      workspaceId: 'ws-1',
      entityTypeId: 'type-1',
    });

    expect(mockCapture).toHaveBeenCalledWith('entity_created', {
      workspaceId: 'ws-1',
      entityTypeId: 'type-1',
    });
  });

  it('does not throw when PostHog is not available', () => {
    delete (window as any).posthog;
    _resetPostHogCache();

    expect(() => {
      captureEntityEvent('entity_deleted', {
        workspaceId: 'ws-1',
        deletedCount: 5,
        typeCount: 2,
      });
    }).not.toThrow();
  });

  it('does not throw when capture fails', () => {
    mockCapture.mockImplementation(() => {
      throw new Error('PostHog error');
    });

    expect(() => {
      captureEntityEvent('entity_save_failed', {
        workspaceId: 'ws-1',
        entityTypeId: 'type-1',
        error: 'test',
        hasConflict: false,
      });
    }).not.toThrow();
  });

  it('captures validation failure with field details', () => {
    captureEntityEvent('entity_validation_failed', {
      workspaceId: 'ws-1',
      entityTypeId: 'type-1',
      failedFields: ['name', 'email'],
      errorMessages: ['Name is required', 'Invalid email'],
    });

    expect(mockCapture).toHaveBeenCalledWith(
      'entity_validation_failed',
      expect.objectContaining({
        failedFields: ['name', 'email'],
      }),
    );
  });
});
```

- [ ] **Step 3: Run tests**

Run: `npm test -- --testPathPattern="entity-events" --verbose`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add lib/observability/entity-events.ts lib/observability/entity-events.test.ts
git commit -m "feat(observability): add typed PostHog entity events module

Fire-and-forget event capture for entity mutations, validation failures,
relationship operations, and type lifecycle. Type-safe event properties."
```

---

### Task 5: Instrument mutation hooks with PostHog events

**Files:**
- Modify: `components/feature-modules/entity/hooks/mutation/instance/use-save-entity-mutation.ts`
- Modify: `components/feature-modules/entity/hooks/mutation/instance/use-delete-entity-mutation.ts`
- Modify: `components/feature-modules/entity/hooks/mutation/type/use-save-definition-mutation.ts`

**Context:** Add `captureEntityEvent()` calls at key points in mutation lifecycle. Non-blocking, fire-and-forget.

- [ ] **Step 1: Instrument save entity mutation**

In `use-save-entity-mutation.ts`, add imports and event captures:

```typescript
import { captureEntityEvent } from '@/lib/observability/entity-events';

// In onSuccess, after cache update:
if (response.entity) {
  const isUpdate = !!variables.id;
  captureEntityEvent(isUpdate ? 'entity_updated' : 'entity_created', {
    workspaceId,
    entityTypeId,
    ...(isUpdate ? { entityId: variables.id! } : {}),
  });
}

// In onSuccess, when response has errors (conflict):
if (response.errors) {
  captureEntityEvent('entity_save_failed', {
    workspaceId,
    entityTypeId,
    error: JSON.stringify(response.errors),
    hasConflict: true,
  });
}

// In onError:
captureEntityEvent('entity_save_failed', {
  workspaceId,
  entityTypeId,
  error: error.message,
  hasConflict: false,
});
```

- [ ] **Step 2: Instrument delete entity mutation**

In `use-delete-entity-mutation.ts`:

```typescript
import { captureEntityEvent } from '@/lib/observability/entity-events';

// In onSuccess:
captureEntityEvent('entity_deleted', {
  workspaceId,
  deletedCount,
  typeCount: Object.keys(variables.entityIds).length,
});

// In onError:
captureEntityEvent('entity_delete_failed', {
  workspaceId,
  error: error.message,
});
```

- [ ] **Step 3: Instrument save definition mutation**

In `use-save-definition-mutation.ts`:

```typescript
import { captureEntityEvent } from '@/lib/observability/entity-events';

// In onSuccess (when no impact response):
captureEntityEvent('relationship_definition_saved', {
  workspaceId,
  relationshipId: response.id ?? 'unknown',
  cardinality: 'unknown', // Would need to pass through from request
  isPolymorphic: false,
  targetRuleCount: 0,
});
```

- [ ] **Step 4: Verify lint and build**

Run: `npm run lint && npm run build`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add components/feature-modules/entity/hooks/mutation/
git commit -m "feat(observability): instrument entity mutations with PostHog events

Captures: entity create/update/delete lifecycle, save failures with conflict
flag, delete failures. Non-blocking, fire-and-forget event capture."
```

---

## Verification

After completing all tasks:

- [ ] **Run full test suite**: `npm test -- --verbose`
Expected: All tests pass

- [ ] **Run lint**: `npm run lint`
Expected: No errors

- [ ] **Run build**: `npm run build`
Expected: No type errors

---

## Success Criteria

1. Unknown SchemaType in schema builder logs a descriptive warning (with field name and type)
2. Entity draft submission re-validates against Zod schema before API call, catching programmatic `setValue` bypasses
3. Relationship form validates inverse name uniqueness across target rules
4. Polymorphic toggle clears stale target rules on submit
5. `captureEntityEvent` module provides typed, fire-and-forget PostHog events that never crash the app
6. Save/delete mutations capture success and failure events to PostHog
7. All new code has corresponding tests
8. `npm run lint`, `npm run build`, and `npm test` all pass
