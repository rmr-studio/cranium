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
import { useSaveEntityMutation } from './mutation/instance/use-save-entity-mutation';
import { buildEntityUpdatePayload } from '../util/entity-payload.util';
import { EntityRow, isDraftRow } from '../components/tables/entity-table-utils';

export function useEntityInlineEdit(
  workspaceId: string,
  entityType: EntityType,
  entities: Entity[],
) {
  const handleConflict = (_request: SaveEntityRequest, _response: SaveEntityResponse) => {};

  const { mutateAsync: saveEntity } = useSaveEntityMutation(
    workspaceId,
    entityType.id,
    undefined,
    handleConflict,
  );

  const handleCellEdit = useCallback(
    async (row: EntityRow, columnId: string, newValue: any, _oldValue: any): Promise<boolean> => {
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
        return !response.errors && !!response.entity;
      }

      if (relationshipDef) {
        const relationship: EntityLink[] = newValue;
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
