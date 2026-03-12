import { DataFormat, DataType, IconColour, IconType, SchemaType } from '@/lib/types/common';
import type { SchemaUUID } from '@/lib/types/common';
import {
  EntityPropertyType,
  EntityRelationshipCardinality,
  EntityType,
  RelationshipDefinition,
  SemanticGroup,
} from '@/lib/types/entity';

// ---------------------------------------------------------------------------
// Helper: build a minimal SchemaUUID attribute entry
// ---------------------------------------------------------------------------
function attr(
  key: SchemaType,
  label: string,
  type: DataType,
  overrides: Partial<SchemaUUID> = {},
): SchemaUUID {
  return {
    key,
    label,
    type,
    icon: { type: IconType.Circle, colour: IconColour.Neutral },
    required: false,
    unique: false,
    _protected: false,
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Helper: build a RelationshipDefinition
// ---------------------------------------------------------------------------
function rel(
  id: string,
  sourceEntityTypeId: string,
  name: string,
  cardinality: EntityRelationshipCardinality,
  targetEntityTypeId: string,
  inverseName: string,
): RelationshipDefinition {
  return {
    id,
    workspaceId: 'workspace-fixture-id',
    sourceEntityTypeId,
    name,
    icon: { type: IconType.Link, colour: IconColour.Blue },
    cardinalityDefault: cardinality,
    _protected: false,
    isPolymorphic: false,
    targetRules: [
      {
        id: `${id}-rule-1`,
        relationshipDefinitionId: id,
        targetEntityTypeId,
        inverseName,
      },
    ],
  };
}

// ---------------------------------------------------------------------------
// Shared base fields reused across fixtures
// ---------------------------------------------------------------------------
const WORKSPACE_ID = 'workspace-fixture-id';

// ---------------------------------------------------------------------------
// Fixture 1: Simple type — contacts with name (required text), age (number),
//             active (checkbox)
// ---------------------------------------------------------------------------
export const simpleContactEntityType: EntityType = {
  id: 'entity-type-contacts',
  key: 'contacts',
  version: 1,
  icon: { type: IconType.Users, colour: IconColour.Blue },
  name: { singular: 'Contact', plural: 'Contacts' },
  _protected: false,
  identifierKey: 'name',
  semanticGroup: SemanticGroup.Customer,
  sourceType: 'USER_CREATED' as EntityType['sourceType'],
  readonly: false,
  workspaceId: WORKSPACE_ID,
  entitiesCount: 0,
  relationships: [],
  columns: [
    { key: 'name', type: EntityPropertyType.Attribute, visible: true, width: 200 },
    { key: 'age', type: EntityPropertyType.Attribute, visible: true, width: 100 },
    { key: 'active', type: EntityPropertyType.Attribute, visible: true, width: 80 },
  ],
  schema: {
    key: SchemaType.Object,
    type: DataType.Object,
    icon: { type: IconType.Box, colour: IconColour.Neutral },
    required: false,
    unique: false,
    _protected: false,
    properties: {
      name: attr(SchemaType.Text, 'Name', DataType.String, { required: true }),
      age: attr(SchemaType.Number, 'Age', DataType.Number),
      active: attr(SchemaType.Checkbox, 'Active', DataType.Boolean),
    },
  },
};

// ---------------------------------------------------------------------------
// Fixture 2: Complex type — one of each SchemaType
// ---------------------------------------------------------------------------
export const complexAllSchemaTypesEntityType: EntityType = {
  id: 'entity-type-all-schema-types',
  key: 'all_schema_types',
  version: 1,
  icon: { type: IconType.Database, colour: IconColour.Purple },
  name: { singular: 'All Schema Type', plural: 'All Schema Types' },
  _protected: false,
  identifierKey: 'title',
  semanticGroup: SemanticGroup.Custom,
  sourceType: 'USER_CREATED' as EntityType['sourceType'],
  readonly: false,
  workspaceId: WORKSPACE_ID,
  entitiesCount: 0,
  relationships: [],
  columns: [
    { key: 'title', type: EntityPropertyType.Attribute, visible: true, width: 200 },
    { key: 'count', type: EntityPropertyType.Attribute, visible: true, width: 100 },
    { key: 'active', type: EntityPropertyType.Attribute, visible: true, width: 80 },
    { key: 'due_date', type: EntityPropertyType.Attribute, visible: true, width: 120 },
    { key: 'created_at', type: EntityPropertyType.Attribute, visible: true, width: 150 },
    { key: 'rating', type: EntityPropertyType.Attribute, visible: true, width: 80 },
    { key: 'phone', type: EntityPropertyType.Attribute, visible: true, width: 120 },
    { key: 'email', type: EntityPropertyType.Attribute, visible: true, width: 180 },
    { key: 'website', type: EntityPropertyType.Attribute, visible: true, width: 200 },
    { key: 'price', type: EntityPropertyType.Attribute, visible: true, width: 100 },
    { key: 'completion', type: EntityPropertyType.Attribute, visible: true, width: 100 },
    { key: 'status', type: EntityPropertyType.Attribute, visible: true, width: 120 },
    { key: 'tags', type: EntityPropertyType.Attribute, visible: true, width: 160 },
    { key: 'attachments', type: EntityPropertyType.Attribute, visible: true, width: 120 },
  ],
  schema: {
    key: SchemaType.Object,
    type: DataType.Object,
    icon: { type: IconType.Box, colour: IconColour.Neutral },
    required: false,
    unique: false,
    _protected: false,
    properties: {
      title: attr(SchemaType.Text, 'Title', DataType.String),
      count: attr(SchemaType.Number, 'Count', DataType.Number),
      active: attr(SchemaType.Checkbox, 'Active', DataType.Boolean),
      due_date: attr(SchemaType.Date, 'Due Date', DataType.String, {
        format: DataFormat.Date,
      }),
      created_at: attr(SchemaType.Datetime, 'Created At', DataType.String, {
        format: DataFormat.Datetime,
      }),
      rating: attr(SchemaType.Rating, 'Rating', DataType.Number),
      phone: attr(SchemaType.Phone, 'Phone', DataType.String, {
        format: DataFormat.Phone,
      }),
      email: attr(SchemaType.Email, 'Email', DataType.String, {
        format: DataFormat.Email,
      }),
      website: attr(SchemaType.Url, 'Website', DataType.String, {
        format: DataFormat.Url,
      }),
      price: attr(SchemaType.Currency, 'Price', DataType.Number, {
        format: DataFormat.Currency,
      }),
      completion: attr(SchemaType.Percentage, 'Completion', DataType.Number, {
        format: DataFormat.Percentage,
      }),
      status: attr(SchemaType.Select, 'Status', DataType.String, {
        options: {
          _enum: ['todo', 'in_progress', 'done'],
        },
      }),
      tags: attr(SchemaType.MultiSelect, 'Tags', DataType.Array, {
        options: {
          _enum: ['bug', 'feature', 'improvement'],
        },
      }),
      attachments: attr(SchemaType.FileAttachment, 'Attachments', DataType.Array),
    },
  },
};

// ---------------------------------------------------------------------------
// Fixture 3: Relationship type — entity with ManyToOne, ManyToMany, OneToOne
//             relationships plus some attributes
// ---------------------------------------------------------------------------
const COMPANY_ENTITY_TYPE_ID = 'entity-type-companies';
const PROJECT_ENTITY_TYPE_ID = 'entity-type-projects';
const OWNER_ENTITY_TYPE_ID = 'entity-type-owners';

export const relationshipHeavyEntityType: EntityType = {
  id: 'entity-type-deals',
  key: 'deals',
  version: 1,
  icon: { type: IconType.Handshake, colour: IconColour.Green },
  name: { singular: 'Deal', plural: 'Deals' },
  _protected: false,
  identifierKey: 'title',
  semanticGroup: SemanticGroup.Transaction,
  sourceType: 'USER_CREATED' as EntityType['sourceType'],
  readonly: false,
  workspaceId: WORKSPACE_ID,
  entitiesCount: 0,
  columns: [
    { key: 'title', type: EntityPropertyType.Attribute, visible: true, width: 200 },
    { key: 'value', type: EntityPropertyType.Attribute, visible: true, width: 100 },
    { key: 'company', type: EntityPropertyType.Relationship, visible: true, width: 150 },
    { key: 'projects', type: EntityPropertyType.Relationship, visible: true, width: 150 },
    { key: 'owner', type: EntityPropertyType.Relationship, visible: true, width: 120 },
  ],
  schema: {
    key: SchemaType.Object,
    type: DataType.Object,
    icon: { type: IconType.Box, colour: IconColour.Neutral },
    required: false,
    unique: false,
    _protected: false,
    properties: {
      title: attr(SchemaType.Text, 'Title', DataType.String, { required: true }),
      value: attr(SchemaType.Currency, 'Value', DataType.Number, {
        format: DataFormat.Currency,
      }),
    },
  },
  relationships: [
    rel(
      'rel-deal-company',
      'entity-type-deals',
      'Company',
      EntityRelationshipCardinality.ManyToOne,
      COMPANY_ENTITY_TYPE_ID,
      'Deals',
    ),
    rel(
      'rel-deal-projects',
      'entity-type-deals',
      'Projects',
      EntityRelationshipCardinality.ManyToMany,
      PROJECT_ENTITY_TYPE_ID,
      'Deals',
    ),
    rel(
      'rel-deal-owner',
      'entity-type-deals',
      'Owner',
      EntityRelationshipCardinality.OneToOne,
      OWNER_ENTITY_TYPE_ID,
      'Deal',
    ),
  ],
};

// ---------------------------------------------------------------------------
// Fixture 4: Edge case type — required fields, optional fields, constraints
//             (min/max), regex, defaults
// ---------------------------------------------------------------------------
export const edgeCaseEntityType: EntityType = {
  id: 'entity-type-edge-cases',
  key: 'edge_cases',
  version: 3,
  icon: { type: IconType.FlaskConical, colour: IconColour.Orange },
  name: { singular: 'Edge Case', plural: 'Edge Cases' },
  _protected: false,
  identifierKey: 'code',
  semanticGroup: SemanticGroup.Uncategorized,
  sourceType: 'USER_CREATED' as EntityType['sourceType'],
  readonly: false,
  workspaceId: WORKSPACE_ID,
  entitiesCount: 0,
  relationships: [],
  columns: [
    { key: 'code', type: EntityPropertyType.Attribute, visible: true, width: 120 },
    { key: 'description', type: EntityPropertyType.Attribute, visible: true, width: 300 },
    { key: 'score', type: EntityPropertyType.Attribute, visible: true, width: 80 },
    { key: 'notes', type: EntityPropertyType.Attribute, visible: false, width: 200 },
    { key: 'priority', type: EntityPropertyType.Attribute, visible: true, width: 100 },
  ],
  schema: {
    key: SchemaType.Object,
    type: DataType.Object,
    icon: { type: IconType.Box, colour: IconColour.Neutral },
    required: false,
    unique: false,
    _protected: false,
    properties: {
      // Required field with regex constraint
      code: attr(SchemaType.Text, 'Code', DataType.String, {
        required: true,
        unique: true,
        _protected: true,
        options: {
          regex: '^[A-Z]{2,4}-\\d{4}$',
          minLength: 5,
          maxLength: 10,
        },
      }),
      // Required text with length constraints
      description: attr(SchemaType.Text, 'Description', DataType.String, {
        required: true,
        options: {
          minLength: 10,
          maxLength: 500,
        },
      }),
      // Number with min/max constraints and default
      score: attr(SchemaType.Number, 'Score', DataType.Number, {
        required: true,
        options: {
          minimum: 0,
          maximum: 100,
          _default: 50,
        },
      }),
      // Optional text — not required, no constraints
      notes: attr(SchemaType.Text, 'Notes', DataType.String),
      // Select with enum options and a default value
      priority: attr(SchemaType.Select, 'Priority', DataType.String, {
        options: {
          _enum: ['low', 'medium', 'high', 'critical'],
          _default: 'medium',
        },
      }),
    },
  },
};
