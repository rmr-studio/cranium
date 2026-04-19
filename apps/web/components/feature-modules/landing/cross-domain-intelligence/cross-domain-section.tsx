import { CrossDomainCarousel } from '@/components/feature-modules/landing/cross-domain-intelligence/cross-domain-carousel';
import { Section } from '@/components/ui/section';
import { SectionDivider } from '@/components/ui/section-divider';
import { ShaderContainer, type ShaderColors } from '@/components/ui/shader-container';

export function CrossDomainIntelligence() {
  const dashboardShaders: ShaderColors = {
    base: '#9e4a5c',
    colors: ['#1a6080', '#1e1218', '#c4a882'],
  };

  const gradient = 'images/texture/static-gradient-4.webp';

  return (
    <Section id="cross-domain-intelligence" size={24} className="mx-0! px-0!">
      <div className="space-y-10 px-4 sm:px-8 md:space-y-14 md:px-12">
        <SectionDivider>Cross Domain Intelligence</SectionDivider>

        <div className="px-4 sm:px-8">
          <h2 className="font-serif text-3xl leading-none tracking-tighter text-primary md:text-4xl lg:text-6xl">
            Insights that no single tool could see.
          </h2>
          <p className="mt-4 max-w-3xl text-sm leading-none text-content/90 md:text-base">
            Your CRM sees deals. Stripe sees payments. Intercom sees tickets. Riven sees the
            connection, and what to do next.
          </p>
        </div>
      </div>

      <div className="mt-10 w-full md:mt-14">
        <ShaderContainer
          staticImage={gradient}
          shaders={dashboardShaders}
          className=":shadow-none relative z-30 mx-0! w-full rounded-none border-none! px-0! py-0! shadow-lg shadow-foreground/40 3xl:rounded-l-lg"
        >
          <div className="pointer-events-none absolute inset-y-0 left-0 z-10 w-24 bg-gradient-to-r from-black/60 via-black/25 to-transparent md:w-40" />
          <div className="pointer-events-none absolute inset-y-0 right-0 z-10 w-24 bg-gradient-to-l from-black/60 via-black/25 to-transparent md:w-40" />
          <div className="pointer-events-none absolute inset-0 z-10 opacity-60 shadow-[inset_20px_0_40px_rgba(0,0,0,0.5),inset_-20px_0_40px_rgba(0,0,0,0.5)] md:shadow-[inset_32px_0_60px_rgba(0,0,0,0.55),inset_-32px_0_60px_rgba(0,0,0,0.25)]" />
          <CrossDomainCarousel />
        </ShaderContainer>
      </div>
    </Section>
  );
}
