'use client';

import { Database } from 'lucide-react';
import { Card, CardContent } from '@riven/ui/card';
import { FC } from 'react';

interface EntityReferenceEmptyStateProps {
  onSelectClick?: () => void;
}

/**
 * Empty state for entity reference blocks with no selected entities.
 * Clickable to trigger entity selection.
 */
export const EntityReferenceEmptyState: FC<EntityReferenceEmptyStateProps> = ({
  onSelectClick,
}) => {
  return (
    <Card
      className="cursor-pointer border-2 border-dashed transition-colors hover:bg-accent/50"
      onClick={onSelectClick}
    >
      <CardContent className="py-12 text-center">
        <Database className="mx-auto mb-4 h-12 w-12 text-muted-foreground" />
        <h3 className="mb-2 text-lg font-medium">No entities selected</h3>
        <p className="mb-1 text-sm text-muted-foreground">
          Click here or use the "Select Entities" button in the toolbar
        </p>
        <p className="text-xs text-muted-foreground/70">to add entities to this reference block</p>
      </CardContent>
    </Card>
  );
};
