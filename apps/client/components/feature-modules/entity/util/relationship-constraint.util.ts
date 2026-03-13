import {
  Entity,
  EntityRelationshipCardinality,
  isRelationshipPayload,
  RelationshipDefinition,
} from '@/lib/types/entity';

export interface ConstraintInfo {
  reason: string;
  linkedLabel: string;
}

/**
 * Determines which entities are unavailable for selection due to cardinality constraints.
 *
 * For ONE_TO_MANY (1 company -> many employees): the target side is singular,
 * meaning each employee can only belong to one company. If an employee is
 * already linked to Company A, they can't be picked for Company B.
 *
 * @returns Map of entityId -> constraint info for entities that can't be selected
 */
export function getConstrainedEntities(
  entities: Entity[],
  relationship: RelationshipDefinition,
  isTargetSide: boolean,
  currentSourceEntityId?: string,
): Map<string, ConstraintInfo> {
  const constrained = new Map<string, ConstraintInfo>();

  const targetIsSingular =
    relationship.cardinalityDefault === EntityRelationshipCardinality.OneToOne ||
    relationship.cardinalityDefault === EntityRelationshipCardinality.OneToMany;

  const sourceIsSingular =
    relationship.cardinalityDefault === EntityRelationshipCardinality.OneToOne ||
    relationship.cardinalityDefault === EntityRelationshipCardinality.ManyToOne;

  // When picking targets (isTargetSide=false): check if target-side cardinality is singular
  // When picking sources (isTargetSide=true): check if source-side cardinality is singular
  const checkSingular = isTargetSide ? sourceIsSingular : targetIsSingular;

  if (!checkSingular) return constrained;

  for (const entity of entities) {
    const attr = entity.payload[relationship.id];
    if (!attr?.payload) continue;
    if (!isRelationshipPayload(attr.payload)) continue;

    const relations = attr.payload.relations;
    if (relations.length === 0) continue;

    for (const link of relations) {
      const linkedId = isTargetSide ? link.sourceEntityId : link.id;
      if (linkedId && linkedId !== currentSourceEntityId) {
        constrained.set(entity.id, {
          reason: `Already assigned to ${link.label}`,
          linkedLabel: link.label,
        });
        break;
      }
    }
  }

  return constrained;
}
