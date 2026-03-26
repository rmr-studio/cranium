import { InfiniteData } from '@tanstack/react-query';
import { EntityQueryResponse, Entity } from '@/lib/types/entity';
import { removeEntitiesFromPages, replaceEntitiesInPages } from './entity-cache.utils';

function makeEntity(id: string): Entity {
  return { id, payload: {}, identifierKey: 'name', createdAt: new Date().toISOString() } as any;
}

function makePages(...pages: Entity[][]): InfiniteData<EntityQueryResponse> {
  return {
    pages: pages.map((entities, i) => ({
      entities,
      hasNextPage: i < pages.length - 1,
      limit: 50,
      offset: i * 50,
    })),
    pageParams: pages.map((_, i) => i * 50),
  };
}

describe('Delete mutation cache updates', () => {
  it('removes a single entity from the correct page', () => {
    const data = makePages([makeEntity('e1'), makeEntity('e2')], [makeEntity('e3')]);
    const result = removeEntitiesFromPages(data, new Set(['e2']));

    expect(result!.pages[0].entities).toHaveLength(1);
    expect(result!.pages[0].entities[0].id).toBe('e1');
    expect(result!.pages[1].entities).toHaveLength(1);
  });

  it('removes entities across multiple pages', () => {
    const data = makePages([makeEntity('e1'), makeEntity('e2')], [makeEntity('e3')]);
    const result = removeEntitiesFromPages(data, new Set(['e1', 'e3']));

    expect(result!.pages[0].entities).toHaveLength(1);
    expect(result!.pages[0].entities[0].id).toBe('e2');
    expect(result!.pages[1].entities).toHaveLength(0);
  });

  it('handles removing non-existent IDs gracefully', () => {
    const data = makePages([makeEntity('e1')]);
    const result = removeEntitiesFromPages(data, new Set(['missing']));

    expect(result!.pages[0].entities).toHaveLength(1);
  });

  it('replaces impacted entities after deletion', () => {
    const data = makePages([makeEntity('e1'), makeEntity('e2')]);
    const updated = { ...makeEntity('e2'), payload: { status: 'orphaned' } } as any;
    const result = replaceEntitiesInPages(data, new Map([['e2', updated]]));

    expect(result!.pages[0].entities[1]).toEqual(updated);
    expect(result!.pages[0].entities[0].id).toBe('e1');
  });
});

import { renderHook, act, waitFor } from '@testing-library/react';
import React from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { useAuth } from '@/components/provider/auth-context';
import { EntityService } from '@/components/feature-modules/entity/service/entity.service';
import { toast } from 'sonner';
import { useDeleteEntityMutation } from '@/components/feature-modules/entity/hooks/mutation/instance/use-delete-entity-mutation';
import {
  createTestQueryClient,
  seedEntityCache,
  getEntityCache,
  createMockEntity,
  createDeleteResponse,
} from '@/components/feature-modules/entity/hooks/mutation/test-utils/mutation-test-helpers';

jest.mock('@/components/provider/auth-context', () => ({
  useAuth: jest.fn(),
}));
jest.mock('@/components/feature-modules/entity/service/entity.service');
jest.mock('sonner', () => ({
  toast: {
    loading: jest.fn(() => 'toast-id'),
    success: jest.fn(),
    warning: jest.fn(),
    error: jest.fn(),
    dismiss: jest.fn(),
  },
}));

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockDeleteEntities = EntityService.deleteEntities as jest.MockedFunction<
  typeof EntityService.deleteEntities
>;

const WORKSPACE_ID = 'workspace-1';
const TYPE_ID_A = 'type-a';
const TYPE_ID_B = 'type-b';

function createWrapper(queryClient: ReturnType<typeof createTestQueryClient>) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return React.createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

beforeEach(() => {
  jest.clearAllMocks();
  mockUseAuth.mockReturnValue({
    session: { access_token: 'test-token' } as never,
    user: null,
    loading: false,
    signIn: jest.fn(),
    signUp: jest.fn(),
    signOut: jest.fn(),
    signInWithOAuth: jest.fn(),
    verifyOtp: jest.fn(),
    resendOtp: jest.fn(),
  });
});

describe('useDeleteEntityMutation', () => {
  describe('cache removal on successful deletion', () => {
    it('removes deleted entities from cache, leaving non-deleted entities intact', async () => {
      const queryClient = createTestQueryClient();
      const entityA = createMockEntity({ id: 'entity-1', typeId: TYPE_ID_A });
      const entityB = createMockEntity({ id: 'entity-2', typeId: TYPE_ID_A });
      const entityC = createMockEntity({ id: 'entity-3', typeId: TYPE_ID_A });

      seedEntityCache(queryClient, WORKSPACE_ID, TYPE_ID_A, [entityA, entityB, entityC]);
      mockDeleteEntities.mockResolvedValue(createDeleteResponse(2));

      const { result } = renderHook(
        () => useDeleteEntityMutation(WORKSPACE_ID),
        { wrapper: createWrapper(queryClient) },
      );

      await act(async () => {
        result.current.mutate({ entityIds: { [TYPE_ID_A]: ['entity-1', 'entity-2'] } });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      const remaining = getEntityCache(queryClient, WORKSPACE_ID, TYPE_ID_A);
      expect(remaining).toHaveLength(1);
      expect(remaining![0].id).toBe('entity-3');
    });

    it('removes entities from multiple type caches independently', async () => {
      const queryClient = createTestQueryClient();
      const entityA1 = createMockEntity({ id: 'entity-a1', typeId: TYPE_ID_A });
      const entityA2 = createMockEntity({ id: 'entity-a2', typeId: TYPE_ID_A });
      const entityB1 = createMockEntity({ id: 'entity-b1', typeId: TYPE_ID_B });
      const entityB2 = createMockEntity({ id: 'entity-b2', typeId: TYPE_ID_B });

      seedEntityCache(queryClient, WORKSPACE_ID, TYPE_ID_A, [entityA1, entityA2]);
      seedEntityCache(queryClient, WORKSPACE_ID, TYPE_ID_B, [entityB1, entityB2]);
      mockDeleteEntities.mockResolvedValue(createDeleteResponse(2));

      const { result } = renderHook(
        () => useDeleteEntityMutation(WORKSPACE_ID),
        { wrapper: createWrapper(queryClient) },
      );

      await act(async () => {
        result.current.mutate({
          entityIds: {
            [TYPE_ID_A]: ['entity-a1'],
            [TYPE_ID_B]: ['entity-b2'],
          },
        });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      const cacheA = getEntityCache(queryClient, WORKSPACE_ID, TYPE_ID_A);
      expect(cacheA).toHaveLength(1);
      expect(cacheA![0].id).toBe('entity-a2');

      const cacheB = getEntityCache(queryClient, WORKSPACE_ID, TYPE_ID_B);
      expect(cacheB).toHaveLength(1);
      expect(cacheB![0].id).toBe('entity-b1');
    });
  });

  describe('cache updates for impacted entities', () => {
    it('updates impacted entities in cache after deletion triggers cascade changes', async () => {
      const queryClient = createTestQueryClient();
      const entityA = createMockEntity({ id: 'entity-1', typeId: TYPE_ID_A, payload: { name: 'Original' } });
      const entityB = createMockEntity({ id: 'entity-2', typeId: TYPE_ID_A });
      const updatedEntityA = createMockEntity({ id: 'entity-1', typeId: TYPE_ID_A, payload: { name: 'Updated' } });

      seedEntityCache(queryClient, WORKSPACE_ID, TYPE_ID_A, [entityA, entityB]);
      mockDeleteEntities.mockResolvedValue(
        createDeleteResponse(1, { [TYPE_ID_A]: [updatedEntityA] }),
      );

      const { result } = renderHook(
        () => useDeleteEntityMutation(WORKSPACE_ID),
        { wrapper: createWrapper(queryClient) },
      );

      await act(async () => {
        result.current.mutate({ entityIds: { [TYPE_ID_A]: ['entity-2'] } });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      const remaining = getEntityCache(queryClient, WORKSPACE_ID, TYPE_ID_A);
      // entity-2 was deleted, entity-1 was updated
      const updated = remaining?.find((e) => e.id === 'entity-1');
      expect(updated?.payload).toEqual({ name: 'Updated' });
    });
  });

  describe('empty entity IDs handling', () => {
    it('rejects and shows error toast when entityIds map is empty', async () => {
      const queryClient = createTestQueryClient();

      const { result } = renderHook(
        () => useDeleteEntityMutation(WORKSPACE_ID),
        { wrapper: createWrapper(queryClient) },
      );

      await act(async () => {
        result.current.mutate({ entityIds: {} });
      });

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(mockDeleteEntities).not.toHaveBeenCalled();
      expect(result.current.error?.message).toBe('No entities to delete');
      expect(toast.error).toHaveBeenCalledWith(
        'Failed to delete selected entities: No entities to delete',
      );
    });

    it('rejects and shows error toast when all ID arrays are empty', async () => {
      const queryClient = createTestQueryClient();

      const { result } = renderHook(
        () => useDeleteEntityMutation(WORKSPACE_ID),
        { wrapper: createWrapper(queryClient) },
      );

      await act(async () => {
        result.current.mutate({ entityIds: { [TYPE_ID_A]: [], [TYPE_ID_B]: [] } });
      });

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(mockDeleteEntities).not.toHaveBeenCalled();
      expect(result.current.error?.message).toBe('No entities to delete');
      expect(toast.error).toHaveBeenCalledWith(
        'Failed to delete selected entities: No entities to delete',
      );
    });
  });

  describe('error response handling', () => {
    it('throws and shows error toast when deletedCount is 0 with an error message', async () => {
      const queryClient = createTestQueryClient();
      const entity = createMockEntity({ id: 'entity-1', typeId: TYPE_ID_A });
      seedEntityCache(queryClient, WORKSPACE_ID, TYPE_ID_A, [entity]);

      mockDeleteEntities.mockResolvedValue({ deletedCount: 0, error: 'Permission denied' });

      const { result } = renderHook(
        () => useDeleteEntityMutation(WORKSPACE_ID),
        { wrapper: createWrapper(queryClient) },
      );

      await act(async () => {
        result.current.mutate({ entityIds: { [TYPE_ID_A]: ['entity-1'] } });
      });

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(result.current.error?.message).toBe('Permission denied');
      expect(toast.error).toHaveBeenCalledWith(
        'Failed to delete selected entities: Permission denied',
      );
      expect(toast.success).not.toHaveBeenCalled();
    });

    it('does not remove entities from cache when deletedCount is 0 with error', async () => {
      const queryClient = createTestQueryClient();
      const entityA = createMockEntity({ id: 'entity-1', typeId: TYPE_ID_A });
      const entityB = createMockEntity({ id: 'entity-2', typeId: TYPE_ID_A });
      seedEntityCache(queryClient, WORKSPACE_ID, TYPE_ID_A, [entityA, entityB]);

      mockDeleteEntities.mockResolvedValue({ deletedCount: 0, error: 'Permission denied' });

      const { result } = renderHook(
        () => useDeleteEntityMutation(WORKSPACE_ID),
        { wrapper: createWrapper(queryClient) },
      );

      await act(async () => {
        result.current.mutate({ entityIds: { [TYPE_ID_A]: ['entity-1'] } });
      });

      await waitFor(() => expect(result.current.isError).toBe(true));

      // Cache should be untouched — no entities removed
      const cache = getEntityCache(queryClient, WORKSPACE_ID, TYPE_ID_A);
      expect(cache).toHaveLength(2);
    });

    it('shows warning toast on partial failure when deletedCount > 0 with error', async () => {
      const queryClient = createTestQueryClient();
      const entityA = createMockEntity({ id: 'entity-1', typeId: TYPE_ID_A });
      const entityB = createMockEntity({ id: 'entity-2', typeId: TYPE_ID_A });
      seedEntityCache(queryClient, WORKSPACE_ID, TYPE_ID_A, [entityA, entityB]);

      mockDeleteEntities.mockResolvedValue({
        deletedCount: 1,
        error: '1 entity could not be deleted',
      });

      const { result } = renderHook(
        () => useDeleteEntityMutation(WORKSPACE_ID),
        { wrapper: createWrapper(queryClient) },
      );

      await act(async () => {
        result.current.mutate({ entityIds: { [TYPE_ID_A]: ['entity-1', 'entity-2'] } });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(toast.warning).toHaveBeenCalledWith(
        '1 entities deleted, but some failed: 1 entity could not be deleted',
      );
      expect(toast.success).not.toHaveBeenCalled();
      expect(toast.error).not.toHaveBeenCalled();
    });
  });
});
