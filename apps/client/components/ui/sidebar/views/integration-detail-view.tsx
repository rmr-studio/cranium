'use client';

import type { SidePanelView } from '@/components/ui/sidebar/types/side-panel.types';

type Props = Extract<SidePanelView, { type: 'integration-detail' }>;

export function IntegrationDetailView({ integrationId }: Props) {
  return (
    <div className="space-y-2 p-2">
      <p className="text-sm text-muted-foreground">Integration detail view</p>
      <p className="text-xs text-muted-foreground">Integration: {integrationId}</p>
    </div>
  );
}
