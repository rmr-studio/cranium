'use client';

import { Propless } from '@/lib/interfaces/interface';
import { Loader2 } from 'lucide-react';
import { motion } from 'motion/react';
import { FC } from 'react';
import { ONBOARD_STEPS } from '../config/onboard-steps';
import { useCompleteOnboardingMutation } from '../hooks/mutation/use-complete-onboarding-mutation';
import { useOnboardStore } from '../hooks/use-onboard-store';
import { OnboardNavControls } from './onboard-nav-controls';
import { OnboardStepForm } from './onboard-step-form';

const OnboardProgress: FC = () => {
  const currentStep = useOnboardStore((s) => s.currentStep);

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-baseline justify-between">
        <span className="text-muted-foreground text-xs font-medium tracking-wide uppercase">
          Step {currentStep + 1} of {ONBOARD_STEPS.length}
        </span>
        <span className="text-muted-foreground/60 text-xs">
          {ONBOARD_STEPS[currentStep].label}
        </span>
      </div>

      {/* Segmented progress bar */}
      <div className="flex gap-1">
        {ONBOARD_STEPS.map((step, index) => (
          <div
            key={step.id}
            className="h-1 flex-1 rounded-full transition-colors duration-300"
            style={{
              backgroundColor:
                index <= currentStep
                  ? 'var(--primary)'
                  : 'var(--border)',
            }}
          />
        ))}
      </div>
    </div>
  );
};

export const OnboardFormPanel: FC<Propless> = () => {
  const submissionStatus = useOnboardStore((s) => s.submissionStatus);
  const setSubmissionStatus = useOnboardStore((s) => s.setSubmissionStatus);
  const mutation = useCompleteOnboardingMutation();

  return (
    <div className="flex h-full flex-col px-10 py-8">
      {/* Logo — always visible */}
      <div className="shrink-0">
        <span className="font-display text-lg tracking-tight">Riven</span>
      </div>

      {submissionStatus === 'idle' && (
        <>
          {/* Progress indicator */}
          <div className="mt-10 shrink-0">
            <OnboardProgress />
          </div>

          {/* Step content area — takes the majority of the panel */}
          <div className="mt-8 flex min-h-0 flex-1 flex-col">
            <OnboardStepForm />
          </div>

          {/* Navigation controls pinned at bottom */}
          <div className="shrink-0 pt-6">
            <OnboardNavControls />
          </div>
        </>
      )}

      {submissionStatus === 'loading' && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.2 }}
          className="flex flex-1 flex-col items-center justify-center gap-4"
        >
          <Loader2 className="text-primary h-10 w-10 animate-spin" />
          <p className="text-muted-foreground text-sm font-medium">
            Setting up your workspace...
          </p>
        </motion.div>
      )}

      {submissionStatus === 'error' && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.2 }}
          className="flex flex-1 flex-col items-center justify-center gap-6"
        >
          <div className="flex flex-col items-center gap-2 text-center">
            <p className="text-foreground font-medium">Something went wrong</p>
            <p className="text-muted-foreground text-sm">
              Something went wrong while setting up your workspace.
            </p>
          </div>
          <div className="flex gap-3">
            <button
              type="button"
              className="text-muted-foreground hover:text-foreground text-sm transition-colors"
              onClick={() => setSubmissionStatus('idle')}
            >
              Go back
            </button>
            <button
              type="button"
              className="bg-primary text-primary-foreground hover:bg-primary/90 rounded-md px-4 py-2 text-sm font-medium transition-colors"
              onClick={() => {
                setSubmissionStatus('idle');
                mutation.mutate();
              }}
            >
              Try again
            </button>
          </div>
        </motion.div>
      )}

    </div>
  );
};
