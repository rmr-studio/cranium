'use client';

import { Button } from '@cranium/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@cranium/ui/tooltip';

import type { QueryFilter } from '@/lib/types/entity';
import { Ellipsis, Trash2 } from 'lucide-react';
import { FC, useState } from 'react';
import type { EntitySelection } from '../../hooks/use-entity-selection';
import { DeleteEntityModal } from '../ui/modals/instance/delete-entity-modal';

interface Props {
  clearSelection: () => void;
  workspaceId: string;
  entityTypeId: string;
  entitySelection: EntitySelection;
  queryFilter: QueryFilter | undefined;
}

const EntityActionBar: FC<Props> = ({
  clearSelection,
  workspaceId,
  entityTypeId,
  entitySelection,
  queryFilter,
}) => {
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);

  const handleDeleteSuccess = () => {
    clearSelection();
  };

  return (
    <>
      <div className="mb-0.5 flex items-center gap-1 px-1">
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant={'ghost'}
              size={'xs'}
              className="p-1! hover:bg-primary/10"
              onClick={() => setDeleteModalOpen(true)}
            >
              <Trash2 className="size-3.5 text-destructive" />
            </Button>
          </TooltipTrigger>
          <TooltipContent className="px-1.5 py-1 text-xs">Delete Selected Rows</TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant={'ghost'} size={'xs'} className="p-1! hover:bg-primary/10">
              <Ellipsis className="size-3.5 text-primary" />
            </Button>
          </TooltipTrigger>
          <TooltipContent className="px-1.5 py-1 text-xs">More Actions</TooltipContent>
        </Tooltip>
      </div>

      <DeleteEntityModal
        open={deleteModalOpen}
        onOpenChange={setDeleteModalOpen}
        workspaceId={workspaceId}
        entityTypeId={entityTypeId}
        entitySelection={entitySelection}
        queryFilter={queryFilter}
        onSuccess={handleDeleteSuccess}
      />
    </>
  );
};

export default EntityActionBar;
