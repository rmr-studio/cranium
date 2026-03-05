'use client';

import { Input } from '@riven/ui';
import { Search, X } from 'lucide-react';
import { useEffect } from 'react';
import { useDataTableSearch } from '../data-table-provider';
import type { SearchConfig } from '../data-table.types';

interface DataTableSearchInputProps<TData> {
  config: SearchConfig<TData>;
}

export function DataTableSearchInput<TData>({ config }: DataTableSearchInputProps<TData>) {
  const { searchValue, setSearchValue, setGlobalFilter, clearSearch, table } = useDataTableSearch();

  // Debounce search value to global filter
  useEffect(() => {
    const debounceMs = config.debounceMs ?? 300;
    const timer = setTimeout(() => {
      setGlobalFilter(searchValue);
      config.onSearchChange?.(searchValue);
    }, debounceMs);

    return () => clearTimeout(timer);
  }, [searchValue, config.debounceMs, setGlobalFilter, config]);

  const resultCount = table ? table.getFilteredRowModel().rows.length : 0;

  return (
    <div className="flex min-w-[200px] flex-1 items-center gap-2">
      <div className="relative max-w-sm flex-1">
        <Search className="absolute top-1/2 left-2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          placeholder={config.placeholder ?? 'Search...'}
          value={searchValue}
          onChange={(e) => setSearchValue(e.target.value)}
          className="h-9 pr-8 pl-8"
          disabled={config.disabled}
        />
        {searchValue && !config.disabled && (
          <button
            onClick={clearSearch}
            className="absolute top-1/2 right-2 -translate-y-1/2 text-muted-foreground transition-colors hover:text-foreground"
          >
            <X className="h-4 w-4" />
          </button>
        )}
      </div>
      {searchValue && (
        <p className="text-sm whitespace-nowrap text-muted-foreground">
          {resultCount} result{resultCount !== 1 ? 's' : ''}
        </p>
      )}
    </div>
  );
}
