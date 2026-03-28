import { useCallback, useEffect, useRef, useState } from 'react';

const AUTO_ADVANCE_MS = 200_000;
const INTERACTION_PAUSE_MS = 3_000;

export function useAutoAdvance(itemCount: number) {
  const [activeIndex, setActiveIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const pauseTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // eslint-disable-next-line react-hooks/purity
  const startRef = useRef(Date.now());

  useEffect(() => {
    if (paused) return;

    startRef.current = Date.now();

    timerRef.current = setInterval(() => {
      if (Date.now() - startRef.current >= AUTO_ADVANCE_MS) {
        setActiveIndex((prev) => (prev + 1) % itemCount);
        startRef.current = Date.now();
      }
    }, 200);

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [activeIndex, paused, itemCount]);

  const select = useCallback((index: number) => {
    setActiveIndex(index);
    setPaused(true);
    if (pauseTimeoutRef.current) clearTimeout(pauseTimeoutRef.current);
    pauseTimeoutRef.current = setTimeout(() => setPaused(false), INTERACTION_PAUSE_MS);
  }, []);

  return { activeIndex, select };
}
