import { useAuth } from '@/components/provider/auth-context';
import { EntityType, type EntityTypeImpactResponse } from '@/lib/types/entity';
import { MutationFunctionContext, useMutation, useQueryClient, type UseMutationOptions } from '@tanstack/react-query';
import { toast } from 'sonner';
import { EntityTypeService } from '../../../service/entity-type.service';

export interface DeleteEntityTypeRequest {
  key: string;
  impactConfirmed?: boolean;
}

export function useDeleteTypeMutation(
  workspaceId: string,
  onImpactConfirmation: (impact: EntityTypeImpactResponse) => void,
  options?: UseMutationOptions<EntityTypeImpactResponse, Error, DeleteEntityTypeRequest>,
) {
  const queryClient = useQueryClient();
  const { session } = useAuth();

  return useMutation({
    mutationFn: (request: DeleteEntityTypeRequest) => {
      const { key, impactConfirmed = false } = request;
      return EntityTypeService.deleteEntityType(session, workspaceId, key, impactConfirmed);
    },
    onMutate: (data: DeleteEntityTypeRequest, context: MutationFunctionContext) => {
      options?.onMutate?.(data, context);
    },
    onError: (error: Error, variables: DeleteEntityTypeRequest, onMutateResult: unknown, context: MutationFunctionContext) => {
      options?.onError?.(error, variables, onMutateResult, context);
      toast.error(`Failed to delete entity type definition: ${error.message}`);
    },
    onSuccess: (
      response: EntityTypeImpactResponse,
      variables: DeleteEntityTypeRequest,
      onMutateResult: unknown,
      context: MutationFunctionContext,
    ) => {
      options?.onSuccess?.(response, variables, onMutateResult, context);

      // If there is an impact, invoke the impact confirmation callback and return early
      if (response.impact) {
        onImpactConfirmation(response);
        return;
      }

      toast.success(`Entity type definition deleted successfully!`);

      if (!response.updatedEntityTypes) return;

      Object.entries(response.updatedEntityTypes).forEach(([key, entityType]) => {
        // Update individual entity type query cache
        queryClient.setQueryData(['entityType', key, workspaceId], entityType);
      });

      queryClient.invalidateQueries({
        queryKey: ['entityType', variables.key, workspaceId],
      });

      // Update the entity types list in cache
      queryClient.setQueryData<EntityType[]>(['entityTypes', workspaceId], (oldData) => {
        if (!response.updatedEntityTypes) return;

        if (!oldData)
          return Object.values(response.updatedEntityTypes).filter(
            (et) => et.key !== variables.key,
          );

        // Create a map of updated entity types for efficient lookup
        const updatedTypesMap = new Map(
          Object.entries(response.updatedEntityTypes!).map(([key, type]) => [key, type]),
        );

        // Replace all updated entity types in the list and remove the delete entity type
        return oldData
          .map((et) => updatedTypesMap.get(et.key) ?? et)
          .filter((et) => et.key !== variables.key);
      });

      return response;
    },
  });
}
