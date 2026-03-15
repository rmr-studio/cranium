import { Entity, EntityRelationshipCardinality, RelationshipDefinition } from '@/lib/types/entity';
import { EntityPropertyType } from '@/lib/types/entity';
import { getConstrainedEntities } from '@/components/feature-modules/entity/util/relationship-constraint.util';

const makeRelationship = (
  cardinality: EntityRelationshipCardinality,
  overrides?: Partial<RelationshipDefinition>,
): RelationshipDefinition => ({
  id: 'rel-1',
  workspaceId: 'ws-1',
  sourceEntityTypeId: 'type-company',
  name: 'Employees',
  icon: { type: 'FILE' as const, colour: 'NEUTRAL' as const },
  cardinalityDefault: cardinality,
  _protected: false,
  targetRules: [],
  isPolymorphic: false,
  ...overrides,
});

const makeEntity = (
  id: string,
  relationshipId: string,
  links: Array<{ id: string; sourceEntityId: string; label: string }>,
): Entity =>
  ({
    id,
    workspaceId: 'ws-1',
    typeId: 'type-employee',
    identifierKey: 'name',
    icon: { type: 'FILE', colour: 'NEUTRAL' },
    sourceType: 'MANUAL',
    syncVersion: 0,
    identifier: id,
    payload: {
      [relationshipId]: {
        payload: {
          type: EntityPropertyType.Relationship,
          relations: links.map((l) => ({
            ...l,
            workspaceId: 'ws-1',
            definitionId: relationshipId,
            key: 'company',
            icon: { type: 'FILE', colour: 'NEUTRAL' },
          })),
        },
      },
      name: {
        payload: {
          type: EntityPropertyType.Attribute,
          value: `Entity ${id}`,
        },
      },
    },
  }) as unknown as Entity;

describe('getConstrainedEntities', () => {
  it('returns empty map for MANY_TO_MANY (no singular constraint)', () => {
    const relationship = makeRelationship(EntityRelationshipCardinality.ManyToMany);
    const entities = [
      makeEntity('emp-1', 'rel-1', [
        { id: 'comp-1', sourceEntityId: 'emp-1', label: 'Company A' },
      ]),
    ];

    const result = getConstrainedEntities(entities, relationship, false);
    expect(result.size).toBe(0);
  });

  it('returns empty map when entities have no existing links', () => {
    const relationship = makeRelationship(EntityRelationshipCardinality.OneToMany);
    const entities = [makeEntity('emp-1', 'rel-1', [])];

    const result = getConstrainedEntities(entities, relationship, false);
    expect(result.size).toBe(0);
  });

  it('returns constrained entry for ONE_TO_MANY when target already linked to different source', () => {
    const relationship = makeRelationship(EntityRelationshipCardinality.OneToMany);
    const entities = [
      makeEntity('emp-1', 'rel-1', [
        { id: 'comp-1', sourceEntityId: 'emp-1', label: 'Company A' },
      ]),
    ];

    // Editing Company B (comp-2), emp-1 is already linked to comp-1
    const result = getConstrainedEntities(entities, relationship, false, 'comp-2');
    expect(result.size).toBe(1);
    expect(result.get('emp-1')).toEqual({
      reason: 'Already assigned to Company A',
      linkedLabel: 'Company A',
    });
  });

  it('does NOT constrain when linked to the current source entity', () => {
    const relationship = makeRelationship(EntityRelationshipCardinality.OneToMany);
    const entities = [
      makeEntity('emp-1', 'rel-1', [
        { id: 'comp-1', sourceEntityId: 'emp-1', label: 'Company A' },
      ]),
    ];

    // Editing Company A (comp-1) — emp-1 is already linked to comp-1, so it's not constrained
    const result = getConstrainedEntities(entities, relationship, false, 'comp-1');
    expect(result.size).toBe(0);
  });

  it('handles missing payload gracefully', () => {
    const relationship = makeRelationship(EntityRelationshipCardinality.OneToMany);
    const entity = {
      id: 'emp-1',
      workspaceId: 'ws-1',
      typeId: 'type-employee',
      identifierKey: 'name',
      icon: { type: 'FILE', colour: 'NEUTRAL' },
      sourceType: 'MANUAL',
      syncVersion: 0,
      identifier: 'emp-1',
      payload: {},
    } as unknown as Entity;

    const result = getConstrainedEntities([entity], relationship, false);
    expect(result.size).toBe(0);
  });

  it('returns reason string with linked entity label', () => {
    const relationship = makeRelationship(EntityRelationshipCardinality.OneToOne);
    const entities = [
      makeEntity('emp-1', 'rel-1', [
        { id: 'comp-1', sourceEntityId: 'emp-1', label: 'Acme Corp' },
      ]),
    ];

    const result = getConstrainedEntities(entities, relationship, false, 'comp-2');
    expect(result.get('emp-1')).toEqual({
      reason: 'Already assigned to Acme Corp',
      linkedLabel: 'Acme Corp',
    });
  });

  it('constrains source-side entities for target-side picker with ManyToOne', () => {
    const relationship = makeRelationship(EntityRelationshipCardinality.ManyToOne);
    const entities = [
      makeEntity('comp-1', 'rel-1', [
        { id: 'emp-1', sourceEntityId: 'comp-1', label: 'Employee 1' },
      ]),
    ];

    // isTargetSide=true, ManyToOne -> source is singular
    const result = getConstrainedEntities(entities, relationship, true, 'emp-2');
    expect(result.size).toBe(1);
    expect(result.get('comp-1')).toEqual({
      reason: 'Already assigned to Employee 1',
      linkedLabel: 'Employee 1',
    });
  });

  it('returns empty map when entities array is empty', () => {
    const relationship = makeRelationship(EntityRelationshipCardinality.OneToMany);
    const result = getConstrainedEntities([], relationship, false);
    expect(result.size).toBe(0);
  });

  it('returns empty map for MANY_TO_MANY on target-side picker', () => {
    const relationship = makeRelationship(EntityRelationshipCardinality.ManyToMany);
    const entities = [
      makeEntity('emp-1', 'rel-1', [
        { id: 'comp-1', sourceEntityId: 'emp-1', label: 'Company A' },
      ]),
    ];
    const result = getConstrainedEntities(entities, relationship, true);
    expect(result.size).toBe(0);
  });

  it('returns empty map for ONE_TO_MANY on target-side picker (source is not singular)', () => {
    // ONE_TO_MANY: source is plural (many), target is singular (one)
    // When isTargetSide=true we check sourceIsSingular — for ONE_TO_MANY that is false
    const relationship = makeRelationship(EntityRelationshipCardinality.OneToMany);
    const entities = [
      makeEntity('emp-1', 'rel-1', [
        { id: 'comp-1', sourceEntityId: 'comp-1', label: 'Company A' },
      ]),
    ];
    const result = getConstrainedEntities(entities, relationship, true, 'other-entity');
    expect(result.size).toBe(0);
  });

  it('constrains entities with OneToOne on target-side picker', () => {
    // ONE_TO_ONE: both source and target are singular
    const relationship = makeRelationship(EntityRelationshipCardinality.OneToOne);
    const entities = [
      makeEntity('comp-1', 'rel-1', [
        { id: 'emp-1', sourceEntityId: 'comp-1', label: 'Employee 1' },
      ]),
    ];
    const result = getConstrainedEntities(entities, relationship, true, 'emp-2');
    expect(result.size).toBe(1);
    expect(result.get('comp-1')).toEqual({
      reason: 'Already assigned to Employee 1',
      linkedLabel: 'Employee 1',
    });
  });

  it('constrains when no currentSourceEntityId provided and entity has existing links', () => {
    // Without currentSourceEntityId, any existing link is treated as a constraint
    const relationship = makeRelationship(EntityRelationshipCardinality.OneToMany);
    const entities = [
      makeEntity('emp-1', 'rel-1', [
        { id: 'comp-1', sourceEntityId: 'emp-1', label: 'Company A' },
      ]),
    ];
    const result = getConstrainedEntities(entities, relationship, false);
    expect(result.size).toBe(1);
    expect(result.get('emp-1')).toEqual({
      reason: 'Already assigned to Company A',
      linkedLabel: 'Company A',
    });
  });

  it('only records the first constraining link for an entity with multiple links', () => {
    const relationship = makeRelationship(EntityRelationshipCardinality.OneToMany);
    const entities = [
      makeEntity('emp-1', 'rel-1', [
        { id: 'comp-1', sourceEntityId: 'emp-1', label: 'Company A' },
        { id: 'comp-2', sourceEntityId: 'emp-1', label: 'Company B' },
      ]),
    ];
    const result = getConstrainedEntities(entities, relationship, false, 'comp-3');
    expect(result.size).toBe(1);
    // Should record first link's label, not the second
    expect(result.get('emp-1')).toEqual({
      reason: 'Already assigned to Company A',
      linkedLabel: 'Company A',
    });
  });

  it('handles multiple entities where some are constrained and some are not', () => {
    const relationship = makeRelationship(EntityRelationshipCardinality.OneToMany);
    const entities = [
      makeEntity('emp-1', 'rel-1', [
        { id: 'comp-1', sourceEntityId: 'emp-1', label: 'Company A' },
      ]),
      makeEntity('emp-2', 'rel-1', []),
      makeEntity('emp-3', 'rel-1', [
        { id: 'comp-2', sourceEntityId: 'emp-3', label: 'Company B' },
      ]),
    ];
    const result = getConstrainedEntities(entities, relationship, false, 'comp-3');
    expect(result.size).toBe(2);
    expect(result.has('emp-1')).toBe(true);
    expect(result.has('emp-2')).toBe(false);
    expect(result.has('emp-3')).toBe(true);
  });

  it('handles entity with relationship key present but non-relationship payload type', () => {
    const relationship = makeRelationship(EntityRelationshipCardinality.OneToMany);
    const entity = {
      id: 'emp-1',
      workspaceId: 'ws-1',
      typeId: 'type-employee',
      identifierKey: 'name',
      icon: { type: 'FILE', colour: 'NEUTRAL' },
      sourceType: 'MANUAL',
      syncVersion: 0,
      identifier: 'emp-1',
      payload: {
        'rel-1': {
          payload: {
            type: EntityPropertyType.Attribute,
            value: 'not a relationship',
          },
        },
      },
    } as unknown as Entity;

    const result = getConstrainedEntities([entity], relationship, false);
    expect(result.size).toBe(0);
  });

  it('does not constrain when ManyToOne and isTargetSide=false (target is not singular)', () => {
    // ManyToOne: source is singular, target (many-side) is plural
    // isTargetSide=false means we check targetIsSingular — for ManyToOne that is false
    const relationship = makeRelationship(EntityRelationshipCardinality.ManyToOne);
    const entities = [
      makeEntity('emp-1', 'rel-1', [
        { id: 'comp-1', sourceEntityId: 'emp-1', label: 'Company A' },
      ]),
    ];
    const result = getConstrainedEntities(entities, relationship, false, 'comp-2');
    expect(result.size).toBe(0);
  });
});
