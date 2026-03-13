/**
 * DataTable Component Library
 *
 * Centralized export for all data table components, hooks, and types.
 *
 * Usage:
 * import { DataTableProvider, DataTable, useDataTableActions } from "@/components/ui/data-table";
 */

// ============================================================================
// Provider and Store
// ============================================================================

export { DataTableProvider } from './data-table-provider';
export type { DataTableProviderProps } from './data-table-provider';

// ============================================================================
// Hooks
// ============================================================================

export {
  useDataTableStore,
  useDataTableActions,
  useTableData,
  useSorting,
  useFiltering,
  useSelection,
  useColumns,
  useUIState,
  useDerivedState,
} from './data-table-provider';

// ============================================================================
// Store Types (for advanced use cases)
// ============================================================================

export type {
  DataTableStore,
  DataTableStoreApi,
  CreateDataTableStoreOptions,
} from './data-table.store';

// ============================================================================
// Shared Types
// ============================================================================

export type {
  SearchConfig,
  FilterConfig,
  FilterType,
  FilterOption,
  ColumnFilter,
  RowAction,
  RowActionsConfig,
  ColumnResizingConfig,
  ColumnOrderingConfig,
  SelectionActionProps,
  RowSelectionConfig,
} from './data-table.types';

// ============================================================================
// Main Component
// ============================================================================

export { DataTable } from './data-table';
export type { DataTableProps } from './data-table';

// ============================================================================
// Sub-Components (for custom compositions)
// ============================================================================

export { DataTableToolbar } from './components/data-table-toolbar';
export { DataTableSearchInput } from './components/data-table-search-input';
export { DataTableSelectionBar } from './components/data-table-selection-bar';
export { DataTableHeader } from './components/data-table-header';
export { DataTableBody } from './components/data-table-body';
export { DraggableColumnHeader } from './components/draggable-column-header';
export { DraggableRow } from './components/draggable-row';
export { RowActionsMenu } from './components/row-actions-menu';
