'use client';

import { cn } from '@cranium/utils';

const STROKE_CLASS = 'text-foreground/25';

export function AuthFrame() {
  return (
    <div
      className="pointer-events-none absolute inset-0 z-30"
      style={{
        maskImage: 'linear-gradient(to bottom, black 60%, transparent 80%)',
        WebkitMaskImage: 'linear-gradient(to bottom, black 60%, transparent 80%)',
      }}
    >
      {/* Single SVG for border + grid + arcs — avoids overlap artifacts */}
      <svg
        className={cn('absolute', STROKE_CLASS)}
        style={{
          top: 'var(--auth-iy)',
          right: 'var(--auth-ix)',
          bottom: 'var(--auth-iy)',
          left: 'var(--auth-ix)',
        }}
        viewBox="0 0 1000 840"
        preserveAspectRatio="none"
        fill="none"
        aria-hidden="true"
      >
        {/* Main border — sharp corners */}
        <rect
          x="0"
          y="0"
          width="1000"
          height="840"
          stroke="currentColor"
          strokeWidth="1"
          vectorEffect="non-scaling-stroke"
        />

        {/* Vertical grid dividers — 3 columns */}
        <line
          x1="333"
          y1="0"
          x2="333"
          y2="840"
          stroke="currentColor"
          strokeWidth="1"
          vectorEffect="non-scaling-stroke"
          opacity="0.5"
        />
        <line
          x1="667"
          y1="0"
          x2="667"
          y2="840"
          stroke="currentColor"
          strokeWidth="1"
          vectorEffect="non-scaling-stroke"
          opacity="0.5"
        />

        {/* Horizontal grid dividers — header/footer bands */}
        <line
          x1="0"
          y1="72"
          x2="1000"
          y2="72"
          stroke="currentColor"
          strokeWidth="1"
          vectorEffect="non-scaling-stroke"
          opacity="0.5"
        />
        <line
          x1="0"
          y1="768"
          x2="1000"
          y2="768"
          stroke="currentColor"
          strokeWidth="1"
          vectorEffect="non-scaling-stroke"
          opacity="0.5"
        />
      </svg>
    </div>
  );
}
