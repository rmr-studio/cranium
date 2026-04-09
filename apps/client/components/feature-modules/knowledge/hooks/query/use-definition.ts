import { useAuth } from '@/components/provider/auth-context';
import { useAuthenticatedQuery } from '@/hooks/query/use-authenticated-query';
import { AuthenticatedQueryResult } from '@/lib/interfaces/interface';
import { WorkspaceBusinessDefinition } from '@/lib/types/workspace';
import { DefinitionService } from '@/components/feature-modules/knowledge/service/definition.service';
import { definitionKeys } from '@/components/feature-modules/knowledge/hooks/query/definition-query-keys';

export function useDefinition(
  workspaceId?: string,
  definitionId?: string,
): AuthenticatedQueryResult<WorkspaceBusinessDefinition> {
  const { session } = useAuth();

  return useAuthenticatedQuery({
    queryKey: definitionKeys.definition.detail(workspaceId!, definitionId!),
    queryFn: () => DefinitionService.getDefinition(session, workspaceId!, definitionId!),
    staleTime: 5 * 60 * 1000,
    gcTime: 10 * 60 * 1000,
    enabled: !!workspaceId && !!definitionId,
    retry: 2,
    refetchOnWindowFocus: false,
  });
}
