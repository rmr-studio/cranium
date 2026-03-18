import { DataType, IconColour, IconType, SchemaType } from '@/lib/types/common';
import type { SchemaUUID } from '@/lib/types/common';
import {
  Entity,
  EntityLink,
  EntityPropertyType,
  EntityRelationshipCardinality,
  RelationshipDefinition,
  EntityType,
} from '@/lib/types/entity';
import {
  applyColumnOrdering,
  createAttributeEqualityFn,
  createRelationshipEqualityFn,
  extractUniqueAttributeValues,
  generateColumnsFromEntityType,
  generateSearchConfigFromEntityType,
  getEntityDisplayName,
  isDraftRow,
  isEntityRow,
  transformEntitiesToRows,
  EntityRow,
} from '@/components/feature-modules/entity/components/tables/entity-table-utils';
import { AccessorKeyColumnDef } from '@tanstack/react-table';

// ============================================================================
// Test Helpers
// ============================================================================

function createMockSchema(overrides: Partial<SchemaUUID> = {}): SchemaUUID {
  return {
    key: SchemaType.Text,
    type: DataType.String,
    label: 'Name',
    icon: { type: IconType.ALargeSmall, colour: IconColour.Neutral },
    required: false,
    unique: false,
    _protected: false,
    ...overrides,
  } as SchemaUUID;
}

function createMockEntity(overrides: Partial<Entity> = {}): Entity {
  return {
    id: 'entity-uuid-1234-5678',
    workspaceId: 'workspace-uuid',
    typeId: 'type-uuid',
    payload: {},
    icon: { type: IconType.Box, colour: IconColour.Neutral },
    identifierKey: 'name',
    sourceType: 'MANUAL' as Entity['sourceType'],
    syncVersion: 0,
    identifier: '',
    ...overrides,
  };
}

function createMockRelationshipDefinition(
  overrides: Partial<RelationshipDefinition> = {},
): RelationshipDefinition {
  return {
    id: 'rel-uuid',
    workspaceId: 'workspace-uuid',
    sourceEntityTypeId: 'type-uuid',
    name: 'Related Items',
    icon: { type: IconType.Link, colour: IconColour.Neutral },
    cardinalityDefault: EntityRelationshipCardinality.ManyToMany,
    _protected: false,
    targetRules: [],
    isPolymorphic: false,
    ...overrides,
  };
}

function createMockEntityLink(overrides: Partial<EntityLink> = {}): EntityLink {
  return {
    id: 'link-id',
    workspaceId: 'workspace-uuid',
    definitionId: 'rel-uuid',
    sourceEntityId: 'entity-uuid-1234-5678',
    icon: { type: IconType.Box, colour: IconColour.Neutral },
    key: 'related-type',
    label: 'Related Item',
    ...overrides,
  };
}

function createMockEntityType(overrides: Partial<EntityType> = {}): EntityType {
  return {
    id: 'type-uuid',
    key: 'contact',
    version: 1,
    icon: { type: IconType.User, colour: IconColour.Neutral },
    name: { singular: 'Contact', plural: 'Contacts' } as EntityType['name'],
    _protected: false,
    identifierKey: 'name',
    semanticGroup: 'CUSTOM' as EntityType['semanticGroup'],
    sourceType: 'MANUAL' as EntityType['sourceType'],
    readonly: false,
    schema: createMockSchema({ properties: {} }),
    columns: [],
    entitiesCount: 0,
    relationships: [],
    ...overrides,
  };
}

// ============================================================================
// isDraftRow
// ============================================================================

describe('isDraftRow', () => {
  it('returns true for a draft row', () => {
    const row: EntityRow = { _entityId: 'abc', _isDraft: true };
    expect(isDraftRow(row)).toBe(true);
  });

  it('returns false for a non-draft entity row', () => {
    const entity = createMockEntity();
    const row: EntityRow = { _entityId: 'abc', _isDraft: false, _entity: entity };
    expect(isDraftRow(row)).toBe(false);
  });
});

// ============================================================================
// isEntityRow
// ============================================================================

describe('isEntityRow', () => {
  it('returns true for a non-draft entity row', () => {
    const entity = createMockEntity();
    const row: EntityRow = { _entityId: 'abc', _isDraft: false, _entity: entity };
    expect(isEntityRow(row)).toBe(true);
  });

  it('returns false for a draft row', () => {
    const row: EntityRow = { _entityId: 'abc', _isDraft: true };
    expect(isEntityRow(row)).toBe(false);
  });
});

// ============================================================================
// transformEntitiesToRows
// ============================================================================

describe('transformEntitiesToRows', () => {
  it('filters out entities with null payload', () => {
    const entity = createMockEntity({ payload: null as unknown as Entity['payload'] });
    const rows = transformEntitiesToRows([entity]);
    expect(rows).toHaveLength(0);
  });

  it('sets _isDraft to false on each row', () => {
    const entity = createMockEntity({ payload: {} });
    const rows = transformEntitiesToRows([entity]);
    expect(rows[0]._isDraft).toBe(false);
  });

  it('sets _entityId from entity.id', () => {
    const entity = createMockEntity({ id: 'test-entity-id', payload: {} });
    const rows = transformEntitiesToRows([entity]);
    expect(rows[0]._entityId).toBe('test-entity-id');
  });

  it('attaches the entity to _entity', () => {
    const entity = createMockEntity({ payload: {} });
    const rows = transformEntitiesToRows([entity]);
    expect(rows[0]._entity).toBe(entity);
  });

  it('extracts primitive attribute values from payload', () => {
    const entity = createMockEntity({
      payload: {
        name: {
          payload: {
            type: EntityPropertyType.Attribute,
            value: 'John Doe',
            schemaType: SchemaType.Text,
          },
        },
      },
    });
    const rows = transformEntitiesToRows([entity]);
    expect(rows[0]['name']).toBe('John Doe');
  });

  it('extracts relations array for relationship payloads', () => {
    const link = createMockEntityLink();
    const entity = createMockEntity({
      payload: {
        'rel-field': {
          payload: {
            type: EntityPropertyType.Relationship,
            relations: [link],
          },
        },
      },
    });
    const rows = transformEntitiesToRows([entity]);
    expect(rows[0]['rel-field']).toEqual([link]);
  });

  it('handles multiple entities', () => {
    const e1 = createMockEntity({ id: 'id-1', payload: {} });
    const e2 = createMockEntity({ id: 'id-2', payload: {} });
    const rows = transformEntitiesToRows([e1, e2]);
    expect(rows).toHaveLength(2);
    expect(rows[0]._entityId).toBe('id-1');
    expect(rows[1]._entityId).toBe('id-2');
  });

  it('returns empty array for empty input', () => {
    expect(transformEntitiesToRows([])).toEqual([]);
  });
});

// ============================================================================
// getEntityDisplayName
// ============================================================================

describe('getEntityDisplayName', () => {
  it('returns the identifier field value when present', () => {
    const entity = createMockEntity({
      id: 'entity-uuid-1234-5678',
      identifierKey: 'name',
      payload: {
        name: {
          payload: {
            type: EntityPropertyType.Attribute,
            value: 'Acme Corp',
            schemaType: SchemaType.Text,
          },
        },
      },
    });
    expect(getEntityDisplayName(entity)).toBe('Acme Corp');
  });

  it('falls back to truncated ID when identifier key is missing from payload', () => {
    const entity = createMockEntity({
      id: 'abcdef12-0000-0000-0000-000000000000',
      identifierKey: 'name',
      payload: {},
    });
    expect(getEntityDisplayName(entity)).toBe('Entity abcdef12...');
  });

  it('falls back when identifier payload is a relationship payload', () => {
    const link = createMockEntityLink();
    const entity = createMockEntity({
      id: 'abcdef12-0000-0000-0000-000000000000',
      identifierKey: 'rel-field',
      payload: {
        'rel-field': {
          payload: {
            type: EntityPropertyType.Relationship,
            relations: [link],
          },
        },
      },
    });
    expect(getEntityDisplayName(entity)).toBe('Entity abcdef12...');
  });

  it('converts numeric identifier values to string', () => {
    const entity = createMockEntity({
      identifierKey: 'count',
      payload: {
        count: {
          payload: {
            type: EntityPropertyType.Attribute,
            value: 42,
            schemaType: SchemaType.Number,
          },
        },
      },
    });
    expect(getEntityDisplayName(entity)).toBe('42');
  });
});

// ============================================================================
// createAttributeEqualityFn
// ============================================================================

describe('createAttributeEqualityFn', () => {
  describe('text schema', () => {
    const schema = createMockSchema({ key: SchemaType.Text });
    const eq = createAttributeEqualityFn(schema);

    it('returns true for equal strings', () => {
      expect(eq('hello', 'hello')).toBe(true);
    });

    it('returns false for different strings', () => {
      expect(eq('hello', 'world')).toBe(false);
    });

    it('treats null and empty string as equal (normalizeEmpty)', () => {
      expect(eq(null, '')).toBe(true);
      expect(eq('', null)).toBe(true);
      expect(eq(null, null)).toBe(true);
      expect(eq('', '')).toBe(true);
    });

    it('returns false when one is empty and the other is not', () => {
      expect(eq('hello', null)).toBe(false);
      expect(eq(null, 'hello')).toBe(false);
    });
  });

  describe('number schema', () => {
    const schema = createMockSchema({ key: SchemaType.Number, type: DataType.Number });
    const eq = createAttributeEqualityFn(schema);

    it('returns true for equal numbers', () => {
      expect(eq(42, 42)).toBe(true);
    });

    it('returns false for different numbers', () => {
      expect(eq(42, 43)).toBe(false);
    });

    it('compares string numbers correctly', () => {
      expect(eq('42', 42)).toBe(true);
      expect(eq('3.14', 3.14)).toBe(true);
    });

    it('treats both NaN as equal', () => {
      expect(eq('not-a-number', NaN)).toBe(true);
    });

    it('returns false when one is NaN and the other is not', () => {
      expect(eq('not-a-number', 42)).toBe(false);
    });
  });

  describe('currency schema', () => {
    const schema = createMockSchema({ key: SchemaType.Currency, type: DataType.Number });
    const eq = createAttributeEqualityFn(schema);

    it('returns true for equal currency values', () => {
      expect(eq(100.5, 100.5)).toBe(true);
    });

    it('returns false for different currency values', () => {
      expect(eq(100.5, 200.0)).toBe(false);
    });
  });

  describe('percentage schema', () => {
    const schema = createMockSchema({ key: SchemaType.Percentage, type: DataType.Number });
    const eq = createAttributeEqualityFn(schema);

    it('returns true for equal percentage values', () => {
      expect(eq(75, 75)).toBe(true);
    });

    it('returns false for different percentage values', () => {
      expect(eq(75, 80)).toBe(false);
    });
  });

  describe('checkbox schema', () => {
    const schema = createMockSchema({ key: SchemaType.Checkbox, type: DataType.Boolean });
    const eq = createAttributeEqualityFn(schema);

    it('returns true when both are truthy', () => {
      expect(eq(true, true)).toBe(true);
      expect(eq(1, true)).toBe(true);
    });

    it('returns true when both are falsy', () => {
      expect(eq(false, false)).toBe(true);
      expect(eq(0, false)).toBe(true);
    });

    it('returns false when values differ', () => {
      expect(eq(true, false)).toBe(false);
    });
  });

  describe('multiselect schema', () => {
    const schema = createMockSchema({ key: SchemaType.MultiSelect, type: DataType.Array });
    const eq = createAttributeEqualityFn(schema);

    it('returns true for same arrays in the same order', () => {
      expect(eq(['a', 'b'], ['a', 'b'])).toBe(true);
    });

    it('returns true for same arrays in different order', () => {
      expect(eq(['b', 'a'], ['a', 'b'])).toBe(true);
    });

    it('returns false for arrays with different elements', () => {
      expect(eq(['a', 'b'], ['a', 'c'])).toBe(false);
    });

    it('returns false for arrays with different lengths', () => {
      expect(eq(['a'], ['a', 'b'])).toBe(false);
    });

    it('treats empty array and null as equal', () => {
      expect(eq([], null)).toBe(true);
      expect(eq(null, [])).toBe(true);
    });
  });
});

// ============================================================================
// createRelationshipEqualityFn
// ============================================================================

describe('createRelationshipEqualityFn', () => {
  const link1 = createMockEntityLink({ id: 'link-1', label: 'Alpha' });
  const link2 = createMockEntityLink({ id: 'link-2', label: 'Beta' });

  describe('single-select (OneToOne)', () => {
    const relationship = createMockRelationshipDefinition({
      cardinalityDefault: EntityRelationshipCardinality.OneToOne,
    });
    const eq = createRelationshipEqualityFn(relationship);

    it('returns true when first elements are equal', () => {
      expect(eq([link1], [link1])).toBe(true);
    });

    it('returns false when first elements differ', () => {
      expect(eq([link1], [link2])).toBe(false);
    });

    it('returns true when both are empty', () => {
      expect(eq([], [])).toBe(true);
    });

    it('returns false when one is empty and the other is not', () => {
      expect(eq([link1], [])).toBe(false);
    });
  });

  describe('single-select (ManyToOne)', () => {
    const relationship = createMockRelationshipDefinition({
      cardinalityDefault: EntityRelationshipCardinality.ManyToOne,
    });
    const eq = createRelationshipEqualityFn(relationship);

    it('returns true when first elements are equal', () => {
      expect(eq([link1], [link1])).toBe(true);
    });

    it('returns false when first elements differ', () => {
      expect(eq([link1], [link2])).toBe(false);
    });
  });

  describe('multi-select (ManyToMany)', () => {
    const relationship = createMockRelationshipDefinition({
      cardinalityDefault: EntityRelationshipCardinality.ManyToMany,
    });
    const eq = createRelationshipEqualityFn(relationship);

    it('returns true for same links in the same order', () => {
      expect(eq([link1, link2], [link1, link2])).toBe(true);
    });

    it('returns true for same links in different order', () => {
      expect(eq([link2, link1], [link1, link2])).toBe(true);
    });

    it('returns false for different links', () => {
      expect(eq([link1], [link2])).toBe(false);
    });

    it('returns true when both are empty', () => {
      expect(eq([], [])).toBe(true);
    });
  });

  describe('multi-select (OneToMany)', () => {
    const relationship = createMockRelationshipDefinition({
      cardinalityDefault: EntityRelationshipCardinality.OneToMany,
    });
    const eq = createRelationshipEqualityFn(relationship);

    it('returns true for same links', () => {
      expect(eq([link1, link2], [link1, link2])).toBe(true);
    });

    it('returns false for different links', () => {
      expect(eq([link1], [link2])).toBe(false);
    });
  });
});

// ============================================================================
// extractUniqueAttributeValues
// ============================================================================

describe('extractUniqueAttributeValues', () => {
  it('returns empty array for empty entities', () => {
    expect(extractUniqueAttributeValues([], 'name')).toEqual([]);
  });

  it('returns empty array when attribute is absent from all entities', () => {
    const entity = createMockEntity({ payload: {} });
    expect(extractUniqueAttributeValues([entity], 'name')).toEqual([]);
  });

  it('skips entities where the attribute is null or undefined', () => {
    const entity = createMockEntity({
      payload: {
        name: null as unknown as Entity['payload'][string],
      },
    });
    expect(extractUniqueAttributeValues([entity], 'name')).toEqual([]);
  });

  it('extracts the inner attribute value from the EntityAttribute wrapper', () => {
    const entity = createMockEntity({
      payload: {
        name: {
          payload: {
            type: EntityPropertyType.Attribute,
            value: 'John',
            schemaType: SchemaType.Text,
          },
        },
      },
    });
    const results = extractUniqueAttributeValues([entity], 'name');
    expect(results).toHaveLength(1);
    expect(results[0].value).toBe('John');
  });

  it('deduplicates values across entities', () => {
    const wrapper = {
      payload: {
        type: EntityPropertyType.Attribute,
        value: 'John',
        schemaType: SchemaType.Text,
      },
    };
    const e1 = createMockEntity({ id: 'id-1', payload: { name: wrapper } });
    const e2 = createMockEntity({ id: 'id-2', payload: { name: wrapper } });
    const results = extractUniqueAttributeValues([e1, e2], 'name');
    expect(results).toHaveLength(1);
    expect(results[0].value).toBe('John');
  });

  it('returns results sorted alphabetically', () => {
    const e1 = createMockEntity({
      id: 'id-1',
      payload: {
        status: { payload: { type: EntityPropertyType.Attribute, value: 'inactive', schemaType: SchemaType.Text } },
      },
    });
    const e2 = createMockEntity({
      id: 'id-2',
      payload: {
        status: { payload: { type: EntityPropertyType.Attribute, value: 'active', schemaType: SchemaType.Text } },
      },
    });
    const results = extractUniqueAttributeValues([e1, e2], 'status');
    expect(results).toHaveLength(2);
    expect(results[0].value).toBe('active');
    expect(results[1].value).toBe('inactive');
  });
});

// ============================================================================
// generateSearchConfigFromEntityType
// ============================================================================

describe('generateSearchConfigFromEntityType', () => {
  it('returns empty array when schema has no properties', () => {
    const entityType = createMockEntityType({
      schema: createMockSchema({ properties: undefined }),
    });
    expect(generateSearchConfigFromEntityType(entityType)).toEqual([]);
  });

  it('includes non-protected string attributes', () => {
    const entityType = createMockEntityType({
      schema: createMockSchema({
        properties: {
          name: createMockSchema({ key: SchemaType.Text, type: DataType.String, _protected: false }),
        },
      }),
    });
    expect(generateSearchConfigFromEntityType(entityType)).toContain('name');
  });

  it('includes protected string attributes (e.g. id) for search', () => {
    const entityType = createMockEntityType({
      schema: createMockSchema({
        properties: {
          id: createMockSchema({ key: SchemaType.Id, type: DataType.String, _protected: true }),
          name: createMockSchema({ key: SchemaType.Text, type: DataType.String, _protected: false }),
        },
      }),
    });
    const result = generateSearchConfigFromEntityType(entityType);
    expect(result).toContain('id');
    expect(result).toContain('name');
  });

  it('excludes non-string attributes', () => {
    const entityType = createMockEntityType({
      schema: createMockSchema({
        properties: {
          count: createMockSchema({ key: SchemaType.Number, type: DataType.Number, _protected: false }),
          active: createMockSchema({ key: SchemaType.Checkbox, type: DataType.Boolean, _protected: false }),
          name: createMockSchema({ key: SchemaType.Text, type: DataType.String, _protected: false }),
        },
      }),
    });
    const result = generateSearchConfigFromEntityType(entityType);
    expect(result).toEqual(['name']);
  });
});

// ============================================================================
// applyColumnOrdering
// ============================================================================

describe('applyColumnOrdering', () => {
  function makeColumn(key: string): AccessorKeyColumnDef<EntityRow> {
    return { accessorKey: key } as AccessorKeyColumnDef<EntityRow>;
  }

  const colA = makeColumn('a');
  const colB = makeColumn('b');
  const colC = makeColumn('c');
  const columns = [colA, colB, colC];

  it('returns columns unchanged when no config is provided', () => {
    expect(applyColumnOrdering(columns, undefined)).toEqual(columns);
  });

  it('reorders columns according to config.order', () => {
    const result = applyColumnOrdering(columns, { order: ['c', 'a', 'b'], overrides: {} });
    expect(result.map((c) => c['accessorKey'])).toEqual(['c', 'a', 'b']);
  });

  it('hides columns with visible: false', () => {
    const result = applyColumnOrdering(columns, {
      order: ['a', 'b', 'c'],
      overrides: { b: { visible: false } },
    });
    expect(result.map((c) => c['accessorKey'])).toEqual(['a', 'c']);
  });

  it('appends columns not mentioned in order to the end', () => {
    const result = applyColumnOrdering(columns, { order: ['a'], overrides: {} });
    expect(result.map((c) => c['accessorKey'])).toEqual(['a', 'b', 'c']);
  });

  it('hides unordered columns when visible: false', () => {
    // b and c are not in order; c has visible:false
    const result = applyColumnOrdering(columns, {
      order: ['a'],
      overrides: { c: { visible: false } },
    });
    expect(result.map((c) => c['accessorKey'])).toEqual(['a', 'b']);
  });

  it('returns empty array when all columns are hidden', () => {
    const result = applyColumnOrdering(columns, {
      order: ['a', 'b', 'c'],
      overrides: {
        a: { visible: false },
        b: { visible: false },
        c: { visible: false },
      },
    });
    expect(result).toEqual([]);
  });

  it('ignores order entries that have no matching column', () => {
    const result = applyColumnOrdering(columns, {
      order: ['a', 'nonexistent', 'c'],
      overrides: {},
    });
    expect(result.map((c) => c['accessorKey'])).toEqual(['a', 'c', 'b']);
  });
});

// ============================================================================
// generateColumnsFromEntityType
// ============================================================================

describe('generateColumnsFromEntityType', () => {
  it('does not create edit config for SchemaType.Id fields even when editing enabled', () => {
    const entityType = createMockEntityType({
      schema: createMockSchema({
        properties: {
          record_id: createMockSchema({ key: SchemaType.Id, type: DataType.String, _protected: true }),
          title: createMockSchema({ key: SchemaType.Text, type: DataType.String }),
        },
      }),
      columns: [
        { key: 'record_id', type: EntityPropertyType.Attribute, visible: true, width: 120 },
        { key: 'title', type: EntityPropertyType.Attribute, visible: true, width: 200 },
      ],
    });

    const columns = generateColumnsFromEntityType(entityType, { enableEditing: true });
    const idColumn = columns.find((c) => c.accessorKey === 'record_id');
    const titleColumn = columns.find((c) => c.accessorKey === 'title');

    expect(idColumn).toBeDefined();
    expect(idColumn!.meta?.edit).toBeUndefined();
    expect(titleColumn).toBeDefined();
    expect(titleColumn!.meta?.edit).toBeDefined();
  });
});
