'use client';

import { Section } from '@/components/ui/section';
import { SectionDivider } from '@/components/ui/section-divider';
import { ShaderContainer } from '@/components/ui/shader-container';
import { FeatureCard } from '@/components/ui/showcase-section';
import { cdnImageLoader } from '@/lib/cdn-image-loader';
import { cn } from '@/lib/utils';
import Image from 'next/image';
import { FC } from 'react';

// ── Main Export ──────────────────────────────────────────────────────

export const LayersSection: FC = () => {
  const FEATURES: FeatureCard[] = [
    {
      title: 'Usage monitoring',
      description:
        'Track organization-wide credit usage in real time. Implement budget and quota controls to avoid surprises.',
    },
    {
      title: 'Centralized access controls',
      description:
        'Set up your teams for automation success by ensuring the right people have access to the right tools at the right time.',
    },
    {
      title: 'Human In The Loop',
      description:
        'Never lose control of whats important to your business. Get notified of important events and approve critical actions before they happen.',
    },
  ];

  return (
    <Section id="features" size={24} className="mx-0! px-0!">
      <div className="relative z-10">
        <SectionDivider>Data Security</SectionDivider>

        <div className="mt-10 px-8 sm:px-16">
          <h2 className="font-serif text-3xl leading-none tracking-tighter md:text-4xl lg:text-6xl">
            One (Secure) Platform.
          </h2>
          <p className="mt-4 max-w-3xl text-sm leading-relaxed text-content/90 md:text-base">
            Feature controls, audit logs, and more. Gain complete oversight and control over all
            layers. From data collection to AI agent actions, know exactly what's happening, why and
            who. Your data, your rules, your security.
          </p>
        </div>

        <ShaderContainer
          className="relative mx-0! rounded-none border p-0! shadow-lg 3xl:rounded-lg!"
          staticOnly
          staticImage="images/landing/background-gradient-3.webp"
        >
          <div className="pointer-events-none absolute inset-0 z-10 opacity-60 shadow-[inset_20px_0_40px_rgba(0,0,0,0.5),inset_-20px_0_40px_rgba(0,0,0,0.5)] md:shadow-[inset_32px_0_60px_rgba(0,0,0,0.55),inset_-32px_0_60px_rgba(0,0,0,0.25)]" />
          <div className="h-60 sm:h-120 md:h-160 lg:h-200">
            <Image
              loader={cdnImageLoader}
              src={'images/landing/stack-layers.webp'}
              alt="Data Security Layers"
              fill
              className="object-contain"
              aria-hidden="true"
            />
          </div>
        </ShaderContainer>

        <div className={cn('mt-10 ml-auto grid grid-cols-1 gap-px sm:grid-cols-3')}>
          {FEATURES.map((f) => (
            <div key={f.title} className="border-l px-5 py-1">
              <p className="font-semibold">{f.title}</p>
              <p className="mt-1 text-sm leading-snug text-content/70">{f.description}</p>
            </div>
          ))}
        </div>
      </div>
    </Section>
  );
};
