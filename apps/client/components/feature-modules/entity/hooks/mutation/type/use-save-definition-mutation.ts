import { useAuth } from '@/components/provider/auth-context';
import {
  SaveTypeDefinitionRequest,
  type DeleteDefinitionImpact,
  type EntityTypeImpactResponse,
} from '@/lib/types/entity';
import { MutationFunctionContext, useMutation, useQueryClient, type UseMutationOptions } from '@tanstack/react-query';
import { useRef } from 'react';
import { toast } from 'sonner';
import { EntityTypeService } from '@/components/feature-modules/entity/service/entity-type.service';
import { entityKeys } from '@/components/feature-modules/entity/hooks/query/entity-query-keys';

export function useSaveDefinitionMutation(
  workspaceId: string,
  onImpactConfirmation?: (impact: DeleteDefinitionImpact) => void,
  options?: UseMutationOptions<EntityTypeImpactResponse, Error, SaveTypeDefinitionRequest>,
) {
  const queryClient = useQueryClient();
  const { session } = useAuth();
  const submissionToastRef = useRef<string | number | undefined>(undefined);

  return useMutation({
    mutationFn: (definition: SaveTypeDefinitionRequest) =>
      EntityTypeService.saveEntityTypeDefinition(session, workspaceId, definition),
    onMutate: (data: SaveTypeDefinitionRequest, context: MutationFunctionContext) => {
      options?.onMutate?.(data, context);
      submissionToastRef.current = toast.loading('Saving entity type definition...');
    },
    onError: (error: Error, variables: SaveTypeDefinitionRequest, onMutateResult: unknown, context: MutationFunctionContext) => {
      options?.onError?.(error, variables, onMutateResult, context);
      toast.dismiss(submissionToastRef.current);
      submissionToastRef.current = undefined;
      toast.error(`Failed to save entity type definition: ${error.message}`);
    },
    onSuccess: (
      response: EntityTypeImpactResponse,
      variables: SaveTypeDefinitionRequest,
      onMutateResult: unknown,
      context: MutationFunctionContext,
    ) => {
      options?.onSuccess?.(response, variables, onMutateResult, context);
      toast.dismiss(submissionToastRef.current);
      submissionToastRef.current = undefined;

      // Check for impact confirmation (409 response)
      if (response.impact) {
        onImpactConfirmation?.(response.impact);
        return;
      }

      toast.success(`Entity type definition saved successfully!`);

      queryClient.invalidateQueries({ queryKey: ['semanticMetadata', workspaceId] });

      if (response.updatedEntityTypes) {
        Object.entries(response.updatedEntityTypes).forEach(([key]) => {
          // Invalidate individual entity type queries (partial match handles varying `include` param)
          queryClient.invalidateQueries({ queryKey: entityKeys.entityTypes.byKey(key, workspaceId) });
        });

        // Invalidate the entity types list
        queryClient.invalidateQueries({ queryKey: entityKeys.entityTypes.list(workspaceId) });
      }

      return response;
    },
  });
}
