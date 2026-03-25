'use client';

import type { TargetAndTransition } from 'motion/react';
import { createContext, useContext } from 'react';

/**
 * When true, motion elements animate immediately on mount instead of waiting
 * for IntersectionObserver (whileInView). This works around iOS Safari's
 * unreliable IntersectionObserver for SVG elements inside overflow-hidden
 * accordion containers.
 */
export const AnimateOnMountContext = createContext(false);

export function useAnimateOnMount() {
  return useContext(AnimateOnMountContext);
}

/** Returns `whileInView` + `viewport` props, or `animate` props when animate-on-mount is enabled. */
export function inViewProps(onMount: boolean, target: TargetAndTransition) {
  return onMount
    ? { animate: target }
    : { whileInView: target, viewport: { once: true } as const };
}
