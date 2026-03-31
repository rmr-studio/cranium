'use client';

import { Button } from '@riven/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@riven/ui/dialog';
import { AlertCircle, Loader2 } from 'lucide-react';
import { FC, useMemo, useState } from 'react';
import { useDeleteEntityMutation } from '../../../../hooks/mutation/instance/use-delete-entity-mutation';
import type { EntitySelection } from '../../../../hooks/use-entity-selection';
import { EntityRow, isEntityRow } from '../../../tables/entity-table-utils';
import { DeleteEntityRequest, EntitySelectType, QueryFilter } from '@/lib/types/entity';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  selectedRows: EntityRow[];
  workspaceId: string;
  entityTypeId: string;
  entitySelection: EntitySelection;
  queryFilter: QueryFilter | undefined;
  onSuccess?: () => void;
}

export const DeleteEntityModal: FC<Props> = ({
  open,
  onOpenChange,
  selectedRows,
  workspaceId,
  entityTypeId,
  entitySelection,
  queryFilter,
  onSuccess,
}) => {
  const [isDeleting, setIsDeleting] = useState(false);

  // Build the delete request based on selection mode
  const deleteRequest: DeleteEntityRequest | null = useMemo(() => {
    if (entitySelection.mode === 'all') {
      return {
        type: EntitySelectType.All,
        entityTypeId,
        ...(queryFilter ? { filter: queryFilter } : {}),
        ...(entitySelection.excludedIds.size > 0
          ? { excludeIds: Array.from(entitySelection.excludedIds) }
          : {}),
      };
    }

    // Manual mode — use selected row entity IDs (filter out drafts)
    const entityIds = selectedRows.filter(isEntityRow).map((row) => row._entityId);
    if (entityIds.length === 0) return null;

    return {
      type: EntitySelectType.ById,
      entityTypeId,
      entityIds,
    };
  }, [entitySelection, entityTypeId, queryFilter, selectedRows]);

  const entityCount = entitySelection.mode === 'all'
    ? entitySelection.selectedCount
    : selectedRows.filter(isEntityRow).length;

  const { mutateAsync: deleteEntities } = useDeleteEntityMutation(workspaceId, {
    onMutate: () => {
      setIsDeleting(true);
    },
    onSuccess: () => {
      setIsDeleting(false);
      onOpenChange(false);
      onSuccess?.();
    },
    onError: () => {
      setIsDeleting(false);
    },
  });

  const handleDelete = async () => {
    if (!deleteRequest) return;
    await deleteEntities(deleteRequest);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>Delete Entities</DialogTitle>
          <DialogDescription>
            Are you sure you want to delete {entityCount}{' '}
            {entityCount === 1 ? 'entity' : 'entities'}? This action cannot be undone.
          </DialogDescription>
        </DialogHeader>

        <div className="py-4">
          <div className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 dark:border-red-900 dark:bg-red-950/20">
            <AlertCircle className="mt-0.5 size-4 shrink-0 text-red-600 dark:text-red-500" />
            <div className="text-sm text-red-900 dark:text-red-200">
              <p className="mb-1 font-medium">Warning</p>
              <p className="text-red-800 dark:text-red-300">
                {entitySelection.mode === 'all' ? (
                  <>
                    This will permanently delete <strong>all {entityCount} matching entities</strong>
                    {entitySelection.excludedIds.size > 0 &&
                      ` (excluding ${entitySelection.excludedIds.size} deselected)`}
                    . All associated data will be lost and this action cannot be reversed.
                  </>
                ) : (
                  <>
                    Deleting {entityCount === 1 ? 'this entity' : 'these entities'} will permanently
                    remove {entityCount === 1 ? 'it' : 'them'} from the system. All associated data will
                    be lost and this action cannot be reversed.
                  </>
                )}
              </p>
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isDeleting}>
            Cancel
          </Button>
          <Button
            variant="destructive"
            onClick={handleDelete}
            disabled={isDeleting || !deleteRequest}
          >
            {isDeleting && <Loader2 className="mr-2 size-4 animate-spin" />}
            {isDeleting ? 'Deleting...' : `Delete ${entityCount === 1 ? 'Entity' : `${entityCount} Entities`}`}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};
