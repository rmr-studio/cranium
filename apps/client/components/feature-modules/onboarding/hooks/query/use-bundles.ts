import { useAuth } from '@/components/provider/auth-context';
import { createTemplatesApi } from '@/lib/api/templates-api';
import { BundleDetail, ManifestSummary } from '@/lib/types';
import { useQuery } from '@tanstack/react-query';

export interface UseBundlesResult {
  bundles: BundleDetail[];
  templates: ManifestSummary[];
  isLoading: boolean;
  isLoadingAuth: boolean;
  error: Error | null;
}

export function useBundles(): UseBundlesResult {
  const { session, loading } = useAuth();

  const { data, isLoading, error } = useQuery({
    queryKey: ['bundles'],
    queryFn: async () => {
      const api = createTemplatesApi(session!);
      const [bundles, templates] = await Promise.all([
        api.listBundles(),
        api.listTemplates(),
      ]);
      return { bundles, templates };
    },
    enabled: !!session && !loading,
    staleTime: 10 * 60 * 1000,
    gcTime: 30 * 60 * 1000,
    retry: 2,
  });

  return {
    bundles: data?.bundles ?? [],
    templates: data?.templates ?? [],
    isLoading: loading || isLoading,
    isLoadingAuth: loading,
    error: error as Error | null,
  };
}
