'use client';

import { useTypewriterCycle } from '@/components/feature-modules/landing/knowledge-rule-base/hooks/use-typewriter-cycle';
import { ClassNameProps, cn } from '@riven/utils';
import { FC } from 'react';

// ── Prompt Data ─────────────────────────────────────────────────────

export const QUERY_PROMPTS = [
  'What were our top growth trends this week, and what should we do next?',
  'Which customers have the highest lifetime value across all channels?',
  'Show me the accounts most likely to upgrade this quarter.',
  'What is driving churn in our enterprise segment right now?',
];

export const RULE_PROMPTS = [
  "When my best cohort's repeat purchase rate drops below 40%, flag it.",
  "If a customer opens 3+ emails but hasn't purchased in 30 days, send a win-back offer.",
  "Alert the team when any enterprise account's usage drops 25% week-over-week.",
  'When a trial user completes onboarding, auto-assign them to the nurture sequence.',
];

// ── Component ───────────────────────────────────────────────────────

interface TypewriterPromptProps extends ClassNameProps {
  prompts: string[];
  label: string;
  startDelay?: number;
}

export const TypewriterPrompt: FC<TypewriterPromptProps> = ({
  prompts,
  label,
  className,
  startDelay = 0,
}) => {
  const { text, phase, selected } = useTypewriterCycle(prompts, startDelay);

  return (
    <div
      className={cn(
        'group relative z-40 mx-4 max-w-xs min-w-xs overflow-hidden rounded-xl bg-amber-50 px-5 py-4 shadow-lg backdrop-blur-xl sm:mx-0 sm:h-32 sm:w-full sm:max-w-2xl',
        className,
      )}
    >
      {/* Glow aura */}
      <div className="pointer-events-none absolute -inset-1 -z-10 rounded-xl bg-gradient-to-r from-[var(--cta-g1)]/20 via-[var(--cta-g2)]/25 to-[var(--cta-g3)]/20 opacity-60 blur-md" />

      {/* Subtle top highlight */}
      <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-white/20 to-transparent" />

      {/* Label */}
      <span className="mb-2 inline-block font-display text-lg font-bold tracking-tight uppercase sm:text-2xl">
        {label}
      </span>

      {/* Text area */}
      <div className="min-h-[3rem]">
        <p className="text-base leading-relaxed text-content md:text-lg">
          <span
            className={
              selected ? 'rounded-sm bg-[var(--cta-g2)]/30 box-decoration-clone py-px' : undefined
            }
          >
            {text}
          </span>
          {(phase === 'typing' || phase === 'shown') && (
            <span className="ml-px inline-block h-5 w-0.5 animate-pulse bg-black/70 align-text-bottom" />
          )}
        </p>
      </div>
    </div>
  );
};
