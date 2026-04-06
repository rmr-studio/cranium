import { renderHook, act } from '@testing-library/react';
import { useEntitySelection } from './use-entity-selection';

describe('useEntitySelection', () => {
  describe('initial state', () => {
    it('starts in manual mode with empty selections', () => {
      const { result } = renderHook(() => useEntitySelection(undefined));

      expect(result.current.mode).toBe('manual');
      expect(result.current.includedIds.size).toBe(0);
      expect(result.current.excludedIds.size).toBe(0);
      expect(result.current.totalCount).toBeUndefined();
      expect(result.current.selectedCount).toBe(0);
    });

    it('syncs totalCount from the query parameter', () => {
      const { result } = renderHook(() => useEntitySelection(150));

      expect(result.current.totalCount).toBe(150);
      expect(result.current.mode).toBe('manual');
    });
  });

  describe('manual mode', () => {
    it('tracks individually selected IDs', () => {
      const { result } = renderHook(() => useEntitySelection(undefined));

      act(() => result.current.toggleId('entity-1'));
      act(() => result.current.toggleId('entity-2'));

      expect(result.current.mode).toBe('manual');
      expect(result.current.includedIds).toEqual(new Set(['entity-1', 'entity-2']));
      expect(result.current.selectedCount).toBe(2);
    });

    it('deselects an already-selected ID', () => {
      const { result } = renderHook(() => useEntitySelection(undefined));

      act(() => result.current.toggleId('entity-1'));
      act(() => result.current.toggleId('entity-1'));

      expect(result.current.includedIds.size).toBe(0);
      expect(result.current.selectedCount).toBe(0);
    });

    it('hasSelection is false when nothing selected', () => {
      const { result } = renderHook(() => useEntitySelection(undefined));

      expect(result.current.hasSelection).toBe(false);
    });

    it('hasSelection is true when IDs are selected', () => {
      const { result } = renderHook(() => useEntitySelection(undefined));

      act(() => result.current.toggleId('entity-1'));

      expect(result.current.hasSelection).toBe(true);
    });
  });

  describe('select all mode', () => {
    it('transitions to all mode using synced totalCount', () => {
      const { result } = renderHook(() => useEntitySelection(150));

      act(() => result.current.selectAll());

      expect(result.current.mode).toBe('all');
      expect(result.current.totalCount).toBe(150);
      expect(result.current.selectedCount).toBe(150);
      expect(result.current.includedIds.size).toBe(0);
      expect(result.current.excludedIds.size).toBe(0);
    });

    it('tracks exclusions when toggling in all mode', () => {
      const { result } = renderHook(() => useEntitySelection(100));

      act(() => result.current.selectAll());
      act(() => result.current.toggleId('entity-5'));

      expect(result.current.mode).toBe('all');
      expect(result.current.excludedIds).toEqual(new Set(['entity-5']));
      expect(result.current.selectedCount).toBe(99);
    });

    it('re-includes an excluded ID by toggling again', () => {
      const { result } = renderHook(() => useEntitySelection(100));

      act(() => result.current.selectAll());
      act(() => result.current.toggleId('entity-5'));
      act(() => result.current.toggleId('entity-5'));

      expect(result.current.excludedIds.size).toBe(0);
      expect(result.current.selectedCount).toBe(100);
    });

    it('auto-reverts to manual mode when all items are excluded', () => {
      const { result } = renderHook(() => useEntitySelection(2));

      act(() => result.current.selectAll());
      act(() => result.current.toggleId('entity-1'));
      act(() => result.current.toggleId('entity-2'));

      expect(result.current.mode).toBe('manual');
      expect(result.current.selectedCount).toBe(0);
      expect(result.current.excludedIds.size).toBe(0);
      // totalCount preserved from query sync
      expect(result.current.totalCount).toBe(2);
    });
  });

  describe('deselectAll', () => {
    it('resets selection state but preserves totalCount', () => {
      const { result } = renderHook(() => useEntitySelection(100));

      act(() => result.current.toggleId('entity-1'));
      act(() => result.current.deselectAll());

      expect(result.current.mode).toBe('manual');
      expect(result.current.includedIds.size).toBe(0);
      expect(result.current.selectedCount).toBe(0);
      expect(result.current.totalCount).toBe(100);
    });

    it('resets from all mode but preserves totalCount', () => {
      const { result } = renderHook(() => useEntitySelection(100));

      act(() => result.current.selectAll());
      act(() => result.current.deselectAll());

      expect(result.current.mode).toBe('manual');
      expect(result.current.excludedIds.size).toBe(0);
      expect(result.current.totalCount).toBe(100);
      expect(result.current.selectedCount).toBe(0);
    });
  });

  describe('totalCount sync from query', () => {
    it('updates totalCount when query parameter changes', () => {
      const { result, rerender } = renderHook(
        ({ count }) => useEntitySelection(count),
        { initialProps: { count: 100 as number | undefined } },
      );

      act(() => result.current.selectAll());
      expect(result.current.selectedCount).toBe(100);

      rerender({ count: 95 });

      expect(result.current.totalCount).toBe(95);
      expect(result.current.selectedCount).toBe(95);
    });

    it('auto-reverts if new totalCount makes all items excluded', () => {
      const { result, rerender } = renderHook(
        ({ count }) => useEntitySelection(count),
        { initialProps: { count: 5 as number | undefined } },
      );

      act(() => result.current.selectAll());
      act(() => result.current.toggleId('a'));
      act(() => result.current.toggleId('b'));
      act(() => result.current.toggleId('c'));
      // 3 excluded, totalCount=5, selectedCount=2 — still in all mode
      expect(result.current.mode).toBe('all');

      // Now totalCount drops to 3, matching excludedIds.size
      rerender({ count: 3 });

      expect(result.current.mode).toBe('manual');
      expect(result.current.selectedCount).toBe(0);
    });

    it('ignores undefined totalCount', () => {
      const { result, rerender } = renderHook(
        ({ count }) => useEntitySelection(count),
        { initialProps: { count: 100 as number | undefined } },
      );

      expect(result.current.totalCount).toBe(100);

      rerender({ count: undefined });

      // Should retain the last known count
      expect(result.current.totalCount).toBe(100);
    });
  });

  describe('isSelected', () => {
    it('returns true for included IDs in manual mode', () => {
      const { result } = renderHook(() => useEntitySelection(undefined));

      act(() => result.current.toggleId('entity-1'));

      expect(result.current.isSelected('entity-1')).toBe(true);
      expect(result.current.isSelected('entity-2')).toBe(false);
    });

    it('returns true for non-excluded IDs in all mode', () => {
      const { result } = renderHook(() => useEntitySelection(100));

      act(() => result.current.selectAll());
      act(() => result.current.toggleId('entity-5'));

      expect(result.current.isSelected('entity-1')).toBe(true);
      expect(result.current.isSelected('entity-5')).toBe(false);
    });
  });
});
