import type { SidePanelView } from '@/components/ui/sidebar/types/side-panel.types';
import { createSidePanelStore } from '@/components/ui/sidebar/stores/side-panel.store';

const makeView = (type: SidePanelView['type'], suffix = ''): SidePanelView => {
  switch (type) {
    case 'definition-detail':
      return {
        type,
        integration: {
          id: `def-${suffix}`,
          name: `Integration ${suffix}`,
        } as any,
        workspaceId: 'ws-1',
      };
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
      const store = createSidePanelStore({ selectedPanel: 'billing', panelOpen: false });
      const state = store.getState();
      expect(state.selectedPanel).toBe('billing');
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

      store.getState().togglePanel('billing');
      expect(store.getState().viewStack).toEqual([]);
    });
  });

  describe('closePanel / openPanel', () => {
    it('closePanel sets panelOpen and mobileOpen to false', () => {
      const store = createSidePanelStore();
      store.getState().setMobileOpen(true);
      store.getState().closePanel();
      expect(store.getState().panelOpen).toBe(false);
      expect(store.getState().mobileOpen).toBe(false);
    });

    it('openPanel sets panelOpen and mobileOpen to true', () => {
      const store = createSidePanelStore({ panelOpen: false });
      store.getState().openPanel();
      expect(store.getState().panelOpen).toBe(true);
      expect(store.getState().mobileOpen).toBe(true);
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

    it('opens the panel and mobile sheet if they were closed', () => {
      const store = createSidePanelStore({ panelOpen: false });
      store.getState().pushView(makeView('definition-detail', '1'));
      expect(store.getState().panelOpen).toBe(true);
      expect(store.getState().mobileOpen).toBe(true);
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

    it('opens the panel and mobile sheet if they were closed', () => {
      const store = createSidePanelStore({ panelOpen: false });
      store.getState().replaceView(makeView('definition-detail', '1'));
      expect(store.getState().panelOpen).toBe(true);
      expect(store.getState().mobileOpen).toBe(true);
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
