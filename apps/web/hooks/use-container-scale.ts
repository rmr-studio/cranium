import { useIsomorphicLayoutEffect } from '@cranium/hooks';
import { useRef, useState } from 'react';

export function useContainerScale(internalWidth: number) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);
  const rafId = useRef<number>(0);

  // useLayoutEffect runs synchronously after DOM mutation but before paint,
  // so the measured scale is applied in the same frame as the initial mount.
  // Without this, the showcase renders at scale(1) (1300px wide) for one
  // frame and then snaps to its real size when ResizeObserver fires, which
  // is the visible "jump" on hydration.
  useIsomorphicLayoutEffect(() => {
    const el = containerRef.current;
    if (!el) return;

    // Measure once synchronously so the first painted frame already has
    // the correct scale.
    const initial = el.getBoundingClientRect().width;
    if (initial > 0) {
      setScale(Math.min(1, initial / internalWidth));
    }

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
