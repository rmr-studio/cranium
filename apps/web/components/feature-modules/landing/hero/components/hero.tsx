'use client';

import { HeroCopy } from '@/components/feature-modules/landing/hero/components/hero-copy';
import { BGPattern } from '@/components/ui/background/grids';
import { Dither } from '@/components/ui/dither';
import { useRef } from 'react';
import { SignalPreview } from '../../preview/components/signal-preview';
import { HeroBackground } from './hero-background';

export function Hero() {
  const sectionRef = useRef<HTMLDivElement>(null);
  return (
    <>
      <section className="h-[min(60rem, 120vh)] relative mx-auto my-0! w-full py-0! pt-20! sm:py-16 lg:h-[clamp(50rem,100svh,80rem)] lg:py-32">
        {/* Dot pattern — visible at top, fades out toward middle */}
        <section className="rouned-t-none absolute inset-x-0 inset-y-0 mx-auto max-h-[clamp(35rem,100svh,80rem)] overflow-hidden sm:inset-y-18 2xl:max-w-[min(90vw,var(--breakpoint-3xl))]">
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

        <section
          className="absolute inset-x-0 -bottom-[10rem] z-0 hidden h-[40rem] sm:block"
          aria-hidden
          ref={sectionRef}
        >
          <Dither
            sectionRef={sectionRef}
            fillColor="oklch(0.95 0.007 81)"
            pattern="noise"
            seed={2}
            direction="bottom-up"
            startWeight={-0.5}
          />
        </section>
        <section className="z-60 flex flex-col items-center space-y-48 sm:mt-32">
          <HeroCopy />
          <SignalPreview />
        </section>
      </section>
    </>
  );
}
