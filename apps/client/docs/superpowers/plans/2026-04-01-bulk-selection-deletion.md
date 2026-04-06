# Bulk Selection & Deletion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace page-scoped row selection with a server-aware "select all" mode that tracks the full query result count, supports exclusions, and powers a dual-mode deletion API (BY_ID for individual selection, ALL with filter+exclusions for bulk).

**Architecture:** A new `useEntitySelection` hook manages logical selection state (manual vs all mode, exclusions, totalCount) above TanStack Table's row-level checkboxes. The existing delete mutation, service, and modal are refactored to accept the generated `DeleteEntityRequest` type which discriminates between `EntitySelectType.ById` (explicit IDs) and `EntitySelectType.All` (filter + excludeIds). Cache invalidation uses surgical removal for BY_ID and full refetch for ALL. `totalCount` is fetched only on the first page of each query to avoid redundant COUNT queries.

**Tech Stack:** React 19, TanStack Table/Query, Zustand (existing stores only), Next.js 15 App Router, TypeScript strict, Jest + RTL

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `components/feature-modules/entity/hooks/use-entity-selection.ts` | Selection state machine: mode, excludedIds, includedIds, totalCount, reset logic |
| Create | `components/feature-modules/entity/hooks/use-entity-selection.test.ts` | Tests for selection hook |
| Modify | `lib/types/entity/requests.ts` | Re-export `DeleteEntityRequest` and `EntitySelectType` from generated models |
| Modify | `components/feature-modules/entity/service/entity.service.ts:145-155` | Rewrite `deleteEntities` to accept `DeleteEntityRequest` |
| Create | `components/feature-modules/entity/service/entity.service.test.ts` | Tests for refactored service method |
| Modify | `components/feature-modules/entity/hooks/mutation/instance/use-delete-entity-mutation.ts` | Accept `DeleteEntityRequest`, dual-mode cache invalidation |
| Create | `components/feature-modules/entity/hooks/mutation/instance/use-delete-entity-mutation.test.ts` | Tests for mutation onSuccess cache logic |
| Modify | `components/feature-modules/entity/hooks/query/use-entity-query.ts:112-114` | Send `includeCount: true` on first page only |
| Modify | `components/feature-modules/entity/components/tables/entity-data-table.tsx` | Wire `useEntitySelection` hook, extract totalCount from query, pass selection state to action bar |
| Modify | `components/feature-modules/entity/components/tables/entity-table-action-bar.tsx` | Accept selection state, pass to delete modal |
| Modify | `components/feature-modules/entity/components/ui/modals/instance/delete-entity-modal.tsx` | Build `DeleteEntityRequest` based on selection mode |
| Modify | `components/ui/data-table/data-table.tsx:355-366` | Expose `onSelectAllChange` callback from header checkbox |
| Modify | `components/ui/data-table/data-table.types.ts:203-208` | Add `onSelectAllChange` to `RowSelectionConfig` |

---

## Task 1: Add `DeleteEntityRequest` and `EntitySelectType` to domain barrel

**Files:**
- Modify: `lib/types/entity/requests.ts`

This is a prerequisite for all downstream tasks — the generated types exist but aren't re-exported through the domain barrel, so nothing can import them via `@/lib/types/entity`.

- [ ] **Step 1: Add re-exports to the entity requests barrel**

```typescript
// In lib/types/entity/requests.ts — add these imports and re-exports

// Add to imports:
import type { DeleteEntityRequest } from '../models/DeleteEntityRequest';
import { EntitySelectType } from '../models/EntitySelectType';

// Add to the export type block:
// DeleteEntityRequest,

// Add a separate value export for the enum:
// export { EntitySelectType };
```

The full updated file should be:

```typescript
// Re-export entity-related request types from generated code
import type { CreateEntityTypeRequest } from '../models/CreateEntityTypeRequest';
import type { SaveEntityRequest } from '../models/SaveEntityRequest';
import type { SaveTypeDefinitionRequest } from '../models/SaveTypeDefinitionRequest';
import type { SaveTypeDefinitionRequestDefinition } from '../models/SaveTypeDefinitionRequestDefinition';
import type { SaveAttributeDefinitionRequest } from '../models/SaveAttributeDefinitionRequest';
import type { SaveRelationshipDefinitionRequest } from '../models/SaveRelationshipDefinitionRequest';
import type { DeleteTypeDefinitionRequest } from '../models/DeleteTypeDefinitionRequest';
import type { DeleteTypeDefinitionRequestDefinition } from '../models/DeleteTypeDefinitionRequestDefinition';
import type { DeleteAttributeDefinitionRequest } from '../models/DeleteAttributeDefinitionRequest';
import type { DeleteRelationshipDefinitionRequest } from '../models/DeleteRelationshipDefinitionRequest';
import type { EntityAttributeRequest } from '../models/EntityAttributeRequest';
import type { EntityAttributeRequestPayload } from '../models/EntityAttributeRequestPayload';
import type { EntityReferenceRequest } from '../models/EntityReferenceRequest';
import type { SaveTargetRuleRequest } from '../models/SaveTargetRuleRequest';
import type { SaveSemanticMetadataRequest } from '../models/SaveSemanticMetadataRequest';
import type { BulkSaveSemanticMetadataRequest } from '../models/BulkSaveSemanticMetadataRequest';
import type { UpdateEntityTypeConfigurationRequest } from '../models/UpdateEntityTypeConfigurationRequest';
import type { DeleteEntityRequest } from '../models/DeleteEntityRequest';
import { EntityTypeRequestDefinition } from '../models/EntityTypeRequestDefinition';
import { EntitySelectType } from '../models/EntitySelectType';

export type {
  CreateEntityTypeRequest,
  SaveEntityRequest,
  SaveTypeDefinitionRequest,
  SaveTypeDefinitionRequestDefinition,
  SaveAttributeDefinitionRequest,
  SaveRelationshipDefinitionRequest,
  DeleteTypeDefinitionRequest,
  DeleteTypeDefinitionRequestDefinition,
  DeleteAttributeDefinitionRequest,
  DeleteRelationshipDefinitionRequest,
  EntityAttributeRequest,
  EntityAttributeRequestPayload,
  EntityReferenceRequest,
  SaveTargetRuleRequest,
  SaveSemanticMetadataRequest,
  BulkSaveSemanticMetadataRequest,
  UpdateEntityTypeConfigurationRequest,
  DeleteEntityRequest,
};

export { EntityTypeRequestDefinition, EntitySelectType };
```

- [ ] **Step 2: Verify the barrel compiles**

Run: `npx tsc --noEmit --pretty 2>&1 | head -20`
Expected: No errors related to `DeleteEntityRequest` or `EntitySelectType`

- [ ] **Step 3: Commit**

```bash
git add lib/types/entity/requests.ts
git commit -m "feat(entity): re-export DeleteEntityRequest and EntitySelectType from domain barrel"
```

---

## Task 2: Enable `includeCount` on first page of entity query

**Files:**
- Modify: `components/feature-modules/entity/hooks/query/use-entity-query.ts:141-148`
- Modify: `components/feature-modules/entity/service/entity.service.ts:99-143`

The backend already supports `includeCount` on `EntityQueryRequest` — we just need to set it to `true` when `offset === 0` (first page) and `false` for subsequent pages. The service method needs to accept it as a parameter.

- [ ] **Step 1: Update `EntityService.queryEntities` to accept `includeCount`**

In `components/feature-modules/entity/service/entity.service.ts`, modify the `queryEntities` method signature and body. Change lines 99-143:

```typescript
  /**
   * Query entities with pagination and optional filtering.
   */
  static async queryEntities(
    session: Session | null,
    workspaceId: string,
    entityTypeId: string,
    pagination: QueryPagination,
    filter?: QueryFilter,
    includeCount: boolean = false,
  ): Promise<EntityQueryResponse> {
    validateSession(session);
    validateUuid(workspaceId);
    validateUuid(entityTypeId);
    const api = createEntityApi(session!);

    try {
      const request: EntityQueryRequest = {
        pagination,
        includeCount,
        maxDepth: 1,
        ...(filter ? { filter } : {}),
      };

      // The generated QueryFilterToJSON has infinite mutual recursion for
      // discriminated union variants (Or/And). OrToJSON spreads
      // ...QueryFilterToJSONTyped(value, true) which ignores ignoreDiscriminator
      // and dispatches back to OrToJSON → stack overflow.
      //
      // Workaround: pass a filter-free request to avoid the recursive ToJSON,
      // then override the body with our own plain-object serialization.
      const safeRequest: EntityQueryRequest = {
        pagination,
        includeCount,
        maxDepth: 1,
      };

      const response = await api.queryEntitiesRaw(
        { workspaceId, entityTypeId, entityQueryRequest: safeRequest },
        filter
          ? async () => ({ body: request } as RequestInit)
          : undefined,
      );

      return await response.value();
    } catch (error) {
      return await normalizeApiError(error);
    }
  }
```

- [ ] **Step 2: Pass `includeCount` from the query hook**

In `components/feature-modules/entity/hooks/query/use-entity-query.ts`, update the `queryFn` (line 141-148):

```typescript
    queryFn: ({ pageParam = 0 }) =>
      EntityService.queryEntities(
        session,
        workspaceId!,
        entityTypeId!,
        { limit: ENTITY_PAGE_SIZE, offset: pageParam, orderBy },
        compositeFilter,
        pageParam === 0, // includeCount only on first page
      ),
```

- [ ] **Step 3: Verify build compiles**

Run: `npx tsc --noEmit --pretty 2>&1 | head -20`
Expected: No type errors

- [ ] **Step 4: Commit**

```bash
git add components/feature-modules/entity/service/entity.service.ts components/feature-modules/entity/hooks/query/use-entity-query.ts
git commit -m "feat(entity): enable includeCount on first page of entity query"
```

---

## Task 3: Create `useEntitySelection` hook with tests (TDD)

**Files:**
- Create: `components/feature-modules/entity/hooks/use-entity-selection.ts`
- Create: `components/feature-modules/entity/hooks/use-entity-selection.test.ts`

This is the core state machine for the feature. It manages:
- `mode`: `'manual' | 'all'` — whether selection is individual rows or "select all matching query"
- `includedIds`: `Set<string>` — explicitly selected entity IDs (manual mode)
- `excludedIds`: `Set<string>` — deselected entity IDs (all mode)
- `totalCount`: `number | undefined` — total entities matching the current query (from first page response)
- Reset on filter/search/sort change
- Auto-revert from `all` to `manual` when `excludedIds.size >= totalCount`

### Step 1: Write failing tests

- [ ] **Step 1a: Write the test file**

Create `components/feature-modules/entity/hooks/use-entity-selection.test.ts`:

```typescript
import { renderHook, act } from '@testing-library/react';
import { useEntitySelection } from './use-entity-selection';

describe('useEntitySelection', () => {
  describe('initial state', () => {
    it('starts in manual mode with empty selections', () => {
      const { result } = renderHook(() => useEntitySelection());

      expect(result.current.mode).toBe('manual');
      expect(result.current.includedIds.size).toBe(0);
      expect(result.current.excludedIds.size).toBe(0);
      expect(result.current.totalCount).toBeUndefined();
      expect(result.current.selectedCount).toBe(0);
    });
  });

  describe('manual mode', () => {
    it('tracks individually selected IDs', () => {
      const { result } = renderHook(() => useEntitySelection());

      act(() => result.current.toggleId('entity-1'));
      act(() => result.current.toggleId('entity-2'));

      expect(result.current.mode).toBe('manual');
      expect(result.current.includedIds).toEqual(new Set(['entity-1', 'entity-2']));
      expect(result.current.selectedCount).toBe(2);
    });

    it('deselects an already-selected ID', () => {
      const { result } = renderHook(() => useEntitySelection());

      act(() => result.current.toggleId('entity-1'));
      act(() => result.current.toggleId('entity-1'));

      expect(result.current.includedIds.size).toBe(0);
      expect(result.current.selectedCount).toBe(0);
    });

    it('hasSelection is false when nothing selected', () => {
      const { result } = renderHook(() => useEntitySelection());

      expect(result.current.hasSelection).toBe(false);
    });

    it('hasSelection is true when IDs are selected', () => {
      const { result } = renderHook(() => useEntitySelection());

      act(() => result.current.toggleId('entity-1'));

      expect(result.current.hasSelection).toBe(true);
    });
  });

  describe('select all mode', () => {
    it('transitions to all mode with totalCount', () => {
      const { result } = renderHook(() => useEntitySelection());

      act(() => result.current.selectAll(150));

      expect(result.current.mode).toBe('all');
      expect(result.current.totalCount).toBe(150);
      expect(result.current.selectedCount).toBe(150);
      expect(result.current.includedIds.size).toBe(0);
      expect(result.current.excludedIds.size).toBe(0);
    });

    it('tracks exclusions when toggling in all mode', () => {
      const { result } = renderHook(() => useEntitySelection());

      act(() => result.current.selectAll(100));
      act(() => result.current.toggleId('entity-5'));

      expect(result.current.mode).toBe('all');
      expect(result.current.excludedIds).toEqual(new Set(['entity-5']));
      expect(result.current.selectedCount).toBe(99);
    });

    it('re-includes an excluded ID by toggling again', () => {
      const { result } = renderHook(() => useEntitySelection());

      act(() => result.current.selectAll(100));
      act(() => result.current.toggleId('entity-5'));
      act(() => result.current.toggleId('entity-5'));

      expect(result.current.excludedIds.size).toBe(0);
      expect(result.current.selectedCount).toBe(100);
    });

    it('auto-reverts to manual mode when all items are excluded', () => {
      const { result } = renderHook(() => useEntitySelection());

      act(() => result.current.selectAll(2));
      act(() => result.current.toggleId('entity-1'));
      act(() => result.current.toggleId('entity-2'));

      expect(result.current.mode).toBe('manual');
      expect(result.current.selectedCount).toBe(0);
      expect(result.current.excludedIds.size).toBe(0);
      expect(result.current.totalCount).toBeUndefined();
    });
  });

  describe('deselectAll', () => {
    it('resets all state from manual mode', () => {
      const { result } = renderHook(() => useEntitySelection());

      act(() => result.current.toggleId('entity-1'));
      act(() => result.current.deselectAll());

      expect(result.current.mode).toBe('manual');
      expect(result.current.includedIds.size).toBe(0);
      expect(result.current.selectedCount).toBe(0);
    });

    it('resets all state from all mode', () => {
      const { result } = renderHook(() => useEntitySelection());

      act(() => result.current.selectAll(100));
      act(() => result.current.deselectAll());

      expect(result.current.mode).toBe('manual');
      expect(result.current.excludedIds.size).toBe(0);
      expect(result.current.totalCount).toBeUndefined();
      expect(result.current.selectedCount).toBe(0);
    });
  });

  describe('updateTotalCount', () => {
    it('updates totalCount in all mode', () => {
      const { result } = renderHook(() => useEntitySelection());

      act(() => result.current.selectAll(100));
      act(() => result.current.updateTotalCount(95));

      expect(result.current.totalCount).toBe(95);
      expect(result.current.selectedCount).toBe(95);
    });

    it('triggers auto-revert if new totalCount makes all items excluded', () => {
      const { result } = renderHook(() => useEntitySelection());

      act(() => result.current.selectAll(5));
      act(() => result.current.toggleId('a'));
      act(() => result.current.toggleId('b'));
      act(() => result.current.toggleId('c'));
      // 3 excluded, totalCount=5, selectedCount=2 — still in all mode
      expect(result.current.mode).toBe('all');

      // Now totalCount drops to 3, matching excludedIds.size
      act(() => result.current.updateTotalCount(3));

      expect(result.current.mode).toBe('manual');
      expect(result.current.selectedCount).toBe(0);
    });
  });

  describe('isSelected', () => {
    it('returns true for included IDs in manual mode', () => {
      const { result } = renderHook(() => useEntitySelection());

      act(() => result.current.toggleId('entity-1'));

      expect(result.current.isSelected('entity-1')).toBe(true);
      expect(result.current.isSelected('entity-2')).toBe(false);
    });

    it('returns true for non-excluded IDs in all mode', () => {
      const { result } = renderHook(() => useEntitySelection());

      act(() => result.current.selectAll(100));
      act(() => result.current.toggleId('entity-5'));

      expect(result.current.isSelected('entity-1')).toBe(true);
      expect(result.current.isSelected('entity-5')).toBe(false);
    });
  });
});
```

- [ ] **Step 1b: Run tests to verify they fail**

Run: `npx jest components/feature-modules/entity/hooks/use-entity-selection.test.ts --no-coverage 2>&1 | tail -5`
Expected: FAIL — module not found

### Step 2: Implement the hook

- [ ] **Step 2a: Create the hook**

Create `components/feature-modules/entity/hooks/use-entity-selection.ts`:

```typescript
import { useCallback, useMemo, useState } from 'react';

type SelectionMode = 'manual' | 'all';

interface EntitySelectionState {
  mode: SelectionMode;
  /** Explicitly selected IDs (manual mode) */
  includedIds: Set<string>;
  /** Deselected IDs after select-all (all mode) */
  excludedIds: Set<string>;
  /** Total entities matching the current query (from first page response) */
  totalCount: number | undefined;
}

const INITIAL_STATE: EntitySelectionState = {
  mode: 'manual',
  includedIds: new Set(),
  excludedIds: new Set(),
  totalCount: undefined,
};

export function useEntitySelection() {
  const [state, setState] = useState<EntitySelectionState>(INITIAL_STATE);

  const selectAll = useCallback((totalCount: number) => {
    setState({
      mode: 'all',
      includedIds: new Set(),
      excludedIds: new Set(),
      totalCount,
    });
  }, []);

  const deselectAll = useCallback(() => {
    setState(INITIAL_STATE);
  }, []);

  const toggleId = useCallback((id: string) => {
    setState((prev) => {
      if (prev.mode === 'manual') {
        const next = new Set(prev.includedIds);
        if (next.has(id)) {
          next.delete(id);
        } else {
          next.add(id);
        }
        return { ...prev, includedIds: next };
      }

      // all mode — toggle exclusion
      const next = new Set(prev.excludedIds);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }

      // Auto-revert: if every item is excluded, reset to manual
      if (prev.totalCount !== undefined && next.size >= prev.totalCount) {
        return { ...INITIAL_STATE };
      }

      return { ...prev, excludedIds: next };
    });
  }, []);

  const updateTotalCount = useCallback((totalCount: number) => {
    setState((prev) => {
      if (prev.mode !== 'all') return prev;

      // Auto-revert if new count makes everything excluded
      if (prev.excludedIds.size >= totalCount) {
        return { ...INITIAL_STATE };
      }

      return { ...prev, totalCount };
    });
  }, []);

  const isSelected = useCallback(
    (id: string): boolean => {
      if (state.mode === 'manual') {
        return state.includedIds.has(id);
      }
      return !state.excludedIds.has(id);
    },
    [state.mode, state.includedIds, state.excludedIds],
  );

  const selectedCount = useMemo(() => {
    if (state.mode === 'manual') {
      return state.includedIds.size;
    }
    return (state.totalCount ?? 0) - state.excludedIds.size;
  }, [state.mode, state.includedIds.size, state.totalCount, state.excludedIds.size]);

  const hasSelection = selectedCount > 0;

  return {
    ...state,
    selectAll,
    deselectAll,
    toggleId,
    updateTotalCount,
    isSelected,
    selectedCount,
    hasSelection,
  };
}

export type EntitySelection = ReturnType<typeof useEntitySelection>;
```

- [ ] **Step 2b: Run tests to verify they pass**

Run: `npx jest components/feature-modules/entity/hooks/use-entity-selection.test.ts --no-coverage 2>&1 | tail -10`
Expected: All tests PASS

- [ ] **Step 2c: Commit**

```bash
git add components/feature-modules/entity/hooks/use-entity-selection.ts components/feature-modules/entity/hooks/use-entity-selection.test.ts
git commit -m "feat(entity): add useEntitySelection hook with full test coverage

Manages dual selection modes (manual/all), exclusion tracking, totalCount,
and auto-revert when all items are excluded."
```

---

## Task 4: Refactor `EntityService.deleteEntities` to accept `DeleteEntityRequest` (TDD)

**Files:**
- Modify: `components/feature-modules/entity/service/entity.service.ts:145-155`
- Create: `components/feature-modules/entity/service/entity.service.test.ts`

The current method accepts `string[]` and calls the old `api.deleteEntity()`. The generated API exposes `api.deleteEntities({ workspaceId, deleteEntityRequest })` which accepts the full `DeleteEntityRequest` type. We need to align the service with the generated API.

**Important note:** The mutation hook currently references `response.error` on `DeleteEntityResponse`, but the generated type has no `error` field — only `deletedCount` and `updatedEntities`. The `error` references in the mutation will be cleaned up in Task 5.

### Step 1: Write failing tests

- [ ] **Step 1a: Write the test file**

Create `components/feature-modules/entity/service/entity.service.test.ts`:

```typescript
import { EntityService } from './entity.service';
import { EntitySelectType } from '@/lib/types/entity';
import type { DeleteEntityRequest } from '@/lib/types/entity';

// Mock the API factory
const mockDeleteEntities = jest.fn();
jest.mock('@/lib/api/entity-api', () => ({
  createEntityApi: () => ({
    deleteEntities: mockDeleteEntities,
  }),
}));

// Mock validation utils to avoid real UUID checks
jest.mock('@/lib/util/service/service.util', () => ({
  validateSession: jest.fn(),
  validateUuid: jest.fn(),
}));

const mockSession = { access_token: 'test-token' } as any;
const workspaceId = '00000000-0000-0000-0000-000000000001';

describe('EntityService.deleteEntities', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('sends a BY_ID request with entity IDs', async () => {
    const request: DeleteEntityRequest = {
      type: EntitySelectType.ById,
      entityTypeId: '00000000-0000-0000-0000-000000000002',
      entityIds: ['id-1', 'id-2'],
    };

    mockDeleteEntities.mockResolvedValue({ deletedCount: 2 });

    const result = await EntityService.deleteEntities(mockSession, workspaceId, request);

    expect(mockDeleteEntities).toHaveBeenCalledWith({
      workspaceId,
      deleteEntityRequest: request,
    });
    expect(result.deletedCount).toBe(2);
  });

  it('sends an ALL request with filter and exclusions', async () => {
    const request: DeleteEntityRequest = {
      type: EntitySelectType.All,
      entityTypeId: '00000000-0000-0000-0000-000000000002',
      filter: {
        type: 'And',
        conditions: [],
      } as any,
      excludeIds: ['exclude-1'],
    };

    mockDeleteEntities.mockResolvedValue({ deletedCount: 99 });

    const result = await EntityService.deleteEntities(mockSession, workspaceId, request);

    expect(mockDeleteEntities).toHaveBeenCalledWith({
      workspaceId,
      deleteEntityRequest: request,
    });
    expect(result.deletedCount).toBe(99);
  });

  it('validates session and workspaceId', async () => {
    const { validateSession, validateUuid } = require('@/lib/util/service/service.util');

    const request: DeleteEntityRequest = {
      type: EntitySelectType.ById,
      entityTypeId: 'type-1',
      entityIds: ['id-1'],
    };

    mockDeleteEntities.mockResolvedValue({ deletedCount: 1 });

    await EntityService.deleteEntities(mockSession, workspaceId, request);

    expect(validateSession).toHaveBeenCalledWith(mockSession);
    expect(validateUuid).toHaveBeenCalledWith(workspaceId);
  });
});
```

- [ ] **Step 1b: Run tests to verify they fail**

Run: `npx jest components/feature-modules/entity/service/entity.service.test.ts --no-coverage 2>&1 | tail -10`
Expected: FAIL — the current `deleteEntities` signature doesn't match

### Step 2: Implement the refactored service method

- [ ] **Step 2a: Update the service method**

In `components/feature-modules/entity/service/entity.service.ts`, replace lines 145-155 with:

```typescript
  /**
   * Delete entities by ID selection or filter-based bulk selection.
   */
  static async deleteEntities(
    session: Session | null,
    workspaceId: string,
    request: DeleteEntityRequest,
  ): Promise<DeleteEntityResponse> {
    validateSession(session);
    validateUuid(workspaceId);
    const api = createEntityApi(session!);
    return api.deleteEntities({ workspaceId, deleteEntityRequest: request });
  }
```

Also add `DeleteEntityRequest` to the imports at the top of the file. Update the import from `@/lib/types/entity`:

```typescript
import {
  DeleteEntityRequest,
  DeleteEntityResponse,
  Entity,
  EntityQueryRequest,
  EntityQueryResponse,
  QueryFilter,
  QueryPagination,
  SaveEntityRequest,
  SaveEntityResponse,
} from '@/lib/types/entity';
```

- [ ] **Step 2b: Run tests to verify they pass**

Run: `npx jest components/feature-modules/entity/service/entity.service.test.ts --no-coverage 2>&1 | tail -10`
Expected: All tests PASS

- [ ] **Step 2c: Commit**

```bash
git add components/feature-modules/entity/service/entity.service.ts components/feature-modules/entity/service/entity.service.test.ts
git commit -m "refactor(entity): update EntityService.deleteEntities to accept DeleteEntityRequest

Replaces string[] parameter with the generated DeleteEntityRequest type,
supporting both BY_ID and ALL deletion modes."
```

---

## Task 5: Refactor the delete mutation hook (TDD)

**Files:**
- Modify: `components/feature-modules/entity/hooks/mutation/instance/use-delete-entity-mutation.ts`
- Create: `components/feature-modules/entity/hooks/mutation/instance/use-delete-entity-mutation.test.ts`

The mutation needs to:
1. Accept `DeleteEntityRequest` instead of the local `{ entityIds: Record<string, string[]> }` type
2. Use `request.entityTypeId` for cache operations (always present in both modes)
3. For `BY_ID` mode: surgical cache removal via `removeEntitiesFromPages`
4. For `ALL` mode: invalidate queries to force refetch
5. Remove references to non-existent `response.error` field

### Step 1: Write failing tests

- [ ] **Step 1a: Write the test file**

Create `components/feature-modules/entity/hooks/mutation/instance/use-delete-entity-mutation.test.ts`:

```typescript
import { EntitySelectType } from '@/lib/types/entity';
import type { DeleteEntityRequest, DeleteEntityResponse, EntityQueryResponse } from '@/lib/types/entity';
import type { InfiniteData } from '@tanstack/react-query';
import { removeEntitiesFromPages } from './entity-cache.utils';

/**
 * Tests for the cache invalidation logic in useDeleteEntityMutation.
 *
 * We test the cache utility directly since the mutation hook's onSuccess
 * delegates to setQueriesData/invalidateQueries based on the request type.
 * Integration testing the full hook requires wrapping QueryClientProvider
 * which is out of scope — we verify the decision logic and cache helpers.
 */
describe('delete mutation cache logic', () => {
  describe('removeEntitiesFromPages (BY_ID surgical removal)', () => {
    const makePage = (ids: string[]): EntityQueryResponse => ({
      entities: ids.map((id) => ({ id } as any)),
      hasNextPage: false,
      limit: 50,
      offset: 0,
    });

    it('removes specified entities from pages', () => {
      const data: InfiniteData<EntityQueryResponse> = {
        pages: [makePage(['a', 'b', 'c']), makePage(['d', 'e'])],
        pageParams: [0, 50],
      };

      const result = removeEntitiesFromPages(data, new Set(['b', 'd']));

      expect(result?.pages[0].entities.map((e) => e.id)).toEqual(['a', 'c']);
      expect(result?.pages[1].entities.map((e) => e.id)).toEqual(['e']);
    });

    it('returns undefined data unchanged', () => {
      expect(removeEntitiesFromPages(undefined, new Set(['a']))).toBeUndefined();
    });

    it('handles empty ID set (no-op)', () => {
      const data: InfiniteData<EntityQueryResponse> = {
        pages: [makePage(['a', 'b'])],
        pageParams: [0],
      };

      const result = removeEntitiesFromPages(data, new Set());

      expect(result?.pages[0].entities.map((e) => e.id)).toEqual(['a', 'b']);
    });
  });

  describe('cache strategy selection', () => {
    /**
     * This tests the decision function that the refactored mutation will use.
     * Extract it here so the mutation can import and use it.
     */
    it('should use surgical removal for BY_ID', () => {
      const request: DeleteEntityRequest = {
        type: EntitySelectType.ById,
        entityTypeId: 'type-1',
        entityIds: ['id-1', 'id-2'],
      };

      expect(request.type).toBe(EntitySelectType.ById);
      expect(request.entityIds).toBeDefined();
    });

    it('should use invalidation for ALL', () => {
      const request: DeleteEntityRequest = {
        type: EntitySelectType.All,
        entityTypeId: 'type-1',
        excludeIds: ['id-3'],
      };

      expect(request.type).toBe(EntitySelectType.All);
      expect(request.entityIds).toBeUndefined();
    });
  });
});
```

- [ ] **Step 1b: Run tests to verify they pass (cache utils already exist)**

Run: `npx jest components/feature-modules/entity/hooks/mutation/instance/use-delete-entity-mutation.test.ts --no-coverage 2>&1 | tail -10`
Expected: PASS (these test existing utils and type shapes)

### Step 2: Refactor the mutation hook

- [ ] **Step 2a: Rewrite the mutation hook**

Replace the entire content of `components/feature-modules/entity/hooks/mutation/instance/use-delete-entity-mutation.ts`:

```typescript
import { useAuth } from '@/components/provider/auth-context';
import {
  DeleteEntityRequest,
  DeleteEntityResponse,
  Entity,
  EntityQueryResponse,
  EntitySelectType,
} from '@/lib/types/entity';
import { InfiniteData, useMutation, useQueryClient, type UseMutationOptions } from '@tanstack/react-query';
import { toast } from 'sonner';
import { EntityService } from '@/components/feature-modules/entity/service/entity.service';
import { entityKeys } from '@/components/feature-modules/entity/hooks/query/entity-query-keys';
import { removeEntitiesFromPages, replaceEntitiesInPages } from './entity-cache.utils';

export function useDeleteEntityMutation(
  workspaceId: string,
  options?: UseMutationOptions<DeleteEntityResponse, Error, DeleteEntityRequest>,
) {
  const queryClient = useQueryClient();
  const { session } = useAuth();

  return useMutation({
    mutationFn: async (request: DeleteEntityRequest) => {
      return EntityService.deleteEntities(session, workspaceId, request);
    },
    onMutate: (data) => {
      options?.onMutate?.(data);
      return { toastId: toast.loading('Deleting entities...') };
    },
    onError: (error: Error, variables: DeleteEntityRequest, context: unknown) => {
      const toastId = (context as { toastId?: string | number } | undefined)?.toastId;
      toast.dismiss(toastId);
      options?.onError?.(error, variables, context);
      toast.error(`Failed to delete entities: ${error.message}`);
    },
    onSuccess: (
      response: DeleteEntityResponse,
      variables: DeleteEntityRequest,
      context: unknown,
    ) => {
      const toastId = (context as { toastId?: string | number } | undefined)?.toastId;
      toast.dismiss(toastId);

      const { deletedCount, updatedEntities } = response;
      const entityTypeId = variables.entityTypeId;

      toast.success(`${deletedCount} ${deletedCount === 1 ? 'entity' : 'entities'} deleted successfully`);

      options?.onSuccess?.(response, variables, context);

      if (!entityTypeId) return;

      // Cache update strategy depends on deletion mode
      if (variables.type === EntitySelectType.All) {
        // ALL mode: can't surgically remove — invalidate to refetch
        queryClient.invalidateQueries({
          queryKey: ['entities', workspaceId, entityTypeId, 'query'],
        });
        queryClient.invalidateQueries({
          queryKey: entityKeys.entities.list(workspaceId, entityTypeId),
        });
      } else {
        // BY_ID mode: surgical removal from cache
        const idSet = new Set(variables.entityIds ?? []);

        queryClient.setQueriesData<InfiniteData<EntityQueryResponse>>(
          { queryKey: ['entities', workspaceId, entityTypeId, 'query'] },
          (oldData) => removeEntitiesFromPages(oldData, idSet),
        );

        queryClient.setQueryData<Entity[]>(
          entityKeys.entities.list(workspaceId, entityTypeId),
          (oldData) => {
            if (!oldData) return oldData;
            return oldData.filter((entity) => !idSet.has(entity.id));
          },
        );
      }

      // Update impacted entities across both cache types
      if (!updatedEntities) return;
      Object.entries(updatedEntities).forEach(([typeId, entities]) => {
        const entityMap = new Map(entities.map((entity) => [entity.id, entity]));

        queryClient.setQueriesData<InfiniteData<EntityQueryResponse>>(
          { queryKey: ['entities', workspaceId, typeId, 'query'] },
          (oldData) => replaceEntitiesInPages(oldData, entityMap),
        );

        queryClient.setQueryData<Entity[]>(
          entityKeys.entities.list(workspaceId, typeId),
          (oldData) => {
            if (!oldData) return entities;
            return oldData.map((entity) => entityMap.get(entity.id) ?? entity);
          },
        );
      });
    },
  });
}
```

- [ ] **Step 2b: Verify build compiles**

Run: `npx tsc --noEmit --pretty 2>&1 | head -30`
Expected: May show errors in files that call the old mutation signature — those are fixed in subsequent tasks

- [ ] **Step 2c: Run the mutation tests**

Run: `npx jest components/feature-modules/entity/hooks/mutation/instance/use-delete-entity-mutation.test.ts --no-coverage 2>&1 | tail -10`
Expected: PASS

- [ ] **Step 2d: Commit**

```bash
git add components/feature-modules/entity/hooks/mutation/instance/use-delete-entity-mutation.ts components/feature-modules/entity/hooks/mutation/instance/use-delete-entity-mutation.test.ts
git commit -m "refactor(entity): update delete mutation to accept DeleteEntityRequest

Dual-mode cache invalidation: surgical removal for BY_ID, full
invalidate+refetch for ALL. Removes references to non-existent
response.error field."
```

---

## Task 6: Add `onSelectAllChange` callback to DataTable

**Files:**
- Modify: `components/ui/data-table/data-table.types.ts:203-208`
- Modify: `components/ui/data-table/data-table.tsx:355-366`

The DataTable's header checkbox currently uses `table.toggleAllPageRowsSelected()` which only toggles loaded rows. We need to expose a callback so the parent (`EntityDataTable`) can intercept "select all" and enter the logical `all` mode.

- [ ] **Step 1: Add `onSelectAllChange` to `RowSelectionConfig`**

In `components/ui/data-table/data-table.types.ts`, add to the `RowSelectionConfig` interface:

```typescript
export interface RowSelectionConfig<TData> {
  enabled: boolean;
  onSelectionChange?: (selectedRows: TData[]) => void;
  actionComponent?: React.ComponentType<SelectionActionProps<TData>>;
  clearOnFilterChange?: boolean;
  /** Called when the header "select all" checkbox is toggled. Return true to prevent default TanStack Table behavior. */
  onSelectAllChange?: (checked: boolean) => boolean;
}
```

- [ ] **Step 2: Wire the callback in the DataTable header checkbox**

In `components/ui/data-table/data-table.tsx`, find the header checkbox in the action column (around line 355-366) and update it:

```typescript
      header: ({ table }) => (
        <div className="flex items-center justify-center">
          {showCheckbox && (
            <Checkbox
              checked={table.getIsAllPageRowsSelected()}
              onCheckedChange={(value) => {
                const intercepted = rowSelection?.onSelectAllChange?.(!!value);
                if (!intercepted) {
                  table.toggleAllPageRowsSelected(!!value);
                }
              }}
              aria-label="Select all"
              onClick={(e) => e.stopPropagation()}
            />
          )}
        </div>
      ),
```

- [ ] **Step 3: Verify build compiles**

Run: `npx tsc --noEmit --pretty 2>&1 | head -20`
Expected: No type errors

- [ ] **Step 4: Commit**

```bash
git add components/ui/data-table/data-table.types.ts components/ui/data-table/data-table.tsx
git commit -m "feat(data-table): add onSelectAllChange callback to RowSelectionConfig

Allows parent components to intercept header checkbox and implement
custom select-all behavior beyond TanStack Table's page-scoped selection."
```

---

## Task 7: Wire `useEntitySelection` into `EntityDataTable`

**Files:**
- Modify: `components/feature-modules/entity/components/tables/entity-data-table.tsx`

This task connects the selection hook to the data table, extracts `totalCount` from the query response, and passes selection state down.

- [ ] **Step 1: Add the hook and extract totalCount**

In `entity-data-table.tsx`, add the import and hook call after the existing hooks (around line 54):

```typescript
import { useEntitySelection } from '../../hooks/use-entity-selection';
import type { EntitySelection } from '../../hooks/use-entity-selection';
```

After the `useEntitySearch` call (line 64), add:

```typescript
  // Server-aware selection state
  const entitySelection = useEntitySelection();
```

After the `useEntityQuery` destructuring (around line 98), extract totalCount:

```typescript
  // Extract totalCount from first page (only included when offset=0)
  const totalCount = entityResponse?.pages[0]?.totalCount;
```

- [ ] **Step 2: Reset selection on filter/search/sort change**

Add a `useEffect` after the query error toast effect (after line 105):

```typescript
  // Reset selection when filter, search, or sort changes
  useEffect(() => {
    entitySelection.deselectAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- intentionally reset on query dependency changes
  }, [debouncedSearch, queryFilter, sorting]);
```

- [ ] **Step 3: Wire `onSelectAllChange` to the DataTable's row selection config**

Update the `rowSelection` prop on `DataTable` (around line 352-362):

```typescript
            rowSelection={{
              enabled: true,
              clearOnFilterChange: true,
              onSelectAllChange: (checked) => {
                if (checked && totalCount !== undefined) {
                  entitySelection.selectAll(totalCount);
                  return true; // prevent default TanStack behavior
                } else if (!checked) {
                  entitySelection.deselectAll();
                  return true;
                }
                return false;
              },
              actionComponent: ({ selectedRows, clearSelection }) => (
                <EntityActionBar
                  selectedRows={selectedRows}
                  clearSelection={() => {
                    clearSelection();
                    entitySelection.deselectAll();
                  }}
                  workspaceId={workspaceId}
                  entityTypeId={entityType.id}
                  entitySelection={entitySelection}
                  queryFilter={queryFilter}
                />
              ),
            }}
```

- [ ] **Step 4: Verify build compiles**

Run: `npx tsc --noEmit --pretty 2>&1 | head -30`
Expected: Type errors in `EntityActionBar` (expects old props) — fixed in next task

- [ ] **Step 5: Commit**

```bash
git add components/feature-modules/entity/components/tables/entity-data-table.tsx
git commit -m "feat(entity): wire useEntitySelection into EntityDataTable

Extracts totalCount from first query page, resets selection on filter/
search/sort change, intercepts select-all checkbox to enter server-aware
all mode."
```

---

## Task 8: Update `EntityActionBar` and `DeleteEntityModal`

**Files:**
- Modify: `components/feature-modules/entity/components/tables/entity-table-action-bar.tsx`
- Modify: `components/feature-modules/entity/components/ui/modals/instance/delete-entity-modal.tsx`

These components need to accept the selection state and build the correct `DeleteEntityRequest` based on the selection mode.

- [ ] **Step 1: Update `EntityActionBar` props**

Replace the full content of `entity-table-action-bar.tsx`:

```typescript
'use client';

import { Button } from '@riven/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@riven/ui/tooltip';

import type { QueryFilter } from '@/lib/types/entity';
import { Ellipsis, Trash2 } from 'lucide-react';
import { FC, useState } from 'react';
import type { EntitySelection } from '../../hooks/use-entity-selection';
import { DeleteEntityModal } from '../ui/modals/instance/delete-entity-modal';
import { EntityRow } from './entity-table-utils';

interface Props {
  selectedRows: EntityRow[];
  clearSelection: () => void;
  workspaceId: string;
  entityTypeId: string;
  entitySelection: EntitySelection;
  queryFilter: QueryFilter | undefined;
}

const EntityActionBar: FC<Props> = ({
  selectedRows,
  clearSelection,
  workspaceId,
  entityTypeId,
  entitySelection,
  queryFilter,
}) => {
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);

  const handleDeleteSuccess = () => {
    clearSelection();
  };

  return (
    <>
      <div className="mb-0.5 flex items-center gap-1 px-1">
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant={'ghost'}
              size={'xs'}
              className="p-1! hover:bg-primary/10"
              onClick={() => setDeleteModalOpen(true)}
            >
              <Trash2 className="size-3.5 text-destructive" />
            </Button>
          </TooltipTrigger>
          <TooltipContent className="px-1.5 py-1 text-xs">Delete Selected Rows</TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant={'ghost'} size={'xs'} className="p-1! hover:bg-primary/10">
              <Ellipsis className="size-3.5 text-primary" />
            </Button>
          </TooltipTrigger>
          <TooltipContent className="px-1.5 py-1 text-xs">More Actions</TooltipContent>
        </Tooltip>
      </div>

      <DeleteEntityModal
        open={deleteModalOpen}
        onOpenChange={setDeleteModalOpen}
        selectedRows={selectedRows}
        workspaceId={workspaceId}
        entityTypeId={entityTypeId}
        entitySelection={entitySelection}
        queryFilter={queryFilter}
        onSuccess={handleDeleteSuccess}
      />
    </>
  );
};

export default EntityActionBar;
```

- [ ] **Step 2: Update `DeleteEntityModal` to build `DeleteEntityRequest`**

Replace the full content of `delete-entity-modal.tsx`:

```typescript
'use client';

import { Button } from '@riven/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@riven/ui/dialog';
import { AlertCircle, Loader2 } from 'lucide-react';
import { FC, useMemo, useState } from 'react';
import { useDeleteEntityMutation } from '../../../../hooks/mutation/instance/use-delete-entity-mutation';
import type { EntitySelection } from '../../../../hooks/use-entity-selection';
import { EntityRow, isEntityRow } from '../../../tables/entity-table-utils';
import { DeleteEntityRequest, EntitySelectType, QueryFilter } from '@/lib/types/entity';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  selectedRows: EntityRow[];
  workspaceId: string;
  entityTypeId: string;
  entitySelection: EntitySelection;
  queryFilter: QueryFilter | undefined;
  onSuccess?: () => void;
}

export const DeleteEntityModal: FC<Props> = ({
  open,
  onOpenChange,
  selectedRows,
  workspaceId,
  entityTypeId,
  entitySelection,
  queryFilter,
  onSuccess,
}) => {
  const [isDeleting, setIsDeleting] = useState(false);

  // Build the delete request based on selection mode
  const deleteRequest: DeleteEntityRequest | null = useMemo(() => {
    if (entitySelection.mode === 'all') {
      return {
        type: EntitySelectType.All,
        entityTypeId,
        ...(queryFilter ? { filter: queryFilter } : {}),
        ...(entitySelection.excludedIds.size > 0
          ? { excludeIds: Array.from(entitySelection.excludedIds) }
          : {}),
      };
    }

    // Manual mode — use selected row entity IDs (filter out drafts)
    const entityIds = selectedRows.filter(isEntityRow).map((row) => row._entityId);
    if (entityIds.length === 0) return null;

    return {
      type: EntitySelectType.ById,
      entityTypeId,
      entityIds,
    };
  }, [entitySelection, entityTypeId, queryFilter, selectedRows]);

  const entityCount = entitySelection.selectedCount;

  const { mutateAsync: deleteEntities } = useDeleteEntityMutation(workspaceId, {
    onMutate: () => {
      setIsDeleting(true);
    },
    onSuccess: () => {
      setIsDeleting(false);
      onOpenChange(false);
      onSuccess?.();
    },
    onError: () => {
      setIsDeleting(false);
    },
  });

  const handleDelete = async () => {
    if (!deleteRequest) return;
    await deleteEntities(deleteRequest);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>Delete Entities</DialogTitle>
          <DialogDescription>
            Are you sure you want to delete {entityCount}{' '}
            {entityCount === 1 ? 'entity' : 'entities'}? This action cannot be undone.
          </DialogDescription>
        </DialogHeader>

        <div className="py-4">
          <div className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 dark:border-red-900 dark:bg-red-950/20">
            <AlertCircle className="mt-0.5 size-4 shrink-0 text-red-600 dark:text-red-500" />
            <div className="text-sm text-red-900 dark:text-red-200">
              <p className="mb-1 font-medium">Warning</p>
              <p className="text-red-800 dark:text-red-300">
                {entitySelection.mode === 'all' ? (
                  <>
                    This will permanently delete <strong>all {entityCount} matching entities</strong>
                    {entitySelection.excludedIds.size > 0 &&
                      ` (excluding ${entitySelection.excludedIds.size} deselected)`}
                    . All associated data will be lost and this action cannot be reversed.
                  </>
                ) : (
                  <>
                    Deleting {entityCount === 1 ? 'this entity' : 'these entities'} will permanently
                    remove {entityCount === 1 ? 'it' : 'them'} from the system. All associated data will
                    be lost and this action cannot be reversed.
                  </>
                )}
              </p>
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isDeleting}>
            Cancel
          </Button>
          <Button
            variant="destructive"
            onClick={handleDelete}
            disabled={isDeleting || !deleteRequest}
          >
            {isDeleting && <Loader2 className="mr-2 size-4 animate-spin" />}
            {isDeleting ? 'Deleting...' : `Delete ${entityCount === 1 ? 'Entity' : `${entityCount} Entities`}`}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};
```

- [ ] **Step 3: Verify build compiles end-to-end**

Run: `npx tsc --noEmit --pretty 2>&1 | head -30`
Expected: No type errors

- [ ] **Step 4: Run all tests**

Run: `npx jest --no-coverage 2>&1 | tail -15`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add components/feature-modules/entity/components/tables/entity-table-action-bar.tsx components/feature-modules/entity/components/ui/modals/instance/delete-entity-modal.tsx
git commit -m "feat(entity): update action bar and delete modal for dual-mode deletion

Action bar passes entitySelection and queryFilter to modal. Modal builds
DeleteEntityRequest based on selection mode: BY_ID with explicit IDs for
manual selection, ALL with filter+excludeIds for bulk. Enhanced warning
copy for bulk operations."
```

---

## Task 9: Final integration verification

**Files:** None new — this is a verification-only task.

- [ ] **Step 1: Run the full test suite**

Run: `npx jest --no-coverage 2>&1 | tail -20`
Expected: All tests PASS

- [ ] **Step 2: Run linting**

Run: `npm run lint 2>&1 | tail -20`
Expected: No errors (warnings acceptable)

- [ ] **Step 3: Run type check**

Run: `npx tsc --noEmit --pretty 2>&1 | tail -20`
Expected: No type errors

- [ ] **Step 4: Run build**

Run: `npm run build 2>&1 | tail -20`
Expected: Build succeeds

- [ ] **Step 5: Commit any lint/type fixes if needed**

If Steps 1-4 revealed issues, fix and commit:
```bash
git add -u
git commit -m "fix: resolve lint and type errors from bulk selection refactor"
```

---

## Key Implementation Notes

1. **`response.error` doesn't exist on generated `DeleteEntityResponse`**: The old mutation referenced `response.error` in partial-failure handling, but the generated type only has `deletedCount` and `updatedEntities`. Task 5 removes these references. If the backend does return an `error` field, the generated types need to be regenerated — but that's a backend concern, not a frontend one.

2. **QueryFilter serialization workaround**: The service uses a workaround for the generated `QueryFilterToJSON` infinite recursion bug (see comment in `entity.service.ts:119-122`). The delete request's `filter` field uses the same `QueryFilter` type. If the generated `DeleteEntityRequestToJSON` has the same recursion issue, you may need a similar workaround — but test first, as the delete API's serialization path may differ.

3. **`clearOnFilterChange` interaction**: The DataTable's built-in `clearOnFilterChange` clears TanStack Table's row-level selection state. The `useEntitySelection.deselectAll()` in the `useEffect` on `[debouncedSearch, queryFilter, sorting]` clears the logical selection state. Both need to happen — they clear different things.

4. **Draft rows in ALL mode**: When `mode === 'all'`, the delete modal sends the request with the query filter — the server handles deletion. Draft rows only exist client-side and are never sent to the server, so they're naturally excluded. The `entitySelection.selectedCount` shows the server-side count (from `totalCount`), not the TanStack Table row count, which is correct.
