'use client';

import { Input } from '@riven/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@riven/ui/select';
import type { EntityType, RelationshipDefinition } from '@/lib/types/entity';
import {
  type FilterConditionState,
  type FilterGroupState,
  type RelationshipConditionType,
  createEmptyCondition,
  createEmptyGroup,
  getCountOperators,
} from './query-builder.utils';
import { FilterOperatorSelect } from './filter-operator-select';
import { FilterConditionRow } from './filter-condition-row';

interface FilterRelationshipConditionProps {
  condition: FilterConditionState;
  relationship: RelationshipDefinition;
  entityTypes?: EntityType[];
  onChange: (updates: Partial<FilterConditionState>) => void;
}

export function FilterRelationshipCondition({
  condition,
  relationship,
  entityTypes,
  onChange,
}: FilterRelationshipConditionProps) {
  const conditionType = condition.relationshipConditionType;

  const targetEntityType = resolveTargetEntityType(relationship, entityTypes);

  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center gap-1.5">
        <Select
          value={conditionType ?? ''}
          onValueChange={(v) => {
            const next: Partial<FilterConditionState> = {
              relationshipConditionType: v as RelationshipConditionType,
            };
            if (v === 'targetMatches' && !condition.targetFilter) {
              next.targetFilter = createEmptyGroup();
            }
            onChange(next);
          }}
        >
          <SelectTrigger className="h-7 w-auto min-w-28 gap-1 px-2.5 text-xs font-medium">
            <SelectValue placeholder="Condition" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="exists" className="text-xs">has any</SelectItem>
            <SelectItem value="notExists" className="text-xs">has none</SelectItem>
            <SelectItem value="countMatches" className="text-xs">count matches</SelectItem>
            {targetEntityType && (
              <SelectItem value="targetMatches" className="text-xs">
                where target matches
              </SelectItem>
            )}
          </SelectContent>
        </Select>

        {conditionType === 'countMatches' && (
          <>
            <FilterOperatorSelect
              operators={getCountOperators()}
              value={condition.countOperator}
              onChange={(op) => onChange({ countOperator: op })}
            />
            <Input
              type="number"
              placeholder="0"
              className="h-7 w-16 text-xs"
              value={condition.countValue != null ? String(condition.countValue) : ''}
              onChange={(e) =>
                onChange({
                  countValue: e.target.value === '' ? undefined : Number(e.target.value),
                })
              }
            />
          </>
        )}
      </div>

      {conditionType === 'targetMatches' && targetEntityType && condition.targetFilter && (
        <TargetMatchesNested
          targetEntityType={targetEntityType}
          group={condition.targetFilter}
          onChange={(targetFilter) => onChange({ targetFilter })}
        />
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Nested target attribute filter (1-hop depth)
// ---------------------------------------------------------------------------

function TargetMatchesNested({
  targetEntityType,
  group,
  onChange,
}: {
  targetEntityType: EntityType;
  group: FilterGroupState;
  onChange: (group: FilterGroupState) => void;
}) {
  const updateCondition = (index: number, updates: Partial<FilterConditionState>) => {
    const next = { ...group, conditions: [...group.conditions] };
    next.conditions[index] = { ...next.conditions[index], ...updates } as FilterConditionState;
    onChange(next);
  };

  const removeCondition = (index: number) => {
    const next = {
      ...group,
      conditions: group.conditions.filter((_, i) => i !== index),
    };
    onChange(next);
  };

  const addCondition = () => {
    const next = {
      ...group,
      conditions: [...group.conditions, createEmptyCondition()],
    };
    onChange(next);
  };

  return (
    <div className="border-muted ml-2 border-l-2 border-dashed pl-3">
      <p className="text-muted-foreground mb-1 text-[10px] font-medium uppercase tracking-wide">
        Target attributes
      </p>
      <div className="flex flex-col gap-1">
        {group.conditions.map((c, i) => (
          <FilterConditionRow
            key={(c as FilterConditionState).id}
            condition={c as FilterConditionState}
            entityType={targetEntityType}
            isNested
            onChange={(updates) => updateCondition(i, updates)}
            onRemove={() => removeCondition(i)}
          />
        ))}
      </div>
      <button
        type="button"
        onClick={addCondition}
        className="text-muted-foreground hover:text-foreground mt-1 text-xs"
      >
        + Add target condition
      </button>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function resolveTargetEntityType(
  relationship: RelationshipDefinition,
  entityTypes?: EntityType[],
): EntityType | undefined {
  if (!entityTypes || entityTypes.length === 0) return undefined;
  const targetId = relationship.targetRules?.[0]?.targetEntityTypeId;
  if (!targetId) return undefined;
  return entityTypes.find((et) => et.id === targetId);
}
