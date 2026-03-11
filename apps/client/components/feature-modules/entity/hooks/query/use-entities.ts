import { useAuth } from '@/components/provider/auth-context';
import { useAuthenticatedQuery } from '@/hooks/query/use-authenticated-query';
import { AuthenticatedMultiQueryResult, AuthenticatedQueryResult } from '@/lib/interfaces/interface';
import { Entity } from '@/lib/types/entity';
import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { EntityService } from '../../service/entity.service';
import { entityKeys } from './entity-query-keys';

export function useEntities(
  workspaceId?: string,
  typeId?: string,
): AuthenticatedQueryResult<Entity[]> {
  const { session } = useAuth();
  return useAuthenticatedQuery({
    queryKey: entityKeys.entities.list(workspaceId!, typeId!),
    queryFn: () => EntityService.getEntitiesForType(session, workspaceId!, typeId!),
    staleTime: 5 * 60 * 1000,
    enabled: !!workspaceId && !!typeId,
    refetchOnWindowFocus: false,
    refetchOnMount: false,
    gcTime: 10 * 60 * 1000,
  });
}

export function useEntitiesFromManyTypes(
  workspaceId: string,
  typeIds: string[],
): AuthenticatedMultiQueryResult<Entity[]> {
  const { session, loading } = useAuth();

  // Sort typeIds for cache key stability
  const sortedTypeIds = useMemo(() => [...typeIds].sort(), [typeIds]);

  const query = useQuery({
    queryKey: ['entities', workspaceId, 'batch', sortedTypeIds],
    queryFn: async () => {
      const result = await EntityService.getEntitiesForTypes(
        session,
        workspaceId,
        sortedTypeIds,
      );
      return Object.values(result).flat();
    },
    enabled: !!session && !loading && !!workspaceId && sortedTypeIds.length > 0,
  });

  return {
    data: query.data ?? [],
    isLoading: query.isLoading,
    isError: query.isError,
    isLoadingAuth: loading,
  };
}
