import { DataType, IconColour, IconType, SchemaType } from '@/lib/types/common';
import type { SchemaUUID } from '@/lib/types/common';
import {
  Entity,
  EntityPropertyType,
} from '@/lib/types/entity';
import { buildEntityUpdatePayload, deriveSchemaOptionsUpdate } from '@/components/feature-modules/entity/util/entity-payload.util';

function createMockEntity(overrides: Partial<Entity> = {}): Entity {
  return {
    id: 'entity-1',
    identifierKey: 'name',
    workspaceId: 'ws-1',
    entityTypeId: 'type-1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    payload: {
      name: {
        payload: {
          type: EntityPropertyType.Attribute,
          value: 'Test Entity',
          schemaType: 'text',
        },
      },
      industry: {
        payload: {
          type: EntityPropertyType.Attribute,
          value: 'Finance',
          schemaType: 'select',
        },
      },
      employees: {
        payload: {
          type: EntityPropertyType.Relationship,
          relations: [
            { id: 'emp-1', label: 'Employee 1', key: 'employees', workspaceId: 'ws-1', sourceEntityId: 'entity-1', definitionId: 'rel-1', icon: { type: 'icon', colour: '#000' } },
          ],
        },
      },
    },
    ...overrides,
  } as Entity;
}

describe('buildEntityUpdatePayload', () => {
  it('preserves existing attribute payloads and applies the updated column', () => {
    const entity = createMockEntity();
    const result = buildEntityUpdatePayload(entity, 'industry', {
      payload: {
        type: EntityPropertyType.Attribute,
        value: 'Technology',
        schemaType: 'select',
      },
    });

    expect(result.id).toBe('entity-1');
    expect(result.payload.industry.payload).toEqual({
      type: EntityPropertyType.Attribute,
      value: 'Technology',
      schemaType: 'select',
    });
    expect(result.payload.name.payload).toEqual({
      type: EntityPropertyType.Attribute,
      value: 'Test Entity',
      schemaType: 'text',
    });
  });

  it('converts relationship payloads to ID arrays', () => {
    const entity = createMockEntity();
    const result = buildEntityUpdatePayload(entity, 'name', {
      payload: {
        type: EntityPropertyType.Attribute,
        value: 'Updated Name',
        schemaType: 'text',
      },
    });

    expect(result.payload.employees.payload).toEqual({
      type: EntityPropertyType.Relationship,
      relations: ['emp-1'],
    });
  });

  it('applies relationship update correctly', () => {
    const entity = createMockEntity();
    const result = buildEntityUpdatePayload(entity, 'employees', {
      payload: {
        type: EntityPropertyType.Relationship,
        relations: ['emp-1', 'emp-2'],
      },
    });

    expect(result.payload.employees.payload).toEqual({
      type: EntityPropertyType.Relationship,
      relations: ['emp-1', 'emp-2'],
    });
  });
});

// ---------------------------------------------------------------------------
// deriveSchemaOptionsUpdate
// ---------------------------------------------------------------------------

function makeSchema(key: SchemaType, overrides: Partial<SchemaUUID> = {}): SchemaUUID {
  return {
    key,
    label: 'Test',
    type: DataType.String,
    icon: { type: IconType.Circle, colour: IconColour.Neutral },
    required: false,
    unique: false,
    _protected: false,
    ...overrides,
  };
}

describe('deriveSchemaOptionsUpdate', () => {
  describe('MultiSelect', () => {
    it('returns updated _enum when new values are present', () => {
      const schema = makeSchema(SchemaType.MultiSelect, {
        options: { _enum: ['bug', 'feature'] },
      });
      const result = deriveSchemaOptionsUpdate(schema, ['bug', 'enhancement']);
      expect(result).toEqual({ _enum: ['bug', 'feature', 'enhancement'] });
    });

    it('returns null when all values already exist', () => {
      const schema = makeSchema(SchemaType.MultiSelect, {
        options: { _enum: ['bug', 'feature'] },
      });
      expect(deriveSchemaOptionsUpdate(schema, ['bug', 'feature'])).toBeNull();
    });

    it('returns new _enum when existing _enum is empty', () => {
      const schema = makeSchema(SchemaType.MultiSelect, { options: { _enum: [] } });
      const result = deriveSchemaOptionsUpdate(schema, ['alpha', 'beta']);
      expect(result).toEqual({ _enum: ['alpha', 'beta'] });
    });

    it('returns new _enum when no options exist', () => {
      const schema = makeSchema(SchemaType.MultiSelect);
      const result = deriveSchemaOptionsUpdate(schema, ['new-tag']);
      expect(result).toEqual({ _enum: ['new-tag'] });
    });

    it('returns null for non-array value', () => {
      const schema = makeSchema(SchemaType.MultiSelect, {
        options: { _enum: ['a'] },
      });
      expect(deriveSchemaOptionsUpdate(schema, 'not-an-array')).toBeNull();
    });
  });

  describe('Select', () => {
    it('returns updated _enum when value is new', () => {
      const schema = makeSchema(SchemaType.Select, {
        options: { _enum: ['todo', 'done'] },
      });
      const result = deriveSchemaOptionsUpdate(schema, 'in_progress');
      expect(result).toEqual({ _enum: ['todo', 'done', 'in_progress'] });
    });

    it('returns null when value already exists', () => {
      const schema = makeSchema(SchemaType.Select, {
        options: { _enum: ['todo', 'done'] },
      });
      expect(deriveSchemaOptionsUpdate(schema, 'todo')).toBeNull();
    });

    it('returns null for empty string value', () => {
      const schema = makeSchema(SchemaType.Select, {
        options: { _enum: ['a'] },
      });
      expect(deriveSchemaOptionsUpdate(schema, '')).toBeNull();
    });
  });

  describe('non-enum types', () => {
    it.each([
      SchemaType.Text,
      SchemaType.Number,
      SchemaType.Checkbox,
      SchemaType.Date,
      SchemaType.Email,
      SchemaType.Url,
    ])('returns null for %s', (schemaType) => {
      const schema = makeSchema(schemaType);
      expect(deriveSchemaOptionsUpdate(schema, 'any-value')).toBeNull();
    });
  });
});
