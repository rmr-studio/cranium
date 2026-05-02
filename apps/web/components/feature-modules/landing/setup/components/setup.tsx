'use client';

import { BrandIcons, Integration } from '@/components/ui/diagrams/brand-icons';
import { GlowBorder } from '@/components/ui/glow-border';
import { Section } from '@/components/ui/section';
import { cdnImageLoader } from '@/lib/cdn-image-loader';
import { cn } from '@/lib/utils';
import Image from 'next/image';
import { FC } from 'react';

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
            native integrations, or use our API to connect anything else. Watch as Riven learns from
            your data, identifies patterns, and starts making connections you didn't even know
            existed. It's like having a team of analysts working 24/7, but without the overhead.
          </p>
        </div>
      </div>
      <div className="mx-auto grid w-full grid-cols-1 gap-4 px-8 sm:px-12 md:grid-cols-4 lg:gap-6">
        <SetupCard
          className="md:col-span-2"
          integrations={['Slack']}
          title="Add Riven to Slack"
          description="Connect Riven to your Slack workspace and start making connections in your conversations."
        />
        <SetupCard
          className="md:col-span-2"
          integrations={['Shopify', 'Cin7', 'Instagram', 'Klaviyo']}
          title="Connect your Business"
          description="Connect Riven to your business tools to bring in data about your customers, orders, inventory, and more."
        />
        <SetupCard
          className="md:col-span-2 md:col-start-2"
          integrations={['Gmail', 'GoogleSheets', 'GoogleMeet']}
          title="Connect Your Team"
          description="Your team lives in email, spreadsheets, and meetings. Connect Riven to these tools to bring that context into the brain."
        />
      </div>
      <div className="relative ml-auto h-60 w-full border-b border-content/30 sm:h-120 md:h-160 lg:h-200">
        <Image
          loader={cdnImageLoader}
          src={'images/landing/city-graphic-1920w.webp'}
          alt="Data Security Layers"
          fill
          className=""
          aria-hidden="true"
        />
      </div>
    </Section>
  );
};

interface SetupCardProps {
  integrations: Integration[];
  title: string;
  description: string;
  className?: string;
}

export const SetupCard: FC<SetupCardProps> = ({
  integrations,
  title,
  description,
  className,
}) => {
  return (
    <GlowBorder className={cn('w-full', className)}>
      <div className="flex h-full gap-4 rounded-lg bg-card/80 p-6 shadow-lg">
        <div className="grid shrink-0 grid-cols-2 content-start gap-1.5">
          {integrations.map((brand, i) => {
            const Icon = BrandIcons[brand];
            return (
              <div key={i} className="flex size-10 items-center justify-center rounded-md shadow-md">
                <Icon size={28} />
              </div>
            );
          })}
        </div>
        <div className="min-w-0 flex-1">
          <h3 className="font-bit text-lg font-medium">{title}</h3>
          <p className="mt-1 font-display text-sm leading-[1.1] text-muted-foreground">
            {description}
          </p>
        </div>
      </div>
    </GlowBorder>
  );
};
