import { Checkbox } from '@/components/ui/checkbox';
import { ColumnFilter, FilterOption } from '@/components/ui/data-table';
import {
  createAttributeRenderer,
  createRelationshipRenderer,
} from '@/components/ui/data-table/components/cells/edit-renderers';
import { DEFAULT_COLUMN_WIDTH } from '@/components/ui/data-table/data-table';
import { ColumnEditConfig } from '@/components/ui/data-table/data-table.types';
import { IconCell } from '@/components/ui/icon/icon-cell';
import { DataFormat, DataType, SchemaType, SchemaUUID } from '@/lib/types/common';
import {
  Entity,
  EntityAttribute,
  EntityLink,
  EntityRelationshipCardinality,
  EntityType,
  EntityTypeAttributeColumn,
  isRelationshipPayload,
  RelationshipDefinition,
} from '@/lib/types/entity';
import { iconFormSchema } from '@/lib/util/form/common/icon.form';
import { buildFieldSchema } from '@/lib/util/form/entity-instance-validation.util';
import { zodResolver } from '@hookform/resolvers/zod';
import { Badge } from '@riven/ui/badge';
import { toTitleCase } from '@riven/utils';
import { AccessorKeyColumnDef, Cell } from '@tanstack/react-table';
import { ArrowUpRight } from 'lucide-react';
import Link from 'next/link';
import { ReactNode } from 'react';
import { useForm } from 'react-hook-form';
import { isUUID } from 'validator';
import { z } from 'zod';

export const entityReferenceFormSchema = z.array(
  z
    .object({
      id: z.string().refine(isUUID, { message: 'Invalid UUID' }),
      workspaceId: z.string().refine(isUUID, { message: 'Invalid UUID' }),
      sourceEntityId: z.string().refine(isUUID, { message: 'Invalid UUID' }),
      fieldId: z.string().refine(isUUID, { message: 'Invalid UUID' }),
      label: z.string().min(1, { message: 'Label cannot be empty' }),
      key: z.string().min(1, { message: 'Key cannot be empty' }),
    })
    .extend(iconFormSchema.shape),
);

// Row type for entity instance data table
// Discriminated union to safely handle both entity rows and draft rows
export type EntityRow =
  | {
      _entityId: string;
      _isDraft: false;
      _entity: Entity;
      [attributeId: string]: any; // Dynamic fields from payload
    }
  | {
      _entityId: string;
      _isDraft: true;
      _entity?: undefined; // Explicitly undefined for draft rows
      [attributeId: string]: any; // Dynamic fields for draft values
    };

/**
 * Type guard to check if a row is a draft row
 */
export function isDraftRow(row: EntityRow): row is Extract<EntityRow, { _isDraft: true }> {
  return row._isDraft === true;
}

/**
 * Type guard to check if a row is an entity row (not draft)
 */
export function isEntityRow(row: EntityRow): row is Extract<EntityRow, { _isDraft: false }> {
  return row._isDraft === false;
}

/**
 * Transform entities into flat row objects for the data table
 */
export function transformEntitiesToRows(entities: Entity[]): EntityRow[] {
  return entities
    .filter((entity) => !!entity.payload)
    .map((entity) => {
      const row: EntityRow = {
        _entityId: entity.id,
        _isDraft: false,
        _entity: entity,
      };

      Object.entries(entity.payload).forEach(([key, value]) => {
        const { payload } = value;
        if (isRelationshipPayload(payload)) {
          row[key] = payload.relations;
          return;
        }
        row[key] = payload.value;
      });

      return row;
    });
}

/**
 * Format entity attribute value based on data type and format
 */
export function formatEntityAttributeValue(value: any, schema: any): ReactNode {
  // Handle null/undefined
  if (value === null || value === undefined) {
    return <span className="text-muted-foreground">-</span>;
  }

  const dataType = schema.type as DataType;
  const dataFormat = schema.format as DataFormat | undefined;

  // STRING type formatting
  if (dataType === DataType.String) {
    const strValue = String(value);

    if (dataFormat === DataFormat.Url) {
      return (
        <a
          href={strValue}
          target="_blank"
          rel="noopener noreferrer"
          className="text-primary hover:underline"
        >
          {strValue}
        </a>
      );
    }

    if (dataFormat === DataFormat.Date) {
      try {
        const date = new Date(strValue);
        return <span>{date.toLocaleDateString()}</span>;
      } catch {
        return <span>{strValue}</span>;
      }
    }

    if (dataFormat === DataFormat.Datetime) {
      try {
        const date = new Date(strValue);
        return <span>{date.toLocaleString()}</span>;
      } catch {
        return <span>{strValue}</span>;
      }
    }

    return <span>{strValue}</span>;
  }

  // NUMBER type formatting
  if (dataType === DataType.Number) {
    const numValue = Number(value);

    if (dataFormat === DataFormat.Currency) {
      return (
        <span>
          {numValue.toLocaleString('en-US', {
            style: 'currency',
            currency: 'USD',
          })}
        </span>
      );
    }

    if (dataFormat === DataFormat.Percentage) {
      return <span>{numValue}%</span>;
    }

    return <span>{numValue.toLocaleString()}</span>;
  }

  // BOOLEAN type formatting
  if (dataType === DataType.Boolean) {
    return (
      <Badge variant={'secondary'} className="gap-2 px-2 py-1">
        <Checkbox checked={Boolean(value)} disabled={true} />
        <span>{value ? 'Yes' : 'No'}</span>
      </Badge>
    );
  }

  // ARRAY type formatting (multi-select enums, etc.)
  if (dataType === DataType.Array) {
    if (Array.isArray(value)) {
      if (value.length === 0) {
        return <div className="flex h-full grow text-muted-foreground" />;
      }

      // Render each item as its own badge
      return (
        <div className="flex flex-wrap gap-1">
          {value.map((item, idx) => (
            <Badge key={idx} variant="secondary" className="font-normal">
              {String(item)}
            </Badge>
          ))}
        </div>
      );
    }
    return <Badge variant="outline">Array</Badge>;
  }

  // OBJECT type formatting
  if (dataType === DataType.Object) {
    return <Badge variant="outline">Object</Badge>;
  }

  // Fallback for unknown types
  return <span>{String(value)}</span>;
}

// ============================================================================
// Value Equality Helpers (for edit change detection)
// ============================================================================

/**
 * Normalize empty values for comparison
 */
function normalizeEmpty<T>(val: T): T | null {
  if (val === null || val === undefined || val === '' || (Array.isArray(val) && val.length === 0)) {
    return null;
  }
  return val;
}

/**
 * Compare arrays order-independently
 */
function arraysEqual(arr1: unknown[], arr2: unknown[]): boolean {
  if (arr1.length !== arr2.length) return false;
  const sorted1 = [...arr1].sort();
  const sorted2 = [...arr2].sort();
  return JSON.stringify(sorted1) === JSON.stringify(sorted2);
}

/**
 * Creates an equality function for attribute values based on schema type
 */
export function createAttributeEqualityFn(
  schema: SchemaUUID,
): (oldValue: unknown, newValue: unknown) => boolean {
  const schemaType = schema.key;

  return (value1: unknown, value2: unknown): boolean => {
    const normalized1 = normalizeEmpty(value1);
    const normalized2 = normalizeEmpty(value2);

    // Both are empty - no change
    if (normalized1 === null && normalized2 === null) {
      return true;
    }

    // One is empty, other is not - changed
    if (normalized1 === null || normalized2 === null) {
      return false;
    }

    // For multi-select, compare arrays (order-independent)
    if (
      schemaType === SchemaType.MultiSelect &&
      Array.isArray(normalized1) &&
      Array.isArray(normalized2)
    ) {
      return arraysEqual(normalized1, normalized2);
    }

    // For checkbox, ensure boolean comparison
    if (schemaType === SchemaType.Checkbox) {
      return Boolean(normalized1) === Boolean(normalized2);
    }

    // For numbers, handle string vs number comparison
    if (
      schemaType === SchemaType.Number ||
      schemaType === SchemaType.Currency ||
      schemaType === SchemaType.Percentage
    ) {
      const num1 =
        typeof normalized1 === 'string' ? parseFloat(normalized1) : (normalized1 as number);
      const num2 =
        typeof normalized2 === 'string' ? parseFloat(normalized2) : (normalized2 as number);

      // Handle NaN cases
      if (isNaN(num1) && isNaN(num2)) return true;
      if (isNaN(num1) || isNaN(num2)) return false;

      return num1 === num2;
    }

    // For other types, use JSON.stringify comparison
    return JSON.stringify(normalized1) === JSON.stringify(normalized2);
  };
}

/**
 * Creates an equality function for relationship values based on cardinality
 */
export function createRelationshipEqualityFn(
  relationship: RelationshipDefinition,
): (oldValue: EntityLink[], newValue: EntityLink[]) => boolean {
  const isSingleSelect =
    relationship.cardinalityDefault === EntityRelationshipCardinality.OneToOne ||
    relationship.cardinalityDefault === EntityRelationshipCardinality.ManyToOne;

  return (value1: EntityLink[], value2: EntityLink[]): boolean => {
    const normalized1 = normalizeEmpty(value1);
    const normalized2 = normalizeEmpty(value2);

    // Both are empty - no change
    if (normalized1 === null && normalized2 === null) {
      return true;
    }

    // One is empty, other is not - changed
    if (normalized1 === null || normalized2 === null) {
      return false;
    }

    // For single-select, simple string comparison
    if (isSingleSelect) {
      return JSON.stringify(normalized1[0]) === JSON.stringify(normalized2[0]);
    }

    // For multi-select, order-independent array comparison
    if (Array.isArray(normalized1) && Array.isArray(normalized2)) {
      return arraysEqual(normalized1, normalized2);
    }

    return JSON.stringify(normalized1) === JSON.stringify(normalized2);
  };
}

// ============================================================================
// Column Generation
// ============================================================================

/**
 * Generate columns from entity type schema
 */
export function generateColumnsFromEntityType(
  entityType: EntityType,
  options?: { enableEditing?: boolean },
): AccessorKeyColumnDef<EntityRow>[] {
  const columns: AccessorKeyColumnDef<EntityRow>[] = [];

  if (!entityType.schema.properties) {
    return columns;
  }

  // Generate attribute columns
  Object.entries(entityType.schema.properties).forEach(([attributeId, schema]) => {
    // Create edit config if editing is enabled
    const editConfig: ColumnEditConfig<EntityRow, any, any> | undefined = options?.enableEditing
      ? {
          enabled: true,
          createFormInstance: (cell: Cell<EntityRow, any>) => {
            const value = cell.getValue();

            // Build Zod schema for this attribute based on its type
            const fieldSchema = buildFieldSchema(schema);
            const formSchema = z.object({
              value: fieldSchema,
            });

            return useForm({
              resolver: zodResolver(formSchema) as any,
              defaultValues: {
                value: value ?? null,
              },
            });
          },
          render: createAttributeRenderer<EntityRow>(schema),
          parseValue: (val: any) => val,
          formatValue: (val: any) => val,
          isEqual: createAttributeEqualityFn(schema),
        }
      : undefined;

    columns.push({
      accessorKey: attributeId,
      size:
        entityType.columns?.find((col) => col.key === attributeId)?.width ?? DEFAULT_COLUMN_WIDTH,
      header: (_) => {
        const { icon, label } = schema;
        const { type, colour } = icon;

        return (
          <div className="flex items-center">
            <IconCell readonly type={type} colour={colour} className="mr-2 size-4" />
            <span>{label}</span>
          </div>
        );
      },
      cell: ({ row }) => {
        const value = row.getValue(attributeId);
        return formatEntityAttributeValue(value, schema);
      },
      enableSorting: true,
      meta: {
        edit: editConfig,
        displayMeta: {
          required: schema.required,
          unique: schema.unique,
          protected: schema._protected,
        },
      },
    });
  });

  // Generate relationship columns
  entityType.relationships?.forEach((relationship) => {
    // Create edit config if editing is enabled
    const editConfig: ColumnEditConfig<EntityRow, EntityLink[], EntityLink[]> | undefined =
      options?.enableEditing
        ? {
            enabled: true,
            createFormInstance: (cell: Cell<EntityRow, EntityLink[]>) => {
              const value: EntityLink[] = cell.getValue() || [];

              const formSchema = z.object({
                value: entityReferenceFormSchema,
              });

              return useForm({
                resolver: zodResolver(formSchema),
                defaultValues: {
                  value: value,
                },
                mode: 'onBlur',
              });
            },
            render: createRelationshipRenderer<EntityRow>(relationship),
            parseValue: (val: EntityLink[]) => val,
            formatValue: (val: EntityLink[]) => val,
            isEqual: createRelationshipEqualityFn(relationship),
          }
        : undefined;

    columns.push({
      accessorKey: relationship.id,
      size:
        entityType.columns?.find((col) => col.key === relationship.id)?.width ??
        DEFAULT_COLUMN_WIDTH,
      header: () => {
        const { icon, name } = relationship;
        return (
          <div className="flex items-center">
            <IconCell readonly type={icon.type} colour={icon.colour} className="mr-2 size-4" />
            <span>{name}</span>
          </div>
        );
      },
      cell: ({ row }) => {
        const value: EntityLink[] = row.getValue(relationship.id);
        if (!value) return null;
        if (Array.isArray(value)) {
          if (value.length === 0) {
            return null;
          }

          return (
            <div className="flex flex-wrap gap-1">
              {value.map((item: EntityLink) => {
                const { icon, label, definitionId, id, workspaceId, key } = item;
                const { type, colour } = icon;

                return (
                  <Link
                    href={`/dashboard/workspace/${workspaceId}/entity/${key}/${id}`}
                    key={`${definitionId}-${id}`}
                    onClick={(e) => e.stopPropagation()}
                  >
                    <Badge
                      variant="secondary"
                      className="group font-normal transition-all hover:bg-border"
                    >
                      <IconCell readonly type={type} colour={colour} className="mr-2 size-4" />
                      <span className="group-hover:underline">{label}</span>
                      <ArrowUpRight className="ml-2 size-4 opacity-0 transition-opacity group-hover:opacity-100" />
                    </Badge>
                  </Link>
                );
              })}
            </div>
          );
        }
        return <span>{String(value)}</span>;
      },
      enableSorting: false,
      meta: {
        edit: editConfig,
        displayMeta: {
          protected: relationship._protected,
        },
      },
    });
  });

  return columns;
}

/**
 * Apply column ordering based on entity type columns array
 */
export function applyColumnOrdering(
  columns: AccessorKeyColumnDef<EntityRow>[],
  columnsOrder: EntityTypeAttributeColumn[],
): AccessorKeyColumnDef<EntityRow>[] {
  const orderedColumns: AccessorKeyColumnDef<EntityRow>[] = [];
  const columnMap = new Map(columns.map((col) => [col['accessorKey'], col]));

  // Add columns in order array sequence
  columnsOrder.forEach((orderItem) => {
    const column = columnMap.get(orderItem.key);
    if (column) {
      orderedColumns.push(column);
      columnMap.delete(orderItem.key);
    }
  });

  // Add remaining columns (not in order array)
  columnMap.forEach((column) => {
    orderedColumns.push(column);
  });

  return orderedColumns;
}

/**
 * Extract unique values from entities for a specific attribute
 */
export function extractUniqueAttributeValues(
  entities: Entity[],
  attributeId: string,
): FilterOption[] {
  const uniqueValues = new Set<string>();

  entities.forEach((entity) => {
    const value = entity.payload?.[attributeId];
    if (value !== null && value !== undefined) {
      uniqueValues.add(String(value));
    }
  });

  return Array.from(uniqueValues)
    .sort()
    .map((value) => ({
      label: toTitleCase(value),
      value: value,
    }));
}

/**
 * Generate filters from entity type schema based on actual entity data
 */
export function generateFiltersFromEntityType(
  entityType: EntityType,
  entities: Entity[],
  selectFilterThreshold: number = 10,
): ColumnFilter<EntityRow>[] {
  const filters: ColumnFilter<EntityRow>[] = [];

  if (!entityType.schema.properties) {
    return filters;
  }

  Object.entries(entityType.schema.properties).forEach(([attributeId, schema]) => {
    // Skip protected fields
    if (schema._protected) {
      return;
    }

    const dataType = schema.type as DataType;
    const label = schema.label || attributeId;

    // STRING type filters
    if (dataType === DataType.String) {
      const uniqueValues = extractUniqueAttributeValues(entities, attributeId);

      // Use select filter for low-cardinality attributes
      if (uniqueValues.length > 0 && uniqueValues.length <= selectFilterThreshold) {
        filters.push({
          column: attributeId,
          type: 'select',
          label,
          options: uniqueValues,
        });
      } else if (uniqueValues.length > selectFilterThreshold) {
        // Use text filter for high-cardinality attributes
        filters.push({
          column: attributeId as keyof EntityRow & string,
          type: 'text',
          label,
          placeholder: `Filter by ${label.toLowerCase()}...`,
        });
      }
    }

    // NUMBER type filters
    if (dataType === DataType.Number) {
      filters.push({
        column: attributeId,
        type: 'number-range',
        label,
      });
    }

    // BOOLEAN type filters
    if (dataType === DataType.Boolean) {
      filters.push({
        column: attributeId,
        type: 'boolean',
        label,
      });
    }

    // DATE/DATETIME filters could be added here when date-range filter is implemented
  });

  return filters;
}

/**
 * Generate search configuration from entity type schema
 */
export function generateSearchConfigFromEntityType(
  entityType: EntityType,
): (keyof EntityRow & string)[] {
  const searchableColumns: string[] = [];

  if (!entityType.schema.properties) {
    return searchableColumns;
  }

  Object.entries(entityType.schema.properties).forEach(([attributeId, schema]) => {
    // Only include non-protected STRING attributes for search
    if (schema.type === DataType.String && !schema._protected) {
      searchableColumns.push(attributeId);
    }
  });

  return searchableColumns;
}

/**
 * Get entity display name using the identifier field value
 * Falls back to truncated entity ID if identifier value not found
 */
export function getEntityDisplayName(entity: Entity): string {
  // Get the identifier field value from the entity payload
  const identifierValue: EntityAttribute | null = entity.payload[entity.identifierKey];
  if (!identifierValue) {
    return `Entity ${entity.id.slice(0, 8)}...`;
  }

  const { payload } = identifierValue;
  // An identifier value should always be of type ATTRIBUTE and be of SCHEMA TYPE STRING or NUMBER
  if (isRelationshipPayload(payload)) {
    return `Entity ${entity.id.slice(0, 8)}...`;
  }

  return String(payload.value);
}
