'use client';

import { useIsMobile } from '@cranium/hooks';
import { useEffect, useRef, useState } from 'react';

interface UseShaderVisibilityResult {
  /** Ref to attach to the container element for intersection observation */
  containerRef: React.RefObject<HTMLDivElement | null>;
  /** Ref to attach to the shader wrapper for canvas-ready detection */
  shaderWrapperRef: React.RefObject<HTMLDivElement | null>;
  /** Whether the animated shader should be shown (not mobile, not reduced motion) */
  showAnimated: boolean;
  /** Whether the container has scrolled into view */
  isVisible: boolean;
  /** Whether the WebGL canvas has painted and is ready to display */
  shaderReady: boolean;
}

export function useShaderVisibility(): UseShaderVisibilityResult {
  const containerRef = useRef<HTMLDivElement>(null);
  const shaderWrapperRef = useRef<HTMLDivElement>(null);
  const [isVisible, setIsVisible] = useState(false);
  const [shaderReady, setShaderReady] = useState(false);
  const [prefersReduced, setPrefersReduced] = useState(false);
  const isMobile = useIsMobile();

  const showAnimated = !prefersReduced && isMobile === false;

  // Detect prefers-reduced-motion
  useEffect(() => {
    const mq = window.matchMedia('(prefers-reduced-motion: reduce)');
    setPrefersReduced(mq.matches);
    const handler = (e: MediaQueryListEvent) => setPrefersReduced(e.matches);
    mq.addEventListener('change', handler);
    return () => mq.removeEventListener('change', handler);
  }, []);

  // Only mount the shader when the container scrolls into view
  useEffect(() => {
    const el = containerRef.current;
    if (!el || !showAnimated) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsVisible(true);
          observer.disconnect();
        }
      },
      { rootMargin: '200px' },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [showAnimated]);

  // Wait for the WebGL canvas to actually paint before revealing
  useEffect(() => {
    const wrapper = shaderWrapperRef.current;
    if (!wrapper || !showAnimated || !isVisible) return;

    let cancelled = false;
    let frames = 0;
    const MAX_FRAMES = 300; // ~5s at 60fps

    const check = () => {
      if (cancelled || frames++ > MAX_FRAMES) return;
      const canvas = wrapper.querySelector('canvas');
      if (canvas && canvas.width > 0 && canvas.height > 0) {
        requestAnimationFrame(() => {
          requestAnimationFrame(() => {
            if (!cancelled) setShaderReady(true);
          });
        });
      } else {
        requestAnimationFrame(check);
      }
    };

    requestAnimationFrame(check);
    return () => {
      cancelled = true;
    };
  }, [showAnimated, isVisible]);

  return { containerRef, shaderWrapperRef, showAnimated, isVisible, shaderReady };
}
