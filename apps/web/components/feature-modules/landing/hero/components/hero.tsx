'use client';

import { HeroCopy } from '@/components/feature-modules/landing/hero/components/hero-copy';
import { ProductShowcaseGraphic } from '@/components/feature-modules/landing/time-saved/components/product-showcase/components/product-showcase';
import { BGPattern } from '@/components/ui/background/grids';
import { ShaderContainer, ThemeStaticImages } from '@/components/ui/shader-container';

export function Hero() {
  const gradients: ThemeStaticImages = {
    light: 'images/texture/static-gradient-2.webp',
    dark: 'images/texture/static-gradient-2.webp',
    amber: 'images/texture/static-gradient-2.webp',
  };

  return (
    <section className="relative w-full overflow-hidden py-16 pt-20! md:py-24 lg:px-12 lg:py-32">
      {/* Dot pattern — visible at top, fades out toward middle */}

      <BGPattern
        variant="dots"
        size={12}
        fill="color-mix(in srgb, var(--foreground) 40%, transparent)"
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

      <ShaderContainer
        priority
        staticImages={gradients}
        className="z-50 mt-10 ml-0! rounded-none p-0! shadow-lg shadow-foreground/50 sm:py-16 lg:rounded-lg lg:rounded-r-lg lg:p-12! dark:shadow-none"
      >
        <ProductShowcaseGraphic />
      </ShaderContainer>
    </section>
  );
}
