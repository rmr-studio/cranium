import { useAuth } from '@/components/provider/auth-context';
import {
  DeleteEntityRequest,
  DeleteEntityResponse,
  Entity,
  EntityQueryResponse,
  EntitySelectType,
} from '@/lib/types/entity';
import { InfiniteData, useMutation, useQueryClient, type UseMutationOptions } from '@tanstack/react-query';
import { toast } from 'sonner';
import { EntityService } from '@/components/feature-modules/entity/service/entity.service';
import { entityKeys } from '@/components/feature-modules/entity/hooks/query/entity-query-keys';
import { removeEntitiesFromPages, replaceEntitiesInPages } from './entity-cache.utils';

export function useDeleteEntityMutation(
  workspaceId: string,
  options?: UseMutationOptions<DeleteEntityResponse, Error, DeleteEntityRequest>,
) {
  const queryClient = useQueryClient();
  const { session } = useAuth();

  return useMutation({
    mutationFn: async (request: DeleteEntityRequest) => {
      return EntityService.deleteEntities(session, workspaceId, request);
    },
    onMutate: (data) => {
      options?.onMutate?.(data);
      return { toastId: toast.loading('Deleting entities...') };
    },
    onError: (error: Error, variables: DeleteEntityRequest, context: unknown) => {
      const toastId = (context as { toastId?: string | number } | undefined)?.toastId;
      toast.dismiss(toastId);
      options?.onError?.(error, variables, context);
      toast.error(`Failed to delete entities: ${error.message}`);
    },
    onSuccess: (
      response: DeleteEntityResponse,
      variables: DeleteEntityRequest,
      context: unknown,
    ) => {
      const toastId = (context as { toastId?: string | number } | undefined)?.toastId;
      toast.dismiss(toastId);

      const { deletedCount, updatedEntities } = response;
      const entityTypeId = variables.entityTypeId;

      toast.success(`${deletedCount} ${deletedCount === 1 ? 'entity' : 'entities'} deleted successfully`);

      options?.onSuccess?.(response, variables, context);

      // Cache update strategy depends on deletion mode
      if (entityTypeId) {
        if (variables.type === EntitySelectType.All) {
          // ALL mode: can't surgically remove — invalidate to refetch
          queryClient.invalidateQueries({
            queryKey: ['entities', workspaceId, entityTypeId, 'query'],
          });
          queryClient.invalidateQueries({
            queryKey: entityKeys.entities.list(workspaceId, entityTypeId),
          });
        } else {
          // BY_ID mode: surgical removal from cache
          const idSet = new Set(variables.entityIds ?? []);

          queryClient.setQueriesData<InfiniteData<EntityQueryResponse>>(
            { queryKey: ['entities', workspaceId, entityTypeId, 'query'] },
            (oldData) => removeEntitiesFromPages(oldData, idSet),
          );

          queryClient.setQueryData<Entity[]>(
            entityKeys.entities.list(workspaceId, entityTypeId),
            (oldData) => {
              if (!oldData) return oldData;
              return oldData.filter((entity) => !idSet.has(entity.id));
            },
          );
        }
      } else {
        // No entityTypeId — broad workspace-level invalidation to clear stale caches
        queryClient.invalidateQueries({
          queryKey: ['entities', workspaceId],
        });
      }

      // Update impacted entities across both cache types
      if (!updatedEntities) return;
      Object.entries(updatedEntities).forEach(([typeId, entities]) => {
        const entityMap = new Map(entities.map((entity) => [entity.id, entity]));

        queryClient.setQueriesData<InfiniteData<EntityQueryResponse>>(
          { queryKey: ['entities', workspaceId, typeId, 'query'] },
          (oldData) => replaceEntitiesInPages(oldData, entityMap),
        );

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
