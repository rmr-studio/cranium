import { useAuth } from '@/components/provider/auth-context';
import { DeleteEntityResponse, Entity, EntityQueryResponse } from '@/lib/types/entity';
import { InfiniteData, useMutation, useQueryClient, type UseMutationOptions } from '@tanstack/react-query';
import { toast } from 'sonner';
import { EntityService } from '@/components/feature-modules/entity/service/entity.service';
import { entityKeys } from '@/components/feature-modules/entity/hooks/query/entity-query-keys';
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

      // Remove deleted entities from both cache types
      // On partial failure, avoid evicting all requested IDs — refetch for consistency instead
      const { entityIds } = variables;
      if (error && deletedCount > 0) {
        Object.keys(entityIds).forEach((typeId) => {
          queryClient.invalidateQueries({
            queryKey: ['entities', workspaceId, typeId, 'query'],
          });
          queryClient.invalidateQueries({
            queryKey: entityKeys.entities.list(workspaceId, typeId),
          });
        });
      } else {
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
      }

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
