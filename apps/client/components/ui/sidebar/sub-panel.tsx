'use client';

import { PanelLeftClose } from 'lucide-react';
import {
  useCurrentView,
  usePanelOpen,
  useSelectedPanel,
  useSidePanelActions,
} from '@/components/ui/sidebar/context/side-panel-provider';
import { panelRootRegistry } from '@/components/ui/sidebar/components/panel-root-registry';
import { PanelViewRenderer } from '@/components/ui/sidebar/components/panel-view-renderer';

/**
 * The side panel content area.
 *
 * When the view stack is empty, renders the root panel component for the
 * selected icon rail section. When views have been pushed, renders the
 * PanelViewRenderer which resolves the top-of-stack view from the registry.
 *
 * Width and collapse/expand are managed by the parent ResizablePanelGroup.
 */
export function SubPanel() {
  const selectedPanel = useSelectedPanel();
  const panelOpen = usePanelOpen();
  const currentView = useCurrentView();
  const { closePanel } = useSidePanelActions();

  if (!panelOpen) return null;

  // If there's a view on the stack, render it instead of the panel root
  if (currentView) {
    return (
      <aside aria-label="Side panel detail view" className="flex h-full flex-col bg-background">
        <PanelViewRenderer />
      </aside>
    );
  }

  // Panel root view
  const entry = panelRootRegistry[selectedPanel];
  const PanelComponent = entry.component;

  return (
    <aside aria-label={`${entry.title} panel`} className="flex h-full flex-col bg-background">
      {/* Header — px-4 matches DESIGN.md spacing for structural headers */}
      <div className="flex min-h-(--header-height) shrink-0 items-center justify-between border-b px-4">
        <span className="text-sm font-medium text-sidebar-foreground">{entry.title}</span>
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
        <PanelComponent />
      </div>
    </aside>
  );
}
