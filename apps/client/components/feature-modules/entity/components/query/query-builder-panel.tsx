'use client';

import { useState } from 'react';
import { Check, ChevronsUpDown, Plus } from 'lucide-react';
import { Button } from '@riven/ui/button';
import { Popover, PopoverContent, PopoverTrigger } from '@riven/ui/popover';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@riven/ui/command';
import { cn } from '@riven/utils';
import type { EntityType } from '@/lib/types/entity';
import {
  type FilterGroupState,
  createEmptyCondition,
  createEmptyGroup,
} from './query-builder.utils';
import { FilterGroupNode } from './filter-group-node';

interface QueryBuilderPanelProps {
  entityType?: EntityType;
  entityTypes?: EntityType[];
  selectedEntityTypeId?: string;
  onEntityTypeChange?: (id: string) => void;
  filterGroup: FilterGroupState;
  onFilterGroupChange: (group: FilterGroupState) => void;
  onClearAll: () => void;
}

export function QueryBuilderPanel({
  entityType,
  entityTypes,
  selectedEntityTypeId,
  onEntityTypeChange,
  filterGroup,
  onFilterGroupChange,
  onClearAll,
}: QueryBuilderPanelProps) {
  const resolvedEntityType =
    entityType ?? entityTypes?.find((et) => et.id === selectedEntityTypeId);
  const hasConditions = filterGroup.conditions.length > 0;

  const addCondition = () => {
    onFilterGroupChange({
      ...filterGroup,
      conditions: [...filterGroup.conditions, createEmptyCondition()],
    });
  };

  const addGroup = () => {
    const nested = createEmptyGroup(filterGroup.logicalOperator === 'and' ? 'or' : 'and');
    onFilterGroupChange({
      ...filterGroup,
      conditions: [...filterGroup.conditions, nested],
    });
  };

  return (
    <div className="flex max-h-[28rem] flex-col">
      {!entityType && entityTypes && onEntityTypeChange && (
        <div className="p-3 pb-0">
          <EntityTypeSelector
            entityTypes={entityTypes}
            selectedId={selectedEntityTypeId}
            onChange={onEntityTypeChange}
          />
        </div>
      )}

      {resolvedEntityType && (
        <>
          <div className="min-h-0 flex-1 overflow-y-auto p-3">
            <FilterGroupNode
              group={filterGroup}
              entityType={resolvedEntityType}
              entityTypes={entityTypes}
              onChange={onFilterGroupChange}
              hideActions
            />
          </div>

          <div className="border-border flex items-center border-t px-3 py-2">
            <div className="flex items-center gap-2">
              <Button
                variant="ghost"
                size="xs"
                className="text-muted-foreground text-xs"
                onClick={addCondition}
              >
                <Plus className="mr-1 size-3" />
                Add condition
              </Button>
              <Button
                variant="ghost"
                size="xs"
                className="text-muted-foreground text-xs"
                onClick={addGroup}
              >
                <Plus className="mr-1 size-3" />
                Add group
              </Button>
            </div>
            {hasConditions && (
              <Button
                variant="ghost"
                size="xs"
                className="text-muted-foreground ml-auto text-xs"
                onClick={onClearAll}
              >
                Remove all
              </Button>
            )}
          </div>
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Entity type selector (when entityType prop is not provided)
// ---------------------------------------------------------------------------

function EntityTypeSelector({
  entityTypes,
  selectedId,
  onChange,
}: {
  entityTypes: EntityType[];
  selectedId?: string;
  onChange: (id: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const selected = entityTypes.find((et) => et.id === selectedId);

  return (
    <div className="flex flex-col gap-1">
      <span className="text-muted-foreground text-xs font-medium">Entity Type</span>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button
            variant="outline"
            size="sm"
            role="combobox"
            aria-expanded={open}
            className={cn(
              'h-8 w-full justify-between text-xs',
              !selected && 'text-muted-foreground',
            )}
          >
            {selected?.name?.singular ?? 'Select entity type...'}
            <ChevronsUpDown className="size-3 shrink-0 opacity-50" />
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-full p-0" align="start">
          <Command>
            <CommandInput placeholder="Search types..." className="h-8 text-xs" />
            <CommandList>
              <CommandEmpty>No entity types found.</CommandEmpty>
              <CommandGroup>
                {entityTypes.map((et) => (
                  <CommandItem
                    key={et.id}
                    value={et.name?.singular ?? et.key}
                    onSelect={() => {
                      onChange(et.id);
                      setOpen(false);
                    }}
                    className="text-xs"
                  >
                    <Check
                      className={cn(
                        'mr-1.5 size-3',
                        selectedId === et.id ? 'opacity-100' : 'opacity-0',
                      )}
                    />
                    {et.name?.singular ?? et.key}
                  </CommandItem>
                ))}
              </CommandGroup>
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
    </div>
  );
}
