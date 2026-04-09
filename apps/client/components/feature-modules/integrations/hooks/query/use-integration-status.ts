import { useAuth } from '@/components/provider/auth-context';
import { useAuthenticatedQuery } from '@/hooks/query/use-authenticated-query';
import { AuthenticatedQueryResult } from '@/lib/interfaces/interface';
import { IntegrationConnectionModel } from '@/lib/types/integration';
import { IntegrationService } from '@/components/feature-modules/integrations/service/integration.service';
import { integrationKeys } from '@/components/feature-modules/integrations/hooks/query/integration-query-keys';

export function useIntegrationStatus(
  workspaceId?: string,
): AuthenticatedQueryResult<IntegrationConnectionModel[]> {
  const { session } = useAuth();
  return useAuthenticatedQuery({
    queryKey: integrationKeys.connections.byWorkspace(workspaceId!),
    queryFn: () => IntegrationService.getWorkspaceIntegrationStatus(session, workspaceId!),
    staleTime: 60 * 1000,
    gcTime: 5 * 60 * 1000,
    enabled: !!workspaceId,
    refetchOnWindowFocus: true,
  });
}
