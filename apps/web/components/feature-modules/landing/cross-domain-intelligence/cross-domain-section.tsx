import { CrossDomainCarousel } from '@/components/feature-modules/landing/cross-domain-intelligence/cross-domain-carousel';
import { Section } from '@/components/ui/section';
import { SectionDivider } from '@/components/ui/section-divider';
import { ShaderContainer, ThemeStaticImages } from '@/components/ui/shader-container';

export function CrossDomainIntelligence() {
  const dashboardShaders = {
    light: {
      base: '#9e4a5c',
      colors: ['#1a6080', '#1e1218', '#c4a882'] as [string, string, string],
    },
    dark: {
      base: '#8dbaa4',
      colors: ['#0f3d5c', '#1a2a3f', '#0d1f2d'] as [string, string, string],
    },
    amber: {
      base: '#0a0d12',
      colors: ['#2a6878', '#0d1f2d', '#7ab0a8'] as [string, string, string],
    },
  };

  const gradients: ThemeStaticImages = {
    light: 'images/texture/static-gradient-4.webp',
    dark: 'images/texture/static-gradient-4.webp',
    amber: 'images/texture/static-gradient-4.webp',
  };

  return (
    <Section id="cross-domain-intelligence" size={24} className="mx-0! px-0!">
      <div className="clamp">
        <div className="space-y-10 px-4 sm:px-8 md:space-y-14 md:px-12">
          <SectionDivider>Cross Domain Intelligence</SectionDivider>

          <div className="max-w-3xl px-4 sm:px-8">
            <h2 className="font-sans text-3xl leading-none tracking-tighter text-primary md:text-4xl lg:text-5xl">
              Insights that{' '}
              <span className="font-serif font-normal italic">no single tool could see.</span>
            </h2>
            <p className="mt-4 text-sm leading-none text-content/90 md:text-base">
              Riven connects the dots across every data source you use. Presenting the patterns,
              risks, and opportunities that only emerge when your entire business is understood as
              one system.
            </p>
          </div>
        </div>

        <div className="mt-10 md:mt-14">
          <ShaderContainer
            staticImages={gradients}
            shaders={dashboardShaders}
            className="relative z-30 mx-0! w-full rounded-none border-none! px-0!"
          >
            <CrossDomainCarousel />
          </ShaderContainer>
        </div>
      </div>
    </Section>
  );
}
