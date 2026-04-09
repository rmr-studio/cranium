import { useAuth } from '@/components/provider/auth-context';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRef } from 'react';
import { toast } from 'sonner';
import { DefinitionService } from '@/components/feature-modules/knowledge/service/definition.service';
import { definitionKeys } from '@/components/feature-modules/knowledge/hooks/query/definition-query-keys';

export function useDeleteDefinitionMutation(
  workspaceId: string,
  options?: { onSuccess?: () => void },
) {
  const queryClient = useQueryClient();
  const { session } = useAuth();
  const toastRef = useRef<string | number | undefined>(undefined);

  return useMutation({
    mutationFn: (definitionId: string) =>
      DefinitionService.deleteDefinition(session, workspaceId, definitionId),
    onMutate: () => {
      toastRef.current = toast.loading('Deleting definition...');
    },
    onError: (error: Error) => {
      toast.dismiss(toastRef.current);
      toastRef.current = undefined;
      toast.error(`Failed to delete definition: ${error.message}`);
    },
    onSuccess: () => {
      toast.dismiss(toastRef.current);
      toastRef.current = undefined;
      toast.success('Definition deleted');
      queryClient.invalidateQueries({
        queryKey: definitionKeys.definitions.base(workspaceId),
      });
      options?.onSuccess?.();
    },
  });
}
