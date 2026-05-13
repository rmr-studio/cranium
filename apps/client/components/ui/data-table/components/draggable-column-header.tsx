'use client';

import { TableHead } from '@cranium/ui/table';
import { cn } from '@cranium/utils';
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

  // Actions column should not be draggable - it stays fixed at the left
  const isActionsColumn = header.id === 'actions';

  // Check if ANY column is currently being resized (not just this one)
  const isAnyColumnResizing = resizingColumnId !== null;
  const isThisColumnResizing = header.column.getIsResizing();

  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: header.id,
    disabled: !enableColumnOrdering || !isMounted || isActionsColumn || isAnyColumnResizing,
  });

  // Wrap the resize handler to track resize state in the store
  // Attaches global mouseup/touchend listener to clear state when resize ends
  // Uses a small delay before re-enabling drag to prevent accidental reorder on small columns
  const wrappedResizeHandler = useCallback(
    (event: React.MouseEvent | React.TouchEvent) => {
      setResizingColumnId(header.id);

      const cleanup = () => {
        // Debounce before re-enabling drag to prevent accidental reorder
        // when resizing small columns (mouse release can trigger drag)

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
        'group/header relative border-l px-3 py-2 first:border-l-transparent',
        onHeaderClick && !isActionsColumn && 'cursor-pointer hover:bg-muted/50',
      )}
      onClick={handleClick}
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1">
          {/* Drag handle for column reordering — visible on hover */}
          {enableColumnOrdering && !isActionsColumn && isMounted && (
            <button
              className={cn(
                'cursor-grab text-muted-foreground/50 hover:text-muted-foreground active:cursor-grabbing',
                'shrink-0 -ml-1 opacity-0 transition-opacity duration-150 group-hover/header:opacity-100',
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
