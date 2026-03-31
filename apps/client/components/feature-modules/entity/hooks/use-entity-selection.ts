import { useCallback, useMemo, useState } from 'react';

type SelectionMode = 'manual' | 'all';

interface EntitySelectionState {
  mode: SelectionMode;
  /** Explicitly selected IDs (manual mode) */
  includedIds: Set<string>;
  /** Deselected IDs after select-all (all mode) */
  excludedIds: Set<string>;
  /** Total entities matching the current query (from first page response) */
  totalCount: number | undefined;
}

const INITIAL_STATE: EntitySelectionState = {
  mode: 'manual',
  includedIds: new Set(),
  excludedIds: new Set(),
  totalCount: undefined,
};

export function useEntitySelection() {
  const [state, setState] = useState<EntitySelectionState>(INITIAL_STATE);

  const selectAll = useCallback((totalCount: number) => {
    setState({
      mode: 'all',
      includedIds: new Set(),
      excludedIds: new Set(),
      totalCount,
    });
  }, []);

  const deselectAll = useCallback(() => {
    setState(INITIAL_STATE);
  }, []);

  const toggleId = useCallback((id: string) => {
    setState((prev) => {
      if (prev.mode === 'manual') {
        const next = new Set(prev.includedIds);
        if (next.has(id)) {
          next.delete(id);
        } else {
          next.add(id);
        }
        return { ...prev, includedIds: next };
      }

      // all mode — toggle exclusion
      const next = new Set(prev.excludedIds);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }

      // Auto-revert: if every item is excluded, reset to manual
      if (prev.totalCount !== undefined && next.size >= prev.totalCount) {
        return { ...INITIAL_STATE };
      }

      return { ...prev, excludedIds: next };
    });
  }, []);

  const updateTotalCount = useCallback((totalCount: number) => {
    setState((prev) => {
      if (prev.mode !== 'all') return prev;

      // Auto-revert if new count makes everything excluded
      if (prev.excludedIds.size >= totalCount) {
        return { ...INITIAL_STATE };
      }

      return { ...prev, totalCount };
    });
  }, []);

  const isSelected = useCallback(
    (id: string): boolean => {
      if (state.mode === 'manual') {
        return state.includedIds.has(id);
      }
      return !state.excludedIds.has(id);
    },
    [state.mode, state.includedIds, state.excludedIds],
  );

  const selectedCount = useMemo(() => {
    if (state.mode === 'manual') {
      return state.includedIds.size;
    }
    return (state.totalCount ?? 0) - state.excludedIds.size;
  }, [state.mode, state.includedIds.size, state.totalCount, state.excludedIds.size]);

  const hasSelection = selectedCount > 0;

  return {
    ...state,
    selectAll,
    deselectAll,
    toggleId,
    updateTotalCount,
    isSelected,
    selectedCount,
    hasSelection,
  };
}

export type EntitySelection = ReturnType<typeof useEntitySelection>;
