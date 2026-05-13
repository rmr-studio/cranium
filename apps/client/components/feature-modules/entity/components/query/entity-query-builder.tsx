'use client';

import { Badge } from '@/components/ui/badge';
import type { EntityType, QueryFilter } from '@/lib/types/entity';
import { Button } from '@cranium/ui/button';
import { Popover, PopoverContent, PopoverTrigger } from '@cranium/ui/popover';
import { cn } from '@cranium/utils';
import { Filter } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { QueryBuilderPanel } from './query-builder-panel';
import { debounce } from '@/lib/util/debounce.util';
import {
  type FilterGroupState,
  countActiveConditions,
  createEmptyCondition,
  createEmptyGroup,
  filterGroupStateToQueryFilter,
  queryFilterToFilterGroupState,
} from './query-builder.utils';

export interface EntityQueryBuilderProps {
  entityType?: EntityType;
  entityTypes?: EntityType[];
  value?: QueryFilter;
  onChange: (filter: QueryFilter | undefined, entityTypeId?: string) => void;
  className?: string;
}

function createDefaultGroup(): FilterGroupState {
  const group = createEmptyGroup();
  group.conditions = [createEmptyCondition()];
  return group;
}

export function EntityQueryBuilder({
  entityType,
  entityTypes,
  value,
  onChange,
  className,
}: EntityQueryBuilderProps) {
  const [open, setOpen] = useState(false);
  const [selectedEntityTypeId, setSelectedEntityTypeId] = useState<string | undefined>(
    entityType?.id,
  );
  const [filterGroup, setFilterGroup] = useState<FilterGroupState>(() =>
    value ? queryFilterToFilterGroupState(value) : createDefaultGroup(),
  );

  const activeCount = countActiveConditions(filterGroup);

  const debouncedOnChange = useMemo(
    () =>
      debounce((filter: QueryFilter | undefined, entityTypeId: string | undefined) => {
        onChange(filter, entityTypeId);
      }, 300),
    [onChange],
  );

  // Clean up debounce timer on unmount
  useEffect(() => {
    return () => debouncedOnChange.cancel();
  }, [debouncedOnChange]);

  const handleFilterGroupChange = useCallback(
    (group: FilterGroupState) => {
      setFilterGroup(group);
      const filter = filterGroupStateToQueryFilter(group);
      debouncedOnChange(filter, selectedEntityTypeId);
    },
    [debouncedOnChange, selectedEntityTypeId],
  );

  const handleEntityTypeChange = useCallback(
    (id: string) => {
      debouncedOnChange.cancel();
      setSelectedEntityTypeId(id);
      setFilterGroup(createDefaultGroup());
      onChange(undefined, id);
    },
    [debouncedOnChange, onChange],
  );

  const handleClearAll = useCallback(() => {
    debouncedOnChange.cancel();
    setFilterGroup(createDefaultGroup());
    onChange(undefined, selectedEntityTypeId);
  }, [debouncedOnChange, onChange, selectedEntityTypeId]);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="outline" size="sm" className={cn('gap-1.5', className)}>
          <Filter className="size-3.5" />
          Filter
          {activeCount > 0 && (
            <Badge variant="secondary" className="ml-0.5 h-5 min-w-5 px-1.5 text-xs">
              {activeCount}
            </Badge>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[560px] overflow-hidden p-0" align="end" sideOffset={4}>
        <QueryBuilderPanel
          entityType={entityType}
          entityTypes={entityTypes}
          selectedEntityTypeId={selectedEntityTypeId}
          onEntityTypeChange={!entityType ? handleEntityTypeChange : undefined}
          filterGroup={filterGroup}
          onFilterGroupChange={handleFilterGroupChange}
          onClearAll={handleClearAll}
        />
      </PopoverContent>
    </Popover>
  );
}
