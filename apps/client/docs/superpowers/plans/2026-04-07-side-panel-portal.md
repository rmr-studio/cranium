# Side Panel Portal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-dcranium-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the side panel from a flat navigation list into a stack-based content portal that can display detail views pushed from main content, with back navigation, resizable width, and mobile sheet fallback.

**Architecture:** Zustand store (factory+context+provider) replaces `IconRailContext` to manage panel selection, a discriminated-union view stack with push/pop/replace, and resize width. Desktop layout uses `react-resizable-panels` (already installed via shadcn). Mobile renders the panel as a full-screen `Sheet` overlay. Detail views are lazy-loaded via a typed view registry.

**Tech Stack:** Zustand + subscribeWithSelector, react-resizable-panels (shadcn Resizable), React.lazy + Suspense, Framer Motion (content transitions only), Next.js App Router

---

## File Structure

### New files

| File | Responsibility |
|------|---------------|
| `components/ui/sidebar/stores/side-panel.store.ts` | Zustand store factory: panel selection, view stack, resize width, mobile state |
| `components/ui/sidebar/stores/side-panel.store.test.ts` | Comprehensive store unit tests |
| `components/ui/sidebar/context/side-panel-provider.tsx` | Provider + context + selector hooks |
| `components/ui/sidebar/types/side-panel.types.ts` | `PanelId`, `SidePanelView` discriminated union, `PanelRegistryEntry`, store types |
| `components/ui/sidebar/components/panel-view-frame.tsx` | Shared frame: header (title + back + close), scrollable content |
| `components/ui/sidebar/components/panel-view-renderer.tsx` | Resolves current stack view to lazy component via registry, wraps in Suspense |
| `components/ui/sidebar/components/panel-view-registry.ts` | Maps `SidePanelView['type']` to lazy components + metadata |
| `components/ui/sidebar/components/panel-view-registry.test.ts` | Exhaustiveness test: every union member has a registry entry |
| `components/ui/sidebar/components/panel-error-fallback.tsx` | Error boundary fallback with retry button for failed lazy loads |
| `components/ui/sidebar/components/panel-skeleton.tsx` | Skeleton fallback for Suspense during lazy load |

### Modified files

| File | Changes |
|------|---------|
| `components/ui/sidebar/sub-panel.tsx` | Replace `useIconRail()` with `useSidePanelStore()`. Remove parallel `panelTitles`/`panelComponents` maps — use single registry. Render stack view when stack is non-empty, panel root otherwise. |
| `components/ui/sidebar/icon-rail.tsx` | Replace `useIconRail()` with `useSidePanelStore()` selectors. |
| `components/ui/sidebar/icon-rail-context.tsx` | **Delete entirely** — replaced by Zustand store + provider. |
| `components/ui/nav/navbar.tsx` | Replace `useIconRail()` with `useSidePanelStore()` selectors. |
| `app/dashboard/layout.tsx` | Replace `IconRailProvider` with `SidePanelProvider`. Restructure layout to use `ResizablePanelGroup` on desktop. |
| `components/ui/sidebar/dashboard-content.tsx` | Remove — content becomes a `ResizablePanel`. |
| `app/globals.css` | Remove `--sub-panel-width` (now controlled by resize library). Keep `--icon-rail-width` and `--header-height`. |

### Files that import `useIconRail()` (all must migrate)

1. `components/ui/sidebar/icon-rail-context.tsx` — deleted
2. `components/ui/sidebar/icon-rail.tsx` — migrated
3. `components/ui/sidebar/sub-panel.tsx` — migrated
4. `components/ui/nav/navbar.tsx` — migrated

---

## Task 1: Side Panel Types

**Files:**
- Create: `components/ui/sidebar/types/side-panel.types.ts`

- [ ] **Step 1: Create the types file**

```typescript
// components/ui/sidebar/types/side-panel.types.ts

import type { ComponentType } from 'react';

/**
 * Identifies which root panel is selected in the icon rail.
 */
export type PanelId =
  | 'workspaces'
  | 'overview'
  | 'entities'
  | 'knowledge'
  | 'notes'
  | 'billing'
  | 'settings';

/**
 * All valid panel IDs — used for runtime validation (e.g. localStorage hydration).
 */
export const PANEL_IDS: PanelId[] = [
  'workspaces',
  'overview',
  'entities',
  'knowledge',
  'notes',
  'billing',
  'settings',
];

/**
 * Discriminated union of all views that can be pushed onto the side panel stack.
 * Add new members here when creating new detail views.
 *
 * Each member must have:
 * - `type`: unique string literal (used as registry key)
 * - `title`: display title for the panel header
 * - any data props the detail component needs
 */
export type SidePanelView =
  | { type: 'definition-detail'; title: string; definitionId: string; workspaceId: string }
  | { type: 'entity-notes'; title: string; entityId: string; workspaceId: string }
  | { type: 'integration-detail'; title: string; integrationId: string; workspaceId: string };

/**
 * Extract the `type` literal union from SidePanelView.
 */
export type SidePanelViewType = SidePanelView['type'];

/**
 * Registry entry for a panel root (icon rail section).
 */
export interface PanelRegistryEntry {
  title: string;
  component: ComponentType;
}

/**
 * Maximum number of views on the stack before oldest entries are dropped.
 */
export const SIDE_PANEL_MAX_STACK_DEPTH = 10;

/**
 * Default panel shown on first load.
 */
export const DEFAULT_PANEL: PanelId = 'overview';

/**
 * Resize constraints (percentage of viewport width).
 */
export const PANEL_MIN_SIZE_PCT = 15;
export const PANEL_MAX_SIZE_PCT = 40;
export const PANEL_DEFAULT_SIZE_PCT = 25;
```

- [ ] **Step 2: Commit**

```bash
git add components/ui/sidebar/types/side-panel.types.ts
git commit -m "feat(side-panel): add discriminated union types for panel views and registry"
```

---

## Task 2: Side Panel Zustand Store

**Files:**
- Create: `components/ui/sidebar/stores/side-panel.store.ts`

- [ ] **Step 1: Write the store test file**

Create `components/ui/sidebar/stores/side-panel.store.test.ts`:

```typescript
// components/ui/sidebar/stores/side-panel.store.test.ts

import { createSidePanelStore } from './side-panel.store';
import type { SidePanelView } from '../types/side-panel.types';

const makeView = (type: SidePanelView['type'], suffix = ''): SidePanelView => {
  switch (type) {
    case 'definition-detail':
      return { type, title: `Def${suffix}`, definitionId: `def-${suffix}`, workspaceId: 'ws-1' };
    case 'entity-notes':
      return { type, title: `Notes${suffix}`, entityId: `ent-${suffix}`, workspaceId: 'ws-1' };
    case 'integration-detail':
      return { type, title: `Int${suffix}`, integrationId: `int-${suffix}`, workspaceId: 'ws-1' };
  }
};

describe('SidePanelStore', () => {
  describe('initial state', () => {
    it('starts with overview panel selected and open', () => {
      const store = createSidePanelStore();
      const state = store.getState();
      expect(state.selectedPanel).toBe('overview');
      expect(state.panelOpen).toBe(true);
      expect(state.viewStack).toEqual([]);
    });

    it('accepts initial state overrides', () => {
      const store = createSidePanelStore({ selectedPanel: 'settings', panelOpen: false });
      const state = store.getState();
      expect(state.selectedPanel).toBe('settings');
      expect(state.panelOpen).toBe(false);
    });
  });

  describe('togglePanel', () => {
    it('switches to a different panel and opens it', () => {
      const store = createSidePanelStore();
      store.getState().togglePanel('entities');
      expect(store.getState().selectedPanel).toBe('entities');
      expect(store.getState().panelOpen).toBe(true);
    });

    it('toggles visibility when same panel is clicked', () => {
      const store = createSidePanelStore();
      // overview is selected and open by default
      store.getState().togglePanel('overview');
      expect(store.getState().panelOpen).toBe(false);

      store.getState().togglePanel('overview');
      expect(store.getState().panelOpen).toBe(true);
    });

    it('clears the view stack when switching panels', () => {
      const store = createSidePanelStore();
      store.getState().pushView(makeView('definition-detail', '1'));
      expect(store.getState().viewStack).toHaveLength(1);

      store.getState().togglePanel('settings');
      expect(store.getState().viewStack).toEqual([]);
    });
  });

  describe('closePanel / openPanel', () => {
    it('closePanel sets panelOpen to false', () => {
      const store = createSidePanelStore();
      store.getState().closePanel();
      expect(store.getState().panelOpen).toBe(false);
    });

    it('openPanel sets panelOpen to true', () => {
      const store = createSidePanelStore({ panelOpen: false });
      store.getState().openPanel();
      expect(store.getState().panelOpen).toBe(true);
    });
  });

  describe('pushView', () => {
    it('pushes a view onto the stack', () => {
      const store = createSidePanelStore();
      const view = makeView('definition-detail', '1');
      store.getState().pushView(view);
      expect(store.getState().viewStack).toEqual([view]);
    });

    it('pushes multiple views creating a stack', () => {
      const store = createSidePanelStore();
      const v1 = makeView('definition-detail', '1');
      const v2 = makeView('entity-notes', '2');
      store.getState().pushView(v1);
      store.getState().pushView(v2);
      expect(store.getState().viewStack).toEqual([v1, v2]);
    });

    it('opens the panel if it was closed', () => {
      const store = createSidePanelStore({ panelOpen: false });
      store.getState().pushView(makeView('definition-detail', '1'));
      expect(store.getState().panelOpen).toBe(true);
    });

    it('caps stack at max depth, dropping oldest entries', () => {
      const store = createSidePanelStore();
      for (let i = 0; i < 12; i++) {
        store.getState().pushView(makeView('definition-detail', String(i)));
      }
      expect(store.getState().viewStack).toHaveLength(10);
      // Oldest (0, 1) dropped — first remaining is index 2
      expect(store.getState().viewStack[0]).toEqual(makeView('definition-detail', '2'));
    });
  });

  describe('popView', () => {
    it('removes the top view from the stack', () => {
      const store = createSidePanelStore();
      const v1 = makeView('definition-detail', '1');
      const v2 = makeView('entity-notes', '2');
      store.getState().pushView(v1);
      store.getState().pushView(v2);
      store.getState().popView();
      expect(store.getState().viewStack).toEqual([v1]);
    });

    it('is a no-op on empty stack', () => {
      const store = createSidePanelStore();
      store.getState().popView(); // should not throw
      expect(store.getState().viewStack).toEqual([]);
    });
  });

  describe('replaceView', () => {
    it('replaces the top view', () => {
      const store = createSidePanelStore();
      const v1 = makeView('definition-detail', '1');
      const v2 = makeView('entity-notes', '2');
      store.getState().pushView(v1);
      store.getState().replaceView(v2);
      expect(store.getState().viewStack).toEqual([v2]);
    });

    it('acts as push when stack is empty', () => {
      const store = createSidePanelStore();
      const v1 = makeView('definition-detail', '1');
      store.getState().replaceView(v1);
      expect(store.getState().viewStack).toEqual([v1]);
    });
  });

  describe('clearStack', () => {
    it('clears all views from the stack', () => {
      const store = createSidePanelStore();
      store.getState().pushView(makeView('definition-detail', '1'));
      store.getState().pushView(makeView('entity-notes', '2'));
      store.getState().clearStack();
      expect(store.getState().viewStack).toEqual([]);
    });
  });

  describe('mobile state', () => {
    it('mobileOpen defaults to false', () => {
      const store = createSidePanelStore();
      expect(store.getState().mobileOpen).toBe(false);
    });

    it('setMobileOpen updates mobile state', () => {
      const store = createSidePanelStore();
      store.getState().setMobileOpen(true);
      expect(store.getState().mobileOpen).toBe(true);
    });

    it('mobile state does not affect view stack', () => {
      const store = createSidePanelStore();
      store.getState().pushView(makeView('definition-detail', '1'));
      store.getState().setMobileOpen(true);
      expect(store.getState().viewStack).toHaveLength(1);
    });
  });

  describe('derived selectors (computed from viewStack)', () => {
    it('viewStack.at(-1) returns undefined when stack is empty', () => {
      const store = createSidePanelStore();
      expect(store.getState().viewStack.at(-1)).toBeUndefined();
    });

    it('viewStack.at(-1) returns the top of stack', () => {
      const store = createSidePanelStore();
      const v1 = makeView('definition-detail', '1');
      const v2 = makeView('entity-notes', '2');
      store.getState().pushView(v1);
      store.getState().pushView(v2);
      expect(store.getState().viewStack.at(-1)).toEqual(v2);
    });

    it('viewStack.length returns 0 for empty stack', () => {
      const store = createSidePanelStore();
      expect(store.getState().viewStack.length).toBe(0);
    });

    it('viewStack.length returns correct depth', () => {
      const store = createSidePanelStore();
      store.getState().pushView(makeView('definition-detail', '1'));
      store.getState().pushView(makeView('entity-notes', '2'));
      expect(store.getState().viewStack.length).toBe(2);
    });
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /home/jared/dev/worktrees/sidepanel/apps/client && npx jest components/ui/sidebar/stores/side-panel.store.test.ts --no-coverage`
Expected: FAIL — module not found

- [ ] **Step 3: Write the store implementation**

```typescript
// components/ui/sidebar/stores/side-panel.store.ts

import { createStore } from 'zustand';
import { subscribeWithSelector } from 'zustand/middleware';
import {
  DEFAULT_PANEL,
  SIDE_PANEL_MAX_STACK_DEPTH,
  type PanelId,
  type SidePanelView,
} from '../types/side-panel.types';

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

      // Actions — currentView and stackDepth are derived via selectors in the provider,
      // NOT stored here. This prevents sync bugs when multiple actions mutate viewStack.
      togglePanel: (id: PanelId) => {
        const { selectedPanel, panelOpen } = get();
        if (selectedPanel === id) {
          set({ panelOpen: !panelOpen });
        } else {
          set({ selectedPanel: id, panelOpen: true, viewStack: [] });
        }
      },

      closePanel: () => set({ panelOpen: false }),

      openPanel: () => set({ panelOpen: true }),

      pushView: (view: SidePanelView) => {
        const { viewStack } = get();
        let next = [...viewStack, view];
        if (next.length > SIDE_PANEL_MAX_STACK_DEPTH) {
          next = next.slice(next.length - SIDE_PANEL_MAX_STACK_DEPTH);
        }
        set({ viewStack: next, panelOpen: true });
      },

      popView: () => {
        const { viewStack } = get();
        if (viewStack.length === 0) return;
        set({ viewStack: viewStack.slice(0, -1) });
      },

      replaceView: (view: SidePanelView) => {
        const { viewStack } = get();
        if (viewStack.length === 0) {
          set({ viewStack: [view] });
        } else {
          set({ viewStack: [...viewStack.slice(0, -1), view] });
        }
      },

      clearStack: () => set({ viewStack: [] }),

      setMobileOpen: (open: boolean) => set({ mobileOpen: open }),
    })),
  );
};
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd /home/jared/dev/worktrees/sidepanel/apps/client && npx jest components/ui/sidebar/stores/side-panel.store.test.ts --no-coverage`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add components/ui/sidebar/stores/side-panel.store.ts components/ui/sidebar/stores/side-panel.store.test.ts
git commit -m "feat(side-panel): add Zustand store with navigation stack, push/pop/replace"
```

---

## Task 3: Side Panel Provider

**Files:**
- Create: `components/ui/sidebar/context/side-panel-provider.tsx`

- [ ] **Step 1: Write the provider with context and selector hooks**

```typescript
// components/ui/sidebar/context/side-panel-provider.tsx

'use client';

import { createContext, useContext, useEffect, useRef, type ReactNode } from 'react';
import { useStore } from 'zustand';
import { useShallow } from 'zustand/react/shallow';
import { useIsMobile } from '@cranium/hooks';
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
  const isMobile = useIsMobile();
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
        const { panelOpen, stackDepth, popView, closePanel } = store.getState();
        if (!panelOpen) return;
        e.preventDefault();
        if (stackDepth > 0) {
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
```

- [ ] **Step 2: Commit**

```bash
git add components/ui/sidebar/context/side-panel-provider.tsx
git commit -m "feat(side-panel): add provider with localStorage persistence and keyboard shortcut"
```

---

## Task 4: Panel View Frame and Skeleton

**Files:**
- Create: `components/ui/sidebar/components/panel-view-frame.tsx`
- Create: `components/ui/sidebar/components/panel-skeleton.tsx`

- [ ] **Step 1: Create the skeleton component**

```typescript
// components/ui/sidebar/components/panel-skeleton.tsx

import { Skeleton } from '@/components/ui/skeleton';

export function PanelSkeleton() {
  return (
    <div className="flex flex-col gap-3 p-4">
      <Skeleton className="h-5 w-2/3 rounded" />
      <Skeleton className="h-4 w-full rounded" />
      <Skeleton className="h-4 w-5/6 rounded" />
      <Skeleton className="h-32 w-full rounded" />
      <Skeleton className="h-4 w-3/4 rounded" />
    </div>
  );
}
```

- [ ] **Step 2: Create the panel view frame**

```typescript
// components/ui/sidebar/components/panel-view-frame.tsx

'use client';

import { Button } from '@cranium/ui/button';
import { ChevronLeft, X } from 'lucide-react';
import { useEffect, useRef } from 'react';

interface PanelViewFrameProps {
  title: string;
  onBack?: () => void;
  onClose?: () => void;
  children: React.ReactNode;
}

export function PanelViewFrame({ title, onBack, onClose, children }: PanelViewFrameProps) {
  const titleRef = useRef<HTMLSpanElement>(null);

  // Focus management: move focus to title when view mounts
  useEffect(() => {
    titleRef.current?.focus();
  }, [title]);

  return (
    <div className="flex h-full flex-col">
      {/* Header — px-4 matches existing sub-panel header (per DESIGN.md alignment) */}
      <div className="flex min-h-(--header-height) shrink-0 items-center gap-2 border-b px-4">
        {onBack && (
          <Button
            variant="ghost"
            size="icon"
            className="size-7 shrink-0"
            onClick={onBack}
            aria-label="Back"
          >
            <ChevronLeft className="size-4" />
          </Button>
        )}
        <span
          ref={titleRef}
          tabIndex={-1}
          className="min-w-0 flex-1 truncate text-sm font-medium text-sidebar-foreground outline-none"
        >
          {title}
        </span>
        {onClose && (
          <Button
            variant="ghost"
            size="icon"
            className="size-7 shrink-0 text-sidebar-foreground/70 hover:text-sidebar-foreground"
            onClick={onClose}
            aria-label="Close"
          >
            <X className="size-4" />
          </Button>
        )}
      </div>

      {/* Scrollable content */}
      <div className="flex-1 overflow-y-auto px-3 py-2">{children}</div>
    </div>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add components/ui/sidebar/components/panel-skeleton.tsx components/ui/sidebar/components/panel-view-frame.tsx components/ui/sidebar/components/panel-error-fallback.tsx
git commit -m "feat(side-panel): add PanelViewFrame, PanelSkeleton, and PanelErrorFallback"
```

---

## Task 5: View Registry with Exhaustiveness Test

**Files:**
- Create: `components/ui/sidebar/components/panel-view-registry.ts`
- Create: `components/ui/sidebar/components/panel-view-registry.test.ts`
- Create: `components/ui/sidebar/components/panel-view-renderer.tsx`

- [ ] **Step 1: Write the registry exhaustiveness test**

```typescript
// components/ui/sidebar/components/panel-view-registry.test.ts

import { viewRegistry } from './panel-view-registry';
import type { SidePanelViewType } from '../types/side-panel.types';

/**
 * This test ensures every member of the SidePanelView discriminated union
 * has a corresponding entry in the view registry. If you add a new view type
 * to side-panel.types.ts, this test will fail until you add a registry entry.
 */
describe('viewRegistry', () => {
  // Manually list all SidePanelView type literals.
  // If you add a new type, add it here — the test will catch missing registry entries.
  const allViewTypes: SidePanelViewType[] = [
    'definition-detail',
    'entity-notes',
    'integration-detail',
  ];

  it.each(allViewTypes)('has a registry entry for "%s"', (viewType) => {
    expect(viewRegistry).toHaveProperty(viewType);
    expect(viewRegistry[viewType]).toBeDefined();
  });

  it('registry has no extra entries beyond known view types', () => {
    const registryKeys = Object.keys(viewRegistry);
    expect(registryKeys.sort()).toEqual([...allViewTypes].sort());
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /home/jared/dev/worktrees/sidepanel/apps/client && npx jest components/ui/sidebar/components/panel-view-registry.test.ts --no-coverage`
Expected: FAIL — module not found

- [ ] **Step 3: Create the view registry**

```typescript
// components/ui/sidebar/components/panel-view-registry.ts

import { lazy, type ComponentType, type LazyExoticComponent } from 'react';
import type { SidePanelView, SidePanelViewType } from '../types/side-panel.types';

/**
 * Props that every detail view component receives.
 * The component gets the full SidePanelView object matching its type.
 */
export type ViewComponentProps<T extends SidePanelViewType> = Extract<SidePanelView, { type: T }>;

/**
 * Registry mapping each view type to its lazy-loaded component.
 *
 * To add a new view:
 * 1. Add a member to `SidePanelView` in `side-panel.types.ts`
 * 2. Create the component in `components/ui/sidebar/views/`
 * 3. Add a lazy entry here
 *
 * TypeScript enforces exhaustiveness via the `satisfies` check.
 */
export const viewRegistry: Record<SidePanelViewType, LazyExoticComponent<ComponentType<any>>> = {
  'definition-detail': lazy(() =>
    import('../views/definition-detail-view').then((m) => ({ default: m.DefinitionDetailView })),
  ),
  'entity-notes': lazy(() =>
    import('../views/entity-notes-view').then((m) => ({ default: m.EntityNotesView })),
  ),
  'integration-detail': lazy(() =>
    import('../views/integration-detail-view').then((m) => ({ default: m.IntegrationDetailView })),
  ),
} satisfies Record<SidePanelViewType, LazyExoticComponent<ComponentType<any>>>;
```

- [ ] **Step 4: Create placeholder view components so the registry resolves**

Create three placeholder files. These will be implemented properly when each feature is wired up. For now they render enough to prove the registry works.

`components/ui/sidebar/views/definition-detail-view.tsx`:

```typescript
'use client';

import type { SidePanelView } from '../types/side-panel.types';

type Props = Extract<SidePanelView, { type: 'definition-detail' }>;

export function DefinitionDetailView({ definitionId, workspaceId }: Props) {
  return (
    <div className="space-y-2 p-2">
      <p className="text-sm text-muted-foreground">Definition detail view</p>
      <p className="text-xs text-muted-foreground">ID: {definitionId}</p>
    </div>
  );
}
```

`components/ui/sidebar/views/entity-notes-view.tsx`:

```typescript
'use client';

import type { SidePanelView } from '../types/side-panel.types';

type Props = Extract<SidePanelView, { type: 'entity-notes' }>;

export function EntityNotesView({ entityId, workspaceId }: Props) {
  return (
    <div className="space-y-2 p-2">
      <p className="text-sm text-muted-foreground">Entity notes view</p>
      <p className="text-xs text-muted-foreground">Entity: {entityId}</p>
    </div>
  );
}
```

`components/ui/sidebar/views/integration-detail-view.tsx`:

```typescript
'use client';

import type { SidePanelView } from '../types/side-panel.types';

type Props = Extract<SidePanelView, { type: 'integration-detail' }>;

export function IntegrationDetailView({ integrationId, workspaceId }: Props) {
  return (
    <div className="space-y-2 p-2">
      <p className="text-sm text-muted-foreground">Integration detail view</p>
      <p className="text-xs text-muted-foreground">Integration: {integrationId}</p>
    </div>
  );
}
```

- [ ] **Step 5: Create the panel view renderer**

First create the error fallback component:

`components/ui/sidebar/components/panel-error-fallback.tsx`:

```typescript
'use client';

import { Button } from '@cranium/ui/button';
import { AlertCircle, RotateCcw } from 'lucide-react';

interface PanelErrorFallbackProps {
  onRetry: () => void;
}

export function PanelErrorFallback({ onRetry }: PanelErrorFallbackProps) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-12 text-center">
      <AlertCircle className="size-8 text-muted-foreground/40" />
      <p className="text-sm text-muted-foreground">Failed to load panel content</p>
      <Button variant="outline" size="sm" onClick={onRetry}>
        <RotateCcw className="mr-1.5 size-3.5" />
        Retry
      </Button>
    </div>
  );
}
```

Then the renderer with error boundary and crossfade transitions:

```typescript
// components/ui/sidebar/components/panel-view-renderer.tsx

'use client';

import { Component, Suspense, type ReactNode } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useCurrentView, useStackDepth, useSidePanelActions } from '../context/side-panel-provider';
import { viewRegistry } from './panel-view-registry';
import { PanelSkeleton } from './panel-skeleton';
import { PanelViewFrame } from './panel-view-frame';
import { PanelErrorFallback } from './panel-error-fallback';

// Error boundary for lazy-loaded view components
interface ErrorBoundaryState { hasError: boolean }

class PanelErrorBoundary extends Component<
  { fallback: ReactNode; children: ReactNode; resetKey: string },
  ErrorBoundaryState
> {
  constructor(props: { fallback: ReactNode; children: ReactNode; resetKey: string }) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  componentDidUpdate(prevProps: { resetKey: string }) {
    if (prevProps.resetKey !== this.props.resetKey) {
      this.setState({ hasError: false });
    }
  }

  render() {
    if (this.state.hasError) return this.props.fallback;
    return this.props.children;
  }
}

/**
 * Renders the current stack view using the registry.
 * Returns null when the stack is empty (caller should render panel root instead).
 *
 * Includes:
 * - Error boundary with retry for failed lazy loads
 * - 150ms crossfade transition on push/pop (per DESIGN.md short timing)
 * - Suspense with skeleton fallback
 */
export function PanelViewRenderer() {
  const currentView = useCurrentView();
  const stackDepth = useStackDepth();
  const { popView, clearStack } = useSidePanelActions();

  if (!currentView) return null;

  const ViewComponent = viewRegistry[currentView.type];
  const viewKey = `${currentView.type}-${JSON.stringify(currentView)}`;

  return (
    <PanelViewFrame
      title={currentView.title}
      onBack={stackDepth > 0 ? popView : undefined}
      onClose={clearStack}
    >
      <PanelErrorBoundary
        resetKey={viewKey}
        fallback={<PanelErrorFallback onRetry={() => clearStack()} />}
      >
        <AnimatePresence mode="wait">
          <motion.div
            key={viewKey}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15, ease: 'easeOut' }}
            className="h-full"
          >
            <Suspense fallback={<PanelSkeleton />}>
              <ViewComponent {...(currentView as any)} />
            </Suspense>
          </motion.div>
        </AnimatePresence>
      </PanelErrorBoundary>
    </PanelViewFrame>
  );
}
```

- [ ] **Step 6: Run the registry test to verify it passes**

Run: `cd /home/jared/dev/worktrees/sidepanel/apps/client && npx jest components/ui/sidebar/components/panel-view-registry.test.ts --no-coverage`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add components/ui/sidebar/components/panel-view-registry.ts components/ui/sidebar/components/panel-view-registry.test.ts components/ui/sidebar/components/panel-view-renderer.tsx components/ui/sidebar/views/
git commit -m "feat(side-panel): add view registry with lazy loading, renderer, and placeholder views"
```

---

## Task 6: Panel Root Registry (Single Source of Truth)

**Files:**
- Create: `components/ui/sidebar/components/panel-root-registry.ts`

This replaces the duplicate `panelTitles` and `panelComponents` maps in `sub-panel.tsx`.

- [ ] **Step 1: Create the panel root registry**

```typescript
// components/ui/sidebar/components/panel-root-registry.ts

import type { ComponentType } from 'react';
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
```

- [ ] **Step 2: Commit**

```bash
git add components/ui/sidebar/components/panel-root-registry.ts
git commit -m "feat(side-panel): add single-source panel root registry"
```

---

## Task 7: Rewrite SubPanel to Use Store + Registries + View Stack

**Files:**
- Modify: `components/ui/sidebar/sub-panel.tsx`

- [ ] **Step 1: Rewrite sub-panel.tsx**

Replace the entire contents of `components/ui/sidebar/sub-panel.tsx`:

```typescript
// components/ui/sidebar/sub-panel.tsx

'use client';

import { PanelLeftClose } from 'lucide-react';
import {
  useCurrentView,
  usePanelOpen,
  useSelectedPanel,
  useSidePanelActions,
} from './context/side-panel-provider';
import { panelRootRegistry } from './components/panel-root-registry';
import { PanelViewRenderer } from './components/panel-view-renderer';

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
```

- [ ] **Step 2: Commit**

```bash
git add components/ui/sidebar/sub-panel.tsx
git commit -m "refactor(side-panel): rewrite SubPanel to use store, registries, and view stack"
```

---

## Task 8: Migrate Icon Rail to Store

**Files:**
- Modify: `components/ui/sidebar/icon-rail.tsx`

- [ ] **Step 1: Replace useIconRail imports with store selectors**

Replace the entire contents of `components/ui/sidebar/icon-rail.tsx`:

```typescript
// components/ui/sidebar/icon-rail.tsx

'use client';

import { useProfile } from '@/components/feature-modules/user/hooks/use-profile';
import { WorkspaceIcon } from '@/components/feature-modules/workspace/components/workspace-icon';
import { useWorkspaceStore } from '@/components/feature-modules/workspace/provider/workspace-provider';
import { Logo } from '@cranium/ui/logo';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@cranium/ui/tooltip';
import { cn } from '@cranium/utils';
import { useIsMobile } from '@cranium/hooks';
import { BookOpen, Building2, CogIcon, SquareDashedMousePointer, StickyNote, TrendingUpDown } from 'lucide-react';
import { Kbd, KbdGroup } from '../kbd';
import { Skeleton } from '../skeleton';
import {
  useSelectedPanel,
  useSidePanelActions,
} from './context/side-panel-provider';
import type { PanelId } from './types/side-panel.types';

interface RailButton {
  id: PanelId;
  icon: React.ReactNode;
  label: string;
}

const navItems: RailButton[] = [
  { id: 'overview', icon: <Building2 className="size-5" />, label: 'Overview' },
  { id: 'entities', icon: <SquareDashedMousePointer className="size-5" />, label: 'Entities' },
  { id: 'knowledge', icon: <BookOpen className="size-5" />, label: 'Knowledge' },
  { id: 'notes', icon: <StickyNote className="size-5" />, label: 'Notes' },
  { id: 'billing', icon: <TrendingUpDown className="size-5" />, label: 'Billing' },
  { id: 'settings', icon: <CogIcon className="size-5" />, label: 'Settings' },
];

function SelectedWorkspaceIcon() {
  const { data, isPending, isLoadingAuth } = useProfile();
  const selectedWorkspaceId = useWorkspaceStore((s) => s.selectedWorkspaceId);

  const workspace = data?.memberships.find(
    (m) => m.workspace?.id === selectedWorkspaceId,
  )?.workspace;

  if (isPending || isLoadingAuth) {
    return <Skeleton className="size-8 rounded-md" />;
  }

  return (
    <WorkspaceIcon
      name={workspace?.name ?? 'Workspace'}
      avatarUrl={workspace?.avatarUrl}
    />
  );
}

export function IconRail() {
  const selectedPanel = useSelectedPanel();
  const { togglePanel } = useSidePanelActions();
  const isMobile = useIsMobile();

  if (isMobile) return null;

  return (
    <TooltipProvider delayDuration={0}>
      <aside className="flex h-full w-(--icon-rail-width) shrink-0 flex-col items-center bg-foreground dark:bg-secondary">
        {/* Top section — matches header height */}
        <div className="flex h-(--header-height) w-full shrink-0 flex-col items-center justify-center gap-1 border-b border-background/15 [--logo-primary:var(--background)] dark:[--logo-primary:var(--foreground)]">
          <Logo size={24} />
        </div>

        {/* Workspace switcher */}
        <div className="pt-2">
          <Tooltip>
            <TooltipTrigger asChild>
              <button
                onClick={() => togglePanel('workspaces')}
                aria-label="Workspaces"
                className={cn(
                  'mb-1 flex items-center justify-center rounded-md p-1 transition-colors hover:bg-background/10',
                  selectedPanel === 'workspaces' && 'bg-background/15',
                )}
              >
                <SelectedWorkspaceIcon />
              </button>
            </TooltipTrigger>
            <TooltipContent side="right">Workspaces</TooltipContent>
          </Tooltip>
        </div>

        {/* Separator */}
        <div className="mx-auto my-2 h-px w-8 bg-background/20" />

        {/* Nav items */}
        <nav className="flex flex-1 flex-col items-center gap-1">
          {navItems.map((item) => (
            <Tooltip key={item.id}>
              <TooltipTrigger asChild>
                <button
                  onClick={() => togglePanel(item.id)}
                  aria-label={item.label}
                  aria-pressed={selectedPanel === item.id}
                  className={cn(
                    'flex size-10 items-center justify-center rounded-md text-background/60 transition-colors hover:bg-background/10 hover:text-background dark:text-foreground/50',
                    selectedPanel === item.id &&
                      'bg-background/15 text-background dark:bg-foreground/20 dark:text-foreground',
                  )}
                >
                  {item.icon}
                </button>
              </TooltipTrigger>
              <TooltipContent side="right">{item.label}</TooltipContent>
            </Tooltip>
          ))}
        </nav>

        {/* Keyboard shortcut hint */}
        <div className="mt-auto mb-3 flex flex-col items-center">
          <KbdGroup className="scale-75 text-background/40">
            <Kbd className="h-4 min-w-4 bg-background/10 text-[10px] text-background/40">
              {typeof navigator !== 'undefined' && /Mac|iPod|iPhone|iPad/.test(navigator.platform)
                ? '⌘'
                : 'Ctrl'}
            </Kbd>
            <Kbd className="h-4 min-w-4 bg-background/10 text-[10px] text-background/40">B</Kbd>
          </KbdGroup>
        </div>
      </aside>
    </TooltipProvider>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add components/ui/sidebar/icon-rail.tsx
git commit -m "refactor(side-panel): migrate IconRail from context to Zustand store"
```

---

## Task 9: Migrate Navbar to Store

**Files:**
- Modify: `components/ui/nav/navbar.tsx`

- [ ] **Step 1: Replace useIconRail with store selectors**

Replace the entire contents of `components/ui/nav/navbar.tsx`:

```typescript
// components/ui/nav/navbar.tsx

'use client';

import { UserProfileDropdown } from '@/components/feature-modules/user/components/avatar-dropdown';
import { useProfile } from '@/components/feature-modules/user/hooks/use-profile';
import { ConnectionStatus } from '@/components/feature-modules/workspace/components/connection-status';
import { Skeleton } from '@/components/ui/skeleton';
import {
  usePanelOpen,
  useSidePanelActions,
} from '@/components/ui/sidebar/context/side-panel-provider';
import { Button } from '@cranium/ui/button';
import { ThemeToggle } from '@cranium/ui/theme-toggle';
import { useIsMobile } from '@cranium/hooks';
import { AnimatePresence, motion } from 'framer-motion';
import { Menu, PanelLeftOpen } from 'lucide-react';
import Link from 'next/link';
import { FC } from 'react';

export const Navbar = () => {
  const { setMobileOpen, openPanel } = useSidePanelActions();
  const panelOpen = usePanelOpen();
  const isMobile = useIsMobile();

  const showReopenButton = !isMobile && !panelOpen;

  return (
    <nav className="sticky top-0 flex h-(--header-height) w-auto flex-grow items-center border-b bg-background/40 px-4">
      {isMobile && (
        <Button
          onClick={() => setMobileOpen(true)}
          variant="ghost"
          size="icon"
          className="mr-4"
          aria-label="Open menu"
        >
          <Menu className="size-5" />
        </Button>
      )}
      <AnimatePresence>
        {showReopenButton && (
          <motion.div
            initial={{ opacity: 0, width: 0 }}
            animate={{ opacity: 1, width: 'auto' }}
            exit={{ opacity: 0, width: 0 }}
            transition={{ duration: 0.15, delay: 0.1 }}
          >
            <Button onClick={openPanel} variant="ghost" size="icon" aria-label="Open sidebar">
              <PanelLeftOpen className="size-4" />
            </Button>
          </motion.div>
        )}
      </AnimatePresence>
      <div className="flex w-auto grow items-center justify-end space-x-2">
        <ConnectionStatus />
        <ThemeToggle />
        <NavbarUserProfile />
      </div>
    </nav>
  );
};

export const NavbarUserProfile: FC = () => {
  const { isLoadingAuth, isLoading, data: user } = useProfile();
  if (isLoadingAuth || isLoading) return <Skeleton className="size-9 rounded-sm" />;
  if (!user)
    return (
      <div className="flex">
        <Button variant={'outline'} asChild>
          <Link href="/auth/login">Login</Link>
        </Button>
        <Button className="ml-2" asChild>
          <Link href="/auth/register">Get Started</Link>
        </Button>
      </div>
    );
  return <UserProfileDropdown user={user} />;
};
```

- [ ] **Step 2: Commit**

```bash
git add components/ui/nav/navbar.tsx
git commit -m "refactor(side-panel): migrate Navbar from context to Zustand store"
```

---

## Task 10: Restructure Dashboard Layout with ResizablePanelGroup

**Files:**
- Modify: `app/dashboard/layout.tsx`
- Delete: `components/ui/sidebar/icon-rail-context.tsx`
- Delete: `components/ui/sidebar/dashboard-content.tsx`
- Modify: `app/globals.css` (remove `--sub-panel-width`)

- [ ] **Step 1: Rewrite the dashboard layout**

Replace the entire contents of `app/dashboard/layout.tsx`:

```typescript
// app/dashboard/layout.tsx

import { AuthGuard } from '@/components/feature-modules/authentication/components/auth-guard';
import { OnboardWrapper } from '@/components/feature-modules/onboarding/context/onboard-handler';
import { WebSocketSubscriptionManager } from '@/components/feature-modules/workspace/components/websocket-subscription-manager';
import { Navbar } from '@/components/ui/nav/navbar';
import { DashboardShell } from '@/components/ui/sidebar/dashboard-shell';
import { SidePanelProvider } from '@/components/ui/sidebar/context/side-panel-provider';
import type { ChildNodeProps } from '@cranium/utils';
import { FC } from 'react';

const layout: FC<ChildNodeProps> = ({ children }) => {
  return (
    <AuthGuard>
      <OnboardWrapper>
        <SidePanelProvider>
          <WebSocketSubscriptionManager />
          <DashboardShell>
            <header className="relative">
              <Navbar />
            </header>
            <section className="px-12 py-6">{children}</section>
          </DashboardShell>
        </SidePanelProvider>
      </OnboardWrapper>
    </AuthGuard>
  );
};

export default layout;
```

- [ ] **Step 2: Create the DashboardShell component**

This replaces both the old layout `div` and `DashboardContent`, using `ResizablePanelGroup` on desktop and a simpler layout on mobile.

Create `components/ui/sidebar/dashboard-shell.tsx`:

```typescript
// components/ui/sidebar/dashboard-shell.tsx

'use client';

import { useIsMobile } from '@cranium/hooks';
import { type ReactNode, useEffect, useRef } from 'react';
import { type ImperativePanelHandle } from 'react-resizable-panels';
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from '../resizable';
import { IconRail } from './icon-rail';
import { SubPanel } from './sub-panel';
import {
  usePanelOpen,
  useSidePanelActions,
} from './context/side-panel-provider';
import {
  PANEL_DEFAULT_SIZE_PCT,
  PANEL_MAX_SIZE_PCT,
  PANEL_MIN_SIZE_PCT,
} from './types/side-panel.types';
import { Sheet, SheetContent } from '../sheet';
import { useMobileOpen, useSelectedPanel } from './context/side-panel-provider';
import { panelRootRegistry } from './components/panel-root-registry';
import { PanelViewRenderer } from './components/panel-view-renderer';
import { useCurrentView } from './context/side-panel-provider';
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
        {/* Invisible resize handle — reveals subtle border on hover, focus ring for keyboard (per DESIGN.md) */}
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
  const { closePanel } = useSidePanelActions();

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
```

- [ ] **Step 3: Delete the old files**

```bash
git rm components/ui/sidebar/icon-rail-context.tsx
git rm components/ui/sidebar/dashboard-content.tsx
```

- [ ] **Step 4: Remove `--sub-panel-width` from globals.css**

In `app/globals.css`, find and remove the `--sub-panel-width: 30rem;` line from the `:root` block. Keep `--icon-rail-width` and `--header-height`.

- [ ] **Step 5: Verify the build compiles**

Run: `cd /home/jared/dev/worktrees/sidepanel/apps/client && npx next build`
Expected: Build succeeds with no type errors. If there are import errors for the deleted `icon-rail-context.tsx`, check for any remaining imports and update them.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(side-panel): restructure dashboard layout with ResizablePanelGroup and mobile Sheet"
```

---

## Task 11: Run All Tests and Verify

**Files:** None — verification only.

- [ ] **Step 1: Run the full test suite**

Run: `cd /home/jared/dev/worktrees/sidepanel/apps/client && npx jest --no-coverage`
Expected: All tests pass, including the new store tests and registry exhaustiveness test.

- [ ] **Step 2: Run the linter**

Run: `cd /home/jared/dev/worktrees/sidepanel/apps/client && npm run lint`
Expected: No errors.

- [ ] **Step 3: Run the build**

Run: `cd /home/jared/dev/worktrees/sidepanel/apps/client && npm run build`
Expected: Build succeeds.

- [ ] **Step 4: Manual smoke test**

Start the dev server (`npm run dev`) and verify:
1. Icon rail renders and switches panels
2. Ctrl+B toggles panel visibility
3. Panel can be resized by dragging the handle (invisible handle, visible on hover)
4. Panel can be collapsed to zero width (Ctrl+B or drag to min)
5. Selected panel persists across page refresh (selectedPanel in localStorage)
6. Mobile view shows Sheet overlay (resize browser below 768px)
7. Escape key pops stack view, then closes panel at root (layered dismissal)
8. Push/pop transitions show 150ms crossfade (not instant swap)
9. Lazy load failure shows error fallback with retry button (test by breaking an import)

---

## Task 12: Integration Test — Push/Pop Flow

**Files:**
- Create: `components/ui/sidebar/__tests__/side-panel-integration.test.tsx`

- [ ] **Step 1: Write the integration test**

```typescript
// components/ui/sidebar/__tests__/side-panel-integration.test.tsx

import { render, screen, act } from '@testing-library/react';
import { createSidePanelStore, type SidePanelStoreApi } from '../stores/side-panel.store';
import { SidePanelProvider, useSidePanelStore } from '../context/side-panel-provider';
import type { SidePanelView } from '../types/side-panel.types';

// Mock @cranium/hooks
jest.mock('@cranium/hooks', () => ({
  useIsMobile: () => false,
}));

/**
 * Test harness that renders children inside a SidePanelProvider.
 * Exposes store actions via a button for triggering in tests.
 */
function TestHarness({ children }: { children: React.ReactNode }) {
  return <SidePanelProvider>{children}</SidePanelProvider>;
}

function StackDisplay() {
  const currentView = useSidePanelStore((s) => s.currentView);
  const stackDepth = useSidePanelStore((s) => s.stackDepth);
  const pushView = useSidePanelStore((s) => s.pushView);
  const popView = useSidePanelStore((s) => s.popView);

  const testView: SidePanelView = {
    type: 'definition-detail',
    title: 'Test Definition',
    definitionId: 'def-123',
    workspaceId: 'ws-1',
  };

  return (
    <div>
      <span data-testid="depth">{stackDepth}</span>
      <span data-testid="current">{currentView?.title ?? 'none'}</span>
      <button onClick={() => pushView(testView)}>push</button>
      <button onClick={() => popView()}>pop</button>
    </div>
  );
}

describe('SidePanel integration', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('push adds a view, pop removes it', () => {
    render(
      <TestHarness>
        <StackDisplay />
      </TestHarness>,
    );

    expect(screen.getByTestId('depth').textContent).toBe('0');
    expect(screen.getByTestId('current').textContent).toBe('none');

    act(() => {
      screen.getByText('push').click();
    });

    expect(screen.getByTestId('depth').textContent).toBe('1');
    expect(screen.getByTestId('current').textContent).toBe('Test Definition');

    act(() => {
      screen.getByText('pop').click();
    });

    expect(screen.getByTestId('depth').textContent).toBe('0');
    expect(screen.getByTestId('current').textContent).toBe('none');
  });
});
```

- [ ] **Step 2: Run the integration test**

Run: `cd /home/jared/dev/worktrees/sidepanel/apps/client && npx jest components/ui/sidebar/__tests__/side-panel-integration.test.tsx --no-coverage`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add components/ui/sidebar/__tests__/side-panel-integration.test.tsx
git commit -m "test(side-panel): add integration test for push/pop view flow"
```

---

## Summary of Deliverables

| Task | What it produces |
|------|-----------------|
| 1 | Type foundation: `PanelId`, `SidePanelView` union, constants |
| 2 | Zustand store with full test suite (push/pop/replace/toggle/edge cases) |
| 3 | Provider with localStorage persistence, keyboard shortcut, selector hooks |
| 4 | `PanelViewFrame` + `PanelSkeleton` + `PanelErrorFallback` components |
| 5 | View registry with lazy loading, exhaustiveness test, renderer, placeholder views |
| 6 | Single-source panel root registry (replaces duplicate maps) |
| 7 | Rewritten `SubPanel` using store + registries + view stack |
| 8 | Icon rail migrated from context to store |
| 9 | Navbar migrated from context to store |
| 10 | Dashboard layout restructured with `ResizablePanelGroup` + mobile `Sheet` |
| 11 | Full verification (tests, lint, build, smoke test) |
| 12 | Integration test for push/pop flow |

### What's NOT in this plan (future work)

- **Wiring real detail views** — The placeholder views (`definition-detail-view.tsx`, `entity-notes-view.tsx`, `integration-detail-view.tsx`) need to be replaced with real implementations that fetch data and render meaningful content. Each is a separate task.
- **Migrating existing drawers** — The `NoteDrawer` and other drawers can be migrated to use the panel portal in follow-up work, once the infrastructure is proven.
- **URL integration for panel state** — Pushing a detail view does not change the URL. Browser back button navigates main content, not the panel stack. This matches Linear and Notion panel behavior. Add searchParam integration if users request it.
- **DESIGN.md layout section update** — The layout section references `--sub-panel-width: 25rem` which is removed. Should be updated to describe resizable behavior.
- **Panel collapse/expand animation timing** — `react-resizable-panels` default transition may differ from DESIGN.md's 200ms linear spec. Calibrate after implementation.
- **Panel width persistence** — `autoSaveId` removed to avoid dual-persistence with store. Panel width resets to default on page load. Add width to store's localStorage persistence if users want width memory.

### Design decisions (from design review)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Error boundary for lazy views | Yes, with retry button | Matches `EditorErrorBoundary` pattern in `note-drawer.tsx`. Prevents white-screen crashes. |
| Push/pop transitions | 150ms ease-out crossfade | Matches DESIGN.md `short(150-200ms)` timing. Signals navigation depth. |
| Resize handle style | Invisible with hover reveal | Matches DESIGN.md minimal decoration. No `withHandle` grip icon. `transition-colors duration-150`. |
| PanelViewFrame header padding | `px-4` | Matches existing sub-panel header. Creates hierarchy vs `px-3` content. |
| PanelViewFrame title weight | `font-medium` (500) | Matches DESIGN.md UI/Labels spec (was `font-semibold` 600). |
| Escape key behavior | Layered dismissal | Pop stack if views exist, close panel if at root. Matches nested modal conventions. |
| Focus management | Title focus on push | `titleRef.current?.focus()` on mount. Enables keyboard/screen reader navigation. |
| ARIA landmarks | `<aside>` with `aria-label` | Panel content wrapped in `<aside>` for screen reader landmark navigation. |
| Resize handle focus | `focus-visible:ring-1` | Keyboard users can see when handle is focused for arrow-key resize. |

## GSTACK REVIEW REPORT

| Review | Trigger | Why | Runs | Status | Findings |
|--------|---------|-----|------|--------|----------|
| CEO Review | `/plan-ceo-review` | Scope & strategy | 0 | — | — |
| Codex Review | `/codex review` | Independent 2nd opinion | 0 | — | — |
| Eng Review | `/plan-eng-review` | Architecture & tests (required) | 1 | CLEAR (PLAN) | 5 issues, 0 critical gaps |
| Design Review | `/plan-design-review` | UI/UX gaps | 1 | CLEAR (FULL) | score: 5/10 → 8/10, 9 decisions |

- **OUTSIDE VOICE:** Claude subagent found 9 issues. 3 were plan bugs (fixed). 3 were architectural improvements (applied: derived selectors, single-source persistence, imperative panel sync). 1 UX risk deferred (URL integration). 2 were false positives.
- **UNRESOLVED:** 0 blocking. 4 deferred to future work (URL integration, DESIGN.md update, collapse timing, width persistence).
- **VERDICT:** ENG + DESIGN CLEARED — ready to implement
