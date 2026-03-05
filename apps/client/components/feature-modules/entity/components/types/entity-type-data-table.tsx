import { DataTable, DataTableProvider } from '@/components/ui/data-table';
import { TooltipProvider } from '@riven/ui';
import {
  EntityPropertyType,
  EntityType,
  EntityTypeAttributeColumn,
  EntityTypeAttributeRow,
  type EntityTypeDefinition,
} from '@/lib/types/entity';
import { Row } from '@tanstack/react-table';
import { Edit2, Trash2 } from 'lucide-react';
import { FC, useCallback, useMemo } from 'react';
import { toast } from 'sonner';
import { useConfigForm } from '../../context/configuration-provider';
import { useEntityTypeTable } from '../../hooks/use-entity-type-table';

interface Props {
  type: EntityType;
  identifierKey: string;
  onEdit: (definition: EntityTypeDefinition) => void;
  onDelete: (definition: EntityTypeDefinition) => void;
}

const EntityTypeDataTable: FC<Props> = ({ type, identifierKey, onEdit, onDelete }) => {
  const {
    sortedRowData,
    columns,
    onDelete: deleteRow,
    onEdit: editRow,
  } = useEntityTypeTable(type, identifierKey, onEdit, onDelete);

  const form = useConfigForm();

  const columnData: EntityTypeAttributeColumn[] = form.watch('columns');

  const columnSizeMap = useMemo<Map<string, number>>(() => {
    const map = new Map<string, number>();
    columnData.forEach((col) => {
      if (col.width) {
        map.set(col.key, col.width);
      }
    });
    return map;
  }, [columnData]);

  const handleFieldsReorder = (newOrder: EntityTypeAttributeRow[]): boolean => {
    // Validate: Identifier must remain in first position
    const firstRow = newOrder[0];

    // Check if first row is the identifier
    const isFirstRowIdentifier =
      firstRow.id === identifierKey && firstRow.type === EntityPropertyType.Attribute;

    if (!isFirstRowIdentifier) {
      // Reject the reorder - identifier was moved from first position
      toast.error('The identifier column must remain in the first position', {
        description: 'This field serves as the primary identifier and cannot be moved.',
      });

      return false; // Reject - table will stay at original position
    }

    // Valid reorder - proceed as normal
    const columns: EntityTypeAttributeColumn[] = newOrder.map((item) => {
      return {
        key: item.id,
        type: item.type,
        width: columnSizeMap.get(item.id) || 250,
      };
    });

    form.setValue('columns', columns, {
      shouldDirty: true,
    });

    return true; // Accept the reorder
  };

  const canDelete = (row: EntityTypeAttributeRow): boolean => {
    return (
      !row.protected &&
      !(
        [type.identifierKey, identifierKey].includes(row.id) &&
        row.type === EntityPropertyType.Attribute
      )
    );
  };

  // Disable drag for identifier column (it must stay first)
  const disableDragForRow = useCallback(
    (row: Row<EntityTypeAttributeRow>) => {
      return (
        row.original.id === identifierKey && row.original.type === EntityPropertyType.Attribute
      );
    },
    [identifierKey],
  );

  return (
    <DataTableProvider initialData={sortedRowData}>
      <TooltipProvider>
        <DataTable
          columns={columns}
          enableDragDrop
          alwaysShowActionHandles={true}
          onReorder={handleFieldsReorder}
          getRowId={(row) => row.id}
          disableDragForRow={disableDragForRow}
          search={{
            enabled: true,
            searchableColumns: ['label'],
            placeholder: 'Search fields...',
          }}
          filter={{
            enabled: true,
            filters: [
              {
                column: 'type',
                type: 'select',
                label: 'Type',
                options: [
                  {
                    label: 'Attributes',
                    value: EntityPropertyType.Attribute,
                  },
                  {
                    label: 'Relationships',
                    value: EntityPropertyType.Relationship,
                  },
                ],
              },
            ],
          }}
          rowActions={{
            enabled: true,
            menuLabel: 'Actions',
            actions: [
              {
                label: 'Edit',
                icon: Edit2,
                onClick: (row) => {
                  editRow(row);
                },
              },
              {
                label: 'Delete',
                icon: Trash2,
                onClick: (row) => {
                  deleteRow(row);
                },
                variant: 'destructive',
                disabled: (row) => !canDelete(row),
              },
            ],
          }}
          emptyMessage="No fields defined yet. Add your first attribute or relationship to get started."
        />
      </TooltipProvider>
    </DataTableProvider>
  );
};

export default EntityTypeDataTable;
