import { SchemaOptions, SchemaType, SchemaUUID } from '@/lib/types/common';
import {
  Entity,
  EntityAttributeRequest,
  EntityPropertyType,
  isRelationshipPayload,
  SaveEntityRequest,
} from '@/lib/types/entity';

export function buildEntityUpdatePayload(
  entity: Entity,
  columnId: string,
  entry: EntityAttributeRequest,
): SaveEntityRequest {
  const payload: Record<string, EntityAttributeRequest> = {};

  Object.entries(entity.payload).forEach(([key, value]) => {
    if (isRelationshipPayload(value.payload)) {
      payload[key] = {
        payload: {
          type: EntityPropertyType.Relationship,
          relations: value.payload.relations.map((rel) => rel.id),
        },
      };
    } else {
      payload[key] = {
        payload: {
          type: EntityPropertyType.Attribute,
          value: value.payload.value,
          schemaType: value.payload.schemaType,
        },
      };
    }
  });

  payload[columnId] = entry;

  return {
    id: entity.id,
    payload,
  };
}

/**
 * Compares a new value against the current schema definition and returns
 * updated SchemaOptions if the value implies changes (e.g. new enum entries),
 * or null if no updates are needed.
 */
export function deriveSchemaOptionsUpdate(
  schema: SchemaUUID,
  newValue: unknown,
): Partial<SchemaOptions> | null {
  switch (schema.key) {
    case SchemaType.MultiSelect: {
      if (!Array.isArray(newValue)) return null;
      const existing = schema.options?._enum ?? [];
      const newEntries = (newValue as unknown[])
        .filter((v): v is string => typeof v === 'string')
        .map((v) => v.trim())
        .filter((v) => v !== '' && !existing.includes(v));
      // Deduplicate within newEntries
      const uniqueNew = [...new Set(newEntries)];
      if (uniqueNew.length === 0) return null;
      return { _enum: [...existing, ...uniqueNew] };
    }
    case SchemaType.Select: {
      if (typeof newValue !== 'string') return null;
      const trimmed = newValue.trim();
      if (trimmed === '') return null;
      const existing = schema.options?._enum ?? [];
      if (existing.includes(trimmed)) return null;
      return { _enum: [...existing, trimmed] };
    }
    default:
      return null;
  }
}
