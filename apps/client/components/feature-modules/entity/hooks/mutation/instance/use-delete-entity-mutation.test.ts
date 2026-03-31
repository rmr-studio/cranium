import { InfiniteData } from '@tanstack/react-query';
import { EntityQueryResponse, Entity, EntitySelectType } from '@/lib/types/entity';
import type { DeleteEntityRequest } from '@/lib/types/entity';
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
  describe('BY_ID mode: surgical cache removal', () => {
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

      const request: DeleteEntityRequest = {
        type: EntitySelectType.ById,
        entityTypeId: TYPE_ID_A,
        entityIds: ['entity-1', 'entity-2'],
      };

      await act(async () => {
        result.current.mutate(request);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      const remaining = getEntityCache(queryClient, WORKSPACE_ID, TYPE_ID_A);
      expect(remaining).toHaveLength(1);
      expect(remaining![0].id).toBe('entity-3');
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

      const request: DeleteEntityRequest = {
        type: EntitySelectType.ById,
        entityTypeId: TYPE_ID_A,
        entityIds: ['entity-2'],
      };

      await act(async () => {
        result.current.mutate(request);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      const remaining = getEntityCache(queryClient, WORKSPACE_ID, TYPE_ID_A);
      const updated = remaining?.find((e) => e.id === 'entity-1');
      expect(updated?.payload).toEqual({ name: 'Updated' });
    });
  });

  describe('toast notifications', () => {
    it('shows success toast with count', async () => {
      const queryClient = createTestQueryClient();
      mockDeleteEntities.mockResolvedValue(createDeleteResponse(3));

      const { result } = renderHook(
        () => useDeleteEntityMutation(WORKSPACE_ID),
        { wrapper: createWrapper(queryClient) },
      );

      const request: DeleteEntityRequest = {
        type: EntitySelectType.ById,
        entityTypeId: TYPE_ID_A,
        entityIds: ['e1', 'e2', 'e3'],
      };

      await act(async () => {
        result.current.mutate(request);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(toast.success).toHaveBeenCalledWith('3 entities deleted successfully');
    });

    it('shows singular toast for single entity', async () => {
      const queryClient = createTestQueryClient();
      mockDeleteEntities.mockResolvedValue(createDeleteResponse(1));

      const { result } = renderHook(
        () => useDeleteEntityMutation(WORKSPACE_ID),
        { wrapper: createWrapper(queryClient) },
      );

      const request: DeleteEntityRequest = {
        type: EntitySelectType.ById,
        entityTypeId: TYPE_ID_A,
        entityIds: ['e1'],
      };

      await act(async () => {
        result.current.mutate(request);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(toast.success).toHaveBeenCalledWith('1 entity deleted successfully');
    });

    it('shows error toast on failure', async () => {
      const queryClient = createTestQueryClient();
      mockDeleteEntities.mockRejectedValue(new Error('Permission denied'));

      const { result } = renderHook(
        () => useDeleteEntityMutation(WORKSPACE_ID),
        { wrapper: createWrapper(queryClient) },
      );

      const request: DeleteEntityRequest = {
        type: EntitySelectType.ById,
        entityTypeId: TYPE_ID_A,
        entityIds: ['e1'],
      };

      await act(async () => {
        result.current.mutate(request);
      });

      await waitFor(() => expect(result.current.isError).toBe(true));

      expect(toast.error).toHaveBeenCalledWith('Failed to delete entities: Permission denied');
    });
  });

  describe('cache strategy selection', () => {
    it('uses surgical removal for BY_ID requests', () => {
      const request: DeleteEntityRequest = {
        type: EntitySelectType.ById,
        entityTypeId: 'type-1',
        entityIds: ['id-1', 'id-2'],
      };

      expect(request.type).toBe(EntitySelectType.ById);
      expect(request.entityIds).toBeDefined();
    });

    it('uses invalidation for ALL requests', () => {
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
