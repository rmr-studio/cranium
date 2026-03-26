import { debounce, debounceLeading } from './debounce.util';

describe('debounce', () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => jest.useRealTimers());

  it('flush() executes pending function immediately', () => {
    const fn = jest.fn();
    const debounced = debounce(fn, 1000);
    debounced('arg1');
    expect(fn).not.toHaveBeenCalled();
    debounced.flush();
    expect(fn).toHaveBeenCalledWith('arg1');
  });

  it('flush() clears the timer', () => {
    const fn = jest.fn();
    const debounced = debounce(fn, 1000);
    debounced('arg1');
    debounced.flush();
    jest.advanceTimersByTime(1000);
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it('flush() is a no-op when no pending call', () => {
    const fn = jest.fn();
    const debounced = debounce(fn, 1000);
    debounced.flush();
    expect(fn).not.toHaveBeenCalled();
  });

  it('flush() followed by debounced call works correctly', () => {
    const fn = jest.fn();
    const debounced = debounce(fn, 1000);
    debounced('first');
    debounced.flush();
    debounced('second');
    jest.advanceTimersByTime(1000);
    expect(fn).toHaveBeenCalledTimes(2);
    expect(fn).toHaveBeenNthCalledWith(1, 'first');
    expect(fn).toHaveBeenNthCalledWith(2, 'second');
  });
});

describe('debounceLeading', () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => jest.useRealTimers());

  it('flush() executes pending function immediately', () => {
    const fn = jest.fn();
    const debounced = debounceLeading(fn, 1000);
    // First call executes immediately (leading edge)
    debounced('first');
    expect(fn).toHaveBeenCalledTimes(1);
    // Second call within window is debounced
    debounced('second');
    expect(fn).toHaveBeenCalledTimes(1);
    debounced.flush();
    expect(fn).toHaveBeenCalledTimes(2);
    expect(fn).toHaveBeenNthCalledWith(2, 'second');
  });

  it('flush() is a no-op when no pending call', () => {
    const fn = jest.fn();
    const debounced = debounceLeading(fn, 1000);
    debounced.flush();
    expect(fn).not.toHaveBeenCalled();
  });
});
