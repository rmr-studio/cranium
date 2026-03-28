'use client';

import type { DraggableAttributes } from '@dnd-kit/core';
import type { DraggableSyntheticListeners } from '@dnd-kit/core';
import { TableCell } from '@riven/ui/table';
import { cn } from '@riven/utils';
import { GripVertical } from 'lucide-react';
import { Checkbox } from '@/components/ui/checkbox';
import type { ActionColumnConfig, ActionVisibility } from '@/components/ui/data-table/data-table.types';
import type { ReactNode } from 'react';

interface ActionCellProps<TData> {
  isSelected: boolean;
  onToggleSelected: (value: boolean) => void;
  enableDragDrop: boolean;
  isSelectionEnabled: boolean;
  isDragDisabled: boolean;
  isMounted: boolean;
  dragAttributes: DraggableAttributes;
  dragListeners: DraggableSyntheticListeners | undefined;
  cellSize: number;
  actionColumnConfig?: ActionColumnConfig;
  extra?: ReactNode;
}

function getVisibilityClass(visibility: ActionVisibility, isSelected: boolean) {
  if (visibility === 'always') return 'opacity-100';
  if (isSelected) return 'opacity-100';
  return 'opacity-0 group-hover/row:opacity-100 transition-opacity duration-150';
}

export function ActionCell<TData>({
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
  extra,
}: ActionCellProps<TData>) {
  const dragConfig = actionColumnConfig?.dragHandle;
  const checkboxConfig = actionColumnConfig?.checkbox;

  const showDragHandle = enableDragDrop && dragConfig?.enabled !== false;
  const showCheckbox = isSelectionEnabled && checkboxConfig?.enabled !== false;

  const dragVisibility = dragConfig?.visibility ?? 'hover-or-selected';
  const checkboxVisibility = checkboxConfig?.visibility ?? 'hover-or-selected';

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
            type="button"
            className={cn(
              'cursor-grab text-muted-foreground transition-colors hover:text-foreground active:cursor-grabbing',
              isDragDisabled && 'cursor-not-allowed opacity-30',
              getVisibilityClass(dragVisibility, isSelected),
            )}
            {...(!isDragDisabled ? dragAttributes : {})}
            {...(!isDragDisabled ? dragListeners : {})}
            onClick={(e) => e.stopPropagation()}
          >
            <GripVertical className="h-4 w-4" />
          </button>
        )}
        {showCheckbox && (
          <span className={getVisibilityClass(checkboxVisibility, isSelected)}>
            <Checkbox
              checked={isSelected}
              onCheckedChange={(value) => onToggleSelected(!!value)}
              aria-label="Select row"
              onClick={(e) => e.stopPropagation()}
            />
          </span>
        )}
        {extra}
      </div>
    </TableCell>
  );
}
