/**
 * DataTable Zustand Store
 *
 * Manages all client-side state for the data table component:
 * - Data (synced from TanStack Query)
 * - Sorting, filtering, search
 * - Row selection
 * - Column sizing and ordering
 * - UI state (hover, popovers, mounted)
 * - Drag and drop state
 *
 * Architecture:
 * - Single store with logical slices
 * - Factory function creates scoped instances
 * - subscribeWithSelector for fine-grained updates
 * - Integrates with TanStack React Table instance
 */

import { ColumnFiltersState, RowSelectionState, SortingState, Table } from '@tanstack/react-table';
import { toast } from 'sonner';
import { create } from 'zustand';
import { subscribeWithSelector } from 'zustand/middleware';
import { isEditableColumn } from './data-table.types';

// ============================================================================
// State Interfaces (organized by concern)
// ============================================================================

interface DataState<TData> {
  /** Current table data (synced from props) */
  tableData: TData[];
  /** TanStack Table instance (set after initialization) */
  tableInstance: Table<TData> | null;
}

interface SortingSliceState {
  /** TanStack Table sorting state */
  sorting: SortingState;
}

interface FilteringSliceState {
  /** Column-specific filters (TanStack Table format) */
  columnFilters: ColumnFiltersState;
  /** Global search filter value */
  globalFilter: string;
  /** Search input value (before debounce) */
  searchValue: string;
  /** Active filter values by column ID */
  activeFilters: Record<string, any>;
  /** Set of enabled filter column IDs */
  enabledFilters: Set<string>;
}

interface SelectionSliceState {
  /** Row selection state (TanStack Table format) */
  rowSelection: RowSelectionState;
  /** ID of currently hovered row (for conditional checkbox display) */
  hoveredRowId: string | null;
}

interface ColumnSliceState {
  /** Column widths by column ID */
  columnSizing: Record<string, number>;
  /** Ordered array of column IDs */
  columnOrder: string[];
  /** Column visibility by column ID */
  columnVisibility: Record<string, boolean>;
}

interface UISliceState {
  /** Client-side hydration flag (prevents SSR mismatches) */
  isMounted: boolean;
  /** Filter popover open state */
  filterPopoverOpen: boolean;
  /** Column currently being resized (or null if none) */
  resizingColumnId: string | null;
}

interface EditSliceState {
  /** Currently focused cell (rowId + columnId) - highlighted but not editing */
  focusedCell: { rowId: string; columnId: string } | null;
  /** Currently editing cell (rowId + columnId) */
  editingCell: { rowId: string; columnId: string } | null;
  /** Pending value being edited */
  pendingValue: any;
  /** Save operation in progress */
  isSaving: boolean;
  /** Save error message */
  saveError: string | null;
  /** Cell edit callback (stored in state to allow updates) */
  onCellEdit:
    | ((row: any, columnId: string, newValue: any, oldValue: any) => Promise<boolean>)
    | null;
}

// ============================================================================
// Actions Interfaces (organized by concern)
// ============================================================================

interface DataActions<TData> {
  /** Sync table data from external source (TanStack Query) */
  setTableData: (data: TData[]) => void;
  /** Register TanStack Table instance for advanced operations */
  setTableInstance: (table: Table<TData>) => void;
  /** Reorder rows (for drag and drop) */
  reorderRows: (oldIndex: number, newIndex: number) => void;
}

interface SortingActions {
  setSorting: (sorting: SortingState | ((prev: SortingState) => SortingState)) => void;
}

interface FilteringActions {
  setColumnFilters: (filters: ColumnFiltersState) => void;
  setGlobalFilter: (filter: string) => void;
  setSearchValue: (value: string) => void;
  setActiveFilters: (
    filters: Record<string, any> | ((prev: Record<string, any>) => Record<string, any>),
  ) => void;
  setEnabledFilters: (filters: Set<string> | ((prev: Set<string>) => Set<string>)) => void;
  /** Clear search input and global filter */
  clearSearch: () => void;
  /** Clear all filters and reset enabled state */
  clearAllFilters: () => void;
  /** Clear a specific filter */
  clearFilter: (columnId: string) => void;
  /** Toggle a filter on/off */
  toggleFilter: (columnId: string, enabled: boolean) => void;
  /** Update a specific filter value */
  updateFilter: (columnId: string, value: any) => void;
}

interface SelectionActions<TData> {
  setRowSelection: (
    selection: RowSelectionState | ((prev: RowSelectionState) => RowSelectionState),
  ) => void;
  setHoveredRowId: (rowId: string | null) => void;
  /** Clear all row selections */
  clearSelection: () => void;
  /** Get selected row data (requires table instance) */
  getSelectedRows: () => TData[];
}

interface ColumnActions {
  setColumnSizing: (
    sizing: Record<string, number> | ((prev: Record<string, number>) => Record<string, number>),
  ) => void;
  setColumnOrder: (order: string[]) => void;
  setColumnVisibility: (visibility: Record<string, boolean>) => void;
  toggleColumnVisibility: (columnId: string) => void;
}

interface UIActions {
  setMounted: (mounted: boolean) => void;
  setFilterPopoverOpen: (open: boolean) => void;
  setResizingColumnId: (columnId: string | null) => void;
}

interface EditActions<TData> {
  /** Start editing a cell (auto-commits previous cell if editing) */
  startEditing: (rowId: string, columnId: string, initialValue: any) => Promise<void>;
  /** Update the pending value as user types */
  updatePendingValue: (value: any) => void;
  /** Cancel editing and revert to original value */
  cancelEditing: () => void;
  /** Commit edit: validate → call callback → update data or stay in edit mode */
  commitEdit: () => Promise<void>;
  /** Register a commit callback from the editing widget (called on click-outside) */
  registerCommitCallback: (callback: (() => void) | null) => void;
  /** Request commit via the registered callback (for click-outside handling) */
  requestCommit: () => void;
  /** Set focused cell without entering edit mode */
  setFocusedCell: (rowId: string, columnId: string) => void;
  /** Clear focus (unfocus table entirely) */
  clearFocus: () => void;
  /** Navigate focus to next editable cell (Tab behavior, wraps) */
  focusNextCell: () => void;
  /** Navigate focus to previous editable cell (Shift+Tab behavior, wraps) */
  focusPrevCell: () => void;
  /** Navigate focus to adjacent cell (arrow key behavior) */
  focusAdjacentCell: (direction: 'up' | 'down' | 'left' | 'right') => void;
  /** Enter edit mode for currently focused cell */
  enterEditMode: () => void;
  /** Exit edit mode but keep focus on cell (discard changes) */
  exitToFocused: () => void;
  /** Update the cell edit callback (allows syncing from props) */
  setOnCellEdit: (
    callback:
      | ((row: TData, columnId: string, newValue: any, oldValue: any) => Promise<boolean>)
      | null,
  ) => void;
}

// ============================================================================
// Combined Store Type
// ============================================================================

export type DataTableStore<TData> = DataState<TData> &
  SortingSliceState &
  FilteringSliceState &
  SelectionSliceState &
  ColumnSliceState &
  UISliceState &
  EditSliceState &
  DataActions<TData> &
  SortingActions &
  FilteringActions &
  SelectionActions<TData> &
  ColumnActions &
  UIActions &
  EditActions<TData> & {
    // Derived state (computed on-demand)
    /** Number of active filters */
    getActiveFilterCount: () => number;
    /** Number of selected rows */
    getSelectedCount: () => number;
    /** Whether any rows are selected */
    hasSelections: () => boolean;
    /** Whether drag-drop should be enabled (no filters/search/selections) */
    isDragDropEnabled: (enableDragDrop: boolean) => boolean;
  };

// ============================================================================
// Store Factory
// ============================================================================

export interface CreateDataTableStoreOptions<TData> {
  /** Initial table data */
  initialData: TData[];
  /** Initial column sizing */
  initialColumnSizing?: Record<string, number>;
  /** Initial column order */
  initialColumnOrder?: string[];
  /** Initial column visibility */
  initialColumnVisibility?: Record<string, boolean>;
  /** Callback for cell edit (returns true on success) */
  onCellEdit?: (row: TData, columnId: string, newValue: any, oldValue: any) => Promise<boolean>;
  /** Function to get unique row ID */
  getRowId?: (row: TData, index: number) => string;
}

// ============================================================================
// Navigation Helpers
// ============================================================================

/**
 * Find the next/previous editable cell with wrapping behavior.
 * - Right/Tab at end of row → first cell of next row
 * - Left/Shift+Tab at start of row → last cell of previous row
 * - Wraps around at table boundaries
 */
function findNextEditableCell<TData>(
  table: Table<TData>,
  currentRowId: string,
  currentColumnId: string,
  direction: 'next' | 'prev',
): { rowId: string; columnId: string } | null {
  const rows = table.getRowModel().rows;
  const columns = table
    .getAllLeafColumns()
    .filter((col) => isEditableColumn(col.columnDef.meta) && col.id !== 'select');

  if (columns.length === 0 || rows.length === 0) return null;

  const currentRowIndex = rows.findIndex((r) => r.id === currentRowId);
  const currentColIndex = columns.findIndex((c) => c.id === currentColumnId);

  if (currentRowIndex === -1 || currentColIndex === -1) return null;

  let rowIdx = currentRowIndex;
  let colIdx = currentColIndex;

  if (direction === 'next') {
    colIdx++;
    if (colIdx >= columns.length) {
      colIdx = 0;
      rowIdx++;
    }
    if (rowIdx >= rows.length) {
      // Wrap to first cell of table
      rowIdx = 0;
      colIdx = 0;
    }
  } else {
    colIdx--;
    if (colIdx < 0) {
      colIdx = columns.length - 1;
      rowIdx--;
    }
    if (rowIdx < 0) {
      // Wrap to last cell of table
      rowIdx = rows.length - 1;
      colIdx = columns.length - 1;
    }
  }

  return {
    rowId: rows[rowIdx].id,
    columnId: columns[colIdx].id,
  };
}

/**
 * Find adjacent cell in the specified direction.
 * - Up/Down: Stop at table boundaries (no wrap)
 * - Left/Right: Wrap to next/previous row
 */
function findAdjacentCell<TData>(
  table: Table<TData>,
  currentRowId: string,
  currentColumnId: string,
  direction: 'up' | 'down' | 'left' | 'right',
): { rowId: string; columnId: string } | null {
  const rows = table.getRowModel().rows;
  const columns = table
    .getAllLeafColumns()
    .filter((col) => isEditableColumn(col.columnDef.meta) && col.id !== 'select');

  if (columns.length === 0 || rows.length === 0) return null;

  const currentRowIndex = rows.findIndex((r) => r.id === currentRowId);
  const currentColIndex = columns.findIndex((c) => c.id === currentColumnId);

  if (currentRowIndex === -1 || currentColIndex === -1) return null;

  let rowIdx = currentRowIndex;
  let colIdx = currentColIndex;

  switch (direction) {
    case 'up':
      if (rowIdx > 0) {
        rowIdx--;
      } else {
        return null; // At top boundary
      }
      break;
    case 'down':
      if (rowIdx < rows.length - 1) {
        rowIdx++;
      } else {
        return null; // At bottom boundary
      }
      break;
    case 'left':
      if (colIdx > 0) {
        colIdx--;
      } else if (rowIdx > 0) {
        // Wrap to previous row's last cell
        rowIdx--;
        colIdx = columns.length - 1;
      } else {
        return null; // At very first cell
      }
      break;
    case 'right':
      if (colIdx < columns.length - 1) {
        colIdx++;
      } else if (rowIdx < rows.length - 1) {
        // Wrap to next row's first cell
        rowIdx++;
        colIdx = 0;
      } else {
        return null; // At very last cell
      }
      break;
  }

  return {
    rowId: rows[rowIdx].id,
    columnId: columns[colIdx].id,
  };
}

/**
 * Factory function to create a DataTable store instance
 *
 * Each table gets its own store instance to avoid state collisions.
 * The store is scoped to the table's lifecycle via the provider.
 */
export const createDataTableStore = <TData>(options: CreateDataTableStoreOptions<TData>) => {
  const {
    initialData,
    initialColumnSizing = {},
    initialColumnVisibility = {},
    onCellEdit,
    getRowId = (row: TData, index: number) => String(index),
  } = options;

  // Closure variable for commit callback - not reactive to avoid re-render loops
  let commitCallback: (() => void) | null = null;

  return create<DataTableStore<TData>>()(
    subscribeWithSelector((set, get) => ({
      // ================================================================
      // Initial State
      // ================================================================

      // Data slice
      tableData: initialData,
      tableInstance: null,

      // Sorting slice
      sorting: [],

      // Filtering slice
      columnFilters: [],
      globalFilter: '',
      searchValue: '',
      activeFilters: {},
      enabledFilters: new Set<string>(),

      // Selection slice
      rowSelection: {},
      hoveredRowId: null,

      // Column slice
      columnSizing: initialColumnSizing,
      columnOrder: [],
      columnVisibility: initialColumnVisibility,

      // UI slice
      isMounted: false,
      filterPopoverOpen: false,
      resizingColumnId: null,

      // Edit slice
      focusedCell: null,
      editingCell: null,
      pendingValue: null,
      isSaving: false,
      saveError: null,
      onCellEdit: onCellEdit || null,

      // ================================================================
      // Data Actions
      // ================================================================

      setTableData: (data) => set({ tableData: data }),

      setTableInstance: (table) => set({ tableInstance: table }),

      reorderRows: (oldIndex, newIndex) => {
        const { tableData } = get();
        const newData = [...tableData];
        const [movedItem] = newData.splice(oldIndex, 1);
        newData.splice(newIndex, 0, movedItem);
        set({ tableData: newData });
      },

      // ================================================================
      // Sorting Actions
      // ================================================================

      setSorting: (sorting) =>
        set((state) => ({
          sorting: typeof sorting === 'function' ? sorting(state.sorting) : sorting,
        })),

      // ================================================================
      // Filtering Actions
      // ================================================================

      setColumnFilters: (filters) => set({ columnFilters: filters }),

      setGlobalFilter: (filter) => set({ globalFilter: filter }),

      setSearchValue: (value) => set({ searchValue: value }),

      setActiveFilters: (filters) =>
        set((state) => ({
          activeFilters: typeof filters === 'function' ? filters(state.activeFilters) : filters,
        })),

      setEnabledFilters: (filters) =>
        set((state) => ({
          enabledFilters: typeof filters === 'function' ? filters(state.enabledFilters) : filters,
        })),

      clearSearch: () => set({ searchValue: '', globalFilter: '' }),

      clearAllFilters: () =>
        set({
          activeFilters: {},
          enabledFilters: new Set(),
          columnFilters: [],
        }),

      clearFilter: (columnId) => {
        set((state) => {
          const newActiveFilters = { ...state.activeFilters };
          delete newActiveFilters[columnId];

          const newEnabledFilters = new Set(state.enabledFilters);
          newEnabledFilters.delete(columnId);

          return {
            activeFilters: newActiveFilters,
            enabledFilters: newEnabledFilters,
          };
        });
      },

      toggleFilter: (columnId, enabled) => {
        if (enabled) {
          set((state) => ({
            enabledFilters: new Set(state.enabledFilters).add(columnId),
          }));
        } else {
          set((state) => {
            const newEnabledFilters = new Set(state.enabledFilters);
            newEnabledFilters.delete(columnId);

            const newActiveFilters = { ...state.activeFilters };
            delete newActiveFilters[columnId];

            return {
              enabledFilters: newEnabledFilters,
              activeFilters: newActiveFilters,
            };
          });
        }
      },

      updateFilter: (columnId, value) => {
        set((state) => ({
          activeFilters: {
            ...state.activeFilters,
            [columnId]: value,
          },
        }));
      },

      // ================================================================
      // Selection Actions
      // ================================================================

      setRowSelection: (selection) =>
        set((state) => ({
          rowSelection: typeof selection === 'function' ? selection(state.rowSelection) : selection,
        })),

      setHoveredRowId: (rowId) => set({ hoveredRowId: rowId }),

      clearSelection: () => set({ rowSelection: {} }),

      getSelectedRows: () => {
        const { tableInstance } = get();
        if (!tableInstance) return [];
        return tableInstance.getSelectedRowModel().rows.map((row) => row.original);
      },

      // ================================================================
      // Column Actions
      // ================================================================

      setColumnSizing: (sizing) =>
        set((state) => ({
          columnSizing: typeof sizing === 'function' ? sizing(state.columnSizing) : sizing,
        })),

      setColumnOrder: (order) => set({ columnOrder: order }),

      setColumnVisibility: (visibility) => set({ columnVisibility: visibility }),

      toggleColumnVisibility: (columnId) =>
        set((state) => ({
          columnVisibility: {
            ...state.columnVisibility,
            [columnId]: !(state.columnVisibility[columnId] ?? true),
          },
        })),

      // ================================================================
      // UI Actions
      // ================================================================

      setMounted: (mounted) => set({ isMounted: mounted }),

      setFilterPopoverOpen: (open) => set({ filterPopoverOpen: open }),

      setResizingColumnId: (columnId) => set({ resizingColumnId: columnId }),

      // ================================================================
      // Edit Actions
      // ================================================================

      startEditing: async (rowId, columnId, initialValue) => {
        const { editingCell, commitEdit } = get();

        // If editing a different cell, commit the previous one in the background
        if (editingCell && (editingCell.rowId !== rowId || editingCell.columnId !== columnId)) {
          // Don't await - commit in background to avoid race condition with click events
          // This allows immediate transition to the new cell
          commitEdit().catch((error) => {
            console.error('Background commit failed:', error);
          });
        }

        // Immediately set new editing cell (don't wait for previous commit)
        set({
          editingCell: { rowId, columnId },
          pendingValue: initialValue,
          saveError: null,
        });
      },

      updatePendingValue: (value) => set({ pendingValue: value }),

      cancelEditing: () => {
        commitCallback = null;
        set({
          editingCell: null,
          pendingValue: null,
          saveError: null,
        });
      },

      commitEdit: async () => {
        const { editingCell, pendingValue, tableData, onCellEdit: cellEditCallback } = get();
        if (!editingCell || !cellEditCallback) return;

        const { rowId, columnId } = editingCell;
        const rowIndex = tableData.findIndex((r, idx) => getRowId(r, idx) === rowId);
        if (rowIndex === -1) return;

        const row = tableData[rowIndex];
        const oldValue = (row as any)[columnId];

        set({ isSaving: true, saveError: null });

        try {
          const success = await cellEditCallback(row, columnId, pendingValue, oldValue);

          if (success) {
            // Optimistic update - keep focus on the cell after save
            commitCallback = null;
            set((state) => ({
              tableData: state.tableData.map((r, idx) =>
                getRowId(r, idx) === rowId ? { ...r, [columnId]: pendingValue } : r,
              ),
              editingCell: null,
              pendingValue: null,
              isSaving: false,
              saveError: null,
              focusedCell: editingCell, // Keep focus on the saved cell
            }));
          } else {
            toast.error('Failed to save changes.');
            set({
              isSaving: false,
              saveError: 'Save failed',
              editingCell: null,
              pendingValue: null,
              focusedCell: editingCell,
            });
          }
        } catch (error) {
          set({ isSaving: false, saveError: (error as Error).message || 'Save failed' });
        }
      },

      registerCommitCallback: (callback) => {
        // Use closure variable - no state update to avoid re-render loops
        commitCallback = callback;
      },

      requestCommit: () => {
        // Use closure variable
        if (commitCallback) {
          commitCallback();
        }
      },

      setOnCellEdit: (callback) => set({ onCellEdit: callback }),

      // ================================================================
      // Focus Actions
      // ================================================================

      setFocusedCell: (rowId, columnId) => {
        set({ focusedCell: { rowId, columnId } });
      },

      clearFocus: () => {
        set({ focusedCell: null });
      },

      focusNextCell: () => {
        const { focusedCell, tableInstance } = get();
        if (!focusedCell || !tableInstance) return;

        const nextCell = findNextEditableCell(
          tableInstance,
          focusedCell.rowId,
          focusedCell.columnId,
          'next',
        );
        if (nextCell) {
          set({ focusedCell: nextCell });
        }
      },

      focusPrevCell: () => {
        const { focusedCell, tableInstance } = get();
        if (!focusedCell || !tableInstance) return;

        const prevCell = findNextEditableCell(
          tableInstance,
          focusedCell.rowId,
          focusedCell.columnId,
          'prev',
        );
        if (prevCell) {
          set({ focusedCell: prevCell });
        }
      },

      focusAdjacentCell: (direction) => {
        const { focusedCell, tableInstance } = get();
        if (!focusedCell || !tableInstance) return;

        const adjacentCell = findAdjacentCell(
          tableInstance,
          focusedCell.rowId,
          focusedCell.columnId,
          direction,
        );
        if (adjacentCell) {
          set({ focusedCell: adjacentCell });
        }
      },

      enterEditMode: () => {
        const { focusedCell, tableInstance, tableData } = get();
        if (!focusedCell || !tableInstance) return;

        // Find the row and get the cell value
        const rowIndex = tableData.findIndex((r, idx) => getRowId(r, idx) === focusedCell.rowId);
        if (rowIndex === -1) return;

        const row = tableData[rowIndex];
        const value = (row as Record<string, unknown>)[focusedCell.columnId];

        set({
          editingCell: focusedCell,
          pendingValue: value,
          saveError: null,
        });
      },

      exitToFocused: () => {
        const { editingCell } = get();
        commitCallback = null;
        set({
          editingCell: null,
          pendingValue: null,
          saveError: null,
          // Keep focus on the cell that was being edited
          focusedCell: editingCell,
        });
      },

      // ================================================================
      // Derived State (Computed)
      // ================================================================

      getActiveFilterCount: () => {
        const { activeFilters, enabledFilters } = get();
        return Object.entries(activeFilters).filter(([key, value]) => {
          if (!enabledFilters.has(key)) return false;
          if (Array.isArray(value)) return value.length > 0;
          return value !== null && value !== undefined && value !== '';
        }).length;
      },

      getSelectedCount: () => {
        const { rowSelection } = get();
        return Object.keys(rowSelection).filter((key) => rowSelection[key]).length;
      },

      hasSelections: () => {
        const { rowSelection } = get();
        return Object.keys(rowSelection).length > 0;
      },

      isDragDropEnabled: (enableDragDrop) => {
        if (!enableDragDrop) return false;
        const { globalFilter, getActiveFilterCount } = get();
        if (globalFilter && globalFilter.length > 0) return false;
        if (getActiveFilterCount() > 0) return false;
        return true;
      },
    })),
  );
};

// Export store API type for TypeScript
export type DataTableStoreApi<TData> = ReturnType<typeof createDataTableStore<TData>>;
