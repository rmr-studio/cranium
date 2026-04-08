'use client';

import { Section } from '@/components/ui/section';
import { SectionDivider } from '@/components/ui/section-divider';
import { ShaderContainer, ThemeStaticImages } from '@/components/ui/shader-container';
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
  const dashboardShaders = {
    light: {
      base: '#9e4a5c',
      colors: ['#1a6080', '#1e1218', '#c4a882'] as [string, string, string],
    },
    dark: {
      base: '#8dbaa4',
      colors: ['#0f3d5c', '#1a2a3f', '#0d1f2d'] as [string, string, string],
    },
  };

  const gradients: ThemeStaticImages = {
    light: 'images/texture/static-gradient-4.webp',
    dark: 'images/texture/static-gradient-4.webp',
  };

  return (
    <Section id="features" size={24} className="mx-0! px-0!">
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
          <p className="mt-4 max-w-2xl text-sm leading-relaxed text-content/90 md:text-base">
            Get instant answers, uncover trends, and set intelligent rules that act on your behalf —
            all in plain English. No SQL, no code, no waiting.
          </p>
        </div>
      </div>

      <div className="mt-40 w-full">
        <ShaderContainer
          staticImages={gradients}
          shaders={dashboardShaders}
          className="relative z-30 mx-0! w-full overflow-visible rounded-none border-none! px-0! py-0! shadow-lg shadow-foreground/40 3xl:rounded-l-lg dark:shadow-none"
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
              className="absolute! -top-14 h-32 sm:left-16"
            />
            <TypewriterPrompt
              prompts={RULE_PROMPTS}
              label="Automate"
              startDelay={1000}
              className="absolute! -bottom-18 h-40 sm:right-24 sm:-bottom-14 sm:h-32"
            />
          </div>
        </ShaderContainer>
      </div>
    </Section>
  );
};
