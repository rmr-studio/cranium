'use client';

import { cn } from '@/lib/utils';
import { useScroll } from 'motion/react';
import { RefObject, useEffect, useRef } from 'react';

const CELL = 16;
const BAYER_4 = [
  [0, 8, 2, 10],
  [12, 4, 14, 6],
  [3, 11, 1, 9],
  [15, 7, 13, 5],
];

export type DitherDirection =
  | 'bottom-up'
  | 'top-down'
  | 'left-right'
  | 'right-left'
  | 'radial'
  | 'bottom-right'
  | 'bottom-left';
export type DitherPattern = 'bayer' | 'bayer-inverted' | 'checker' | 'noise';

interface DitherProps {
  sectionRef: RefObject<HTMLElement | null>;
  cardRef?: RefObject<HTMLElement | null>;
  className?: string;
  fillColor?: string;
  /** Erosion direction — controls which edge dithers first. */
  direction?: DitherDirection;
  /** Pattern source for the ordered noise mixed into the threshold map. */
  pattern?: DitherPattern;
  /** Integer seed shifting bayer/noise phase so stacked layers don't align. */
  seed?: number;
  /** 0–1, how much erosion vs noise contributes (default 0.78). */
  erosionWeight?: number;
  /** -1 –1, how much the dithering starts (default -0.5 - ie. Already progressed). */
  startWeight?: number;
  /** When true, start fully covered and dither away to reveal content as scroll progresses. */
  inverse?: boolean;
}

// Deterministic hash for noise pattern — keeps render stable across frames.
const hash2 = (x: number, y: number, seed: number) => {
  let h = (x * 374761393 + y * 668265263 + seed * 2147483647) | 0;
  h = (h ^ (h >>> 13)) * 1274126177;
  h = h ^ (h >>> 16);
  return ((h >>> 0) % 1024) / 1024;
};

export function Dither({
  sectionRef,
  cardRef,
  className,
  fillColor = 'oklch(0.95 0.007 81)',
  direction = 'bottom-up',
  pattern = 'bayer',
  seed = 0,
  erosionWeight = 0.78,
  startWeight = -0.5,
  inverse = false,
}: DitherProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const thresholdMapRef = useRef<Float32Array | null>(null);
  const dimsRef = useRef({ cols: 0, rows: 0, w: 0, h: 0, dpr: 1 });
  const fillColorRef = useRef(fillColor);
  const progressRef = useRef(0);

  useEffect(() => {
    fillColorRef.current = fillColor;
  }, [fillColor]);

  const { scrollYProgress } = useScroll({
    target: sectionRef,
    offset: ['start end', 'end start'],
  });

  useEffect(() => {
    const canvas = canvasRef.current;
    const section = sectionRef.current;
    if (!canvas || !section) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const buildMap = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      const sectionRect = section.getBoundingClientRect();
      const card = cardRef?.current;
      const cardRect = card?.getBoundingClientRect();
      const w = Math.max(1, Math.ceil(sectionRect.width));
      const h = Math.max(1, Math.ceil(sectionRect.height));
      canvas.width = w * dpr;
      canvas.height = h * dpr;
      canvas.style.width = `${w}px`;
      canvas.style.height = `${h}px`;
      const cols = Math.ceil(w / CELL);
      const rows = Math.ceil(h / CELL);
      const map = new Float32Array(cols * rows);

      // Card bounds in canvas coordinate space (if card provided).
      const cardL = cardRect ? cardRect.left - sectionRect.left : 0;
      const cardT = cardRect ? cardRect.top - sectionRect.top : 0;
      const cardR = cardRect ? cardL + cardRect.width : 0;
      const cardB = cardRect ? cardT + cardRect.height : 0;

      const cxCenter = (cols - 1) / 2;
      const cyCenter = (rows - 1) / 2;
      const maxRadial = Math.hypot(cxCenter, cyCenter) || 1;
      const noiseW = 1 - erosionWeight;

      for (let y = 0; y < rows; y++) {
        for (let x = 0; x < cols; x++) {
          const cx = x * CELL + CELL / 2;
          const cy = y * CELL + CELL / 2;

          // Cells inside the card are skipped so the dashboard always shows.
          if (cardRect && cx >= cardL && cx <= cardR && cy >= cardT && cy <= cardB) {
            map[y * cols + x] = -1;
            continue;
          }

          // Erosion gradient — cells with low values dither out first.
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

          // Noise — ordered or pseudo-random, phase-shifted by seed so
          // stacked dithers don't fall into the same speckle pattern.
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
      draw(progressRef.current);
    };

    const draw = (progress: number) => {
      const map = thresholdMapRef.current;
      if (!map) return;
      const { cols, rows, w, h, dpr } = dimsRef.current;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, w, h);

      // Erosion only starts after the section has begun leaving — keeps the
      // card pristine while it's the focal point of the viewport, then
      // dithers the bg out as the section scrolls past.
      const RAW_START = startWeight;
      const p = Math.min(1, Math.max(0, (progress - RAW_START) / (1 - RAW_START)));
      ctx.fillStyle = fillColorRef.current || '#fff';

      for (let y = 0; y < rows; y++) {
        for (let x = 0; x < cols; x++) {
          const t = map[y * cols + x];
          if (t < 0) continue;
          if (inverse ? p > t : p <= t) continue;
          // +1 overlap prevents sub-pixel gaps between adjacent cells under dpr scaling.
          ctx.fillRect(x * CELL, y * CELL, CELL + 1, CELL + 1);
        }
      }
    };

    buildMap();

    const ro = new ResizeObserver(() => buildMap());
    ro.observe(section);
    if (cardRef?.current) ro.observe(cardRef.current);

    const unsub = scrollYProgress.on('change', (v) => {
      progressRef.current = v;
      draw(v);
    });

    return () => {
      ro.disconnect();
      unsub();
    };
  }, [
    scrollYProgress,
    sectionRef,
    cardRef,
    direction,
    pattern,
    seed,
    erosionWeight,
    startWeight,
    inverse,
  ]);

  return (
    <canvas
      ref={canvasRef}
      aria-hidden
      className={cn('pointer-events-none absolute inset-0', className)}
    />
  );
}
