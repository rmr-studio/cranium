'use client';

/**
 * DataTable - Refactored Main Component
 *
 * This is the orchestrator component that:
 * - Initializes TanStack Table instance
 * - Registers table with store
 * - Sets up DnD context
 * - Composes sub-components
 * - Manages TanStack Table configuration
 *
 * State management is handled by the DataTableProvider and store.
 * Components access state directly via hooks (no prop drilling).
 */

import { Checkbox } from '@/components/ui/checkbox';
import { Table } from '@riven/ui';
import { cn } from '@riven/utils';
import {
  closestCenter,
  DndContext,
  DragEndEvent,
  KeyboardSensor,
  Modifier,
  PointerSensor,
  UniqueIdentifier,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import { arrayMove, sortableKeyboardCoordinates } from '@dnd-kit/sortable';
import {
  AccessorKeyColumnDef,
  ColumnDef,
  getCoreRowModel,
  getFilteredRowModel,
  getSortedRowModel,
  Row,
  useReactTable,
} from '@tanstack/react-table';
import { ReactNode, useCallback, useEffect, useMemo, useRef } from 'react';
import { DataTableBody } from './components/data-table-body';
import { DataTableHeader } from './components/data-table-header';
import { DataTableSelectionBar } from './components/data-table-selection-bar';
import { DataTableToolbar } from './components/data-table-toolbar';
import { useDataTableActions, useDataTableStore, useDerivedState } from './data-table-provider';
import type {
  ColumnOrderingConfig,
  ColumnResizingConfig,
  FilterConfig,
  RowActionsConfig,
  RowSelectionConfig,
  SearchConfig,
} from './data-table.types';

// ============================================================================
// DataTable Props
// ============================================================================

export interface DataTableProps<TData, TValue> {
  columns: AccessorKeyColumnDef<TData, TValue>[];
  enableDragDrop?: boolean;
  onReorder?: (data: TData[]) => void | boolean;
  getRowId?: (row: TData, index: number) => string;
  enableSorting?: boolean;
  enableFiltering?: boolean;
  onRowClick?: (row: Row<TData>) => void;
  className?: string;
  emptyMessage?: string;
  search?: SearchConfig<TData>;
  filter?: FilterConfig<TData>;
  rowActions?: RowActionsConfig<TData>;
  columnResizing?: ColumnResizingConfig;
  columnOrdering?: ColumnOrderingConfig;
  customRowRenderer?: (row: Row<TData>) => ReactNode | null;
  addingNewEntry?: boolean;
  disableDragForRow?: (row: Row<TData>) => boolean;
  onTableReady?: (columnSizes: Record<string, number>) => void;
  rowSelection?: RowSelectionConfig<TData>;
  /** Enable inline cell editing */
  enableInlineEdit?: boolean;

  /** Callback when a cell is edited (returns true on success) */
  onCellEdit?: (row: TData, columnId: string, newValue: any, oldValue: any) => Promise<boolean>;
  /** Edit mode trigger (click or doubleClick) */
  editMode?: 'click' | 'doubleClick';
  /** Always show action handles (drag/select) even when not hovering */
  alwaysShowActionHandles?: boolean;
  defaultColumnWidth?: number;
}

export const DEFAULT_COLUMN_WIDTH = 250;

// ============================================================================
// Main Component
// ============================================================================

export function DataTable<TData, TValue>({
  columns,
  enableDragDrop = false,
  onReorder,
  getRowId,
  enableSorting = false,
  enableFiltering = false,
  onRowClick,
  className,
  emptyMessage = 'No results.',
  search,
  filter,
  rowActions,
  columnResizing,
  columnOrdering,
  customRowRenderer,
  addingNewEntry = false,
  disableDragForRow,
  onTableReady,
  rowSelection,
  enableInlineEdit = false,
  onCellEdit,
  editMode = 'click',
  defaultColumnWidth = DEFAULT_COLUMN_WIDTH,
  alwaysShowActionHandles = false,
}: DataTableProps<TData, TValue>) {
  // ========================================================================
  // Store State & Actions
  // ========================================================================

  const tableData = useDataTableStore<TData, TData[]>((state) => state.tableData);
  const sorting = useDataTableStore<TData, any>((state) => state.sorting);
  const columnFilters = useDataTableStore<TData, any>((state) => state.columnFilters);
  const globalFilter = useDataTableStore<TData, string>((state) => state.globalFilter);
  const columnSizing = useDataTableStore<TData, Record<string, number>>(
    (state) => state.columnSizing,
  );
  const columnOrder = useDataTableStore<TData, string[]>((state) => state.columnOrder);
  const rowSelectionState = useDataTableStore<TData, any>((state) => state.rowSelection);
  const activeFilterCount = useDataTableStore<TData, number>((state) =>
    state.getActiveFilterCount(),
  );

  const resizingColumnId = useDataTableStore<TData, string | null>(
    (state) => state.resizingColumnId,
  );

  const focusedCell = useDataTableStore<TData, { rowId: string; columnId: string } | null>(
    (state) => state.focusedCell,
  );
  const editingCell = useDataTableStore<TData, { rowId: string; columnId: string } | null>(
    (state) => state.editingCell,
  );
  const requestCommit = useDataTableStore<TData, () => void>((state) => state.requestCommit);

  const {
    setSorting,
    setColumnSizing,
    setColumnOrder,
    setRowSelection,
    setTableInstance,
    reorderRows,
    clearSelection,
    // Focus actions
    clearFocus,
    focusNextCell,
    focusPrevCell,
    focusAdjacentCell,
    enterEditMode,
  } = useDataTableActions<TData>();

  // Ref for table container to detect outside clicks
  const tableContainerRef = useRef<HTMLDivElement>(null);

  // Derived state
  const { isDragDropEnabled, isSelectionEnabled } = useDerivedState<TData>(
    enableDragDrop,
    rowSelection?.enabled || false,
  );

  // ========================================================================
  // Action Column (only added when drag-drop or selection is enabled)
  // ========================================================================

  const finalColumns = useMemo(() => {
    // Check if any actions are enabled
    const hasActions = enableDragDrop || isSelectionEnabled;

    // If no actions, return columns as-is
    if (!hasActions) {
      return columns;
    }

    // Calculate dynamic width based on enabled features (35px per icon)
    const ACTION_ICON_WIDTH = 35;
    let actionColumnWidth = 0;

    if (enableDragDrop) actionColumnWidth += ACTION_ICON_WIDTH;
    if (isSelectionEnabled) actionColumnWidth += ACTION_ICON_WIDTH;

    // Action Column includes drag handle and/or selection checkbox
    const actionsColumn: ColumnDef<TData, TValue> = {
      id: 'actions',
      size: actionColumnWidth,
      minSize: actionColumnWidth,
      maxSize: actionColumnWidth,
      enableResizing: false,
      enableSorting: false,
      enableHiding: false,
      header: ({ table }) => (
        <div className="flex items-center justify-center">
          {isSelectionEnabled && (
            <Checkbox
              checked={table.getIsAllPageRowsSelected()}
              onCheckedChange={(value) => table.toggleAllPageRowsSelected(!!value)}
              aria-label="Select all"
              onClick={(e) => e.stopPropagation()}
            />
          )}
        </div>
      ),
    };

    return [actionsColumn, ...columns];
  }, [columns, enableDragDrop, isSelectionEnabled]);

  // ========================================================================
  // TanStack Table Configuration
  // ========================================================================

  // Custom global filter function with nested property support
  const globalFilterFn = (row: Row<TData>, columnId: string, filterValue: string) => {
    if (!search?.enabled || !filterValue) return true;

    const searchableColumns = search.searchableColumns;
    if (!searchableColumns || searchableColumns.length === 0) return true;

    const searchLower = filterValue.toLowerCase();

    // Helper to get nested property value (e.g., "name.plural")
    const getNestedValue = (obj: any, path: string): any => {
      return path.split('.').reduce((current, prop) => current?.[prop], obj);
    };

    return searchableColumns.some((colId) => {
      const colIdStr = colId as string;
      let value: any;

      // Check if colId contains dot notation (nested property)
      if (colIdStr.includes('.')) {
        // Access nested property from row.original
        value = getNestedValue(row.original, colIdStr);
      } else {
        // Standard column access
        value = row.getValue(colIdStr);
      }

      if (value == null) return false;

      // Handle objects by searching all their string values
      if (typeof value === 'object' && !Array.isArray(value)) {
        return Object.values(value).some(
          (v) => v != null && String(v).toLowerCase().includes(searchLower),
        );
      }

      return String(value).toLowerCase().includes(searchLower);
    });
  };

  // Check if any column has explicit size defined
  const hasExplicitColumnSizes = useMemo(() => {
    return finalColumns.some((col) => col.size !== undefined);
  }, [finalColumns]);

  const table = useReactTable<TData>({
    data: tableData,
    columns: finalColumns,
    getCoreRowModel: getCoreRowModel(),
    ...(enableSorting && {
      getSortedRowModel: getSortedRowModel(),
      onSortingChange: setSorting,
    }),
    ...((enableFiltering || search?.enabled || filter?.enabled) && {
      getFilteredRowModel: getFilteredRowModel(),
      globalFilterFn,
      filterFns: {
        multiSelect: (row: Row<TData>, columnId: string, filterValue: any[]) => {
          if (!filterValue || filterValue.length === 0) return true;
          const value = row.getValue(columnId);
          return filterValue.includes(value);
        },
        numberRange: (
          row: Row<TData>,
          columnId: string,
          filterValue: { min?: number; max?: number },
        ) => {
          if (!filterValue) return true;
          const value = row.getValue(columnId) as number;
          if (filterValue.min !== undefined && value < filterValue.min) return false;
          if (filterValue.max !== undefined && value > filterValue.max) return false;
          return true;
        },
      },
    }),
    // Always set defaultColumn for sizing, but only enable resize interactions if columnResizing is enabled
    defaultColumn: {
      size: defaultColumnWidth,
      minSize: 50,
      maxSize: 500,
    },
    ...(columnResizing?.enabled && {
      columnResizeMode: columnResizing.columnResizeMode ?? 'onEnd',
      onColumnSizingChange: setColumnSizing,
    }),
    ...(columnOrdering?.enabled && {
      onColumnOrderChange: setColumnOrder,
    }),
    ...(isSelectionEnabled && {
      enableRowSelection: true,
      onRowSelectionChange: setRowSelection,
    }),
    getRowId: getRowId,
    state: {
      ...(enableSorting && { sorting }),
      ...((enableFiltering || search?.enabled || filter?.enabled) && { columnFilters }),
      ...(search?.enabled && { globalFilter }),
      ...(columnResizing?.enabled && { columnSizing }),
      ...(columnOrdering?.enabled && { columnOrder }),
      ...(isSelectionEnabled && { rowSelection: rowSelectionState }),
    },
  } as any);

  // ========================================================================
  // Register Table Instance with Store
  // ========================================================================

  useEffect(() => {
    setTableInstance(table);
  }, [table, setTableInstance]);

  // ========================================================================
  // Notify Parent on Table Ready
  // ========================================================================

  const hasNotifiedRef = useRef(false);
  useEffect(() => {
    if (!onTableReady || hasNotifiedRef.current) return;

    if (tableData.length === 0) return;

    const sizes: Record<string, number> = {};
    table.getAllLeafColumns().forEach((column) => {
      sizes[column.id] = column.getSize();
    });

    onTableReady(sizes);
    hasNotifiedRef.current = true;
  }, [tableData.length, table, onTableReady]);

  // ========================================================================
  // Clear Selections on Filter Change
  // ========================================================================

  useEffect(() => {
    if (!rowSelection?.enabled) return;
    if (rowSelection.clearOnFilterChange === false) return;

    if (globalFilter || activeFilterCount > 0) {
      clearSelection();
    }
  }, [globalFilter, activeFilterCount, rowSelection, clearSelection]);

  // ========================================================================
  // Clear Focus When Focused Row No Longer Exists
  // ========================================================================

  useEffect(() => {
    if (!focusedCell) return;

    const rows = table.getRowModel().rows;
    const rowExists = rows.some((r) => r.id === focusedCell.rowId);

    if (!rowExists) {
      clearFocus();
    }
  }, [table, focusedCell, clearFocus]);

  // ========================================================================
  // Handle Click Outside Editing Cell
  // ========================================================================

  const handleClickOutside = useCallback(
    (event: MouseEvent) => {
      // Only handle if we have focus or edit state
      if (!editingCell && !focusedCell) return;

      const target = event.target as HTMLElement;

      // Check if click is inside the table container
      if (tableContainerRef.current?.contains(target)) {
        // Click is inside table - let the cell click handlers manage this
        // (startEditing auto-commits previous cell)
        return;
      }

      // Check if click is inside a portal (select dropdowns, popovers, etc.)
      // These are rendered outside the table but are still part of the editing context
      const isInsidePortal =
        target.closest('[data-radix-popper-content-wrapper]') ||
        target.closest('[data-radix-select-viewport]') ||
        target.closest('[data-radix-menu-content]') ||
        target.closest('[data-radix-popover-content]') ||
        target.closest("[role='listbox']") ||
        target.closest("[role='dialog']");

      if (isInsidePortal) {
        return;
      }

      // Click is outside the table and not in a portal
      if (editingCell) {
        // Commit edit and clear focus
        requestCommit();
      }
      // Clear focus when clicking outside table
      clearFocus();
    },
    [editingCell, focusedCell, requestCommit, clearFocus],
  );

  useEffect(() => {
    // Attach when editing or focused
    if (!editingCell && !focusedCell) return;

    // Use mousedown to catch the click before focus changes
    document.addEventListener('mousedown', handleClickOutside);

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [editingCell, focusedCell, handleClickOutside]);

  // ========================================================================
  // Table-Level Keyboard Navigation (when focused but not editing)
  // ========================================================================

  const handleTableKeyDown = useCallback(
    (event: KeyboardEvent) => {
      // Only handle when focused but not editing
      if (!focusedCell || editingCell) return;

      switch (event.key) {
        case 'Enter':
          event.preventDefault();
          enterEditMode();
          break;
        case 'Escape':
          event.preventDefault();
          clearFocus();
          break;
        case 'Tab':
          event.preventDefault();
          if (event.shiftKey) {
            focusPrevCell();
          } else {
            focusNextCell();
          }
          break;
        case 'ArrowUp':
          event.preventDefault();
          focusAdjacentCell('up');
          break;
        case 'ArrowDown':
          event.preventDefault();
          focusAdjacentCell('down');
          break;
        case 'ArrowLeft':
          event.preventDefault();
          focusAdjacentCell('left');
          break;
        case 'ArrowRight':
          event.preventDefault();
          focusAdjacentCell('right');
          break;
      }
    },
    [
      focusedCell,
      editingCell,
      enterEditMode,
      clearFocus,
      focusNextCell,
      focusPrevCell,
      focusAdjacentCell,
    ],
  );

  useEffect(() => {
    // Only attach keyboard handler when focused but not editing
    if (!focusedCell || editingCell) return;

    document.addEventListener('keydown', handleTableKeyDown);
    return () => {
      document.removeEventListener('keydown', handleTableKeyDown);
    };
  }, [focusedCell, editingCell, handleTableKeyDown]);

  // ========================================================================
  // DnD Sensors
  // ========================================================================

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 8,
      },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  );

  const columnIds = useMemo(() => {
    return table.getAllLeafColumns().map((col) => col.id as UniqueIdentifier);
  }, [table]);

  const rowIds = useMemo(() => {
    return table.getRowModel().rows.map((row) => row.id as UniqueIdentifier);
  }, [table, tableData]);

  // Column IDs excluding fixed columns (actions) for reordering
  const sortableColumnIds = useMemo(() => {
    return table
      .getAllLeafColumns()
      .filter((col) => col.id !== 'actions')
      .map((col) => col.id as UniqueIdentifier);
  }, [table]);

  const handleDragEnd = (event: DragEndEvent) => {
    if (resizingColumnId) return;

    const { active, over } = event;

    if (!over || active.id === over.id) return;

    const isColumnDrag = columnIds.includes(active.id);

    if (isColumnDrag) {
      // Skip if trying to drag the actions column (defensive check)
      if (active.id === 'actions' || over.id === 'actions') return;

      // Handle column reordering (only for sortable columns)
      const oldIndex = sortableColumnIds.indexOf(active.id);
      const newIndex = sortableColumnIds.indexOf(over.id);

      if (oldIndex === -1 || newIndex === -1) return;

      const reorderedColumns = arrayMove(sortableColumnIds as string[], oldIndex, newIndex);

      // Always keep 'actions' column first if it exists
      const hasActionsColumn = columnIds.includes('actions');
      const newColumnOrder = hasActionsColumn ? ['actions', ...reorderedColumns] : reorderedColumns;

      setColumnOrder(newColumnOrder);
    } else {
      // Handle row reordering
      const oldIndex = rowIds.indexOf(active.id);
      const newIndex = rowIds.indexOf(over.id);

      // Call onReorder callback if provided
      if (onReorder) {
        const newData = arrayMove([...tableData], oldIndex, newIndex);
        const result = onReorder(newData);

        // Only update store if not explicitly rejected
        if (result !== false) {
          reorderRows(oldIndex, newIndex);
        }
      } else {
        reorderRows(oldIndex, newIndex);
      }
    }
  };

  // ========================================================================
  // Render
  // ========================================================================

  const tableContent = (
    <div ref={tableContainerRef} className={cn('relative overflow-auto rounded-t-md')}>
      <Table className={cn((columnResizing?.enabled || hasExplicitColumnSizes) && 'table-fixed')}>
        <DataTableHeader
          table={table}
          enableColumnOrdering={columnOrdering?.enabled ?? false}
          columnResizing={columnResizing}
          rowActions={rowActions}
          addingNewEntry={addingNewEntry}
        />
        <DataTableBody
          table={table}
          enableDragDrop={isDragDropEnabled}
          isSelectionEnabled={isSelectionEnabled}
          onRowClick={onRowClick}
          rowActions={rowActions}
          columnResizing={columnResizing}
          customRowRenderer={customRowRenderer}
          addingNewEntry={addingNewEntry}
          disableDragForRow={disableDragForRow}
          emptyMessage={emptyMessage}
          finalColumnsCount={finalColumns.length}
          enableInlineEdit={enableInlineEdit}
          focusedCell={focusedCell}
          alwaysShowActionHandles={alwaysShowActionHandles}
        />
      </Table>
    </div>
  );

  // Custom modifier that applies axis restriction based on what's being dragged
  // - Column headers: restrict to horizontal axis (y: 0)
  // - Rows: restrict to vertical axis (x: 0)
  const axisRestrictionModifier: Modifier = useCallback(
    ({ transform, active }) => {
      if (!active) return transform;

      const isColumnDrag = columnIds.includes(active.id);

      if (isColumnDrag) {
        // Column ordering: horizontal only
        return { ...transform, y: 0 };
      } else {
        // Row drag-drop: vertical only
        return { ...transform, x: 0 };
      }
    },
    [columnIds],
  );

  const wrappedContent =
    isDragDropEnabled || columnOrdering?.enabled ? (
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        onDragEnd={handleDragEnd}
        modifiers={[axisRestrictionModifier]}
      >
        {tableContent}
      </DndContext>
    ) : (
      tableContent
    );

  return (
    <div className={cn('relative min-w-0 space-y-4', className)}>
      {/* Selection Action Bar */}
      <DataTableSelectionBar actionComponent={rowSelection?.actionComponent} />

      {/* Toolbar */}
      <DataTableToolbar search={search} filter={filter} />

      {/* Table */}
      {wrappedContent}
    </div>
  );
}
