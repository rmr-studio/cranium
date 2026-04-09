import { DefinitionCategory } from '@/lib/types/workspace';
import { definitionSchema } from '@/components/feature-modules/knowledge/hooks/form/use-definition-form';

describe('definitionSchema', () => {
  const validData = {
    term: 'MRR',
    definition: 'Monthly recurring revenue from active subscriptions',
    category: DefinitionCategory.Metric,
    entityTypeRefs: [],
    attributeRefs: [],
  };

  it('accepts valid definition data', () => {
    const result = definitionSchema.safeParse(validData);
    expect(result.success).toBe(true);
  });

  it('rejects empty term', () => {
    const result = definitionSchema.safeParse({ ...validData, term: '' });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('term'))).toBe(true);
  });

  it('rejects term exceeding 255 characters', () => {
    const result = definitionSchema.safeParse({ ...validData, term: 'x'.repeat(256) });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('term'))).toBe(true);
  });

  it('rejects empty definition', () => {
    const result = definitionSchema.safeParse({ ...validData, definition: '' });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('definition'))).toBe(true);
  });

  it('rejects definition exceeding 2000 characters', () => {
    const result = definitionSchema.safeParse({ ...validData, definition: 'x'.repeat(2001) });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('definition'))).toBe(true);
  });

  it('rejects missing category', () => {
    const result = definitionSchema.safeParse({
      term: validData.term,
      definition: validData.definition,
    });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('category'))).toBe(true);
  });

  it('rejects invalid category value', () => {
    const result = definitionSchema.safeParse({ ...validData, category: 'INVALID' });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((i) => i.path.includes('category'))).toBe(true);
  });

  it('accepts all valid DefinitionCategory values', () => {
    const categories = [
      DefinitionCategory.Metric,
      DefinitionCategory.Segment,
      DefinitionCategory.Status,
      DefinitionCategory.LifecycleStage,
      DefinitionCategory.Custom,
    ];
    for (const category of categories) {
      const result = definitionSchema.safeParse({ ...validData, category });
      expect(result.success).toBe(true);
    }
  });

  it('defaults entityTypeRefs to empty array when missing', () => {
    const result = definitionSchema.safeParse({
      term: validData.term,
      definition: validData.definition,
      category: validData.category,
      attributeRefs: [],
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.entityTypeRefs).toEqual([]);
    }
  });

  it('defaults attributeRefs to empty array when missing', () => {
    const result = definitionSchema.safeParse({
      term: validData.term,
      definition: validData.definition,
      category: validData.category,
      entityTypeRefs: [],
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.attributeRefs).toEqual([]);
    }
  });

  it('accepts valid UUIDs in entityTypeRefs', () => {
    const result = definitionSchema.safeParse({
      ...validData,
      entityTypeRefs: ['550e8400-e29b-41d4-a716-446655440000'],
    });
    expect(result.success).toBe(true);
  });

  it('rejects non-UUID strings in entityTypeRefs', () => {
    const result = definitionSchema.safeParse({
      ...validData,
      entityTypeRefs: ['not-a-uuid'],
    });
    expect(result.success).toBe(false);
  });

  it('accepts term at exactly 255 characters', () => {
    const result = definitionSchema.safeParse({ ...validData, term: 'x'.repeat(255) });
    expect(result.success).toBe(true);
  });

  it('accepts definition at exactly 2000 characters', () => {
    const result = definitionSchema.safeParse({ ...validData, definition: 'x'.repeat(2000) });
    expect(result.success).toBe(true);
  });
});
