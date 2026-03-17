import { useAuth } from '@/components/provider/auth-context';
import {
  EntityQueryResponse,
  FilterOperator,
  OrderByClause,
  QueryFilter,
  SortDirection,
} from '@/lib/types/entity';
import {
  keepPreviousData,
  useInfiniteQuery,
} from '@tanstack/react-query';
import { SortingState } from '@tanstack/react-table';
import { EntityService } from '../../service/entity.service';
import { entityKeys } from './entity-query-keys';

export const ENTITY_PAGE_SIZE = 50;
const MAX_PAGES = 10;

interface UseEntityQueryOptions {
  workspaceId?: string;
  entityTypeId?: string;
  /** Debounced search term — triggers server-side text search */
  debouncedSearch?: string;
  /** Attribute IDs to search across (OR) */
  searchableAttributeIds?: string[];
  /** Query builder filter from EntityQueryBuilder */
  queryFilter?: QueryFilter;
  /** TanStack Table sorting state — converted to OrderByClause[] for the API */
  sorting?: SortingState;
}

/**
 * Converts TanStack Table SortingState to the backend's OrderByClause[].
 * Exported for testability.
 */
export function sortingStateToOrderBy(sorting: SortingState): OrderByClause[] | undefined {
  if (sorting.length === 0) return undefined;
  return sorting.map((sort) => ({
    attributeId: sort.id,
    direction: sort.desc ? SortDirection.Desc : SortDirection.Asc,
  }));
}

/**
 * Builds a composite QueryFilter from search term and/or query builder filter.
 *
 * - Search only → OR filter across searchable attributes with CONTAINS
 * - Filter only → use as-is
 * - Both → AND(search OR-group, filter)
 */
export function buildCompositeFilter(
  debouncedSearch?: string,
  searchableAttributeIds?: string[],
  queryFilter?: QueryFilter,
): QueryFilter | undefined {
  const searchFilter = buildSearchFilter(debouncedSearch, searchableAttributeIds);

  if (searchFilter && queryFilter) {
    return {
      type: 'AND',
      conditions: [searchFilter, queryFilter],
    } as QueryFilter;
  }

  return searchFilter ?? queryFilter;
}

function buildSearchFilter(
  search?: string,
  attributeIds?: string[],
): QueryFilter | undefined {
  if (!search || !attributeIds || attributeIds.length === 0) {
    return undefined;
  }

  const conditions: QueryFilter[] = attributeIds.map((attributeId) => ({
    type: 'ATTRIBUTE' as const,
    attributeId,
    operator: FilterOperator.Contains,
    value: { kind: 'Literal' as const, value: search },
  }));

  if (conditions.length === 1) {
    return conditions[0];
  }

  return {
    type: 'OR',
    conditions,
  } as QueryFilter;
}

/**
 * Determines the next page offset for infinite scroll.
 * Exported for testability.
 */
export function getNextPageParam(
  lastPage: EntityQueryResponse,
  allPages: EntityQueryResponse[],
): number | undefined {
  if (!lastPage.hasNextPage) return undefined;
  return allPages.length * ENTITY_PAGE_SIZE;
}

/**
 * Paginated entity query hook using useInfiniteQuery.
 *
 * - Single composed queryKey includes search + filter state
 * - TanStack auto-resets pagination when queryKey changes (search/filter change)
 * - keepPreviousData shows stale results during refetch for smooth UX
 * - maxPages caps memory at MAX_PAGES × ENTITY_PAGE_SIZE entities
 */
export function useEntityQuery({
  workspaceId,
  entityTypeId,
  debouncedSearch,
  searchableAttributeIds,
  queryFilter,
  sorting,
}: UseEntityQueryOptions) {
  const { session, loading } = useAuth();

  const compositeFilter = buildCompositeFilter(
    debouncedSearch,
    searchableAttributeIds,
    queryFilter,
  );

  const orderBy = sortingStateToOrderBy(sorting ?? []);

  return useInfiniteQuery({
    queryKey: entityKeys.entities.query(
      workspaceId ?? '',
      entityTypeId ?? '',
      debouncedSearch || undefined,
      compositeFilter,
      orderBy,
    ),
    queryFn: ({ pageParam = 0 }) =>
      EntityService.queryEntities(
        session,
        workspaceId!,
        entityTypeId!,
        { limit: ENTITY_PAGE_SIZE, offset: pageParam, orderBy },
        compositeFilter,
      ),
    initialPageParam: 0,
    getNextPageParam,
    maxPages: MAX_PAGES,
    placeholderData: keepPreviousData,
    enabled: !!session && !loading && !!workspaceId && !!entityTypeId,
    staleTime: 5 * 60 * 1000,
    gcTime: 10 * 60 * 1000,
    refetchOnWindowFocus: false,
    refetchOnMount: false,
  });
}
