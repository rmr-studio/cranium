import { useAuth } from '@/components/provider/auth-context';
import { useAuthenticatedQuery } from '@/hooks/query/use-authenticated-query';
import { AuthenticatedQueryResult } from '@/lib/interfaces/interface';
import { type SemanticMetadataBundle } from '@/lib/types/entity';
import { KnowledgeService } from '../../../service/knowledge.service';
import { entityKeys } from '../entity-query-keys';

export function useSemanticMetadata(
  workspaceId?: string,
  entityTypeId?: string,
): AuthenticatedQueryResult<SemanticMetadataBundle> {
  const { session } = useAuth();
  return useAuthenticatedQuery({
    queryKey: entityKeys.semanticMetadata(workspaceId!, entityTypeId!),
    queryFn: () => KnowledgeService.getAllMetadata(session, workspaceId!, entityTypeId!),
    staleTime: 5 * 60 * 1000,
    enabled: !!workspaceId && !!entityTypeId,
    refetchOnWindowFocus: false,
    gcTime: 10 * 60 * 1000,
  });
}
