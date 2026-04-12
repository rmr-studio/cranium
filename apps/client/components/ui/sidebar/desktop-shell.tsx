'use client';

import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from '@/components/ui/resizable';
import { SubPanel } from '@/components/ui/sidebar/sub-panel';
import { IconRail } from '@/components/ui/sidebar/icon-rail';
import { usePanelOpen, useSidePanelActions } from '@/components/ui/sidebar/context/side-panel-provider';
import {
  PANEL_DEFAULT_SIZE_PCT,
  PANEL_MAX_SIZE_PCT,
  PANEL_MIN_SIZE_PCT,
} from '@/components/ui/sidebar/types/side-panel.types';
import { type ReactNode, useEffect, useRef } from 'react';
import { type ImperativePanelHandle } from 'react-resizable-panels';

export function DesktopShell({ children }: { children: ReactNode }) {
  const panelOpen = usePanelOpen();
  const panelRef = useRef<ImperativePanelHandle>(null);
  const { closePanel, openPanel } = useSidePanelActions();

  // Sync store → library: when store says close/open, imperatively collapse/expand
  useEffect(() => {
    const panel = panelRef.current;
    if (!panel) return;
    if (panelOpen && panel.isCollapsed()) {
      panel.expand();
    } else if (!panelOpen && panel.isExpanded()) {
      panel.collapse();
    }
  }, [panelOpen]);

  const handlePanelCollapse = () => {
    closePanel();
  };

  const handlePanelExpand = () => {
    openPanel();
  };

  return (
    <div className="flex h-screen w-full bg-primary py-0.5 dark:bg-secondary">
      <IconRail />
      <ResizablePanelGroup direction="horizontal" className="h-full">
        <ResizablePanel
          ref={panelRef}
          defaultSize={PANEL_DEFAULT_SIZE_PCT}
          minSize={PANEL_MIN_SIZE_PCT}
          maxSize={PANEL_MAX_SIZE_PCT}
          collapsible
          collapsedSize={0}
          onCollapse={handlePanelCollapse}
          onExpand={handlePanelExpand}
          className="rounded-r-xl border-r-4 border-r-primary dark:border-r-secondary"
          order={1}
        >
          <SubPanel />
        </ResizablePanel>
        <ResizableHandle className="w-0 bg-transparent transition-colors duration-150 hover:bg-border focus-visible:ring-1 focus-visible:ring-ring active:bg-border/80" />
        <ResizablePanel defaultSize={100 - PANEL_DEFAULT_SIZE_PCT} minSize={40} order={2}>
          <div className="h-full min-w-0 flex-1 overflow-hidden rounded-l-lg bg-background">
            <div className="flex h-full min-w-0 flex-col overflow-auto">{children}</div>
          </div>
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>
  );
}
