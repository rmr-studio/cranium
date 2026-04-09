'use client';

import { HeroCopy } from '@/components/feature-modules/landing/hero/components/hero-copy';
import { BGPattern } from '@/components/ui/background/grids';
import { StarsBackground } from '@/components/ui/background/stars';
import { ShaderContainer, ThemeStaticImages } from '@/components/ui/shader-container';
import { useTheme } from 'next-themes';
import { DesktopShowcase } from './showcase/desktop-showcase';
import { KnowledgeShowcase } from './showcase/knowledge-showcase';

export function Hero() {
  const { theme } = useTheme();

  const gradients: ThemeStaticImages = {
    light: 'images/texture/static-gradient-2.webp',
    dark: 'images/texture/static-gradient-2.webp',
  };

  const gradients2: ThemeStaticImages = {
    light: 'images/texture/static-gradient-4.webp',
    dark: 'images/texture/static-gradient-4.webp',
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
  };

  return (
    <StarsBackground starColor={theme === 'light' ? 'black' : 'white'} factor={0.01}>
      {' '}
      --- IGNORE ---
      <section className="relative h-fit w-full py-16 pt-20! lg:py-32">
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
        <section className="md:py-24 lg:px-12">
          <HeroCopy />
        </section>
        <section className="relative flex flex-col space-x-4 xl:flex-row 3xl:px-3">
          <ShaderContainer
            priority
            staticImages={gradients}
            className="z-50 mx-0! w-full rounded-l-none! rounded-r-none! p-0 shadow-lg shadow-foreground/30 lg:mr-4! lg:ml-0! xl:w-2/3 xl:rounded-r-lg! 3xl:rounded-l-lg! dark:shadow-none"
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
            className="z-50 mx-0! hidden rounded-l-none rounded-r-none! p-0 shadow-lg shadow-foreground/40 sm:block lg:mr-0! lg:ml-4! lg:rounded-l-lg xl:w-1/3 3xl:rounded-r-lg! dark:shadow-none"
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
    </StarsBackground>
  );
}
