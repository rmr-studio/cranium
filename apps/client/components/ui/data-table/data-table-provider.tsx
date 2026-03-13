'use client';

/**
 * DataTableProvider
 *
 * Context provider that manages the DataTable store instance and makes it
 * available to all child components via custom hooks.
 *
 * Pattern:
 * - Factory function creates store instance on mount
 * - Store ref prevents recreation on re-renders
 * - Context provides store to descendants
 * - Selector-based hooks prevent unnecessary re-renders
 *
 * Usage:
 * <DataTableProvider initialData={data} initialColumnSizing={sizing}>
 *   <DataTable columns={columns} />
 * </DataTableProvider>
 */

import { Table } from '@tanstack/react-table';
import { createContext, useContext, useEffect, useRef, type ReactNode } from 'react';
import { useStoreWithEqualityFn } from 'zustand/traditional';
import {
  createDataTableStore,
  CreateDataTableStoreOptions,
  DataTableStore,
  DataTableStoreApi,
} from './data-table.store';

// ============================================================================
// Context
// ============================================================================

const DataTableContext = createContext<DataTableStoreApi<any> | undefined>(undefined);

// ============================================================================
// Provider Component
// ============================================================================

export interface DataTableProviderProps<TData> extends CreateDataTableStoreOptions<TData> {
  children: ReactNode;
  /** Callback when data is reordered via drag-drop */
  onReorder?: (data: TData[]) => void | boolean;
  /** Callback when column widths change */
  onColumnWidthsChange?: (columnSizing: Record<string, number>) => void;
  /** Callback when column order changes */
  onColumnOrderChange?: (columnOrder: string[]) => void;
  /** Callback when filters change */
  onFiltersChange?: (filters: Record<string, any>) => void;
  /** Callback when search changes */
  onSearchChange?: (value: string) => void;
  /** Callback when selection changes */
  onSelectionChange?: (selectedRows: TData[]) => void;
  /** Callback when a cell is edited (returns true on success) */
  onCellEdit?: (row: TData, columnId: string, newValue: any, oldValue: any) => Promise<boolean>;
}

export function DataTableProvider<TData>({
  children,
  initialData,
  initialColumnSizing,
  initialColumnOrder,
  initialColumnVisibility,
  onReorder,
  onColumnWidthsChange,
  onColumnOrderChange,
  onFiltersChange,
  onSearchChange,
  onSelectionChange,
  onCellEdit,
  getRowId,
}: DataTableProviderProps<TData>) {
  const storeRef = useRef<DataTableStoreApi<TData> | null>(null);

  // Create store only once per component instance
  if (!storeRef.current) {
    storeRef.current = createDataTableStore<TData>({
      initialData,
      initialColumnSizing,
      initialColumnOrder,
      initialColumnVisibility,
      getRowId,
      onCellEdit,
    });
  }

  // Sync external data changes to store
  useEffect(() => {
    storeRef.current?.getState().setTableData(initialData);
  }, [initialData]);

  // Sync external column order changes to store
  useEffect(() => {
    if (initialColumnOrder && initialColumnOrder.length > 0) {
      storeRef.current?.getState().setColumnOrder(initialColumnOrder);
    }
  }, [initialColumnOrder]);

  // Sync onCellEdit callback changes to store (prevents stale closures)
  useEffect(() => {
    storeRef.current?.getState().setOnCellEdit(onCellEdit ?? null);
  }, [onCellEdit]);

  // Subscribe to store changes and notify parent via callbacks
  useEffect(() => {
    if (!storeRef.current) return;

    const store = storeRef.current;

    // Subscribe to column sizing changes
    const unsubColumnSizing = onColumnWidthsChange
      ? store.subscribe(
          (state: DataTableStore<TData>) => state.columnSizing,
          (columnSizing: Record<string, number>) => {
            if (Object.keys(columnSizing).length > 0) {
              onColumnWidthsChange(columnSizing);
            }
          },
        )
      : undefined;

    // Subscribe to column order changes
    const unsubColumnOrder = onColumnOrderChange
      ? store.subscribe(
          (state: DataTableStore<TData>) => state.columnOrder,
          (columnOrder: string[]) => {
            if (columnOrder.length > 0) {
              onColumnOrderChange(columnOrder);
            }
          },
        )
      : undefined;

    // Subscribe to active filters (only enabled filters)
    const unsubFilters = onFiltersChange
      ? store.subscribe(
          (state: DataTableStore<TData>) => ({
            activeFilters: state.activeFilters,
            enabledFilters: state.enabledFilters,
          }),
          ({
            activeFilters,
            enabledFilters,
          }: {
            activeFilters: Record<string, any>;
            enabledFilters: Set<string>;
          }) => {
            const enabledActiveFilters = Object.fromEntries(
              Object.entries(activeFilters).filter(([key]) => enabledFilters.has(key)),
            );
            onFiltersChange(enabledActiveFilters);
          },
          {
            equalityFn: (
              a: { activeFilters: Record<string, any>; enabledFilters: Set<string> },
              b: { activeFilters: Record<string, any>; enabledFilters: Set<string> },
            ) => a.activeFilters === b.activeFilters && a.enabledFilters === b.enabledFilters,
          },
        )
      : undefined;

    // Subscribe to search value changes
    const unsubSearch = onSearchChange
      ? store.subscribe(
          (state: DataTableStore<TData>) => state.globalFilter,
          (globalFilter: string) => {
            onSearchChange(globalFilter);
          },
        )
      : undefined;

    // Subscribe to selection changes
    const unsubSelection = onSelectionChange
      ? store.subscribe(
          (state: DataTableStore<TData>) => state.rowSelection,
          () => {
            const selectedRows = store.getState().getSelectedRows();
            onSelectionChange(selectedRows);
          },
        )
      : undefined;

    return () => {
      unsubColumnSizing?.();
      unsubColumnOrder?.();
      unsubFilters?.();
      unsubSearch?.();
      unsubSelection?.();
    };
  }, [
    onColumnWidthsChange,
    onColumnOrderChange,
    onFiltersChange,
    onSearchChange,
    onSelectionChange,
  ]);

  // Set mounted state on client-side (prevents SSR mismatches)
  useEffect(() => {
    storeRef.current?.getState().setMounted(true);
  }, []);

  return (
    <DataTableContext.Provider value={storeRef.current as DataTableStoreApi<any>}>
      {children}
    </DataTableContext.Provider>
  );
}

// ============================================================================
// Custom Hooks (Selector-based for fine-grained subscriptions)
// ============================================================================

/**
 * Base hook to access the store with a selector
 *
 * Usage:
 * const sorting = useDataTableStore(state => state.sorting);
 */
export function useDataTableStore<TData, TResult>(
  selector: (store: DataTableStore<TData>) => TResult,
  equalityFn?: (a: TResult, b: TResult) => boolean,
): TResult {
  const context = useContext(DataTableContext);

  if (!context) {
    throw new Error('useDataTableStore must be used within DataTableProvider');
  }

  return useStoreWithEqualityFn(context, selector, equalityFn);
}

/**
 * Hook to access store actions only (never causes re-renders)
 *
 * This hook uses a stable selector that returns the entire store,
 * but React won't re-render because we only use it for actions.
 */
export function useDataTableActions<TData>() {
  return useDataTableStore<TData, DataTableStore<TData>>(
    (state) => state,
    () => true,
  );
}

export function useDataTableSearch<TData>() {
  return useDataTableStore<
    TData,
    {
      searchValue: string;
      setGlobalFilter: (value: string) => void;
      table: Table<TData> | null;
      setSearchValue: (value: string) => void;
      clearSearch: () => void;
    }
  >((state) => ({
    setGlobalFilter: state.setGlobalFilter,
    searchValue: state.searchValue,
    setSearchValue: state.setSearchValue,
    table: state.tableInstance,
    clearSearch: state.clearSearch,
  }));
}

// ============================================================================
// Convenience Hooks (commonly used slices)
// ============================================================================

/** Get table data (will re-render when data changes) */
export function useTableData<TData>() {
  return useDataTableStore<TData, TData[]>((state) => state.tableData);
}

/** Get sorting state */
export function useSorting<TData>() {
  return useDataTableStore<TData, DataTableStore<TData>['sorting']>((state) => state.sorting);
}

/** Get filtering state */
export function useFiltering<TData>() {
  return useDataTableStore<
    TData,
    {
      columnFilters: DataTableStore<TData>['columnFilters'];
      globalFilter: string;
      searchValue: string;
      activeFilters: Record<string, any>;
      enabledFilters: Set<string>;
    }
  >((state) => ({
    columnFilters: state.columnFilters,
    globalFilter: state.globalFilter,
    searchValue: state.searchValue,
    activeFilters: state.activeFilters,
    enabledFilters: state.enabledFilters,
  }));
}

/** Get selection state */
export function useSelection<TData>() {
  return useDataTableStore<
    TData,
    {
      rowSelection: DataTableStore<TData>['rowSelection'];
      hoveredRowId: string | null;
      selectedCount: number;
      hasSelections: boolean;
    }
  >((state) => ({
    rowSelection: state.rowSelection,
    hoveredRowId: state.hoveredRowId,
    selectedCount: state.getSelectedCount(),
    hasSelections: state.hasSelections(),
  }));
}

/** Get column state */
export function useColumns<TData>() {
  return useDataTableStore<
    TData,
    {
      columnSizing: Record<string, number>;
      columnOrder: string[];
    }
  >((state) => ({
    columnSizing: state.columnSizing,
    columnOrder: state.columnOrder,
  }));
}

/** Get UI state */
export function useUIState<TData>() {
  return useDataTableStore<
    TData,
    {
      isMounted: boolean;
      filterPopoverOpen: boolean;
    }
  >((state) => ({
    isMounted: state.isMounted,
    filterPopoverOpen: state.filterPopoverOpen,
  }));
}

export function useCellInteraction<TData>() {
  return useDataTableStore<
    TData,
    {
      editingCell: { rowId: string; columnId: string } | null;
      focusedCell: { rowId: string; columnId: string } | null;
      startEditing: (rowId: string, columnId: string, initialValue: any) => Promise<void>;
      cancelEditing: () => void;
      commitEdit: () => Promise<void>;
      exitToFocused: () => void;
      updatePendingValue: (value: any) => void;
    }
  >((state) => ({
    editingCell: state.editingCell,
    focusedCell: state.focusedCell,
    startEditing: state.startEditing,
    cancelEditing: state.cancelEditing,
    commitEdit: state.commitEdit,
    exitToFocused: state.exitToFocused,
    updatePendingValue: state.updatePendingValue,
  }));
}

/** Get derived state flags */
export function useDerivedState<TData>(enableDragDrop: boolean, enableSelection: boolean) {
  return useDataTableStore<
    TData,
    {
      isDragDropEnabled: boolean;
      isSelectionEnabled: boolean;
      activeFilterCount: number;
    }
  >((state) => ({
    isDragDropEnabled: state.isDragDropEnabled(enableDragDrop),
    isSelectionEnabled: enableSelection,
    activeFilterCount: state.getActiveFilterCount(),
  }));
}

export function useCellSelectionOverview<TData>() {
  return useDataTableStore<
    TData,
    {
      selectedCount: number;
      hasSelections: boolean;
      selectedRows: TData[];
      clearSelection: () => void;
    }
  >((state) => ({
    selectedCount: state.getSelectedCount(),
    hasSelections: state.hasSelections(),
    selectedRows: state.getSelectedRows(),
    clearSelection: state.clearSelection,
  }));
}
