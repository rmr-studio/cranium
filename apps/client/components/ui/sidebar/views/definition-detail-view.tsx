'use client';

import type { SidePanelView } from '../types/side-panel.types';

type Props = Extract<SidePanelView, { type: 'definition-detail' }>;

export function DefinitionDetailView({ definitionId, workspaceId }: Props) {
  return (
    <div className="space-y-2 p-2">
      <p className="text-sm text-muted-foreground">Definition detail view</p>
      <p className="text-xs text-muted-foreground">ID: {definitionId}</p>
    </div>
  );
}
