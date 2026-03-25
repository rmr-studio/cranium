'use client';

import { useContainerScale } from '@/hooks/use-container-scale';
import { useIsMobile } from '@riven/hooks';
import { cn } from '@/lib/utils';
import { motion } from 'motion/react';
import type { ReactNode } from 'react';

const MOBILE_WIDTH = 800;

interface ScaledShowcaseProps {
  desktopWidth?: number;
  desktopHeight: number;
  /** Height used when rendering at mobile width. Defaults to desktopHeight * (MOBILE_WIDTH / desktopWidth). */
  mobileHeight?: number;
  children: ReactNode;
  className?: string;
  /** Whether to wrap children in a motion.div with fade-in animation. Defaults to true. */
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
    <div ref={containerRef} className={cn('relative w-full', className)}>
      <div
        className="origin-top-left"
        style={{
          width,
          transform: `scale(${scale})`,
          height: height * scale,
        }}
      >
        <div className="relative" style={{ height }}>
          {animate ? (
            <motion.div
              initial={{ opacity: 0, y: 12 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.5 }}
              className="absolute inset-x-0 top-0"
            >
              {children}
            </motion.div>
          ) : (
            children
          )}
        </div>
      </div>
    </div>
  );
}
