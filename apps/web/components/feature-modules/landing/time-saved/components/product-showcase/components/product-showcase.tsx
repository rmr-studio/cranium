'use client';

import { useEffect, useRef, useState } from 'react';

import { DesktopShowcase } from '@/components/feature-modules/landing/time-saved/components/product-showcase/components/desktop-showcase';
import { MobileShowcase } from '@/components/feature-modules/landing/time-saved/components/product-showcase/components/mobile-showcase';

/**
 * Computes a zoom factor so the showcase fills the container edge-to-edge.
 * Uses the container width-to-viewport-height ratio to interpolate between
 * narrow (phone, higher zoom) and wide (tablet/small desktop, lower zoom).
 */
function useMobileShowcaseZoom() {
  const containerRef = useRef<HTMLDivElement>(null);
  const [zoom, setZoom] = useState(1.4);
  const rafId = useRef<number>(0);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;

    function update() {
      cancelAnimationFrame(rafId.current);
      rafId.current = requestAnimationFrame(() => {
        const width = el!.clientWidth;
        const vh = window.innerHeight;
        const ratio = width / vh;
        const z = Math.max(1, Math.min(1.6, 1.9 - ratio * 1.3));
        setZoom(z);
      });
    }

    const obs = new ResizeObserver(update);
    obs.observe(el);
    window.addEventListener('resize', update);
    return () => {
      obs.disconnect();
      window.removeEventListener('resize', update);
      cancelAnimationFrame(rafId.current);
    };
  }, []);

  return { containerRef, zoom };
}

export function ProductShowcaseGraphic({ className }: { className?: string }) {
  const { containerRef, zoom } = useMobileShowcaseZoom();

  return (
    <>
      {/* Desktop: wide horizontal overlap layout */}

      <DesktopShowcase />

      {/* Mobile: vertical stacked layout */}
      <div ref={containerRef} className="mt-10 block overflow-hidden px-4 lg:hidden">
        <div className="dark origin-top-left translate-y-12" style={{ scale: zoom }}>
          <MobileShowcase />
        </div>
      </div>
    </>
  );
}
