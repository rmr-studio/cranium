'use client';

import { TableHead, TableHeader, TableRow } from '@riven/ui/table';
import { cn } from '@riven/utils';
import { SortableContext, horizontalListSortingStrategy } from '@dnd-kit/sortable';
import { Table as TanStackTable, flexRender } from '@tanstack/react-table';
import { ReactNode, useMemo } from 'react';
import { useDataTableStore } from '../data-table-provider';
import type { ColumnResizingConfig, RowActionsConfig } from '../data-table.types';
import { DraggableColumnHeader } from './draggable-column-header';

interface DataTableHeaderProps<TData> {
  table: TanStackTable<TData>;
  enableColumnOrdering: boolean;
  columnResizing?: ColumnResizingConfig;
  rowActions?: RowActionsConfig<TData>;
  addingNewEntry: boolean;
  onHeaderClick?: (columnId: string, anchorEl: HTMLElement) => void;
  endOfHeaderContent?: ReactNode;
}

export function DataTableHeader<TData>({
  table,
  enableColumnOrdering,
  columnResizing,
  rowActions,
  addingNewEntry,
  onHeaderClick,
  endOfHeaderContent,
}: DataTableHeaderProps<TData>) {
  const isMounted = useDataTableStore<TData, boolean>((state) => state.isMounted);

  // Get all column IDs for sortable context
  // Exclude 'actions' column as it should stay fixed at the left
  const sortableColumnIds = useMemo(() => {
    return table
      .getAllLeafColumns()
      .filter((col) => col.id !== 'actions')
      .map((col) => col.id);
  }, [table]);

  return (
    <TableHeader className="sticky top-0 z-10 bg-background">
      {table.getHeaderGroups().map((headerGroup) => (
        <TableRow key={headerGroup.id}>
          {/* Column headers */}
          {enableColumnOrdering && isMounted ? (
            <SortableContext items={sortableColumnIds} strategy={horizontalListSortingStrategy}>
              {headerGroup.headers.map((header) => (
                <DraggableColumnHeader
                  key={header.id}
                  header={header}
                  enableColumnOrdering={enableColumnOrdering}
                  columnResizing={columnResizing}
                  addingNewEntry={addingNewEntry}
                  onHeaderClick={onHeaderClick}
                />
              ))}
            </SortableContext>
          ) : (
            headerGroup.headers.map((header) => {
              return (
                <TableHead
                  key={header.id}
                  className={cn('relative border-l px-3 py-2 first:border-l-transparent')}
                  style={{
                    width: `${header.getSize()}px`,
                  }}
                >
                  <div className="flex items-center justify-between">
                    {header.isPlaceholder
                      ? null
                      : flexRender(header.column.columnDef.header, header.getContext())}
                  </div>

                  {columnResizing?.enabled && header.column.getCanResize() && !addingNewEntry && (
                    <div
                      onMouseDown={header.getResizeHandler()}
                      onTouchStart={header.getResizeHandler()}
                      className="group/resizer absolute top-0 right-0 z-10 h-full w-3 cursor-col-resize select-none"
                      onClick={(e) => e.stopPropagation()}
                    >
                      <div
                        className={`absolute top-0 right-0 h-full w-[1px] bg-transparent transition-all duration-150 group-hover/resizer:w-[2px] group-hover/resizer:bg-blue-500 group-active/resizer:bg-blue-600 ${
                          header.column.getIsResizing() && 'w-[2px] bg-blue-600'
                        }`}
                      />
                    </div>
                  )}
                </TableHead>
              );
            })
          )}

          {/* Row actions column header */}
          {rowActions?.enabled && (
            <TableHead className="w-[50px]">
              <span className="sr-only">Actions</span>
            </TableHead>
          )}

          {/* End-of-header content (e.g. add property, visibility buttons) */}
          {endOfHeaderContent && (
            <TableHead className="w-auto border-l px-1">
              {endOfHeaderContent}
            </TableHead>
          )}
        </TableRow>
      ))}
    </TableHeader>
  );
}
