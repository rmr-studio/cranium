import { useAuth } from '@/components/provider/auth-context';
import { useAuthenticatedQuery } from '@/hooks/query/use-authenticated-query';
import { AuthenticatedQueryResult } from '@/lib/interfaces/interface';
import {
  WorkspaceBusinessDefinition,
  DefinitionStatus,
  DefinitionCategory,
} from '@/lib/types/workspace';
import { DefinitionService } from '@/components/feature-modules/knowledge/service/definition.service';
import { definitionKeys } from '@/components/feature-modules/knowledge/hooks/query/definition-query-keys';

export function useDefinitions(
  workspaceId?: string,
  status?: DefinitionStatus,
  category?: DefinitionCategory,
): AuthenticatedQueryResult<WorkspaceBusinessDefinition[]> {
  const { session } = useAuth();

  return useAuthenticatedQuery({
    queryKey: definitionKeys.definitions.list(workspaceId!, status, category),
    queryFn: () => DefinitionService.listDefinitions(session, workspaceId!, status, category),
    staleTime: 5 * 60 * 1000,
    gcTime: 10 * 60 * 1000,
    enabled: !!workspaceId,
    retry: 2,
    refetchOnWindowFocus: false,
  });
}
