'use client';

import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { TableBody, TableCell, TableRow } from '@riven/ui/table';
import { Row, Table as TanStackTable } from '@tanstack/react-table';
import React, { ReactNode, useMemo } from 'react';
import { useDataTableStore } from '../data-table-provider';
import type {
  ActionColumnConfig,
  ColumnResizingConfig,
  RowActionsConfig,
} from '../data-table.types';
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
  actionColumnConfig?: ActionColumnConfig;
  /** Whether the header has an end-of-header content column (needs matching empty td) */
  hasEndOfHeaderContent?: boolean;
  /** Whether the header has a row actions column (needs matching td) */
  hasRowActions?: boolean;
  /** External selection override: determines if a row is selected */
  getIsRowSelected?: (rowId: string) => boolean;
  /** External selection override: called when a row's selection is toggled */
  onRowToggle?: (rowId: string) => void;
}

function DataTableBodyComponent<TData>({
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
  actionColumnConfig,
  hasEndOfHeaderContent = false,
  hasRowActions = false,
  getIsRowSelected,
  onRowToggle,
}: DataTableBodyProps<TData>) {
  const tableData = useDataTableStore<TData, TData[]>((state) => state.tableData);

  const rowIds = useMemo(() => {
    return table.getRowModel().rows.map((row) => row.id);
  }, [table, tableData]);

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

  // Total columns including extra header-only columns (for colSpan on empty state)
  const totalColSpan =
    finalColumnsCount + (hasRowActions ? 1 : 0) + (hasEndOfHeaderContent ? 1 : 0);

  if (!rows?.length) {
    return (
      <TableBody>
        <TableRow>
          <TableCell colSpan={totalColSpan} className="h-24 text-center text-muted-foreground">
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
        const isSelected = getIsRowSelected ? getIsRowSelected(row.id) : row.getIsSelected();

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
            isSelected={isSelected}
            onToggleSelected={
              onRowToggle ? () => onRowToggle(row.id) : (value) => row.toggleSelected(value)
            }
            enableInlineEdit={enableInlineEdit}
            focusedCell={focusedCell}
            actionColumnConfig={actionColumnConfig}
            hasEndOfHeaderContent={hasEndOfHeaderContent}
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

export const DataTableBody = React.memo(DataTableBodyComponent) as typeof DataTableBodyComponent;
