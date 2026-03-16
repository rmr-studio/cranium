import { InfiniteData } from '@tanstack/react-query';
import { Entity, EntityQueryResponse } from '@/lib/types/entity';

/**
 * Update a single entity by ID across all pages in an InfiniteData cache entry.
 * Returns a new InfiniteData object (immutable).
 */
export function updateEntityInPages(
  data: InfiniteData<EntityQueryResponse> | undefined,
  entityId: string,
  updatedEntity: Entity,
): InfiniteData<EntityQueryResponse> | undefined {
  if (!data) return data;

  return {
    ...data,
    pages: data.pages.map((page) => ({
      ...page,
      entities: page.entities.map((e) =>
        e.id === entityId ? updatedEntity : e,
      ),
    })),
  };
}

/**
 * Remove entities by ID set across all pages in an InfiniteData cache entry.
 */
export function removeEntitiesFromPages(
  data: InfiniteData<EntityQueryResponse> | undefined,
  idsToRemove: Set<string>,
): InfiniteData<EntityQueryResponse> | undefined {
  if (!data) return data;

  return {
    ...data,
    pages: data.pages.map((page) => ({
      ...page,
      entities: page.entities.filter((e) => !idsToRemove.has(e.id)),
    })),
  };
}

/**
 * Replace multiple entities by ID map across all pages in an InfiniteData cache entry.
 */
export function replaceEntitiesInPages(
  data: InfiniteData<EntityQueryResponse> | undefined,
  replacements: Map<string, Entity>,
): InfiniteData<EntityQueryResponse> | undefined {
  if (!data) return data;

  return {
    ...data,
    pages: data.pages.map((page) => ({
      ...page,
      entities: page.entities.map((e) => replacements.get(e.id) ?? e),
    })),
  };
}
