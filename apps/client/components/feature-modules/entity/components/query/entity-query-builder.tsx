'use client';

import { Badge } from '@/components/ui/badge';
import type { EntityType } from '@/lib/types/entity';
import type { QueryFilter } from '@/lib/types/models/QueryFilter';
import { Button } from '@riven/ui/button';
import { Popover, PopoverContent, PopoverTrigger } from '@riven/ui/popover';
import { cn } from '@riven/utils';
import { Filter } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { QueryBuilderPanel } from './query-builder-panel';
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

  // Sync from external value changes
  const prevValueRef = useRef(value);
  useEffect(() => {
    if (value !== prevValueRef.current) {
      prevValueRef.current = value;
      setFilterGroup(value ? queryFilterToFilterGroupState(value) : createDefaultGroup());
    }
  }, [value]);

  const activeCount = countActiveConditions(filterGroup);

  const handleFilterGroupChange = useCallback(
    (group: FilterGroupState) => {
      setFilterGroup(group);
      const filter = filterGroupStateToQueryFilter(group);
      onChange(filter, selectedEntityTypeId);
    },
    [onChange, selectedEntityTypeId],
  );

  const handleEntityTypeChange = useCallback(
    (id: string) => {
      setSelectedEntityTypeId(id);
      setFilterGroup(createDefaultGroup());
      onChange(undefined, id);
    },
    [onChange],
  );

  const handleClearAll = useCallback(() => {
    setFilterGroup(createDefaultGroup());
    onChange(undefined, selectedEntityTypeId);
  }, [onChange, selectedEntityTypeId]);

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
