import type { WaitlistMultiStepFormData } from '@/lib/validations';

export enum Step {
  CTA = 0,
  CONTACT = 1,
  BRIDGE = 2,
  BUSINESS_OVERVIEW = 3,
  PAIN_POINTS = 4,
  INTEGRATIONS = 5,
  INVOLVEMENT = 6,
  SUCCESS = 7,
}

export const TOTAL_FORM_STEPS = 4;

export const STEP_NAMES: Record<number, string> = {
  [Step.CTA]: 'cta',
  [Step.CONTACT]: 'contact',
  [Step.BRIDGE]: 'bridge',
  [Step.BUSINESS_OVERVIEW]: 'business_overview',
  [Step.PAIN_POINTS]: 'pain_points',
  [Step.INTEGRATIONS]: 'integrations',
  [Step.INVOLVEMENT]: 'involvement',
  [Step.SUCCESS]: 'success',
};

export const STEP_FIELDS: Partial<Record<Step, (keyof WaitlistMultiStepFormData)[]>> = {
  [Step.CONTACT]: ['name', 'email'],
  [Step.BUSINESS_OVERVIEW]: ['businessOverview'],
  [Step.PAIN_POINTS]: ['painPoints'],
  [Step.INTEGRATIONS]: ['integrations'],
  [Step.INVOLVEMENT]: ['involvement'],
};

export const INPUT_CLASS =
  'w-full bg-transparent border-0 border-b border-foreground/20 pb-2 text-lg placeholder:text-muted-foreground/50 focus:outline-none focus:border-foreground/50 transition-colors';

export const INPUT_ERROR_CLASS = 'border-destructive focus:border-destructive';
