'use client';

import { cn } from '@/lib/utils';
import { useTheme } from 'next-themes';
import dynamic from 'next/dynamic';
import { type ReactNode, useEffect, useRef, useState } from 'react';

const GrainGradient = dynamic(
  () => import('@paper-design/shaders-react').then((m) => m.GrainGradient),
  { ssr: false },
);

interface ShaderColors {
  base: string;
  colors: [string, string, string];
}

type ThemeShaders = Partial<Record<string, ShaderColors>>;

const defaultShaders: Record<string, ShaderColors> = {
  light: {
    base: '#021267500',
    colors: ['#c6750c', '#beae60', '#d7cbc6'],
  },
  dark: {
    base: '#0a0a0a',
    colors: ['#1a3a4a', '#2a2a3a', '#1a1a2a'],
  },
  amber: {
    base: '#3d2b10',
    colors: ['#8b6914', '#a67c52', '#c4a882'],
  },
};

/** Maps each theme to a CSS gradient that approximates the shader visually. */
const fallbackGradients: Record<string, string> = {
  light: 'linear-gradient(304deg, #c6750c 0%, #beae60 40%, #d7cbc6 100%)',
  dark: 'linear-gradient(304deg, #1a3a4a 0%, #2a2a3a 40%, #1a1a2a 100%)',
  amber: 'linear-gradient(304deg, #8b6914 0%, #a67c52 40%, #c4a882 100%)',
};

interface ShaderContainerProps {
  children: ReactNode;
  shaders?: ThemeShaders;
  softness?: number;
  intensity?: number;
  noise?: number;
  speed?: number;
  rotation?: number;
  shape?: string;
  className?: string;
}

export function ShaderContainer({
  children,
  shaders: overrides,
  softness = 0.125,
  intensity = 0.3,
  noise = 0.35,
  speed = 1,
  rotation = 304,
  shape = 'wave',
  className,
}: ShaderContainerProps) {
  const { theme } = useTheme();
  const key = theme || 'light';
  const ref = useRef<HTMLDivElement>(null);
  const [isVisible, setIsVisible] = useState(false);
  const [prefersReduced, setPrefersReduced] = useState(false);

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
    const el = ref.current;
    if (!el || prefersReduced) return;

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
  }, [prefersReduced]);

  const merged = { ...defaultShaders, ...overrides };
  const shader = merged[key] || merged['light']!;
  const showShader = isVisible && !prefersReduced;

  return (
    <div
      ref={ref}
      className={cn(
        'relative z-50 mt-10 ml-4 overflow-hidden rounded-lg rounded-r-none px-8 py-16 sm:ml-8 lg:ml-0 lg:rounded-r-lg',
        className,
      )}
    >
      {/* CSS gradient fallback — always present, shader renders on top */}
      <div
        className="absolute inset-0 opacity-15"
        style={{ background: fallbackGradients[key] || fallbackGradients['light'] }}
      />

      {showShader && (
        <GrainGradient
          colors={shader.colors}
          colorBack={shader.base}
          softness={softness}
          intensity={intensity}
          noise={noise}
          shape={shape as "dots" | "wave" | "truchet" | "corners" | "ripple" | "blob" | "sphere" | undefined}
          speed={speed}
          className="absolute inset-0"
          rotation={rotation}
        />
      )}
      {children}
    </div>
  );
}
