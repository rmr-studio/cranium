'use client';

import { Button } from '@riven/ui/button';
import { cn } from '@riven/utils';
import { X } from 'lucide-react';
import { TooltipProvider } from '../../tooltip';
import { useCellSelectionOverview } from '../data-table-provider';
import type { SelectionActionProps } from '../data-table.types';

interface DataTableSelectionBarProps<TData> {
  actionComponent?: React.ComponentType<SelectionActionProps<TData>>;
}

export function DataTableSelectionBar<TData>({
  actionComponent: CustomActionComponent,
}: DataTableSelectionBarProps<TData>) {
  const { hasSelections, clearSelection, selectedCount, selectedRows } =
    useCellSelectionOverview<TData>();

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
