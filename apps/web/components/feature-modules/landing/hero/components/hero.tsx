'use client';

import { HeroCopy } from '@/components/feature-modules/landing/hero/components/hero-copy';
import { BGPattern } from '@/components/ui/background/grids';
import { StarsBackground } from '@/components/ui/background/stars';
import { HeroBackground } from './hero-background';

const STAR_COLOR = '#b34a7a';

export function Hero() {
  return (
    <StarsBackground starColor={STAR_COLOR} factor={0.01}>
      {' '}
      <section className="relative mx-auto h-svh w-full py-16 pt-20! lg:py-32">
        {/* Dot pattern — visible at top, fades out toward middle */}
        <section className="rouned-t-none absolute inset-x-0 inset-y-18 mx-auto overflow-hidden 2xl:max-w-[min(90vw,var(--breakpoint-3xl))]">
          <HeroBackground
            className="z-0 h-full opacity-90"
            image={{
              webp: [
                {
                  src: 'images/landing/hero-landing.webp',
                  width: 1920,
                },
              ],
            }}
          />
        </section>

        <BGPattern
          variant="dots"
          size={12}
          fill="color-mix(in srgb, var(--foreground) 10%, transparent)"
          mask="none"
          className="z-20 3xl:opacity-10"
          style={{
            maskImage:
              'radial-gradient(ellipse at center, black 30%, transparent 75%), linear-gradient(to bottom, black 0%, black 40%, transparent 65%)',
            maskComposite: 'intersect',
            WebkitMaskImage:
              'radial-gradient(ellipse at center, black 30%, transparent 75%), linear-gradient(to bottom, black 0%, black 40%, transparent 65%)',
            WebkitMaskComposite: 'source-in' as string,
          }}
        />
        <section className="absolute inset-0 top-12 z-[65] sm:top-1/5 3xl:py-24">
          <HeroCopy />
        </section>
      </section>
    </StarsBackground>
  );
}
