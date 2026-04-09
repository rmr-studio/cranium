import { createKnowledgeApi } from '@/lib/api/knowledge-api';
import { Session } from '@/lib/auth';
import {
  CreateBusinessDefinitionRequest,
  UpdateBusinessDefinitionRequest,
  WorkspaceBusinessDefinition,
  DefinitionStatus,
  DefinitionCategory,
} from '@/lib/types/models';
import { normalizeApiError } from '@/lib/util/error/error.util';
import { validateSession, validateUuid } from '@/lib/util/service/service.util';

export class DefinitionService {
  static async listDefinitions(
    session: Session | null,
    workspaceId: string,
    status?: DefinitionStatus,
    category?: DefinitionCategory,
  ): Promise<WorkspaceBusinessDefinition[]> {
    validateSession(session);
    validateUuid(workspaceId);
    const api = createKnowledgeApi(session);

    try {
      return await api.listDefinitions({ workspaceId, status, category });
    } catch (error) {
      return await normalizeApiError(error);
    }
  }

  static async getDefinition(
    session: Session | null,
    workspaceId: string,
    id: string,
  ): Promise<WorkspaceBusinessDefinition> {
    validateSession(session);
    validateUuid(workspaceId);
    validateUuid(id);
    const api = createKnowledgeApi(session);

    try {
      return await api.getDefinition({ workspaceId, id });
    } catch (error) {
      return await normalizeApiError(error);
    }
  }

  static async createDefinition(
    session: Session | null,
    workspaceId: string,
    request: CreateBusinessDefinitionRequest,
  ): Promise<WorkspaceBusinessDefinition> {
    validateSession(session);
    validateUuid(workspaceId);
    const api = createKnowledgeApi(session);

    try {
      return await api.createDefinition({
        workspaceId,
        createBusinessDefinitionRequest: request,
      });
    } catch (error) {
      return await normalizeApiError(error);
    }
  }

  static async updateDefinition(
    session: Session | null,
    workspaceId: string,
    id: string,
    request: UpdateBusinessDefinitionRequest,
  ): Promise<WorkspaceBusinessDefinition> {
    validateSession(session);
    validateUuid(workspaceId);
    validateUuid(id);
    const api = createKnowledgeApi(session);

    try {
      return await api.updateDefinition({
        workspaceId,
        id,
        updateBusinessDefinitionRequest: request,
      });
    } catch (error) {
      return await normalizeApiError(error);
    }
  }

  static async deleteDefinition(
    session: Session | null,
    workspaceId: string,
    id: string,
  ): Promise<void> {
    validateSession(session);
    validateUuid(workspaceId);
    validateUuid(id);
    const api = createKnowledgeApi(session);

    try {
      await api.deleteDefinition({ workspaceId, id });
    } catch (error) {
      await normalizeApiError(error);
    }
  }
}
