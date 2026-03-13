# Data Table Architecture Overhaul

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the shared DataTable component to cleanly support divergent use cases (entity instance tables vs config tables) through composition, fix action column visibility, separate column drag from resize, and add comprehensive test coverage.

**Architecture:** The shared `DataTable` + `DataTableProvider` + store remain the generic engine. A new `ActionColumnConfig` type replaces boolean props to control per-element visibility (drag handle, checkbox). Column drag gets its own DnD context with a dedicated drag handle element in the header, separated from resize. The entity-data-table god component is decomposed into focused hooks. CSS-only hover replaces JS `hoveredRowId` tracking for row action visibility.

**Tech Stack:** React 19, TanStack Table, dnd-kit, Zustand, Tailwind 4, Jest, react-hook-form, zod

---

## File Map

### New Files
| File | Responsibility |
|------|---------------|
| `components/ui/data-table/data-table.types.ts` | Add `ActionColumnConfig` type (modify existing) |
| `components/ui/data-table/components/action-cell.tsx` | Extracted action cell component (drag handle + checkbox) with CSS-only hover |
| `components/ui/data-table/data-table.store.test.ts` | Comprehensive store tests |
| `components/feature-modules/entity/util/entity-payload.util.ts` | `buildEntityUpdatePayload()` pure utility |
| `components/feature-modules/entity/util/entity-payload.util.test.ts` | Tests for payload utility |
| `components/feature-modules/entity/hooks/use-entity-inline-edit.ts` | Extracted inline edit hook |
| `components/feature-modules/entity/hooks/use-entity-column-config.ts` | Extracted column config persistence hook |
| `components/feature-modules/entity/hooks/use-entity-table-data.ts` | Extracted row transformation hook |

### Modified Files
| File | Changes |
|------|---------|
| `components/ui/data-table/data-table.tsx` | Replace `alwaysShowActionHandles` with `ActionColumnConfig`; split DnD into nested contexts; extract `globalFilterFn` to module level; add drag handle to column headers |
| `components/ui/data-table/data-table.types.ts` | Add `ActionColumnConfig`, `ActionElementConfig`, `ActionVisibility` types |
| `components/ui/data-table/data-table.store.ts` | Remove `hoveredRowId` from store (CSS handles hover now) |
| `components/ui/data-table/data-table-provider.tsx` | Remove `hoveredRowId` subscription; remove `setHoveredRowId` from actions |
| `components/ui/data-table/components/draggable-row.tsx` | Use `ActionCell` sub-component; remove `hoveredRowId` subscription; fix memo comparator |
| `components/ui/data-table/components/draggable-column-header.tsx` | Add drag handle element for column reordering; separate drag zone from resize zone |
| `components/ui/data-table/components/data-table-header.tsx` | Separate column DnD context from row DnD context |
| `components/ui/data-table/components/data-table-body.tsx` | Pass `ActionColumnConfig` instead of `alwaysShowActionHandles` |
| `components/ui/data-table/index.ts` | Export new types and `ActionCell` |
| `components/feature-modules/entity/components/tables/entity-data-table.tsx` | Decompose into hooks; use `ActionColumnConfig` |
| `components/feature-modules/entity/components/types/entity-type-data-table.tsx` | Use `ActionColumnConfig` with `always` visibility, no checkbox |
| `components/feature-modules/entity/components/types/entity-types-overview.tsx` | No action column (no drag, no selection) — no changes needed |

---

## Chunk 1: ActionColumnConfig Type System & CSS Hover

### Task 1: Add ActionColumnConfig types

**Files:**
- Modify: `components/ui/data-table/data-table.types.ts`

- [ ] **Step 1: Add the new types to data-table.types.ts**

Add after the `ColumnOrderingConfig` interface (around line 160):

```typescript
// ============================================================================
// Action Column Configuration
// ============================================================================

/** Controls when an action element (drag handle or checkbox) is visible */
export type ActionVisibility = 'always' | 'hover-or-selected';

/** Configuration for a single action element */
export interface ActionElementConfig {
  /** Whether this element is rendered at all. Default: true when parent feature is enabled */
  enabled: boolean;
  /** When the element becomes visible. Default: 'hover-or-selected' */
  visibility: ActionVisibility;
}

/** Configuration for the action column (drag handle + selection checkbox) */
export interface ActionColumnConfig {
  /** Drag handle configuration. Only applies when enableDragDrop is true */
  dragHandle?: ActionElementConfig;
  /** Selection checkbox configuration. Only applies when rowSelection is enabled */
  checkbox?: ActionElementConfig;
}
```

- [ ] **Step 2: Commit**

```bash
git add components/ui/data-table/data-table.types.ts
git commit -m "feat(data-table): add ActionColumnConfig type system for per-element visibility control"
```

---

### Task 2: Create ActionCell, update DraggableRow, remove hoveredRowId (atomic)

> **IMPORTANT:** This task is atomic — all changes must land together. Removing `hoveredRowId` from the store while DraggableRow still reads it would break the build. The ActionCell's `group-hover/row:` classes require the `group/row` class on TableRow (added in the same task).

**Files:**
- Create: `components/ui/data-table/components/action-cell.tsx`
- Modify: `components/ui/data-table/data-table.store.ts`
- Modify: `components/ui/data-table/data-table-provider.tsx`
- Modify: `components/ui/data-table/components/draggable-row.tsx`

#### Part A: Create ActionCell component

**Files:**
- Create: `components/ui/data-table/components/action-cell.tsx`

- [ ] **Step 1: Create the ActionCell component**

```typescript
'use client';

import { TableCell } from '@riven/ui/table';
import { cn } from '@riven/utils';
import { GripVertical } from 'lucide-react';
import { Checkbox } from '../../checkbox';
import { useDataTableStore } from '../data-table-provider';
import type { ActionColumnConfig } from '../data-table.types';

interface ActionCellProps<TData> {
  rowId: string;
  isSelected: boolean;
  onToggleSelected: (value: boolean) => void;
  enableDragDrop: boolean;
  isSelectionEnabled: boolean;
  isDragDisabled: boolean;
  isMounted: boolean;
  dragAttributes: Record<string, any>;
  dragListeners: Record<string, any>;
  cellSize: number;
  actionColumnConfig?: ActionColumnConfig;
}

export function ActionCell<TData>({
  rowId,
  isSelected,
  onToggleSelected,
  enableDragDrop,
  isSelectionEnabled,
  isDragDisabled,
  isMounted,
  dragAttributes,
  dragListeners,
  cellSize,
  actionColumnConfig,
}: ActionCellProps<TData>) {
  const hasSelections = useDataTableStore<TData, boolean>((state) => state.hasSelections());

  const dragConfig = actionColumnConfig?.dragHandle;
  const checkboxConfig = actionColumnConfig?.checkbox;

  // Determine if drag handle should render
  const showDragHandle = enableDragDrop && (dragConfig?.enabled !== false);
  // Determine if checkbox should render
  const showCheckbox = isSelectionEnabled && (checkboxConfig?.enabled !== false);

  // Visibility classes per element
  const dragVisibility = dragConfig?.visibility ?? 'hover-or-selected';
  const checkboxVisibility = checkboxConfig?.visibility ?? 'hover-or-selected';

  // For 'hover-or-selected': use CSS group-hover + JS selection state
  const getDragVisibilityClass = () => {
    if (dragVisibility === 'always') return 'opacity-100';
    // hover-or-selected: show if row selected or any selections exist, otherwise show on hover
    if (isSelected || hasSelections) return 'opacity-100';
    return 'opacity-0 group-hover/row:opacity-100 transition-opacity duration-150';
  };

  const getCheckboxVisibilityClass = () => {
    if (checkboxVisibility === 'always') return 'opacity-100';
    if (isSelected || hasSelections) return 'opacity-100';
    return 'opacity-0 group-hover/row:opacity-100 transition-opacity duration-150';
  };

  // If neither element is shown, don't render the cell
  if (!showDragHandle && !showCheckbox) return null;

  return (
    <TableCell
      className="border-l border-l-accent/40 first:border-l-transparent"
      style={{
        width: `${cellSize}px`,
        maxWidth: `${cellSize}px`,
      }}
    >
      <div className="flex items-center gap-2">
        {showDragHandle && isMounted && (
          <button
            className={cn(
              'cursor-grab text-muted-foreground transition-colors hover:text-foreground active:cursor-grabbing',
              isDragDisabled && 'cursor-not-allowed opacity-30',
              getDragVisibilityClass(),
            )}
            {...(isMounted && !isDragDisabled ? dragAttributes : {})}
            {...(isMounted && !isDragDisabled ? dragListeners : {})}
            onClick={(e) => e.stopPropagation()}
          >
            <GripVertical className="h-4 w-4" />
          </button>
        )}
        {showCheckbox && (
          <span className={getCheckboxVisibilityClass()}>
            <Checkbox
              checked={isSelected}
              onCheckedChange={(value) => onToggleSelected(!!value)}
              aria-label="Select row"
              onClick={(e) => e.stopPropagation()}
            />
          </span>
        )}
      </div>
    </TableCell>
  );
}
```

#### Part B: Remove hoveredRowId from store and provider

- [ ] **Step 2: Remove hoveredRowId from store**

In `data-table.store.ts`:
- Remove `hoveredRowId: string | null` from `SelectionSliceState`
- Remove `setHoveredRowId: (rowId: string | null) => void` from `SelectionActions`
- Remove `hoveredRowId: null` from initial state in `createDataTableStore`
- Remove `setHoveredRowId: (rowId) => set({ hoveredRowId: rowId })` from actions

- [ ] **Step 3: Remove hoveredRowId from provider hooks**

In `data-table-provider.tsx`, remove `hoveredRowId` from the `useSelection` hook return type and selector.

#### Part C: Update DraggableRow to use ActionCell

- [ ] **Step 4: Update DraggableRow imports and props**

Replace the `alwaysShowActionHandles` prop with `actionColumnConfig` and remove all `hoveredRowId`/`setHoveredRowId` usage:

```typescript
import type { ActionColumnConfig, ColumnResizingConfig, RowActionsConfig } from '../data-table.types';
import { ActionCell } from './action-cell';

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
  hasEndOfHeaderContent?: boolean;
}
```

- [ ] **Step 5: Replace action cell rendering with ActionCell component**

In `DraggableRowComponent`, remove the `hoveredRowId` subscription and `setHoveredRowId` calls. Remove the `isVisible` computation. Replace the inline action cell rendering with the `ActionCell` component.

Remove these lines:
```typescript
// REMOVE
const hoveredRowId = useDataTableStore<TData, string | null>((state) => state.hoveredRowId);
const hasSelections = useDataTableStore<TData, boolean>((state) => state.hasSelections());
const isVisible = alwaysShowActionHandles || hasSelections || hoveredRowId === row.id;
```

Remove `setHoveredRowId` from the `useDataTableActions` destructuring.

Add `group/row` class to the `TableRow` for CSS hover targeting:

```typescript
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
```

Replace the action cell block (the `if (cell.column.id === 'actions')` block) with:

```typescript
if (cell.column.id === 'actions') {
  return (
    <ActionCell
      key={cell.id}
      rowId={row.id}
      isSelected={row.getIsSelected()}
      onToggleSelected={(value) => row.toggleSelected(value)}
      enableDragDrop={enableDragDrop}
      isSelectionEnabled={isSelectionEnabled}
      isDragDisabled={isDragDisabled}
      isMounted={isMounted}
      dragAttributes={isMounted && !isDragDisabled ? attributes : {}}
      dragListeners={isMounted && !isDragDisabled ? listeners : {}}
      cellSize={cell.column.getSize()}
      actionColumnConfig={actionColumnConfig}
    />
  );
}
```

Remove `onMouseEnter` and `onMouseLeave` handlers from `TableRow`.

- [ ] **Step 6: Fix the memo comparator**

Replace the memo comparator to include `row.original` and `actionColumnConfig`:

```typescript
export const DraggableRow = React.memo(DraggableRowComponent, (prevProps, nextProps) => {
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
```

- [ ] **Step 7: Verify build**

Run: `npx tsc --noEmit --pretty 2>&1 | head -50`

Expected: No type errors related to DraggableRow, ActionCell, or hoveredRowId.

- [ ] **Step 8: Commit (single atomic commit for all Part A+B+C changes)**

```bash
git add components/ui/data-table/components/action-cell.tsx components/ui/data-table/data-table.store.ts components/ui/data-table/data-table-provider.tsx components/ui/data-table/components/draggable-row.tsx
git commit -m "refactor(data-table): replace hoveredRowId with CSS hover, add ActionCell with per-element visibility, fix memo comparator"
```

---

### Task 3: Update DataTable and DataTableBody to pass ActionColumnConfig

**Files:**
- Modify: `components/ui/data-table/data-table.tsx`
- Modify: `components/ui/data-table/components/data-table-body.tsx`

- [ ] **Step 1: Update DataTable props — replace alwaysShowActionHandles with actionColumnConfig**

In `data-table.tsx`, replace:

```typescript
// REMOVE
alwaysShowActionHandles?: boolean;

// ADD
actionColumnConfig?: ActionColumnConfig;
```

Update the destructuring to use `actionColumnConfig` instead of `alwaysShowActionHandles`.

Import the new type:
```typescript
import type {
  ActionColumnConfig,
  ColumnOrderingConfig,
  ColumnResizingConfig,
  RowActionsConfig,
  SearchConfig,
} from './data-table.types';
```

- [ ] **Step 2: Update action column width calculation to use ActionColumnConfig**

Replace the action column width calculation:

```typescript
const finalColumns = useMemo(() => {
  const ACTION_ICON_WIDTH = 35;
  let actionColumnWidth = 0;

  // Calculate width from enabled action elements
  const showDragHandle = enableDragDrop && (actionColumnConfig?.dragHandle?.enabled !== false);
  const showCheckbox = isSelectionEnabled && (actionColumnConfig?.checkbox?.enabled !== false);

  if (showDragHandle) actionColumnWidth += ACTION_ICON_WIDTH;
  if (showCheckbox) actionColumnWidth += ACTION_ICON_WIDTH;

  if (actionColumnWidth === 0) return columns;

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
        {showCheckbox && (
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
}, [columns, enableDragDrop, isSelectionEnabled, actionColumnConfig]);
```

- [ ] **Step 3: Pass actionColumnConfig to DataTableBody instead of alwaysShowActionHandles**

In the render section, replace `alwaysShowActionHandles={alwaysShowActionHandles}` with `actionColumnConfig={actionColumnConfig}`:

```typescript
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
  actionColumnConfig={actionColumnConfig}
  hasEndOfHeaderContent={hasEndOfHeaderContent}
  hasRowActions={hasRowActions}
/>
```

- [ ] **Step 4: Update DataTableBody to pass actionColumnConfig to DraggableRow**

In `data-table-body.tsx`, replace `alwaysShowActionHandles` prop with `actionColumnConfig`:

```typescript
interface DataTableBodyProps<TData> {
  // ... existing props ...
  actionColumnConfig?: ActionColumnConfig;
  // REMOVE: alwaysShowActionHandles?: boolean;
}
```

And in the `DraggableRow` rendering:

```typescript
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
  actionColumnConfig={actionColumnConfig}
  hasEndOfHeaderContent={hasEndOfHeaderContent}
/>
```

- [ ] **Step 5: Update barrel exports**

In `index.ts`, add export for `ActionColumnConfig` and related types:

```typescript
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
  ActionColumnConfig,
  ActionElementConfig,
  ActionVisibility,
} from './data-table.types';
```

- [ ] **Step 6: Verify build**

Run: `npx tsc --noEmit --pretty 2>&1 | head -50`

Expected: No type errors. May see errors in consumer files (entity-data-table, entity-type-data-table) since they still use `alwaysShowActionHandles` — those are fixed in Chunk 3.

- [ ] **Step 7: Commit**

```bash
git add components/ui/data-table/data-table.tsx components/ui/data-table/components/data-table-body.tsx components/ui/data-table/index.ts
git commit -m "refactor(data-table): wire ActionColumnConfig through DataTable and DataTableBody"
```

---

## Chunk 2: Column Drag Fix & globalFilterFn Extraction

### Task 4: Add dedicated drag handles to column headers (keep single DndContext)

> **Note:** dnd-kit does NOT isolate nested DndContext instances — events bubble. We keep the existing single-DndContext architecture but add a dedicated drag handle button to column headers so drag and resize have separate interaction zones. The existing `axisRestrictionModifier` and column/row discrimination in `handleDragEnd` remain unchanged.

**Files:**
- Modify: `components/ui/data-table/components/draggable-column-header.tsx`

- [ ] **Step 1: Add a drag handle button to DraggableColumnHeader**

The key change: instead of making the entire header cell draggable (which conflicts with resize), add a small `GripVertical` button that owns the `attributes` and `listeners` from `useSortable`. The rest of the header cell handles clicks normally. The resize zone on the right edge stays separate.

In `draggable-column-header.tsx`, the key change is moving `attributes` and `listeners` from the `<TableHead>` cell onto a dedicated `<button>` with a `GripVertical` icon. The `<TableHead>` no longer receives drag props — it only handles `onClick` for the header popover. The resize zone on the right edge is unchanged.

```typescript
'use client';

import { TableHead } from '@riven/ui/table';
import { cn } from '@riven/utils';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { Header, flexRender } from '@tanstack/react-table';
import { GripVertical } from 'lucide-react';
import { useCallback } from 'react';
import { useDataTableActions, useDataTableStore } from '../data-table-provider';
import type { ColumnResizingConfig } from '../data-table.types';

interface DraggableColumnHeaderProps<TData, TValue> {
  header: Header<TData, TValue>;
  enableColumnOrdering: boolean;
  columnResizing?: ColumnResizingConfig;
  addingNewEntry: boolean;
  onHeaderClick?: (columnId: string, anchorEl: HTMLElement) => void;
}

export function DraggableColumnHeader<TData, TValue>({
  header,
  enableColumnOrdering,
  columnResizing,
  addingNewEntry,
  onHeaderClick,
}: DraggableColumnHeaderProps<TData, TValue>) {
  const isMounted = useDataTableStore<TData, boolean>((state) => state.isMounted);
  const resizingColumnId = useDataTableStore<TData, string | null>(
    (state) => state.resizingColumnId,
  );
  const { setResizingColumnId } = useDataTableActions<TData>();

  const isActionsColumn = header.id === 'actions';
  const isAnyColumnResizing = resizingColumnId !== null;
  const isThisColumnResizing = header.column.getIsResizing();

  // Sortable hook — disabled for actions column and during resize
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: header.id,
    disabled: !enableColumnOrdering || !isMounted || isActionsColumn || isAnyColumnResizing,
  });

  // Wrap resize handler to track state
  const wrappedResizeHandler = useCallback(
    (event: React.MouseEvent | React.TouchEvent) => {
      setResizingColumnId(header.id);

      const cleanup = () => {
        setResizingColumnId(null);
        document.removeEventListener('mouseup', cleanup);
        document.removeEventListener('touchend', cleanup);
      };

      document.addEventListener('mouseup', cleanup);
      document.addEventListener('touchend', cleanup);

      header.getResizeHandler()(event);
    },
    [header, setResizingColumnId],
  );

  const handleClick = useCallback(
    (e: React.MouseEvent<HTMLTableCellElement>) => {
      if (!onHeaderClick || isActionsColumn || isAnyColumnResizing || isDragging) return;
      onHeaderClick(header.id, e.currentTarget);
    },
    [onHeaderClick, header.id, isActionsColumn, isAnyColumnResizing, isDragging],
  );

  const style = isMounted
    ? {
        transform: isAnyColumnResizing ? undefined : CSS.Transform.toString(transform),
        transition: isAnyColumnResizing ? undefined : transition,
        opacity: isDragging ? 0.5 : 1,
        width: `${header.getSize()}px`,
      }
    : {
        width: `${header.getSize()}px`,
      };

  return (
    <TableHead
      ref={enableColumnOrdering && isMounted ? setNodeRef : undefined}
      style={style}
      key={header.id}
      className={cn(
        'relative border-l px-3 py-2 first:border-l-transparent',
        onHeaderClick && !isActionsColumn && 'cursor-pointer hover:bg-muted/50',
      )}
      onClick={handleClick}
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1">
          {/* Drag handle for column reordering — separate from resize zone */}
          {enableColumnOrdering && !isActionsColumn && isMounted && (
            <button
              className={cn(
                'cursor-grab text-muted-foreground/50 hover:text-muted-foreground active:cursor-grabbing',
                'shrink-0 -ml-1',
              )}
              {...attributes}
              {...listeners}
              onClick={(e) => e.stopPropagation()}
            >
              <GripVertical className="h-3.5 w-3.5" />
            </button>
          )}
          {header.isPlaceholder
            ? null
            : flexRender(header.column.columnDef.header, header.getContext())}
        </div>
      </div>

      {/* Resize handle — right edge, separate from drag */}
      {columnResizing?.enabled && header.column.getCanResize() && !addingNewEntry && (
        <div
          onMouseDown={wrappedResizeHandler}
          onTouchStart={wrappedResizeHandler}
          className={cn(
            'absolute top-0 right-0 h-full w-3 cursor-col-resize select-none',
            'group/resizer z-10',
          )}
          onClick={(e) => e.stopPropagation()}
        >
          <div
            className={cn(
              'absolute top-0 right-0 h-full w-[1px]',
              'bg-transparent group-hover/resizer:w-[2px] group-hover/resizer:bg-blue-500',
              'transition-all duration-150 group-active/resizer:bg-blue-600',
              isThisColumnResizing && 'w-[2px] bg-blue-600',
            )}
          />
        </div>
      )}
    </TableHead>
  );
}
```

The existing single `DndContext` in `data-table.tsx`, `axisRestrictionModifier`, and `handleDragEnd` column/row discrimination all remain unchanged. The only change is in `draggable-column-header.tsx` — moving drag listeners to a handle button.

- [ ] **Step 2: Verify build**

Run: `npx tsc --noEmit --pretty 2>&1 | head -50`

Expected: No type errors.

- [ ] **Step 3: Commit**

```bash
git add components/ui/data-table/components/draggable-column-header.tsx
git commit -m "refactor(data-table): add dedicated drag handle to column headers, separate from resize zone"
```

---

### Task 7: Extract globalFilterFn as stable module-level function

**Files:**
- Modify: `components/ui/data-table/data-table.tsx`

- [ ] **Step 1: Extract globalFilterFn to module level**

Move the filter function above the component, making it a factory:

```typescript
// ============================================================================
// Global Filter Function Factory
// ============================================================================

function createGlobalFilterFn<TData>(searchableColumns: string[]) {
  // Helper to get nested property value (e.g., "name.plural")
  const getNestedValue = (obj: any, path: string): any => {
    return path.split('.').reduce((current, prop) => current?.[prop], obj);
  };

  return (row: Row<TData>, _columnId: string, filterValue: string): boolean => {
    if (!filterValue || searchableColumns.length === 0) return true;

    const searchLower = filterValue.toLowerCase();

    return searchableColumns.some((colId) => {
      let value: any;

      if (colId.includes('.')) {
        value = getNestedValue(row.original, colId);
      } else {
        value = row.getValue(colId);
      }

      if (value == null) return false;

      if (typeof value === 'object' && !Array.isArray(value)) {
        return Object.values(value).some(
          (v) => v != null && String(v).toLowerCase().includes(searchLower),
        );
      }

      return String(value).toLowerCase().includes(searchLower);
    });
  };
}
```

- [ ] **Step 2: Use factory in component with useMemo**

Replace the inline `globalFilterFn` with:

```typescript
const stableGlobalFilterFn = useMemo(
  () => createGlobalFilterFn<TData>(search?.searchableColumns ?? []),
  [search?.searchableColumns],
);
```

Update the TanStack Table config to use `stableGlobalFilterFn` instead of `globalFilterFn`.

- [ ] **Step 3: Commit**

```bash
git add components/ui/data-table/data-table.tsx
git commit -m "perf(data-table): extract globalFilterFn as stable module-level factory"
```

---

## Chunk 3: Entity Consumer Decomposition

### Task 8: Extract buildEntityUpdatePayload utility

**Files:**
- Create: `components/feature-modules/entity/util/entity-payload.util.ts`
- Create: `components/feature-modules/entity/util/entity-payload.util.test.ts`

- [ ] **Step 1: Write the failing test**

```typescript
import {
  Entity,
  EntityPropertyType,
  SaveEntityRequest,
} from '@/lib/types/entity';
import { buildEntityUpdatePayload } from './entity-payload.util';

// Minimal mock entity factory
function createMockEntity(overrides: Partial<Entity> = {}): Entity {
  return {
    id: 'entity-1',
    identifierKey: 'name',
    workspaceId: 'ws-1',
    entityTypeId: 'type-1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    payload: {
      name: {
        payload: {
          type: EntityPropertyType.Attribute,
          value: 'Test Entity',
          schemaType: 'text',
        },
      },
      industry: {
        payload: {
          type: EntityPropertyType.Attribute,
          value: 'Finance',
          schemaType: 'select',
        },
      },
      employees: {
        payload: {
          type: EntityPropertyType.Relationship,
          relations: [
            { id: 'emp-1', label: 'Employee 1', key: 'employees', workspaceId: 'ws-1', sourceEntityId: 'entity-1', definitionId: 'rel-1', icon: { type: 'icon', colour: '#000' } },
          ],
        },
      },
    },
    ...overrides,
  } as Entity;
}

describe('buildEntityUpdatePayload', () => {
  it('preserves existing attribute payloads and applies the updated column', () => {
    const entity = createMockEntity();
    const result = buildEntityUpdatePayload(entity, 'industry', {
      payload: {
        type: EntityPropertyType.Attribute,
        value: 'Technology',
        schemaType: 'select',
      },
    });

    expect(result.id).toBe('entity-1');
    expect(result.payload.industry.payload).toEqual({
      type: EntityPropertyType.Attribute,
      value: 'Technology',
      schemaType: 'select',
    });
    // Name should be preserved
    expect(result.payload.name.payload).toEqual({
      type: EntityPropertyType.Attribute,
      value: 'Test Entity',
      schemaType: 'text',
    });
  });

  it('converts relationship payloads to ID arrays', () => {
    const entity = createMockEntity();
    const result = buildEntityUpdatePayload(entity, 'name', {
      payload: {
        type: EntityPropertyType.Attribute,
        value: 'Updated Name',
        schemaType: 'text',
      },
    });

    // Employees relationship should be converted to ID array
    expect(result.payload.employees.payload).toEqual({
      type: EntityPropertyType.Relationship,
      relations: ['emp-1'],
    });
  });

  it('applies relationship update correctly', () => {
    const entity = createMockEntity();
    const result = buildEntityUpdatePayload(entity, 'employees', {
      payload: {
        type: EntityPropertyType.Relationship,
        relations: ['emp-1', 'emp-2'],
      },
    });

    expect(result.payload.employees.payload).toEqual({
      type: EntityPropertyType.Relationship,
      relations: ['emp-1', 'emp-2'],
    });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx jest components/feature-modules/entity/util/entity-payload.util.test.ts --no-coverage`

Expected: FAIL — module not found.

- [ ] **Step 3: Write the implementation**

```typescript
import {
  Entity,
  EntityAttributeRequest,
  EntityPropertyType,
  isRelationshipPayload,
  SaveEntityRequest,
} from '@/lib/types/entity';

/**
 * Build a full SaveEntityRequest from an entity with one column updated.
 *
 * Reconstructs the entire payload (converting relationship payloads to ID arrays)
 * and overwrites the specified column with the new entry.
 */
export function buildEntityUpdatePayload(
  entity: Entity,
  columnId: string,
  entry: EntityAttributeRequest,
): SaveEntityRequest {
  const payload: Record<string, EntityAttributeRequest> = {};

  Object.entries(entity.payload).forEach(([key, value]) => {
    if (isRelationshipPayload(value.payload)) {
      payload[key] = {
        payload: {
          type: EntityPropertyType.Relationship,
          relations: value.payload.relations.map((rel) => rel.id),
        },
      };
    } else {
      payload[key] = {
        payload: {
          type: EntityPropertyType.Attribute,
          value: value.payload.value,
          schemaType: value.payload.schemaType,
        },
      };
    }
  });

  // Apply the updated column
  payload[columnId] = entry;

  return {
    id: entity.id,
    payload,
  };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx jest components/feature-modules/entity/util/entity-payload.util.test.ts --no-coverage`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add components/feature-modules/entity/util/entity-payload.util.ts components/feature-modules/entity/util/entity-payload.util.test.ts
git commit -m "feat(entity): extract buildEntityUpdatePayload utility with tests"
```

---

### Task 9: Extract useEntityInlineEdit hook

**Files:**
- Create: `components/feature-modules/entity/hooks/use-entity-inline-edit.ts`

- [ ] **Step 1: Create the hook**

Extract the `handleCellEdit` + `updateEntity` logic from `entity-data-table.tsx`:

```typescript
import { SchemaUUID } from '@/lib/types/common';
import {
  Entity,
  EntityAttributePrimitivePayload,
  EntityAttributeRelationPayloadReference,
  EntityAttributeRequest,
  EntityLink,
  EntityPropertyType,
  EntityType,
  RelationshipDefinition,
  SaveEntityRequest,
  SaveEntityResponse,
} from '@/lib/types/entity';
import { useCallback } from 'react';
import { useSaveEntityMutation } from './mutation/instance/use-save-entity-mutation';
import { buildEntityUpdatePayload } from '../util/entity-payload.util';
import { EntityRow, isDraftRow } from '../components/tables/entity-table-utils';

export function useEntityInlineEdit(
  workspaceId: string,
  entityType: EntityType,
  entities: Entity[],
) {
  const handleConflict = (_request: SaveEntityRequest, _response: SaveEntityResponse) => {};

  const { mutateAsync: saveEntity } = useSaveEntityMutation(
    workspaceId,
    entityType.id,
    undefined,
    handleConflict,
  );

  const handleCellEdit = useCallback(
    async (row: EntityRow, columnId: string, newValue: any, _oldValue: any): Promise<boolean> => {
      if (isDraftRow(row)) return false;
      const entity = entities.find((e) => e.id === row._entityId);
      if (!entity) return false;

      // Determine if updated column is an attribute or relationship
      const attributeDef: SchemaUUID | undefined = entityType.schema.properties?.[columnId];
      const relationshipDef: RelationshipDefinition | undefined = entityType.relationships?.find(
        (rel) => rel.id === columnId,
      );

      if (attributeDef) {
        const payloadEntry: EntityAttributePrimitivePayload = {
          type: EntityPropertyType.Attribute,
          value: newValue,
          schemaType: attributeDef.key,
        };

        const request = buildEntityUpdatePayload(entity, columnId, { payload: payloadEntry });
        const response = await saveEntity(request);
        return !response.errors && !!response.entity;
      }

      if (relationshipDef) {
        const relationship: EntityLink[] = newValue;
        const relationshipEntry: EntityAttributeRelationPayloadReference = {
          type: EntityPropertyType.Relationship,
          relations: relationship.map((rel) => rel.id),
        };

        const request = buildEntityUpdatePayload(entity, columnId, { payload: relationshipEntry });
        const response = await saveEntity(request);
        return !response.errors && !!response.entity;
      }

      return false;
    },
    [entities, entityType, saveEntity],
  );

  return { handleCellEdit };
}
```

- [ ] **Step 2: Commit**

```bash
git add components/feature-modules/entity/hooks/use-entity-inline-edit.ts
git commit -m "refactor(entity): extract useEntityInlineEdit hook"
```

---

### Task 10: Extract useEntityColumnConfig hook

**Files:**
- Create: `components/feature-modules/entity/hooks/use-entity-column-config.ts`

- [ ] **Step 1: Create the hook**

```typescript
import { EntityType } from '@/lib/types/entity';
import { useCallback } from 'react';
import { UseFormReturn } from 'react-hook-form';

/**
 * Manages entity column configuration persistence via react-hook-form.
 * Subscribes to DataTable events and persists column state to the form.
 */
export function useEntityColumnConfig(
  form: UseFormReturn<any>,
  entityType: EntityType,
) {
  const handleColumnResize = useCallback(
    (columnSizing: Record<string, number>) => {
      const current = form.getValues('columnConfiguration');
      const updatedOverrides = { ...current.overrides };
      Object.entries(columnSizing).forEach(([key, width]) => {
        updatedOverrides[key] = { ...updatedOverrides[key], width };
      });
      form.setValue('columnConfiguration.overrides', updatedOverrides, { shouldDirty: true });
    },
    [form],
  );

  const handleHideColumn = useCallback(
    (columnId: string) => {
      const current = form.getValues('columnConfiguration');
      form.setValue(
        'columnConfiguration.overrides',
        {
          ...current.overrides,
          [columnId]: { ...current.overrides[columnId], visible: false },
        },
        { shouldDirty: true },
      );
    },
    [form],
  );

  const handleToggleVisibility = useCallback(
    (columnId: string) => {
      if (columnId === entityType.identifierKey) return;
      const current = form.getValues('columnConfiguration');
      const currentVisible = current.overrides[columnId]?.visible !== false;
      form.setValue(
        'columnConfiguration.overrides',
        {
          ...current.overrides,
          [columnId]: { ...current.overrides[columnId], visible: !currentVisible },
        },
        { shouldDirty: true },
      );
    },
    [form, entityType.identifierKey],
  );

  const handleReorder = useCallback(
    (newOrder: string[]) => {
      form.setValue('columnConfiguration.order', newOrder, { shouldDirty: true });
    },
    [form],
  );

  const handleShowAll = useCallback(() => {
    const current = form.getValues('columnConfiguration');
    const updatedOverrides = { ...current.overrides };
    Object.keys(updatedOverrides).forEach((key) => {
      updatedOverrides[key] = { ...updatedOverrides[key], visible: true };
    });
    form.setValue('columnConfiguration.overrides', updatedOverrides, { shouldDirty: true });
  }, [form]);

  const handleHideAll = useCallback(() => {
    const current = form.getValues('columnConfiguration');
    const updatedOverrides = { ...current.overrides };
    Object.keys(updatedOverrides).forEach((key) => {
      if (key === entityType.identifierKey) return;
      updatedOverrides[key] = { ...updatedOverrides[key], visible: false };
    });
    form.setValue('columnConfiguration.overrides', updatedOverrides, { shouldDirty: true });
  }, [form, entityType.identifierKey]);

  return {
    handleColumnResize,
    handleHideColumn,
    handleToggleVisibility,
    handleReorder,
    handleShowAll,
    handleHideAll,
  };
}
```

- [ ] **Step 2: Commit**

```bash
git add components/feature-modules/entity/hooks/use-entity-column-config.ts
git commit -m "refactor(entity): extract useEntityColumnConfig hook"
```

---

### Task 11: Extract useEntityTableData hook

**Files:**
- Create: `components/feature-modules/entity/hooks/use-entity-table-data.ts`

- [ ] **Step 1: Create the hook**

```typescript
import { Entity, EntityType } from '@/lib/types/entity';
import { useMemo } from 'react';
import {
  EntityRow,
  generateColumnsFromEntityType,
  generateSearchConfigFromEntityType,
  applyColumnOrdering,
  transformEntitiesToRows,
} from '../components/tables/entity-table-utils';

/**
 * Transforms entity data into table rows, generates columns, and builds search config.
 */
export function useEntityTableData(
  entityType: EntityType,
  entities: Entity[],
  isDraftMode: boolean,
) {
  const rowData = useMemo(() => {
    const sortedEntities = [...entities].sort((a, b) => {
      const dateA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
      const dateB = b.createdAt ? new Date(b.createdAt).getTime() : 0;
      return dateA - dateB;
    });

    const rows = transformEntitiesToRows(sortedEntities);

    if (isDraftMode) {
      const draftRow: EntityRow = {
        _entityId: '_draft',
        _isDraft: true,
      };
      return [...rows, draftRow];
    }

    return rows;
  }, [entities, isDraftMode]);

  const columns = useMemo(() => {
    const generatedColumns = generateColumnsFromEntityType(entityType, { enableEditing: true });
    return applyColumnOrdering(generatedColumns, entityType.columnConfiguration);
  }, [entityType.schema, entityType.relationships, entityType.columnConfiguration]);

  const searchableColumns = useMemo<string[]>(() => {
    return generateSearchConfigFromEntityType(entityType);
  }, [entityType.schema]);

  return { rowData, columns, searchableColumns };
}
```

- [ ] **Step 2: Commit**

```bash
git add components/feature-modules/entity/hooks/use-entity-table-data.ts
git commit -m "refactor(entity): extract useEntityTableData hook with narrowed memo dependencies"
```

---

### Task 12: Rewrite entity-data-table.tsx using extracted hooks and ActionColumnConfig

**Files:**
- Modify: `components/feature-modules/entity/components/tables/entity-data-table.tsx`

- [ ] **Step 1: Rewrite the component using extracted hooks**

The component should now be significantly smaller (~200 lines instead of ~515), composing the extracted hooks:

```typescript
'use client';

import { ActionColumnConfig, ColumnResizingConfig, DataTable, DataTableProvider } from '@/components/ui/data-table';
import { Form } from '@/components/ui/form';
import type { QueryFilter } from '@/lib/types/models/QueryFilter';
import {
  Entity,
  EntityAttributeDefinition,
  EntityType,
  EntityTypeDefinition,
  RelationshipDefinition,
} from '@/lib/types/entity';
import { debounce } from '@/lib/util/debounce.util';
import type { ClassNameProps } from '@riven/utils';
import { cn } from '@riven/utils';

import { Button } from '@riven/ui/button';
import { Row } from '@tanstack/react-table';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { MoreHorizontal, Plus } from 'lucide-react';
import { FC, useCallback, useMemo, useRef, useState } from 'react';
import { useConfigFormState } from '../../context/configuration-provider';
import { useEntityDraft } from '../../context/entity-provider';
import { useEntityColumnConfig } from '../../hooks/use-entity-column-config';
import { useEntityInlineEdit } from '../../hooks/use-entity-inline-edit';
import { useEntityTableData } from '../../hooks/use-entity-table-data';
import { EntityQueryBuilder } from '../query/entity-query-builder';
import { EntityTypeHeader } from '../ui/entity-type-header';
import { AttributeFormModal } from '../ui/modals/type/attribute-form-modal';
import { DeleteDefinitionModal } from '../ui/modals/type/delete-definition-modal';
import { ColumnHeaderPopover } from './column-header-popover';
import { ColumnVisibilityPopover } from './column-visibility-popover';
import { EntityDraftRow } from './entity-draft-row';
import EntityActionBar from './entity-table-action-bar';
import { EntityRow, isDraftRow } from './entity-table-utils';

export interface Props extends ClassNameProps {
  entityType: EntityType;
  entities: Entity[];
  loadingEntities?: boolean;
  workspaceId: string;
}

export const EntityDataTable: FC<Props> = ({
  entityType,
  entities,
  loadingEntities,
  className,
  workspaceId,
}) => {
  const { isDraftMode, enterDraftMode } = useEntityDraft();
  const { form } = useConfigFormState();

  // Extracted hooks
  const { rowData, columns, searchableColumns } = useEntityTableData(entityType, entities, isDraftMode);
  const { handleCellEdit } = useEntityInlineEdit(workspaceId, entityType, entities);
  const {
    handleColumnResize,
    handleHideColumn,
    handleToggleVisibility,
    handleReorder,
    handleShowAll,
    handleHideAll,
  } = useEntityColumnConfig(form, entityType);

  // Column header popover state
  const [activePopoverColumnId, setActivePopoverColumnId] = useState<string | null>(null);
  const [popoverAnchorEl, setPopoverAnchorEl] = useState<HTMLElement | null>(null);
  const [visibilityPopoverOpen, setVisibilityPopoverOpen] = useState(false);

  // Attribute/relationship modal state
  const [attributeDialogOpen, setAttributeDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [editingDefinition, setEditingDefinition] = useState<
    EntityAttributeDefinition | RelationshipDefinition | undefined
  >();
  const [deletingDefinition, setDeletingDefinition] = useState<EntityTypeDefinition | undefined>();

  // Header click handler
  const handleHeaderClick = useCallback(
    (columnId: string, anchorEl: HTMLElement) => {
      if (isDraftMode) return;
      setActivePopoverColumnId(columnId);
      setPopoverAnchorEl(anchorEl);
    },
    [isDraftMode],
  );

  // End-of-header content
  const endOfHeaderContent = useMemo(
    () => (
      <div className="flex items-center gap-1">
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              onClick={() => {
                setEditingDefinition(undefined);
                setAttributeDialogOpen(true);
              }}
              variant="ghost"
              size="icon"
              className="size-7"
              disabled={isDraftMode}
            >
              <Plus className="size-4" />
              <span className="sr-only">Add property</span>
            </Button>
          </TooltipTrigger>
          <TooltipContent side="top">Add property</TooltipContent>
        </Tooltip>
        <Tooltip>
          <ColumnVisibilityPopover
            entityType={entityType}
            columnConfiguration={form.getValues('columnConfiguration')}
            onToggleVisibility={handleToggleVisibility}
            onReorder={handleReorder}
            onShowAll={handleShowAll}
            onHideAll={handleHideAll}
            open={visibilityPopoverOpen}
            onOpenChange={setVisibilityPopoverOpen}
          >
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="size-7"
                disabled={isDraftMode}
              >
                <MoreHorizontal className="size-4" />
                <span className="sr-only">Manage columns</span>
              </Button>
            </TooltipTrigger>
          </ColumnVisibilityPopover>
          <TooltipContent side="top">Column settings</TooltipContent>
        </Tooltip>
      </div>
    ),
    [entityType, form, visibilityPopoverOpen, handleToggleVisibility, handleReorder, handleShowAll, handleHideAll, isDraftMode],
  );

  const emptyMessage = loadingEntities
    ? 'Loading entities...'
    : `No ${entityType.name.plural} found.`;

  const searchConfig = useMemo(
    () => ({
      enabled: true,
      searchableColumns,
      placeholder: `Search ${entityType.name.plural.toLowerCase()}...`,
      disabled: isDraftMode,
    }),
    [searchableColumns, isDraftMode, entityType.name.plural],
  );

  const customRowRenderer = useCallback(
    (row: Row<EntityRow>) => {
      if (isDraftRow(row.original)) {
        return <EntityDraftRow key="_draft" entityType={entityType} row={row} />;
      }
      return null;
    },
    [entityType],
  );

  const debouncedResizeHandler = useRef(
    debounce((columnSizing: Record<string, number>) => {
      handleColumnResize(columnSizing);
    }, 500),
  ).current;

  const columnResizingConfig: ColumnResizingConfig = useMemo(
    () => ({ enabled: true, columnResizeMode: 'onChange' as const }),
    [],
  );

  const getRowId = useCallback((row: EntityRow, _index: number) => row._entityId, []);

  // Action column config: hover-reveal for both drag and checkbox
  const actionColumnConfig: ActionColumnConfig = useMemo(
    () => ({
      dragHandle: { enabled: true, visibility: 'hover-or-selected' },
      checkbox: { enabled: true, visibility: 'hover-or-selected' },
    }),
    [],
  );

  // Query filter state
  const [_queryFilter, setQueryFilter] = useState<QueryFilter | undefined>();
  const handleQueryFilterChange = useCallback((filter: QueryFilter | undefined) => {
    setQueryFilter(filter);
  }, []);

  const filterContent = useMemo(
    () => (
      <EntityQueryBuilder
        entityType={entityType}
        value={_queryFilter}
        onChange={handleQueryFilterChange}
      />
    ),
    [entityType, _queryFilter, handleQueryFilterChange],
  );

  return (
    <Form {...form}>
      <div className="w-full min-w-0 space-y-4">
        <EntityTypeHeader>
          <div className="text-sm text-muted-foreground italic">
            Manage your entities and their data
          </div>
        </EntityTypeHeader>

        <DataTableProvider
          initialData={rowData}
          getRowId={getRowId}
          onCellEdit={handleCellEdit}
          onColumnWidthsChange={(columnSizing) => debouncedResizeHandler(columnSizing)}
        >
          <DataTable
            columns={columns}
            rowSelection={{
              enabled: true,
              clearOnFilterChange: true,
              actionComponent: ({ selectedRows, clearSelection }) => (
                <EntityActionBar
                  selectedRows={selectedRows}
                  clearSelection={clearSelection}
                  workspaceId={workspaceId}
                  entityTypeId={entityType.id}
                />
              ),
            }}
            enableDragDrop
            actionColumnConfig={actionColumnConfig}
            getRowId={(row) => row._entityId}
            search={searchConfig}
            filterContent={filterContent}
            columnResizing={columnResizingConfig}
            emptyMessage={emptyMessage}
            className={cn(className)}
            enableInlineEdit={true}
            customRowRenderer={customRowRenderer}
            addingNewEntry={isDraftMode}
            onHeaderClick={handleHeaderClick}
            endOfHeaderContent={endOfHeaderContent}
            scrollContainerClassName="max-h-[calc(100dvh-18rem)]"
            footerContent={
              !isDraftMode ? (
                <button
                  type="button"
                  onClick={enterDraftMode}
                  className="flex w-full items-center gap-1.5 border-t border-border/40 px-3 py-1.5 text-sm text-muted-foreground/50 transition-colors hover:bg-muted/30 hover:text-muted-foreground"
                >
                  <Plus className="size-3.5" />
                  <span>New {entityType.name.singular}</span>
                </button>
              ) : undefined
            }
          />

          <ColumnHeaderPopover
            columnId={activePopoverColumnId}
            entityType={entityType}
            workspaceId={workspaceId}
            anchorEl={popoverAnchorEl}
            onClose={() => {
              setActivePopoverColumnId(null);
              setPopoverAnchorEl(null);
            }}
            onEditProperties={(def) => {
              setEditingDefinition(def.definition);
              setAttributeDialogOpen(true);
            }}
            onDelete={(def) => {
              setDeletingDefinition(def);
              setDeleteDialogOpen(true);
            }}
            onInsert={(_position, _refColumnId) => {
              setEditingDefinition(undefined);
              setAttributeDialogOpen(true);
            }}
            onHide={handleHideColumn}
          />
        </DataTableProvider>

        <AttributeFormModal
          dialog={{ open: attributeDialogOpen, setOpen: setAttributeDialogOpen }}
          type={entityType}
          selectedAttribute={editingDefinition}
        />

        {deletingDefinition && (
          <DeleteDefinitionModal
            workspaceId={workspaceId}
            dialog={{ open: deleteDialogOpen, setOpen: setDeleteDialogOpen }}
            type={entityType}
            definition={deletingDefinition}
          />
        )}
      </div>
    </Form>
  );
};
```

- [ ] **Step 2: Verify build**

Run: `npx tsc --noEmit --pretty 2>&1 | head -50`

Expected: No type errors.

- [ ] **Step 3: Commit**

```bash
git add components/feature-modules/entity/components/tables/entity-data-table.tsx
git commit -m "refactor(entity): decompose entity-data-table into focused hooks, use ActionColumnConfig"
```

---

### Task 13: Update entity-type-data-table.tsx to use ActionColumnConfig

**Files:**
- Modify: `components/feature-modules/entity/components/types/entity-type-data-table.tsx`

- [ ] **Step 1: Add ActionColumnConfig with always-visible drag, no checkbox**

Import `ActionColumnConfig` and add a config object:

```typescript
import { DataTable, DataTableProvider, ActionColumnConfig } from '@/components/ui/data-table';
```

Add the config inside the component:

```typescript
const actionColumnConfig: ActionColumnConfig = useMemo(
  () => ({
    dragHandle: { enabled: true, visibility: 'always' },
    checkbox: { enabled: false },
  }),
  [],
);
```

Pass it to DataTable:

```typescript
<DataTable
  columns={columns}
  enableDragDrop
  actionColumnConfig={actionColumnConfig}
  alwaysShowActionHandles={true}  // REMOVE THIS LINE
  // ... rest of props
/>
```

Remove `alwaysShowActionHandles={true}` and replace with `actionColumnConfig={actionColumnConfig}`.

- [ ] **Step 2: Verify build**

Run: `npx tsc --noEmit --pretty 2>&1 | head -50`

Expected: No type errors.

- [ ] **Step 3: Commit**

```bash
git add components/feature-modules/entity/components/types/entity-type-data-table.tsx
git commit -m "refactor(entity-type): use ActionColumnConfig with always-visible drag handle, no checkbox"
```

---

## Chunk 4: Store Tests & Regression Tests

### Task 14: Comprehensive data-table store tests

**Files:**
- Create: `components/ui/data-table/data-table.store.test.ts`

- [ ] **Step 1: Write store tests for edit lifecycle**

```typescript
import { createDataTableStore } from './data-table.store';

type TestRow = { id: string; name: string; value: number };

function createTestStore(overrides: Partial<Parameters<typeof createDataTableStore<TestRow>>[0]> = {}) {
  return createDataTableStore<TestRow>({
    initialData: [
      { id: '1', name: 'Row 1', value: 10 },
      { id: '2', name: 'Row 2', value: 20 },
      { id: '3', name: 'Row 3', value: 30 },
    ],
    getRowId: (row) => row.id,
    ...overrides,
  });
}

describe('DataTableStore', () => {
  describe('edit lifecycle', () => {
    it('starts editing a cell', async () => {
      const store = createTestStore();
      await store.getState().startEditing('1', 'name', 'Row 1');

      const state = store.getState();
      expect(state.editingCell).toEqual({ rowId: '1', columnId: 'name' });
      expect(state.pendingValue).toBe('Row 1');
    });

    it('updates pending value', async () => {
      const store = createTestStore();
      await store.getState().startEditing('1', 'name', 'Row 1');
      store.getState().updatePendingValue('Updated');

      expect(store.getState().pendingValue).toBe('Updated');
    });

    it('cancels editing and clears state', async () => {
      const store = createTestStore();
      await store.getState().startEditing('1', 'name', 'Row 1');
      store.getState().cancelEditing();

      const state = store.getState();
      expect(state.editingCell).toBeNull();
      expect(state.pendingValue).toBeNull();
    });

    it('commits edit and calls onCellEdit callback', async () => {
      const onCellEdit = jest.fn().mockResolvedValue(true);
      const store = createTestStore({ onCellEdit });

      await store.getState().startEditing('1', 'name', 'Row 1');
      store.getState().updatePendingValue('Updated');
      await store.getState().commitEdit();

      expect(onCellEdit).toHaveBeenCalledWith(
        expect.objectContaining({ id: '1' }),
        'name',
        'Updated',
        'Row 1',
      );

      const state = store.getState();
      expect(state.editingCell).toBeNull();
      expect(state.isSaving).toBe(false);
      // Focus should remain on saved cell
      expect(state.focusedCell).toEqual({ rowId: '1', columnId: 'name' });
    });

    it('handles failed commit', async () => {
      const onCellEdit = jest.fn().mockResolvedValue(false);
      const store = createTestStore({ onCellEdit });

      await store.getState().startEditing('1', 'name', 'Row 1');
      store.getState().updatePendingValue('Updated');
      await store.getState().commitEdit();

      const state = store.getState();
      expect(state.editingCell).toBeNull();
      expect(state.saveError).toBe('Save failed');
      expect(state.focusedCell).toEqual({ rowId: '1', columnId: 'name' });
    });

    it('does not commit when not editing', async () => {
      const onCellEdit = jest.fn();
      const store = createTestStore({ onCellEdit });
      await store.getState().commitEdit();
      expect(onCellEdit).not.toHaveBeenCalled();
    });

    it('does not commit when no callback registered', async () => {
      const store = createTestStore();
      await store.getState().startEditing('1', 'name', 'Row 1');
      // Should not throw
      await store.getState().commitEdit();
    });

    it('exitToFocused clears edit state but keeps focus', async () => {
      const store = createTestStore();
      await store.getState().startEditing('1', 'name', 'Row 1');
      store.getState().exitToFocused();

      const state = store.getState();
      expect(state.editingCell).toBeNull();
      expect(state.focusedCell).toEqual({ rowId: '1', columnId: 'name' });
    });
  });

  describe('focus navigation', () => {
    // NOTE: focusNextCell/focusPrevCell/focusAdjacentCell require a mock TanStack Table
    // instance set via setTableInstance(). They early-return without one. Testing those
    // requires integration-level setup — skip for now, cover in future integration tests.

    it('sets focused cell', () => {
      const store = createTestStore();
      store.getState().setFocusedCell('1', 'name');
      expect(store.getState().focusedCell).toEqual({ rowId: '1', columnId: 'name' });
    });

    it('clears focus', () => {
      const store = createTestStore();
      store.getState().setFocusedCell('1', 'name');
      store.getState().clearFocus();
      expect(store.getState().focusedCell).toBeNull();
    });
  });

  describe('row reordering', () => {
    it('reorders rows correctly', () => {
      const store = createTestStore();
      store.getState().reorderRows(0, 2);
      const data = store.getState().tableData;
      expect(data[0].id).toBe('2');
      expect(data[1].id).toBe('3');
      expect(data[2].id).toBe('1');
    });
  });

  describe('derived state', () => {
    it('isDragDropEnabled returns false when search is active', () => {
      const store = createTestStore();
      store.getState().setGlobalFilter('test');
      expect(store.getState().isDragDropEnabled(true)).toBe(false);
    });

    it('isDragDropEnabled returns true when no filters', () => {
      const store = createTestStore();
      expect(store.getState().isDragDropEnabled(true)).toBe(true);
    });

    it('isDragDropEnabled returns false when prop is false', () => {
      const store = createTestStore();
      expect(store.getState().isDragDropEnabled(false)).toBe(false);
    });

    it('hasSelections returns true when rows selected', () => {
      const store = createTestStore();
      store.getState().setRowSelection({ '0': true });
      expect(store.getState().hasSelections()).toBe(true);
    });

    it('hasSelections returns false when no rows selected', () => {
      const store = createTestStore();
      expect(store.getState().hasSelections()).toBe(false);
    });

    it('getSelectedCount counts only true values', () => {
      const store = createTestStore();
      store.getState().setRowSelection({ '0': true, '1': true });
      expect(store.getState().getSelectedCount()).toBe(2);
    });

    it('getActiveFilterCount counts only enabled filters with values', () => {
      const store = createTestStore();
      store.getState().setActiveFilters({ name: 'test', empty: '' });
      store.getState().setEnabledFilters(new Set(['name', 'empty']));
      expect(store.getState().getActiveFilterCount()).toBe(1);
    });
  });

  describe('selection actions', () => {
    it('clearSelection empties selection', () => {
      const store = createTestStore();
      store.getState().setRowSelection({ '0': true, '1': true });
      store.getState().clearSelection();
      expect(store.getState().rowSelection).toEqual({});
    });
  });

  describe('commit callback registration', () => {
    it('registers and calls commit callback', () => {
      const store = createTestStore();
      const callback = jest.fn();
      store.getState().registerCommitCallback(callback);
      store.getState().requestCommit();
      expect(callback).toHaveBeenCalledTimes(1);
    });

    it('does nothing when no callback registered', () => {
      const store = createTestStore();
      // Should not throw
      store.getState().requestCommit();
    });

    it('clears callback on cancel', async () => {
      const store = createTestStore();
      const callback = jest.fn();
      store.getState().registerCommitCallback(callback);
      await store.getState().startEditing('1', 'name', 'Row 1');
      store.getState().cancelEditing();
      store.getState().requestCommit();
      expect(callback).not.toHaveBeenCalled();
    });
  });
});
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `npx jest components/ui/data-table/data-table.store.test.ts --no-coverage`

Expected: All tests PASS.

- [ ] **Step 3: Commit**

```bash
git add components/ui/data-table/data-table.store.test.ts
git commit -m "test(data-table): comprehensive store tests for edit lifecycle, navigation, derived state"
```

---

### Task 15: Flesh out relationship-constraint.util tests

**Files:**
- Modify: `components/feature-modules/entity/util/relationship-constraint.util.test.ts` (already exists as untracked)

- [ ] **Step 1: Read the existing test file and implementation**

Read both files to understand what's already covered:
- `components/feature-modules/entity/util/relationship-constraint.util.ts`
- `components/feature-modules/entity/util/relationship-constraint.util.test.ts`

- [ ] **Step 2: Add any missing edge case tests**

Based on the implementation, add tests for:
- Empty constraint arrays
- Null/undefined relationship definitions
- Boundary cardinality checks
- Self-referential relationships (if applicable)

(Exact test code depends on the current implementation — read first, then fill gaps.)

- [ ] **Step 3: Run tests**

Run: `npx jest components/feature-modules/entity/util/relationship-constraint.util.test.ts --no-coverage`

Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add components/feature-modules/entity/util/relationship-constraint.util.test.ts
git commit -m "test(entity): flesh out relationship-constraint.util tests"
```

---

### Task 16: Full build verification and cleanup

**Files:**
- All modified files

- [ ] **Step 1: Run TypeScript type check**

Run: `npx tsc --noEmit --pretty`

Expected: No errors.

- [ ] **Step 2: Run linter**

Run: `npm run lint`

Expected: No errors (warnings acceptable).

- [ ] **Step 3: Run all tests**

Run: `npm test`

Expected: All tests pass.

- [ ] **Step 4: Run build**

Run: `npm run build`

Expected: Build succeeds.

- [ ] **Step 5: Final commit if any cleanup was needed**

```bash
git add -A
git commit -m "chore: cleanup and verify data-table architecture overhaul"
```
