import { ONBOARD_STEPS } from '../config/onboard-steps';
import { create } from 'zustand';
import { subscribeWithSelector } from 'zustand/middleware';

export interface OnboardState {
  currentStep: number;
  direction: 'forward' | 'backward';
  validatedData: Record<string, unknown>;
}

export interface OnboardActions {
  goNext: () => void;
  goBack: () => void;
  skip: () => void;
  setStepData: (stepId: string, data: unknown) => void;
  reset: () => void;
}

export type OnboardStore = OnboardState & OnboardActions;

const initialState: OnboardState = {
  currentStep: 0,
  direction: 'forward',
  validatedData: {},
};

export const useOnboardStore = create<OnboardStore>()(
  subscribeWithSelector((set, get) => ({
    ...initialState,

    goNext: () => {
      const { currentStep } = get();
      set({
        currentStep: Math.min(currentStep + 1, ONBOARD_STEPS.length - 1),
        direction: 'forward',
      });
    },

    goBack: () => {
      const { currentStep } = get();
      set({
        currentStep: Math.max(currentStep - 1, 0),
        direction: 'backward',
      });
    },

    skip: () => {
      const { currentStep } = get();
      const step = ONBOARD_STEPS[currentStep];
      if (step?.optional) {
        set({
          currentStep: Math.min(currentStep + 1, ONBOARD_STEPS.length - 1),
          direction: 'forward',
        });
      }
      // no-op if step is required
    },

    setStepData: (stepId, data) => {
      set((state) => ({
        validatedData: {
          ...state.validatedData,
          [stepId]: data,
        },
      }));
    },

    reset: () => {
      set({ ...initialState, validatedData: {} });
    },
  })),
);
