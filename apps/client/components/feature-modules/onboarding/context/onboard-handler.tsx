'use client';

import { useProfile } from '@/components/feature-modules/user/hooks/use-profile';
import { FCWC, Propless } from '@/lib/interfaces/interface';
import { OnboardShell } from '@/components/feature-modules/onboarding/components/onboard-shell';
import { OnboardProvider } from '@/components/feature-modules/onboarding/context/onboard-provider';

export const OnboardWrapper: FCWC<Propless> = ({ children }) => {
  const { data: user, isLoading } = useProfile();

  if (isLoading) return <>{children}</>;
  if (user?.onboardingCompletedAt) return <>{children}</>;

  return (
    <OnboardProvider>
      <OnboardShell />
    </OnboardProvider>
  );
};
