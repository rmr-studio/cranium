'use client';

import { Button } from '@riven/ui/button';
import { DataTable, DataTableProvider } from '@/components/ui/data-table';
import { IconCell } from '@/components/ui/icon/icon-cell';
import { EntityType, EntityTypeImpactResponse } from '@/lib/types/entity';
import { ColumnDef } from '@tanstack/react-table';
import { Edit, Plus, Trash2 } from 'lucide-react';
import { useRouter, useSearchParams } from 'next/navigation';
import { FC, useEffect, useMemo, useState } from 'react';
import { useDeleteTypeMutation } from '../../hooks/mutation/type/use-delete-type-mutation';
import { useEntityTypes } from '../../hooks/query/type/use-entity-types';
import { NewEntityTypeForm } from '../forms/type/new-entity-type-form';

interface Props {
  workspaceId: string;
}

export const EntityTypesOverview: FC<Props> = ({ workspaceId }) => {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [newTypeOpen, setNewTypeOpen] = useState(false);
  const [impactModalOpen, setImpactModalOpen] = useState<boolean>(false);

  // Auto-open the new entity type form if ?new query param is present
  useEffect(() => {
    if (searchParams.get('new') !== null) {
      setNewTypeOpen(true);
      router.replace(`/dashboard/workspace/${workspaceId}/entity`);
    }
  }, [searchParams, workspaceId, router]);

  const { data: types, isPending } = useEntityTypes(workspaceId);

  const onImpactConfirmation = (impact: EntityTypeImpactResponse) => {
    // todo
    setImpactModalOpen(true);
  };

  const { mutateAsync: deleteType } = useDeleteTypeMutation(workspaceId, onImpactConfirmation);

  const onDelete = async (row: EntityType) => {
    await deleteType({ key: row.key });
  };

  const columns: ColumnDef<EntityType>[] = useMemo(
    () => [
      {
        accessorKey: 'name',
        header: 'Entity Type',
        cell: ({ row }) => (
          <div className="flex items-center gap-3">
            <div className="flex h-8 w-8 items-center justify-center rounded-md bg-primary/5">
              <IconCell
                readonly={true}
                type={row.original.icon.type}
                colour={row.original.icon.colour}
              />
            </div>
            <div className="flex flex-col">
              <span className="font-medium">{row.original.name.plural}</span>
              {row.original.semantics?.entityType?.definition && (
                <span className="text-xs text-muted-foreground">
                  {row.original.semantics.entityType.definition}
                </span>
              )}
            </div>
          </div>
        ),
      },
      {
        accessorKey: 'relationships',
        header: 'Relationships',
        cell: ({ row }) => {
          return (
            <span className="text-muted-foreground">
              {row.original.relationships.length}
            </span>
          );
        },
      },
      {
        accessorKey: 'schema',
        header: 'Attributes',
        cell: ({ row }) => {
          const schemaCount = Object.keys(row.original.schema.properties || {}).length;
          return (
            <span className="text-muted-foreground">
              {schemaCount + row.original.relationships.length}
            </span>
          );
        },
      },
    ],
    [],
  );

  if (isPending) {
    return <div>Loading entity types...</div>;
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Entity Types</h1>
          <p className="text-sm text-muted-foreground">
            Modify and add entity types in your workspace
          </p>
        </div>
        <NewEntityTypeForm
          workspaceId={workspaceId}
          entityTypes={types}
          open={newTypeOpen}
          onOpenChange={setNewTypeOpen}
        >
          <Button>
            <Plus className="mr-2 h-4 w-4" />
            New Entity Type
          </Button>
        </NewEntityTypeForm>
      </div>

      {/* Data Table */}
      <DataTableProvider initialData={types || []}>
        <DataTable
          columns={columns}
          enableSorting
          search={{
            enabled: true,
            searchableColumns: ['name.plural', 'name.singular'],
            placeholder: 'Search entity types...',
          }}
          onRowClick={(row) => {
            router.push(`/dashboard/workspace/${workspaceId}/entity/${row.original.key}`);
          }}
          rowActions={{
            enabled: true,
            menuLabel: 'Actions',
            actions: [
              {
                label: 'Edit',
                icon: Edit,
                onClick: (row) => {
                  router.push(`/dashboard/workspace/${workspaceId}/entity/${row.key}/settings`);
                },
                separator: true,
              },
              {
                label: 'Delete',
                icon: Trash2,
                onClick: onDelete,
                variant: 'destructive',
              },
            ],
          }}
          emptyMessage="No entity types found."
        />
      </DataTableProvider>
    </div>
  );
};
