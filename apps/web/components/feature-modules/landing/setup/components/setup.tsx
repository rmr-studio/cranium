'use client';

import { BrandIcons, Integration } from '@/components/ui/diagrams/brand-icons';
import { Section } from '@/components/ui/section';
import { cdnImageLoader } from '@/lib/cdn-image-loader';
import { cn } from '@/lib/utils';
import Image from 'next/image';
import { FC } from 'react';

interface SetupItem {
  integrations: Integration[];
  title: string;
  description: string;
}

const items: SetupItem[] = [
  {
    integrations: ['Slack'],
    title: 'Add Cranium to Slack',
    description:
      'Connect Cranium to your Slack workspace and start making connections in your conversations.',
  },
  {
    integrations: ['Shopify', 'Cin7', 'Klaviyo'],
    title: 'Connect your Business',
    description:
      'Connect Cranium to your business tools to bring in data about your customers, orders, inventory, and more.',
  },
  {
    integrations: ['Gmail', 'GoogleSheets', 'GoogleMeet'],
    title: 'Connect Your Team',
    description:
      'Your team lives in email, spreadsheets, and meetings. Connect Cranium to these tools to bring that context into the brain.',
  },
];

export const Setup = () => {
  return (
    <Section
      id="setup"
      size={24}
      mask="none"
      className="mx-auto border-x border-x-content/25 px-0 pb-20 lg:px-0 2xl:max-w-[min(90dvw,var(--breakpoint-3xl))]"
    >
      <div className="relative z-10">
        {/* Heading */}
        <div className="mb-14 px-8 sm:px-12 md:mb-20">
          <h2 className="font-bit text-2xl leading-none sm:text-4xl md:text-5xl lg:text-6xl">
            A city of connections. Built in minutes.
          </h2>
          <p className="mt-4 max-w-3xl font-display text-base leading-none tracking-tighter text-content/90">
            Building a brain has never been easier. Connect your data sources in minutes with our
            native integrations, or use our API to connect anything else. Watch as Cranium learns from
            your data, identifies patterns, and starts making connections you didn't even know
            existed. It's like having a team of analysts working 24/7, but without the overhead.
          </p>
        </div>
      </div>

      {/* Desktop grid */}
      <div className="hidden md:block">
        <div className="overflow-hidden border-y border-y-content/50">
          <div className="grid grid-cols-3">
            {items.map((item, i) => (
              <div
                key={item.title}
                className={cn('p-7 lg:p-8', i > 0 && 'border-l border-content/50')}
              >
                <IntegrationRow integrations={item.integrations} />
                <p className="mt-5 mb-3 font-bit text-lg font-medium tracking-tight lg:text-xl">
                  {item.title}
                </p>
                <p className="font-display text-sm leading-[1.1] tracking-tighter text-content/70">
                  {item.description}
                </p>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Mobile layout */}
      <div className="flex flex-col gap-4 px-8 sm:px-12 md:hidden">
        {items.map((item) => (
          <div key={item.title} className="border-l-2 border-content/50 py-4 pr-3 pl-5">
            <IntegrationRow integrations={item.integrations} />
            <p className="mt-4 font-bit text-base font-medium tracking-tight">{item.title}</p>
            <p className="mt-1.5 font-display text-sm leading-[1.1] tracking-tighter text-content/90">
              {item.description}
            </p>
          </div>
        ))}
      </div>

      <div className="relative mt-20 ml-auto h-60 w-full border-b border-content/30 sm:h-120 md:h-160">
        <Image
          loader={cdnImageLoader}
          src={'images/landing/city-graphic-1920w.webp'}
          alt="Data Security Layers"
          fill
          className="object-contain object-bottom-right"
          aria-hidden="true"
        />
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0"
          style={{
            background:
              'radial-gradient(ellipse 70% 60% at 100% 100%, oklch(0 0 0 / 0.4), transparent 55%)',
          }}
        />
      </div>
    </Section>
  );
};

const IntegrationRow: FC<{ integrations: Integration[] }> = ({ integrations }) => (
  <div className="flex flex-wrap items-center gap-1.5">
    {integrations.map((brand, i) => {
      const Icon = BrandIcons[brand];
      return (
        <div
          key={i}
          className="flex size-9 items-center justify-center rounded-sm bg-card shadow-sm ring-1 ring-foreground/5"
        >
          <Icon size={22} />
        </div>
      );
    })}
  </div>
);
