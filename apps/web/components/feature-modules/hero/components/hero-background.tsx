'use client';

import { StarsBackground } from '@/components/animate-ui/components/backgrounds/stars';
import { BGPattern } from '@/components/ui/background/grids';
import { cdnImageLoader, getCdnUrl } from '@/lib/cdn-image-loader';
import { cn } from '@/lib/utils';
import { useTheme } from 'next-themes';
import Image from 'next/image';
import { useState } from 'react';

interface ImageVariant {
  src: string;
  width: number;
}

interface HeroBackgroundProps {
  className?: string;
  image: {
    avif: ImageVariant[];
    webp: ImageVariant[];
  };
  alt?: string;
}

function buildSrcSet(variants: ImageVariant[]): string {
  return variants.map((v) => `${getCdnUrl(v.src)} ${v.width}w`).join(', ');
}

export function HeroBackground({
  className,
  image,
  alt = 'Background image',
}: HeroBackgroundProps) {
  const [isLoaded, setIsLoaded] = useState(false);

  // Build the fallback src from the smallest webp variant
  const fallbackSrc = getCdnUrl(image.webp[0].src);
  const { resolvedTheme } = useTheme();
  return (
    <>
      <BGPattern
        variant={'grid'}
        size={24}
        mask="fade-edges"
        fill="color-mix(in srgb, var(--primary) 2.5%, transparent)"
        className="z-20"
      />
      <div
        className={cn(
          'absolute inset-x-0 z-0 mx-auto max-w-screen-3xl 3xl:right-0 3xl:left-auto 3xl:mx-0 3xl:w-[55%] 3xl:max-w-none',
          className,
        )}
      >
        {/* Side gradient faders — inside the image container so they blend its edges */}
        <div
          className="pointer-events-none absolute inset-y-0 left-0 z-10 hidden w-[8%] md:block 3xl:w-[40%]"
          style={{
            background: 'linear-gradient(to right, var(--background) 10%, transparent)',
          }}
        />
        <div
          className="pointer-events-none absolute inset-y-0 right-0 z-10 hidden w-[8%] md:block 3xl:w-[8%]"
          style={{
            background: 'linear-gradient(to left, var(--background) 10%, transparent)',
          }}
        />

        <picture className="relative block h-full w-full">
          <source srcSet={buildSrcSet(image.avif)} type="image/avif" sizes="100vw" />
          <source srcSet={buildSrcSet(image.webp)} type="image/webp" sizes="100vw" />
          <Image
            loader={cdnImageLoader}
            src={fallbackSrc}
            alt={alt}
            fill
            fetchPriority={'high'}
            decoding={'async'}
            priority={true}
            sizes="100vw"
            className={cn(
              'object-cover object-bottom invert-100 transition-opacity duration-400 ease-out dark:invert-0',
              isLoaded ? 'opacity-100' : 'opacity-0',
            )}
            onLoad={() => setIsLoaded(true)}
          />
        </picture>

        <div className="absolute inset-0 bg-linear-to-t from-transparent via-transparent to-background" />
      </div>
      <StarsBackground
        factor={0.15}
        starColor={'color-mix(in srgb, var(--foreground) 100%, transparent)'}
        className={cn('absolute inset-0 flex items-center justify-center rounded-xl')}
      />
      {/* Horizontal colour wash — cool blue (left) → warm rose (right) */}

      <div className="absolute inset-0 bg-linear-to-b from-transparent via-transparent to-background" />
      <div
        className={cn(
          'pointer-events-none absolute inset-0 opacity-0',
          'bg-linear-to-r from-red-500/40 to-pink-500/30 mix-blend-hard-light dark:from-purple-600/20 dark:to-orange-500/20 amber:from-pink-800/70 amber:mix-blend-color-dodge',
          isLoaded && 'animate-[fade-wash_0.8s_ease-out_0.6s_forwards]',
        )}
        aria-hidden="true"
        style={{
          maskImage: 'linear-gradient(to bottom, black 50%, transparent 100%)',
          WebkitMaskImage: 'linear-gradient(to bottom, black 50%, transparent 100%)',
        }}
      />
    </>
  );
}
