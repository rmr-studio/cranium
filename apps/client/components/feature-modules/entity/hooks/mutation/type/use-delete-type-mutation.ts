import { useAuth } from '@/components/provider/auth-context';
import { type EntityTypeImpactResponse } from '@/lib/types/entity';
import { MutationFunctionContext, useMutation, useQueryClient, type UseMutationOptions } from '@tanstack/react-query';
import { toast } from 'sonner';
import { EntityTypeService } from '../../../service/entity-type.service';
import { entityKeys } from '../../query/entity-query-keys';

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

      // Invalidate all affected entity type queries (partial match handles varying `include` param)
      Object.entries(response.updatedEntityTypes).forEach(([key]) => {
        queryClient.invalidateQueries({ queryKey: entityKeys.entityTypes.byKey(key, workspaceId) });
      });

      // Also invalidate the deleted type's queries
      queryClient.invalidateQueries({ queryKey: entityKeys.entityTypes.byKey(variables.key, workspaceId) });

      // Invalidate the entity types list
      queryClient.invalidateQueries({ queryKey: entityKeys.entityTypes.list(workspaceId) });

      return response;
    },
  });
}
