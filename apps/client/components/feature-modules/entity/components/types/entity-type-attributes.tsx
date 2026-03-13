import { useWorkspace } from '@/components/feature-modules/workspace/hooks/query/use-workspace';
import { type EntityType, type EntityTypeDefinition, EntityPropertyType } from '@/lib/types/entity';
import { useRouter, usePathname, useSearchParams } from 'next/navigation';
import { FC, useEffect, useState } from 'react';
import { useConfigForm } from '../../context/configuration-provider';
import { AttributeFormModal } from '../ui/modals/type/attribute-form-modal';
import { DeleteDefinitionModal } from '../ui/modals/type/delete-definition-modal';
import EntityTypeDataTable from './entity-type-data-table';

interface Props {
  type: EntityType;
  editDefinitionId?: string;
}

export const EntityTypesAttributes: FC<Props> = ({ type, editDefinitionId }) => {
  // Get identifierKey from store instead of props, fallback to entity type default
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [editingAttribute, setEditingAttribute] = useState<EntityTypeDefinition | undefined>(
    undefined,
  );
  const [deletingAttribute, setDeletingAttribute] = useState<EntityTypeDefinition | undefined>(
    undefined,
  );
  const { data: workspace } = useWorkspace();
  const form = useConfigForm();

  useEffect(() => {
    if (!dialogOpen) {
      setEditingAttribute(undefined);
    }
  }, [dialogOpen]);

  useEffect(() => {
    if (!deleteDialogOpen) {
      setDeletingAttribute(undefined);
    }
  }, [deleteDialogOpen]);

  // Auto-open edit modal when editDefinitionId is provided via URL param
  useEffect(() => {
    if (!editDefinitionId) return;

    // Look up in schema properties
    if (type.schema.properties && editDefinitionId in type.schema.properties) {
      const schemaEntry = type.schema.properties[editDefinitionId];
      setEditingAttribute({
        id: editDefinitionId,
        type: EntityPropertyType.Attribute,
        definition: { id: editDefinitionId, schema: schemaEntry },
      });
      setDialogOpen(true);
    } else {
      // Look up in relationships
      const relationship = type.relationships?.find((rel) => rel.id === editDefinitionId);
      if (relationship) {
        setEditingAttribute({
          id: editDefinitionId,
          type: EntityPropertyType.Relationship,
          definition: relationship,
        });
        setDialogOpen(true);
      }
    }

    // Clear the edit param from URL
    const params = new URLSearchParams(searchParams.toString());
    params.delete('edit');
    const newUrl = params.toString() ? `${pathname}?${params.toString()}` : pathname;
    router.replace(newUrl);
  }, [editDefinitionId, type.schema.properties, type.relationships, pathname, router, searchParams]);

  const { watch } = form;
  const identifierKey = watch('identifierKey');

  const onDelete = (attribute: EntityTypeDefinition) => {
    setDeletingAttribute(attribute);
    setDeleteDialogOpen(true);
  };

  const onEdit = (attribute: EntityTypeDefinition) => {
    setEditingAttribute(attribute);
    setDialogOpen(true);
  };

  if (!workspace) return null;

  return (
    <>
      <section className="mt-4">
        <EntityTypeDataTable
          type={type}
          identifierKey={identifierKey}
          onEdit={onEdit}
          onDelete={onDelete}
          onAdd={() => {
            setEditingAttribute(undefined);
            setDialogOpen(true);
          }}
        />
      </section>
      <AttributeFormModal
        dialog={{ open: dialogOpen, setOpen: setDialogOpen }}
        type={type}
        selectedAttribute={editingAttribute?.definition}
      />
      {deletingAttribute && (
        <DeleteDefinitionModal
          workspaceId={workspace.id}
          dialog={{ open: deleteDialogOpen, setOpen: setDeleteDialogOpen }}
          type={type}
          definition={deletingAttribute}
        />
      )}
    </>
  );
};
