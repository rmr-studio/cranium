'use client';

import { Section } from '@/components/ui/section';
import { SectionDivider } from '@/components/ui/section-divider';
import { ShaderContainer, type ShaderColors } from '@/components/ui/shader-container';
import { FC } from 'react';

import {
  ROW_1_CARDS,
  ROW_2_CARDS,
  ScrollingRow,
} from '@/components/feature-modules/landing/knowledge-rule-base/components/scrolling-cards';
import {
  QUERY_PROMPTS,
  RULE_PROMPTS,
  TypewriterPrompt,
} from '@/components/feature-modules/landing/knowledge-rule-base/components/typewriter-prompt';

// ── Main Export ──────────────────────────────────────────────────────

export const RuleBaseSection: FC = () => {
  const dashboardShaders: ShaderColors = {
    base: '#fbc4ff',
    colors: ['#6c83ab', '#3b2245'],
  };

  const gradient = 'images/texture/static-gradient-4.webp';

  return (
    <Section id="features" size={24} className="mx-0! overflow-hidden px-0! pb-30">
      <style>{`
        @keyframes scroll-left {
          from { transform: translateX(0); }
          to { transform: translateX(-50%); }
        }
        @keyframes scroll-right {
          from { transform: translateX(-50%); }
          to { transform: translateX(0); }
        }
      `}</style>

      {/* ── Heading ──────────────────────────────────────────── */}
      <div className="relative z-10 px-4 sm:px-8 md:px-12">
        <SectionDivider>Natural Language Rule Engine</SectionDivider>

        <div className="mt-10 px-4 sm:px-8">
          <h2 className="font-serif text-3xl leading-none tracking-tighter md:text-4xl lg:text-6xl">
            Ask anything about your business.
            <br />
            Action anything about your business.
          </h2>
          <p className="mt-4 text-sm leading-relaxed text-content/90 md:text-base">
            Type a question in plain English. Get the answer. Or turn the question into an action
            that runs while you sleep. No SQL, no code, no waiting.
          </p>
        </div>
      </div>

      <div className="mt-40 w-full">
        <ShaderContainer
          staticImage={gradient}
          shaders={dashboardShaders}
          className=":shadow-none relative z-30 mx-0! w-full overflow-visible rounded-none border-none! px-0! py-0! shadow-lg shadow-foreground/40 3xl:rounded-l-lg"
        >
          <div className="pointer-events-none absolute inset-y-0 left-0 z-10 w-24 bg-gradient-to-r from-black/60 via-black/25 to-transparent md:w-40" />
          <div className="pointer-events-none absolute inset-y-0 right-0 z-10 w-24 bg-gradient-to-l from-black/60 via-black/25 to-transparent md:w-40" />
          <div className="pointer-events-none absolute inset-0 z-10 opacity-60 shadow-[inset_20px_0_40px_rgba(0,0,0,0.5),inset_-20px_0_40px_rgba(0,0,0,0.5)] md:shadow-[inset_32px_0_60px_rgba(0,0,0,0.55),inset_-32px_0_60px_rgba(0,0,0,0.25)]" />

          {/* ── Cards + Prompts ─────────────────────────────────── */}
          <div className="py-24">
            {/* Scrolling card rows */}
            <div className="flex flex-col gap-4">
              <ScrollingRow cards={ROW_1_CARDS} direction="left" duration={55} />
              <ScrollingRow cards={ROW_2_CARDS} direction="right" duration={60} />
            </div>

            {/* Animated prompts */}
            <TypewriterPrompt
              prompts={QUERY_PROMPTS}
              label="Query"
              startDelay={500}
              className="absolute! -top-14 right-4 left-4 h-32 lg:right-auto lg:left-16"
            />
            <TypewriterPrompt
              prompts={RULE_PROMPTS}
              label="Automate"
              startDelay={1000}
              className="xs:-bottom-14 absolute! -bottom-18 left-4 h-40 sm:h-32 lg:right-24 lg:left-auto"
            />
          </div>
        </ShaderContainer>
      </div>
    </Section>
  );
};
