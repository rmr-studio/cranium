
import { createEntityApi } from '@/lib/api/entity-api';
import { Session } from '@/lib/auth';
import { ResponseError } from '@/lib/types';
import {
  DeleteEntityResponse,
  Entity,
  EntityQueryRequest,
  EntityQueryResponse,
  QueryFilter,
  QueryPagination,
  SaveEntityRequest,
  SaveEntityResponse,
} from '@/lib/types/entity';
import { fromError, isSaveEntityResponse, normalizeApiError } from '@/lib/util/error/error.util';
import { validateSession, validateUuid } from '@/lib/util/service/service.util';

export class EntityService {
  /**
   * Create a new entity instance for a given entity type
   */
  static async saveEntity(
    session: Session | null,
    workspaceId: string,
    entityTypeId: string,
    request: SaveEntityRequest,
  ): Promise<SaveEntityResponse> {
    validateSession(session);
    validateUuid(workspaceId);
    validateUuid(entityTypeId);
    const api = createEntityApi(session);

    try {
      return await api.saveEntity({
        workspaceId,
        entityTypeId,
        saveEntityRequest: request,
      });
    } catch (error) {
      if (error instanceof ResponseError) {
        const body = await error.response.json();

        // 409 (impact conflicts) always return SaveEntityResponse
        if (error.response.status === 409) {
          return body as SaveEntityResponse;
        }

        // 400 can be either SaveEntityResponse (schema validation) or
        // ErrorResponse (relationship constraint violations)
        if (error.response.status === 400) {
          if (isSaveEntityResponse(body)) {
            return body as SaveEntityResponse;
          }
          // Re-throw as a structured error for mutation onError handling
          throw fromError({
            message: body.message ?? 'Validation failed',
            status: error.response.status,
            error: body.error ?? 'VALIDATION_ERROR',
          });
        }
      }
      throw error;
    }
  }

  /**
   * Get all entity instances
   */
  static async getEntitiesForType(
    session: Session | null,
    workspaceId: string,
    typeId: string,
  ): Promise<Entity[]> {
    validateSession(session);
    validateUuid(workspaceId);
    validateUuid(typeId);
    const api = createEntityApi(session!);
    return api.getEntityByTypeIdForWorkspace({ workspaceId, id: typeId });
  }

  /**
   * Get all entity instances for multiple keys
   */
  static async getEntitiesForTypes(
    session: Session | null,
    workspaceId: string,
    typeIds: string[],
  ): Promise<Record<string, Entity[]>> {
    validateSession(session);
    validateUuid(workspaceId);
    typeIds.forEach((id) => validateUuid(id));
    const api = createEntityApi(session!);
    return api.getEntityByTypeIdInForWorkspace({ workspaceId, ids: typeIds });
  }

  /**
   * Query entities with pagination and optional filtering.
   */
  static async queryEntities(
    session: Session | null,
    workspaceId: string,
    entityTypeId: string,
    pagination: QueryPagination,
    filter?: QueryFilter,
  ): Promise<EntityQueryResponse> {
    validateSession(session);
    validateUuid(workspaceId);
    validateUuid(entityTypeId);
    const api = createEntityApi(session!);

    try {
      const request: EntityQueryRequest = {
        pagination,
        includeCount: false,
        maxDepth: 1,
        ...(filter ? { filter } : {}),
      };

      return await api.queryEntities({
        workspaceId,
        entityTypeId,
        entityQueryRequest: request,
      });
    } catch (error) {
      return await normalizeApiError(error);
    }
  }

  static async deleteEntities(
    session: Session | null,
    workspaceId: string,
    entityIds: string[],
  ): Promise<DeleteEntityResponse> {
    validateSession(session);
    validateUuid(workspaceId);
    entityIds.forEach((id) => validateUuid(id));
    const api = createEntityApi(session!);
    return api.deleteEntity({ workspaceId, requestBody: entityIds });
  }
}
