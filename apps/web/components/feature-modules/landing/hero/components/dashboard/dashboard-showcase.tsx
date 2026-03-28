'use client';

import { ShaderContainer, ThemeStaticImages } from '@/components/ui/shader-container';
import { ShowcaseSection, type FeatureCard } from '@/components/ui/showcase-section';
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
        <h2 className="mt-4 text-3xl md:text-4xl lg:text-5xl">
          What channels produce{' '}
          <span className="font-serif font-normal italic">customers who stay?</span>
        </h2>
      }
      features={FEATURES}
      featureCols={3}
    >
      <div className="flex flex-col-reverse md:flex-col">
        <section className="mx-auto mt-10 flex w-full flex-col items-center">
          <h3 className="mt-8 font-sans text-2xl sm:text-4xl">
            Keep powerful insights <span className="font-serif font-normal italic">in sight.</span>
          </h3>

          <p className="mx-4 mt-4 max-w-3xl text-center text-base leading-tight text-content">
            Riven explores and surfaces patterns, trends and insights hidden deep in your data. From
            channel performance to cohort health to churn risks, it has never been easier to
            understand your customers from a singular glance
          </p>
        </section>

        <ShaderContainer
          staticImages={gradients}
          shaders={dashboardShaders}
          className="relative z-30 w-full"
        >
          <MockDashboard />
        </ShaderContainer>
      </div>
    </ShowcaseSection>
  );
}
