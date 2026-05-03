'use client';

import { Dither } from '@/components/ui/dither';
import { cn } from '@/lib/utils';
import { useRef } from 'react';
import { SignalPreview } from './signal-preview';

interface PreviewProps {
  className?: string;
  children?: React.ReactNode;
}

export function Preview({ className, children }: PreviewProps) {
  const sectionRef = useRef<HTMLElement>(null);
  const cardRef = useRef<HTMLDivElement>(null);

  return (
    <section
      ref={sectionRef}
      className={cn(
        'pointer-events-none absolute inset-x-0 flex w-full items-end justify-center px-4',
        '[top:clamp(-30rem,calc(-30dvh-5rem),-2rem)] h-[40rem]',
        'sm:[top:clamp(-32rem,calc(-25dvh-3rem),-4rem)] md:h-[52rem]',
        'lg:[top:clamp(-50rem,calc(-40dvh-3rem),-6rem)] xl:h-[64rem]',
        className,
      )}
      aria-hidden
    >
      {/* Dither blanket — covers everything behind, except the card. */}
      <Dither sectionRef={sectionRef} cardRef={cardRef} pattern="noise" startWeight={-1.25} />

      {/* Card surrounds the preview only; the bg blanket is independent. */}
      <div
        ref={cardRef}
        className="pointer-events-auto relative z-[70] mb-12 aspect-[4/5] max-h-[80dvh] w-full max-w-[88rem] origin-top overflow-hidden rounded-2xl bg-background/95 shadow-[0_40px_120px_-30px_rgb(0_0_0/0.35)] ring-1 ring-foreground/8 backdrop-blur-md md:aspect-[16/9] 3xl:translate-y-0"
      >
        {children ?? <SignalPreview />}
      </div>
    </section>
  );
}
