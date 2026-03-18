import { renderHook, act } from '@testing-library/react';
import { useEntitySearch } from './use-entity-search';

describe('useEntitySearch', () => {
  it('initializes with empty search term and debounced value', () => {
    const { result } = renderHook(() => useEntitySearch());
    expect(result.current.searchTerm).toBe('');
    expect(result.current.debouncedSearch).toBe('');
  });

  it('updates both searchTerm and debouncedSearch synchronously', () => {
    const { result } = renderHook(() => useEntitySearch());

    act(() => {
      result.current.setSearchTerm('hello');
    });

    expect(result.current.searchTerm).toBe('hello');
    expect(result.current.debouncedSearch).toBe('hello');
  });

  it('reflects the latest value after rapid typing', () => {
    const { result } = renderHook(() => useEntitySearch());

    act(() => {
      result.current.setSearchTerm('h');
    });
    act(() => {
      result.current.setSearchTerm('he');
    });
    act(() => {
      result.current.setSearchTerm('hello');
    });

    expect(result.current.searchTerm).toBe('hello');
    expect(result.current.debouncedSearch).toBe('hello');
  });

  it('resets both values when search is cleared', () => {
    const { result } = renderHook(() => useEntitySearch());

    act(() => {
      result.current.setSearchTerm('hello');
    });
    expect(result.current.debouncedSearch).toBe('hello');

    act(() => {
      result.current.clearSearch();
    });

    expect(result.current.searchTerm).toBe('');
    expect(result.current.debouncedSearch).toBe('');
  });
});
