import { createIntegrationApi } from '@/lib/api/integration-api';
import { Session } from '@/lib/auth';
import { IntegrationConnectionModel, IntegrationDefinitionModel } from '@/lib/types/integration';
import { normalizeApiError } from '@/lib/util/error/error.util';
import { validateSession, validateUuid } from '@/lib/util/service/service.util';

export class IntegrationService {
  static async getAvailableIntegrations(
    session: Session | null,
  ): Promise<IntegrationDefinitionModel[]> {
    validateSession(session);
    try {
      const api = createIntegrationApi(session);
      return await api.listAvailableIntegrations();
    } catch (error) {
      return await normalizeApiError(error);
    }
  }

  static async getWorkspaceIntegrationStatus(
    session: Session | null,
    workspaceId: string,
  ): Promise<IntegrationConnectionModel[]> {
    validateSession(session);
    validateUuid(workspaceId);
    try {
      const api = createIntegrationApi(session);
      return await api.getWorkspaceIntegrationStatus({ workspaceId });
    } catch (error) {
      return await normalizeApiError(error);
    }
  }

  static async disableIntegration(
    session: Session | null,
    workspaceId: string,
    integrationDefinitionId: string,
  ) {
    validateSession(session);
    validateUuid(workspaceId);
    validateUuid(integrationDefinitionId);
    try {
      const api = createIntegrationApi(session);
      return await api.disableIntegration({
        workspaceId,
        disableIntegrationRequest: { integrationDefinitionId },
      });
    } catch (error) {
      return await normalizeApiError(error);
    }
  }
}
