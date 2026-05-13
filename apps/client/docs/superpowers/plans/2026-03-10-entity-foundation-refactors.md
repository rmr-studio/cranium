# Phase 1: Entity Foundation Refactors Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-dcranium-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the entity module's foundational infrastructure — centralized query keys, auth-gated query wrapper, error normalization improvements, and performance fixes — to create a stable base for testing and observability work in subsequent phases.

**Architecture:** Six independent refactors that reduce duplication, fix performance bugs, and improve error context. Each produces a focused, independently testable change. The query key factory becomes the single source of truth for all entity cache keys. The auth query wrapper eliminates duplicated auth-gating logic. Error normalization gains context preservation for PostHog integration (Phase 3).

**Tech Stack:** TypeScript, TanStack Query v5, Zustand, React Hook Form, Zod, Next.js 15 App Router

---

## File Structure

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `components/feature-modules/entity/hooks/query/entity-query-keys.ts` | Centralized query key factory for all entity-related queries |
| Create | `hooks/query/use-authenticated-query.ts` | Shared wrapper hook for auth-gated TanStack queries |
| Modify | `lib/util/error/error.util.ts` | Add context preservation, double-normalization guard, 409 parsing fix |
| Modify | `components/feature-modules/entity/context/entity-provider.tsx` | Stabilize useMemo dependencies |
| Modify | `components/feature-modules/entity/components/tables/entity-table-utils.tsx` | Memoization of column generation not needed here — done at call site |
| Modify | `components/feature-modules/entity/components/tables/entity-data-table.tsx` | Add useMemo wrappers for column/filter/search generation |
| Modify | `components/feature-modules/entity/hooks/query/use-entities.ts` | Use query key factory + auth wrapper |
| Modify | `components/feature-modules/entity/hooks/query/type/use-entity-types.ts` | Use query key factory + auth wrapper |
| Modify | `components/feature-modules/entity/hooks/query/type/use-semantic-metadata.ts` | Use query key factory + auth wrapper |
| Modify | `components/feature-modules/entity/hooks/mutation/instance/use-save-entity-mutation.ts` | Use query key factory |
| Modify | `components/feature-modules/entity/hooks/mutation/instance/use-delete-entity-mutation.ts` | Use query key factory |
| Modify | `components/feature-modules/entity/hooks/mutation/type/use-publish-type-mutation.ts` | Use query key factory |
| Modify | `components/feature-modules/entity/hooks/mutation/type/use-save-configuration-mutation.ts` | Use query key factory |
| Modify | `components/feature-modules/entity/hooks/mutation/type/use-save-definition-mutation.ts` | Use query key factory |
| Modify | `components/feature-modules/entity/hooks/mutation/type/use-delete-definition-mutation.ts` | Use query key factory |
| Modify | `components/feature-modules/entity/hooks/mutation/type/use-delete-type-mutation.ts` | Use query key factory |
| Create | `components/feature-modules/entity/hooks/query/entity-query-keys.test.ts` | Tests for query key factory |
| Create | `hooks/query/use-authenticated-query.test.ts` | Tests for auth wrapper |
| Create | `lib/util/error/error.util.test.ts` | Tests for error normalization changes |

---

## Chunk 1: Centralized Query Key Factory

### Task 1: Create entity query key factory

**Files:**
- Create: `components/feature-modules/entity/hooks/query/entity-query-keys.ts`
- Test: `components/feature-modules/entity/hooks/query/entity-query-keys.test.ts`

**Context:** Currently, query keys are scattered as magic strings across 5 query hooks and 7 mutation hooks. Examples:
- `['entities', workspaceId, typeId]` in `use-entities.ts:13`
- `['entityTypes', workspaceId]` in `use-entity-types.ts:14`
- `['entityType', key, workspaceId]` in `use-entity-types.ts:32`
- `['semanticMetadata', workspaceId, entityTypeId]` in `use-semantic-metadata.ts:15`
- These same strings are duplicated in mutation `onSuccess` handlers for cache invalidation

- [ ] **Step 1: Write the failing test for query key factory**

Create the test file first. These test the exact key structures that query hooks and mutations depend on.

```typescript
// components/feature-modules/entity/hooks/query/entity-query-keys.test.ts
import { entityKeys } from './entity-query-keys';

describe('entityKeys', () => {
  const workspaceId = 'ws-123';
  const typeId = 'type-456';
  const typeKey = 'contacts';
  const entityTypeId = 'et-789';

  describe('entities', () => {
    it('returns base key for entity list invalidation', () => {
      expect(entityKeys.entities.base(workspaceId)).toEqual(['entities', workspaceId]);
    });

    it('returns full key for specific entity type list', () => {
      expect(entityKeys.entities.list(workspaceId, typeId)).toEqual([
        'entities',
        workspaceId,
        typeId,
      ]);
    });
  });

  describe('entityTypes', () => {
    it('returns key for entity types list', () => {
      expect(entityKeys.entityTypes.list(workspaceId)).toEqual(['entityTypes', workspaceId]);
    });

    it('returns key for single entity type by key', () => {
      expect(entityKeys.entityTypes.byKey(typeKey, workspaceId)).toEqual([
        'entityType',
        typeKey,
        workspaceId,
      ]);
    });

    it('returns key for single entity type by key with include', () => {
      expect(entityKeys.entityTypes.byKey(typeKey, workspaceId, ['relationships'])).toEqual([
        'entityType',
        typeKey,
        workspaceId,
        ['relationships'],
      ]);
    });
  });

  describe('semanticMetadata', () => {
    it('returns key for semantic metadata', () => {
      expect(entityKeys.semanticMetadata(workspaceId, entityTypeId)).toEqual([
        'semanticMetadata',
        workspaceId,
        entityTypeId,
      ]);
    });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --testPathPattern="entity-query-keys" --verbose`
Expected: FAIL — `entity-query-keys` module not found

- [ ] **Step 3: Implement the query key factory**

```typescript
// components/feature-modules/entity/hooks/query/entity-query-keys.ts

/**
 * Centralized query key factory for all entity-related TanStack Query keys.
 *
 * Every query hook and mutation cache operation MUST use these keys.
 * This prevents key drift and makes cache invalidation predictable.
 */
export const entityKeys = {
  entities: {
    /** Base key for broad invalidation of all entity lists in a workspace */
    base: (workspaceId: string) => ['entities', workspaceId] as const,
    /** Full key for a specific entity type's entity list */
    list: (workspaceId: string, typeId: string) =>
      ['entities', workspaceId, typeId] as const,
  },
  entityTypes: {
    /** Key for the full entity types list in a workspace */
    list: (workspaceId: string) => ['entityTypes', workspaceId] as const,
    /** Key for a single entity type by its key (not UUID) */
    byKey: (key: string, workspaceId: string, include?: string[]) =>
      include
        ? (['entityType', key, workspaceId, include] as const)
        : (['entityType', key, workspaceId] as const),
  },
  /** Key for semantic metadata for a specific entity type */
  semanticMetadata: (workspaceId: string, entityTypeId: string) =>
    ['semanticMetadata', workspaceId, entityTypeId] as const,
} as const;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- --testPathPattern="entity-query-keys" --verbose`
Expected: PASS — all 5 assertions pass

- [ ] **Step 5: Commit**

```bash
git add components/feature-modules/entity/hooks/query/entity-query-keys.ts components/feature-modules/entity/hooks/query/entity-query-keys.test.ts
git commit -m "feat(entity): add centralized query key factory

Eliminates magic string query keys scattered across hooks and mutations.
All entity query/mutation cache operations will use this factory."
```

---

### Task 2: Migrate query hooks to use key factory

**Files:**
- Modify: `components/feature-modules/entity/hooks/query/use-entities.ts`
- Modify: `components/feature-modules/entity/hooks/query/type/use-entity-types.ts`
- Modify: `components/feature-modules/entity/hooks/query/type/use-semantic-metadata.ts`

**Context:** These 3 hooks currently use inline string arrays for query keys. After this task, they import from `entity-query-keys.ts`.

- [ ] **Step 1: Update `use-entities.ts` to use key factory**

In `use-entities.ts`, replace:
- Line 13: `queryKey: ['entities', workspaceId, typeId]` → `queryKey: entityKeys.entities.list(workspaceId!, typeId!)`
- Line 37: `queryKey: ['entities', workspaceId, typeId]` → `queryKey: entityKeys.entities.list(workspaceId, typeId)`
- Add import: `import { entityKeys } from './entity-query-keys';`

- [ ] **Step 2: Update `use-entity-types.ts` to use key factory**

In `use-entity-types.ts`, replace:
- `queryKey: ['entityTypes', workspaceId]` → `queryKey: entityKeys.entityTypes.list(workspaceId!)`
- `queryKey: ['entityType', key, workspaceId, include]` → `queryKey: entityKeys.entityTypes.byKey(key!, workspaceId!, include)`
- Add import: `import { entityKeys } from '../entity-query-keys';`

- [ ] **Step 3: Update `use-semantic-metadata.ts` to use key factory**

In `use-semantic-metadata.ts`, replace:
- `queryKey: ['semanticMetadata', workspaceId, entityTypeId]` → `queryKey: entityKeys.semanticMetadata(workspaceId!, entityTypeId!)`
- Add import: `import { entityKeys } from '../entity-query-keys';`

- [ ] **Step 4: Verify no other query hooks use hardcoded entity keys**

Run: `grep -rn "queryKey.*\['entit\|queryKey.*\['semantic" components/feature-modules/entity/hooks/query/`
Expected: Only the 3 files just modified should match, using `entityKeys.*`

- [ ] **Step 5: Commit**

```bash
git add components/feature-modules/entity/hooks/query/
git commit -m "refactor(entity): migrate query hooks to centralized key factory"
```

---

### Task 3: Migrate mutation hooks to use key factory

**Files:**
- Modify: `components/feature-modules/entity/hooks/mutation/instance/use-save-entity-mutation.ts`
- Modify: `components/feature-modules/entity/hooks/mutation/instance/use-delete-entity-mutation.ts`
- Modify: `components/feature-modules/entity/hooks/mutation/type/use-publish-type-mutation.ts`
- Modify: `components/feature-modules/entity/hooks/mutation/type/use-save-configuration-mutation.ts`
- Modify: `components/feature-modules/entity/hooks/mutation/type/use-save-definition-mutation.ts`
- Modify: `components/feature-modules/entity/hooks/mutation/type/use-delete-definition-mutation.ts`
- Modify: `components/feature-modules/entity/hooks/mutation/type/use-delete-type-mutation.ts`

**Context:** Mutation hooks use `queryClient.setQueryData` and `queryClient.invalidateQueries` with hardcoded string arrays. These must all use the key factory.

- [ ] **Step 1: Update `use-save-entity-mutation.ts`**

Replace all cache key references:
- Line 49-50: `['entities', workspaceId, entityTypeId]` → `entityKeys.entities.list(workspaceId, entityTypeId)`
- Line 72: `['entities', workspaceId, impactedTypeId]` → `entityKeys.entities.list(workspaceId, impactedTypeId)`
- Add import: `import { entityKeys } from '../../query/entity-query-keys';`

- [ ] **Step 2: Update `use-delete-entity-mutation.ts`**

Replace:
- Line 59: `['entities', workspaceId, typeId]` → `entityKeys.entities.list(workspaceId, typeId)`
- Line 68: `['entities', workspaceId, typeId]` → `entityKeys.entities.list(workspaceId, typeId)`
- Add import: `import { entityKeys } from '../../query/entity-query-keys';`

- [ ] **Step 3: Update type mutation hooks (publish, save-config, save-definition, delete-definition, delete-type)**

For each of these hooks, find any `invalidateQueries` or `setQueryData` calls that use string arrays like `['entityType', ...]`, `['entityTypes', ...]`, or `['semanticMetadata', ...]` and replace with the corresponding `entityKeys.*` call.

Common patterns to replace:
- `queryKey: ['entityType', key, workspaceId]` → `queryKey: entityKeys.entityTypes.byKey(key, workspaceId)`
- `queryKey: ['entityTypes', workspaceId]` → `queryKey: entityKeys.entityTypes.list(workspaceId)`
- `queryKey: ['semanticMetadata']` → partial match invalidation stays as-is (TanStack partial matching)

Add import to each: `import { entityKeys } from '../../query/entity-query-keys';`

- [ ] **Step 4: Verify no hardcoded entity query keys remain in mutation hooks**

Run: `grep -rn "\['entit\|\['semantic" components/feature-modules/entity/hooks/mutation/`
Expected: No matches with raw string arrays. All should use `entityKeys.*`

- [ ] **Step 5: Run lint to verify no import errors**

Run: `npm run lint`
Expected: No errors related to entity hooks

- [ ] **Step 6: Commit**

```bash
git add components/feature-modules/entity/hooks/mutation/
git commit -m "refactor(entity): migrate mutation hooks to centralized key factory"
```

---

## Chunk 2: Authenticated Query Wrapper

### Task 4: Create `useAuthenticatedQuery` wrapper hook

**Files:**
- Create: `hooks/query/use-authenticated-query.ts`
- Test: `hooks/query/use-authenticated-query.test.ts`

**Context:** Every query hook in the app repeats the same pattern:
```typescript
const { session, loading } = useAuth();
const query = useQuery({
  // ...
  enabled: !!session && !loading && !!otherParam,
});
return { isLoadingAuth: loading, ...query };
```

This is repeated in `use-entities.ts`, `use-entity-types.ts`, `use-semantic-metadata.ts`, and likely in workspace/workflow modules too. The wrapper extracts this into a single hook.

- [ ] **Step 1: Write the failing test**

```typescript
// hooks/query/use-authenticated-query.test.ts
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useAuthenticatedQuery } from './use-authenticated-query';
import { useAuth } from '@/components/provider/auth-context';
import { ReactNode } from 'react';

// Mock useAuth
jest.mock('@/components/provider/auth-context', () => ({
  useAuth: jest.fn(),
}));

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('useAuthenticatedQuery', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('disables query when session is null', () => {
    mockUseAuth.mockReturnValue({
      session: null,
      loading: false,
    } as any);

    const { result } = renderHook(
      () =>
        useAuthenticatedQuery({
          queryKey: ['test'],
          queryFn: async () => 'data',
        }),
      { wrapper: createWrapper() },
    );

    expect(result.current.fetchStatus).toBe('idle');
    expect(result.current.isLoadingAuth).toBe(false);
  });

  it('disables query when auth is loading', () => {
    mockUseAuth.mockReturnValue({
      session: { access_token: 'token' },
      loading: true,
    } as any);

    const { result } = renderHook(
      () =>
        useAuthenticatedQuery({
          queryKey: ['test'],
          queryFn: async () => 'data',
        }),
      { wrapper: createWrapper() },
    );

    expect(result.current.fetchStatus).toBe('idle');
    expect(result.current.isLoadingAuth).toBe(true);
  });

  it('enables query when session exists and not loading', async () => {
    mockUseAuth.mockReturnValue({
      session: { access_token: 'token' },
      loading: false,
    } as any);

    const queryFn = jest.fn().mockResolvedValue('result');

    const { result } = renderHook(
      () =>
        useAuthenticatedQuery({
          queryKey: ['test'],
          queryFn,
        }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toBe('result');
    expect(result.current.isLoadingAuth).toBe(false);
  });

  it('respects additional enabled condition', () => {
    mockUseAuth.mockReturnValue({
      session: { access_token: 'token' },
      loading: false,
    } as any);

    const queryFn = jest.fn().mockResolvedValue('result');

    const { result } = renderHook(
      () =>
        useAuthenticatedQuery({
          queryKey: ['test'],
          queryFn,
          enabled: false, // Additional condition overrides
        }),
      { wrapper: createWrapper() },
    );

    expect(result.current.fetchStatus).toBe('idle');
    expect(queryFn).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --testPathPattern="use-authenticated-query" --verbose`
Expected: FAIL — module not found

- [ ] **Step 3: Implement the wrapper hook**

```typescript
// hooks/query/use-authenticated-query.ts
import { useAuth } from '@/components/provider/auth-context';
import { AuthenticatedQueryResult } from '@/lib/interfaces/interface';
import {
  QueryKey,
  useQuery,
  UseQueryOptions,
} from '@tanstack/react-query';

/**
 * Wrapper around useQuery that handles auth-gating automatically.
 *
 * - Disables the query when session is null or auth is loading
 * - Merges the caller's `enabled` condition with auth checks
 * - Returns `isLoadingAuth` flag alongside standard query result
 *
 * Usage:
 * ```ts
 * const result = useAuthenticatedQuery({
 *   queryKey: entityKeys.entities.list(workspaceId, typeId),
 *   queryFn: () => EntityService.getEntitiesForType(session, workspaceId, typeId),
 *   enabled: !!workspaceId && !!typeId,
 *   staleTime: 5 * 60 * 1000,
 * });
 * ```
 */
export function useAuthenticatedQuery<
  TQueryFnData = unknown,
  TError = Error,
  TData = TQueryFnData,
  TQueryKey extends QueryKey = QueryKey,
>(
  options: UseQueryOptions<TQueryFnData, TError, TData, TQueryKey>,
): AuthenticatedQueryResult<TData, TError> {
  const { session, loading } = useAuth();

  const isAuthReady = !!session && !loading;
  const callerEnabled = options.enabled ?? true;

  const query = useQuery<TQueryFnData, TError, TData, TQueryKey>({
    ...options,
    enabled: isAuthReady && callerEnabled,
  });

  return {
    ...query,
    isLoadingAuth: loading, // Match existing semantics: only true while auth is actively loading
  };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- --testPathPattern="use-authenticated-query" --verbose`
Expected: PASS — all 4 assertions pass

- [ ] **Step 5: Commit**

```bash
git add hooks/query/use-authenticated-query.ts hooks/query/use-authenticated-query.test.ts
git commit -m "feat: add useAuthenticatedQuery wrapper hook

Extracts duplicated auth-gating pattern from query hooks into a single
reusable wrapper. Handles session check, loading state, and isLoadingAuth."
```

---

### Task 5: Migrate entity query hooks to use wrapper

**Files:**
- Modify: `components/feature-modules/entity/hooks/query/use-entities.ts`
- Modify: `components/feature-modules/entity/hooks/query/type/use-entity-types.ts`
- Modify: `components/feature-modules/entity/hooks/query/type/use-semantic-metadata.ts`

**Context:** Each hook currently imports `useAuth`, manually checks `session`/`loading`, and spreads `isLoadingAuth`. After migration, they use `useAuthenticatedQuery` which handles all of this.

Note: `useEntitiesFromManyTypes` in `use-entities.ts` uses `useQueries` (plural), not `useQuery`. The wrapper doesn't apply to it — leave it as-is for now.

**Important:** The services (`EntityService.getEntitiesForType`, etc.) call `validateSession(session)` which throws if session is null. The `useAuthenticatedQuery` wrapper gates on `!!session`, so the queryFn will only execute when session exists. However, the queryFn closure still needs the real session reference. **Keep `useAuth` imported for session access in the queryFn, but let `useAuthenticatedQuery` handle the `enabled` logic.**

- [ ] **Step 1: Migrate `useEntities` in `use-entities.ts`**

Replace the `useEntities` function (lines 7-28) with:

```typescript
import { useAuth } from '@/components/provider/auth-context';
import { useAuthenticatedQuery } from '@/hooks/query/use-authenticated-query';
import { entityKeys } from './entity-query-keys';

export function useEntities(
  workspaceId?: string,
  typeId?: string,
): AuthenticatedQueryResult<Entity[]> {
  const { session } = useAuth(); // Still needed for queryFn — services require real session
  return useAuthenticatedQuery({
    queryKey: entityKeys.entities.list(workspaceId!, typeId!),
    queryFn: () => EntityService.getEntitiesForType(session, workspaceId!, typeId!),
    staleTime: 5 * 60 * 1000,
    enabled: !!workspaceId && !!typeId,
    refetchOnWindowFocus: false,
    refetchOnMount: false,
    gcTime: 10 * 60 * 1000,
  });
}
```

- [ ] **Step 2: Migrate `useEntityTypes` and `useEntityTypeByKey` in `use-entity-types.ts`**

Same pattern — replace manual auth logic with `useAuthenticatedQuery`, keep `useAuth` if needed for session in queryFn.

- [ ] **Step 3: Migrate `useSemanticMetadata` in `use-semantic-metadata.ts`**

Same pattern.

- [ ] **Step 4: Verify all hooks compile and lint passes**

Run: `npm run lint`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add components/feature-modules/entity/hooks/query/
git commit -m "refactor(entity): migrate query hooks to useAuthenticatedQuery wrapper"
```

---

## Chunk 3: Error Normalization Improvements

### Task 6: Improve `normalizeApiError` with context preservation

**Files:**
- Modify: `lib/util/error/error.util.ts`
- Test: `lib/util/error/error.util.test.ts`

**Context:** Current issues in `normalizeApiError` (line 129-159 of `error.util.ts`):
1. **Double-normalization**: If error is already a `ResponseError`, it gets re-processed by `fromError` (line 158) which returns it as-is, but `normalizeApiError` then throws it. Not a bug per se, but wasteful and confusing.
2. **409 body parsing**: In the try/catch (line 134-154), if `response.json()` succeeds but returns a non-error object, the code throws a fresh ResponseError. But if `response.json()` fails (malformed JSON), the catch block checks `isResponseError(parseError)` — this would be false for a `SyntaxError`, so it falls through to throw a generic error, masking the original 409 status.
3. **No context preservation**: The thrown errors don't include the original error as `cause` or the request context.

Additionally, the entity services (`entity.service.ts:34-43`, `entity-type.service.ts:114-118`) catch 409s and parse `error.response.json()` directly — if that parse fails, the error is uncaught.

- [ ] **Step 1: Write tests for current behavior and new behavior**

```typescript
// lib/util/error/error.util.test.ts
import {
  normalizeApiError,
  isResponseError,
  fromError,
  ResponseError,
} from './error.util';
import { ResponseError as OpenApiResponseError } from '@/lib/types';

// Mock the OpenAPI ResponseError since it requires a real Response object
function createMockOpenApiError(status: number, body: object | string): OpenApiResponseError {
  const isString = typeof body === 'string';
  const response = {
    status,
    statusText: 'Error',
    json: isString
      ? () => Promise.reject(new SyntaxError('Unexpected token'))
      : () => Promise.resolve(body),
  } as Response;

  const error = new OpenApiResponseError(response, 'API Error');
  return error;
}

describe('normalizeApiError', () => {
  it('normalizes OpenAPI ResponseError with valid JSON body', async () => {
    const apiError = createMockOpenApiError(400, {
      statusCode: 400,
      error: 'VALIDATION_ERROR',
      message: 'Invalid input',
    });

    await expect(normalizeApiError(apiError)).rejects.toMatchObject({
      status: 400,
      error: 'VALIDATION_ERROR',
      message: 'Invalid input',
    });
  });

  it('handles malformed JSON body gracefully', async () => {
    const apiError = createMockOpenApiError(500, 'not json');

    await expect(normalizeApiError(apiError)).rejects.toMatchObject({
      status: 500,
      error: 'API_ERROR',
    });
  });

  it('does not double-normalize an existing ResponseError', async () => {
    const existing: ResponseError = Object.assign(new Error('Already normalized'), {
      name: 'ResponseError',
      status: 422,
      error: 'ALREADY_NORMALIZED',
      message: 'Already normalized',
    });

    await expect(normalizeApiError(existing)).rejects.toMatchObject({
      status: 422,
      error: 'ALREADY_NORMALIZED',
      message: 'Already normalized',
    });
  });

  it('preserves original error as cause when available', async () => {
    const apiError = createMockOpenApiError(400, {
      statusCode: 400,
      error: 'BAD_REQUEST',
      message: 'Bad request',
    });

    try {
      await normalizeApiError(apiError);
    } catch (error: any) {
      expect(error.cause).toBeDefined();
    }
  });
});

describe('fromError', () => {
  it('returns existing ResponseError as-is', () => {
    const existing: ResponseError = Object.assign(new Error('test'), {
      name: 'ResponseError',
      status: 400,
      error: 'TEST',
      message: 'test',
    });
    expect(fromError(existing)).toBe(existing);
  });

  it('converts standard Error', () => {
    const error = new Error('Something failed');
    const result = fromError(error);
    expect(result.status).toBe(500);
    expect(result.message).toBe('Something failed');
  });

  it('converts unknown object with message', () => {
    const result = fromError({ message: 'Custom error', status: 503 });
    expect(result.status).toBe(503);
    expect(result.message).toBe('Custom error');
  });

  it('converts primitive error', () => {
    const result = fromError('string error');
    expect(result.message).toBe('string error');
    expect(result.status).toBe(500);
  });
});

describe('isResponseError', () => {
  it('returns true for valid ResponseError shape', () => {
    expect(isResponseError({ status: 400, error: 'ERR', message: 'msg' })).toBe(true);
  });

  it('returns false for plain Error', () => {
    expect(isResponseError(new Error('test'))).toBe(false);
  });

  it('returns false for null', () => {
    expect(isResponseError(null)).toBe(false);
  });
});
```

- [ ] **Step 2: Run tests to verify current behavior passes, new tests fail**

Run: `npm test -- --testPathPattern="error.util" --verbose`
Expected: Most tests pass. The `preserves original error as cause` test should FAIL (cause not implemented yet).

- [ ] **Step 3: Implement error normalization improvements**

Modify `lib/util/error/error.util.ts`:

1. Add double-normalization guard at the top of `normalizeApiError`:
```typescript
export async function normalizeApiError(error: unknown): Promise<never> {
    // Guard: don't double-normalize
    if (isResponseError(error)) {
        throw error;
    }

    // Handle OpenAPI-generated ResponseError
    if (error instanceof OpenApiResponseError) {
        // ... rest of existing logic
```

2. Add `cause` to the thrown error in the OpenAPI branch:
```typescript
throw Object.assign(new Error(body.message), {
    name: "ResponseError",
    status: body.statusCode ?? response.status,
    error: body.error ?? "API_ERROR",
    message: body.message ?? "An unexpected error occurred",
    stackTrace: body.stackTrace,
    cause: error, // Preserve original error
}) as ResponseError;
```

3. Also add `cause` to the JSON parse failure fallback:
```typescript
throw Object.assign(new Error(response.statusText), {
    name: "ResponseError",
    status: response.status,
    error: "API_ERROR",
    message: response.statusText || "An unexpected error occurred",
    cause: error, // Preserve original error
}) as ResponseError;
```

- [ ] **Step 4: Run tests to verify all pass**

Run: `npm test -- --testPathPattern="error.util" --verbose`
Expected: PASS — all assertions pass

- [ ] **Step 5: Commit**

```bash
git add lib/util/error/error.util.ts lib/util/error/error.util.test.ts
git commit -m "fix(error): add double-normalization guard and context preservation

- Prevents re-normalizing already-normalized ResponseErrors
- Preserves original error as 'cause' for debugging
- Adds comprehensive tests for error normalization"
```

---

## Chunk 4: Performance Fixes

### Task 7: Stabilize EntityDraftProvider memo dependencies

**Files:**
- Modify: `components/feature-modules/entity/context/entity-provider.tsx`

**Context:** In `entity-provider.tsx:37-46`, two `useMemo` hooks depend on `entityType.schema` and `entityType.relationships`. These are object references from a TanStack Query result — they change on every background refetch even if the data hasn't changed. This causes the Zod schema to rebuild and default values to recalculate unnecessarily.

The current deps are: `[entityType.key, entityType.schema, entityType.relationships]`

Safer deps would be: `[entityType.id, entityType.version]` — the version increments when the schema actually changes (attribute added/removed/modified), and the id is stable.

**Important:** Read the `EntityType` model to confirm `version` exists and increments on schema changes. From the generated model at `lib/types/models/EntityType.ts`, EntityType has `version: number`.

- [ ] **Step 1: Update memo dependencies in entity-provider.tsx**

Change lines 37-46:

```typescript
// Before:
const schema = useMemo(
  () => buildZodSchemaFromEntityType(entityType),
  [entityType.key, entityType.schema, entityType.relationships],
);

const defaultValues = useMemo(
  () => buildDefaultValuesFromEntityType(entityType),
  [entityType.key, entityType.schema, entityType.relationships],
);

// After:
const schema = useMemo(
  () => buildZodSchemaFromEntityType(entityType),
  [entityType.id, entityType.version],
);

const defaultValues = useMemo(
  () => buildDefaultValuesFromEntityType(entityType),
  [entityType.id, entityType.version],
);
```

- [ ] **Step 2: Verify lint passes (exhaustive-deps warning is acceptable here)**

Run: `npm run lint`
Expected: May get react-hooks/exhaustive-deps warning — this is intentional. The memo uses `entityType` in the callback but only keys on `id` + `version` because we want structural stability. Add a disable comment if needed:

```typescript
// eslint-disable-next-line react-hooks/exhaustive-deps -- intentional: version increments when schema changes
```

- [ ] **Step 3: Commit**

```bash
git add components/feature-modules/entity/context/entity-provider.tsx
git commit -m "perf(entity): stabilize EntityDraftProvider memo dependencies

Use entityType.id + version instead of schema/relationships object refs.
Prevents unnecessary schema rebuilds on background query refetches."
```

---

### ~~Task 8: Memoize column/filter/search generation in EntityDataTable~~ — SKIP

**Status:** Already implemented. `entity-data-table.tsx` already wraps `generateColumnsFromEntityType`, `generateFiltersFromEntityType`, `generateSearchConfigFromEntityType` in `useMemo` (lines 248, 254, 259, 268). No work needed.

---

### Task 8: Use batched endpoint for relationship picker queries

**Files:**
- Modify: `components/feature-modules/entity/hooks/query/use-entities.ts`

**Context:** `useEntitiesFromManyTypes` (lines 30-54) creates N parallel queries via `useQueries`, one per type ID. But `EntityService.getEntitiesForTypes()` already exists and accepts multiple type IDs in a single request. Replace `useQueries` with a single `useQuery` using the batched endpoint.

The trade-off: individual per-type cache entries are lost. But the relationship picker always needs all types together, so a combined cache key is more appropriate.

- [ ] **Step 1: Rewrite `useEntitiesFromManyTypes` to use batched endpoint**

```typescript
export function useEntitiesFromManyTypes(
  workspaceId: string,
  typeIds: string[],
): AuthenticatedMultiQueryResult<Entity[]> {
  const { session, loading } = useAuth();

  // Sort typeIds for cache key stability
  const sortedTypeIds = useMemo(() => [...typeIds].sort(), [typeIds]);

  const query = useQuery({
    queryKey: ['entities', workspaceId, 'batch', sortedTypeIds],
    queryFn: async () => {
      const result = await EntityService.getEntitiesForTypes(
        session,
        workspaceId,
        sortedTypeIds,
      );
      // Flatten Record<typeId, Entity[]> to Entity[]
      return Object.values(result).flat();
    },
    enabled: !!session && !loading && !!workspaceId && sortedTypeIds.length > 0,
  });

  return {
    data: query.data ?? [],
    isLoading: query.isLoading,
    isError: query.isError,
    isLoadingAuth: loading,
  };
}
```

Add `useMemo` import if not already present.

- [ ] **Step 2: Verify the relationship picker still works**

The relationship picker (`entity-relationship-picker.tsx`) calls `useEntitiesFromManyTypes`. The return shape stays the same (`data: Entity[]`, `isLoading`, `isError`, `isLoadingAuth`), so consumers should not need changes.

Run: `npm run build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add components/feature-modules/entity/hooks/query/use-entities.ts
git commit -m "perf(entity): use batched endpoint for multi-type entity queries

Replaces N parallel queries with a single batched API call in
useEntitiesFromManyTypes. Reduces HTTP requests in relationship picker."
```

---

## Verification

After completing all tasks:

- [ ] **Run full lint**: `npm run lint` — no errors
- [ ] **Run full build**: `npm run build` — no type errors
- [ ] **Run all tests**: `npm test` — all pass
- [ ] **Verify query keys grep**: `grep -rn "queryKey.*\['" components/feature-modules/entity/hooks/` — no raw string array query keys remain (only `entityKeys.*` calls)

---

## Success Criteria

1. All entity query keys are defined in `entity-query-keys.ts` — no magic strings in hooks or mutations
2. `useAuthenticatedQuery` wrapper exists and is used by all entity query hooks
3. `normalizeApiError` guards against double-normalization and preserves `cause`
4. `EntityDraftProvider` memo dependencies use `id + version`, not object references
5. Column/filter/search generation is memoized in `EntityDataTable`
6. `useEntitiesFromManyTypes` uses single batched API call
7. All new code has corresponding tests
8. `npm run lint`, `npm run build`, and `npm test` all pass
