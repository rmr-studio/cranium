import { useAuth } from '@/components/provider/auth-context';
import { updateUser } from '@/components/feature-modules/user/service/user.service';
import type { User, SaveUserRequest } from '@/lib/types/user';
import { useMutation, UseMutationOptions, useQueryClient } from '@tanstack/react-query';
import { useRef } from 'react';
import { toast } from 'sonner';

interface UpdateProfileMutationProps {
  user: SaveUserRequest;
  avatar?: Blob | null;
}

export function useUpdateProfileMutation(
  options?: UseMutationOptions<User, Error, UpdateProfileMutationProps>,
) {
  const queryClient = useQueryClient();
  const { user, session } = useAuth();
  const submissionToastRef = useRef<string | number | undefined>(undefined);

  return useMutation({
    mutationFn: ({ user: userData, avatar }: UpdateProfileMutationProps) =>
      updateUser(session, userData, avatar),
    onMutate: (data) => {
      submissionToastRef.current = toast.loading('Updating profile...');
      options?.onMutate?.(data);
    },
    onSuccess: (response: User, variables: UpdateProfileMutationProps, context: unknown) => {
      toast.dismiss(submissionToastRef.current);
      toast.success('Profile updated successfully');
      queryClient.invalidateQueries({
        queryKey: ['userProfile', user?.id],
      });
      options?.onSuccess?.(response, variables, context);
    },
    onError: (error: Error, variables: UpdateProfileMutationProps, context: unknown) => {
      toast.dismiss(submissionToastRef.current);
      toast.error(`Failed to update profile: ${error.message}`);
      options?.onError?.(error, variables, context);
    },
  });
}
