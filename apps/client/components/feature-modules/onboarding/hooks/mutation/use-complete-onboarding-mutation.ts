'use client';

import { useAuth } from '@/components/provider/auth-context';
import { CompleteOnboardingResponse } from '@/lib/types/models';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { assemblePayload, OnboardingService } from '@/components/feature-modules/onboarding/service/onboarding.service';
import {
  useOnboardStoreApi,
  useOnboardSubmission,
} from '@/components/feature-modules/onboarding/hooks/use-onboard-store';
import { toast } from 'sonner';

export function useCompleteOnboardingMutation() {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const storeApi = useOnboardStoreApi();
  const { setSubmissionStatus, setSubmissionResponse } = useOnboardSubmission();

  return useMutation<CompleteOnboardingResponse, Error, void>({
    mutationFn: async () => {
      if (!session) throw new Error('Session required');

      const { validatedData, profileAvatarBlob, workspaceAvatarBlob } =
        storeApi.getState();

      const request = assemblePayload(validatedData);

      return OnboardingService.completeOnboarding(
        session,
        request,
        profileAvatarBlob,
        workspaceAvatarBlob,
      );
    },
    onMutate: () => {
      setSubmissionStatus('loading');
    },
    onError: (error) => {
      setSubmissionStatus('error');
      toast.error(error.message || 'Something went wrong setting up your workspace.');
    },
    onSuccess: (response) => {
      setSubmissionResponse(response);
      setSubmissionStatus('success');
      queryClient.invalidateQueries({ queryKey: ['userProfile'] });
    },
  });
}
