'use client';

import { HeroCopy } from '@/components/feature-modules/landing/hero/components/hero-copy';
import { BGPattern } from '@/components/ui/background/grids';
import { StarsBackground } from '@/components/ui/background/stars';
import { HeroBackground } from './hero-background';

export function Hero() {
  return (
    <StarsBackground starColor={'#b34a7a'} factor={0.01}>
      {' '}
      <HeroBackground
        className="z-0 h-[60svh] translate-y-2/3 opacity-80 3xl:h-[70svh] 3xl:opacity-60"
        image={{
          webp: [
            {
              src: 'images/landing/hero-landing.webp',
              width: 1920,
            },
          ],
        }}
      />
      <section className="relative mx-auto h-screen w-full py-16 pt-20! lg:max-w-[min(100dvw,var(--breakpoint-3xl))] lg:py-32">
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
        <section className="md:py-36 lg:px-12">
          <HeroCopy />
        </section>
      </section>
    </StarsBackground>
  );
}
