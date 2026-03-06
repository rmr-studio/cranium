import { createEntityApi } from '@/lib/api/entity-api';
import { Session } from '@/lib/auth';
import { ResponseError as OpenApiResponseError } from '@/lib/types';
import {
  CreateEntityTypeRequest,
  DeleteTypeDefinitionRequest,
  EntityType,
  EntityTypeImpactResponse,
  SaveTypeDefinitionRequest,
  UpdateEntityTypeConfigurationRequest,
} from '@/lib/types/entity';
import { normalizeApiError } from '@/lib/util/error/error.util';
import { validateSession, validateUuid } from '@/lib/util/service/service.util';

export class EntityTypeService {
  static async getEntityTypes(
    session: Session | null,
    workspaceId: string,
    include?: string[],
  ): Promise<EntityType[]> {
    validateSession(session);
    validateUuid(workspaceId);
    const api = createEntityApi(session!);

    try {
      return await api.getEntityTypesForWorkspace({ workspaceId });
    } catch (error) {
      throw await normalizeApiError(error);
    }
  }

  static async getEntityTypeByKey(
    session: Session | null,
    workspaceId: string,
    key: string,
    include?: string[],
  ): Promise<EntityType> {
    validateSession(session);
    validateUuid(workspaceId);
    const api = createEntityApi(session!);

    try {
      return await api.getEntityTypeByKeyForWorkspace({ workspaceId, key });
    } catch (error) {
      throw await normalizeApiError(error);
    }
  }

  static async publishEntityType(
    session: Session | null,
    workspaceId: string,
    request: CreateEntityTypeRequest,
  ): Promise<EntityType> {
    validateSession(session);
    validateUuid(workspaceId);
    const api = createEntityApi(session!);

    try {
      return await api.createEntityType({ workspaceId, createEntityTypeRequest: request });
    } catch (error) {
      throw await normalizeApiError(error);
    }
  }

  static async saveEntityTypeConfiguration(
    session: Session | null,
    workspaceId: string,
    request: UpdateEntityTypeConfigurationRequest,
  ): Promise<EntityType> {
    validateSession(session);
    validateUuid(workspaceId);
    const api = createEntityApi(session!);

    try {
      return await api.updateEntityType({
        workspaceId,
        updateEntityTypeConfigurationRequest: request,
      });
    } catch (error) {
      throw await normalizeApiError(error);
    }
  }

  /**
   * Resolves an entity type key to its UUID from a known list of entity types.
   * Use this to convert user-facing keys to API-required UUIDs in the service layer.
   */
  static resolveEntityTypeId(entityTypes: EntityType[], key: string): string {
    const found = entityTypes.find((et) => et.key === key);
    if (!found) {
      throw new Error(`Entity type with key "${key}" not found`);
    }
    return found.id;
  }

  static async removeEntityTypeDefinition(
    session: Session | null,
    workspaceId: string,
    definition: DeleteTypeDefinitionRequest,
    impactConfirmed: boolean = false,
  ): Promise<EntityTypeImpactResponse> {
    validateSession(session);
    validateUuid(workspaceId);
    const api = createEntityApi(session!);

    try {
      return await api.deleteEntityTypeDefinition({
        workspaceId,
        deleteTypeDefinitionRequest: definition,
        impactConfirmed,
      });
    } catch (error) {
      // Handle impact confirmation flow (409 returns impact details)
      if (error instanceof OpenApiResponseError && error.response.status === 409) {
        return await error.response.json();
      }
      throw await normalizeApiError(error);
    }
  }

  /**
   * This will handle saving (ie. Publishing/Updating) new entity schema attributes and definitions
   */
  static async saveEntityTypeDefinition(
    session: Session | null,
    workspaceId: string,
    definition: SaveTypeDefinitionRequest,
    impactConfirmed: boolean = false,
  ): Promise<EntityTypeImpactResponse> {
    validateSession(session);
    validateUuid(workspaceId);
    const api = createEntityApi(session!);

    try {
      return await api.saveEntityTypeDefinition({
        workspaceId,
        saveTypeDefinitionRequest: definition,
        impactConfirmed,
      });
    } catch (error) {
      // Handle impact confirmation flow (409 returns impact details)
      if (error instanceof OpenApiResponseError && error.response.status === 409) {
        return await error.response.json();
      }
      throw await normalizeApiError(error);
    }
  }

  static async deleteEntityType(
    session: Session | null,
    workspaceId: string,
    entityTypeKey: string,
    impactConfirmed: boolean = false,
  ): Promise<EntityTypeImpactResponse> {
    validateSession(session);
    validateUuid(workspaceId);
    const api = createEntityApi(session!);

    try {
      return await api.deleteEntityTypeByKey({
        workspaceId,
        key: entityTypeKey,
        impactConfirmed,
      });
    } catch (error) {
      // Handle impact confirmation flow (409 returns impact details)
      if (error instanceof OpenApiResponseError && error.response.status === 409) {
        return await error.response.json();
      }
      throw await normalizeApiError(error);
    }
  }
}
