'use client';

import { useIsMobile } from '@riven/hooks';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react';

export type PanelId = 'workspaces' | 'overview' | 'entities' | 'billing' | 'settings';

interface IconRailContextValue {
  /** Which panel icon is selected (stays set even when panel is collapsed) */
  selectedPanel: PanelId;
  /** Whether the sub-panel is currently visible */
  panelOpen: boolean;
  togglePanel: (id: PanelId) => void;
  closePanel: () => void;
  openPanel: () => void;
  isMobile: boolean;
  mobileOpen: boolean;
  setMobileOpen: (open: boolean) => void;
}

const IconRailContext = createContext<IconRailContextValue | null>(null);

export function IconRailProvider({ children }: { children: ReactNode }) {
  const DEFAULT_PANEL: PanelId = 'overview';
  const validPanels: PanelId[] = ['workspaces', 'overview', 'entities', 'billing', 'settings'];

  const [selectedPanel, setSelectedPanel] = useState<PanelId>(DEFAULT_PANEL);
  const [panelOpen, setPanelOpen] = useState(true);
  const [mobileOpen, setMobileOpen] = useState(false);
  const isMobile = useIsMobile();

  // Hydrate from localStorage after mount
  useEffect(() => {
    const stored = localStorage.getItem('activePanel');
    if (stored && validPanels.includes(stored as PanelId)) {
      setSelectedPanel(stored as PanelId);
    }
    setPanelOpen(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const togglePanel = useCallback(
    (id: PanelId) => {
      if (selectedPanel === id) {
        // Same icon clicked — toggle panel visibility
        const next = !panelOpen;
        setPanelOpen(next);
        if (next) localStorage.setItem('activePanel', id);
        else localStorage.removeItem('activePanel');
      } else {
        // Different icon — select it and open
        setSelectedPanel(id);
        setPanelOpen(true);
        localStorage.setItem('activePanel', id);
      }
    },
    [selectedPanel, panelOpen],
  );

  const closePanel = useCallback(() => {
    setPanelOpen(false);
    localStorage.removeItem('activePanel');
  }, []);

  const openPanel = useCallback(() => {
    setPanelOpen(true);
    localStorage.setItem('activePanel', selectedPanel);
  }, [selectedPanel]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'b') {
        const target = e.target as HTMLElement;
        if (
          target instanceof HTMLInputElement ||
          target instanceof HTMLTextAreaElement ||
          target.isContentEditable
        ) {
          return;
        }
        e.preventDefault();
        if (panelOpen) {
          closePanel();
        } else {
          openPanel();
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [panelOpen, selectedPanel, closePanel, openPanel]);

  return (
    <IconRailContext.Provider
      value={{ selectedPanel, panelOpen, togglePanel, closePanel, openPanel, isMobile, mobileOpen, setMobileOpen }}
    >
      {children}
    </IconRailContext.Provider>
  );
}

export function useIconRail() {
  const context = useContext(IconRailContext);
  if (!context) {
    throw new Error('useIconRail must be used within an IconRailProvider');
  }
  return context;
}
