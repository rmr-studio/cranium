'use client';

import { Plus } from 'lucide-react';
import { Button } from '@riven/ui/button';
import { cn } from '@riven/utils';
import type { EntityType } from '@/lib/types/entity';
import {
  type FilterConditionState,
  type FilterGroupState,
  isFilterGroup,
  createEmptyCondition,
  createEmptyGroup,
} from './query-builder.utils';
import { FilterConditionRow } from './filter-condition-row';

interface FilterGroupNodeProps {
  group: FilterGroupState;
  entityType: EntityType;
  entityTypes?: EntityType[];
  depth?: number;
  onChange: (group: FilterGroupState) => void;
  hideActions?: boolean;
}

export function FilterGroupNode({
  group,
  entityType,
  entityTypes,
  depth = 0,
  onChange,
  hideActions = false,
}: FilterGroupNodeProps) {
  const updateCondition = (index: number, updates: Partial<FilterConditionState>) => {
    const next = { ...group, conditions: [...group.conditions] };
    const existing = next.conditions[index];
    if (isFilterGroup(existing)) return;
    next.conditions[index] = { ...existing, ...updates };
    onChange(next);
  };

  const updateNestedGroup = (index: number, nestedGroup: FilterGroupState) => {
    const next = { ...group, conditions: [...group.conditions] };
    next.conditions[index] = nestedGroup;
    onChange(next);
  };

  const removeCondition = (index: number) => {
    onChange({
      ...group,
      conditions: group.conditions.filter((_, i) => i !== index),
    });
  };

  const addCondition = () => {
    onChange({
      ...group,
      conditions: [...group.conditions, createEmptyCondition()],
    });
  };

  const addGroup = () => {
    const nested = createEmptyGroup(group.logicalOperator === 'and' ? 'or' : 'and');
    onChange({
      ...group,
      conditions: [...group.conditions, nested],
    });
  };

  const toggleLogicalOperator = () => {
    onChange({
      ...group,
      logicalOperator: group.logicalOperator === 'and' ? 'or' : 'and',
    });
  };

  return (
    <div
      className={cn(
        'flex flex-col gap-1.5',
        depth > 0 && 'border-muted border-l-2 border-dashed pl-3',
      )}
    >
      {group.conditions.map((item, index) => {
          if (isFilterGroup(item)) {
            return (
              <div key={item.id} className="flex flex-col gap-1.5">
                {index > 0 && (
                  <LogicalConnector
                    operator={group.logicalOperator}
                    onToggle={toggleLogicalOperator}
                  />
                )}
                <FilterGroupNode
                  group={item}
                  entityType={entityType}
                  entityTypes={entityTypes}
                  depth={depth + 1}
                  onChange={(g) => updateNestedGroup(index, g)}
                />
                <Button
                  variant="ghost"
                  size="xs"
                  className="text-muted-foreground hover:text-destructive ml-auto text-xs"
                  onClick={() => removeCondition(index)}
                >
                  Remove group
                </Button>
              </div>
            );
          }

          return (
            <div key={item.id} className="flex flex-col gap-1.5">
              {index === 0 && depth === 0 && (
                <span className="text-muted-foreground text-xs font-medium">Where</span>
              )}
              {index > 0 && (
                <LogicalConnector
                  operator={group.logicalOperator}
                  onToggle={toggleLogicalOperator}
                />
              )}
              <FilterConditionRow
                condition={item}
                entityType={entityType}
                entityTypes={entityTypes}
                onChange={(updates) => updateCondition(index, updates)}
                onRemove={() => removeCondition(index)}
              />
            </div>
          );
        })}

      {!hideActions && (
        <div className="flex items-center gap-2 pt-1">
          <Button
            variant="ghost"
            size="xs"
            className="text-muted-foreground text-xs"
            onClick={addCondition}
          >
            <Plus className="mr-1 size-3" />
            Add condition
          </Button>
          {depth === 0 && (
            <Button
              variant="ghost"
              size="xs"
              className="text-muted-foreground text-xs"
              onClick={addGroup}
            >
              <Plus className="mr-1 size-3" />
              Add group
            </Button>
          )}
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Logical connector chip
// ---------------------------------------------------------------------------

function LogicalConnector({
  operator,
  onToggle,
}: {
  operator: 'and' | 'or';
  onToggle: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onToggle}
      className="text-muted-foreground hover:text-foreground hover:bg-muted w-fit rounded px-1.5 py-0.5 text-xs font-medium uppercase transition-colors"
    >
      {operator}
    </button>
  );
}
