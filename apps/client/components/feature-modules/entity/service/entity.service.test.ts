import { EntityService } from './entity.service';
import { createEntityApi } from '@/lib/api/entity-api';
import { EntityQueryResponse } from '@/lib/types/entity';
import { FilterOperator } from '@/lib/types/entity';

// Mock the API factory
jest.mock('@/lib/api/entity-api');

const mockSession = { access_token: 'test-token' } as any;
const workspaceId = '550e8400-e29b-41d4-a716-446655440000';
const entityTypeId = '660e8400-e29b-41d4-a716-446655440001';

describe('EntityService.queryEntities', () => {
  const mockQueryEntities = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
    (createEntityApi as jest.Mock).mockReturnValue({
      queryEntities: mockQueryEntities,
    });
  });

  it('calls API with correct pagination params', async () => {
    const expectedResponse: EntityQueryResponse = {
      entities: [],
      hasNextPage: false,
      limit: 50,
      offset: 0,
    };
    mockQueryEntities.mockResolvedValue(expectedResponse);

    const result = await EntityService.queryEntities(
      mockSession,
      workspaceId,
      entityTypeId,
      { limit: 50, offset: 0 },
    );

    expect(mockQueryEntities).toHaveBeenCalledWith({
      workspaceId,
      entityTypeId,
      entityQueryRequest: {
        pagination: { limit: 50, offset: 0 },
        includeCount: false,
        maxDepth: 1,
      },
    });
    expect(result).toEqual(expectedResponse);
  });

  it('includes filter when provided', async () => {
    const filter = {
      type: 'Attribute' as const,
      attributeId: 'attr-1',
      operator: FilterOperator.Contains,
      value: { kind: 'Literal' as const, value: 'test' },
    };
    mockQueryEntities.mockResolvedValue({
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

    expect(mockQueryEntities).toHaveBeenCalledWith({
      workspaceId,
      entityTypeId,
      entityQueryRequest: {
        filter,
        pagination: { limit: 50, offset: 0 },
        includeCount: false,
        maxDepth: 1,
      },
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
    mockQueryEntities.mockRejectedValue(apiError);

    await expect(
      EntityService.queryEntities(mockSession, workspaceId, entityTypeId, { limit: 50, offset: 0 }),
    ).rejects.toMatchObject({
      status: 400,
      message: 'Invalid filter',
    });
  });
});
