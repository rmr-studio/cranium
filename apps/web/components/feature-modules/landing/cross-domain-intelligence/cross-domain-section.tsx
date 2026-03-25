import { Section } from '@/components/ui/section';
import { SectionDivider } from '@/components/ui/section-divider';
import { CrossDomainCarousel } from '@/components/feature-modules/landing/cross-domain-intelligence/cross-domain-carousel';

export function CrossDomainIntelligence() {
  return (
    <Section id="cross-domain-intelligence" size={24} lazyRender>
      <div className="clamp">
        <div className="space-y-10 md:space-y-14">
          <SectionDivider>Cross Domain Intelligence</SectionDivider>

          <div className="max-w-3xl px-4 sm:px-8">
            <h2 className="font-sans text-3xl leading-none tracking-tighter text-primary md:text-4xl lg:text-5xl">
              Insights that{' '}
              <span className="font-serif font-normal italic">no single tool could see.</span>
            </h2>
            <p className="mt-4 text-sm leading-none text-content/90 md:text-base">
              Riven connects the dots across every data source you use — surfacing patterns, risks,
              and opportunities that only emerge when your entire business is understood as one
              system.
            </p>
          </div>
        </div>

        <div className="mt-10 md:mt-14">
          <CrossDomainCarousel />
        </div>
      </div>
    </Section>
  );
}
