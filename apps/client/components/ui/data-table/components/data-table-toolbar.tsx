'use client';

import { Filter } from 'lucide-react';
import { Badge, Button, Popover, PopoverContent, PopoverTrigger } from '@riven/ui';
import { DataTableSearchInput } from './data-table-search-input';
import { DataTableFilterButton } from './data-table-filter-button';
import { useDataTableStore, useDataTableActions } from '../data-table-provider';
import type { SearchConfig, FilterConfig } from '../data-table.types';

interface DataTableToolbarProps<TData> {
  search?: SearchConfig<TData>;
  filter?: FilterConfig<TData>;
}

export function DataTableToolbar<TData>({ search, filter }: DataTableToolbarProps<TData>) {
  const activeFilterCount = useDataTableStore<TData, number>((state) =>
    state.getActiveFilterCount(),
  );
  const filterPopoverOpen = useDataTableStore<TData, boolean>((state) => state.filterPopoverOpen);
  const { setFilterPopoverOpen } = useDataTableActions<TData>();

  const showSearch = search?.enabled;
  const showFilter = filter?.enabled && filter.filters.length > 0;

  if (!showSearch && !showFilter) {
    return null;
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      {/* Search Input */}
      {showSearch && <DataTableSearchInput config={search} />}

      {/* Filter Button */}
      {showFilter && (
        <Popover open={filterPopoverOpen} onOpenChange={setFilterPopoverOpen}>
          <PopoverTrigger asChild>
            <Button variant="outline" size="sm" className="h-9" disabled={filter.disabled}>
              <Filter className="mr-2 h-4 w-4" />
              Filters
              {activeFilterCount > 0 && (
                <Badge variant="secondary" className="ml-2 h-5 px-1.5">
                  {activeFilterCount}
                </Badge>
              )}
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-80 p-0" align="end">
            <DataTableFilterButton config={filter} />
          </PopoverContent>
        </Popover>
      )}
    </div>
  );
}
