import { EntityRelationshipCardinality, RelationshipLimit } from '@/lib/types/entity';
import {
  calculateCardinalityFromLimits,
  processCardinalityToLimits,
} from '@/components/feature-modules/entity/util/relationship.util';

describe('processCardinalityToLimits', () => {
  it('maps OneToOne to SINGULAR source and SINGULAR target', () => {
    const result = processCardinalityToLimits(EntityRelationshipCardinality.OneToOne);
    expect(result).toEqual({ source: RelationshipLimit.SINGULAR, target: RelationshipLimit.SINGULAR });
  });

  it('maps OneToMany to MANY source and SINGULAR target', () => {
    const result = processCardinalityToLimits(EntityRelationshipCardinality.OneToMany);
    expect(result).toEqual({ source: RelationshipLimit.MANY, target: RelationshipLimit.SINGULAR });
  });

  it('maps ManyToOne to SINGULAR source and MANY target', () => {
    const result = processCardinalityToLimits(EntityRelationshipCardinality.ManyToOne);
    expect(result).toEqual({ source: RelationshipLimit.SINGULAR, target: RelationshipLimit.MANY });
  });

  it('maps ManyToMany to MANY source and MANY target', () => {
    const result = processCardinalityToLimits(EntityRelationshipCardinality.ManyToMany);
    expect(result).toEqual({ source: RelationshipLimit.MANY, target: RelationshipLimit.MANY });
  });

  it('falls back to SINGULAR/SINGULAR for unknown cardinality', () => {
    const result = processCardinalityToLimits('UNKNOWN' as EntityRelationshipCardinality);
    expect(result).toEqual({ source: RelationshipLimit.SINGULAR, target: RelationshipLimit.SINGULAR });
  });
});

describe('calculateCardinalityFromLimits', () => {
  it('maps SINGULAR source and SINGULAR target to OneToOne', () => {
    const result = calculateCardinalityFromLimits(RelationshipLimit.SINGULAR, RelationshipLimit.SINGULAR);
    expect(result).toBe(EntityRelationshipCardinality.OneToOne);
  });

  it('maps MANY source and SINGULAR target to OneToMany', () => {
    const result = calculateCardinalityFromLimits(RelationshipLimit.MANY, RelationshipLimit.SINGULAR);
    expect(result).toBe(EntityRelationshipCardinality.OneToMany);
  });

  it('maps SINGULAR source and MANY target to ManyToOne', () => {
    const result = calculateCardinalityFromLimits(RelationshipLimit.SINGULAR, RelationshipLimit.MANY);
    expect(result).toBe(EntityRelationshipCardinality.ManyToOne);
  });

  it('maps MANY source and MANY target to ManyToMany', () => {
    const result = calculateCardinalityFromLimits(RelationshipLimit.MANY, RelationshipLimit.MANY);
    expect(result).toBe(EntityRelationshipCardinality.ManyToMany);
  });
});

describe('round-trip consistency', () => {
  it('round-trips OneToOne correctly', () => {
    const { source, target } = processCardinalityToLimits(EntityRelationshipCardinality.OneToOne);
    expect(calculateCardinalityFromLimits(source, target)).toBe(EntityRelationshipCardinality.OneToOne);
  });

  it('round-trips OneToMany correctly', () => {
    const { source, target } = processCardinalityToLimits(EntityRelationshipCardinality.OneToMany);
    expect(calculateCardinalityFromLimits(source, target)).toBe(EntityRelationshipCardinality.OneToMany);
  });

  it('round-trips ManyToOne correctly', () => {
    const { source, target } = processCardinalityToLimits(EntityRelationshipCardinality.ManyToOne);
    expect(calculateCardinalityFromLimits(source, target)).toBe(EntityRelationshipCardinality.ManyToOne);
  });

  it('round-trips ManyToMany correctly', () => {
    const { source, target } = processCardinalityToLimits(EntityRelationshipCardinality.ManyToMany);
    expect(calculateCardinalityFromLimits(source, target)).toBe(EntityRelationshipCardinality.ManyToMany);
  });
});
