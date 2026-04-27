import { createDevApi } from '@/lib/api/dev-api';
import { Session } from '@/lib/auth';
import { TemplateInstallationResponse } from '@/lib/types';
import { normalizeApiError } from '@/lib/util/error/error.util';

export const DevService = {
  async reinstallTemplate(
    session: Session,
    workspaceId: string,
    templateKey: string,
  ): Promise<TemplateInstallationResponse> {
    try {
      const api = createDevApi(session);
      return await api.reinstallTemplate({ workspaceId, templateKey });
    } catch (error) {
      return normalizeApiError(error);
    }
  },
};
