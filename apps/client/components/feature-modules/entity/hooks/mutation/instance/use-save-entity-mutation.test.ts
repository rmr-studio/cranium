import { renderHook, act, waitFor } from '@testing-library/react';
import React from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { useAuth } from '@/components/provider/auth-context';
import { EntityService } from '@/components/feature-modules/entity/service/entity.service';
import { toast } from 'sonner';
import { useSaveEntityMutation } from '@/components/feature-modules/entity/hooks/mutation/instance/use-save-entity-mutation';
import {
  createTestQueryClient,
  seedEntityCache,
  getEntityCache,
  createMockEntity,
  createSaveResponse,
} from '@/components/feature-modules/entity/hooks/mutation/test-utils/mutation-test-helpers';
import type { SaveEntityRequest } from '@/lib/types/entity';

jest.mock('@/components/provider/auth-context', () => ({
  useAuth: jest.fn(),
}));
jest.mock('@/components/feature-modules/entity/service/entity.service');
jest.mock('sonner', () => ({
  toast: {
    loading: jest.fn(() => 'toast-id'),
    success: jest.fn(),
    error: jest.fn(),
    dismiss: jest.fn(),
  },
}));

const mockedUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockedSaveEntity = EntityService.saveEntity as jest.MockedFunction<
  typeof EntityService.saveEntity
>;

const WORKSPACE_ID = 'workspace-1';
const TYPE_ID = 'type-1';

function createWrapper(queryClient: ReturnType<typeof createTestQueryClient>) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return React.createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

beforeEach(() => {
  jest.clearAllMocks();
  mockedUseAuth.mockReturnValue({
    session: { access_token: 'test-token' } as never,
    loading: false,
    user: null,
    signIn: jest.fn(),
    signUp: jest.fn(),
    signOut: jest.fn(),
    signInWithOAuth: jest.fn(),
    verifyOtp: jest.fn(),
    resendOtp: jest.fn(),
  });
});

describe('useSaveEntityMutation', () => {
  describe('cache behavior on create', () => {
    it('appends a new entity to an existing cache list', async () => {
      const queryClient = createTestQueryClient();
      const existingEntity = createMockEntity({ id: 'entity-existing' });
      seedEntityCache(queryClient, WORKSPACE_ID, TYPE_ID, [existingEntity]);

      const newEntity = createMockEntity({ id: 'entity-new' });
      const saveResponse = createSaveResponse(newEntity);
      mockedSaveEntity.mockResolvedValueOnce(saveResponse);

      const { result } = renderHook(
        () => useSaveEntityMutation(WORKSPACE_ID, TYPE_ID),
        { wrapper: createWrapper(queryClient) },
      );

      const request: SaveEntityRequest = { payload: {} };
      act(() => {
        result.current.mutate(request);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      const cached = getEntityCache(queryClient, WORKSPACE_ID, TYPE_ID);
      expect(cached).toHaveLength(2);
      expect(cached).toContainEqual(existingEntity);
      expect(cached).toContainEqual(newEntity);
    });

    it('inserts entity into empty cache when no prior cache exists', async () => {
      const queryClient = createTestQueryClient();
      // No seed — cache is empty

      const newEntity = createMockEntity({ id: 'entity-brand-new' });
      const saveResponse = createSaveResponse(newEntity);
      mockedSaveEntity.mockResolvedValueOnce(saveResponse);

      const { result } = renderHook(
        () => useSaveEntityMutation(WORKSPACE_ID, TYPE_ID),
        { wrapper: createWrapper(queryClient) },
      );

      const request: SaveEntityRequest = { payload: {} };
      act(() => {
        result.current.mutate(request);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      const cached = getEntityCache(queryClient, WORKSPACE_ID, TYPE_ID);
      expect(cached).toEqual([newEntity]);
    });
  });

  describe('cache behavior on update', () => {
    it('replaces an existing entity in cache on update', async () => {
      const queryClient = createTestQueryClient();
      const originalEntity = createMockEntity({
        id: 'entity-to-update',
        payload: { name: 'Original' },
      });
      const otherEntity = createMockEntity({ id: 'entity-other' });
      seedEntityCache(queryClient, WORKSPACE_ID, TYPE_ID, [originalEntity, otherEntity]);

      const updatedEntity = createMockEntity({
        id: 'entity-to-update',
        payload: { name: 'Updated' },
      });
      const saveResponse = createSaveResponse(updatedEntity);
      mockedSaveEntity.mockResolvedValueOnce(saveResponse);

      const { result } = renderHook(
        () => useSaveEntityMutation(WORKSPACE_ID, TYPE_ID),
        { wrapper: createWrapper(queryClient) },
      );

      const request: SaveEntityRequest = { id: 'entity-to-update', payload: { name: 'Updated' } };
      act(() => {
        result.current.mutate(request);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      const cached = getEntityCache(queryClient, WORKSPACE_ID, TYPE_ID);
      expect(cached).toHaveLength(2);
      const found = cached?.find((e) => e.id === 'entity-to-update');
      expect(found?.payload).toEqual({ name: 'Updated' });
      expect(cached).toContainEqual(otherEntity);
    });
  });

  describe('impacted entities cache updates', () => {
    it('updates caches for other entity types when impactedEntities is present', async () => {
      const queryClient = createTestQueryClient();
      const IMPACTED_TYPE_ID = 'type-impacted';

      const savedEntity = createMockEntity({ id: 'entity-saved' });
      const impactedEntity = createMockEntity({
        id: 'entity-impacted',
        typeId: IMPACTED_TYPE_ID,
      });
      const existingImpacted = createMockEntity({
        id: 'entity-existing-impacted',
        typeId: IMPACTED_TYPE_ID,
      });
      seedEntityCache(queryClient, WORKSPACE_ID, IMPACTED_TYPE_ID, [existingImpacted]);

      const saveResponse = createSaveResponse(savedEntity, {
        [IMPACTED_TYPE_ID]: [impactedEntity],
      });
      mockedSaveEntity.mockResolvedValueOnce(saveResponse);

      const { result } = renderHook(
        () => useSaveEntityMutation(WORKSPACE_ID, TYPE_ID),
        { wrapper: createWrapper(queryClient) },
      );

      const request: SaveEntityRequest = { payload: {} };
      act(() => {
        result.current.mutate(request);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      const impactedCache = getEntityCache(queryClient, WORKSPACE_ID, IMPACTED_TYPE_ID);
      expect(impactedCache).toHaveLength(2);
      expect(impactedCache).toContainEqual(existingImpacted);
      expect(impactedCache).toContainEqual(impactedEntity);
    });

    it('replaces existing impacted entity in its type cache', async () => {
      const queryClient = createTestQueryClient();
      const IMPACTED_TYPE_ID = 'type-impacted';

      const savedEntity = createMockEntity({ id: 'entity-saved' });
      const originalImpacted = createMockEntity({
        id: 'entity-impacted',
        typeId: IMPACTED_TYPE_ID,
        payload: { name: 'Old' },
      });
      seedEntityCache(queryClient, WORKSPACE_ID, IMPACTED_TYPE_ID, [originalImpacted]);

      const updatedImpacted = createMockEntity({
        id: 'entity-impacted',
        typeId: IMPACTED_TYPE_ID,
        payload: { name: 'New' },
      });
      const saveResponse = createSaveResponse(savedEntity, {
        [IMPACTED_TYPE_ID]: [updatedImpacted],
      });
      mockedSaveEntity.mockResolvedValueOnce(saveResponse);

      const { result } = renderHook(
        () => useSaveEntityMutation(WORKSPACE_ID, TYPE_ID),
        { wrapper: createWrapper(queryClient) },
      );

      const request: SaveEntityRequest = { payload: {} };
      act(() => {
        result.current.mutate(request);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      const impactedCache = getEntityCache(queryClient, WORKSPACE_ID, IMPACTED_TYPE_ID);
      expect(impactedCache).toHaveLength(1);
      expect(impactedCache?.[0].payload).toEqual({ name: 'New' });
    });
  });

  describe('conflict/error handling', () => {
    it('calls onConflict when response contains errors', async () => {
      const queryClient = createTestQueryClient();
      const onConflict = jest.fn();

      mockedSaveEntity.mockResolvedValueOnce({ errors: ['conflict detected'] });

      const { result } = renderHook(
        () => useSaveEntityMutation(WORKSPACE_ID, TYPE_ID, undefined, onConflict),
        { wrapper: createWrapper(queryClient) },
      );

      const request: SaveEntityRequest = { id: 'entity-1', payload: {} };
      act(() => {
        result.current.mutate(request);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(onConflict).toHaveBeenCalledTimes(1);
      expect(onConflict).toHaveBeenCalledWith(request, { errors: ['conflict detected'] });
    });

    it('does not update cache when response contains errors', async () => {
      const queryClient = createTestQueryClient();
      const existingEntity = createMockEntity({ id: 'entity-existing' });
      seedEntityCache(queryClient, WORKSPACE_ID, TYPE_ID, [existingEntity]);

      mockedSaveEntity.mockResolvedValueOnce({ errors: ['validation failed'] });

      const { result } = renderHook(
        () => useSaveEntityMutation(WORKSPACE_ID, TYPE_ID),
        { wrapper: createWrapper(queryClient) },
      );

      const request: SaveEntityRequest = { payload: {} };
      act(() => {
        result.current.mutate(request);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      const cached = getEntityCache(queryClient, WORKSPACE_ID, TYPE_ID);
      // Cache should remain unchanged — only the original entity
      expect(cached).toHaveLength(1);
      expect(cached).toContainEqual(existingEntity);
    });

    it('shows error toast when response contains errors and no onConflict handler', async () => {
      const queryClient = createTestQueryClient();

      mockedSaveEntity.mockResolvedValueOnce({ errors: ['field X is invalid', 'missing Y'] });

      const { result } = renderHook(
        () => useSaveEntityMutation(WORKSPACE_ID, TYPE_ID),
        { wrapper: createWrapper(queryClient) },
      );

      const request: SaveEntityRequest = { payload: {} };
      act(() => {
        result.current.mutate(request);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(toast.error).toHaveBeenCalledWith(
        'Failed to save entity: field X is invalid, missing Y',
      );
    });
  });
});
