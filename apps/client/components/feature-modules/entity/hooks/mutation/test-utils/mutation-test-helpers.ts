import { QueryClient } from '@tanstack/react-query';
import { Entity, SaveEntityResponse, DeleteEntityResponse } from '@/lib/types/entity';
import { entityKeys } from '@/components/feature-modules/entity/hooks/query/entity-query-keys';

/**
 * Creates a QueryClient configured for testing:
 * - retry:false so mutations fail immediately without retries
 * - gcTime:Infinity so cached data is never garbage collected during a test run
 */
export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: Infinity,
      },
      mutations: {
        retry: false,
      },
    },
  });
}

/**
 * Seeds the entity list cache for a given workspace + type combination.
 * Uses the same key that mutation hooks use so setQueryData/invalidateQueries
 * operations in tests interact with the correct cache entry.
 */
export function seedEntityCache(
  queryClient: QueryClient,
  workspaceId: string,
  typeId: string,
  entities: Entity[],
): void {
  queryClient.setQueryData<Entity[]>(
    entityKeys.entities.list(workspaceId, typeId),
    entities,
  );
}

/**
 * Reads the entity list cache for a given workspace + type combination.
 * Returns undefined if no data is cached.
 */
export function getEntityCache(
  queryClient: QueryClient,
  workspaceId: string,
  typeId: string,
): Entity[] | undefined {
  return queryClient.getQueryData<Entity[]>(
    entityKeys.entities.list(workspaceId, typeId),
  );
}

/**
 * Creates a mock Entity with sensible defaults.
 * The `id` field is required; all other fields can be overridden.
 */
export function createMockEntity(overrides: Partial<Entity> & { id: string }): Entity {
  return {
    workspaceId: 'workspace-1',
    typeId: 'type-1',
    payload: {},
    icon: {},
    identifierKey: 'name',
    sourceType: 'USER_CREATED',
    syncVersion: 0,
    identifier: `entity-${overrides.id}`,
    ...overrides,
  } as Entity;
}

/**
 * Builds a SaveEntityResponse wrapping the given entity.
 * Optionally includes impacted entities keyed by entity type ID.
 */
export function createSaveResponse(
  entity: Entity,
  impactedEntities?: { [key: string]: Array<Entity> },
): SaveEntityResponse {
  return {
    entity,
    impactedEntities,
  };
}

/**
 * Builds a DeleteEntityResponse with the given deleted count.
 * Optionally includes updated entities keyed by entity type ID.
 */
export function createDeleteResponse(
  deletedCount: number,
  updatedEntities?: { [key: string]: Array<Entity> },
): DeleteEntityResponse {
  return {
    deletedCount,
    updatedEntities,
  };
}
