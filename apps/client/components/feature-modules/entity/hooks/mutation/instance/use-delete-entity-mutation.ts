import { useAuth } from '@/components/provider/auth-context';
import { DeleteEntityResponse, Entity, EntityQueryResponse } from '@/lib/types/entity';
import { InfiniteData, useMutation, useQueryClient, type UseMutationOptions } from '@tanstack/react-query';
import { toast } from 'sonner';
import { EntityService } from '../../../service/entity.service';
import { entityKeys } from '../../query/entity-query-keys';
import { removeEntitiesFromPages, replaceEntitiesInPages } from './entity-cache.utils';

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

      // Remove deleted entities from both cache types
      const { entityIds } = variables;
      Object.entries(entityIds).forEach(([typeId, ids]) => {
        const idSet = new Set(ids);

        // Infinite query cache
        queryClient.setQueriesData<InfiniteData<EntityQueryResponse>>(
          { queryKey: ['entities', workspaceId, typeId, 'query'] },
          (oldData) => removeEntitiesFromPages(oldData, idSet),
        );

        // Legacy list cache (relationship picker)
        queryClient.setQueryData<Entity[]>(
          entityKeys.entities.list(workspaceId, typeId),
          (oldData) => {
            if (!oldData) return oldData;
            return oldData.filter((entity) => !idSet.has(entity.id));
          },
        );
      });

      // Update impacted entities across both cache types
      if (!updatedEntities) return;
      Object.entries(updatedEntities).forEach(([typeId, entities]) => {
        const entityMap = new Map(entities.map((entity) => [entity.id, entity]));

        // Infinite query cache
        queryClient.setQueriesData<InfiniteData<EntityQueryResponse>>(
          { queryKey: ['entities', workspaceId, typeId, 'query'] },
          (oldData) => replaceEntitiesInPages(oldData, entityMap),
        );

        // Legacy list cache
        queryClient.setQueryData<Entity[]>(
          entityKeys.entities.list(workspaceId, typeId),
          (oldData) => {
            if (!oldData) return entities;
            return oldData.map((entity) => entityMap.get(entity.id) ?? entity);
          },
        );
      });
    },
  });
}
