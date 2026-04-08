'use client';

import { AnimatePresence, motion } from 'framer-motion';
import { PanelLeftClose } from 'lucide-react';
import { type PanelId, useIconRail } from './icon-rail-context';
import { BillingPanel } from './panels/billing-panel';
import { EntitiesPanel } from './panels/entities-panel';
import { KnowledgePanel } from './panels/knowledge-panel';
import { NotesPanel } from './panels/notes-panel';
import { OverviewPanel } from './panels/overview-panel';
import { SettingsPanel } from './panels/settings-panel';
import { WorkspacesPanel } from './panels/workspaces-panel';

const panelTitles: Record<PanelId, string> = {
  workspaces: 'Workspaces',
  overview: 'Overview',
  entities: 'Entities',
  knowledge: 'Knowledge',
  notes: 'Notes',
  billing: 'Billing',
  settings: 'Settings',
};

const panelComponents: Record<PanelId, React.ComponentType> = {
  workspaces: WorkspacesPanel,
  overview: OverviewPanel,
  entities: EntitiesPanel,
  knowledge: KnowledgePanel,
  notes: NotesPanel,
  billing: BillingPanel,
  settings: SettingsPanel,
};

export function SubPanel() {
  const { selectedPanel, panelOpen, closePanel, isMobile } = useIconRail();

  if (isMobile) return null;

  return (
    <AnimatePresence>
      {panelOpen && (
        <motion.aside
          key="sub-panel"
          initial={{ width: 0, opacity: 0 }}
          animate={{ width: 'var(--sub-panel-width)', opacity: 1 }}
          exit={{ width: 0, opacity: 0 }}
          transition={{ duration: 0.2, ease: 'linear' }}
          className="h-full shrink-0 overflow-hidden"
        >
          <div className="flex h-full w-(--sub-panel-width) flex-col rounded-r-xl border-r-4 border-r-primary bg-background dark:border-r-secondary">
            {/* Header */}
            <div className="flex min-h-(--header-height) shrink-0 items-center justify-between border-b px-4">
              <span className="text-sm font-semibold text-sidebar-foreground">
                {panelTitles[selectedPanel]}
              </span>
              <button
                onClick={closePanel}
                aria-label="Close panel"
                className="flex size-7 items-center justify-center rounded-md text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground"
              >
                <PanelLeftClose className="size-4" />
              </button>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-y-auto px-3 py-2">
              {(() => {
                const PanelComponent = panelComponents[selectedPanel];
                return <PanelComponent />;
              })()}
            </div>
          </div>
        </motion.aside>
      )}
    </AnimatePresence>
  );
}
