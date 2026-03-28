'use client';

import { createContext, useContext } from 'react';

/**
 * When true, elements animate immediately on mount instead of waiting
 * for IntersectionObserver. This works around iOS Safari's unreliable
 * IntersectionObserver for SVG elements inside overflow-hidden accordion containers.
 */
export const AnimateOnMountContext = createContext(false);

export function useAnimateOnMount() {
  return useContext(AnimateOnMountContext);
}
