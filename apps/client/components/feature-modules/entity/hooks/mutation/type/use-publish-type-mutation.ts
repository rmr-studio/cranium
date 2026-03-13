import { useAuth } from '@/components/provider/auth-context';
import { CreateEntityTypeRequest, EntityType } from '@/lib/types/entity';
import { MutationFunctionContext, useMutation, UseMutationOptions, useQueryClient } from '@tanstack/react-query';
import { useRef } from 'react';
import { toast } from 'sonner';
import { EntityTypeService } from '../../../service/entity-type.service';
import { entityKeys } from '../../query/entity-query-keys';

export function usePublishEntityTypeMutation(
  workspaceId: string,
  options?: UseMutationOptions<EntityType, Error, CreateEntityTypeRequest>,
) {
  const queryClient = useQueryClient();
  const { session } = useAuth();
  const submissionToastRef = useRef<string | number | undefined>(undefined);

  return useMutation({
    mutationFn: (request: CreateEntityTypeRequest) =>
      EntityTypeService.publishEntityType(session, workspaceId, request),
    onMutate: (data: CreateEntityTypeRequest, context: MutationFunctionContext) => {
      options?.onMutate?.(data, context);
      submissionToastRef.current = toast.loading('Creating entity type...');
    },
    onError: (error: Error, variables: CreateEntityTypeRequest, onMutateResult: unknown, context: MutationFunctionContext) => {
      options?.onError?.(error, variables, onMutateResult, context);
      toast.dismiss(submissionToastRef.current);
      submissionToastRef.current = undefined;
      toast.error(`Failed to create entity type: ${error.message}`);
    },
    onSuccess: (response: EntityType, variables: CreateEntityTypeRequest, onMutateResult: unknown, context: MutationFunctionContext) => {
      options?.onSuccess?.(response, variables, onMutateResult, context);
      toast.dismiss(submissionToastRef.current);
      submissionToastRef.current = undefined;
      toast.success(`Entity type created successfully!`);

      // Invalidate entity type queries (partial match handles varying `include` param)
      queryClient.invalidateQueries({ queryKey: entityKeys.entityTypes.byKey(response.key, workspaceId) });

      // Invalidate the entity types list
      queryClient.invalidateQueries({ queryKey: entityKeys.entityTypes.list(workspaceId) });

      return response;
    },
  });
}
