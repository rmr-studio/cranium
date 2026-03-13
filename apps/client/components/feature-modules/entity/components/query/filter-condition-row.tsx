'use client';

import { X } from 'lucide-react';
import { Button } from '@riven/ui/button';
import { motion } from 'framer-motion';
import type { EntityType, RelationshipDefinition } from '@/lib/types/entity';
import type { FilterOperator } from '@/lib/types/models/FilterOperator';
import {
  type AttributeInfo,
  type FilterConditionState,
  getOperatorsForSchemaType,
  getSchemaForAttribute,
  getSchemaTypeForAttribute,
  getRelationshipsFromEntityType,
} from './query-builder.utils';
import { FilterAttributePicker } from './filter-attribute-picker';
import { FilterOperatorSelect } from './filter-operator-select';
import { FilterValueInput } from './filter-value-input';
import { FilterRelationshipCondition } from './filter-relationship-condition';

interface FilterConditionRowProps {
  condition: FilterConditionState;
  entityType: EntityType;
  entityTypes?: EntityType[];
  isNested?: boolean;
  onChange: (updates: Partial<FilterConditionState>) => void;
  onRemove: () => void;
}

export function FilterConditionRow({
  condition,
  entityType,
  entityTypes,
  isNested,
  onChange,
  onRemove,
}: FilterConditionRowProps) {
  const schemaType = condition.attributeId
    ? getSchemaTypeForAttribute(condition.attributeId, entityType)
    : undefined;

  const schema = condition.attributeId
    ? getSchemaForAttribute(condition.attributeId, entityType)
    : undefined;

  const operators = schemaType ? getOperatorsForSchemaType(schemaType) : [];

  const relationships = getRelationshipsFromEntityType(entityType);
  const selectedRelationship = condition.relationshipId
    ? relationships.find((r) => r.id === condition.relationshipId)
    : undefined;

  const handleSelectAttribute = (attr: AttributeInfo) => {
    onChange({
      type: 'attribute',
      attributeId: attr.id,
      relationshipId: undefined,
      operator: undefined,
      value: undefined,
      relationshipConditionType: undefined,
      countOperator: undefined,
      countValue: undefined,
      targetFilter: undefined,
    });
  };

  const handleSelectRelationship = (rel: RelationshipDefinition) => {
    onChange({
      type: 'relationship',
      relationshipId: rel.id,
      attributeId: undefined,
      operator: undefined,
      value: undefined,
    });
  };

  const handleOperatorChange = (op: FilterOperator) => {
    onChange({ operator: op, value: undefined });
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: -8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -8 }}
      transition={{ duration: 0.15 }}
      className="flex items-start gap-1.5"
    >
      <div className="flex flex-1 flex-wrap items-center gap-1.5">
        {!isNested && (
          <FilterAttributePicker
            entityType={entityType}
            selectedAttributeId={condition.attributeId}
            selectedRelationshipId={condition.relationshipId}
            onSelectAttribute={handleSelectAttribute}
            onSelectRelationship={handleSelectRelationship}
          />
        )}

        {isNested && (
          <FilterAttributePicker
            entityType={entityType}
            selectedAttributeId={condition.attributeId}
            selectedRelationshipId={undefined}
            onSelectAttribute={handleSelectAttribute}
            onSelectRelationship={() => {}}
          />
        )}

        {condition.type === 'attribute' && condition.attributeId && (
          <>
            <FilterOperatorSelect
              operators={operators}
              value={condition.operator}
              onChange={handleOperatorChange}
            />
            {condition.operator && schemaType && (
              <FilterValueInput
                schemaType={schemaType}
                schema={schema}
                operator={condition.operator}
                value={condition.value}
                onChange={(v) => onChange({ value: v })}
              />
            )}
          </>
        )}

        {condition.type === 'relationship' && selectedRelationship && (
          <FilterRelationshipCondition
            condition={condition}
            relationship={selectedRelationship}
            entityTypes={entityTypes}
            onChange={onChange}
          />
        )}
      </div>

      <Button
        variant="ghost"
        size="icon"
        className="size-7 shrink-0"
        onClick={onRemove}
      >
        <X className="size-3" />
      </Button>
    </motion.div>
  );
}
