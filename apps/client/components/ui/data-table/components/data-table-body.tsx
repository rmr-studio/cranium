'use client';

import { TableBody, TableCell, TableRow } from '@riven/ui/table';
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { Row, Table as TanStackTable } from '@tanstack/react-table';
import { ReactNode, useMemo } from 'react';
import type { ColumnResizingConfig, RowActionsConfig } from '../data-table.types';
import { DraggableRow } from './draggable-row';

interface DataTableBodyProps<TData> {
  table: TanStackTable<TData>;
  enableDragDrop: boolean;
  isSelectionEnabled: boolean;
  onRowClick?: (row: Row<TData>) => void;
  rowActions?: RowActionsConfig<TData>;
  columnResizing?: ColumnResizingConfig;
  customRowRenderer?: (row: Row<TData>) => ReactNode | null;
  addingNewEntry: boolean;
  disableDragForRow?: (row: Row<TData>) => boolean;
  emptyMessage?: string;
  finalColumnsCount: number;
  enableInlineEdit?: boolean;
  focusedCell?: { rowId: string; columnId: string } | null;
  alwaysShowActionHandles?: boolean;
}

export function DataTableBody<TData>({
  table,
  enableDragDrop,
  isSelectionEnabled,
  onRowClick,
  rowActions,
  columnResizing,
  customRowRenderer,
  addingNewEntry,
  disableDragForRow,
  emptyMessage = 'No results.',
  finalColumnsCount,
  enableInlineEdit,
  focusedCell,
  alwaysShowActionHandles = false,
}: DataTableBodyProps<TData>) {
  const rowIds = useMemo(() => {
    return table.getRowModel().rows.map((row) => row.id);
  }, [table]);

  // Filter out disabled rows from sortable context to keep them fixed in position
  const sortableRowIds = useMemo(() => {
    if (!disableDragForRow) return rowIds;

    const rows = table.getRowModel().rows;
    return rowIds.filter((rowId) => {
      const row = rows.find((r) => r.id === rowId);
      return row ? !disableDragForRow(row) : true;
    });
  }, [rowIds, table, disableDragForRow]);

  const rows = table.getRowModel().rows;

  if (!rows?.length) {
    return (
      <TableBody>
        <TableRow>
          <TableCell colSpan={finalColumnsCount} className="h-24 text-center text-muted-foreground">
            {emptyMessage}
          </TableCell>
        </TableRow>
      </TableBody>
    );
  }

  const content = (
    <TableBody>
      {rows.map((row) => {
        // Check for custom row renderer
        const customRow = customRowRenderer?.(row);
        if (customRow) {
          return customRow;
        }

        // Default rendering
        return (
          <DraggableRow
            key={row.id}
            row={row}
            enableDragDrop={enableDragDrop}
            onRowClick={onRowClick}
            rowActions={rowActions}
            columnResizing={columnResizing}
            disabled={addingNewEntry}
            disableDragForRow={disableDragForRow}
            isSelectionEnabled={isSelectionEnabled}
            enableInlineEdit={enableInlineEdit}
            focusedCell={focusedCell}
            alwaysShowActionHandles={alwaysShowActionHandles}
          />
        );
      })}
    </TableBody>
  );

  // Wrap in SortableContext if drag-drop is enabled
  if (enableDragDrop) {
    return (
      <SortableContext items={sortableRowIds} strategy={verticalListSortingStrategy}>
        {content}
      </SortableContext>
    );
  }

  return content;
}
