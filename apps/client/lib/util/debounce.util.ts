/**
 * Debounced function with cancel capability
 */
export type DebouncedFunction<T extends (...args: any[]) => any> =
    ((...args: Parameters<T>) => void) & { cancel: () => void; flush: () => void };

/**
 * Creates a debounced function that delays invoking func until after wait milliseconds
 * have elapsed since the last time the debounced function was invoked.
 *
 * @param func - The function to debounce
 * @param wait - The number of milliseconds to delay
 * @returns A debounced version of the function with a cancel method
 *
 * @example
 * const debouncedSearch = debounce((query: string) => {
 *   console.log('Searching for:', query);
 * }, 300);
 *
 * debouncedSearch('hello'); // Will only execute after 300ms of no calls
 *
 * // Cleanup on unmount
 * useEffect(() => {
 *   return () => debouncedSearch.cancel();
 * }, []);
 */
export function debounce<T extends (...args: any[]) => any>(
    func: T,
    wait: number
): DebouncedFunction<T> {
    let timeoutId: ReturnType<typeof setTimeout> | null = null;
    let pendingArgs: Parameters<T> | null = null;

    const debounced = function (...args: Parameters<T>) {
        if (timeoutId !== null) {
            clearTimeout(timeoutId);
        }

        pendingArgs = args;

        timeoutId = setTimeout(() => {
            func(...args);
            timeoutId = null;
            pendingArgs = null;
        }, wait);
    };

    const cancel = () => {
        if (timeoutId !== null) {
            clearTimeout(timeoutId);
            timeoutId = null;
        }
        pendingArgs = null;
    };

    const flush = () => {
        if (pendingArgs !== null) {
            if (timeoutId !== null) {
                clearTimeout(timeoutId);
                timeoutId = null;
            }
            const args = pendingArgs;
            pendingArgs = null;
            func(...args);
        }
    };

    return Object.assign(debounced, { cancel, flush });
}

/**
 * Creates a hybrid debounced function that executes immediately if enough time
 * has elapsed since the last execution, otherwise debounces the call.
 * This combines leading-edge (immediate execution) with trailing-edge (delayed execution).
 *
 * @param func - The function to debounce
 * @param wait - The number of milliseconds to delay
 * @returns A debounced version of the function that executes immediately on first call with a cancel method
 *
 * @example
 * const debouncedSave = debounceLeading((data: any) => {
 *   console.log('Saving:', data);
 * }, 500);
 *
 * debouncedSave(data); // Executes immediately
 * debouncedSave(data); // Debounced (waits 500ms)
 *
 * // Cleanup on unmount
 * useEffect(() => {
 *   return () => debouncedSave.cancel();
 * }, []);
 */
export function debounceLeading<T extends (...args: any[]) => any>(
    func: T,
    wait: number
): DebouncedFunction<T> {
    let timeoutId: ReturnType<typeof setTimeout> | null = null;
    let lastCallTime: number = 0;
    let pendingArgs: Parameters<T> | null = null;

    const debounced = function (...args: Parameters<T>) {
        const now = Date.now();
        const timeSinceLastCall = now - lastCallTime;

        if (timeoutId !== null) {
            clearTimeout(timeoutId);
        }

        // Execute immediately if enough time has passed
        if (timeSinceLastCall >= wait) {
            func(...args);
            lastCallTime = now;
            pendingArgs = null;
        } else {
            // Otherwise, debounce
            pendingArgs = args;
            timeoutId = setTimeout(() => {
                func(...args);
                lastCallTime = Date.now();
                timeoutId = null;
                pendingArgs = null;
            }, wait);
        }
    };

    const cancel = () => {
        if (timeoutId !== null) {
            clearTimeout(timeoutId);
            timeoutId = null;
        }
        pendingArgs = null;
    };

    const flush = () => {
        if (pendingArgs !== null) {
            if (timeoutId !== null) {
                clearTimeout(timeoutId);
                timeoutId = null;
            }
            const args = pendingArgs;
            pendingArgs = null;
            func(...args);
            lastCallTime = Date.now();
        }
    };

    return Object.assign(debounced, { cancel, flush });
}
