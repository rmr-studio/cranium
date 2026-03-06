import { createKnowledgeApi } from '@/lib/api/knowledge-api';
import { Session } from '@/lib/auth';
import { SemanticMetadataBundle } from '@/lib/types/entity';
import { normalizeApiError } from '@/lib/util/error/error.util';
import { validateSession, validateUuid } from '@/lib/util/service/service.util';

export class KnowledgeService {
  static async getAllMetadata(
    session: Session | null,
    workspaceId: string,
    entityTypeId: string,
  ): Promise<SemanticMetadataBundle> {
    validateSession(session);
    validateUuid(workspaceId);
    validateUuid(entityTypeId);
    const api = createKnowledgeApi(session!);

    try {
      return await api.getAllMetadata({ workspaceId, entityTypeId });
    } catch (error) {
      throw await normalizeApiError(error);
    }
  }
}
