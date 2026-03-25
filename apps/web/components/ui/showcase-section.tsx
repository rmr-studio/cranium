import { cn } from '@/lib/utils';
import React, { forwardRef, type ReactNode } from 'react';
import { AtmosphericOrbs } from './atmospheric-orbs';
import { BGPattern } from './background/grids';
import { Section } from './section';

/* ── Feature card data ──────────────────────────────────────────── */

export interface FeatureCard {
  title: string;
  description: string;
}

/* ── Showcase section (dark section + heading + feature cards) ──── */

interface ShowcaseSectionProps extends React.HTMLAttributes<HTMLDivElement> {
  heading: ReactNode;
  features: FeatureCard[];
  featureCols?: 2 | 3;
  children: ReactNode;
  className?: string;
  lazyRender?: boolean;
}

export const ShowcaseSection = forwardRef<HTMLDivElement, ShowcaseSectionProps>(
  ({ heading, features, featureCols = 2, children, className, lazyRender, ...props }, ref) => {
    return (
      <Section
        {...props}
        ref={ref}
        className={cn(
          'relative w-full overflow-hidden bg-foreground py-12! md:py-16! lg:px-12! lg:py-20!',
          className,
        )}
        navbarInverse
        lazyRender={lazyRender}
        fill="color-mix(in srgb, var(--background) 15%, transparent)"
        variant="dots"
        size={12}
        mask="none"
        gridClassName="bg-foreground"
      >
        <AtmosphericOrbs variant="outer" />
        <div className="clamp relative">
          {/* ── Top: Heading + Feature Cards ─────────────── */}
          <div className="flex flex-col gap-10 px-4 sm:px-8 lg:flex-row lg:items-end lg:justify-between">
            <div className="max-w-2xl">{heading}</div>

            <div
              className={cn(
                'grid grid-cols-1 gap-px lg:max-w-xl',
                featureCols === 3 ? 'sm:grid-cols-3' : 'sm:grid-cols-2',
              )}
            >
              {features.map((f) => (
                <div key={f.title} className="border-l border-primary-foreground/10 px-5 py-1">
                  <p className="font-semibold text-primary-foreground">{f.title}</p>
                  <p className="mt-1 text-sm leading-snug text-primary-foreground/50">
                    {f.description}
                  </p>
                </div>
              ))}
            </div>
          </div>

          {/* ── Bottom: Inner content card ───────────────── */}
          {children}
        </div>
      </Section>
    );
  },
);

ShowcaseSection.displayName = 'ShowcaseSection';

/* ── Inner card (shared border + gradient + grid pattern wrapper) ── */

interface ShowcaseCardProps {
  children: ReactNode;
  className?: string;
}

export function ShowcaseCard({ children, className }: ShowcaseCardProps) {
  return (
    <div
      className={cn(
        'relative z-10 mt-12 overflow-hidden border-[1.5px] border-primary-foreground/10 bg-primary-foreground/5 shadow-md md:mt-16 lg:rounded-xl lg:backdrop-blur-sm dark:border-primary-foreground/40 dark:bg-background dark:shadow-xl dark:shadow-black/60',
        className,
      )}
    >
      <AtmosphericOrbs variant="inner" className="z-40 opacity-60" />

      <div
        className="absolute inset-0"
        style={{
          background:
            'linear-gradient(to right, var(--inverse-card) 5%, transparent 50%), linear-gradient(to top, var(--inverse-card) 5%, transparent 40%), linear-gradient(to bottom, var(--inverse-card) 5%, transparent 40%)',
        }}
      />
      <BGPattern
        variant="grid"
        size={24}
        fill="color-mix(in srgb, white 5%, transparent)"
        mask="none"
        className="z-10 hidden lg:block"
        style={{
          maskImage:
            'radial-gradient(ellipse at center, black 30%, transparent 75%), linear-gradient(to bottom, transparent 10%, black 30%)',
          maskComposite: 'intersect',
          WebkitMaskImage:
            'radial-gradient(ellipse at center, black 30%, transparent 75%), linear-gradient(to bottom, transparent 10%, black 30%)',
          WebkitMaskComposite: 'source-in' as string,
        }}
      />

      <div className="relative z-10 flex flex-col lg:flex-row">{children}</div>
    </div>
  );
}
