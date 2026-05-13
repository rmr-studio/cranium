'use client';

import LogoLoop, { type LogoItem } from '@/components/LogoLoop';
import { BrandIcons, Integration } from '@/components/ui/diagrams/brand-icons';
import { Dither } from '@/components/ui/dither';
import { Section } from '@/components/ui/section';
import { cn } from '@/lib/utils';
import { ClassNameProps } from '@cranium/utils';
import { FC, useMemo, useRef } from 'react';
import { TerminalBody, TerminalChrome } from '../../features/components/diagrams/claude-mcp-query';

interface IntegrationCarouselItem {
  brand: Integration;
  title?: string;
  capability: string;
}

const CAROUSEL_ITEMS: IntegrationCarouselItem[] = [
  {
    brand: 'Slack',
    capability:
      'Bring the context of your data into your conversations, and the insights of your conversations, meetings and announcements back into your data.',
  },
  {
    brand: 'Shopify',

    capability:
      'The heart and soul of your e-commerce operations. It sees everything. But Cranium takes it a step further. Connecting the dots between customers, orders, inventory, and acting on those insights',
  },
  {
    brand: 'Gmail',

    capability:
      'Your team’s knowledge, decisions and plans are buried in email. Cranium brings that context into the brain, and surfaces insights and actions right where you work.',
  },
  {
    brand: 'Klaviyo',

    capability:
      'Your marketing automation is only as good as the data and insights behind it. Cranium connects the dots in your data, and gives you a direct line to your customers, so you can send the right message, at the right time, through the right channel.',
  },

  {
    brand: 'Intercom',

    capability:
      'Your support tickets, feedback and complaints are a goldmine of insights. Cranium connects the dots between your customers, their interactions and your operations, so you can turn that feedback into action, and close the loop between support and operations.',
  },
  {
    brand: 'GoogleSheets',
    title: 'Sheets and Documents',

    capability:
      'Spreadsheets are the original no-code tool, but they don’t have to be the end of the line. Cranium connects the dots in your data, and gives you a direct line to your spreadsheets, so you can get the insights you need, without manual exports or messy formulas.',
  },
  {
    brand: 'GoogleMeet',
    title: 'Meetings',

    capability:
      'Meetings are where decisions are made and plans are hatched, but they’re also where insights go to die. Cranium connects the dots between your meetings, their outcomes and your operations, so you can turn those insights into action, and close the loop between meetings and operations.',
  },
  {
    brand: 'Cin7',

    capability:
      'Your inventory and supply chain data is crucial for making informed decisions, but it’s often siloed away from the rest of your operations. Cranium connects the dots between your inventory, supply chain and operations, so you can get a complete picture of your business and make smarter decisions.',
  },
  {
    brand: 'Facebook',
    title: 'Meta Ads',
    capability:
      'Your advertising data is a treasure trove of insights, but it’s often disconnected from the rest of your operations. Cranium connects the dots between your advertising, customers and operations, so you can get a complete picture of your marketing performance and make smarter decisions.',
  },
];

const ITEM_HEIGHT_REM = 9;
const LOOP_SPEED_PX_PER_S = 60;

export const Connections: FC<ClassNameProps> = ({ className }) => {
  const sectionRef = useRef<HTMLElement>(null);

  return (
    <Section
      className={cn(
        'relative w-full overflow-hidden bg-background px-0! pt-32 md:py-44',
        className,
      )}
    >
      <section ref={sectionRef} className="absolute inset-0 z-[60] h-[60rem]">
        <Dither
          sectionRef={sectionRef}
          fillColor="oklch(0.145 0 0)"
          pattern="noise"
          seed={7}
          inverse
          direction="bottom-up"
          startWeight={-0.5}
        />
      </section>

      <div className="mx-auto mt-[20rem] w-full 2xl:max-w-[min(90dvw,var(--breakpoint-3xl))]">
        {/* Heading */}
        <div className="mb-14 px-12 md:mb-20">
          <h2 className="font-bit text-2xl leading-none sm:text-4xl md:text-5xl lg:text-6xl">
            Connect Everything. Lose Nothing
          </h2>
          <p className="mt-4 max-w-4xl font-display text-base leading-none tracking-tighter text-content/90">
            Plug in the systems you already run on, and access Cranium wherever you work. With native
            integrations across all your tools, and an open API, CLI and MCP for anything else,
            Cranium becomes the central nervous system of your operations, connecting everything
            together and surfacing insights and actions right where you need them.
          </p>
        </div>

        <article className="relative flex flex-col border-y border-y-content/30 lg:flex-row">
          <Integrations />
          <Terminal />
        </article>
      </div>
    </Section>
  );
};

const Integrations: FC = () => {
  const logos: LogoItem[] = useMemo(
    () =>
      CAROUSEL_ITEMS.map((item) => ({
        node: <CarouselRow item={item} />,
        ariaLabel: item.title ?? item.brand,
      })),
    [],
  );

  return (
    <section className="relative flex w-full flex-col-reverse lg:flex-col">
      <section className="relative z-10 mx-auto w-full">
        <div
          className="relative w-full overflow-hidden"
          style={{
            height: `${ITEM_HEIGHT_REM * 3}rem`,
            maskImage:
              'linear-gradient(to bottom, transparent 0%, black 18%, black 82%, transparent 100%)',
            WebkitMaskImage:
              'linear-gradient(to bottom, transparent 0%, black 18%, black 82%, transparent 100%)',
          }}
        >
          <LogoLoop
            logos={logos}
            direction="up"
            speed={LOOP_SPEED_PX_PER_S}
            gap={0}
            ariaLabel="Integration list"
          />
        </div>
      </section>
      <div className="border-b border-b-content/30 px-4 pt-2 pb-6 lg:border-b-0">
        <p className="mt-5 mb-3 font-bit text-lg font-medium tracking-tight lg:text-xl">
          Don't change for the brain.
        </p>
        <p className="max-w-md font-display text-sm leading-[1.1] tracking-tighter text-content/70">
          Integrate with your existing tools, analytics and workflows to create a seamless
          experience.
        </p>
      </div>
    </section>
  );
};

const Terminal: FC = () => {
  return (
    <section className="flex w-full flex-col-reverse lg:flex-col">
      <div className="relative flex h-full max-h-[27rem] overflow-hidden">
        <div
          className={cn('relative w-full origin-top-left scale-110 overflow-hidden bg-foreground')}
        >
          <TerminalChrome />
          <TerminalBody />
        </div>
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 z-10"
          style={{
            background:
              'radial-gradient(ellipse 80% 50% at 50% 0%, oklch(0 0 0 / 0.55), transparent 65%)',
          }}
        />
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 z-10"
          style={{
            background:
              'radial-gradient(ellipse 60% 70% at 100% 100%, oklch(0 0 0 / 0.4), transparent 55%)',
          }}
        />
      </div>
      <div className="border-t border-t-content/30 px-4 pt-2 pb-6">
        <p className="mt-5 mb-3 font-bit text-lg font-medium tracking-tight lg:text-xl">
          Access Everywhere
        </p>
        <p className="font-display text-sm leading-[1.1] tracking-tighter text-content/70">
          In the new world of AI and Agents. Cranium is accessible wherever you work, through native
          integrations, an open API, and a MCP that lets you interact with your data and operations
          directly from your terminal.
        </p>
      </div>
    </section>
  );
};

const CarouselRow: FC<{ item: IntegrationCarouselItem }> = ({ item }) => {
  const Icon = BrandIcons[item.brand];
  return (
    <div className="flex h-fit w-full shrink-0 items-center border-t border-t-content/30 p-6">
      <div className="flex h-full w-1/6 items-center justify-center">
        <Icon size={56} />
      </div>
      <div className="min-w-0 flex-1 px-6">
        <p className="font-bit text-base font-medium tracking-tight text-foreground sm:text-lg">
          {item.title ?? item.brand}
        </p>
        <p className="mt-1.5 font-display text-sm leading-[1.15] tracking-tighter text-content/70">
          {item.capability}
        </p>
      </div>
    </div>
  );
};
