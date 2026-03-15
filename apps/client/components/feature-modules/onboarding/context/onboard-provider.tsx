'use client';

import { type StoreApi, useStore } from 'zustand';
import React, { createContext, useContext, useRef } from 'react';
import {
  createOnboardStore,
  type OnboardStore,
} from '@/components/feature-modules/onboarding/stores/onboard.store';
import { useShallow } from 'zustand/react/shallow';

type OnboardStoreApi = StoreApi<OnboardStore>;

const OnboardStoreContext = createContext<OnboardStoreApi | undefined>(undefined);

export const OnboardProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const storeRef = useRef<OnboardStoreApi | undefined>(undefined);
  if (!storeRef.current) {
    storeRef.current = createOnboardStore();
  }
  return (
    <OnboardStoreContext.Provider value={storeRef.current}>
      {children}
    </OnboardStoreContext.Provider>
  );
};

export function useOnboardStore<T>(selector: (store: OnboardStore) => T): T {
  const store = useContext(OnboardStoreContext);
  if (!store) {
    throw new Error('useOnboardStore must be used within OnboardProvider');
  }
  return useStore(store, selector);
}

export function useOnboardStoreApi(): OnboardStoreApi {
  const store = useContext(OnboardStoreContext);
  if (!store) {
    throw new Error('useOnboardStoreApi must be used within OnboardProvider');
  }
  return store;
}

// ── Selector hooks ───────────────────────────────────────────────────

export const useOnboardStepState = () =>
  useOnboardStore(useShallow((s) => ({ currentStep: s.currentStep, direction: s.direction })));

export const useOnboardLiveData = <T = unknown>(stepId: string) =>
  useOnboardStore((s) => s.liveData[stepId] as T | undefined);

export const useOnboardNavigation = () =>
  useOnboardStore(useShallow((s) => ({ goNext: s.goNext, goBack: s.goBack, skip: s.skip })));

export const useOnboardFormControls = () =>
  useOnboardStore(
    useShallow((s) => ({
      setLiveData: s.setLiveData,
      setStepData: s.setStepData,
      registerFormTrigger: s.registerFormTrigger,
      clearFormTrigger: s.clearFormTrigger,
    })),
  );

export const useOnboardSubmission = () =>
  useOnboardStore(
    useShallow((s) => ({
      submissionStatus: s.submissionStatus,
      submissionResponse: s.submissionResponse,
      setSubmissionStatus: s.setSubmissionStatus,
      setSubmissionResponse: s.setSubmissionResponse,
      reset: s.reset,
    })),
  );
