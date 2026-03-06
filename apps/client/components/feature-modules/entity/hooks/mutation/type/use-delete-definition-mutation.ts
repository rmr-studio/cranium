import { useAuth } from '@/components/provider/auth-context';
import {
  DeleteTypeDefinitionRequest,
  EntityType,
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

export function useDeleteDefinitionMutation(
  workspaceId: string,
  onImpactConfirmation?: (impact: DeleteDefinitionImpact) => void,
  options?: UseMutationOptions<EntityTypeImpactResponse, Error, DeleteTypeDefinitionRequest>,
) {
  const queryClient = useQueryClient();
  const { session } = useAuth();
  return useMutation({
    mutationFn: (definition: DeleteTypeDefinitionRequest) =>
      EntityTypeService.removeEntityTypeDefinition(session, workspaceId, definition),
    onMutate: (data: DeleteTypeDefinitionRequest, context: MutationFunctionContext) => {
      options?.onMutate?.(data, context);
    },
    onError: (
      error: Error,
      variables: DeleteTypeDefinitionRequest,
      onMutateResult: unknown,
      context: MutationFunctionContext,
    ) => {
      options?.onError?.(error, variables, onMutateResult, context);
      toast.error(`Failed to delete entity type definition: ${error.message}`);
    },
    onSuccess: (
      response: EntityTypeImpactResponse,
      variables: DeleteTypeDefinitionRequest,
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
        Object.entries(response.updatedEntityTypes).forEach(([key, entityType]) => {
          // Update individual entity type query cache
          queryClient.setQueryData(['entityType', key, workspaceId], entityType);
        });

        // Update the entity types list in cache
        queryClient.setQueryData<EntityType[]>(['entityTypes', workspaceId], (oldData) => {
          if (!oldData) return Object.values(response.updatedEntityTypes!);

          // Create a map of updated entity types for efficient lookup
          const updatedTypesMap = new Map(
            Object.entries(response.updatedEntityTypes!).map(([key, type]) => [key, type]),
          );

          // Replace all updated entity types in the list
          return oldData.map((et) => updatedTypesMap.get(et.key) ?? et);
        });
      }

      return response;
    },
  });
}
