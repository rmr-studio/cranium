import { useAuth } from '@/components/provider/auth-context';
import {
  CreateBusinessDefinitionRequest,
  UpdateBusinessDefinitionRequest,
  WorkspaceBusinessDefinition,
} from '@/lib/types/workspace';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRef } from 'react';
import { toast } from 'sonner';
import { DefinitionService } from '@/components/feature-modules/knowledge/service/definition.service';
import { definitionKeys } from '@/components/feature-modules/knowledge/hooks/query/definition-query-keys';

export function useSaveDefinitionMutation(
  workspaceId: string,
  definitionId?: string,
  options?: { onSuccess?: (data: WorkspaceBusinessDefinition) => void },
) {
  const queryClient = useQueryClient();
  const { session } = useAuth();
  const toastRef = useRef<string | number | undefined>(undefined);
  const isUpdate = !!definitionId;

  return useMutation({
    mutationFn: (request: CreateBusinessDefinitionRequest | UpdateBusinessDefinitionRequest) =>
      isUpdate
        ? DefinitionService.updateDefinition(session, workspaceId, definitionId!, request as UpdateBusinessDefinitionRequest)
        : DefinitionService.createDefinition(session, workspaceId, request as CreateBusinessDefinitionRequest),
    onMutate: () => {
      toastRef.current = toast.loading('Saving definition...');
    },
    onError: (error: Error) => {
      toast.dismiss(toastRef.current);
      toastRef.current = undefined;
      toast.error(`Failed to save definition: ${error.message}`);
    },
    onSuccess: (response) => {
      toast.dismiss(toastRef.current);
      toastRef.current = undefined;
      toast.success(isUpdate ? 'Definition updated' : 'Definition created');
      queryClient.invalidateQueries({
        queryKey: definitionKeys.definitions.base(workspaceId),
      });
      if (isUpdate) {
        queryClient.invalidateQueries({
          queryKey: definitionKeys.definition.detail(workspaceId, definitionId!),
        });
      }
      options?.onSuccess?.(response);
    },
  });
}
