import { useAuth } from '@/components/provider/auth-context';
import { WorkspaceService } from '@/components/feature-modules/workspace/service/workspace.service';
import { useMutation, UseMutationOptions, useQueryClient } from '@tanstack/react-query';
import { useRef } from 'react';
import { toast } from 'sonner';

export function useDeleteWorkspaceMutation(
  options?: UseMutationOptions<void, Error, string>,
) {
  const queryClient = useQueryClient();
  const { user, session } = useAuth();
  const submissionToastRef = useRef<string | number | undefined>(undefined);

  return useMutation({
    mutationFn: (workspaceId: string) =>
      WorkspaceService.deleteWorkspace(session, workspaceId),
    onMutate: (workspaceId) => {
      submissionToastRef.current = toast.loading('Deleting workspace...');
      options?.onMutate?.(workspaceId);
    },
    onSuccess: (response: void, variables: string, context: unknown) => {
      toast.dismiss(submissionToastRef.current);
      toast.success('Workspace deleted successfully');
      queryClient.invalidateQueries({
        queryKey: ['userProfile', user?.id],
      });
      options?.onSuccess?.(response, variables, context);
    },
    onError: (error: Error, variables: string, context: unknown) => {
      toast.dismiss(submissionToastRef.current);
      toast.error(`Failed to delete workspace: ${error.message}`);
      options?.onError?.(error, variables, context);
    },
  });
}
