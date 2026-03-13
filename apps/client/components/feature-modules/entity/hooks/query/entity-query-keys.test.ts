import { entityKeys } from './entity-query-keys';

describe('entityKeys', () => {
  const workspaceId = 'ws-123';
  const typeId = 'type-456';
  const typeKey = 'contacts';
  const entityTypeId = 'et-789';

  describe('entities', () => {
    it('returns base key for entity list invalidation', () => {
      expect(entityKeys.entities.base(workspaceId)).toEqual(['entities', workspaceId]);
    });

    it('returns full key for specific entity type list', () => {
      expect(entityKeys.entities.list(workspaceId, typeId)).toEqual([
        'entities',
        workspaceId,
        typeId,
      ]);
    });
  });

  describe('entityTypes', () => {
    it('returns key for entity types list', () => {
      expect(entityKeys.entityTypes.list(workspaceId)).toEqual(['entityTypes', workspaceId]);
    });

    it('returns key for single entity type by key', () => {
      expect(entityKeys.entityTypes.byKey(typeKey, workspaceId)).toEqual([
        'entityType',
        typeKey,
        workspaceId,
      ]);
    });

    it('returns key for single entity type by key with include', () => {
      expect(entityKeys.entityTypes.byKey(typeKey, workspaceId, ['relationships'])).toEqual([
        'entityType',
        typeKey,
        workspaceId,
        ['relationships'],
      ]);
    });
  });

  describe('semanticMetadata', () => {
    it('returns key for semantic metadata', () => {
      expect(entityKeys.semanticMetadata(workspaceId, entityTypeId)).toEqual([
        'semanticMetadata',
        workspaceId,
        entityTypeId,
      ]);
    });
  });
});
