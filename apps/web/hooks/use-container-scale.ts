import { useEffect, useRef, useState } from 'react';

export function useContainerScale(internalWidth: number) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);
  const rafId = useRef<number>(0);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const obs = new ResizeObserver(([entry]) => {
      cancelAnimationFrame(rafId.current);
      rafId.current = requestAnimationFrame(() => {
        setScale(Math.min(1, entry.contentRect.width / internalWidth));
      });
    });
    obs.observe(el);
    return () => {
      obs.disconnect();
      cancelAnimationFrame(rafId.current);
    };
  }, [internalWidth]);

  return { containerRef, scale };
}
