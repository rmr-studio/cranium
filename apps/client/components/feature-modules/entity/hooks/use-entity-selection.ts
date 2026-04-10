import { useCallback, useEffect, useMemo, useState } from 'react';

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

/**
 * Server-aware entity selection state.
 *
 * @param queryTotalCount - Total count from the query's first page response.
 *   Kept in sync automatically — when filters/search change the query resets,
 *   a new page 0 is fetched with `includeCount`, and this value updates.
 */
export function useEntitySelection(queryTotalCount: number | undefined) {
  const [state, setState] = useState<EntitySelectionState>(INITIAL_STATE);

  // Sync totalCount from the query into selection state
  useEffect(() => {
    if (queryTotalCount === undefined) return;

    setState((prev) => {
      if (prev.totalCount === queryTotalCount) return prev;

      // In "all" mode, auto-revert if everything is now excluded
      if (prev.mode === 'all' && prev.excludedIds.size >= queryTotalCount) {
        return { ...INITIAL_STATE };
      }

      return { ...prev, totalCount: queryTotalCount };
    });
  }, [queryTotalCount]);

  const selectAll = useCallback(() => {
    setState((prev) => ({
      mode: 'all',
      includedIds: new Set(),
      excludedIds: new Set(),
      totalCount: prev.totalCount,
    }));
  }, []);

  const deselectAll = useCallback(() => {
    setState((prev) => ({ ...INITIAL_STATE, totalCount: prev.totalCount }));
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
        return { ...INITIAL_STATE, totalCount: prev.totalCount };
      }

      return { ...prev, excludedIds: next };
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
    isSelected,
    selectedCount,
    hasSelection,
  };
}

export type EntitySelection = ReturnType<typeof useEntitySelection>;
