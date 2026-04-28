import type { PanelId, PanelRegistryEntry } from '@/components/ui/sidebar/types/side-panel.types';
import { BillingPanel } from '@/components/ui/sidebar/panels/billing-panel';
import { EntitiesPanel } from '@/components/ui/sidebar/panels/entities-panel';
import { KnowledgePanel } from '@/components/ui/sidebar/panels/knowledge-panel';
import { NotesPanel } from '@/components/ui/sidebar/panels/notes-panel';
import { OverviewPanel } from '@/components/ui/sidebar/panels/overview-panel';
import { WorkspacesPanel } from '@/components/ui/sidebar/panels/workspaces-panel';

/**
 * Single registry for all root panel sections.
 * Title, component, and any panel-level metadata live here — one place to update.
 */
export const panelRootRegistry: Record<PanelId, PanelRegistryEntry> = {
  workspaces: { title: 'Workspaces', component: WorkspacesPanel, hideHeader: true },
  overview: { title: 'Overview', component: OverviewPanel },
  entities: { title: 'Entities', component: EntitiesPanel },
  knowledge: { title: 'Knowledge', component: KnowledgePanel },
  notes: { title: 'Notes', component: NotesPanel },
  billing: { title: 'Billing', component: BillingPanel },
};
