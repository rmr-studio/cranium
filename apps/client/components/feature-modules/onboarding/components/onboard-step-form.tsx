'use client';

import { AnimatePresence, motion } from 'motion/react';
import { FC } from 'react';
import { ONBOARD_STEPS } from '@/components/feature-modules/onboarding/config/onboard-steps';
import { useOnboardStepState } from '@/components/feature-modules/onboarding/hooks/use-onboard-store';
import { ProfileStepForm } from '@/components/feature-modules/onboarding/components/forms/profile-step-form';
import { TeamStepForm } from '@/components/feature-modules/onboarding/components/forms/team-step-form';
import { DefinitionsStepForm } from '@/components/feature-modules/onboarding/components/forms/definitions-step-form';
import { ChannelsStepForm } from '@/components/feature-modules/onboarding/components/forms/channels-step-form';
import { WorkspaceStepForm } from '@/components/feature-modules/onboarding/components/forms/workspace-step-form';

const STEP_FORMS: Record<string, React.ComponentType> = {
  profile: ProfileStepForm,
  workspace: WorkspaceStepForm,
  definitions: DefinitionsStepForm,
  channels: ChannelsStepForm,
  team: TeamStepForm,
};

export const OnboardStepForm: FC = () => {
  const { currentStep, direction } = useOnboardStepState();

  const xOffset = direction === 'forward' ? 40 : -40;
  const stepConfig = ONBOARD_STEPS[currentStep];

  if (!stepConfig) return null;

  const StepForm = STEP_FORMS[stepConfig.id];

  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.div
        key={currentStep}
        initial={{ opacity: 0, x: xOffset }}
        animate={{ opacity: 1, x: 0 }}
        exit={{ opacity: 0, x: -xOffset }}
        transition={{ duration: 0.25, ease: [0.4, 0, 0.2, 1] }}
        className="flex h-full min-h-0 flex-col"
      >
        <div className="shrink-0">
          <h2 className="text-heading font-serif text-2xl">{stepConfig.label}</h2>
          <p className="text-muted-foreground mt-1.5 text-sm leading-relaxed">
            {stepConfig.description}
          </p>
        </div>

        <div className="mt-6 min-h-0 flex-1 overflow-y-auto pr-1 pb-2">
          {StepForm ? <StepForm /> : null}
        </div>
      </motion.div>
    </AnimatePresence>
  );
};
