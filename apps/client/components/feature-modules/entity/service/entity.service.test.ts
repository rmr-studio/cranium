import { EntityService } from '@/components/feature-modules/entity/service/entity.service';
import { createEntityApi } from '@/lib/api/entity-api';
import { EntityQueryResponse, EntitySelectType, FilterOperator, type QueryFilter } from '@/lib/types/entity';
import type { DeleteEntityRequest } from '@/lib/types/entity';
import type { Session } from '@/lib/auth/auth.types';

// Mock the API factory
jest.mock('@/lib/api/entity-api');

const mockSession: Session = { access_token: 'test-token', expires_at: 9999999999, user: { id: 'test-user', metadata: {} } };
const workspaceId = '550e8400-e29b-41d4-a716-446655440000';
const entityTypeId = '660e8400-e29b-41d4-a716-446655440001';

describe('EntityService.queryEntities', () => {
  const mockQueryEntitiesRaw = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
    (createEntityApi as jest.Mock).mockReturnValue({
      queryEntitiesRaw: mockQueryEntitiesRaw,
    });
  });

  function mockRawResponse(data: EntityQueryResponse) {
    mockQueryEntitiesRaw.mockResolvedValue({
      value: () => Promise.resolve(data),
    });
  }

  it('calls API with correct pagination params (no filter)', async () => {
    const expectedResponse: EntityQueryResponse = {
      entities: [],
      hasNextPage: false,
      limit: 50,
      offset: 0,
    };
    mockRawResponse(expectedResponse);

    const result = await EntityService.queryEntities(
      mockSession,
      workspaceId,
      entityTypeId,
      { limit: 50, offset: 0 },
    );

    expect(mockQueryEntitiesRaw).toHaveBeenCalledWith(
      {
        workspaceId,
        entityTypeId,
        entityQueryRequest: {
          pagination: { limit: 50, offset: 0 },
          includeCount: false,
          maxDepth: 1,
        },
      },
      undefined, // no initOverrides when no filter
    );
    expect(result).toEqual(expectedResponse);
  });

  it('passes filter via initOverrides to bypass recursive ToJSON', async () => {
    const filter = {
      type: 'Attribute' as const,
      attributeId: 'attr-1',
      operator: FilterOperator.Contains,
      value: { kind: 'Literal' as const, value: 'test' },
    };
    mockRawResponse({
      entities: [],
      hasNextPage: false,
      limit: 50,
      offset: 0,
    });

    await EntityService.queryEntities(
      mockSession,
      workspaceId,
      entityTypeId,
      { limit: 50, offset: 0 },
      filter,
    );

    // Safe request (without filter) is passed to the API to avoid recursive ToJSON
    expect(mockQueryEntitiesRaw).toHaveBeenCalledWith(
      {
        workspaceId,
        entityTypeId,
        entityQueryRequest: {
          pagination: { limit: 50, offset: 0 },
          includeCount: false,
          maxDepth: 1,
        },
      },
      expect.any(Function), // initOverrides function when filter present
    );

    // Verify the override function returns the full body with filter
    const overrideFn = mockQueryEntitiesRaw.mock.calls[0][1];
    const overrideResult = await overrideFn();
    expect(overrideResult.body).toEqual({
      pagination: { limit: 50, offset: 0 },
      includeCount: false,
      maxDepth: 1,
      filter,
    });
  });

  it('validates session before calling API', async () => {
    await expect(
      EntityService.queryEntities(null, workspaceId, entityTypeId, { limit: 50, offset: 0 }),
    ).rejects.toMatchObject({ error: 'NO_SESSION' });
  });

  it('validates workspaceId is a UUID', async () => {
    await expect(
      EntityService.queryEntities(mockSession, 'not-a-uuid', entityTypeId, { limit: 50, offset: 0 }),
    ).rejects.toMatchObject({ error: 'INVALID_ID' });
  });

  it('normalizes API errors', async () => {
    const { ResponseError } = await import('@/lib/types');
    const mockResponse = {
      status: 400,
      statusText: 'Bad Request',
      json: () => Promise.resolve({ statusCode: 400, error: 'BAD_REQUEST', message: 'Invalid filter' }),
    } as Response;
    const apiError = new ResponseError(mockResponse, 'Bad Request');
    mockQueryEntitiesRaw.mockRejectedValue(apiError);

    await expect(
      EntityService.queryEntities(mockSession, workspaceId, entityTypeId, { limit: 50, offset: 0 }),
    ).rejects.toMatchObject({
      status: 400,
      message: 'Invalid filter',
    });
  });
});

describe('EntityService.deleteEntities', () => {
  const mockDeleteEntitiesRaw = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
    (createEntityApi as jest.Mock).mockReturnValue({
      deleteEntitiesRaw: mockDeleteEntitiesRaw,
    });
  });

  function mockRawDeleteResponse(data: { deletedCount: number }) {
    mockDeleteEntitiesRaw.mockResolvedValue({
      value: () => Promise.resolve(data),
    });
  }

  it('sends a BY_ID request with entity IDs', async () => {
    const request: DeleteEntityRequest = {
      type: EntitySelectType.ById,
      entityTypeId,
      entityIds: ['id-1', 'id-2'],
    };

    mockRawDeleteResponse({ deletedCount: 2 });

    const result = await EntityService.deleteEntities(mockSession, workspaceId, request);

    expect(mockDeleteEntitiesRaw).toHaveBeenCalledWith(
      {
        workspaceId,
        deleteEntityRequest: { ...request, filter: undefined },
      },
      undefined, // no initOverrides when no filter
    );
    expect(result.deletedCount).toBe(2);
  });

  it('sends an ALL request with filter and exclusions', async () => {
    const filter: QueryFilter = {
      type: 'And',
      conditions: [],
    } as QueryFilter;

    const request: DeleteEntityRequest = {
      type: EntitySelectType.All,
      entityTypeId,
      filter,
      excludeIds: ['exclude-1'],
    };

    mockRawDeleteResponse({ deletedCount: 99 });

    const result = await EntityService.deleteEntities(mockSession, workspaceId, request);

    expect(mockDeleteEntitiesRaw).toHaveBeenCalledWith(
      {
        workspaceId,
        deleteEntityRequest: { ...request, filter: undefined },
      },
      expect.any(Function), // initOverrides function when filter present
    );
    expect(result.deletedCount).toBe(99);
  });

  it('validates session before calling API', async () => {
    const request: DeleteEntityRequest = {
      type: EntitySelectType.ById,
      entityTypeId,
      entityIds: ['id-1'],
    };

    await expect(
      EntityService.deleteEntities(null, workspaceId, request),
    ).rejects.toMatchObject({ error: 'NO_SESSION' });
  });

  it('validates workspaceId is a UUID', async () => {
    const request: DeleteEntityRequest = {
      type: EntitySelectType.ById,
      entityTypeId,
      entityIds: ['id-1'],
    };

    await expect(
      EntityService.deleteEntities(mockSession, 'not-a-uuid', request),
    ).rejects.toMatchObject({ error: 'INVALID_ID' });
  });

  it('normalizes API errors', async () => {
    const { ResponseError } = await import('@/lib/types');
    const mockResponse = {
      status: 400,
      statusText: 'Bad Request',
      json: () => Promise.resolve({ statusCode: 400, error: 'BAD_REQUEST', message: 'Invalid request' }),
    } as Response;
    const apiError = new ResponseError(mockResponse, 'Bad Request');
    mockDeleteEntitiesRaw.mockRejectedValue(apiError);

    const request: DeleteEntityRequest = {
      type: EntitySelectType.ById,
      entityTypeId,
      entityIds: ['id-1'],
    };

    await expect(
      EntityService.deleteEntities(mockSession, workspaceId, request),
    ).rejects.toMatchObject({
      status: 400,
      message: 'Invalid request',
    });
  });
});
