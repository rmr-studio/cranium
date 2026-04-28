'use client';

import { Sheet, SheetContent } from '@/components/ui/sheet';
import { panelRootRegistry } from '@/components/ui/sidebar/components/panel-root-registry';
import { PanelViewRenderer } from '@/components/ui/sidebar/components/panel-view-renderer';
import {
  useCurrentView,
  useMobileOpen,
  useSelectedPanel,
  useSidePanelActions,
} from '@/components/ui/sidebar/context/side-panel-provider';
import { PanelLeftClose } from 'lucide-react';
import { type ReactNode } from 'react';

export function MobileShell({ children }: { children: ReactNode }) {
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
