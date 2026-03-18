import { useAuth } from '@/components/provider/auth-context';
import { DeleteEntityResponse, Entity } from '@/lib/types/entity';
import { useMutation, useQueryClient, type UseMutationOptions } from '@tanstack/react-query';
import { toast } from 'sonner';
import { EntityService } from '@/components/feature-modules/entity/service/entity.service';
import { entityKeys } from '@/components/feature-modules/entity/hooks/query/entity-query-keys';

interface DeleteEntityRequest {
  entityIds: Record<string, string[]>; // Map of entity type id to array of entity IDs
}

export function useDeleteEntityMutation(
  workspaceId: string,
  options?: UseMutationOptions<DeleteEntityResponse, Error, DeleteEntityRequest>,
) {
  const queryClient = useQueryClient();
  const { session } = useAuth();

  return useMutation({
    mutationFn: async (request: DeleteEntityRequest) => {
      const { entityIds } = request;
      const ids = Object.values(entityIds).flat();

      if (ids.length === 0) {
        throw new Error('No entities to delete');
      }

      const response = await EntityService.deleteEntities(session, workspaceId, ids);

      if (response.error && response.deletedCount === 0) {
        throw new Error(response.error);
      }

      return response;
    },
    onMutate: (data) => {
      options?.onMutate?.(data);
      return { toastId: toast.loading('Deleting entities...') };
    },
    onError: (error: Error, variables: DeleteEntityRequest, context: unknown) => {
      const toastId = (context as { toastId?: string | number } | undefined)?.toastId;
      toast.dismiss(toastId);
      options?.onError?.(error, variables, context);
      toast.error(`Failed to delete selected entities: ${error.message}`);
    },
    onSuccess: (
      response: DeleteEntityResponse,
      variables: DeleteEntityRequest,
      context: unknown,
    ) => {
      const toastId = (context as { toastId?: string | number } | undefined)?.toastId;
      toast.dismiss(toastId);

      const { deletedCount, error, updatedEntities } = response;

      if (error && deletedCount > 0) {
        toast.warning(`${deletedCount} entities deleted, but some failed: ${error}`);
      } else {
        toast.success(`${deletedCount} entities deleted successfully!`);
      }

      options?.onSuccess?.(response, variables, context);

      // Remove deleted entities from the cache
      // On partial failure, avoid evicting all requested IDs — refetch for consistency instead
      if (error && deletedCount > 0) {
        const { entityIds } = variables;
        Object.keys(entityIds).forEach((typeId) => {
          queryClient.invalidateQueries({
            queryKey: entityKeys.entities.list(workspaceId, typeId),
          });
        });
      } else {
        const { entityIds } = variables;
        Object.entries(entityIds).forEach(([typeId, ids]) => {
          const set = new Set(ids);
          queryClient.setQueryData<Entity[]>(entityKeys.entities.list(workspaceId, typeId), (oldData) => {
            if (!oldData) return oldData;
            return oldData.filter((entity) => !set.has(entity.id));
          });
        });
      }

      // Adjust data cache for updated entities. Payload only includes entities that were updated as a result of deletion, grouped by their type IDs
      if (!updatedEntities) return;
      Object.entries(updatedEntities).forEach(([typeId, entities]) => {
        queryClient.setQueryData<Entity[]>(entityKeys.entities.list(workspaceId, typeId), (oldData) => {
          if (!oldData) return entities;
          const map = new Map(entities.map((entity) => [entity.id, entity]));
          return oldData.map((entity) => map.get(entity.id) ?? entity);
        });
      });
    },
  });
}
