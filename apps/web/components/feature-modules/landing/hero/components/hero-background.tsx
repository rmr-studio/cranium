'use client';

import { BGPattern } from '@/components/ui/background/grids';
import { StarsBackground } from '@/components/ui/background/stars';
import { cdnImageLoader, getCdnUrl } from '@/lib/cdn-image-loader';
import { cn } from '@/lib/utils';
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
  return (
    <div
      className="absolute inset-0"
      style={{
        maskImage: 'linear-gradient(to bottom, black 55%, transparent 100%)',
        WebkitMaskImage: 'linear-gradient(to bottom, black 55%, transparent 100%)',
      }}
    >
      <BGPattern
        variant={'grid'}
        size={24}
        mask="fade-edges"
        fill="color-mix(in srgb, var(--primary) 2.5%, transparent)"
        className="z-20"
      />
      <div
        className={cn(
          'absolute inset-x-0 z-0 mx-auto max-w-screen-3xl 3xl:right-0 3xl:left-auto 3xl:mx-0 3xl:w-[70%] 3xl:max-w-none',
          className,
        )}
        style={{
          maskImage:
            'linear-gradient(to bottom, transparent, black 25%), linear-gradient(to right, transparent, black 8%, black 92%, transparent)',
          WebkitMaskImage:
            'linear-gradient(to bottom, transparent, black 25%), linear-gradient(to right, transparent, black 8%, black 92%, transparent)',
          maskComposite: 'intersect',
          WebkitMaskComposite: 'source-in' as string,
        }}
      >
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
      </div>
      <StarsBackground
        factor={0.15}
        starColor={'color-mix(in srgb, var(--foreground) 100%, transparent)'}
        className={cn('absolute inset-0 flex items-center justify-center rounded-xl')}
      />
    </div>
  );
}
