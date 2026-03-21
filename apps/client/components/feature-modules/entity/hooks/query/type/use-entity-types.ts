import { useAuth } from '@/components/provider/auth-context';
import { useAuthenticatedQuery } from '@/hooks/query/use-authenticated-query';
import { AuthenticatedQueryResult } from '@/lib/interfaces/interface';
import { type EntityType } from '@/lib/types/entity';
import { EntityTypeService } from '../../../service/entity-type.service';
import { entityKeys } from '@/components/feature-modules/entity/hooks/query/entity-query-keys';

export function useEntityTypes(workspaceId?: string): AuthenticatedQueryResult<EntityType[]> {
  const { session } = useAuth();
  return useAuthenticatedQuery({
    queryKey: entityKeys.entityTypes.list(workspaceId!),
    queryFn: () => EntityTypeService.getEntityTypes(session, workspaceId!),
    staleTime: 5 * 60 * 1000,
    enabled: !!workspaceId,
    refetchOnWindowFocus: false,
    refetchOnMount: false,
    gcTime: 10 * 60 * 1000,
  });
}

export function useEntityTypeByKey(
  key?: string,
  workspaceId?: string,
  include?: string[],
): AuthenticatedQueryResult<EntityType> {
  const { session } = useAuth();
  return useAuthenticatedQuery({
    queryKey: entityKeys.entityTypes.byKey(key ?? '', workspaceId ?? '', include),
    queryFn: () => EntityTypeService.getEntityTypeByKey(session, workspaceId ?? '', key ?? '', include),
    staleTime: 10 * 60 * 1000,
    enabled: !!key && !!workspaceId,
    refetchOnWindowFocus: false,
    retry: 1,
    gcTime: 30 * 60 * 1000,
  });
}
