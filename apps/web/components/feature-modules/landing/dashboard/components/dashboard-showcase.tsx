'use client';

import { SectionDivider } from '@/components/ui/section-divider';
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
};

const FEATURES: FeatureCard[] = [
  {
    title: 'Live Patterns',
    description:
      'See which cohorts retain best and which channels produce them, the moment patterns emerge',
  },
  {
    title: 'One connected view',
    description:
      'Unified lifecycle data from your CRM, product, billing, support and marketing tools, all read and surfaced in one place',
  },
  {
    title: 'Morning moves',
    description:
      'Wake up to the shifts that happened overnight, already sorted and surfaced for you to act on',
  },
];

export function DashboardShowcase() {
  const gradients: ThemeStaticImages = {
    light: 'images/texture/static-gradient-3.webp',
    dark: 'images/texture/static-gradient-3.webp',
  };

  return (
    <>
      <SectionDivider className="lg:mx-12">Real-time Insights</SectionDivider>
      <ShowcaseSection
        heading={
          <h2 className="mt-4 font-serif text-3xl md:text-5xl lg:text-6xl">
            What channels produce customers who stay?
          </h2>
        }
        features={FEATURES}
        featureCols={3}
      >
        <div className="flex flex-col-reverse md:flex-col">
          <section className="mx-auto mt-10 flex w-full flex-col items-center">
            <h3 className="mt-8 font-serif text-2xl sm:text-4xl md:text-5xl">
              Keep powerful insights in sight.
            </h3>

            <p className="mx-4 mt-4 max-w-3xl text-center text-base leading-tight text-content">
              Riven explores and surfaces patterns, trends and insights hidden deep in your data.
              From channel performance to cohort health to churn risks, it has never been easier to
              understand your customers from a singular glance
            </p>
          </section>

          <ShaderContainer
            staticImages={gradients}
            shaders={dashboardShaders}
            className="relative z-30 w-full p-0! shadow-lg shadow-foreground/40 lg:rounded-lg dark:shadow-none"
          >
            <section className="lg:p-12">
              <div className="pointer-events-none absolute inset-y-0 left-0 z-10 hidden w-24 bg-gradient-to-r from-black/60 via-black/25 to-transparent md:w-40 3xl:block" />
              <div className="pointer-events-none absolute inset-y-0 right-0 z-10 w-24 bg-gradient-to-l from-black/60 via-black/25 to-transparent md:w-40" />
              <div className="pointer-events-none absolute inset-0 z-10 opacity-60 shadow-[inset_20px_0_40px_rgba(0,0,0,0.5),inset_-20px_0_40px_rgba(0,0,0,0.5)] md:shadow-[inset_32px_0_60px_rgba(0,0,0,0.55),inset_-32px_0_60px_rgba(0,0,0,0.25)]" />
              <MockDashboard />
            </section>
          </ShaderContainer>
        </div>
      </ShowcaseSection>
    </>
  );
}
