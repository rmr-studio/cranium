'use client';

import { cdnImageLoader } from '@/lib/cdn-image-loader';
import { cn } from '@/lib/utils';
import { useTheme } from 'next-themes';
import dynamic from 'next/dynamic';
import Image from 'next/image';
import type { ReactNode } from 'react';
import { useShaderVisibility } from './use-shader-visibility';

const GrainGradient = dynamic(
  () => import('@paper-design/shaders-react').then((m) => m.GrainGradient),
  { ssr: false, loading: () => null },
);

interface ShaderColors {
  base: string;
  colors: string[];
}

type ThemeShaders = Partial<Record<string, ShaderColors>>;

/**
 * Optional per-theme CDN image paths for mobile static backgrounds.
 * Paths are relative to the CDN root (e.g. 'images/shaders/hero-light.webp').
 */
export type ThemeStaticImages = Partial<Record<string, string>>;

const defaultShaders: Record<string, ShaderColors> = {
  light: {
    base: '#868ba4',
    colors: ['#c6750c', '#beae60', '#d7cbc6'],
  },
  dark: {
    base: '#75618c',
    colors: ['#1a3a4a', '#2a2a3a', '#1a1a2a'],
  },
  amber: {
    base: '#1a1a2a',
    colors: ['#c6750c', '#1a1a2a', '#1a3a4a'],
  },
};

/** Builds a CSS gradient fallback from a ShaderColors config. */
function buildFallbackGradient(colors: ShaderColors, rotation = 304): string {
  const c = colors.colors;
  if (c.length >= 3) {
    return `linear-gradient(${rotation}deg, ${c[0]} 0%, ${c[1]} 40%, ${c[2]} 100%)`;
  }
  if (c.length === 2) {
    return `linear-gradient(${rotation}deg, ${c[0]} 0%, ${c[1]} 100%)`;
  }
  return `linear-gradient(${rotation}deg, ${c[0] || colors.base} 0%, ${colors.base} 100%)`;
}

interface ShaderContainerProps {
  children: ReactNode;
  shaders?: ThemeShaders;
  /** Per-theme CDN image paths for mobile. Falls back to CSS gradient if omitted. */
  staticImages?: ThemeStaticImages;
  softness?: number;
  intensity?: number;
  noise?: number;
  speed?: number;
  rotation?: number;
  shape?: 'wave' | 'dots' | 'truchet' | 'corners' | 'ripple' | 'blob' | 'sphere';
  className?: string;
  staticOnly?: boolean; // If true, forces static image fallback
  /** Marks the static image as the LCP element — sets loading="eager" and fetchpriority="high" */
  priority?: boolean;
}

export function ShaderContainer({
  children,
  shaders: overrides,
  staticImages,
  softness = 0.125,
  intensity = 0.3,
  noise = 0.7,
  speed = 1,
  rotation = 304,
  shape = 'wave',
  staticOnly = false,
  priority = false,
  className,
}: ShaderContainerProps) {
  const { theme } = useTheme();
  const key = theme || 'light';
  const { containerRef, shaderWrapperRef, showAnimated, isVisible, shaderReady } =
    useShaderVisibility();

  const merged = { ...defaultShaders, ...overrides };
  const shader = merged[key] || merged['light']!;
  const staticImagePath = staticImages?.[key] || staticImages?.['light'];

  return (
    <div
      ref={containerRef}
      className={cn(
        'relative z-50 mt-10 ml-4 overflow-hidden rounded-lg px-8 py-16  sm:ml-8 lg:ml-0',
        className,
      )}
      style={{ backgroundColor: shader.base }}
    >
      {/* CSS gradient fallback — always present as base layer */}
      <div
        className="absolute inset-0 rounded-lg transition-opacity duration-700 ease-out"
        style={{
          background: buildFallbackGradient(shader, rotation),
          opacity: showAnimated ? (shaderReady ? 0 : 0.15) : 0.15,
        }}
      />

      {!staticOnly && showAnimated ? (
        isVisible && (
          <div
            ref={shaderWrapperRef}
            className="absolute inset-0 rounded-lg transition-opacity duration-700 ease-out"
            style={{ opacity: shaderReady ? 1 : 0 }}
          >
            <GrainGradient
              colors={shader.colors}
              colorBack={shader.base}
              softness={softness}
              intensity={intensity}
              noise={noise}
              shape={shape}
              speed={speed}
              className="absolute inset-0"
              rotation={rotation}
            />
          </div>
        )
      ) : staticImagePath ? (
        /* Pre-baked static image from CDN — zero WebGL cost */
        <Image
          loader={cdnImageLoader}
          src={staticImagePath}
          alt=""
          fill
          priority={priority}
          aria-hidden="true"
          className="object-cover"
        />
      ) : null}

      <div className="relative z-10 flex h-full w-auto">{children}</div>
    </div>
  );
}
