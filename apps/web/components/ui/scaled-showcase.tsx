'use client';

import { useContainerScale } from '@/hooks/use-container-scale';
import { useIsMobile } from '@cranium/hooks';
import type { ReactNode } from 'react';

const MOBILE_WIDTH = 800;

interface ScaledShowcaseProps {
  desktopWidth?: number;
  desktopHeight: number;
  /** Height used when rendering at mobile width. Defaults to desktopHeight * (MOBILE_WIDTH / desktopWidth). */
  mobileHeight?: number;
  children: ReactNode;
  className?: string;
  /** Whether to wrap children in a fade-in animation. Defaults to true. */
  animate?: boolean;
}

export function ScaledShowcase({
  desktopWidth = 1920,
  desktopHeight,
  mobileHeight,
  children,
  className,
  animate = true,
}: ScaledShowcaseProps) {
  const isMobile = useIsMobile();
  const width = isMobile ? MOBILE_WIDTH : desktopWidth;
  const height = isMobile
    ? (mobileHeight ?? Math.round(desktopHeight * (MOBILE_WIDTH / desktopWidth)))
    : desktopHeight;
  const { containerRef, scale } = useContainerScale(width);

  return (
    <div ref={containerRef} className={`relative w-full ${className ?? ''}`}>
      <div
        className="origin-top-left"
        style={{
          width,
          transform: `scale(${scale})`,
          height: height * scale,
        }}
      >
        <div className="relative" style={{ height }}>
          {animate ? <div className="absolute inset-x-0 top-0">{children}</div> : children}
        </div>
      </div>
    </div>
  );
}
