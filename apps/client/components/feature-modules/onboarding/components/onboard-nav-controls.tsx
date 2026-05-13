'use client';

import { Button } from '@cranium/ui/button';
import { FC } from 'react';
import { ONBOARD_STEPS } from '@/components/feature-modules/onboarding/config/onboard-steps';
import { useCompleteOnboardingMutation } from '@/components/feature-modules/onboarding/hooks/mutation/use-complete-onboarding-mutation';
import {
  useOnboardStoreApi,
  useOnboardStepState,
  useOnboardSubmission,
  useOnboardNavigation,
  useOnboardFormControls,
} from '@/components/feature-modules/onboarding/hooks/use-onboard-store';

export const OnboardNavControls: FC = () => {
  const storeApi = useOnboardStoreApi();
  const { currentStep } = useOnboardStepState();
  const { submissionStatus } = useOnboardSubmission();
  const { goNext, goBack, skip } = useOnboardNavigation();
  const { setStepData, setLiveData } = useOnboardFormControls();

  const mutation = useCompleteOnboardingMutation();

  const isFirstStep = currentStep === 0;
  const isLastStep = currentStep === ONBOARD_STEPS.length - 1;
  const isOptional = ONBOARD_STEPS[currentStep].optional;

  // Hide nav controls while submission is in progress or done
  if (submissionStatus !== 'idle') {
    return null;
  }

  const handleNext = async () => {
    const { formTrigger, liveData } = storeApi.getState();
    const step = ONBOARD_STEPS[currentStep];

    if (formTrigger) {
      const valid = await formTrigger();
      if (!valid) return;
      setStepData(step.id, liveData[step.id]);
    }

    if (isLastStep) {
      mutation.mutate();
    } else {
      goNext();
    }
  };

  return (
    <div className="flex items-center">
      {!isFirstStep && (
        <Button variant="ghost" onClick={() => goBack()}>
          Back
        </Button>
      )}

      <div className="ml-auto flex items-center gap-3">
        {isOptional && (
          <button
            type="button"
            className="text-muted-foreground hover:text-foreground text-sm transition-colors"
            onClick={() => {
              const step = ONBOARD_STEPS[currentStep];
              setLiveData(step.id, undefined);
              skip();
            }}
          >
            Skip
          </button>
        )}

        <Button onClick={handleNext}>{isLastStep ? 'Complete' : 'Next'}</Button>
      </div>
    </div>
  );
};
