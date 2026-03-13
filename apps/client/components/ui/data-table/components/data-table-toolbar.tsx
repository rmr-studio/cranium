'use client';

import type { ReactNode } from 'react';
import { DataTableSearchInput } from './data-table-search-input';
import type { SearchConfig } from '../data-table.types';

interface DataTableToolbarProps<TData> {
  search?: SearchConfig<TData>;
  /** Custom filter UI provided by each consumer */
  filterContent?: ReactNode;
  actions?: ReactNode;
}

export function DataTableToolbar<TData>({ search, filterContent, actions }: DataTableToolbarProps<TData>) {
  const showSearch = search?.enabled;

  if (!showSearch && !filterContent && !actions) {
    return null;
  }

  return (
    <div className="flex items-center gap-2">
      {/* Search Input (left) */}
      {showSearch && <DataTableSearchInput config={search} />}

      {/* Right-side actions */}
      <div className="ml-auto flex items-center gap-2">
        {/* Custom filter content */}
        {filterContent}

        {/* Extra toolbar actions */}
        {actions}
      </div>
    </div>
  );
}
