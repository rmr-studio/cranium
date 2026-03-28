'use client';

import { OkButton } from '@/components/feature-modules/waitlist/components/ok-button';
import { INPUT_CLASS } from '@/components/feature-modules/waitlist/config/steps';
import { cn } from '@/lib/utils';
import { Check } from 'lucide-react';
import { motion } from 'motion/react';

export const PAIN_POINTS_CONFIG = {
  title: 'What are your biggest operational pain points?',
  instruction: 'Choose up to 3',
  maxSelections: 3,
  options: [
    { key: 'A', label: 'Jumping between tools to make decisions' },
    { key: 'B', label: "Can't trace churn back to acquisition channel" },
    { key: 'C', label: 'Manual reporting in spreadsheets' },
    { key: 'D', label: 'No single view of the customer lifecycle' },
    { key: 'E', label: "Tools don't talk to each other" },
    { key: 'F', label: 'Shipping features/launching products without concrete demand' },
  ],
};

export function PainPointsStep({
  selectedPainPoints,
  onToggle,
  otherText,
  onOtherChange,
  onNext,
  error,
}: {
  selectedPainPoints: string[];
  onToggle: (label: string) => void;
  otherText: string;
  onOtherChange: (value: string) => void;
  onNext: () => void;
  error?: string;
}) {
  const atLimit = selectedPainPoints.length >= PAIN_POINTS_CONFIG.maxSelections;

  return (
    <div className="py-8">
      <div className="flex items-start gap-3">
        <h3 className="text-xl leading-snug font-medium md:text-2xl">
          {PAIN_POINTS_CONFIG.title}
          <span className="text-destructive">*</span>
        </h3>
      </div>
      <p className="mt-6 text-sm text-muted-foreground">
        {PAIN_POINTS_CONFIG.instruction}
        <span className="ml-2 text-xs text-muted-foreground/60">
          ({selectedPainPoints.length}/{PAIN_POINTS_CONFIG.maxSelections})
        </span>
      </p>

      <div className="mt-4 space-y-2.5">
        {PAIN_POINTS_CONFIG.options.map((option, i) => {
          const selected = selectedPainPoints.includes(option.label);
          const disabled = atLimit && !selected;

          return (
            <motion.div
              key={option.key}
              initial={{ opacity: 0, x: -10 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.08 + i * 0.04, duration: 0.25 }}
            >
              <button
                type="button"
                onClick={() => !disabled && onToggle(option.label)}
                disabled={disabled}
                className={cn(
                  'group flex w-full cursor-pointer items-center gap-3 rounded-xl border px-4 py-3 text-left transition-all duration-200',
                  selected
                    ? 'border-foreground/25 bg-foreground/10'
                    : 'border-foreground/10 bg-foreground/[0.03] hover:border-foreground/15 hover:bg-foreground/[0.07]',
                  disabled && 'cursor-not-allowed opacity-40',
                )}
              >
                <span
                  className={cn(
                    'flex h-6 w-6 shrink-0 items-center justify-center rounded-xl text-[11px] font-semibold transition-colors',
                    selected
                      ? 'bg-foreground/20 text-foreground'
                      : 'bg-foreground/8 text-muted-foreground group-hover:bg-foreground/12',
                  )}
                >
                  {option.key}
                </span>
                <span className="text-sm font-medium">{option.label}</span>
                {selected && (
                  <motion.span initial={{ scale: 0 }} animate={{ scale: 1 }} className="ml-auto">
                    <Check className="h-3.5 w-3.5 text-foreground/60" />
                  </motion.span>
                )}
              </button>
            </motion.div>
          );
        })}
      </div>

      <div className="mt-6">
        <label className="text-sm text-muted-foreground">
          Anything else?
          <span className="ml-1 text-xs text-muted-foreground/60">(optional)</span>
        </label>
        <input
          type="text"
          value={otherText}
          onChange={(e) => onOtherChange(e.target.value)}
          placeholder="Other pain points..."
          className={cn(INPUT_CLASS, 'mt-2')}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              onNext();
            }
          }}
        />
      </div>

      {error && <p className="mt-3 text-xs text-destructive">{error}</p>}
      <div className="mt-6 flex items-center justify-end">
        <OkButton onClick={onNext} disabled={selectedPainPoints.length === 0} />
      </div>
    </div>
  );
}
