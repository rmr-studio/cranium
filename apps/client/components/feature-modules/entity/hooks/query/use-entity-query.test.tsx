import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactNode } from 'react';
import { useEntityQuery, ENTITY_PAGE_SIZE, getNextPageParam } from './use-entity-query';
import { EntityService } from '../../service/entity.service';
import { EntityQueryResponse } from '@/lib/types/entity';

// Mock dependencies
jest.mock('../../service/entity.service');
jest.mock('@/components/provider/auth-context', () => ({
  useAuth: () => ({ session: { access_token: 'test' }, loading: false }),
}));

const mockQueryEntities = EntityService.queryEntities as jest.MockedFunction<
  typeof EntityService.queryEntities
>;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  };
}

const workspaceId = 'ws-123';
const entityTypeId = 'type-456';

const mockPage = (
  entities: { id: string }[],
  hasNextPage: boolean,
  offset: number,
): EntityQueryResponse => ({
  entities: entities as any,
  hasNextPage,
  limit: ENTITY_PAGE_SIZE,
  offset,
});

describe('getNextPageParam', () => {
  it('returns next offset when hasNextPage is true', () => {
    const lastPage = mockPage([], true, 0);
    const result = getNextPageParam(lastPage, [lastPage]);
    expect(result).toBe(ENTITY_PAGE_SIZE);
  });

  it('returns undefined when hasNextPage is false', () => {
    const lastPage = mockPage([], false, 0);
    const result = getNextPageParam(lastPage, [lastPage]);
    expect(result).toBeUndefined();
  });

  it('calculates correct offset for multiple pages', () => {
    const page1 = mockPage([], true, 0);
    const page2 = mockPage([], true, ENTITY_PAGE_SIZE);
    const result = getNextPageParam(page2, [page1, page2]);
    expect(result).toBe(ENTITY_PAGE_SIZE * 2);
  });
});

describe('useEntityQuery', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('fetches first page on mount', async () => {
    mockQueryEntities.mockResolvedValue(mockPage([{ id: 'e1' }], false, 0));

    const { result } = renderHook(
      () => useEntityQuery({ workspaceId, entityTypeId }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockQueryEntities).toHaveBeenCalledWith(
      { access_token: 'test' },
      workspaceId,
      entityTypeId,
      { limit: ENTITY_PAGE_SIZE, offset: 0 },
      undefined,
    );
    expect(result.current.data?.pages).toHaveLength(1);
  });

  it('does not fetch when workspaceId is undefined', () => {
    renderHook(
      () => useEntityQuery({ workspaceId: undefined, entityTypeId }),
      { wrapper: createWrapper() },
    );

    expect(mockQueryEntities).not.toHaveBeenCalled();
  });

  it('does not fetch when entityTypeId is undefined', () => {
    renderHook(
      () => useEntityQuery({ workspaceId, entityTypeId: undefined }),
      { wrapper: createWrapper() },
    );

    expect(mockQueryEntities).not.toHaveBeenCalled();
  });

  it('passes search filter when debouncedSearch is provided', async () => {
    const searchableAttributeIds = ['attr-name', 'attr-title'];
    mockQueryEntities.mockResolvedValue(mockPage([], false, 0));

    renderHook(
      () =>
        useEntityQuery({
          workspaceId,
          entityTypeId,
          debouncedSearch: 'hello',
          searchableAttributeIds,
        }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(mockQueryEntities).toHaveBeenCalled());

    const calledFilter = mockQueryEntities.mock.calls[0][4];
    expect(calledFilter).toMatchObject({
      type: 'OR',
      conditions: expect.arrayContaining([
        expect.objectContaining({
          type: 'ATTRIBUTE',
          attributeId: 'attr-name',
          operator: 'CONTAINS',
        }),
        expect.objectContaining({
          type: 'ATTRIBUTE',
          attributeId: 'attr-title',
          operator: 'CONTAINS',
        }),
      ]),
    });
  });

  it('passes queryFilter when provided', async () => {
    const queryFilter = {
      type: 'Attribute' as const,
      attributeId: 'attr-1',
      operator: 'EQUALS' as any,
      value: { kind: 'Literal' as const, value: 'test' },
    };
    mockQueryEntities.mockResolvedValue(mockPage([], false, 0));

    renderHook(
      () => useEntityQuery({ workspaceId, entityTypeId, queryFilter }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(mockQueryEntities).toHaveBeenCalled());

    const calledFilter = mockQueryEntities.mock.calls[0][4];
    expect(calledFilter).toEqual(queryFilter);
  });

  it('combines search and queryFilter with AND', async () => {
    const queryFilter = {
      type: 'Attribute' as const,
      attributeId: 'attr-1',
      operator: 'EQUALS' as any,
      value: { kind: 'Literal' as const, value: 'test' },
    };
    mockQueryEntities.mockResolvedValue(mockPage([], false, 0));

    renderHook(
      () =>
        useEntityQuery({
          workspaceId,
          entityTypeId,
          debouncedSearch: 'hello',
          searchableAttributeIds: ['attr-name'],
          queryFilter,
        }),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(mockQueryEntities).toHaveBeenCalled());

    const calledFilter = mockQueryEntities.mock.calls[0][4];
    expect(calledFilter).toMatchObject({
      type: 'AND',
      conditions: expect.arrayContaining([
        expect.objectContaining({ type: 'ATTRIBUTE' }),
        queryFilter,
      ]),
    });
  });
});
