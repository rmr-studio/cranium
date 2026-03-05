'use client';

import { Button } from '@/components/ui/button';
import { Carousel, CarouselContent, CarouselItem, useCarousel } from '@/components/ui/carousel';
import { useBreakpoint } from '@/hooks/use-breakpoint';
import { useIsMobile } from '@/hooks/use-is-mobile';
import { useMounted } from '@riven/hooks';
import { cn } from '@/lib/utils';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { AnimatePresence, motion } from 'motion/react';
import * as React from 'react';
import { BGPattern } from './background/grids';

function MobileCarouselNav() {
  const { scrollPrev, scrollNext, canScrollPrev, canScrollNext } = useCarousel();

  return (
    <div className="mt-6 flex justify-end gap-2 px-4">
      <Button
        variant="outline"
        size="icon"
        onClick={scrollPrev}
        disabled={!canScrollPrev}
        className="rounded-full"
        aria-label="Previous slide"
      >
        <ChevronLeft className="h-4 w-4" />
      </Button>
      <Button
        variant="outline"
        size="icon"
        onClick={scrollNext}
        disabled={!canScrollNext}
        className="rounded-full"
        aria-label="Next slide"
      >
        <ChevronRight className="h-4 w-4" />
      </Button>
    </div>
  );
}

interface BentoCardProps {
  title: string;
  description?: string;
  className?: string;
  children?: React.ReactNode;
  /** Grid area name for CSS grid placement */
  area?: string;
}

export function BentoCard({ title, description, className, children, area }: BentoCardProps) {
  return (
    <div
      className={cn(
        'relative flex flex-col overflow-hidden rounded-2xl border border-border p-6',
        className,
      )}
      style={area ? { gridArea: area } : undefined}
    >
      <BGPattern
        variant="grid"
        // mask="fade-edges"
        className="z-0 bg-secondary"
        size={8}
        fill="color-mix(in srgb, var(--primary) 5%, transparent)"
      />
      <div className="relative z-10 flex-shrink-0 space-y-2">
        <h3 className="text-lg leading-tight font-medium tracking-tight">{title}</h3>
        {description && (
          <p className="line-clamp-3 text-xs leading-tight tracking-tighter text-muted-foreground md:text-sm">
            {description}
          </p>
        )}
      </div>
      {children && <div className="relative z-10 mt-4 min-h-0 flex-1">{children}</div>}
    </div>
  );
}

interface ResponsiveGridConfig {
  /** CSS grid-template-areas string */
  areas?: string;
  /** CSS grid-template-columns */
  cols?: string;
  /** CSS grid-template-rows */
  rows?: string;
}

interface BentoSlideProps {
  children: React.ReactNode;
  className?: string;
  /** CSS grid-template-areas string for custom layouts (xl breakpoint) */
  gridAreas?: string;
  /** CSS grid-template-columns (xl breakpoint) */
  gridCols?: string;
  /** CSS grid-template-rows (xl breakpoint) */
  gridRows?: string;
  /** Grid config for md breakpoint (768px-1023px) */
  md?: ResponsiveGridConfig;
  /** Grid config for lg breakpoint (1024px-1279px) */
  lg?: ResponsiveGridConfig;
}

export function BentoSlide({
  children,
  className,
  gridAreas,
  gridCols = 'repeat(3, 1fr)',
  gridRows = 'repeat(2, 1fr)',
  md,
  lg,
}: BentoSlideProps) {
  const mounted = useMounted();
  const breakpoint = useBreakpoint();

  // Determine grid config based on current breakpoint
  const getGridConfig = (): ResponsiveGridConfig => {
    if (breakpoint === 'md' && md) {
      return {
        areas: md.areas,
        cols: md.cols ?? 'repeat(2, 1fr)',
        rows: md.rows ?? 'auto',
      };
    }

    if (breakpoint === 'lg' && lg) {
      return {
        areas: lg.areas ?? gridAreas,
        cols: lg.cols ?? gridCols,
        rows: lg.rows ?? gridRows,
      };
    }

    // xl breakpoint or fallback
    return {
      areas: gridAreas,
      cols: gridCols,
      rows: gridRows,
    };
  };

  const config = getGridConfig();

  return (
    <AnimatePresence>
      {mounted && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.5 }}
          className={cn('grid h-full gap-4', className)}
          style={{
            gridTemplateAreas: config.areas,
            gridTemplateColumns: config.cols,
            gridTemplateRows: config.rows,
          }}
        >
          {children}
        </motion.div>
      )}
    </AnimatePresence>
  );
}

interface BentoCarouselProps {
  children: React.ReactNode;
  className?: string;
  /** How much of the next slide to show (in pixels) */
  peekAmount?: number;
  /** Cards to show in mobile single-file carousel mode */
  mobileCards?: React.ReactNode[];
  /** Side inset for full-bleed mode. When set, carousel extends edge-to-edge with content inset from viewport edges. */
  inset?: string;
}

export function BentoCarouselContainer({
  children,
  className,
  peekAmount = 80,
  mobileCards,
  inset,
}: BentoCarouselProps) {
  const mounted = useMounted();
  const containerRef = React.useRef<HTMLDivElement>(null);
  const [currentSlide, setCurrentSlide] = React.useState(0);
  const [canScrollLeft, setCanScrollLeft] = React.useState(false);
  const [canScrollRight, setCanScrollRight] = React.useState(true);
  const [isDragging, setIsDragging] = React.useState(false);
  const isMobile = useIsMobile('md');

  // Drag state refs (using refs to avoid re-renders during drag)
  const dragStartX = React.useRef(0);
  const scrollStartX = React.useRef(0);
  const hasDragged = React.useRef(false);

  const childCount = React.Children.count(children);
  const isLastSlide = currentSlide >= childCount - 1;

  const getSlideWidth = React.useCallback(() => {
    const container = containerRef.current;
    if (!container) return 0;
    const firstSlide = container.querySelector('[data-slide]') as HTMLElement;
    return firstSlide ? firstSlide.offsetWidth + 24 : 0; // 24px gap
  }, []);

  const checkScrollability = React.useCallback(() => {
    const container = containerRef.current;
    if (!container) return;

    const { scrollLeft, scrollWidth, clientWidth } = container;
    setCanScrollLeft(scrollLeft > 0);
    setCanScrollRight(scrollLeft < scrollWidth - clientWidth - 1);
  }, []);

  const updateCurrentSlide = React.useCallback(() => {
    const container = containerRef.current;
    if (!container) return;

    const slideWidth = getSlideWidth();
    if (slideWidth === 0) return;

    const newSlide = Math.round(container.scrollLeft / slideWidth);
    setCurrentSlide(Math.max(0, Math.min(newSlide, childCount - 1)));
  }, [childCount, getSlideWidth]);

  React.useEffect(() => {
    checkScrollability();
    window.addEventListener('resize', checkScrollability);
    return () => window.removeEventListener('resize', checkScrollability);
  }, [checkScrollability]);

  // Drag handlers
  const handleDragStart = (clientX: number) => {
    const container = containerRef.current;
    if (!container) return;

    setIsDragging(true);
    hasDragged.current = false;
    dragStartX.current = clientX;
    scrollStartX.current = container.scrollLeft;
  };

  const handleDragMove = (clientX: number) => {
    if (!isDragging) return;
    const container = containerRef.current;
    if (!container) return;

    const deltaX = dragStartX.current - clientX;
    if (Math.abs(deltaX) > 5) {
      hasDragged.current = true;
    }
    container.scrollLeft = scrollStartX.current + deltaX;
  };

  const handleDragEnd = () => {
    if (!isDragging) return;
    const container = containerRef.current;
    if (!container) {
      setIsDragging(false);
      return;
    }

    const slideWidth = getSlideWidth();
    if (slideWidth === 0) {
      setIsDragging(false);
      return;
    }

    // Calculate drag distance and direction
    const dragDistance = container.scrollLeft - scrollStartX.current;
    const dragThreshold = slideWidth * 0.15; // 15% of slide width triggers change

    let targetSlide = currentSlide;

    if (dragDistance > dragThreshold) {
      // Dragged right (next slide)
      targetSlide = Math.min(currentSlide + 1, childCount - 1);
    } else if (dragDistance < -dragThreshold) {
      // Dragged left (previous slide)
      targetSlide = Math.max(currentSlide - 1, 0);
    }

    // Update state immediately for fade effect
    setCurrentSlide(targetSlide);
    setIsDragging(false);

    // Smoothly scroll to the target slide
    const targetScrollLeft = targetSlide * slideWidth;
    container.scrollTo({ left: targetScrollLeft, behavior: 'smooth' });
  };

  // Mouse events
  const onMouseDown = (e: React.MouseEvent) => {
    e.preventDefault();
    handleDragStart(e.clientX);
  };

  const onMouseMove = (e: React.MouseEvent) => {
    handleDragMove(e.clientX);
  };

  const onMouseUp = () => {
    handleDragEnd();
  };

  const onMouseLeave = () => {
    handleDragEnd();
  };

  // Touch events
  const onTouchStart = (e: React.TouchEvent) => {
    handleDragStart(e.touches[0].clientX);
  };

  const onTouchMove = (e: React.TouchEvent) => {
    handleDragMove(e.touches[0].clientX);
  };

  const onTouchEnd = () => {
    handleDragEnd();
  };

  const scroll = (direction: 'left' | 'right') => {
    const container = containerRef.current;
    if (!container) return;

    // Update slide index immediately before animation
    setCurrentSlide((prev) => {
      if (direction === 'right') {
        return Math.min(prev + 1, childCount - 1);
      } else {
        return Math.max(prev - 1, 0);
      }
    });

    const slideWidth = getSlideWidth();
    if (slideWidth === 0) return;

    const scrollAmount = direction === 'left' ? -slideWidth : slideWidth;
    container.scrollBy({ left: scrollAmount, behavior: 'smooth' });
  };

  // Mobile view using shadcn carousel
  if (isMobile && mobileCards) {
    return (
      <AnimatePresence>
        {mounted && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.5 }}
          >
            <Carousel
              className={cn('w-full', className)}
              opts={{
                align: 'start',
                loop: false,
              }}
            >
              <CarouselContent className="ml-4">
                {mobileCards.map((card, index) => (
                  <CarouselItem key={index} className="basis-[90%] pl-2">
                    <div className="h-[480px] [&>*]:h-full">{card}</div>
                  </CarouselItem>
                ))}
              </CarouselContent>
              <MobileCarouselNav />
            </Carousel>
          </motion.div>
        )}
      </AnimatePresence>
    );
  }

  // Desktop view with custom implementation
  return (
    <AnimatePresence>
      {mounted && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.5 }}
          className={cn('relative', className)}
        >
          {/* Carousel container */}
          <div
            ref={containerRef}
            onScroll={checkScrollability}
            onMouseDown={onMouseDown}
            onMouseMove={onMouseMove}
            onMouseUp={onMouseUp}
            onMouseLeave={onMouseLeave}
            onTouchStart={onTouchStart}
            onTouchMove={onTouchMove}
            onTouchEnd={onTouchEnd}
            className={cn(
              'scrollbar-hide flex gap-6 overflow-x-auto select-none',
              isDragging ? 'cursor-grabbing' : 'cursor-grab',
              !isDragging && 'scroll-smooth',
            )}
            style={{
              scrollSnapType: isDragging ? 'none' : 'x mandatory',
              WebkitOverflowScrolling: 'touch',
              ...(inset && { scrollPaddingLeft: inset }),
            }}
          >
            {inset && (
              <div
                className="flex-shrink-0"
                style={{ width: `calc(${inset} - 24px)` }}
                aria-hidden="true"
              />
            )}
            {React.Children.map(children, (child, index) => {
              const isLast = index === childCount - 1;
              return (
                <div
                  key={index}
                  data-slide
                  className="h-[clamp(600px,80vh,1000px)] flex-shrink-0"
                  style={{
                    width: inset
                      ? isLast
                        ? `calc(100dvw - 2 * ${inset})`
                        : `calc(100dvw - ${inset} - ${peekAmount + 24}px)`
                      : isLast
                        ? '100%'
                        : `calc(100% - ${peekAmount}px)`,
                    scrollSnapAlign: 'start',
                  }}
                >
                  {child}
                </div>
              );
            })}
            {inset && (
              <div
                className="flex-shrink-0"
                style={{ width: `calc(${inset} - 24px)` }}
                aria-hidden="true"
              />
            )}
          </div>

          {/* Navigation arrows */}
          <div
            className="mt-6 flex justify-end gap-2"
            style={inset ? { paddingRight: inset } : undefined}
          >
            <Button
              variant="outline"
              size="icon"
              onClick={() => scroll('left')}
              disabled={!canScrollLeft}
              className="rounded-full"
              aria-label="Previous slide"
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <Button
              variant="outline"
              size="icon"
              onClick={() => scroll('right')}
              disabled={!canScrollRight}
              className="rounded-full"
              aria-label="Next slide"
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
