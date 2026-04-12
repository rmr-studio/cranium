import { createStore } from 'zustand';
import { subscribeWithSelector } from 'zustand/middleware';
import {
  DEFAULT_PANEL,
  SIDE_PANEL_MAX_STACK_DEPTH,
  type PanelId,
  type SidePanelView,
} from '@/components/ui/sidebar/types/side-panel.types';

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

interface SidePanelState {
  /** Which root panel icon is selected */
  selectedPanel: PanelId;
  /** Whether the side panel is visible */
  panelOpen: boolean;
  /** Stack of detail views pushed on top of the panel root */
  viewStack: SidePanelView[];
  /** Mobile sheet open state */
  mobileOpen: boolean;
}

// ---------------------------------------------------------------------------
// Actions
// ---------------------------------------------------------------------------

interface SidePanelActions {
  togglePanel: (id: PanelId) => void;
  closePanel: () => void;
  openPanel: () => void;
  pushView: (view: SidePanelView) => void;
  popView: () => void;
  replaceView: (view: SidePanelView) => void;
  clearStack: () => void;
  setMobileOpen: (open: boolean) => void;
}

// ---------------------------------------------------------------------------
// Combined type
// ---------------------------------------------------------------------------

export type SidePanelStore = SidePanelState & SidePanelActions;
export type SidePanelStoreApi = ReturnType<typeof createSidePanelStore>;

// ---------------------------------------------------------------------------
// Factory
// ---------------------------------------------------------------------------

interface SidePanelInitState {
  selectedPanel?: PanelId;
  panelOpen?: boolean;
}

export const createSidePanelStore = (init?: SidePanelInitState) => {
  const selectedPanel = init?.selectedPanel ?? DEFAULT_PANEL;
  const panelOpen = init?.panelOpen ?? true;

  return createStore<SidePanelStore>()(
    subscribeWithSelector((set, get) => ({
      // State
      selectedPanel,
      panelOpen,
      viewStack: [],
      mobileOpen: false,

      // Actions
      togglePanel: (id: PanelId) => {
        const { selectedPanel, panelOpen } = get();
        if (selectedPanel === id) {
          const next = !panelOpen;
          set({ panelOpen: next, mobileOpen: next });
        } else {
          set({ selectedPanel: id, panelOpen: true, mobileOpen: true, viewStack: [] });
        }
      },

      closePanel: () => set({ panelOpen: false, mobileOpen: false }),

      openPanel: () => set({ panelOpen: true, mobileOpen: true }),

      pushView: (view: SidePanelView) => {
        const { viewStack } = get();
        let next = [...viewStack, view];
        if (next.length > SIDE_PANEL_MAX_STACK_DEPTH) {
          next = next.slice(next.length - SIDE_PANEL_MAX_STACK_DEPTH);
        }
        set({ viewStack: next, panelOpen: true, mobileOpen: true });
      },

      popView: () => {
        const { viewStack } = get();
        if (viewStack.length === 0) return;
        set({ viewStack: viewStack.slice(0, -1) });
      },

      replaceView: (view: SidePanelView) => {
        const { viewStack } = get();
        const nextStack = viewStack.length === 0 ? [view] : [...viewStack.slice(0, -1), view];
        set({ viewStack: nextStack, panelOpen: true, mobileOpen: true });
      },

      clearStack: () => set({ viewStack: [] }),

      setMobileOpen: (open: boolean) => set({ mobileOpen: open }),
    })),
  );
};
