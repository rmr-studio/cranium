'use client';

import { Propless } from '@/lib/interfaces/interface';
import { FC } from 'react';
import { useBundles } from '@/components/feature-modules/onboarding/hooks/query/use-bundles';
import { useOnboardSubmission } from '@/components/feature-modules/onboarding/hooks/use-onboard-store';
import { OnboardCelebration } from '@/components/feature-modules/onboarding/components/onboard-celebration';
import { OnboardFormPanel } from '@/components/feature-modules/onboarding/components/onboard-form-panel';
import { OnboardPreviewPanel } from '@/components/feature-modules/onboarding/components/onboard-preview-panel';

export const OnboardShell: FC<Propless> = () => {
  const { submissionStatus } = useOnboardSubmission();

  // Prefetch bundles/templates so data is cached before the user reaches step 3
  useBundles();

  return (
    <div className="fixed inset-0 z-50 flex">
      {submissionStatus === 'success' ? (
        /* Full-screen celebration takeover — replaces both panels */
        <OnboardCelebration />
      ) : (
        <>
          {/* Left panel — form area (full on mobile, 40% on md+) */}
          <div className="bg-card h-full w-full md:w-2/5">
            <OnboardFormPanel />
          </div>

          {/* Right panel — preview area (hidden on mobile, 60% on md+) */}
          <div className="bg-muted relative hidden h-full w-3/5 overflow-hidden md:block">
            <OnboardPreviewPanel />
          </div>
        </>
      )}
    </div>
  );
};
