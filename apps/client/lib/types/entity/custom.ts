// Custom types specific to entity domain
import { EntityPropertyType } from '../models/EntityPropertyType';
import { SchemaUUID, SchemaType, DataType, Icon } from '../common';

import type { EntityRelationshipCardinality, RelationshipDefinition, RelationshipTargetRule } from './models';
import type { SemanticAttributeClassification } from '../models/SemanticAttributeClassification';

export interface EntityTypeDefinition {
  id: string;
  type: EntityPropertyType;
  definition: EntityAttributeDefinition | RelationshipDefinition;
}

export interface EntityAttributeDefinition {
  id: string;
  schema: SchemaUUID;
}

export interface EntityTypeAttributeRow {
  // Persistent Hash map lookup uuid - Cannot be changed after creation. Also unique identifier for relationships
  id: string;
  // Human readable display name
  label: string;
  type: EntityPropertyType;
  protected?: boolean;
  required: boolean;
  schemaType: SchemaType | 'RELATIONSHIP';
  additionalConstraints: string[];
  dataType?: DataType;
  unique?: boolean;
  // Icon for this attribute or relationship
  icon?: Icon;
  // Relationship-specific fields (optional for attributes)
  cardinalityDefault?: EntityRelationshipCardinality;
  allowPolymorphic?: boolean;
  targetRules?: RelationshipTargetRule[];
  // Resolved target entity type names (for relationships)
  targetEntityTypeNames?: string[];
  // Target-side relationship metadata
  isTargetSide?: boolean;
  sourceEntityTypeId?: string;
  sourceEntityTypeKey?: string;
  // Semantic metadata fields (optional, populated from SemanticMetadataBundle)
  classification?: SemanticAttributeClassification;
  definition?: string;
}

export interface EntityRelationshipCandidate {
  icon: Icon;
  name: string;
  key: string;
  existingRelationship: RelationshipDefinition;
}

export enum RelationshipLimit {
  SINGULAR,
  MANY,
}

/**
 * Discriminator values for the QueryFilter sealed hierarchy.
 *
 * Must match the backend's @JsonTypeName annotations exactly (uppercase).
 * The OpenAPI generator maps these to PascalCase in the TypeScript union type,
 * but when bypassing the generated ToJSON serializer we must use the raw values.
 */
export enum QueryFilterType {
  Attribute = 'ATTRIBUTE',
  Relationship = 'RELATIONSHIP',
  IsRelatedTo = 'IS_RELATED_TO',
  And = 'AND',
  Or = 'OR',
}

/**
 * Discriminator values for the FilterValue sealed hierarchy.
 *
 * Must match the backend's @JsonTypeName annotations exactly (uppercase).
 */
export enum FilterValueKind {
  Literal = 'LITERAL',
  Template = 'TEMPLATE',
}

/**
 * Discriminator values for the RelationshipFilter sealed hierarchy.
 *
 * Must match the backend's @JsonTypeName annotations exactly (uppercase).
 */
export enum RelationshipFilterType {
  Exists = 'EXISTS',
  NotExists = 'NOT_EXISTS',
  TargetEquals = 'TARGET_EQUALS',
  TargetMatches = 'TARGET_MATCHES',
  TargetTypeMatches = 'TARGET_TYPE_MATCHES',
  CountMatches = 'COUNT_MATCHES',
}

/**
 * Props interface for relationship picker component
 * Used for selecting related entities in draft rows
 */
export interface RelationshipPickerProps {
  relationship: RelationshipDefinition;
  value: string | string[] | null;
  onChange: (value: string | string[] | null) => void;
  onBlur: () => void;
  errors?: string[];
  disabled?: boolean;
}

