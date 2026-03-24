import { AtmosphericOrbs } from '@/components/ui/atmospheric-orbs';
import { BGPattern } from '@/components/ui/background/grids';
import { Section } from '@/components/ui/section';
import { DailyActionAccordion } from './action-accordion';

const FEATURES = [
  {
    title: 'Cross-domain view',
    description: 'Unified lifecycle data from every connected tool in one glance',
  },
  {
    title: 'AI-powered briefs',
    description: 'Your morning brief learns what matters most to your business',
  },
];

export const DailyActions = () => {
  return (
    <Section
      className="relative w-full overflow-hidden bg-foreground py-12! md:py-16! lg:px-12! lg:py-20!"
      navbarInverse
      fill="color-mix(in srgb, var(--background) 15%, transparent)"
      variant="dots"
      size={12}
      mask="none"
      gridClassName="bg-foreground"
    >
      <AtmosphericOrbs variant="outer" />
      <div className="clamp relative">
        {/* ── Top: Heading + Feature Cards ─────────────────────────── */}
        <div className="flex flex-col gap-10 lg:flex-row lg:items-end lg:justify-between">
          {/* Left: Label + heading */}
          <div className="max-w-2xl">
            <h2 className="mx-4 mt-4 text-3xl leading-tight -tracking-[0.02em] text-primary-foreground md:mx-0 md:text-4xl lg:text-5xl">
              <span className="font-sans font-semibold">More Results. </span>
              <span className="font-serif font-normal italic">Less Tabs</span>
            </h2>
          </div>

          {/* Right: 3 feature cards */}
          <div className="grid grid-cols-1 gap-px sm:grid-cols-2 lg:max-w-xl">
            {FEATURES.map((feature) => (
              <div key={feature.title} className="border-l border-primary-foreground/10 px-5 py-1">
                <p className="text-sm font-semibold text-primary-foreground">{feature.title}</p>
                <p className="mt-1 text-xs leading-relaxed text-primary-foreground/50">
                  {feature.description}
                </p>
              </div>
            ))}
          </div>
        </div>

        {/* ── Bottom: Content Card ────────────────────────────────── */}
        <div className="relative z-10 mt-12 overflow-hidden border border-primary-foreground/10 bg-primary-foreground/5 shadow-md md:mt-16 lg:rounded-xl dark:border-primary-foreground/25 dark:bg-background/80 dark:shadow-xl dark:shadow-black/60">
          <AtmosphericOrbs variant="inner" className="z-40 opacity-60" />

          <div className="z-10 flex flex-col backdrop-blur-xs lg:flex-row">
            {/* Left: Copy + CTA */}
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
              fill="color-mix(in srgb, var(--background) 15%, transparent)"
              mask="none"
              className="z-2"
              style={{
                maskImage:
                  'radial-gradient(ellipse at center, black 30%, transparent 75%), linear-gradient(to bottom, transparent 10%, black 30%)',
                maskComposite: 'intersect',
                WebkitMaskImage:
                  'radial-gradient(ellipse at center, black 30%, transparent 75%), linear-gradient(to bottom, transparent 10%, black 30%)',
                WebkitMaskComposite: 'source-in' as string,
              }}
            />

            <DailyActionAccordion />
          </div>
        </div>
      </div>
    </Section>
  );
};
