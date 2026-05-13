'use client';

import { Badge } from '@cranium/ui/badge';
import { IconCell } from '@/components/ui/icon/icon-cell';
import { attributeTypes } from '@/lib/util/form/schema.util';
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
import { SchemaType } from '@/lib/types/common';
import { toTitleCase } from '@cranium/utils';
import { ColumnDef } from '@tanstack/react-table';
import { ArrowDownLeft } from 'lucide-react';
import { useMemo } from 'react';

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
  allEntityTypes?: EntityType[],
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

  // Create entity type name lookup from allEntityTypes
  const entityTypeNameLookup = useMemo(() => {
    const map = new Map<string, string>();
    allEntityTypes?.forEach((et) => {
      map.set(et.id, et.name.plural);
    });
    return map;
  }, [allEntityTypes]);

  // Unified columns for both attributes and relationships
  const fieldColumns: ColumnDef<EntityTypeAttributeRow>[] = useMemo(
    () => [
      {
        accessorKey: 'label',
        header: 'Name',
        cell: ({ row }) => {
          const { icon, label, type: rowType, targetEntityTypeNames, isTargetSide } =
            row.original;
          const isRelationship = rowType === EntityPropertyType.Relationship;

          return (
            <div className="flex items-center gap-2">
              {icon && (
                <div className="relative shrink-0">
                  <IconCell
                    type={icon.type}
                    colour={icon.colour}
                    readonly
                    className="size-4"
                  />
                  {isTargetSide && (
                    <ArrowDownLeft className="absolute -bottom-0.5 -right-0.5 size-2.5 text-muted-foreground" />
                  )}
                </div>
              )}
              <span className="font-medium">{label}</span>
              {isRelationship && targetEntityTypeNames && targetEntityTypeNames.length > 0 && (
                <div className="flex items-center gap-1">
                  {targetEntityTypeNames.map((name) => (
                    <Badge
                      key={name}
                      variant="outline"
                      className="px-1.5 py-0 text-xs font-normal text-muted-foreground"
                    >
                      {name}
                    </Badge>
                  ))}
                </div>
              )}
            </div>
          );
        },
      },
      {
        accessorKey: 'rowType',
        header: 'Type',
        cell: ({ row }) => {
          const { schemaType } = row.original;
          const label =
            schemaType === 'RELATIONSHIP'
              ? 'Relationship'
              : (attributeTypes[schemaType as SchemaType]?.label ?? toTitleCase(schemaType));

          return <span className="text-muted-foreground">{label}</span>;
        },
      },
      {
        id: 'constraints',
        header: 'Constraints',
        cell: ({ row }) => {
          const { type: rowType, required, unique, allowPolymorphic } = row.original;
          const isIdentifier = row.original.id === identifierKey;
          const isRelationship = rowType === EntityPropertyType.Relationship;
          const constraints: string[] = [];

          if (isIdentifier) constraints.push('Identifier');
          if (required) constraints.push('Required');
          if (!isRelationship && unique) constraints.push('Unique');
          if (isRelationship && allowPolymorphic) constraints.push('Polymorphic');

          if (constraints.length === 0) return null;

          return (
            <div className="flex flex-wrap gap-1">
              {constraints.map((constraint) => (
                <Badge key={constraint} variant="secondary" className="text-xs">
                  {constraint}
                </Badge>
              ))}
            </div>
          );
        },
      },
    ],
    [identifierKey],
  );

  const convertRelationshipToRow = (
    relationship: RelationshipDefinition,
  ): EntityTypeAttributeRow => {
    const isTargetSide = relationship.sourceEntityTypeId !== type.id;

    if (isTargetSide) {
      // Find the target rule where this entity type is the target
      const matchingRule = relationship.targetRules?.find(
        (rule) => rule.targetEntityTypeId === type.id,
      );

      // Resolve source entity type name for badge display
      const sourceEntityType = allEntityTypes?.find(
        (et) => et.id === relationship.sourceEntityTypeId,
      );
      const sourceTypeName = sourceEntityType?.name.plural;

      return {
        id: relationship.id,
        label: matchingRule?.inverseName || relationship.name || relationship.id,
        type: EntityPropertyType.Relationship,
        protected: relationship._protected,
        required: false,
        schemaType: 'RELATIONSHIP',
        additionalConstraints: [],
        icon: relationship.icon,
        cardinalityDefault: relationship.cardinalityDefault,
        targetRules: relationship.targetRules,
        allowPolymorphic: relationship.allowPolymorphic,
        targetEntityTypeNames: sourceTypeName ? [sourceTypeName] : [],
        isTargetSide: true,
        sourceEntityTypeId: relationship.sourceEntityTypeId,
        sourceEntityTypeKey: sourceEntityType?.key,
      };
    }

    // Source-side: keep existing logic unchanged
    const targetNames = relationship.targetRules
      ?.map((rule) => {
        if (rule.targetEntityTypeId) {
          return entityTypeNameLookup.get(rule.targetEntityTypeId);
        }
        return undefined;
      })
      .filter((name): name is string => !!name);

    return {
      id: relationship.id,
      label: relationship.name || relationship.id,
      type: EntityPropertyType.Relationship,
      protected: relationship._protected,
      required: false,
      schemaType: 'RELATIONSHIP',
      additionalConstraints: [],
      icon: relationship.icon,
      cardinalityDefault: relationship.cardinalityDefault,
      targetRules: relationship.targetRules,
      allowPolymorphic: relationship.allowPolymorphic,
      targetEntityTypeNames: targetNames,
    };
  };

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
      additionalConstraints: [],
      dataType: schema.type,
      unique: schema.unique || false,
      icon: schema.icon,
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
  }, [type, semanticBundle, entityTypeNameLookup]);

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
