'use client';

import { cn } from '@/lib/utils';
import { ArrowUpDown } from 'lucide-react';
import { useState } from 'react';

interface Column {
  key: string;
  label: string;
}

interface Row {
  [key: string]: string | boolean;
}

interface ComparisonTableProps {
  columns: Column[];
  rows: Row[];
  highlightColumn?: string;
}

function CellValue({ value }: { value: string | boolean }) {
  if (typeof value === 'boolean') {
    return (
      <span className={cn('font-medium', value ? 'text-success' : 'text-muted-foreground')}>
        {value ? 'Yes' : 'No'}
      </span>
    );
  }
  if (value === 'Partial') {
    return <span className="text-warning font-medium">Partial</span>;
  }
  return <span>{value}</span>;
}

export function ComparisonTable({ columns, rows, highlightColumn }: ComparisonTableProps) {
  const [sortKey, setSortKey] = useState<string | null>(null);
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc');

  const handleSort = (key: string) => {
    if (sortKey === key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir('asc');
    }
  };

  const sorted = sortKey
    ? [...rows].sort((a, b) => {
        const av = String(a[sortKey] ?? '');
        const bv = String(b[sortKey] ?? '');
        return sortDir === 'asc' ? av.localeCompare(bv) : bv.localeCompare(av);
      })
    : rows;

  return (
    <div className="my-6 overflow-x-auto rounded-lg border border-border">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border bg-muted/50">
            {columns.map((col) => (
              <th
                key={col.key}
                scope="col"
                className={cn(
                  'cursor-pointer px-4 py-3 text-left font-mono text-xs font-bold uppercase tracking-widest text-muted-foreground transition-colors hover:text-foreground',
                  highlightColumn === col.key && 'bg-primary/5 text-foreground',
                )}
                onClick={() => handleSort(col.key)}
              >
                <span className="inline-flex items-center gap-1.5">
                  {col.label}
                  <ArrowUpDown className="size-3" />
                </span>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {sorted.map((row, i) => (
            <tr key={String(row[columns[0]?.key] ?? i)} className="border-b border-border last:border-0">
              {columns.map((col) => (
                <td
                  key={col.key}
                  className={cn(
                    'px-4 py-3',
                    highlightColumn === col.key && 'bg-primary/5',
                  )}
                >
                  <CellValue value={row[col.key] ?? ''} />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
