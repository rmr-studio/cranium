import { useAuth } from "@/components/provider/auth-context";
import { AuthenticatedQueryResult } from "@/lib/interfaces/interface";
import { type EntityType } from "@/lib/types/entity";
import { useQuery } from "@tanstack/react-query";
import { EntityTypeService } from "../../../service/entity-type.service";

export function useEntityTypes(workspaceId?: string): AuthenticatedQueryResult<EntityType[]> {
  const { session, loading } = useAuth();
  const query = useQuery({
    queryKey: ['entityTypes', workspaceId],
    queryFn: async () => {
      return await EntityTypeService.getEntityTypes(session, workspaceId!); // non-null assertion as enabled ensures workspaceId is defined
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
    enabled: !!session && !!workspaceId && !loading,
    refetchOnWindowFocus: false,
    refetchOnMount: false,
    gcTime: 10 * 60 * 1000, // 10 minutes garbage collection
  });

  return {
    isLoadingAuth: loading,
    ...query,
  };
}

export function useEntityTypeByKey(
  key: string,
  workspaceId?: string,
  include?: string[],
): AuthenticatedQueryResult<EntityType> {
  const { session, loading } = useAuth();
  const query = useQuery({
    queryKey: ['entityType', key, workspaceId, include],
    queryFn: async () => {
      return await EntityTypeService.getEntityTypeByKey(session, workspaceId!, key, include); // non-null assertion as enabled ensures workspaceId is defined;
    },
    staleTime: 10 * 60 * 1000, // 10 minutes
    enabled: !!key && !!workspaceId && !!session && !loading,
    refetchOnWindowFocus: false,
    retry: 1,
    gcTime: 30 * 60 * 1000, // 30 minutes garbage collection
  });

  return {
    isLoadingAuth: loading,
    ...query,
  };
}
