'use client';

import { cn } from '@/lib/utils';
import { useEffect, useRef, useState } from 'react';
import type { DitherDirection, DitherPattern } from './dither';

const CELL = 16;
const BAYER_4 = [
  [0, 8, 2, 10],
  [12, 4, 14, 6],
  [3, 11, 1, 9],
  [15, 7, 13, 5],
];

interface DitherTransitionProps {
  /** When true, animate progress 0→1 (cells appear). When false, animate 1→0 (cells erode away). */
  active: boolean;
  /** Animation duration in ms. */
  duration?: number;
  className?: string;
  /**
   * Color to paint each filled cell. Accepts any canvas-valid color string. If the value starts with
   * `var(`, it is resolved against the parent element's computed style at draw time so design tokens work.
   */
  fillColor?: string;
  direction?: DitherDirection;
  pattern?: DitherPattern;
  /** Phase shifts the noise pattern so stacked dithers don't align. Defaults to a random seed per mount. */
  seed?: number;
  erosionWeight?: number;
  /** Fires once an exit animation reaches progress=0 — useful for unmount-after-exit. */
  onExited?: () => void;
}

const hash2 = (x: number, y: number, seed: number) => {
  let h = (x * 374761393 + y * 668265263 + seed * 2147483647) | 0;
  h = (h ^ (h >>> 13)) * 1274126177;
  h = h ^ (h >>> 16);
  return ((h >>> 0) % 1024) / 1024;
};

// ease-out-quart — natural deceleration, no bounce.
const easeOut = (t: number) => 1 - Math.pow(1 - t, 4);

const resolveColor = (raw: string, scope: HTMLElement | null): string => {
  if (!raw.startsWith('var(') || !scope) return raw;
  const match = raw.match(/var\(\s*(--[\w-]+)\s*(?:,\s*([^)]+))?\)/);
  if (!match) return raw;
  const value = getComputedStyle(scope).getPropertyValue(match[1]).trim();
  return value || match[2]?.trim() || raw;
};

export function DitherTransition({
  active,
  duration = 500,
  className,
  fillColor = 'oklch(0.95 0.007 81)',
  direction = 'bottom-up',
  pattern = 'bayer',
  seed: seedProp,
  erosionWeight = 0.78,
  onExited,
}: DitherTransitionProps) {
  const [autoSeed] = useState(() => Math.floor(Math.random() * 65536));
  const seed = seedProp ?? autoSeed;
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const thresholdMapRef = useRef<Float32Array | null>(null);
  const dimsRef = useRef({ cols: 0, rows: 0, w: 0, h: 0, dpr: 1 });
  const progressRef = useRef(0);
  const rafRef = useRef<number | null>(null);
  const drawRef = useRef<((p: number) => void) | null>(null);
  const onExitedRef = useRef(onExited);

  useEffect(() => {
    onExitedRef.current = onExited;
  }, [onExited]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let resolvedFill = fillColor;

    const buildMap = () => {
      const parent = canvas.parentElement;
      if (!parent) return;
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      const rect = parent.getBoundingClientRect();
      const w = Math.max(1, Math.ceil(rect.width));
      const h = Math.max(1, Math.ceil(rect.height));
      canvas.width = w * dpr;
      canvas.height = h * dpr;
      canvas.style.width = `${w}px`;
      canvas.style.height = `${h}px`;
      const cols = Math.ceil(w / CELL);
      const rows = Math.ceil(h / CELL);
      const map = new Float32Array(cols * rows);

      const cxCenter = (cols - 1) / 2;
      const cyCenter = (rows - 1) / 2;
      const maxRadial = Math.hypot(cxCenter, cyCenter) || 1;
      const noiseW = 1 - erosionWeight;

      for (let y = 0; y < rows; y++) {
        for (let x = 0; x < cols; x++) {
          let erosion: number;
          switch (direction) {
            case 'top-down':
              erosion = rows > 1 ? y / (rows - 1) : 0;
              break;
            case 'left-right':
              erosion = cols > 1 ? 1 - x / (cols - 1) : 0;
              break;
            case 'right-left':
              erosion = cols > 1 ? x / (cols - 1) : 0;
              break;
            case 'radial':
              erosion = 1 - Math.hypot(x - cxCenter, y - cyCenter) / maxRadial;
              break;
            case 'bottom-right': {
              const nx = cols > 1 ? x / (cols - 1) : 0;
              const ny = rows > 1 ? y / (rows - 1) : 0;
              erosion = 1 - (nx + ny) / 2;
              break;
            }
            case 'bottom-left': {
              const nx = cols > 1 ? x / (cols - 1) : 0;
              const ny = rows > 1 ? y / (rows - 1) : 0;
              erosion = (nx + (1 - ny)) / 2;
              break;
            }
            case 'bottom-up':
            default:
              erosion = rows > 1 ? 1 - y / (rows - 1) : 0;
          }

          const sx = x + seed;
          const sy = y + seed * 3;
          let noise: number;
          switch (pattern) {
            case 'bayer-inverted':
              noise = 1 - BAYER_4[sy % 4][sx % 4] / 16;
              break;
            case 'checker':
              noise = (sx + sy) % 2 === 0 ? 0.25 : 0.75;
              break;
            case 'noise':
              noise = hash2(x, y, seed);
              break;
            case 'bayer':
            default:
              noise = BAYER_4[sy % 4][sx % 4] / 16;
          }

          map[y * cols + x] = erosion * erosionWeight + noise * noiseW;
        }
      }

      thresholdMapRef.current = map;
      dimsRef.current = { cols, rows, w, h, dpr };
      resolvedFill = resolveColor(fillColor, canvas.parentElement);
      draw(progressRef.current);
    };

    const draw = (progress: number) => {
      const map = thresholdMapRef.current;
      if (!map) return;
      const { cols, rows, w, h, dpr } = dimsRef.current;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, w, h);
      ctx.fillStyle = resolvedFill;
      for (let y = 0; y < rows; y++) {
        for (let x = 0; x < cols; x++) {
          const t = map[y * cols + x];
          if (progress <= t) continue;
          ctx.fillRect(x * CELL, y * CELL, CELL + 1, CELL + 1);
        }
      }
    };

    drawRef.current = draw;
    buildMap();

    const ro = new ResizeObserver(() => buildMap());
    if (canvas.parentElement) ro.observe(canvas.parentElement);

    return () => {
      ro.disconnect();
      drawRef.current = null;
    };
  }, [direction, pattern, seed, erosionWeight, fillColor]);

  useEffect(() => {
    const draw = drawRef.current;
    if (!draw) return;
    if (rafRef.current) cancelAnimationFrame(rafRef.current);

    const from = progressRef.current;
    const to = active ? 1 : 0;
    // No-op if already at the target. Skip the onExited callback when there was nothing to exit
    // from — otherwise mounting with active=false would fire it immediately.
    if (from === to) return;

    const start = performance.now();
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / duration);
      const p = from + (to - from) * easeOut(t);
      progressRef.current = p;
      draw(p);
      if (t < 1) {
        rafRef.current = requestAnimationFrame(tick);
      } else {
        rafRef.current = null;
        if (!active) onExitedRef.current?.();
      }
    };
    rafRef.current = requestAnimationFrame(tick);

    return () => {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
      rafRef.current = null;
    };
  }, [active, duration]);

  return (
    <canvas
      ref={canvasRef}
      aria-hidden
      className={cn('pointer-events-none absolute inset-0', className)}
    />
  );
}
