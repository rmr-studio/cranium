'use client';

import { useIsMobile } from '@/hooks/use-is-mobile';
import { cn } from '@/lib/utils';
import { motion, useReducedMotion, useScroll, useTransform, type MotionValue } from 'motion/react';
import { useRef } from 'react';
import { FeatureCard, FeatureCardProps } from './reel-card';

export function DiagonalReel({
  cards = [],

  className,
}: {
  cards?: FeatureCardProps[];
  className?: string;
}) {
  const total = cards.length;
  const sectionRef = useRef<HTMLDivElement>(null);
  const reduced = useReducedMotion();
  const isMobile = useIsMobile('lg');

  const { scrollYProgress } = useScroll({
    target: sectionRef,
    offset: ['start start', 'end end'],
  });

  if (reduced || isMobile) {
    return (
      <div className={cn('flex w-full flex-col bg-foreground', className)}>
        <div className="flex flex-col gap-6 p-6 py-24">
          {cards.map((card, i) => (
            <div key={card.title} className="flex w-full">
              <FeatureCard key={card.title} card={card} index={i} total={total} static />
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div
      ref={sectionRef}
      className={cn('relative z-65 w-full bg-foreground', className)}
      style={{ height: `${total * 100}svh` }}
    >
      <div className="sticky top-24 flex h-svh w-full flex-col bg-foreground">
        <div className="flex shrink-0 items-center justify-between px-3 py-4 sm:px-4 sm:py-5">
          <div className="flex items-center gap-2 font-mono text-xs tracking-widest text-zinc-500 uppercase">
            <span className="inline-block h-1.5 w-1.5 rounded-full bg-zinc-400" />
            <span>Features</span>
          </div>
          <ReelProgress total={total} progress={scrollYProgress} />
        </div>

        <div className="relative flex flex-1 items-center justify-center overflow-hidden border-y border-white/15">
          <div
            aria-hidden
            className="pointer-events-none absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-white/15"
          />

          <div className="relative h-full w-full">
            {cards.map((card, i) => (
              <FeatureCard
                key={card.title}
                card={card}
                index={i}
                total={total}
                progress={scrollYProgress}
              />
            ))}
          </div>
        </div>

        <div className="flex shrink-0 justify-end px-3 py-4 sm:px-4 sm:py-5">
          <span className="hidden font-mono text-xs tracking-widest text-zinc-500 uppercase sm:block">
            Scroll ↓
          </span>
        </div>
      </div>
    </div>
  );
}

function ReelProgress({ total, progress }: { total: number; progress: MotionValue<number> }) {
  return (
    <div className="flex items-center gap-1.5">
      {Array.from({ length: total }).map((_, i) => (
        <Tick key={i} i={i} total={total} progress={progress} />
      ))}
    </div>
  );
}

function Tick({ i, total, progress }: { i: number; total: number; progress: MotionValue<number> }) {
  const opacity = useTransform(progress, (p) => {
    const d = Math.abs(p * (total - 1) - i);
    return d < 0.5 ? 1 : 0.3;
  });
  const width = useTransform(progress, (p) => {
    const d = Math.abs(p * (total - 1) - i);
    return d < 0.5 ? 24 : 8;
  });
  return <motion.span style={{ opacity, width }} className="block h-px bg-zinc-200" />;
}
