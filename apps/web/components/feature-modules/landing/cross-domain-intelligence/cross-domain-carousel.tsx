'use client';

import {
  INSIGHT_CARDS,
  type InsightCard,
} from '@/components/feature-modules/landing/cross-domain-intelligence/card-data';
import {
  Carousel,
  CarouselContent,
  CarouselItem,
  type CarouselApi,
} from '@/components/ui/carousel';
import { WindowControls } from '@/components/ui/window-controls';
import { cn } from '@/lib/utils';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';

const VISIBLE_ENTITIES = 3;

function EntityChips({ entities }: { entities: InsightCard['entities'] }) {
  const visible = entities.slice(0, VISIBLE_ENTITIES);
  const remaining = entities.length - VISIBLE_ENTITIES;

  return (
    <div className="flex items-center gap-1.5 overflow-hidden">
      {visible.map((entity) => (
        <div
          key={entity.label}
          className="flex shrink-0 items-center gap-1.5 rounded-md border bg-card/30 px-2.5 py-1"
        >
          {entity.icon}
          <span className="text-xs font-medium -tracking-[0.02em] whitespace-nowrap text-muted-foreground">
            {entity.label}
          </span>
        </div>
      ))}
      {remaining > 0 && (
        <span className="shrink-0 text-xs -tracking-[0.02em] whitespace-nowrap text-muted-foreground/60">
          +{remaining} more
        </span>
      )}
    </div>
  );
}

function InsightCardComponent({ card }: { card: InsightCard }) {
  return (
    <div className="flex h-full flex-col">
      {/* Card title */}
      <h3 className="mb-4 text-2xl leading-[1.3] font-normal -tracking-[0.01em] text-white md:text-3xl">
        {card.title}
      </h3>

      {/* Mock browser window */}
      <div className="flex flex-1 flex-col overflow-hidden rounded-2xl border bg-card/60 backdrop-blur-lg">
        <div className="flex flex-col gap-4 p-5 md:p-7">
          {/* Browser chrome row */}
          <div className="flex items-center justify-between gap-4">
            <WindowControls size={7} />
            <EntityChips entities={card.entities} />
          </div>

          {/* Insight text */}
          <p className="text-sm leading-tight tracking-tighter text-foreground md:text-base">
            {card.body}
          </p>
        </div>
      </div>
    </div>
  );
}

export function CrossDomainCarousel() {
  const [api, setApi] = useState<CarouselApi>();
  const [current, setCurrent] = useState(0);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const pauseTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [paused, setPaused] = useState(false);

  const onSelect = useCallback(() => {
    if (!api) return;
    setCurrent(api.selectedScrollSnap());
  }, [api]);

  useEffect(() => {
    if (!api) return;
    onSelect();
    api.on('select', onSelect);
    api.on('reInit', onSelect);
    return () => {
      api.off('select', onSelect);
      api.off('reInit', onSelect);
    };
  }, [api, onSelect]);

  // Auto-advance
  useEffect(() => {
    if (!api || paused) return;

    intervalRef.current = setInterval(() => {
      api.scrollNext();
    }, 10000);

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [api, paused]);

  const handleManualNav = useCallback(
    (direction: 'prev' | 'next') => {
      if (!api) return;
      if (direction === 'prev') api.scrollPrev();
      else api.scrollNext();

      setPaused(true);
      if (pauseTimeoutRef.current) clearTimeout(pauseTimeoutRef.current);
      pauseTimeoutRef.current = setTimeout(() => setPaused(false), 8000);
    },
    [api],
  );

  return (
    <div className="dark relative w-full py-12">
      <Carousel
        setApi={setApi}
        opts={{
          loop: true,
          align: 'center',
          skipSnaps: false,
        }}
        className="w-full"
      >
        <CarouselContent className="mx-0!">
          {INSIGHT_CARDS.map((card) => (
            <CarouselItem
              key={card.title}
              className="basis-[95%] bg-transparent md:basis-[45%] md:pl-6 lg:basis-[36%]"
            >
              <InsightCardComponent card={card} />
            </CarouselItem>
          ))}
        </CarouselContent>
      </Carousel>

      {/* Navigation arrows */}
      <div className="mt-10 flex items-center justify-center gap-3">
        <button
          type="button"
          onClick={() => handleManualNav('prev')}
          className="flex h-12 w-12 items-center justify-center rounded-full border border-border/50 bg-muted/60 backdrop-blur-sm transition-colors hover:bg-muted"
          aria-label="Previous slide"
        >
          <ChevronLeft className="h-5 w-5 text-muted-foreground" strokeWidth={1.5} />
        </button>
        <button
          type="button"
          onClick={() => handleManualNav('next')}
          className="flex h-12 w-12 items-center justify-center rounded-full border border-border/50 bg-muted/60 backdrop-blur-sm transition-colors hover:bg-muted"
          aria-label="Next slide"
        >
          <ChevronRight className="h-5 w-5 text-muted-foreground" strokeWidth={1.5} />
        </button>
      </div>

      {/* Dot indicators */}
      <div className="mt-5 flex items-center justify-center gap-2">
        {INSIGHT_CARDS.map((card, index) => (
          <button
            key={card.title}
            type="button"
            onClick={() => {
              api?.scrollTo(index);
              setPaused(true);
              if (pauseTimeoutRef.current) clearTimeout(pauseTimeoutRef.current);
              pauseTimeoutRef.current = setTimeout(() => setPaused(false), 8000);
            }}
            className="flex h-6 w-6 items-center justify-center"
            aria-label={`Go to slide ${index + 1}`}
          >
            <span
              className={cn(
                'h-2.5 rounded-full transition-all duration-300',
                current === index ? 'w-8 bg-foreground/70' : 'w-2.5 bg-foreground/20',
              )}
            />
          </button>
        ))}
      </div>
    </div>
  );
}
