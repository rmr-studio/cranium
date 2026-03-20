'use client';

import { TableCell, TableRow } from '@riven/ui/table';
import { cn } from '@riven/utils';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { Row, flexRender } from '@tanstack/react-table';
import React from 'react';
import { useCellInteraction, useDataTableActions, useDataTableStore } from '../data-table-provider';
import type { ActionColumnConfig, ColumnResizingConfig, RowActionsConfig } from '../data-table.types';
import { isEditableColumn } from '../data-table.types';
import { ActionCell } from './action-cell';
import { EditableCell } from './cells/editable-cell';
import { RowActionsMenu } from './row-actions-menu';

interface DraggableRowProps<TData> {
  row: Row<TData>;
  enableDragDrop: boolean;
  onRowClick?: (row: Row<TData>) => void;
  rowActions?: RowActionsConfig<TData>;
  columnResizing?: ColumnResizingConfig;
  disabled?: boolean;
  disableDragForRow?: (row: Row<TData>) => boolean;
  enableInlineEdit?: boolean;
  isSelectionEnabled: boolean;
  focusedCell?: { rowId: string; columnId: string } | null;
  actionColumnConfig?: ActionColumnConfig;
  /** Render an empty trailing td to match the endOfHeaderContent th */
  hasEndOfHeaderContent?: boolean;
}

function DraggableRowComponent<TData>({
  row,
  enableDragDrop,
  onRowClick,
  rowActions,
  columnResizing,
  disabled,
  disableDragForRow,
  isSelectionEnabled,
  enableInlineEdit,
  focusedCell,
  actionColumnConfig,
  hasEndOfHeaderContent = false,
}: DraggableRowProps<TData>) {
  const isMounted = useDataTableStore<TData, boolean>((state) => state.isMounted);

  const { setFocusedCell, focusNextCell, focusPrevCell } = useDataTableActions<TData>();

  const { exitToFocused, commitEdit, startEditing, editingCell, updatePendingValue } =
    useCellInteraction();

  const isDragDisabled = disableDragForRow?.(row) ?? false;

  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: row.id,
    disabled: !enableDragDrop || !isMounted || isDragDisabled,
  });

  const style = isMounted
    ? {
        transform: CSS.Transform.toString(transform),
        transition,
      }
    : undefined;

  return (
    <TableRow
      ref={enableDragDrop && isMounted ? setNodeRef : undefined}
      style={style}
      data-state={row.getIsSelected() ? 'selected' : undefined}
      className={cn(
        'group/row',
        isDragging && 'opacity-0',
        onRowClick && !disabled && 'cursor-pointer',
        disabled && 'pointer-events-none opacity-40',
      )}
      onClick={() => !disabled && onRowClick?.(row)}
    >
      {/* Data cells (includes selection checkbox if enabled) */}
      {row.getVisibleCells().map((cell) => {
        // Special handling for action column
        if (cell.column.id === 'actions') {
          return (
            <ActionCell<TData>
              key={cell.id}
              isSelected={row.getIsSelected()}
              onToggleSelected={(value) => row.toggleSelected(value)}
              enableDragDrop={enableDragDrop}
              isSelectionEnabled={isSelectionEnabled}
              isDragDisabled={isDragDisabled}
              isMounted={isMounted}
              dragAttributes={attributes}
              dragListeners={listeners}
              cellSize={cell.column.getSize()}
              actionColumnConfig={actionColumnConfig}
              extra={actionColumnConfig?.renderExtra?.(row)}
            />
          );
        }

        // Check if this cell is being edited
        const isEditing = editingCell?.rowId === row.id && editingCell?.columnId === cell.column.id;

        // Check if this cell is focused (but not editing)
        const isFocused =
          focusedCell?.rowId === row.id && focusedCell?.columnId === cell.column.id && !isEditing;

        // Check if this cell is editable using type guard
        const isEditable =
          isEditableColumn(cell.column.columnDef.meta) && enableInlineEdit && !disabled;

        // Handle cell click with focus state awareness
        const handleCellClick = () => {
          if (!isEditable) return;

          if (isEditing) {
            // Already editing this cell - do nothing
            return;
          }

          if (isFocused) {
            // Focused → enter edit mode
            startEditing(row.id, cell.column.id, cell.getValue());
          } else if (editingCell) {
            // Editing different cell → save, then focus (no edit)
            commitEdit().then(() => setFocusedCell(row.id, cell.column.id));
          } else if (focusedCell) {
            // Focused on different cell → move focus
            setFocusedCell(row.id, cell.column.id);
          } else {
            // No focus/edit → enter edit mode directly
            startEditing(row.id, cell.column.id, cell.getValue());
          }
        };

        return (
          <TableCell
            onClick={handleCellClick}
            key={cell.id}
            className={cn(
              'border-l border-l-accent/40 first:border-l-transparent',
              // Focus styling - blue ring and subtle background
              isFocused && 'bg-blue-50 ring-2 ring-blue-500 ring-inset dark:bg-blue-500/5',
            )}
            style={{
              width: `${cell.column.getSize()}px`,
              maxWidth: `${cell.column.getSize()}px`,
            }}
          >
            {isEditing ? (
              <EditableCell
                cell={cell}
                onSave={async (value) => {
                  // Update pending value before commit so store has the latest value
                  updatePendingValue(value);
                  await commitEdit();
                }}
                onCancel={exitToFocused}
                onFocusNext={focusNextCell}
                onFocusPrev={focusPrevCell}
              />
            ) : (
              <div
                className={cn(
                  // Ensure div takes full cell space
                  columnResizing?.enabled && 'overflow-x-auto',
                )}
              >
                <div className="overflow-hidden text-ellipsis">
                  {flexRender(cell.column.columnDef.cell, cell.getContext())}
                </div>
              </div>
            )}
          </TableCell>
        );
      })}
      {/* Row actions column */}
      {rowActions?.enabled && (
        <TableCell className="w-[50px] p-2">
          <RowActionsMenu row={row.original} config={rowActions} />
        </TableCell>
      )}
      {/* Empty cell to match endOfHeaderContent column */}
      {hasEndOfHeaderContent && <TableCell />}
    </TableRow>
  );
}

// Memoize to prevent unnecessary re-renders
export const DraggableRow = React.memo(DraggableRowComponent, (prevProps, nextProps) => {
  // Re-render only if row ID, disabled state, focus state, or config changes
  return (
    prevProps.row.id === nextProps.row.id &&
    prevProps.row.original === nextProps.row.original &&
    prevProps.disabled === nextProps.disabled &&
    prevProps.enableDragDrop === nextProps.enableDragDrop &&
    prevProps.isSelectionEnabled === nextProps.isSelectionEnabled &&
    prevProps.focusedCell?.rowId === nextProps.focusedCell?.rowId &&
    prevProps.focusedCell?.columnId === nextProps.focusedCell?.columnId &&
    prevProps.hasEndOfHeaderContent === nextProps.hasEndOfHeaderContent &&
    prevProps.actionColumnConfig === nextProps.actionColumnConfig &&
    prevProps.enableInlineEdit === nextProps.enableInlineEdit
  );
}) as typeof DraggableRowComponent;
