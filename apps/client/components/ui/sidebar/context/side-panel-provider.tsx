'use client';

import { createContext, useContext, useEffect, useRef, type ReactNode } from 'react';
import { useStore } from 'zustand';
import { useShallow } from 'zustand/react/shallow';
import {
  createSidePanelStore,
  type SidePanelStore,
  type SidePanelStoreApi,
} from '../stores/side-panel.store';
import { DEFAULT_PANEL, PANEL_IDS, type PanelId } from '../types/side-panel.types';

// ---------------------------------------------------------------------------
// Context
// ---------------------------------------------------------------------------

const SidePanelContext = createContext<SidePanelStoreApi | undefined>(undefined);

// ---------------------------------------------------------------------------
// Provider
// ---------------------------------------------------------------------------

export function SidePanelProvider({ children }: { children: ReactNode }) {
  const storeRef = useRef<SidePanelStoreApi | null>(null);

  // Create store once
  if (!storeRef.current) {
    // Hydrate selectedPanel from localStorage
    let initialPanel: PanelId = DEFAULT_PANEL;
    if (typeof window !== 'undefined') {
      const stored = localStorage.getItem('activePanel');
      if (stored && PANEL_IDS.includes(stored as PanelId)) {
        initialPanel = stored as PanelId;
      }
    }
    storeRef.current = createSidePanelStore({ selectedPanel: initialPanel });
  }

  // Persist selectedPanel to localStorage on change
  useEffect(() => {
    if (!storeRef.current) return;
    const unsub = storeRef.current.subscribe(
      (state) => ({ panel: state.selectedPanel, open: state.panelOpen }),
      ({ panel, open }) => {
        if (open) {
          localStorage.setItem('activePanel', panel);
        } else {
          localStorage.removeItem('activePanel');
        }
      },
    );
    return unsub;
  }, []);

  // Keyboard shortcuts:
  // - Ctrl/Cmd+B: toggle panel visibility
  // - Escape: pop stack view if any, otherwise close panel (layered dismissal)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      const isInput =
        target instanceof HTMLInputElement ||
        target instanceof HTMLTextAreaElement ||
        target.isContentEditable;

      // Ctrl/Cmd+B: toggle panel
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'b') {
        if (isInput) return;
        e.preventDefault();
        const store = storeRef.current;
        if (!store) return;
        const { panelOpen, closePanel, openPanel } = store.getState();
        if (panelOpen) {
          closePanel();
        } else {
          openPanel();
        }
        return;
      }

      // Escape: layered dismissal (pop stack first, then close panel)
      if (e.key === 'Escape') {
        if (isInput) return;
        const store = storeRef.current;
        if (!store) return;
        const { panelOpen, viewStack, popView, closePanel } = store.getState();
        if (!panelOpen) return;
        e.preventDefault();
        if (viewStack.length > 0) {
          popView();
        } else {
          closePanel();
        }
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  return (
    <SidePanelContext.Provider value={storeRef.current}>{children}</SidePanelContext.Provider>
  );
}

// ---------------------------------------------------------------------------
// Base hook
// ---------------------------------------------------------------------------

export function useSidePanelStore<T>(selector: (store: SidePanelStore) => T): T {
  const context = useContext(SidePanelContext);
  if (!context) {
    throw new Error('useSidePanelStore must be used within a SidePanelProvider');
  }
  return useStore(context, selector);
}

// ---------------------------------------------------------------------------
// Convenience selector hooks
// ---------------------------------------------------------------------------

/** Which root panel is selected */
export const useSelectedPanel = () => useSidePanelStore((s) => s.selectedPanel);

/** Whether the panel is open (desktop) */
export const usePanelOpen = () => useSidePanelStore((s) => s.panelOpen);

/** The current detail view on top of the stack, or undefined (derived from viewStack) */
export const useCurrentView = () => useSidePanelStore((s) => s.viewStack.at(-1));

/** How many views are on the stack — for back button visibility (derived from viewStack) */
export const useStackDepth = () => useSidePanelStore((s) => s.viewStack.length);

/** Mobile sheet state */
export const useMobileOpen = () => useSidePanelStore((s) => s.mobileOpen);

/** All actions — uses useShallow to prevent re-renders from new object references */
export const useSidePanelActions = () =>
  useSidePanelStore(
    useShallow((s) => ({
      togglePanel: s.togglePanel,
      closePanel: s.closePanel,
      openPanel: s.openPanel,
      pushView: s.pushView,
      popView: s.popView,
      replaceView: s.replaceView,
      clearStack: s.clearStack,
      setMobileOpen: s.setMobileOpen,
    })),
  );
