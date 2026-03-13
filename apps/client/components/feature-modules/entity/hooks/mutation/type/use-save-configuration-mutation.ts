import { useAuth } from '@/components/provider/auth-context';
import { EntityType, UpdateEntityTypeConfigurationRequest } from '@/lib/types/entity';
import { MutationFunctionContext, useMutation, UseMutationOptions, useQueryClient } from '@tanstack/react-query';
import { useRef } from 'react';
import { toast } from 'sonner';
import { EntityTypeService } from '../../../service/entity-type.service';
import { entityKeys } from '../../query/entity-query-keys';

export function useSaveEntityTypeConfiguration(
  workspaceId: string,
  options?: UseMutationOptions<EntityType, Error, UpdateEntityTypeConfigurationRequest>,
) {
  const queryClient = useQueryClient();
  const { session } = useAuth();
  const submissionToastRef = useRef<string | number | undefined>(undefined);

  return useMutation({
    mutationFn: (request: UpdateEntityTypeConfigurationRequest) =>
      EntityTypeService.saveEntityTypeConfiguration(session, workspaceId, request),
    onMutate: (data: UpdateEntityTypeConfigurationRequest, context: MutationFunctionContext) => {
      options?.onMutate?.(data, context);
      submissionToastRef.current = toast.loading('Updating entity type...');
    },
    onError: (error: Error, variables: UpdateEntityTypeConfigurationRequest, onMutateResult: unknown, context: MutationFunctionContext) => {
      options?.onError?.(error, variables, onMutateResult, context);
      toast.dismiss(submissionToastRef.current);
      submissionToastRef.current = undefined;
      toast.error(`Failed to update entity type: ${error.message}`);
    },
    onSuccess: (response: EntityType, variables: UpdateEntityTypeConfigurationRequest, onMutateResult: unknown, context: MutationFunctionContext) => {
      options?.onSuccess?.(response, variables, onMutateResult, context);
      toast.dismiss(submissionToastRef.current);
      toast.success(`Entity type updated successfully!`);

      // Invalidate entity type queries (partial match handles varying `include` param)
      queryClient.invalidateQueries({ queryKey: entityKeys.entityTypes.byKey(response.key, workspaceId) });

      // Invalidate the entity types list
      queryClient.invalidateQueries({ queryKey: entityKeys.entityTypes.list(workspaceId) });

      // Stay on the same page after update
      return response;
    },
  });
}
