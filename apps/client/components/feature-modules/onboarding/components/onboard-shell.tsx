'use client';

import { Propless } from '@/lib/interfaces/interface';
import { FC } from 'react';
import { useOnboardStore } from '../hooks/use-onboard-store';
import { OnboardCelebration } from './onboard-celebration';
import { OnboardFormPanel } from './onboard-form-panel';
import { OnboardPreviewPanel } from './onboard-preview-panel';

export const OnboardShell: FC<Propless> = () => {
  const submissionStatus = useOnboardStore((s) => s.submissionStatus);

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
