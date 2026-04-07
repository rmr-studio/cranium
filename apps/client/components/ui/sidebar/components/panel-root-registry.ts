import type { PanelId, PanelRegistryEntry } from '../types/side-panel.types';
import { BillingPanel } from '../panels/billing-panel';
import { EntitiesPanel } from '../panels/entities-panel';
import { KnowledgePanel } from '../panels/knowledge-panel';
import { NotesPanel } from '../panels/notes-panel';
import { OverviewPanel } from '../panels/overview-panel';
import { SettingsPanel } from '../panels/settings-panel';
import { WorkspacesPanel } from '../panels/workspaces-panel';

/**
 * Single registry for all root panel sections.
 * Title, component, and any panel-level metadata live here — one place to update.
 */
export const panelRootRegistry: Record<PanelId, PanelRegistryEntry> = {
  workspaces: { title: 'Workspaces', component: WorkspacesPanel },
  overview: { title: 'Overview', component: OverviewPanel },
  entities: { title: 'Entities', component: EntitiesPanel },
  knowledge: { title: 'Knowledge', component: KnowledgePanel },
  notes: { title: 'Notes', component: NotesPanel },
  billing: { title: 'Billing', component: BillingPanel },
  settings: { title: 'Settings', component: SettingsPanel },
};
