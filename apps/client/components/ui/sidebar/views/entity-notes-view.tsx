'use client';

import type { SidePanelView } from '../types/side-panel.types';

type Props = Extract<SidePanelView, { type: 'entity-notes' }>;

export function EntityNotesView({ entityId, workspaceId }: Props) {
  return (
    <div className="space-y-2 p-2">
      <p className="text-sm text-muted-foreground">Entity notes view</p>
      <p className="text-xs text-muted-foreground">Entity: {entityId}</p>
    </div>
  );
}
