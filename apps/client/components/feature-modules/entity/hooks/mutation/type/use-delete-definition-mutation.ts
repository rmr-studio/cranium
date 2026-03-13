import { useAuth } from '@/components/provider/auth-context';
import {
  DeleteTypeDefinitionRequest,
  EntityTypeImpactResponse,
  type DeleteDefinitionImpact,
} from '@/lib/types/entity';
import {
  MutationFunctionContext,
  useMutation,
  UseMutationOptions,
  useQueryClient,
} from '@tanstack/react-query';
import { toast } from 'sonner';
import { EntityTypeService } from '../../../service/entity-type.service';
import { entityKeys } from '../../query/entity-query-keys';

interface DeleteDefinitionMutationVariables extends DeleteTypeDefinitionRequest {
  impactConfirmed?: boolean;
}

export function useDeleteDefinitionMutation(
  workspaceId: string,
  onImpactConfirmation?: (impact: DeleteDefinitionImpact) => void,
  options?: UseMutationOptions<EntityTypeImpactResponse, Error, DeleteDefinitionMutationVariables>,
) {
  const queryClient = useQueryClient();
  const { session } = useAuth();
  return useMutation({
    mutationFn: ({ impactConfirmed = false, ...definition }: DeleteDefinitionMutationVariables) =>
      EntityTypeService.removeEntityTypeDefinition(session, workspaceId, definition, impactConfirmed),
    onMutate: (data: DeleteDefinitionMutationVariables, context: MutationFunctionContext) => {
      options?.onMutate?.(data, context);
    },
    onError: (
      error: Error,
      variables: DeleteDefinitionMutationVariables,
      onMutateResult: unknown,
      context: MutationFunctionContext,
    ) => {
      options?.onError?.(error, variables, onMutateResult, context);
      toast.error(`Failed to delete entity type definition: ${error.message}`);
    },
    onSuccess: (
      response: EntityTypeImpactResponse,
      variables: DeleteDefinitionMutationVariables,
      onMutateResult: unknown,
      context: MutationFunctionContext,
    ) => {
      options?.onSuccess?.(response, variables, onMutateResult, context);

      // Check for impact confirmation (409 response)
      if (response.impact) {
        onImpactConfirmation?.(response.impact);
        return;
      }

      toast.success('Entity type definition deleted successfully!');

      if (response.updatedEntityTypes) {
        Object.entries(response.updatedEntityTypes).forEach(([key]) => {
          // Invalidate all entity type queries for this key (regardless of include params)
          queryClient.invalidateQueries({ queryKey: entityKeys.entityTypes.byKey(key, workspaceId) });
        });

        // Invalidate the entity types list in cache
        queryClient.invalidateQueries({ queryKey: entityKeys.entityTypes.list(workspaceId) });
      }

      return response;
    },
  });
}
