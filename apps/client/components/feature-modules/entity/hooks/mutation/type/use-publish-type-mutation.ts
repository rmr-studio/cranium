import { useAuth } from '@/components/provider/auth-context';
import { CreateEntityTypeRequest, EntityType } from '@/lib/types/entity';
import { MutationFunctionContext, useMutation, UseMutationOptions, useQueryClient } from '@tanstack/react-query';
import { useRef } from 'react';
import { toast } from 'sonner';
import { EntityTypeService } from '../../../service/entity-type.service';

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

      // Update the specific entity type in cache
      queryClient.setQueryData(['entityType', response.key, workspaceId], response);

      // Update the entity types list in cache
      queryClient.setQueryData<EntityType[]>(['entityTypes', workspaceId], (oldData) => {
        if (!oldData) return [response];

        // Add new entity type to the list
        return [...oldData, response];
      });

      return response;
    },
  });
}
