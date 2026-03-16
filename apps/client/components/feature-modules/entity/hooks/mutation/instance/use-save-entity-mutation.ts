import { useAuth } from '@/components/provider/auth-context';
import { Entity, EntityQueryResponse, SaveEntityRequest, SaveEntityResponse } from '@/lib/types/entity';
import { InfiniteData, useMutation, useQueryClient, type UseMutationOptions } from '@tanstack/react-query';
import { useRef } from 'react';
import { toast } from 'sonner';
import { EntityService } from '../../../service/entity.service';
import { entityKeys } from '@/components/feature-modules/entity/hooks/query/entity-query-keys';
import { updateEntityInPages, replaceEntitiesInPages } from './entity-cache.utils';

export function useSaveEntityMutation(
  workspaceId: string,
  entityTypeId: string,
  options?: UseMutationOptions<SaveEntityResponse, Error, SaveEntityRequest>,
  onConflict?: (request: SaveEntityRequest, response: SaveEntityResponse) => void,
) {
  const queryClient = useQueryClient();
  const { session } = useAuth();
  const submissionToastRef = useRef<string | number | undefined>(undefined);

  return useMutation({
    mutationFn: (payload: SaveEntityRequest) =>
      EntityService.saveEntity(session, workspaceId, entityTypeId, payload),
    onMutate: (data) => {
      options?.onMutate?.(data);
      const isUpdate = !!data.id;
      submissionToastRef.current = toast.loading(
        isUpdate ? 'Updating entity...' : 'Creating entity...',
      );
    },
    onError: (error: Error, variables: SaveEntityRequest, context: unknown) => {
      options?.onError?.(error, variables, context);
      toast.dismiss(submissionToastRef.current);
      submissionToastRef.current = undefined;
      toast.error(`Failed to save entity: ${error.message}`);
    },
    onSuccess: (response: SaveEntityResponse, variables: SaveEntityRequest, context: unknown) => {
      toast.dismiss(submissionToastRef.current);
      submissionToastRef.current = undefined;

      // Handle schema validation or impact confirmation errors
      if (response.errors) {
        onConflict?.(variables, response);
        return;
      }

      if (response.entity) {
        const savedEntity = response.entity;
        const isUpdate = !!variables.id;

        // Update infinite query cache (paginated data table)
        if (isUpdate) {
          queryClient.setQueriesData<InfiniteData<EntityQueryResponse>>(
            { queryKey: ['entities', workspaceId, entityTypeId, 'query'] },
            (oldData) => updateEntityInPages(oldData, savedEntity.id, savedEntity),
          );
        }

        // Also update the legacy list cache (used by relationship picker)
        queryClient.setQueryData<Entity[]>(
          entityKeys.entities.list(workspaceId, entityTypeId),
          (currentEntities) => {
            if (!currentEntities) return [savedEntity];
            const existingIndex = currentEntities.findIndex((e) => e.id === savedEntity.id);
            if (existingIndex >= 0) {
              const updated = [...currentEntities];
              updated[existingIndex] = savedEntity;
              return updated;
            }
            return [...currentEntities, savedEntity];
          },
        );

        // For new entities, invalidate to refetch from server (server determines sort position)
        if (!isUpdate) {
          queryClient.invalidateQueries({
            queryKey: ['entities', workspaceId, entityTypeId, 'query'],
          });
        }

        // Update impacted entities across both cache types
        if (response.impactedEntities) {
          Object.entries(response.impactedEntities).forEach(
            ([impactedTypeId, impactedEntities]) => {
              // Infinite query cache
              const replacements = new Map(impactedEntities.map((e) => [e.id, e]));
              queryClient.setQueriesData<InfiniteData<EntityQueryResponse>>(
                { queryKey: ['entities', workspaceId, impactedTypeId, 'query'] },
                (oldData) => replaceEntitiesInPages(oldData, replacements),
              );

              // Legacy list cache
              queryClient.setQueryData<Entity[]>(
                entityKeys.entities.list(workspaceId, impactedTypeId),
                (currentEntities) => {
                  if (!currentEntities) return impactedEntities;
                  const updatedEntities = [...currentEntities];
                  impactedEntities.forEach((impactedEntity) => {
                    const idx = updatedEntities.findIndex((e) => e.id === impactedEntity.id);
                    if (idx >= 0) updatedEntities[idx] = impactedEntity;
                    else updatedEntities.push(impactedEntity);
                  });
                  return updatedEntities;
                },
              );
            },
          );
        }

        options?.onSuccess?.(response, variables, context);
        toast.success(isUpdate ? 'Entity updated successfully!' : 'Entity created successfully!');

        return response;
      }
    },
  });
}
