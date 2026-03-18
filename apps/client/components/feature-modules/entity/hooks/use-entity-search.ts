import { useCallback, useState } from 'react';

/**
 * Encapsulates search term state for server-side search.
 *
 * Debouncing is now owned by the DataTableSearchInput component.
 * This hook simply keeps `searchTerm` (for the input) and `debouncedSearch`
 * (for query keys) in sync. `clearSearch` resets both immediately.
 */
export function useEntitySearch() {
  const [searchTerm, setSearchTermState] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');

  const setSearchTerm = useCallback((value: string) => {
    setSearchTermState(value);
    setDebouncedSearch(value);
  }, []);

  const clearSearch = useCallback(() => {
    setSearchTermState('');
    setDebouncedSearch('');
  }, []);

  return { searchTerm, setSearchTerm, debouncedSearch, clearSearch };
}
