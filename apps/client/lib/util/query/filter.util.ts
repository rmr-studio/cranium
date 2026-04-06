import {
  FilterOperator,
  FilterValueKind,
  QueryFilter,
  QueryFilterType,
} from '@/lib/types/entity';

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
      conditions: [searchFilter, queryFilter],
      type: QueryFilterType.And,
    } as QueryFilter;
  }

  return searchFilter ?? queryFilter;
}

function buildSearchFilter(search?: string, attributeIds?: string[]): QueryFilter | undefined {
  if (!search || !attributeIds || attributeIds.length === 0) {
    return undefined;
  }

  const conditions: QueryFilter[] = attributeIds.map((attributeId) => {
    const filter: QueryFilter = {
      type: QueryFilterType.Attribute,
      attributeId,
      operator: FilterOperator.Contains,
      // @ts-expect-error - TS doesn't understand the backend's sealed class hierarchy with discriminator properties
      value: { kind: FilterValueKind.Literal, value: search },
    };

    return filter;
  });

  if (conditions.length === 1) {
    return conditions[0];
  }

  return {
    type: QueryFilterType.Or,
    conditions,
  } as QueryFilter;
}
