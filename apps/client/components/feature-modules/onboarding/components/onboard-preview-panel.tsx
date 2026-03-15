'use client';

import { ONBOARD_STEPS } from '@/components/feature-modules/onboarding/config/onboard-steps';
import {
  useOnboardStepState,
  useOnboardNavigation,
} from '@/components/feature-modules/onboarding/hooks/use-onboard-store';
import { BGPattern } from '@/components/ui/background/grids';
import { Propless } from '@/lib/interfaces/interface';
import { FC } from 'react';
import { OnboardCameraCanvas } from '@/components/feature-modules/onboarding/components/onboard-camera-canvas';

const DebugStepControls: FC<Propless> = () => {
  const { currentStep } = useOnboardStepState();
  const { goNext, goBack } = useOnboardNavigation();

  return (
    <div className="absolute bottom-4 left-1/2 z-10 flex -translate-x-1/2 items-center gap-2 rounded-lg border border-dashed border-yellow-500/50 bg-black/70 px-3 py-2 text-xs text-white backdrop-blur">
      <span className="text-yellow-400 font-mono">DEBUG</span>
      <button
        onClick={goBack}
        disabled={currentStep === 0}
        className="rounded bg-white/10 px-2 py-1 hover:bg-white/20 disabled:opacity-30"
      >
        ← Back
      </button>
      <span className="font-mono">
        {currentStep + 1}/{ONBOARD_STEPS.length} — {ONBOARD_STEPS[currentStep].label}
      </span>
      <button
        onClick={goNext}
        disabled={currentStep === ONBOARD_STEPS.length - 1}
        className="rounded bg-white/10 px-2 py-1 hover:bg-white/20 disabled:opacity-30"
      >
        Next →
      </button>
    </div>
  );
};

export const OnboardPreviewPanel: FC<Propless> = () => {
  return (
    <div className="relative h-full w-full">
      <BGPattern
        variant="dots"
        size={20}
        fill="var(--muted-foreground)"
        className="opacity-15"
        mask="none"
      />
      <OnboardCameraCanvas />
      {process.env.NODE_ENV === 'development' && <DebugStepControls />}
    </div>
  );
};
