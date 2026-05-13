'use client';

import { Button } from '@cranium/ui/button';
import { cn } from '@cranium/utils';
import { X } from 'lucide-react';
import { TooltipProvider } from '../../tooltip';
import { useCellSelectionOverview } from '../data-table-provider';
import type { SelectionActionProps } from '../data-table.types';

interface DataTableSelectionBarProps<TData> {
  actionComponent?: React.ComponentType<SelectionActionProps<TData>>;
  /** External override: selected count (bypasses TanStack store) */
  externalSelectedCount?: number;
  /** External override: clear selection handler (bypasses TanStack store) */
  externalClearSelection?: () => void;
}

export function DataTableSelectionBar<TData>({
  actionComponent: CustomActionComponent,
  externalSelectedCount,
  externalClearSelection,
}: DataTableSelectionBarProps<TData>) {
  const internal = useCellSelectionOverview<TData>();

  const hasExternal = externalSelectedCount !== undefined;
  const selectedCount = hasExternal ? externalSelectedCount : internal.selectedCount;
  const clearSelection = externalClearSelection ?? internal.clearSelection;
  const hasSelections = hasExternal ? externalSelectedCount > 0 : internal.hasSelections;
  // In external mode, selectedRows from TanStack may be empty (e.g. server-side select-all).
  // The action component receives them for display but should use its own selection source for actions.
  const selectedRows = internal.selectedRows;

  // Don't render if no selections
  if (!hasSelections) {
    return null;
  }

  return (
    <TooltipProvider>
      <div
        className={cn(
          'absolute top-0 left-0 rounded-md border bg-accent py-0.5 text-primary-foreground',
          'z-10 shadow-lg',
        )}
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center">
            <div className="border-r px-1">
              <Button
                variant="ghost"
                size="xs"
                onClick={clearSelection}
                className="px-1! text-primary hover:bg-primary/10"
              >
                <X className="size-3" />
                {selectedCount} selected
              </Button>
            </div>
            {CustomActionComponent && (
              <CustomActionComponent selectedRows={selectedRows} clearSelection={clearSelection} />
            )}
          </div>
        </div>
      </div>
    </TooltipProvider>
  );
}
