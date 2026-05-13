'use client';

import { MoreVertical } from 'lucide-react';
import { Button } from '@cranium/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@cranium/ui/dropdown-menu';
import { cn } from '@cranium/utils';
import type { RowActionsConfig } from '../data-table.types';

interface RowActionsMenuProps<TData> {
  row: TData;
  config: RowActionsConfig<TData>;
}

export function RowActionsMenu<TData>({ row, config }: RowActionsMenuProps<TData>) {
  if (!config.enabled) {
    return null;
  }

  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="sm"
          className="h-8 w-8 p-0"
          onClick={(e) => e.stopPropagation()}
        >
          <span className="sr-only">Open menu</span>
          <MoreVertical className="h-4 w-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {config.menuLabel && (
          <>
            <DropdownMenuLabel>{config.menuLabel}</DropdownMenuLabel>
            <DropdownMenuSeparator />
          </>
        )}
        {config.actions.map((action, index) => (
          <div key={index}>
            <DropdownMenuItem
              disabled={action.disabled?.(row) ?? false}
              onClick={(e) => {
                e.stopPropagation();
                action.onClick(row);
              }}
              className={cn(
                action.variant === 'destructive' && 'text-destructive focus:text-destructive',
              )}
            >
              {action.icon && <action.icon className="mr-2 h-4 w-4" />}
              {action.label}
            </DropdownMenuItem>
            {action.separator && <DropdownMenuSeparator />}
          </div>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
