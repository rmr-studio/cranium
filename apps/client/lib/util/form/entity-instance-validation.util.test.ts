import { IconColour, IconType, SchemaType } from '@/lib/types/common';
import type { SchemaUUID } from '@/lib/types/common';
import { EntityRelationshipCardinality } from '@/lib/types/entity';
import type { RelationshipDefinition } from '@/lib/types/entity';
import {
  buildDefaultValuesFromEntityType,
  buildFieldSchema,
  buildRelationshipFieldSchema,
  buildZodSchemaFromEntityType,
  getDefaultValueForSchema,
} from '@/lib/util/form/entity-instance-validation.util';
import { attributeTypes } from '@/lib/util/form/schema.util';
import {
  complexAllSchemaTypesEntityType,
  edgeCaseEntityType,
  entityTypeWithId,
  relationshipHeavyEntityType,
  simpleContactEntityType,
} from '@/lib/util/form/test-fixtures/entity-type-fixtures';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const VALID_UUID = '550e8400-e29b-41d4-a716-446655440000';
const VALID_UUID_2 = '6ba7b810-9dad-11d1-80b4-00c04fd430c8';
const VALID_UUID_3 = '6ba7b811-9dad-11d1-80b4-00c04fd430c8';
const VALID_UUID_4 = '6ba7b812-9dad-11d1-80b4-00c04fd430c8';

function makeAttr(key: SchemaType, overrides: Partial<SchemaUUID> = {}): SchemaUUID {
  const attrType = attributeTypes[key];
  return {
    key,
    label: 'Test Field',
    type: attrType.type,
    icon: { type: IconType.Circle, colour: IconColour.Neutral },
    required: false,
    unique: false,
    _protected: false,
    ...overrides,
  };
}

function makeRel(
  id: string,
  cardinality: EntityRelationshipCardinality,
): RelationshipDefinition {
  return {
    id,
    workspaceId: VALID_UUID,
    sourceEntityTypeId: VALID_UUID_2,
    name: 'Test Relationship',
    icon: { type: IconType.Link, colour: IconColour.Blue },
    cardinalityDefault: cardinality,
    _protected: false,
    isPolymorphic: false,
    targetRules: [
      {
        id: `${id}-rule`,
        relationshipDefinitionId: id,
        targetEntityTypeId: VALID_UUID_3,
        inverseName: 'Inverse',
      },
    ],
  };
}

function makeValidEntityRef() {
  return {
    id: VALID_UUID,
    workspaceId: VALID_UUID_2,
    sourceEntityId: VALID_UUID_3,
    definitionId: VALID_UUID_4,
    label: 'Test Entity',
    key: 'test_entity',
    icon: { type: IconType.Circle, colour: IconColour.Neutral },
  };
}

// ---------------------------------------------------------------------------
// buildZodSchemaFromEntityType
// ---------------------------------------------------------------------------

describe('buildZodSchemaFromEntityType', () => {
  it('returns a z.object schema', () => {
    const schema = buildZodSchemaFromEntityType(simpleContactEntityType);
    expect(schema).toBeDefined();
    expect(typeof schema.parse).toBe('function');
    expect(typeof schema.safeParse).toBe('function');
  });

  it('accepts valid data matching simpleContactEntityType', () => {
    const schema = buildZodSchemaFromEntityType(simpleContactEntityType);
    const result = schema.safeParse({ name: 'Alice', age: 30, active: true });
    expect(result.success).toBe(true);
  });

  it('rejects missing required name field in simpleContactEntityType', () => {
    const schema = buildZodSchemaFromEntityType(simpleContactEntityType);
    const result = schema.safeParse({ name: '', age: 30, active: true });
    expect(result.success).toBe(false);
    if (!result.success) {
      const paths = result.error.issues.map((i) => i.path);
      expect(paths).toContainEqual(['name']);
    }
  });

  it('includes relationship fields from relationshipHeavyEntityType', () => {
    const schema = buildZodSchemaFromEntityType(relationshipHeavyEntityType);
    const keys = Object.keys(schema.shape);
    expect(keys).toContain('rel-deal-company');
    expect(keys).toContain('rel-deal-projects');
    expect(keys).toContain('rel-deal-owner');
  });

  it('accepts valid data with relationships', () => {
    const schema = buildZodSchemaFromEntityType(relationshipHeavyEntityType);
    const result = schema.safeParse({
      title: 'My Deal',
      value: 5000,
      'rel-deal-company': [makeValidEntityRef()],
      'rel-deal-projects': [makeValidEntityRef()],
      'rel-deal-owner': [makeValidEntityRef()],
    });
    expect(result.success).toBe(true);
  });

  it('validates all attributes in complexAllSchemaTypesEntityType', () => {
    const schema = buildZodSchemaFromEntityType(complexAllSchemaTypesEntityType);
    const keys = Object.keys(schema.shape);
    expect(keys).toContain('title');
    expect(keys).toContain('count');
    expect(keys).toContain('active');
    expect(keys).toContain('due_date');
    expect(keys).toContain('created_at');
    expect(keys).toContain('email');
    expect(keys).toContain('website');
    expect(keys).toContain('status');
    expect(keys).toContain('tags');
    expect(keys).toContain('attachments');
  });

  it('includes Id attribute in schema for entityTypeWithId', () => {
    const schema = buildZodSchemaFromEntityType(entityTypeWithId);
    const keys = Object.keys(schema.shape);
    expect(keys).toContain('record_id');
    expect(keys).toContain('title');
    expect(keys).toContain('status');
  });

  it('accepts valid complex entity instance', () => {
    const schema = buildZodSchemaFromEntityType(complexAllSchemaTypesEntityType);
    const result = schema.safeParse({
      title: null,
      count: null,
      active: false,
      due_date: null,
      created_at: null,
      rating: null,
      phone: null,
      email: null,
      website: null,
      price: null,
      completion: null,
      status: null,
      tags: null,
      attachments: null,
    });
    expect(result.success).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// buildFieldSchema — text fields
// ---------------------------------------------------------------------------

describe('buildFieldSchema', () => {
  describe('text fields', () => {
    it('accepts any string when optional', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Text));
      expect(schema.safeParse('hello').success).toBe(true);
    });

    it('accepts null when optional', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Text));
      expect(schema.safeParse(null).success).toBe(true);
    });

    it('accepts undefined when optional', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Text));
      expect(schema.safeParse(undefined).success).toBe(true);
    });

    it('rejects empty string when required and no minLength', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Text, { required: true }));
      const result = schema.safeParse('');
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues).toContainEqual(
          expect.objectContaining({ code: 'too_small', type: 'string', minimum: 1 }),
        );
      }
    });

    it('accepts non-empty string when required', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Text, { required: true }));
      expect(schema.safeParse('hello').success).toBe(true);
    });

    it('enforces minLength constraint', () => {
      const schema = buildFieldSchema(
        makeAttr(SchemaType.Text, { options: { minLength: 5, maxLength: undefined } }),
      );
      const result = schema.safeParse('hi');
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues).toContainEqual(
          expect.objectContaining({ code: 'too_small', type: 'string', minimum: 5 }),
        );
      }
      expect(schema.safeParse('hello').success).toBe(true);
    });

    it('enforces maxLength constraint', () => {
      const schema = buildFieldSchema(
        makeAttr(SchemaType.Text, { options: { maxLength: 5 } }),
      );
      const result = schema.safeParse('toolong');
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues).toContainEqual(
          expect.objectContaining({ code: 'too_big', type: 'string', maximum: 5 }),
        );
      }
      expect(schema.safeParse('ok').success).toBe(true);
    });

    it('enforces regex constraint', () => {
      const schema = buildFieldSchema(
        makeAttr(SchemaType.Text, {
          required: true,
          options: { regex: '^[A-Z]{2,4}-\\d{4}$', minLength: 5, maxLength: 10 },
        }),
      );
      expect(schema.safeParse('AB-1234').success).toBe(true);
      const result = schema.safeParse('invalid');
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues).toContainEqual(
          expect.objectContaining({ code: 'invalid_string', validation: 'regex' }),
        );
      }
    });

    it('does not apply min(1) when minLength is explicitly set', () => {
      // If required + minLength is set, min(1) should NOT be applied separately,
      // only the explicit minLength should apply.
      const schema = buildFieldSchema(
        makeAttr(SchemaType.Text, { required: true, options: { minLength: 3 } }),
      );
      // Empty string fails because minLength=3
      const emptyResult = schema.safeParse('');
      expect(emptyResult.success).toBe(false);
      if (!emptyResult.success) {
        expect(emptyResult.error.issues).toContainEqual(
          expect.objectContaining({ code: 'too_small', type: 'string', minimum: 3 }),
        );
      }
      // 1-2 chars fail due to minLength
      const shortResult = schema.safeParse('ab');
      expect(shortResult.success).toBe(false);
      if (!shortResult.success) {
        expect(shortResult.error.issues).toContainEqual(
          expect.objectContaining({ code: 'too_small', type: 'string', minimum: 3 }),
        );
      }
      // 3+ chars pass
      expect(schema.safeParse('abc').success).toBe(true);
    });
  });

  // ---------------------------------------------------------------------------
  // email fields
  // ---------------------------------------------------------------------------

  describe('email fields', () => {
    it('accepts valid email', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Email));
      expect(schema.safeParse('user@example.com').success).toBe(true);
    });

    it('rejects invalid email', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Email));
      const result = schema.safeParse('not-an-email');
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues).toContainEqual(
          expect.objectContaining({ code: 'invalid_string', validation: 'email' }),
        );
      }
    });

    it('accepts null when optional', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Email));
      expect(schema.safeParse(null).success).toBe(true);
    });

    it('rejects empty string when required', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Email, { required: true }));
      const result = schema.safeParse('');
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues).toContainEqual(
          expect.objectContaining({ code: 'too_small' }),
        );
      }
    });
  });

  // ---------------------------------------------------------------------------
  // URL fields
  // ---------------------------------------------------------------------------

  describe('URL fields', () => {
    it('accepts valid URL', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Url));
      expect(schema.safeParse('https://example.com').success).toBe(true);
    });

    it('rejects invalid URL', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Url));
      const result = schema.safeParse('not-a-url');
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues).toContainEqual(
          expect.objectContaining({ code: 'invalid_string', validation: 'url' }),
        );
      }
    });

    it('accepts null when optional', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Url));
      expect(schema.safeParse(null).success).toBe(true);
    });
  });

  // ---------------------------------------------------------------------------
  // date fields
  // ---------------------------------------------------------------------------

  describe('date fields', () => {
    it('accepts valid YYYY-MM-DD date string', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Date));
      expect(schema.safeParse('2024-03-15').success).toBe(true);
    });

    it('rejects non-date strings', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Date));
      expect(schema.safeParse('March 15, 2024').success).toBe(false);
    });

    it('rejects ISO datetime strings', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Date));
      expect(schema.safeParse('2024-03-15T10:00:00Z').success).toBe(false);
    });

    it('accepts null when optional', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Date));
      expect(schema.safeParse(null).success).toBe(true);
    });
  });

  // ---------------------------------------------------------------------------
  // datetime fields
  // ---------------------------------------------------------------------------

  describe('datetime fields', () => {
    it('accepts valid ISO datetime string', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Datetime));
      expect(schema.safeParse('2024-03-15T10:00:00Z').success).toBe(true);
    });

    it('rejects plain date strings', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Datetime));
      expect(schema.safeParse('2024-03-15').success).toBe(false);
    });

    it('accepts null when optional', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Datetime));
      expect(schema.safeParse(null).success).toBe(true);
    });
  });

  // ---------------------------------------------------------------------------
  // phone fields
  // ---------------------------------------------------------------------------

  describe('phone fields', () => {
    it('accepts valid phone number', () => {
      // The phone regex allows 3 groups: country code + area + number
      // Pattern: ^[+]?[(]?[0-9]{1,4}[)]?[-\s.]?[(]?[0-9]{1,4}[)]?[-\s.]?[0-9]{1,9}$
      const schema = buildFieldSchema(makeAttr(SchemaType.Phone));
      expect(schema.safeParse('+1.555.1234567').success).toBe(true);
    });

    it('accepts phone without country code', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Phone));
      expect(schema.safeParse('5551234567').success).toBe(true);
    });

    it('rejects obviously invalid phone', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Phone));
      expect(schema.safeParse('not-a-phone').success).toBe(false);
    });

    it('accepts null when optional', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Phone));
      expect(schema.safeParse(null).success).toBe(true);
    });
  });

  // ---------------------------------------------------------------------------
  // number fields
  // ---------------------------------------------------------------------------

  describe('number fields', () => {
    it('accepts any number when optional', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Number));
      expect(schema.safeParse(42).success).toBe(true);
    });

    it('accepts null when optional', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Number));
      expect(schema.safeParse(null).success).toBe(true);
    });

    it('rejects non-number values', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Number));
      expect(schema.safeParse('42').success).toBe(false);
    });

    it('enforces minimum constraint', () => {
      const schema = buildFieldSchema(
        makeAttr(SchemaType.Number, { options: { minimum: 0, maximum: undefined } }),
      );
      expect(schema.safeParse(-1).success).toBe(false);
      expect(schema.safeParse(0).success).toBe(true);
    });

    it('enforces maximum constraint', () => {
      const schema = buildFieldSchema(
        makeAttr(SchemaType.Number, { options: { maximum: 100 } }),
      );
      expect(schema.safeParse(101).success).toBe(false);
      expect(schema.safeParse(100).success).toBe(true);
    });

    it('enforces both min and max constraints', () => {
      const schema = buildFieldSchema(
        makeAttr(SchemaType.Number, {
          required: true,
          options: { minimum: 0, maximum: 100 },
        }),
      );
      expect(schema.safeParse(-1).success).toBe(false);
      expect(schema.safeParse(101).success).toBe(false);
      expect(schema.safeParse(50).success).toBe(true);
    });

    it('enforces inherent Rating min=1 max=5 constraints', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Rating));
      expect(schema.safeParse(0).success).toBe(false);
      expect(schema.safeParse(6).success).toBe(false);
      expect(schema.safeParse(3).success).toBe(true);
    });
  });

  // ---------------------------------------------------------------------------
  // boolean fields
  // ---------------------------------------------------------------------------

  describe('boolean fields', () => {
    it('accepts true', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Checkbox));
      expect(schema.safeParse(true).success).toBe(true);
    });

    it('accepts false', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Checkbox));
      expect(schema.safeParse(false).success).toBe(true);
    });

    it('accepts null when optional', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Checkbox));
      expect(schema.safeParse(null).success).toBe(true);
    });

    it('rejects string values', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Checkbox));
      expect(schema.safeParse('true').success).toBe(false);
    });
  });

  // ---------------------------------------------------------------------------
  // select fields
  // ---------------------------------------------------------------------------

  describe('select fields', () => {
    it('accepts value in enum', () => {
      const schema = buildFieldSchema(
        makeAttr(SchemaType.Select, {
          options: { _enum: ['todo', 'in_progress', 'done'] },
        }),
      );
      expect(schema.safeParse('todo').success).toBe(true);
    });

    it('rejects value not in enum', () => {
      const schema = buildFieldSchema(
        makeAttr(SchemaType.Select, {
          options: { _enum: ['todo', 'in_progress', 'done'] },
        }),
      );
      const result = schema.safeParse('invalid');
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues).toContainEqual(
          expect.objectContaining({ code: 'invalid_enum_value' }),
        );
      }
    });

    it('accepts null when optional (no enum)', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Select));
      expect(schema.safeParse(null).success).toBe(true);
    });

    it('accepts null when optional with enum', () => {
      const schema = buildFieldSchema(
        makeAttr(SchemaType.Select, {
          options: { _enum: ['a', 'b', 'c'] },
        }),
      );
      expect(schema.safeParse(null).success).toBe(true);
    });
  });

  // ---------------------------------------------------------------------------
  // multi-select fields
  // ---------------------------------------------------------------------------

  describe('multi-select fields', () => {
    it('accepts empty array', () => {
      const schema = buildFieldSchema(
        makeAttr(SchemaType.MultiSelect, {
          options: { _enum: ['bug', 'feature'] },
        }),
      );
      expect(schema.safeParse([]).success).toBe(true);
    });

    it('accepts array of valid enum values', () => {
      const schema = buildFieldSchema(
        makeAttr(SchemaType.MultiSelect, {
          options: { _enum: ['bug', 'feature', 'improvement'] },
        }),
      );
      expect(schema.safeParse(['bug', 'feature']).success).toBe(true);
    });

    it('accepts array containing custom values not in enum', () => {
      const schema = buildFieldSchema(
        makeAttr(SchemaType.MultiSelect, {
          options: { _enum: ['bug', 'feature'] },
        }),
      );
      const result = schema.safeParse(['bug', 'custom-tag']);
      expect(result.success).toBe(true);
    });

    it('accepts null when optional', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.MultiSelect));
      expect(schema.safeParse(null).success).toBe(true);
    });

    it('accepts array of strings when no enum defined', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.MultiSelect));
      expect(schema.safeParse(['any', 'string']).success).toBe(true);
    });
  });

  // ---------------------------------------------------------------------------
  // file attachment fields
  // ---------------------------------------------------------------------------

  describe('file attachment fields', () => {
    it('accepts empty array', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.FileAttachment));
      expect(schema.safeParse([]).success).toBe(true);
    });

    it('accepts array of valid URLs', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.FileAttachment));
      expect(
        schema.safeParse(['https://example.com/file.pdf', 'https://cdn.io/img.png']).success,
      ).toBe(true);
    });

    it('rejects array with non-URL strings', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.FileAttachment));
      const result = schema.safeParse(['not-a-url']);
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues).toContainEqual(
          expect.objectContaining({ code: 'invalid_string', validation: 'url' }),
        );
      }
    });

    it('accepts null when optional', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.FileAttachment));
      expect(schema.safeParse(null).success).toBe(true);
    });
  });

  // ---------------------------------------------------------------------------
  // Id fields
  // ---------------------------------------------------------------------------

  describe('Id fields', () => {
    it('accepts any string value (backend-generated)', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Id, { required: true, _protected: true }));
      expect(schema.safeParse('TKT-1').success).toBe(true);
      expect(schema.safeParse('ABC-99999').success).toBe(true);
    });

    it('accepts empty string as draft default value', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Id, { required: true, _protected: true }));
      expect(schema.safeParse('').success).toBe(true);
    });

    it('accepts null when optional', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Id));
      expect(schema.safeParse(null).success).toBe(true);
    });
  });

  // ---------------------------------------------------------------------------
  // inherent options merging
  // ---------------------------------------------------------------------------

  describe('inherent options merging', () => {
    it('applies inherent Rating min/max when no per-instance options exist', () => {
      const schema = buildFieldSchema(makeAttr(SchemaType.Rating));
      // Rating has inherent { minimum: 1, maximum: 5 } from attributeTypes
      expect(schema.safeParse(0).success).toBe(false);
      expect(schema.safeParse(6).success).toBe(false);
      expect(schema.safeParse(1).success).toBe(true);
      expect(schema.safeParse(5).success).toBe(true);
      expect(schema.safeParse(3).success).toBe(true);
    });

    it('allows per-instance options to override inherent options', () => {
      const schema = buildFieldSchema(
        makeAttr(SchemaType.Rating, { options: { minimum: 0, maximum: 10 } }),
      );
      // Per-instance overrides inherent { minimum: 1, maximum: 5 }
      expect(schema.safeParse(0).success).toBe(true);
      expect(schema.safeParse(10).success).toBe(true);
      expect(schema.safeParse(11).success).toBe(false);
    });

    it('partial per-instance override merges with inherent options', () => {
      const schema = buildFieldSchema(
        makeAttr(SchemaType.Rating, { options: { minimum: 0 } }),
      );
      // minimum overridden to 0, maximum inherits 5 from inherent
      expect(schema.safeParse(0).success).toBe(true);
      expect(schema.safeParse(5).success).toBe(true);
      expect(schema.safeParse(6).success).toBe(false);
    });

    it('does not interfere when attributeType has no inherent options', () => {
      // Text has no inherent options — per-instance options should work unchanged
      const schema = buildFieldSchema(
        makeAttr(SchemaType.Text, { options: { minLength: 3 } }),
      );
      expect(schema.safeParse('ab').success).toBe(false);
      expect(schema.safeParse('abc').success).toBe(true);
    });
  });
});

// ---------------------------------------------------------------------------
// buildRelationshipFieldSchema
// ---------------------------------------------------------------------------

describe('buildRelationshipFieldSchema', () => {
  it('accepts empty array for any cardinality', () => {
    const schema = buildRelationshipFieldSchema(
      makeRel('rel-1', EntityRelationshipCardinality.ManyToMany),
    );
    expect(schema.safeParse([]).success).toBe(true);
  });

  it('accepts valid entity reference array', () => {
    const schema = buildRelationshipFieldSchema(
      makeRel('rel-1', EntityRelationshipCardinality.ManyToMany),
    );
    expect(schema.safeParse([makeValidEntityRef()]).success).toBe(true);
  });

  it('accepts multiple references for ManyToMany', () => {
    const schema = buildRelationshipFieldSchema(
      makeRel('rel-1', EntityRelationshipCardinality.ManyToMany),
    );
    expect(schema.safeParse([makeValidEntityRef(), makeValidEntityRef()]).success).toBe(true);
  });

  it('enforces max(1) for ManyToOne', () => {
    const schema = buildRelationshipFieldSchema(
      makeRel('rel-1', EntityRelationshipCardinality.ManyToOne),
    );
    const result = schema.safeParse([makeValidEntityRef(), makeValidEntityRef()]);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues).toContainEqual(
        expect.objectContaining({ code: 'too_big', type: 'array', maximum: 1 }),
      );
    }
    expect(schema.safeParse([makeValidEntityRef()]).success).toBe(true);
  });

  it('enforces max(1) for OneToOne', () => {
    const schema = buildRelationshipFieldSchema(
      makeRel('rel-1', EntityRelationshipCardinality.OneToOne),
    );
    const result = schema.safeParse([makeValidEntityRef(), makeValidEntityRef()]);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues).toContainEqual(
        expect.objectContaining({ code: 'too_big', type: 'array', maximum: 1 }),
      );
    }
    expect(schema.safeParse([makeValidEntityRef()]).success).toBe(true);
  });

  it('does NOT enforce max(1) for OneToMany', () => {
    const schema = buildRelationshipFieldSchema(
      makeRel('rel-1', EntityRelationshipCardinality.OneToMany),
    );
    expect(schema.safeParse([makeValidEntityRef(), makeValidEntityRef()]).success).toBe(true);
  });

  it('rejects entity reference with invalid UUID for id field', () => {
    const schema = buildRelationshipFieldSchema(
      makeRel('rel-1', EntityRelationshipCardinality.ManyToMany),
    );
    const badRef = { ...makeValidEntityRef(), id: 'not-a-uuid' };
    const result = schema.safeParse([badRef]);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues).toContainEqual(
        expect.objectContaining({ code: 'custom', message: 'Invalid UUID', path: [0, 'id'] }),
      );
    }
  });

  it('rejects entity reference with empty label', () => {
    const schema = buildRelationshipFieldSchema(
      makeRel('rel-1', EntityRelationshipCardinality.ManyToMany),
    );
    const badRef = { ...makeValidEntityRef(), label: '' };
    const result = schema.safeParse([badRef]);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues).toContainEqual(
        expect.objectContaining({ code: 'too_small', type: 'string', minimum: 1 }),
      );
    }
  });
});

// ---------------------------------------------------------------------------
// getDefaultValueForSchema
// ---------------------------------------------------------------------------

describe('getDefaultValueForSchema', () => {
  it('returns empty string for Text fields', () => {
    expect(getDefaultValueForSchema(makeAttr(SchemaType.Text))).toBe('');
  });

  it('returns empty string for Id fields', () => {
    expect(getDefaultValueForSchema(makeAttr(SchemaType.Id))).toBe('');
  });

  it('returns empty string for Email fields', () => {
    expect(getDefaultValueForSchema(makeAttr(SchemaType.Email))).toBe('');
  });

  it('returns empty string for Url fields', () => {
    expect(getDefaultValueForSchema(makeAttr(SchemaType.Url))).toBe('');
  });

  it('returns empty string for Phone fields', () => {
    expect(getDefaultValueForSchema(makeAttr(SchemaType.Phone))).toBe('');
  });

  it('returns empty string for Date fields', () => {
    expect(getDefaultValueForSchema(makeAttr(SchemaType.Date))).toBe('');
  });

  it('returns empty string for Datetime fields', () => {
    expect(getDefaultValueForSchema(makeAttr(SchemaType.Datetime))).toBe('');
  });

  it('returns empty string for Select fields', () => {
    expect(getDefaultValueForSchema(makeAttr(SchemaType.Select))).toBe('');
  });

  it('returns 0 for Number fields with no minimum', () => {
    expect(getDefaultValueForSchema(makeAttr(SchemaType.Number))).toBe(0);
  });

  it('returns minimum value for Number fields with minimum', () => {
    expect(
      getDefaultValueForSchema(makeAttr(SchemaType.Number, { options: { minimum: 5 } })),
    ).toBe(5);
  });

  it('returns 0 for Currency fields with no minimum', () => {
    expect(getDefaultValueForSchema(makeAttr(SchemaType.Currency))).toBe(0);
  });

  it('returns 0 for Percentage fields with no minimum', () => {
    expect(getDefaultValueForSchema(makeAttr(SchemaType.Percentage))).toBe(0);
  });

  it('returns false for Checkbox fields', () => {
    expect(getDefaultValueForSchema(makeAttr(SchemaType.Checkbox))).toBe(false);
  });

  it('returns empty array for MultiSelect fields', () => {
    expect(getDefaultValueForSchema(makeAttr(SchemaType.MultiSelect))).toEqual([]);
  });

  it('returns empty array for FileAttachment fields', () => {
    expect(getDefaultValueForSchema(makeAttr(SchemaType.FileAttachment))).toEqual([]);
  });

  it('returns empty object for Object fields', () => {
    expect(getDefaultValueForSchema(makeAttr(SchemaType.Object))).toEqual({});
  });

  it('returns custom _default when set', () => {
    expect(
      getDefaultValueForSchema(makeAttr(SchemaType.Number, { options: { _default: 50 } })),
    ).toBe(50);
  });

  it('returns custom string _default when set', () => {
    expect(
      getDefaultValueForSchema(
        makeAttr(SchemaType.Select, { options: { _default: 'medium' } }),
      ),
    ).toBe('medium');
  });

  it('prefers _default over minimum for numbers', () => {
    expect(
      getDefaultValueForSchema(
        makeAttr(SchemaType.Number, { options: { _default: 50, minimum: 0 } }),
      ),
    ).toBe(50);
  });

  it('returns edgeCaseEntityType score default of 50', () => {
    const scoreSchema = edgeCaseEntityType.schema.properties!['score'];
    expect(getDefaultValueForSchema(scoreSchema)).toBe(50);
  });

  it('returns edgeCaseEntityType priority default of medium', () => {
    const prioritySchema = edgeCaseEntityType.schema.properties!['priority'];
    expect(getDefaultValueForSchema(prioritySchema)).toBe('medium');
  });
});

// ---------------------------------------------------------------------------
// buildDefaultValuesFromEntityType
// ---------------------------------------------------------------------------

describe('buildDefaultValuesFromEntityType', () => {
  it('returns defaults for all attributes in simpleContactEntityType', () => {
    const defaults = buildDefaultValuesFromEntityType(simpleContactEntityType);
    expect(defaults).toHaveProperty('name', '');
    expect(defaults).toHaveProperty('age', 0);
    expect(defaults).toHaveProperty('active', false);
  });

  it('returns empty arrays for all relationships', () => {
    const defaults = buildDefaultValuesFromEntityType(relationshipHeavyEntityType);
    expect(defaults['rel-deal-company']).toEqual([]);
    expect(defaults['rel-deal-projects']).toEqual([]);
    expect(defaults['rel-deal-owner']).toEqual([]);
  });

  it('includes both attribute and relationship keys', () => {
    const defaults = buildDefaultValuesFromEntityType(relationshipHeavyEntityType);
    expect(defaults).toHaveProperty('title');
    expect(defaults).toHaveProperty('value');
    expect(defaults).toHaveProperty('rel-deal-company');
  });

  it('uses _default value when set in options', () => {
    const defaults = buildDefaultValuesFromEntityType(edgeCaseEntityType);
    expect(defaults['score']).toBe(50);
    expect(defaults['priority']).toBe('medium');
  });

  it('returns empty string for text fields with no default', () => {
    const defaults = buildDefaultValuesFromEntityType(edgeCaseEntityType);
    expect(defaults['code']).toBe('');
    expect(defaults['description']).toBe('');
    expect(defaults['notes']).toBe('');
  });

  it('returns correct keys count for complexAllSchemaTypesEntityType', () => {
    const defaults = buildDefaultValuesFromEntityType(complexAllSchemaTypesEntityType);
    const schemaPropertyCount = Object.keys(
      complexAllSchemaTypesEntityType.schema.properties!,
    ).length;
    expect(Object.keys(defaults).length).toBe(schemaPropertyCount);
  });

  it('returns empty arrays for array-type fields (MultiSelect, FileAttachment)', () => {
    const defaults = buildDefaultValuesFromEntityType(complexAllSchemaTypesEntityType);
    expect(defaults['tags']).toEqual([]);
    expect(defaults['attachments']).toEqual([]);
  });

  it('returns false for checkbox fields', () => {
    const defaults = buildDefaultValuesFromEntityType(complexAllSchemaTypesEntityType);
    expect(defaults['active']).toBe(false);
  });

  it('returns empty string for select fields with no default', () => {
    const defaults = buildDefaultValuesFromEntityType(complexAllSchemaTypesEntityType);
    expect(defaults['status']).toBe('');
  });

  it('returns empty string default for Id fields', () => {
    const defaults = buildDefaultValuesFromEntityType(entityTypeWithId);
    expect(defaults['record_id']).toBe('');
  });
});
