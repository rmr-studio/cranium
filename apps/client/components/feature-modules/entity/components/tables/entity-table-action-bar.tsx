'use client';

import { Button } from '@riven/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@riven/ui/tooltip';

import { Ellipsis, Trash2 } from 'lucide-react';
import { FC, useState } from 'react';
import { DeleteEntityModal } from '../ui/modals/instance/delete-entity-modal';
import { EntityRow } from './entity-table-utils';

interface Props {
  selectedRows: EntityRow[];
  clearSelection: () => void;
  workspaceId: string;
  entityTypeId: string;
}

const EntityActionBar: FC<Props> = ({
  selectedRows,
  clearSelection,
  workspaceId,
  entityTypeId,
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
        selectedRows={selectedRows}
        workspaceId={workspaceId}
        entityTypeId={entityTypeId}
        onSuccess={handleDeleteSuccess}
      />
    </>
  );
};

export default EntityActionBar;
