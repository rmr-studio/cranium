'use client';

import { Button } from '@cranium/ui/button';
import { Eye, Plus } from 'lucide-react';
import { FC } from 'react';

interface EntityHeaderActionsProps {
  onAddProperty: () => void;
  onToggleVisibility: () => void;
  disabled?: boolean;
}

export const EntityHeaderActions: FC<EntityHeaderActionsProps> = ({
  onAddProperty,
  onToggleVisibility,
  disabled,
}) => {
  return (
    <div className="flex items-center gap-1">
      <Button
        onClick={onAddProperty}
        variant="ghost"
        size="icon"
        className="size-7"
        disabled={disabled}
      >
        <Plus className="size-4" />
        <span className="sr-only">Add property</span>
      </Button>
      <Button
        onClick={onToggleVisibility}
        variant="ghost"
        size="icon"
        className="size-7"
        disabled={disabled}
      >
        <Eye className="size-4" />
        <span className="sr-only">Manage visibility</span>
      </Button>
    </div>
  );
};
