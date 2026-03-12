import { useAuth } from '@/components/provider/auth-context';
import { useAuthenticatedQuery } from '@/hooks/query/use-authenticated-query';
import { AuthenticatedMultiQueryResult, AuthenticatedQueryResult } from '@/lib/interfaces/interface';
import { Entity } from '@/lib/types/entity';
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
  const { session } = useAuth();

  // Sort typeIds for cache key stability
  const sortedTypeIds = useMemo(() => [...typeIds].sort(), [typeIds]);

  const result = useAuthenticatedQuery({
    queryKey: entityKeys.entities.batch(workspaceId, sortedTypeIds),
    queryFn: async () => {
      const response = await EntityService.getEntitiesForTypes(
        session,
        workspaceId,
        sortedTypeIds,
      );
      return Object.values(response).flat();
    },
    enabled: !!workspaceId && sortedTypeIds.length > 0,
  });

  return {
    data: result.data ?? [],
    isLoading: result.isLoading,
    isError: result.isError,
    isLoadingAuth: result.isLoadingAuth,
  };
}
