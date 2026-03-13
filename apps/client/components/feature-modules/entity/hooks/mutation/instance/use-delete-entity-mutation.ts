import { useAuth } from '@/components/provider/auth-context';
import { DeleteEntityResponse, Entity } from '@/lib/types/entity';
import { useMutation, useQueryClient, type UseMutationOptions } from '@tanstack/react-query';
import { toast } from 'sonner';
import { EntityService } from '../../../service/entity.service';
import { entityKeys } from '../../query/entity-query-keys';

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
    mutationFn: (request: DeleteEntityRequest) => {
      const { entityIds } = request;
      const ids = Object.values(entityIds).flat();

      if (ids.length === 0) {
        const response: DeleteEntityResponse = {
          deletedCount: 0,
          error: 'No entities to delete',
        };
        return Promise.resolve(response);
      }

      return EntityService.deleteEntities(session, workspaceId, ids);
    },
    onMutate: (data) => {
      options?.onMutate?.(data);
    },
    onError: (error: Error, variables: DeleteEntityRequest, context: unknown) => {
      options?.onError?.(error, variables, context);
      toast.error(`Failed to delete selected entities: ${error.message}`);
    },
    onSuccess: (
      response: DeleteEntityResponse,
      variables: DeleteEntityRequest,
      context: unknown,
    ) => {
      const { deletedCount, error, updatedEntities } = response;

      if (deletedCount === 0 && error) {
        toast.error(`Failed to delete entities: ${error}`);
        return;
      }

      options?.onSuccess?.(response, variables, context);
      toast.success(`${deletedCount} entities deleted successfully!`);

      // Remove deleted entities from the cache
      const { entityIds } = variables;
      Object.entries(entityIds).forEach(([typeId, ids]) => {
        const set = new Set(ids);
        queryClient.setQueryData<Entity[]>(entityKeys.entities.list(workspaceId, typeId), (oldData) => {
          if (!oldData) return oldData;
          return oldData.filter((entity) => !set.has(entity.id));
        });
      });

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
