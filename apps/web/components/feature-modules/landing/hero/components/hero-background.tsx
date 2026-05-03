'use client';

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
    webp: ImageVariant[];
  };
  alt?: string;
}

function buildSrcSet(variants: ImageVariant[]): string {
  return variants.map((v) => `${getCdnUrl(v.src)} ${v.width}w`).join(', ');
}

export function HeroBackground({ className, image, alt = '' }: HeroBackgroundProps) {
  const [isLoaded, setIsLoaded] = useState(false);

  // Build the fallback src from the smallest webp variant
  const fallbackSrc = getCdnUrl(image.webp[0].src);
  return (
    <div className="absolute inset-0">
      <div className={cn('absolute inset-x-0 z-0 mx-auto', className)}>
        <picture className="relative block h-full w-full">
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
              'object-cover transition-opacity duration-400 ease-out',
              isLoaded ? 'opacity-100' : 'opacity-0',
            )}
            onLoad={() => setIsLoaded(true)}
          />
        </picture>
      </div>
    </div>
  );
}
