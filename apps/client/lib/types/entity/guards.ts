// Type guards for entity discriminated unions

import type {
  EntityAttributePayload,
  EntityAttributeRelationPayload,
  RelationshipDefinition,
} from './models';
import type { EntityAttributeDefinition } from './custom';
import { EntityPropertyType } from '../models/EntityPropertyType';

export const isRelationshipDefinition = (
  attribute: RelationshipDefinition | EntityAttributeDefinition,
): attribute is RelationshipDefinition => {
  return !('schema' in attribute);
};

export const isAttributeDefinition = (
  attribute: RelationshipDefinition | EntityAttributeDefinition,
): attribute is EntityAttributeDefinition => {
  return 'schema' in attribute;
};

export const isRelationshipPayload = (
  payload: EntityAttributePayload,
): payload is EntityAttributeRelationPayload => {
  return payload.type === EntityPropertyType.Relationship;
};
