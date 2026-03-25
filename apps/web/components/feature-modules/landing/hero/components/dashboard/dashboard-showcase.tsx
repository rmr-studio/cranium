'use client';

import { ShaderContainer, ThemeStaticImages } from '@/components/ui/shader-container';
import { ShowcaseCard, ShowcaseSection, type FeatureCard } from '@/components/ui/showcase-section';
import { useContainerScale } from '@/hooks/use-container-scale';
import { useIsMobile } from '@riven/hooks';
import { MockDashboard } from './mock-dashboard';

const dashboardShaders = {
  light: {
    base: '#4a5e6e',
    colors: ['#1a6080', '#3a8aaa', '#78b8cc'] as [string, string, string],
  },
  dark: {
    base: '#0a0d12',
    colors: ['#0f3d5c', '#1a2a3f', '#0d1f2d'] as [string, string, string],
  },
  amber: {
    base: '#1e2a2d',
    colors: ['#2a6878', '#4a8a8e', '#7ab0a8'] as [string, string, string],
  },
};

const FEATURES: FeatureCard[] = [
  {
    title: 'Real-time insights',
    description: 'Surface churn risks and channel performance the moment patterns emerge',
  },
  {
    title: 'Cross-domain view',
    description: 'Unified lifecycle data from every connected tool in one glance',
  },
  {
    title: 'AI-powered briefs',
    description: 'Your morning brief learns what matters most to your business',
  },
];

const DESKTOP_WIDTH = 1920;
const DESKTOP_HEIGHT = 1260;
const MOBILE_WIDTH = 800;
const MOBILE_HEIGHT = Math.round(DESKTOP_HEIGHT * (MOBILE_WIDTH / DESKTOP_WIDTH));

function DashboardOverlay() {
  const isMobile = useIsMobile();
  const width = isMobile ? MOBILE_WIDTH : DESKTOP_WIDTH;
  const height = isMobile ? MOBILE_HEIGHT : DESKTOP_HEIGHT;
  const { containerRef, scale } = useContainerScale(width);

  return (
    <div className="relative z-30" ref={containerRef}>
      <div
        className="origin-top-left"
        style={{
          width,
          transform: `scale(${scale})`,
          height: height * scale,
        }}
      >
        <MockDashboard />
      </div>
    </div>
  );
}

export function DashboardShowcase() {
  const gradients: ThemeStaticImages = {
    light: 'images/texture/static-gradient-3.webp',
    dark: 'images/texture/static-gradient-3.webp',
    amber: 'images/texture/static-gradient-3.webp',
  };

  return (
    <ShowcaseSection
      id="features"
      heading={
        <h2 className="mt-4 text-3xl text-primary-foreground md:text-4xl lg:text-5xl">
          What channels produce{' '}
          <span className="font-serif font-normal italic">customers who stay?</span>
        </h2>
      }
      features={FEATURES}
      featureCols={3}
    >
      <ShowcaseCard>
        <ShaderContainer
          staticImages={gradients}
          shaders={dashboardShaders}
          className="w-full p-4 lg:m-6 lg:w-3/5"
        >
          <div className="relative z-30 translate-x-24 scale-150 p-4 pt-8 sm:scale-120 sm:p-8 sm:pt-12 md:translate-x-0 md:scale-100">
            <DashboardOverlay />
          </div>
        </ShaderContainer>

        <section className="z-20 flex flex-col px-8 py-6 lg:w-2/5">
          <h3 className="mt-12 font-sans text-2xl text-background sm:text-4xl dark:text-foreground/80">
            Keep powerful insights <span className="font-serif font-normal italic">in sight.</span>
          </h3>

          <p className="mt-4 max-w-lg text-base leading-tight text-primary-foreground/50 md:max-w-md md:text-lg dark:text-content">
            Riven explores and surfaces patterns trends and insights hidden deep in your data. From
            channel performance to cohort health to churn risks. Answer critical questions at a
            glance with the most powerful, connected dashboard you've ever seen.
          </p>
        </section>
      </ShowcaseCard>
    </ShowcaseSection>
  );
}
