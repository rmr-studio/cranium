'use client';

import { DiagonalReel } from '@/components/feature-modules/landing/features/components/diagonal-reel';
import { MockActionMemory } from '@/components/feature-modules/landing/features/components/diagrams/action-memory';
import { MockClaudeMcp } from '@/components/feature-modules/landing/features/components/diagrams/claude-mcp';
import { MockCustomerQuery } from '@/components/feature-modules/landing/features/components/diagrams/customer-query';
import { MockEntityWiki } from '@/components/feature-modules/landing/features/components/diagrams/entity-wiki';
import { Dither } from '@/components/ui/dither';
import { Section } from '@/components/ui/section';
import { cdnImageLoader } from '@/lib/cdn-image-loader';
import Image from 'next/image';
import { useRef } from 'react';
import { MockActionAutomation } from './diagrams/action-automation';
import { FeatureCardProps } from './reel-card';

const cards: FeatureCardProps[] = [
  {
    title: 'The Central Command',
    description: `No longer just a row in a spreadsheet or a message in a group-chat. Every customer, order, signal and interaction is connected to the whole picture.`,
    preview: <MockEntityWiki />,
    integrations: ['Shopify', 'Stripe', 'Intercom'],
  },

  {
    title: 'Escape Spreadsheet Hell',
    description:
      'No more manual exports, messy formulas or outdated documents. Upload your data through the UI or your favourite terminal, and let Riven’s agents analyze, connect the dots, and give you the insights you actually need to grow.',
    preview: <MockClaudeMcp />,
    integrations: ['Shopify', 'Stripe', 'Intercom'],
  },
  {
    title: 'The Signal Spotter',
    description:
      'Riven doesn’t just connect the dots, it takes action on them. With native automations and API access, you can close the loop between insights and action. It’s the difference between a teammate who actually gets things done, and one who just talks about it in meetings.',
    preview: <MockActionAutomation />,
    integrations: ['Shopify', 'Stripe', 'Intercom'],
  },
  {
    title: 'The brains and the brawn',
    description:
      'Once Riven understands the connections in your data. Getting the answers and closing the loop between insights and action becomes second nature. Answering the questions of “what” and “why”, and also taking care of the “how” ',
    preview: <MockCustomerQuery />,
    integrations: ['Shopify', 'Stripe', 'Intercom'],
  },
  {
    title: 'We remember. Do you?',
    preview: <MockActionMemory />,
    description: `Riven learns from every interaction and remembers everything, but will never hallucinate what it does not know. It’s the difference between a teammate who actually knows their stuff, and one who just sounds good in meetings.`,
    integrations: ['Shopify', 'Stripe', 'Intercom'],
  },

  {
    title: 'The secure platform',
    description: `Feature controls, audit logs, and more. Gain complete oversight and control over all layers. From data collection to AI agent actions, know exactly what's happening, why and who. Your data, your rules, your security.`,
    preview: (
      <div className="relative h-75 xl:h-100">
        <Image
          loader={cdnImageLoader}
          src={'images/landing/stack-layers.webp'}
          alt="Data Security Layers"
          fill
          className="object-contain"
          aria-hidden="true"
        />
      </div>
    ),
  },
];

export function Features() {
  const sectionRef = useRef<HTMLDivElement>(null);
  return (
    <>
      <Section
        id="cross-domain-intelligence"
        navbarInverse
        size={24}
        className="isolate z-50 mx-0! mt-[40rem] h-full bg-foreground px-0!"
      >
        <section
          data-navbar-inverse
          className="absolute inset-x-0 -top-[40rem] z-30 h-[60rem]"
          aria-hidden
          ref={sectionRef}
        >
          <Dither
            sectionRef={sectionRef}
            fillColor="oklch(0.145 0 0)"
            pattern="noise"
            seed={7}
            direction="bottom-left"
            startWeight={-0.5}
          />
        </section>
        <section className="z-60 mx-auto h-full lg:max-w-[min(90dvw,var(--breakpoint-3xl))]">
          <div className="relative z-60 mb-10 px-4 text-white mix-blend-difference sm:px-8 md:px-12">
            <h2 className="font-bit text-2xl leading-none font-semibold tracking-normal sm:text-4xl md:text-5xl lg:text-6xl">
              Powering Proactive E-Commerce Operations
            </h2>
            <p className="mt-4 max-w-3xl font-display text-base leading-none tracking-tighter">
              Shopify is the heart of your business. Riven is the brain.
            </p>
          </div>
          <div className="border-y border-y-white/20">
            <DiagonalReel cards={cards} />
          </div>
        </section>
      </Section>
    </>
  );
}
