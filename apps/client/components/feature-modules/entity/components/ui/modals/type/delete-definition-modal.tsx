import { DialogControl } from '@/lib/interfaces/interface';
import { EntityTypeRequestDefinition } from '@/lib/types/entity';
import { Button } from '@riven/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@riven/ui/dialog';
import { AlertCircle, Loader2 } from 'lucide-react';
import { FC, useMemo, useState } from 'react';

import {
  DeleteAttributeDefinitionRequest,
  DeleteRelationshipDefinitionRequest,
  EntityType,
  EntityTypeDefinition,
  isAttributeDefinition,
  isRelationshipDefinition,
} from '@/lib/types/entity';
import { useDeleteDefinitionMutation } from '../../../../hooks/mutation/type/use-delete-definition-mutation';

interface Props {
  dialog: DialogControl;
  type: EntityType;
  workspaceId: string;
  definition: EntityTypeDefinition;
}

interface DefinitionType {
  isRelationship: boolean;
  isOrigin: boolean;
}

export const DeleteDefinitionModal: FC<Props> = ({
  dialog,
  type: entityType,
  definition,
  workspaceId,
}) => {
  const { open, setOpen: onOpenChange } = dialog;

  const [isDeleting, setIsDeleting] = useState(false);

  const type: DefinitionType = useMemo(() => {
    if (isRelationshipDefinition(definition.definition)) {
      return {
        isRelationship: true,
        isOrigin: definition.definition.sourceEntityTypeId === entityType.id,
      };
    }
    return {
      isRelationship: false,
      isOrigin: false,
    };
  }, [definition.definition, entityType.id]);

  const { mutateAsync: deleteDefinition } = useDeleteDefinitionMutation(workspaceId, () => {}, {
    onSuccess: () => {
      setIsDeleting(false);
      onOpenChange(false);
    },
    onError: () => {
      setIsDeleting(false);
    },
  });

  const definitionLabel = isRelationshipDefinition(definition.definition)
    ? definition.definition.name || definition.id
    : isAttributeDefinition(definition.definition)
      ? definition.definition.schema.label || definition.id
      : definition.id;

  const dialogTitle = type.isRelationship
    ? type.isOrigin
      ? 'Delete Relationship'
      : 'Remove from Relationship'
    : 'Delete Attribute';

  const dialogDescription = type.isRelationship
    ? type.isOrigin
      ? `Are you sure you want to delete the "${definitionLabel}" relationship? This will remove it from this entity type and all target entity types. All associated relationship data will be deleted.`
      : `Are you sure you want to remove ${entityType.name.plural} from the "${definitionLabel}" relationship? This entity type will no longer be a target of this relationship.`
    : `Are you sure you want to delete "${definitionLabel}"? This action cannot be undone.`;

  const handleDelete = async () => {
    if (!definition) return;
    setIsDeleting(true);

    try {
      if (type.isRelationship) {
        const request: DeleteRelationshipDefinitionRequest = {
          id: definition.id,
          key: entityType.key,
          type: EntityTypeRequestDefinition.DeleteRelationship,
          sourceEntityTypeKey: type.isOrigin ? undefined : entityType.key,
        };
        await deleteDefinition({ definition: request });
      } else {
        const request: DeleteAttributeDefinitionRequest = {
          id: definition.id,
          key: entityType.key,
          type: EntityTypeRequestDefinition.DeleteSchema,
        };
        await deleteDefinition({ definition: request });
      }
    } catch {
      // Error handled by mutation hook
    }
  };

  if (!definition) return null;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{dialogTitle}</DialogTitle>
          <DialogDescription>{dialogDescription}</DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          {type.isRelationship && type.isOrigin && (
            <div className="flex items-start gap-2 rounded-md border border-amber-200 bg-amber-50 p-3 dark:border-amber-900 dark:bg-amber-950/20">
              <AlertCircle className="mt-0.5 size-4 shrink-0 text-amber-600 dark:text-amber-500" />
              <div className="text-sm text-amber-900 dark:text-amber-200">
                <p className="mb-1 font-medium">Cascade Deletion</p>
                <p className="text-amber-800 dark:text-amber-300">
                  This will remove the relationship from this entity type and all target entity
                  types. All associated relationship data will be deleted.
                </p>
              </div>
            </div>
          )}

          {type.isRelationship && !type.isOrigin && (
            <div className="flex items-start gap-2 rounded-md border border-muted-foreground/20 bg-muted/50 p-3">
              <AlertCircle className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
              <div className="text-sm text-muted-foreground">
                <p className="mb-1 font-medium">Target Removal</p>
                <p>
                  This entity type will be removed as a target of the &quot;{definitionLabel}&quot;
                  relationship. The relationship itself will continue to exist for other entity
                  types.
                </p>
              </div>
            </div>
          )}

          {!type.isRelationship && (
            <div className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 dark:border-red-900 dark:bg-red-950/20">
              <AlertCircle className="mt-0.5 size-4 shrink-0 text-red-600 dark:text-red-500" />
              <div className="text-sm text-red-900 dark:text-red-200">
                <p className="mb-1 font-medium">Warning</p>
                <p className="text-red-800 dark:text-red-300">
                  Deleting this attribute will remove it from all entities of this type. This action
                  cannot be undone.
                </p>
              </div>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isDeleting}>
            Cancel
          </Button>
          <Button variant="destructive" onClick={handleDelete} disabled={isDeleting}>
            {isDeleting && <Loader2 className="size-4 animate-spin" />}
            {isDeleting ? 'Deleting...' : 'Delete'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};
