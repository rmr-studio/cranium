import { InfiniteData } from '@tanstack/react-query';
import { EntityQueryResponse } from '@/lib/types/entity';
import {
  updateEntityInPages,
  removeEntitiesFromPages,
  replaceEntitiesInPages,
} from './entity-cache.utils';

function makePages(...pages: { id: string }[][]): InfiniteData<EntityQueryResponse> {
  return {
    pages: pages.map((entities, i) => ({
      entities: entities as any,
      hasNextPage: i < pages.length - 1,
      limit: 50,
      offset: i * 50,
    })),
    pageParams: pages.map((_, i) => i * 50),
  };
}

describe('entity-cache.utils', () => {
  describe('updateEntityInPages', () => {
    it('replaces an entity in the correct page', () => {
      const data = makePages([{ id: 'a' }, { id: 'b' }], [{ id: 'c' }]);
      const updated = { id: 'b', name: 'updated' } as any;
      const result = updateEntityInPages(data, 'b', updated);

      expect(result!.pages[0].entities[1]).toEqual(updated);
      expect(result!.pages[1].entities[0]).toEqual({ id: 'c' });
    });

    it('returns unchanged data if entity not found', () => {
      const data = makePages([{ id: 'a' }]);
      const result = updateEntityInPages(data, 'missing', { id: 'missing' } as any);

      expect(result!.pages[0].entities).toEqual([{ id: 'a' }]);
    });

    it('returns original data when input is undefined', () => {
      expect(updateEntityInPages(undefined, 'a', {} as any)).toBeUndefined();
    });
  });

  describe('removeEntitiesFromPages', () => {
    it('removes entities matching the ID set', () => {
      const data = makePages([{ id: 'a' }, { id: 'b' }], [{ id: 'c' }]);
      const result = removeEntitiesFromPages(data, new Set(['a', 'c']));

      expect(result!.pages[0].entities).toEqual([{ id: 'b' }]);
      expect(result!.pages[1].entities).toEqual([]);
    });

    it('returns original data when input is undefined', () => {
      expect(removeEntitiesFromPages(undefined, new Set(['a']))).toBeUndefined();
    });
  });

  describe('replaceEntitiesInPages', () => {
    it('replaces multiple entities by ID', () => {
      const data = makePages([{ id: 'a' }, { id: 'b' }]);
      const replacements = new Map([['b', { id: 'b', name: 'new' } as any]]);
      const result = replaceEntitiesInPages(data, replacements);

      expect(result!.pages[0].entities[0]).toEqual({ id: 'a' });
      expect(result!.pages[0].entities[1]).toEqual({ id: 'b', name: 'new' });
    });
  });
});
