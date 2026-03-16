import { renderHook, act } from '@testing-library/react';
import { useEntitySearch } from './use-entity-search';

describe('useEntitySearch', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('initializes with empty search term and debounced value', () => {
    const { result } = renderHook(() => useEntitySearch());
    expect(result.current.searchTerm).toBe('');
    expect(result.current.debouncedSearch).toBe('');
  });

  it('updates searchTerm immediately but debounces debouncedSearch', () => {
    const { result } = renderHook(() => useEntitySearch());

    act(() => {
      result.current.setSearchTerm('hello');
    });

    expect(result.current.searchTerm).toBe('hello');
    expect(result.current.debouncedSearch).toBe('');

    act(() => {
      jest.advanceTimersByTime(300);
    });

    expect(result.current.debouncedSearch).toBe('hello');
  });

  it('only emits last value during rapid typing', () => {
    const { result } = renderHook(() => useEntitySearch());

    act(() => {
      result.current.setSearchTerm('h');
    });
    act(() => {
      jest.advanceTimersByTime(100);
    });
    act(() => {
      result.current.setSearchTerm('he');
    });
    act(() => {
      jest.advanceTimersByTime(100);
    });
    act(() => {
      result.current.setSearchTerm('hello');
    });

    // Not yet debounced
    expect(result.current.debouncedSearch).toBe('');

    act(() => {
      jest.advanceTimersByTime(300);
    });

    // Only final value emitted
    expect(result.current.debouncedSearch).toBe('hello');
  });

  it('resets debouncedSearch immediately when search is cleared', () => {
    const { result } = renderHook(() => useEntitySearch());

    // Set and debounce a value
    act(() => {
      result.current.setSearchTerm('hello');
    });
    act(() => {
      jest.advanceTimersByTime(300);
    });
    expect(result.current.debouncedSearch).toBe('hello');

    // Clear — should reset immediately
    act(() => {
      result.current.clearSearch();
    });

    expect(result.current.searchTerm).toBe('');
    expect(result.current.debouncedSearch).toBe('');
  });

  it('cancels pending debounce on unmount', () => {
    const { result, unmount } = renderHook(() => useEntitySearch());

    act(() => {
      result.current.setSearchTerm('hello');
    });

    unmount();

    // Advancing timers after unmount should not throw
    act(() => {
      jest.advanceTimersByTime(300);
    });
  });

  it('accepts custom debounce delay', () => {
    const { result } = renderHook(() => useEntitySearch(500));

    act(() => {
      result.current.setSearchTerm('hello');
    });

    act(() => {
      jest.advanceTimersByTime(300);
    });
    expect(result.current.debouncedSearch).toBe('');

    act(() => {
      jest.advanceTimersByTime(200);
    });
    expect(result.current.debouncedSearch).toBe('hello');
  });
});
