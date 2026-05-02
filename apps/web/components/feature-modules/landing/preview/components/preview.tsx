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
        'pointer-events-none absolute inset-x-0 [top:max(-25rem,calc(-30dvh-5rem))] flex h-[40rem] w-full items-end justify-center px-4 sm:[top:max(-32rem,calc(-30dvh-7rem))] sm:h-[52rem] lg:[top:max(-44rem,calc(-30dvh-7rem))] lg:h-[64rem]',
        className,
      )}
      aria-hidden
    >
      {/* Dither blanket — covers everything behind, except the card. */}
      <Dither sectionRef={sectionRef} cardRef={cardRef} pattern="noise" startWeight={-1.25} />

      {/* Card surrounds the preview only; the bg blanket is independent. */}
      <div
        ref={cardRef}
        className="pointer-events-auto relative z-10 z-[70] mb-12 aspect-[4/5] w-full max-w-[88rem] overflow-hidden rounded-2xl bg-background/95 shadow-[0_40px_120px_-30px_rgb(0_0_0/0.35)] ring-1 ring-foreground/8 backdrop-blur-md sm:aspect-[16/9] 3xl:translate-y-0"
      >
        {children ?? <SignalPreview />}
      </div>
    </section>
  );
}
