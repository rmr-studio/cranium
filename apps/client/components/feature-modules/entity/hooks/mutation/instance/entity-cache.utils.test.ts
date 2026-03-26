import { InfiniteData } from '@tanstack/react-query';
import { Entity, EntityQueryResponse } from '@/lib/types/entity';
import {
  updateEntityInPages,
  removeEntitiesFromPages,
  replaceEntitiesInPages,
} from '@/components/feature-modules/entity/hooks/mutation/instance/entity-cache.utils';
import { createMockEntity } from '@/components/feature-modules/entity/hooks/mutation/test-utils/mutation-test-helpers';

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

describe('entity-cache.utils', () => {
  describe('updateEntityInPages', () => {
    it('replaces an entity in the correct page', () => {
      const a = createMockEntity({ id: 'a' });
      const b = createMockEntity({ id: 'b' });
      const c = createMockEntity({ id: 'c' });
      const data = makePages([a, b], [c]);
      const updated = createMockEntity({ id: 'b', payload: { name: 'updated' } });
      const result = updateEntityInPages(data, 'b', updated);

      expect(result!.pages[0].entities[1]).toEqual(updated);
      expect(result!.pages[1].entities[0]).toEqual(c);
    });

    it('returns unchanged data if entity not found', () => {
      const a = createMockEntity({ id: 'a' });
      const data = makePages([a]);
      const result = updateEntityInPages(data, 'missing', createMockEntity({ id: 'missing' }));

      expect(result!.pages[0].entities).toEqual([a]);
    });

    it('returns original data when input is undefined', () => {
      expect(updateEntityInPages(undefined, 'a', createMockEntity({ id: 'a' }))).toBeUndefined();
    });
  });

  describe('removeEntitiesFromPages', () => {
    it('removes entities matching the ID set', () => {
      const a = createMockEntity({ id: 'a' });
      const b = createMockEntity({ id: 'b' });
      const c = createMockEntity({ id: 'c' });
      const data = makePages([a, b], [c]);
      const result = removeEntitiesFromPages(data, new Set(['a', 'c']));

      expect(result!.pages[0].entities).toEqual([b]);
      expect(result!.pages[1].entities).toEqual([]);
    });

    it('returns original data when input is undefined', () => {
      expect(removeEntitiesFromPages(undefined, new Set(['a']))).toBeUndefined();
    });
  });

  describe('replaceEntitiesInPages', () => {
    it('replaces multiple entities by ID', () => {
      const a = createMockEntity({ id: 'a' });
      const b = createMockEntity({ id: 'b' });
      const data = makePages([a, b]);
      const replacement = createMockEntity({ id: 'b', payload: { name: 'new' } });
      const replacements = new Map([['b', replacement]]);
      const result = replaceEntitiesInPages(data, replacements);

      expect(result!.pages[0].entities[0]).toEqual(a);
      expect(result!.pages[0].entities[1]).toEqual(replacement);
    });

    it('returns original data when input is undefined', () => {
      expect(replaceEntitiesInPages(undefined, new Map())).toBeUndefined();
    });
  });
});
