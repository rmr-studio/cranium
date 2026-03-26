'use client';

import { Button } from '@riven/ui/button';
import { ColumnDef } from '@tanstack/react-table';
import { ArrowUpDown } from 'lucide-react';
import { Badge } from '../badge';

// Helper to create a sortable column header
export function createSortableHeader<TData>(title: string, accessorKey: string) {
  return ({ column }: any) => {
    return (
      <Button
        variant="ghost"
        onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}
        className="-ml-2 h-8 px-2"
      >
        {title}
        <ArrowUpDown className="ml-2 h-4 w-4" />
      </Button>
    );
  };
}

// Helper to create a badge cell
export function createBadgeCell<TData>(
  variant: 'default' | 'secondary' | 'destructive' | 'outline' = 'outline',
) {
  return ({ getValue }: any) => {
    const value = getValue();
    return value ? (
      <Badge variant={variant} className="text-xs">
        {value}
      </Badge>
    ) : null;
  };
}

// Helper to create a text cell with optional formatting
export function createTextCell<TData>(className?: string, formatter?: (value: any) => string) {
  return ({ getValue }: any) => {
    const value = getValue();
    const displayValue = formatter ? formatter(value) : value;
    return displayValue ? <span className={className}>{displayValue}</span> : null;
  };
}

// Helper to create a cell with icon and text
export function createIconTextCell<TData>(getIcon?: (row: TData) => React.ReactNode) {
  return ({ row, getValue }: any) => {
    const value = getValue();
    const icon = getIcon?.(row.original);
    return (
      <div className="flex items-center gap-2">
        {icon && <span className="text-muted-foreground">{icon}</span>}
        <span className="font-medium">{value}</span>
      </div>
    );
  };
}

// Helper to create an actions column
export function createActionsColumn<TData>(
  actions: Array<{
    label: string;
    onClick: (row: TData) => void;
    variant?: 'default' | 'destructive';
  }>,
): ColumnDef<TData> {
  return {
    id: 'actions',
    header: () => <span className="sr-only">Actions</span>,
    cell: ({ row }) => (
      <div className="flex items-center justify-end gap-2">
        {actions.map((action, index) => (
          <Button
            key={index}
            variant={action.variant === 'destructive' ? 'destructive' : 'ghost'}
            size="sm"
            onClick={(e) => {
              e.stopPropagation();
              action.onClick(row.original);
            }}
          >
            {action.label}
          </Button>
        ))}
      </div>
    ),
  };
}

// Helper to create a checkbox column for row selection
export function createCheckboxColumn<TData>(): ColumnDef<TData> {
  return {
    id: 'select',
    header: ({ table }) => (
      <input
        type="checkbox"
        checked={table.getIsAllPageRowsSelected()}
        onChange={(e) => table.toggleAllPageRowsSelected(e.target.checked)}
        aria-label="Select all"
        className="cursor-pointer"
      />
    ),
    cell: ({ row }) => (
      <input
        type="checkbox"
        checked={row.getIsSelected()}
        onChange={(e) => row.toggleSelected(e.target.checked)}
        aria-label="Select row"
        className="cursor-pointer"
        onClick={(e) => e.stopPropagation()}
      />
    ),
    enableSorting: false,
    enableHiding: false,
  };
}

// Helper to format dates
export const formatDate = (date: Date | string | null | undefined): string => {
  if (!date) return '';
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
};

// Helper to format currency
export const formatCurrency = (
  amount: number | null | undefined,
  currency: string = 'USD',
): string => {
  if (amount === null || amount === undefined) return '';
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
  }).format(amount);
};

// Helper to truncate text
export const truncateText = (text: string, maxLength: number = 50): string => {
  if (!text || text.length <= maxLength) return text;
  return `${text.slice(0, maxLength)}...`;
};
