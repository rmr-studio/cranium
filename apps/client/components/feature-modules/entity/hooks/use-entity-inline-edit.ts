import { SchemaUUID } from '@/lib/types/common';
import {
  Entity,
  EntityAttributePrimitivePayload,
  EntityAttributeRelationPayloadReference,
  EntityLink,
  EntityPropertyType,
  EntityType,
  RelationshipDefinition,
  SaveEntityRequest,
  SaveEntityResponse,
} from '@/lib/types/entity';
import { useCallback } from 'react';
import { toast } from 'sonner';
import { useSaveEntityMutation } from '@/components/feature-modules/entity/hooks/mutation/instance/use-save-entity-mutation';
import { buildEntityUpdatePayload } from '@/components/feature-modules/entity/util/entity-payload.util';
import { EntityRow, isDraftRow } from '@/components/feature-modules/entity/components/tables/entity-table-utils';

export function useEntityInlineEdit(
  workspaceId: string,
  entityType: EntityType,
  getEntityById: (id: string) => Entity | undefined,
) {
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
      const entity = getEntityById(row._entityId);
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
        return !response.errors && !!response.entity;
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
    [getEntityById, entityType, saveEntity],
  );

  return { handleCellEdit };
}
