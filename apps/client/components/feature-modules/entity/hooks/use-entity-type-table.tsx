'use client';

import { Badge } from '@riven/ui/badge';
import { Tooltip, TooltipContent, TooltipTrigger } from '@riven/ui/tooltip';
import {
  EntityAttributeDefinition,
  EntityPropertyType,
  RelationshipDefinition,
  EntityType,
  EntityTypeAttributeRow,
  EntityTypeDefinition,
  SystemRelationshipType,
  type SemanticMetadataBundle,
  type EntityTypeSemanticMetadata,
} from '@/lib/types/entity';
import { toTitleCase } from '@riven/utils';
import { ColumnDef, Row } from '@tanstack/react-table';
import { Key, Link2, ListTodo, ListX } from 'lucide-react';
import { ReactNode, useMemo } from 'react';

// Common type for data table rows (both attributes and relationships)

interface UseEntityTypeTableReturn {
  columns: ColumnDef<EntityTypeAttributeRow>[];
  sortedRowData: EntityTypeAttributeRow[];
  onDelete: (row: EntityTypeAttributeRow) => void;
  onEdit: (row: EntityTypeAttributeRow) => void;
}

export function useEntityTypeTable(
  type: EntityType,
  identifierKey: string,
  editCB: (definition: EntityTypeDefinition) => void,
  deleteCB: (definition: EntityTypeDefinition) => void,
  semanticBundle?: SemanticMetadataBundle,
): UseEntityTypeTableReturn {
  // Create a lookup map for attributes and relationships by their IDs. This should allow for quick access when choosing the correct item to edit
  const attributeLookup: Map<string, EntityAttributeDefinition | RelationshipDefinition> =
    useMemo(() => {
      const map = new Map<string, EntityAttributeDefinition | RelationshipDefinition>();

      if (type.schema.properties) {
        Object.entries(type.schema.properties).forEach(([id, attr]) => {
          map.set(id, {
            id,
            schema: attr,
          });
        });
      }

      type.relationships
        ?.filter((rel) => rel.systemType !== SystemRelationshipType.ConnectedEntities)
        .forEach((rel) => {
          map.set(rel.id, rel);
        });
      return map;
    }, [type]);

  const IconWithTooltip = ({
    icon: Icon,
    tooltip,
  }: {
    icon: typeof Key;
    tooltip: string;
  }): ReactNode => (
    <Tooltip>
      <TooltipTrigger asChild>
        <Icon className="size-4 text-muted-foreground" />
      </TooltipTrigger>
      <TooltipContent className="font-mono text-xs italic">{tooltip}</TooltipContent>
    </Tooltip>
  );

  const getIndicatorIcons = (row: Row<EntityTypeAttributeRow>): ReactNode => {
    const icons: ReactNode[] = [];

    if (identifierKey === row.original.id) {
      return [
        <IconWithTooltip
          key="identifier"
          icon={Key}
          tooltip="This attribute represents the primary identifier for this entity"
        />,
      ];
    }

    if (row.original.type === EntityPropertyType.Relationship) {
      icons.push(
        <IconWithTooltip
          key="relationship"
          icon={Link2}
          tooltip="This attribute references a relationship to another entity type"
        />,
      );
    }

    if (row.original.required) {
      icons.push(
        <IconWithTooltip
          key="required"
          icon={ListTodo}
          tooltip="This attribute is required and must have a value for each entity"
        />,
      );
    }

    if (row.original.unique) {
      icons.push(
        <IconWithTooltip
          key="unique"
          icon={ListX}
          tooltip="This attribute must have a unique value for each entity"
        />,
      );
    }

    return <div className="flex items-center gap-1.5">{icons}</div>;
  };

  // Unified columns for both attributes and relationships
  const fieldColumns: ColumnDef<EntityTypeAttributeRow>[] = useMemo(
    () => [
      {
        accessorKey: 'label',
        header: 'Name',
        cell: ({ row }) => {
          return <span className="font-medium">{row.original.label}</span>;
        },
      },
      {
        id: 'properties',
        size: 60,
        header: 'Properties',
        enableResizing: false,
        cell: ({ row }) => getIndicatorIcons(row),
      },

      {
        accessorKey: 'rowType',
        header: 'Type',
        cell: ({ row }) => {
          return <Badge variant="outline">{toTitleCase(row.original.schemaType)}</Badge>;
        },
      },
      {
        id: 'classification',
        header: 'Classification',
        cell: ({ row }) => {
          const isRelationship = row.original.type === EntityPropertyType.Relationship;
          if (isRelationship) {
            return <span className="text-xs text-muted-foreground">Relationship</span>;
          }

          const classification = row.original.classification;
          if (!classification) {
            return <span className="text-xs text-muted-foreground">Unset</span>;
          }

          const displayLabel = toTitleCase(classification.replace(/_/g, ' '));
          const badge = <Badge variant="outline">{displayLabel}</Badge>;

          const definition = row.original.definition;
          if (definition) {
            return (
              <Tooltip>
                <TooltipTrigger asChild>{badge}</TooltipTrigger>
                <TooltipContent className="max-w-xs text-sm">{definition}</TooltipContent>
              </Tooltip>
            );
          }

          return badge;
        },
      },
      {
        id: 'constraints',
        header: 'Constraints',
        cell: ({ row }) => {
          const isRelationship = row.original.type === EntityPropertyType.Relationship;
          const constraints: string[] = [];

          if (row.original.required) constraints.push('Required');

          if (!isRelationship && row.original.unique) {
            constraints.push('Unique');
          }

          if (isRelationship && row.original.allowPolymorphic) {
            constraints.push('Polymorphic');
          }

          if (isRelationship && row.original.targetRules?.some(r => r.inverseVisible)) {
            constraints.push('Inverse Visible');
          }

          return (
            <div className="flex flex-wrap gap-1">
              {constraints.map((constraint) => (
                <Badge key={constraint} variant="secondary" className="text-xs">
                  {constraint}
                </Badge>
              ))}
              {constraints.length === 0 && (
                <span className="text-xs text-muted-foreground">None</span>
              )}
            </div>
          );
        },
      },
    ],
    [identifierKey],
  );

  const convertRelationshipToRow = (
    relationship: RelationshipDefinition,
  ): EntityTypeAttributeRow => ({
    id: relationship.id,
    label: relationship.name || relationship.id,
    type: EntityPropertyType.Relationship,
    protected: relationship._protected,
    required: false,
    schemaType: 'RELATIONSHIP',
    additionalConstraints: [],
    cardinalityDefault: relationship.cardinalityDefault,
    targetRules: relationship.targetRules,
    allowPolymorphic: relationship.allowPolymorphic,
  });

  const convertSchemaPropertyToRow = (
    attribute: EntityAttributeDefinition,
    semanticMeta?: EntityTypeSemanticMetadata,
  ): EntityTypeAttributeRow => {
    const { id, schema } = attribute;
    return {
      id,
      label: schema.label ?? 'Unknown',
      type: EntityPropertyType.Attribute,
      required: schema.required || false,
      schemaType: schema.key,
      // Todo. Set up additional constraints properly
      additionalConstraints: [],
      dataType: schema.type,
      unique: schema.unique || false,
      classification: semanticMeta?.classification,
      definition: semanticMeta?.definition,
    };
  };

  const sortedRowData: EntityTypeAttributeRow[] = useMemo(() => {
    const { schema, columns, relationships } = type;
    const rows: EntityTypeAttributeRow[] = [
      ...Object.entries(schema.properties || {}).map(([id, attr]) =>
        convertSchemaPropertyToRow({ id, schema: attr }, semanticBundle?.attributes?.[id]),
      ),
      ...(relationships || [])
        .filter((rel) => rel.systemType !== SystemRelationshipType.ConnectedEntities)
        .map((rel) => convertRelationshipToRow(rel)),
    ];

    return rows.toSorted((a, b) => {
      const aIndex = columns.findIndex((o) => o.key === a.id);
      const bIndex = columns.findIndex((o) => o.key === b.id);

      // If both are in columns array, sort by their position
      if (aIndex !== -1 && bIndex !== -1) {
        return aIndex - bIndex;
      }
      // If only one is in columns array, prioritize it
      if (aIndex !== -1) return -1;
      if (bIndex !== -1) return 1;
      // If neither is in columns array, maintain current columns
      return 0;
    });
  }, [type, semanticBundle]);

  const onEdit = (row: EntityTypeAttributeRow) => {
    const definition = attributeLookup.get(row.id);
    if (!definition) return;

    editCB({
      id: row.id,
      type: row.type,
      definition,
    });
  };

  const onDelete = (row: EntityTypeAttributeRow) => {
    const definition = attributeLookup.get(row.id);
    if (!definition) return;

    deleteCB({
      id: row.id,
      type: row.type,
      definition,
    });
  };

  return {
    columns: fieldColumns,
    sortedRowData,
    onEdit,
    onDelete,
  };
}
