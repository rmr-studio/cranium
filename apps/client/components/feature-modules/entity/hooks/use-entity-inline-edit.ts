import { useAuth } from '@/components/provider/auth-context';
import { SchemaUUID } from '@/lib/types/common';
import {
  Entity,
  EntityAttributePrimitivePayload,
  EntityAttributeRelationPayloadReference,
  EntityLink,
  EntityPropertyType,
  EntityType,
  EntityTypeRequestDefinition,
  RelationshipDefinition,
  SaveEntityRequest,
  SaveEntityResponse,
  SaveTypeDefinitionRequest,
} from '@/lib/types/entity';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { toast } from 'sonner';
import { useSaveEntityMutation } from '@/components/feature-modules/entity/hooks/mutation/instance/use-save-entity-mutation';
import { entityKeys } from '@/components/feature-modules/entity/hooks/query/entity-query-keys';
import { buildEntityUpdatePayload, deriveSchemaOptionsUpdate } from '@/components/feature-modules/entity/util/entity-payload.util';
import { EntityRow, isDraftRow } from '@/components/feature-modules/entity/components/tables/entity-table-utils';
import { EntityTypeService } from '@/components/feature-modules/entity/service/entity-type.service';

export function useEntityInlineEdit(
  workspaceId: string,
  entityType: EntityType,
  entities: Entity[],
) {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const handleConflict = (_request: SaveEntityRequest, response: SaveEntityResponse) => {
    const message = response.errors?.join(', ') ?? 'Edit conflict: this record was modified. Please refresh and try again.';
    toast.error(message);
  };

  const { mutateAsync: saveEntity } = useSaveEntityMutation(
    workspaceId,
    entityType.id,
    undefined,
    handleConflict,
  );

  const handleCellEdit = useCallback(
    async (row: EntityRow, columnId: string, newValue: unknown, _oldValue: unknown): Promise<boolean> => {
      if (isDraftRow(row)) return false;
      const entity = entities.find((e) => e.id === row._entityId);
      if (!entity) return false;

      const attributeDef: SchemaUUID | undefined = entityType.schema.properties?.[columnId];
      const relationshipDef: RelationshipDefinition | undefined = entityType.relationships?.find(
        (rel) => rel.id === columnId,
      );

      if (attributeDef) {
        const payloadEntry: EntityAttributePrimitivePayload = {
          type: EntityPropertyType.Attribute,
          value: newValue,
          schemaType: attributeDef.key,
        };

        const request = buildEntityUpdatePayload(entity, columnId, { payload: payloadEntry });
        const response = await saveEntity(request);
        const success = !response.errors && !!response.entity;

        if (success) {
          const optionsUpdate = deriveSchemaOptionsUpdate(attributeDef, newValue);
          if (optionsUpdate) {
            const definitionRequest: SaveTypeDefinitionRequest = {
              definition: {
                key: entityType.key,
                id: entityType.id,
                type: EntityTypeRequestDefinition.SaveSchema,
                schema: {
                  ...attributeDef,
                  options: { ...attributeDef.options, ...optionsUpdate },
                },
              },
            };

            EntityTypeService.saveEntityTypeDefinition(session, workspaceId, definitionRequest)
              .then(() => {
                queryClient.invalidateQueries({
                  queryKey: entityKeys.entityTypes.list(workspaceId),
                });
                queryClient.invalidateQueries({
                  queryKey: entityKeys.entityTypes.byKey(entityType.key, workspaceId),
                });
              })
              .catch((error) => {
                console.warn('Failed to sync schema options to type definition:', error);
              });
          }
        }

        return success;
      }

      if (relationshipDef) {
        const relationship = newValue as EntityLink[];
        const relationshipEntry: EntityAttributeRelationPayloadReference = {
          type: EntityPropertyType.Relationship,
          relations: relationship.map((rel) => rel.id),
        };

        const request = buildEntityUpdatePayload(entity, columnId, { payload: relationshipEntry });
        const response = await saveEntity(request);
        return !response.errors && !!response.entity;
      }

      return false;
    },
    [entities, entityType, saveEntity],
  );

  return { handleCellEdit };
}
