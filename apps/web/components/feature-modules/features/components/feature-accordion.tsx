'use client';

import { cn } from '@/lib/utils';
import { AnimatePresence, motion } from 'motion/react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { AnimateOnMountContext } from '../data-model/components/graphic/animate-context';
import { FEATURE_CONTENT } from '../knowledge/config/accordion-content';

const AUTO_ADVANCE_MS = 200000;
const INTERACTION_PAUSE_MS = 3000;

export const VisualAccordionSection = () => {
  const [activeIndex, setActiveIndex] = useState<number>(0);
  const [paused, setPaused] = useState(false);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const pauseTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const startRef = useRef(Date.now());

  // Auto-advance timer (desktop only, controlled by CSS visibility)
  useEffect(() => {
    if (paused) return;

    startRef.current = Date.now();

    timerRef.current = setInterval(() => {
      const elapsed = Date.now() - startRef.current;

      if (elapsed >= AUTO_ADVANCE_MS) {
        setActiveIndex((prev) => (prev + 1) % FEATURE_CONTENT.length);
        startRef.current = Date.now();
      }
    }, 200);

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [activeIndex, paused]);

  const handleItemClick = useCallback((index: number) => {
    setActiveIndex(index);
    setPaused(true);
    if (pauseTimeoutRef.current) clearTimeout(pauseTimeoutRef.current);
    pauseTimeoutRef.current = setTimeout(() => setPaused(false), INTERACTION_PAUSE_MS);
  }, []);

  return (
    <div className="w-full">
      {/* Desktop: item list left, visual right */}
      <div className="hidden lg:grid lg:grid-cols-2 lg:items-center lg:gap-12">
        {/* Left: Item selector list */}
        <div className="flex flex-col">
          {FEATURE_CONTENT.map((item, index) => {
            const isActive = activeIndex === index;
            return (
              <button
                key={index}
                type="button"
                onClick={() => handleItemClick(index)}
                className="flex cursor-pointer items-start gap-4 border-b border-white/10 py-5 text-left last:border-b-0"
              >
                {/* Indicator dot */}
                <div className="flex h-7 w-4 shrink-0 items-center justify-center">
                  <div
                    className={cn(
                      'rounded-full transition-all duration-300',
                      isActive ? 'h-3 w-3 bg-primary' : 'h-2 w-2 bg-content/20',
                    )}
                  />
                </div>

                {/* Text content */}
                <div className="min-w-0 flex-1">
                  <div
                    className={cn(
                      'text-lg font-medium transition-colors duration-300',
                      isActive ? 'text-heading' : 'text-content/40',
                    )}
                  >
                    {item.title}
                  </div>

                  <AnimatePresence initial={false}>
                    {isActive && (
                      <motion.p
                        initial={{ height: 0, opacity: 0 }}
                        animate={{ height: 'auto', opacity: 1 }}
                        exit={{ height: 0, opacity: 0 }}
                        transition={{ duration: 0.3, ease: 'easeInOut' }}
                        className="overflow-hidden text-sm leading-relaxed text-content"
                      >
                        <span className="block pt-2">{item.description}</span>
                      </motion.p>
                    )}
                  </AnimatePresence>
                </div>
              </button>
            );
          })}
        </div>

        {/* Right: Visual panel */}
        <div className="relative my-4 flex items-end justify-center">
          <div
            className="relative h-180 w-full overflow-hidden rounded-xl"
            style={{
              maskImage:
                'linear-gradient(to right, transparent, black 10%, black 75%, transparent), linear-gradient(to bottom, black 0%, black 95%, transparent)',
              maskComposite: 'intersect',
              WebkitMaskImage:
                'linear-gradient(to right, transparent, black 10%, black 75%, transparent), linear-gradient(to bottom, black 0%, black 95%, transparent)',
              WebkitMaskComposite: 'source-in',
            }}
          >
            <AnimatePresence mode="wait">
              <motion.div
                key={activeIndex}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -12 }}
                transition={{ duration: 0.3, ease: 'easeInOut' }}
                className="flex h-full items-center pt-4"
              >
                {FEATURE_CONTENT[activeIndex]?.content}
              </motion.div>
            </AnimatePresence>
          </div>
        </div>
      </div>

      {/* Mobile: all items fully expanded in scroll */}
      <div className="flex flex-col gap-16 lg:hidden">
        {FEATURE_CONTENT.map((item, index) => (
          <div key={index} className="flex flex-col items-center">
            {/* Title */}
            <div className="mx-auto max-w-7xl text-center text-2xl font-medium text-heading">
              {item.title}
            </div>

            {/* Description */}
            <p className="mx-auto mt-3 max-w-7xl text-center text-sm leading-relaxed text-content">
              {item.description}
            </p>

            {/* Visual — animate-on-mount bypasses unreliable IntersectionObserver
                for SVG elements inside overflow-hidden containers on mobile */}
            <div
              className="relative mx-auto mt-6 min-h-[400px] w-full max-w-2xl overflow-hidden rounded-lg sm:min-h-[500px]"
              style={{
                maskImage:
                  'linear-gradient(to right, black 85%, transparent), linear-gradient(to bottom, black 30%, transparent 120%)',
                maskComposite: 'intersect',
                WebkitMaskImage:
                  'linear-gradient(to right, black 85%, transparent), linear-gradient(to bottom, black 30%, transparent 120%)',
                WebkitMaskComposite: 'source-in',
              }}
            >
              <AnimateOnMountContext.Provider value={true}>
                {item.content}
              </AnimateOnMountContext.Provider>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
