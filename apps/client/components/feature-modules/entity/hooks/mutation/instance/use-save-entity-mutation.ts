import { useAuth } from '@/components/provider/auth-context';
import { Entity, SaveEntityRequest, SaveEntityResponse } from '@/lib/types/entity';
import { useMutation, useQueryClient, type UseMutationOptions } from '@tanstack/react-query';
import { useRef } from 'react';
import { toast } from 'sonner';
import { EntityService } from '../../../service/entity.service';
import { entityKeys } from '@/components/feature-modules/entity/hooks/query/entity-query-keys';

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
        if (onConflict) {
          onConflict(variables, response);
        } else {
          toast.error(`Failed to save entity: ${response.errors.join(', ')}`);
        }
        return;
      }

      if (response.entity) {
        const savedEntity = response.entity;
        const isUpdate = !!variables.id;

        // Update the entity in the cache for its entity type
        queryClient.setQueryData<Entity[]>(
          entityKeys.entities.list(workspaceId, entityTypeId),
          (currentEntities) => {
            if (!currentEntities) return [savedEntity];

            const existingIndex = currentEntities.findIndex((e) => e.id === savedEntity.id);

            if (existingIndex >= 0) {
              // Replace existing entity
              const updated = [...currentEntities];
              updated[existingIndex] = savedEntity;
              return updated;
            } else {
              // Append new entity
              return [...currentEntities, savedEntity];
            }
          },
        );

        // Update any impacted entities in their respective type caches
        if (response.impactedEntities) {
          Object.entries(response.impactedEntities).forEach(
            ([impactedTypeId, impactedEntities]) => {
              queryClient.setQueryData<Entity[]>(
                entityKeys.entities.list(workspaceId, impactedTypeId),
                (currentEntities) => {
                  if (!currentEntities) return impactedEntities;

                  const updatedEntities = [...currentEntities];

                  impactedEntities.forEach((impactedEntity) => {
                    const existingIndex = updatedEntities.findIndex(
                      (e) => e.id === impactedEntity.id,
                    );

                    if (existingIndex >= 0) {
                      updatedEntities[existingIndex] = impactedEntity;
                    } else {
                      updatedEntities.push(impactedEntity);
                    }
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
