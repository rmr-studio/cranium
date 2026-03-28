'use client';

import { useEffect, useState } from 'react';

/**
 * Resolves a CSS custom property (e.g. `--foreground`) to a computed rgb() string
 * that WebGL-based libraries like paper-shaders can consume.
 *
 * Re-resolves when the theme class changes on <html>.
 */
export function useCssColor(cssVar: string, fallback = '#000000'): string {
  const [color, setColor] = useState(fallback);

  useEffect(() => {
    const resolve = () => {
      // Create a temporary element so getComputedStyle resolves any color format to rgb()
      const probe = document.createElement('div');
      probe.style.color = `var(${cssVar})`;
      probe.style.display = 'none';
      document.body.appendChild(probe);
      const rgb = getComputedStyle(probe).color;
      document.body.removeChild(probe);

      if (rgb) {
        // Convert rgb(r, g, b) → #rrggbb
        const match = rgb.match(/(\d+)/g);
        if (match && match.length >= 3) {
          const hex =
            '#' +
            match
              .slice(0, 3)
              .map((n) => Number(n).toString(16).padStart(2, '0'))
              .join('');
          setColor(hex);
        }
      }
    };

    resolve();

    // Re-resolve when the theme changes (class mutation on <html>)
    const observer = new MutationObserver(resolve);
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class'],
    });

    return () => observer.disconnect();
  }, [cssVar, fallback]);

  return color;
}
