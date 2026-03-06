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
  activePanel: PanelId | null;
  togglePanel: (id: PanelId) => void;
  closePanel: () => void;
  isMobile: boolean;
  mobileOpen: boolean;
  setMobileOpen: (open: boolean) => void;
}

const IconRailContext = createContext<IconRailContextValue | null>(null);

export function IconRailProvider({ children }: { children: ReactNode }) {
  const [activePanel, setActivePanel] = useState<PanelId | null>(null);
  const [mobileOpen, setMobileOpen] = useState(false);
  const isMobile = useIsMobile();

  const validPanels: PanelId[] = ['workspaces', 'overview', 'entities', 'billing', 'settings'];

  // Hydrate from localStorage after mount
  useEffect(() => {
    const stored = localStorage.getItem('activePanel');
    if (stored && validPanels.includes(stored as PanelId)) {
      setActivePanel(stored as PanelId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const togglePanel = useCallback((id: PanelId) => {
    setActivePanel((prev) => {
      const next = prev === id ? null : id;
      if (next) localStorage.setItem('activePanel', next);
      else localStorage.removeItem('activePanel');
      return next;
    });
  }, []);

  const closePanel = useCallback(() => {
    setActivePanel(null);
    localStorage.removeItem('activePanel');
  }, []);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'b') {
        e.preventDefault();
        if (activePanel) {
          closePanel();
        } else {
          setActivePanel('overview');
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [activePanel, closePanel]);

  return (
    <IconRailContext.Provider
      value={{ activePanel, togglePanel, closePanel, isMobile, mobileOpen, setMobileOpen }}
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
