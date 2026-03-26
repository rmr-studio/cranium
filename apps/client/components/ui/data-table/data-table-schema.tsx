'use client';

import { Schema } from '@/lib/types/common';
import { DataFormat, DataType } from '@/lib/types/common';
import { AccessorKeyColumnDef, ColumnDef } from '@tanstack/react-table';
import { useMemo } from 'react';
import { ColumnFilter, DataTable, FilterOption, SearchConfig } from '.';
import { Badge } from '../badge';

/**
 * Schema-Driven Data Table
 *
 * Automatically configures columns, filters, and search based on a Schema definition.
 *
 * Features:
 * - Auto-generates columns from schema properties
 * - Auto-configures filters based on field types
 * - Auto-configures searchable columns
 * - Handles nested objects and arrays
 * - Respects schema constraints (required, unique, protected)
 */

interface SchemaDataTableProps<TData extends Record<string, any>> {
  schema: Schema;
  data: TData[];
  getRowId?: (row: TData, index: number) => string;
  enableDragDrop?: boolean;
  onReorder?: (data: TData[]) => void;
  onRowClick?: (row: any) => void;
  className?: string;
  emptyMessage?: string;
  // Override auto-generated configs
  customColumns?: AccessorKeyColumnDef<TData, any>[];
  customFilters?: ColumnFilter<TData>[];
  customSearchableColumns?: string[];
  // Feature toggles
  enableSearch?: boolean;
  enableFilters?: boolean;
  enableSorting?: boolean;
}

/**
 * Maps Schema DataType and DataFormat to appropriate filter type
 */
function getFilterTypeFromSchema(schema: Schema): ColumnFilter<any>['type'] | null {
  // Protected fields shouldn't be filterable
  if (schema._protected) return null;

  switch (schema.type) {
    case DataType.String:
      // Use select for short strings, text for others
      return 'text';

    case DataType.Number:
      if (schema.format === DataFormat.Currency) {
        return 'number-range';
      }
      return 'number-range';

    case DataType.Boolean:
      return 'boolean';

    case DataType.Object:
    case DataType.Array:
      // Complex types not directly filterable
      return null;

    default:
      return null;
  }
}

/**
 * Determines if a field should be searchable based on schema
 */
function isSearchableField(schema: Schema): boolean {
  // Only search text-based fields
  switch (schema.type) {
    case DataType.String:
      return true;
    default:
      return false;
  }
}

/**
 * Formats cell value based on schema type and format
 */
function formatCellValue(value: any, schema: Schema): React.ReactNode {
  if (value === null || value === undefined) {
    return <span className="text-muted-foreground">—</span>;
  }

  switch (schema.type) {
    case DataType.String:
      if (schema.format === DataFormat.Email) {
        return (
          <a href={`mailto:${value}`} className="text-primary hover:underline">
            {value}
          </a>
        );
      }
      if (schema.format === DataFormat.Phone) {
        return (
          <a href={`tel:${value}`} className="text-primary hover:underline">
            {value}
          </a>
        );
      }
      if (schema.format === DataFormat.Date) {
        return new Date(value).toLocaleDateString();
      }
      if (schema.format === DataFormat.Datetime) {
        return new Date(value).toLocaleString();
      }
      return <span>{value}</span>;

    case DataType.Number:
      if (schema.format === DataFormat.Currency) {
        return <span>${value.toLocaleString()}</span>;
      }
      return <span>{value.toLocaleString()}</span>;

    case DataType.Boolean:
      return <Badge variant={value ? 'default' : 'secondary'}>{value ? 'Yes' : 'No'}</Badge>;

    case DataType.Array:
      if (Array.isArray(value)) {
        return <span>{value.length} items</span>;
      }
      return <span className="text-muted-foreground">—</span>;

    case DataType.Object:
      return <span className="text-muted-foreground">Object</span>;

    default:
      return <span>{String(value)}</span>;
  }
}

/**
 * Generates column definitions from schema
 */
function generateColumnsFromSchema<TData extends Record<string, any>>(
  schema: Schema,
): AccessorKeyColumnDef<TData, any>[] {
  if (!schema.properties) return [];

  return Object.entries(schema.properties).map(([key, fieldSchema]) => {
    const column: AccessorKeyColumnDef<TData, any> = {
      accessorKey: key,
      header: fieldSchema.label,
      cell: ({ row }) => {
        const value = row.getValue(key);
        return formatCellValue(value, fieldSchema);
      },
      // Add metadata for potential use
      meta: {
        schema: fieldSchema,
        required: fieldSchema.required,
        unique: fieldSchema.unique,
        protected: fieldSchema._protected,
      },
    };

    return column;
  });
}

/**
 * Generates filter configuration from schema
 */
function generateFiltersFromSchema<TData extends Record<string, any>>(
  schema: Schema,
): ColumnFilter<TData>[] {
  if (!schema.properties) return [];

  const filters: ColumnFilter<TData>[] = [];

  Object.entries(schema.properties).forEach(([key, fieldSchema]) => {
    const filterType = getFilterTypeFromSchema(fieldSchema);
    if (!filterType) return;

    const filter: ColumnFilter<TData> = {
      column: key as any,
      type: filterType,
      label: fieldSchema.label,
    };

    // Add placeholder for text filters
    if (filterType === 'text') {
      filter.placeholder = `Filter by ${fieldSchema.label?.toLowerCase()}...`;
    }

    filters.push(filter);
  });

  return filters;
}

/**
 * Generates search configuration from schema
 */
function generateSearchConfigFromSchema<TData extends Record<string, any>>(
  schema: Schema,
): string[] {
  if (!schema.properties) return [];

  return Object.entries(schema.properties)
    .filter(([_, fieldSchema]) => isSearchableField(fieldSchema))
    .map(([key]) => key);
}

/**
 * Schema-driven DataTable component
 */
export function SchemaDataTable<TData extends Record<string, any>>({
  schema,
  data,
  getRowId,
  enableDragDrop = false,
  onReorder,
  onRowClick,
  className,
  emptyMessage = 'No data available.',
  customColumns,
  customFilters,
  customSearchableColumns,
  enableSearch = true,
  enableFilters = true,
  enableSorting = false,
}: SchemaDataTableProps<TData>) {
  // Generate columns from schema
  const columns = useMemo(() => {
    return customColumns ?? generateColumnsFromSchema<TData>(schema);
  }, [schema, customColumns]);

  // Generate search config from schema
  const searchConfig = useMemo(() => {
    if (!enableSearch) return undefined;
    const searchableColumns =
      customSearchableColumns ?? generateSearchConfigFromSchema<TData>(schema);
    if (searchableColumns.length === 0) return undefined;

    return {
      enabled: true,
      searchableColumns: searchableColumns as any,
      placeholder: `Search ${schema.label?.toLowerCase()}...`,
    } as SearchConfig<TData>;
  }, [schema, customSearchableColumns, enableSearch]);

  return (
    <DataTable
      columns={columns}
      data={data}
      getRowId={getRowId}
      enableDragDrop={enableDragDrop}
      onReorder={onReorder}
      onRowClick={onRowClick}
      className={className}
      emptyMessage={emptyMessage}
      search={searchConfig}
      enableSorting={enableSorting}
    />
  );
}

/**
 * Utility to extract options from data for select filters
 */
export function extractUniqueValuesForFilter<TData extends Record<string, any>>(
  data: TData[],
  columnKey: string,
): FilterOption[] {
  const uniqueValues = new Set<string | number | boolean>();

  data.forEach((row) => {
    const value = row[columnKey];
    if (value !== null && value !== undefined) {
      uniqueValues.add(value);
    }
  });

  return Array.from(uniqueValues)
    .sort()
    .map((value) => ({
      label: String(value),
      value: value,
    }));
}

/**
 * Enhanced Schema DataTable with auto-detected select filters
 */
export function EnhancedSchemaDataTable<TData extends Record<string, any>>(
  props: SchemaDataTableProps<TData> & {
    autoDetectSelectFilters?: boolean;
    selectFilterThreshold?: number; // Max unique values to convert to select
  },
) {
  const {
    schema,
    data,
    autoDetectSelectFilters = true,
    selectFilterThreshold = 10,
    customFilters,
    ...rest
  } = props;

  // Generate enhanced filters with auto-detected selects
  const enhancedFilters = useMemo(() => {
    if (!autoDetectSelectFilters || customFilters) return customFilters;

    const baseFilters = generateFiltersFromSchema<TData>(schema);

    return baseFilters.map((filter) => {
      // Only enhance text filters
      if (filter.type !== 'text') return filter;

      // Extract unique values
      const uniqueValues = extractUniqueValuesForFilter(data, String(filter.column));

      // Convert to select if within threshold
      if (uniqueValues.length > 0 && uniqueValues.length <= selectFilterThreshold) {
        return {
          ...filter,
          type: 'select' as const,
          options: uniqueValues,
        };
      }

      return filter;
    });
  }, [schema, data, autoDetectSelectFilters, selectFilterThreshold, customFilters]);

  return <SchemaDataTable {...rest} schema={schema} data={data} customFilters={enhancedFilters} />;
}
