'use client';

import { HeroCopy } from '@/components/feature-modules/landing/hero/components/hero-copy';
import { BGPattern } from '@/components/ui/background/grids';
import { ShaderContainer, ThemeStaticImages } from '@/components/ui/shader-container';
import { DesktopShowcase } from './showcase/desktop-showcase';
import { KnowledgeShowcase } from './showcase/knowledge-showcase';

export function Hero() {
  const gradients: ThemeStaticImages = {
    light: 'images/texture/static-gradient-2.webp',
    dark: 'images/texture/static-gradient-2.webp',
    amber: 'images/texture/static-gradient-2.webp',
  };

  const gradients2: ThemeStaticImages = {
    light: 'images/texture/static-gradient-5.webp',
    dark: 'images/texture/static-gradient-5.webp',
    amber: 'images/texture/static-gradient-5.webp',
  };

  const dashboardShaders = {
    light: {
      base: '#f4a462',
      colors: ['#f4a462', '#9c2b2b', '#ffffff'] as [string, string, string],
    },
    dark: {
      base: '#274754',
      colors: ['#ffffff', '#f4a462', '#9c2b2b'] as [string, string, string],
    },
    amber: {
      base: '#ffffff',
      colors: ['#9c2b2b', '#274754', '#f4a462'] as [string, string, string],
    },
  };

  return (
    <section className="relative h-fit w-full py-16 pt-20! md:py-24 lg:px-12 lg:py-32">
      {/* Dot pattern — visible at top, fades out toward middle */}

      <BGPattern
        variant="dots"
        size={12}
        fill="color-mix(in srgb, var(--foreground) 10%, transparent)"
        mask="none"
        className="z-20"
        style={{
          maskImage:
            'radial-gradient(ellipse at center, black 30%, transparent 75%), linear-gradient(to bottom, black 0%, black 40%, transparent 65%)',
          maskComposite: 'intersect',
          WebkitMaskImage:
            'radial-gradient(ellipse at center, black 30%, transparent 75%), linear-gradient(to bottom, black 0%, black 40%, transparent 65%)',
          WebkitMaskComposite: 'source-in' as string,
        }}
      />

      <HeroCopy />
      <section className="relative flex flex-col space-x-4 xl:flex-row">
        <ShaderContainer
          priority
          staticImages={gradients}
          className="z-50 mr-0! ml-12! w-full p-0 shadow-lg shadow-foreground/30 lg:mr-4! lg:ml-0! dark:shadow-none"
        >
          <section className="p-4">
            <div className="pointer-events-none absolute inset-y-0 left-0 z-10 hidden w-24 bg-gradient-to-r from-black/60 via-black/25 to-transparent md:w-40 3xl:block" />
            <div className="pointer-events-none absolute inset-y-0 right-0 z-10 w-24 bg-gradient-to-l from-black/60 via-black/25 to-transparent md:w-40" />
            <div className="pointer-events-none absolute inset-0 z-10 opacity-60 shadow-[inset_20px_0_40px_rgba(0,0,0,0.5),inset_-20px_0_40px_rgba(0,0,0,0.5)] md:shadow-[inset_32px_0_60px_rgba(0,0,0,0.55),inset_-32px_0_60px_rgba(0,0,0,0.25)]" />
            <DesktopShowcase />
          </section>
        </ShaderContainer>
        <ShaderContainer
          rotation={130}
          priority
          shaders={dashboardShaders}
          staticImages={gradients2}
          className="z-50 mr-8! ml-0! rounded-l-none rounded-r-lg! p-0 shadow-lg shadow-foreground/40 lg:mr-0! lg:ml-4! lg:rounded-l-lg xl:w-2/5 dark:shadow-none"
        >
          <section className="p-4">
            <div className="pointer-events-none absolute inset-y-0 left-0 z-10 hidden w-24 bg-gradient-to-r from-black/60 via-black/25 to-transparent md:w-40 3xl:block" />
            <div className="pointer-events-none absolute inset-y-0 right-0 z-10 w-24 bg-gradient-to-l from-black/60 via-black/25 to-transparent md:w-40" />
            <div className="pointer-events-none absolute inset-0 z-10 opacity-60 shadow-[inset_20px_0_40px_rgba(0,0,0,0.5),inset_-20px_0_40px_rgba(0,0,0,0.5)] md:shadow-[inset_32px_0_60px_rgba(0,0,0,0.55),inset_-32px_0_60px_rgba(0,0,0,0.25)]" />
            <KnowledgeShowcase />
          </section>
        </ShaderContainer>
      </section>
    </section>
  );
}
