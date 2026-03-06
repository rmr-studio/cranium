import { useAuth } from '@/components/provider/auth-context';
import { AuthenticatedQueryResult } from '@/lib/interfaces/interface';
import { type SemanticMetadataBundle } from '@/lib/types/entity';
import { useQuery } from '@tanstack/react-query';
import { KnowledgeService } from '../../../service/knowledge.service';

export function useSemanticMetadata(
  workspaceId?: string,
  entityTypeId?: string,
): AuthenticatedQueryResult<SemanticMetadataBundle> {
  const { session, loading } = useAuth();
  const query = useQuery({
    queryKey: ['semanticMetadata', workspaceId, entityTypeId],
    queryFn: async () => {
      return await KnowledgeService.getAllMetadata(session, workspaceId!, entityTypeId!);
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
    enabled: !!session && !!workspaceId && !!entityTypeId && !loading,
    refetchOnWindowFocus: false,
    gcTime: 10 * 60 * 1000, // 10 minutes garbage collection
  });

  return {
    isLoadingAuth: loading,
    ...query,
  };
}
