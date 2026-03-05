import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@riven/ui';
import { cn } from '@riven/utils';
import { FC } from 'react';

type Row = { key: string; value: string };

interface Props {
  title?: string;
  description?: string;
  data?: unknown;
  className?: string;
}

function normaliseValue(value: unknown): string {
  if (value == null) return '-';
  if (typeof value === 'number') return Number.isFinite(value) ? value.toLocaleString() : '-';
  if (typeof value === 'string') return value;
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  if (value instanceof Date) return value.toLocaleString();
  return JSON.stringify(value, null, 2);
}

function toRows(data: unknown): Row[] {
  if (!data) return [];

  if (Array.isArray(data)) {
    if (data.length === 0) return [];
    // Consume array of scalars
    if (data.every((item) => typeof item !== 'object' || item == null)) {
      return data.map((value, index) => ({
        key: `Item ${index + 1}`,
        value: normaliseValue(value),
      }));
    }

    // Array of objects -> flatten each top-level key
    const rows: Row[] = [];
    data.forEach((item, index) => {
      if (item && typeof item === 'object') {
        Object.entries(item as Record<string, unknown>).forEach(([key, value]) => {
          rows.push({
            key: `${key} (${index + 1})`,
            value: normaliseValue(value),
          });
        });
      } else {
        rows.push({ key: `Item ${index + 1}`, value: normaliseValue(item) });
      }
    });
    return rows;
  }

  if (typeof data === 'object') {
    return Object.entries(data as Record<string, unknown>).map(([key, value]) => ({
      key,
      value: normaliseValue(value),
    }));
  }

  return [{ key: 'Value', value: normaliseValue(data) }];
}

export const DataSummaryTable: FC<Props> = ({ title, description, data, className }) => {
  const rows = toRows(data);
  return (
    <Card className={cn('transition-shadow duration-150 hover:shadow-lg', className)}>
      {(title || description) && (
        <CardHeader>
          {title ? <CardTitle className="text-base font-semibold">{title}</CardTitle> : null}
          {description ? <CardDescription>{description}</CardDescription> : null}
        </CardHeader>
      )}
      <CardContent className="space-y-2 text-sm">
        {rows.length === 0 ? (
          <p className="text-muted-foreground">No data</p>
        ) : (
          <dl className="grid grid-cols-1 gap-2">
            {rows.map((row, idx) => (
              <div key={`${row.key}-${idx}`} className="flex items-start justify-between gap-4">
                <dt className="text-xs tracking-wide text-muted-foreground uppercase">
                  {row.key.replace(/_/g, ' ')}
                </dt>
                <dd className="text-right text-sm whitespace-pre-wrap text-foreground">
                  {row.value}
                </dd>
              </div>
            ))}
          </dl>
        )}
      </CardContent>
    </Card>
  );
};
