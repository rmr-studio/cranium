'use client';

import { useIsMobile } from '@riven/hooks';
import { type ReactNode, useEffect, useRef } from 'react';
import { type ImperativePanelHandle } from 'react-resizable-panels';
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from '../resizable';
import { IconRail } from './icon-rail';
import { SubPanel } from './sub-panel';
import {
  useCurrentView,
  useMobileOpen,
  usePanelOpen,
  useSelectedPanel,
  useSidePanelActions,
} from './context/side-panel-provider';
import {
  PANEL_DEFAULT_SIZE_PCT,
  PANEL_MAX_SIZE_PCT,
  PANEL_MIN_SIZE_PCT,
} from './types/side-panel.types';
import { Sheet, SheetContent } from '../sheet';
import { panelRootRegistry } from './components/panel-root-registry';
import { PanelViewRenderer } from './components/panel-view-renderer';
import { PanelLeftClose } from 'lucide-react';

interface DashboardShellProps {
  children: ReactNode;
}

export function DashboardShell({ children }: DashboardShellProps) {
  const isMobile = useIsMobile();

  if (isMobile) {
    return <MobileShell>{children}</MobileShell>;
  }

  return <DesktopShell>{children}</DesktopShell>;
}

// ---------------------------------------------------------------------------
// Desktop: ResizablePanelGroup
// ---------------------------------------------------------------------------

function DesktopShell({ children }: { children: ReactNode }) {
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

  // Sync library → store: when user drags to collapse/expand
  const handlePanelCollapse = () => {
    closePanel();
  };

  const handlePanelExpand = () => {
    openPanel();
  };

  return (
    <div className="flex h-screen w-full bg-primary py-0.5 dark:bg-secondary">
      <IconRail />
      <ResizablePanelGroup
        direction="horizontal"
        className="h-full"
      >
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
        {/* Invisible resize handle — reveals subtle border on hover, focus ring for keyboard */}
        <ResizableHandle className="w-0 bg-transparent transition-colors duration-150 hover:bg-border active:bg-border/80 focus-visible:ring-1 focus-visible:ring-ring" />
        <ResizablePanel
          defaultSize={100 - PANEL_DEFAULT_SIZE_PCT}
          minSize={40}
          order={2}
        >
          <div className="min-w-0 flex-1 overflow-hidden rounded-l-lg bg-background">
            <div className="h-full min-w-0 overflow-auto">{children}</div>
          </div>
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Mobile: Sheet overlay
// ---------------------------------------------------------------------------

function MobileShell({ children }: { children: ReactNode }) {
  const mobileOpen = useMobileOpen();
  const { setMobileOpen } = useSidePanelActions();
  const currentView = useCurrentView();
  const selectedPanel = useSelectedPanel();

  const entry = panelRootRegistry[selectedPanel];
  const PanelComponent = entry.component;

  return (
    <div className="flex h-screen w-full flex-col bg-background">
      <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
        <SheetContent side="left" className="flex w-full max-w-sm flex-col p-0">
          {currentView ? (
            <PanelViewRenderer />
          ) : (
            <div className="flex h-full flex-col">
              <div className="flex min-h-(--header-height) shrink-0 items-center justify-between border-b px-4">
                <span className="text-sm font-semibold text-sidebar-foreground">{entry.title}</span>
                <button
                  onClick={() => setMobileOpen(false)}
                  aria-label="Close panel"
                  className="flex size-7 items-center justify-center rounded-md text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground"
                >
                  <PanelLeftClose className="size-4" />
                </button>
              </div>
              <div className="flex-1 overflow-y-auto px-3 py-2">
                <PanelComponent />
              </div>
            </div>
          )}
        </SheetContent>
      </Sheet>
      <div className="h-full min-w-0 overflow-auto">{children}</div>
    </div>
  );
}
