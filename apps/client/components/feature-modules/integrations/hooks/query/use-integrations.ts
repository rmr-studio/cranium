import { integrationKeys } from '@/components/feature-modules/integrations/hooks/query/integration-query-keys';
import { IntegrationService } from '@/components/feature-modules/integrations/service/integration.service';
import { useAuth } from '@/components/provider/auth-context';
import { IntegrationDefinitionModel } from '@/lib/types/integration';
import { useQuery } from '@tanstack/react-query';

export function useIntegrations() {
  const { session } = useAuth();
  return useQuery<IntegrationDefinitionModel[]>({
    queryKey: integrationKeys.definitions.all,
    queryFn: () => IntegrationService.getAvailableIntegrations(session),
    staleTime: 10 * 60 * 1000,
    gcTime: 30 * 60 * 1000,
    retry: 1,
    refetchOnWindowFocus: false,
  });
}
