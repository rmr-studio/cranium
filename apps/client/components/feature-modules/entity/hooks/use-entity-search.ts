import { useCallback, useEffect, useRef, useState } from 'react';

const DEFAULT_DEBOUNCE_MS = 300;

/**
 * Encapsulates search term state with debounce for server-side search.
 *
 * - `searchTerm` updates immediately (for the input field)
 * - `debouncedSearch` updates after the debounce delay (for query keys)
 * - Clearing search resets both immediately (no debounce on clear)
 * - Timer is cancelled on unmount to prevent memory leaks
 */
export function useEntitySearch(debounceMs: number = DEFAULT_DEBOUNCE_MS) {
  const [searchTerm, setSearchTermState] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const cancelTimer = useCallback(() => {
    if (timerRef.current !== null) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  const setSearchTerm = useCallback(
    (value: string) => {
      setSearchTermState(value);
      cancelTimer();

      if (value === '') {
        // Clear immediately — no debounce for clearing
        setDebouncedSearch('');
      } else {
        timerRef.current = setTimeout(() => {
          setDebouncedSearch(value);
          timerRef.current = null;
        }, debounceMs);
      }
    },
    [debounceMs, cancelTimer],
  );

  const clearSearch = useCallback(() => {
    cancelTimer();
    setSearchTermState('');
    setDebouncedSearch('');
  }, [cancelTimer]);

  // Cleanup on unmount
  useEffect(() => {
    return cancelTimer;
  }, [cancelTimer]);

  return { searchTerm, setSearchTerm, debouncedSearch, clearSearch };
}
