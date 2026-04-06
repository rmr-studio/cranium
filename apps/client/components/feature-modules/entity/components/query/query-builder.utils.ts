'use client';

import {
  type EntityType,
  type QueryFilter,
  type RelationshipDefinition,
  type RelationshipFilter,
  type SchemaUUID,
  FilterOperator,
  FilterValueKind,
  QueryFilterType,
  RelationshipFilterType,
  SchemaType,
} from '@/lib/types/entity';
import { v4 as uuid } from 'uuid';

// ---------------------------------------------------------------------------
// Operator display labels
// ---------------------------------------------------------------------------

export interface OperatorOption {
  value: FilterOperator;
  label: string;
}

const OPERATOR_LABELS: Record<FilterOperator, string> = {
  [FilterOperator.Equals]: 'is',
  [FilterOperator.NotEquals]: 'is not',
  [FilterOperator.GreaterThan]: '>',
  [FilterOperator.GreaterThanOrEquals]: '>=',
  [FilterOperator.LessThan]: '<',
  [FilterOperator.LessThanOrEquals]: '<=',
  [FilterOperator.In]: 'is any of',
  [FilterOperator.NotIn]: 'is none of',
  [FilterOperator.Contains]: 'contains',
  [FilterOperator.NotContains]: 'does not contain',
  [FilterOperator.IsNull]: 'is empty',
  [FilterOperator.IsNotNull]: 'is not empty',
  [FilterOperator.StartsWith]: 'starts with',
  [FilterOperator.EndsWith]: 'ends with',
};

// ---------------------------------------------------------------------------
// Operator mappings per SchemaType
// ---------------------------------------------------------------------------

const TEXT_OPERATORS: FilterOperator[] = [
  FilterOperator.Equals,
  FilterOperator.NotEquals,
  FilterOperator.Contains,
  FilterOperator.NotContains,
  FilterOperator.StartsWith,
  FilterOperator.EndsWith,
  FilterOperator.IsNull,
  FilterOperator.IsNotNull,
];

const NUMBER_OPERATORS: FilterOperator[] = [
  FilterOperator.Equals,
  FilterOperator.NotEquals,
  FilterOperator.GreaterThan,
  FilterOperator.GreaterThanOrEquals,
  FilterOperator.LessThan,
  FilterOperator.LessThanOrEquals,
  FilterOperator.IsNull,
  FilterOperator.IsNotNull,
];

const DATE_OPERATORS: FilterOperator[] = [
  FilterOperator.Equals,
  FilterOperator.NotEquals,
  FilterOperator.GreaterThan,
  FilterOperator.GreaterThanOrEquals,
  FilterOperator.LessThan,
  FilterOperator.LessThanOrEquals,
  FilterOperator.IsNull,
  FilterOperator.IsNotNull,
];

const SELECT_OPERATORS: FilterOperator[] = [
  FilterOperator.Equals,
  FilterOperator.NotEquals,
  FilterOperator.In,
  FilterOperator.NotIn,
  FilterOperator.IsNull,
  FilterOperator.IsNotNull,
];

const MULTI_SELECT_OPERATORS: FilterOperator[] = [
  FilterOperator.Contains,
  FilterOperator.NotContains,
  FilterOperator.IsNull,
  FilterOperator.IsNotNull,
];

const CHECKBOX_OPERATORS: FilterOperator[] = [FilterOperator.Equals];

const SCHEMA_TYPE_OPERATORS: Record<SchemaType, FilterOperator[]> = {
  [SchemaType.Text]: TEXT_OPERATORS,
  [SchemaType.Email]: TEXT_OPERATORS,
  [SchemaType.Url]: TEXT_OPERATORS,
  [SchemaType.Phone]: TEXT_OPERATORS,
  [SchemaType.Number]: NUMBER_OPERATORS,
  // ID is a special case of NUMBER type with same operators, as the ID itself is sequential, and queries should ignore the custom prefix.
  [SchemaType.Id]: NUMBER_OPERATORS,
  [SchemaType.Currency]: NUMBER_OPERATORS,
  [SchemaType.Percentage]: NUMBER_OPERATORS,
  [SchemaType.Rating]: NUMBER_OPERATORS,
  [SchemaType.Date]: DATE_OPERATORS,
  [SchemaType.Datetime]: DATE_OPERATORS,
  [SchemaType.Select]: SELECT_OPERATORS,
  [SchemaType.MultiSelect]: MULTI_SELECT_OPERATORS,
  [SchemaType.Checkbox]: CHECKBOX_OPERATORS,
  [SchemaType.Object]: [FilterOperator.IsNull, FilterOperator.IsNotNull],
  [SchemaType.FileAttachment]: [FilterOperator.IsNull, FilterOperator.IsNotNull],
  [SchemaType.Location]: [FilterOperator.IsNull, FilterOperator.IsNotNull],
};

const COUNT_OPERATORS: FilterOperator[] = [
  FilterOperator.Equals,
  FilterOperator.NotEquals,
  FilterOperator.GreaterThan,
  FilterOperator.GreaterThanOrEquals,
  FilterOperator.LessThan,
  FilterOperator.LessThanOrEquals,
];

// ---------------------------------------------------------------------------
// Public helpers
// ---------------------------------------------------------------------------

export function getOperatorsForSchemaType(schemaType: SchemaType): OperatorOption[] {
  const ops = SCHEMA_TYPE_OPERATORS[schemaType] ?? [];
  return ops.map((op) => ({ value: op, label: OPERATOR_LABELS[op] }));
}

export function getCountOperators(): OperatorOption[] {
  return COUNT_OPERATORS.map((op) => ({ value: op, label: OPERATOR_LABELS[op] }));
}

export function getOperatorLabel(op: FilterOperator): string {
  return OPERATOR_LABELS[op];
}

export function isNullaryOperator(op: FilterOperator): boolean {
  return op === FilterOperator.IsNull || op === FilterOperator.IsNotNull;
}

export function isMultiValueOperator(op: FilterOperator): boolean {
  return op === FilterOperator.In || op === FilterOperator.NotIn;
}

// ---------------------------------------------------------------------------
// Attribute helpers
// ---------------------------------------------------------------------------

export interface AttributeInfo {
  id: string;
  label: string;
  schemaType: SchemaType;
  schema: SchemaUUID;
}

export function getAttributesFromEntityType(entityType: EntityType): AttributeInfo[] {
  const properties = entityType.schema?.properties;
  if (!properties) return [];

  return Object.entries(properties).map(([id, schema]) => ({
    id,
    label: schema.label ?? id,
    schemaType: schema.key,
    schema,
  }));
}

export function getRelationshipsFromEntityType(entityType: EntityType): RelationshipDefinition[] {
  return entityType.relationships ?? [];
}

export function getSchemaTypeForAttribute(
  attributeId: string,
  entityType: EntityType,
): SchemaType | undefined {
  return entityType.schema?.properties?.[attributeId]?.key;
}

export function getSchemaForAttribute(
  attributeId: string,
  entityType: EntityType,
): SchemaUUID | undefined {
  return entityType.schema?.properties?.[attributeId];
}

// ---------------------------------------------------------------------------
// Internal state types
// ---------------------------------------------------------------------------

export type RelationshipConditionType = 'exists' | 'notExists' | 'countMatches' | 'targetMatches';

export interface FilterConditionState {
  id: string;
  type: 'attribute' | 'relationship';
  attributeId?: string;
  relationshipId?: string;
  operator?: FilterOperator;
  value?: unknown;
  relationshipConditionType?: RelationshipConditionType;
  countOperator?: FilterOperator;
  countValue?: number;
  targetFilter?: FilterGroupState;
}

export interface FilterGroupState {
  id: string;
  logicalOperator: 'and' | 'or';
  conditions: (FilterConditionState | FilterGroupState)[];
}

export function isFilterGroup(
  item: FilterConditionState | FilterGroupState,
): item is FilterGroupState {
  return 'logicalOperator' in item;
}

// ---------------------------------------------------------------------------
// Factory helpers
// ---------------------------------------------------------------------------

export function createEmptyGroup(logicalOperator: 'and' | 'or' = 'and'): FilterGroupState {
  return { id: uuid(), logicalOperator, conditions: [] };
}

export function createEmptyCondition(): FilterConditionState {
  return { id: uuid(), type: 'attribute' };
}

// ---------------------------------------------------------------------------
// Completeness check
// ---------------------------------------------------------------------------

export function isConditionComplete(condition: FilterConditionState): boolean {
  if (condition.type === 'relationship') {
    if (!condition.relationshipId || !condition.relationshipConditionType) return false;
    if (condition.relationshipConditionType === 'countMatches') {
      return condition.countOperator != null && condition.countValue != null;
    }
    if (condition.relationshipConditionType === 'targetMatches') {
      return condition.targetFilter != null && hasCompleteConditions(condition.targetFilter);
    }
    return true; // exists / notExists
  }

  if (!condition.attributeId || !condition.operator) return false;
  if (isNullaryOperator(condition.operator)) return true;
  if (condition.operator === FilterOperator.Equals && condition.value === false) return true;
  return condition.value != null && condition.value !== '';
}

function hasCompleteConditions(group: FilterGroupState): boolean {
  return group.conditions.some((c) =>
    isFilterGroup(c) ? hasCompleteConditions(c) : isConditionComplete(c),
  );
}

// ---------------------------------------------------------------------------
// State -> QueryFilter conversion
// ---------------------------------------------------------------------------

export function filterGroupStateToQueryFilter(group: FilterGroupState): QueryFilter | undefined {
  const filters = group.conditions
    .map((c) => (isFilterGroup(c) ? filterGroupStateToQueryFilter(c) : conditionToQueryFilter(c)))
    .filter((f): f is QueryFilter => f != null);

  if (filters.length === 0) return undefined;
  if (filters.length === 1) return filters[0];

  if (group.logicalOperator === 'and') {
    return { type: QueryFilterType.And, conditions: filters } as QueryFilter;
  }
  return { type: QueryFilterType.Or, conditions: filters } as QueryFilter;
}

function conditionToQueryFilter(condition: FilterConditionState): QueryFilter | undefined {
  if (!isConditionComplete(condition)) return undefined;

  if (condition.type === 'relationship') {
    return buildRelationshipFilter(condition);
  }

  return {
    type: QueryFilterType.Attribute,
    attributeId: condition.attributeId,
    operator: condition.operator,
    value: isNullaryOperator(condition.operator!)
      ? undefined
      : { kind: FilterValueKind.Literal, value: condition.value },
  } as QueryFilter;
}

function buildRelationshipFilter(condition: FilterConditionState): QueryFilter | undefined {
  let relCondition: RelationshipFilter | undefined;

  switch (condition.relationshipConditionType) {
    case 'exists':
      relCondition = { type: RelationshipFilterType.Exists } as RelationshipFilter;
      break;
    case 'notExists':
      relCondition = { type: RelationshipFilterType.NotExists } as RelationshipFilter;
      break;
    case 'countMatches':
      relCondition = {
        type: RelationshipFilterType.CountMatches,
        operator: condition.countOperator,
        count: condition.countValue,
      } as RelationshipFilter;
      break;
    case 'targetMatches': {
      const nested = condition.targetFilter
        ? filterGroupStateToQueryFilter(condition.targetFilter)
        : undefined;
      if (!nested) return undefined;
      relCondition = {
        type: RelationshipFilterType.TargetMatches,
        filter: nested,
      } as RelationshipFilter;
      break;
    }
    default:
      return undefined;
  }

  return {
    type: QueryFilterType.Relationship,
    relationshipId: condition.relationshipId,
    condition: relCondition,
  };
}

// ---------------------------------------------------------------------------
// QueryFilter -> State conversion (hydration)
// ---------------------------------------------------------------------------

export function queryFilterToFilterGroupState(filter: QueryFilter): FilterGroupState {
  if (filter.type === 'And') {
    return {
      id: uuid(),
      logicalOperator: 'and',
      conditions: (filter.conditions ?? []).map(queryFilterToItem),
    };
  }

  if (filter.type === 'Or') {
    return {
      id: uuid(),
      logicalOperator: 'or',
      conditions: (filter.conditions ?? []).map(queryFilterToItem),
    };
  }

  // Single condition — wrap in an And group
  return {
    id: uuid(),
    logicalOperator: 'and',
    conditions: [queryFilterToItem(filter)],
  };
}

function queryFilterToItem(filter: QueryFilter): FilterConditionState | FilterGroupState {
  if (filter.type === 'And' || filter.type === 'Or') {
    return queryFilterToFilterGroupState(filter);
  }

  if (filter.type === 'Attribute') {
    return {
      id: uuid(),
      type: 'attribute',
      attributeId: filter.attributeId,
      operator: filter.operator,
      value:
        filter.value?.kind === 'Literal' ? (filter.value as { value?: unknown }).value : undefined,
    };
  }

  if (filter.type === 'Relationship') {
    return hydrateRelationshipCondition(filter);
  }

  // IsRelatedTo — treat as relationship without specific ID
  return createEmptyCondition();
}

function hydrateRelationshipCondition(
  filter: QueryFilter & { type: 'Relationship' },
): FilterConditionState {
  const condition: FilterConditionState = {
    id: uuid(),
    type: 'relationship',
    relationshipId: filter.relationshipId,
  };

  const relFilter = filter.condition;
  if (!relFilter) return condition;

  switch (relFilter.type) {
    case 'Exists':
      condition.relationshipConditionType = 'exists';
      break;
    case 'NotExists':
      condition.relationshipConditionType = 'notExists';
      break;
    case 'CountMatches':
      condition.relationshipConditionType = 'countMatches';
      condition.countOperator = (relFilter as { operator?: FilterOperator }).operator;
      condition.countValue = (relFilter as { count?: number }).count;
      break;
    case 'TargetMatches': {
      condition.relationshipConditionType = 'targetMatches';
      const nested = (relFilter as { filter?: QueryFilter }).filter;
      if (nested) {
        condition.targetFilter = queryFilterToFilterGroupState(nested);
      }
      break;
    }
  }

  return condition;
}

// ---------------------------------------------------------------------------
// Active filter count
// ---------------------------------------------------------------------------

export function countActiveConditions(group: FilterGroupState): number {
  return group.conditions.reduce((count, c) => {
    if (isFilterGroup(c)) return count + countActiveConditions(c);
    return count + (isConditionComplete(c) ? 1 : 0);
  }, 0);
}
