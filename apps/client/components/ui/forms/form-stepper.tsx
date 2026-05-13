import {
  Stepper,
  StepperDescription,
  StepperIndicator,
  StepperItem,
  StepperTitle,
  StepperTrigger,
} from '@/components/ui/stepper';

import type { ClassNameProps } from '@cranium/utils';
import { Step } from '@/lib/util/form/form.util';
import { cn } from '@cranium/utils';
import { Info } from 'lucide-react';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '../tooltip';
import { TruncatedTooltip } from '../truncated-tooltip';

type StepperDescription = 'display' | 'icon' | 'none';

interface Props<T> extends ClassNameProps {
  steps: Step<T>[];
  /**
   * The initial step to display in the stepper.
   * If not provided, defaults to the first step in the steps array.
   */
  currentStep?: T;
  /**
   * The description type for each step.
   * Can be "display" for full description, "icon" for an icon, or "none" for no description.
   */
  descriptionType?: StepperDescription;
}

export const FormStepper = <T extends string>({
  steps,
  currentStep,
  className,
  descriptionType = 'none',
}: Props<T>) => {
  if (!steps || steps.length === 0) {
    return <p className="text-muted-foreground">No steps available</p>;
  }

  const initialIndex = (): number => {
    // If initialStep is provided, check if it exists in the steps array
    if (currentStep) {
      const stepIndex = steps.findIndex((s) => s.identifier === currentStep);
      if (stepIndex !== -1) {
        return stepIndex;
      }
    }
    // Otherwise, default to the first step
    return steps[0].step;
  };

  const isCompleted = (step: Step<T>): boolean => {
    // Check if the step is completed based on the current step
    if (!currentStep) return false;
    const current = steps.find((step) => step.identifier === currentStep);
    if (!current) return false;

    return current.step >= step.step;
  };

  const showDescription = descriptionType && descriptionType !== 'none';

  return (
    <div className={cn(`mx-auto max-w-xl min-w-[400px] space-y-8 text-center`, className)}>
      <Stepper defaultValue={initialIndex()} className="items-start gap-4">
        {steps.map((step) => (
          <StepperItem
            key={step.identifier}
            step={step.step}
            completed={isCompleted(step)}
            className="flex-1"
          >
            <StepperTrigger allowSelect={false} className="w-full flex-col items-start gap-2">
              <StepperIndicator asChild className="h-1 w-full bg-border">
                <span className="sr-only">{step.step}</span>
              </StepperIndicator>
              <div className="space-y-0.5">
                <div className="flex">
                  <StepperTitle>{step.title}</StepperTitle>
                  {showDescription && descriptionType == 'icon' && (
                    <TooltipProvider>
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <Info className="ml-1 size-3 stroke-muted-foreground" />
                        </TooltipTrigger>
                        <TooltipContent side="bottom" className="max-w-sm">
                          <p className="text-sm">{step.description}</p>
                        </TooltipContent>
                      </Tooltip>
                    </TooltipProvider>
                  )}
                </div>
                {showDescription && descriptionType == 'display' && (
                  <StepperDescription className="max-w-sm truncate text-sm text-muted-foreground">
                    <TruncatedTooltip
                      render={() => (
                        <p className="text-sm text-muted-foreground">{step.description}</p>
                      )}
                    >
                      description
                    </TruncatedTooltip>
                  </StepperDescription>
                )}
              </div>
            </StepperTrigger>
          </StepperItem>
        ))}
      </Stepper>
    </div>
  );
};
