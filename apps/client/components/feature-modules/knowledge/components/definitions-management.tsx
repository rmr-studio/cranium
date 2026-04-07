'use client';

import { Button } from '@riven/ui/button';
import { Input } from '@riven/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { DefinitionCategory } from '@/lib/types/workspace';
import { cn } from '@/lib/util/utils';
import { Plus, Search } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { FC, useMemo, useState } from 'react';
import { useDefinitions } from '@/components/feature-modules/knowledge/hooks/query/use-definitions';
import { DefinitionRow } from '@/components/feature-modules/knowledge/components/definitions-management-row';
import { DefinitionsSkeleton } from '@/components/feature-modules/knowledge/components/definitions-management-skeleton';
import { DefinitionsEmpty } from '@/components/feature-modules/knowledge/components/definitions-management-empty';

const CATEGORY_FILTERS: Array<{ value: DefinitionCategory | undefined; label: string }> = [
  { value: undefined, label: 'All' },
  { value: DefinitionCategory.Metric, label: 'Metrics' },
  { value: DefinitionCategory.Segment, label: 'Segments' },
  { value: DefinitionCategory.Status, label: 'Statuses' },
  { value: DefinitionCategory.LifecycleStage, label: 'Lifecycle' },
  { value: DefinitionCategory.Custom, label: 'Custom' },
];

export const DefinitionsManagement: FC = () => {
  const { workspaceId } = useParams<{ workspaceId: string }>();

  const [search, setSearch] = useState('');
  const [categoryFilter, setCategoryFilter] = useState<DefinitionCategory | undefined>(undefined);

  const { data: definitions, isLoading, isLoadingAuth, isError } = useDefinitions(
    workspaceId,
    undefined,
    categoryFilter,
  );

  const filtered = useMemo(() => {
    if (!definitions) return [];
    if (!search.trim()) return definitions;
    const q = search.toLowerCase();
    return definitions.filter(
      (d) =>
        d.term.toLowerCase().includes(q) ||
        d.definition.toLowerCase().includes(q),
    );
  }, [definitions, search]);

  if (isLoading || isLoadingAuth) {
    return (
      <div className="mx-auto max-w-5xl px-8 py-8">
        <Skeleton className="mb-6 h-8 w-48" />
        <DefinitionsSkeleton />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="mx-auto max-w-5xl px-8 py-8">
        <p className="text-sm text-muted-foreground">Failed to load definitions</p>
      </div>
    );
  }

  const hasActiveFilters = !!search.trim() || !!categoryFilter;
  const isEmpty = !definitions || definitions.length === 0;

  return (
    <div className="mx-auto max-w-5xl px-8 py-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-heading text-3xl font-bold tracking-tight">Definitions</h1>
        <Button asChild size="sm">
          <Link href={`/dashboard/workspace/${workspaceId}/definitions/new`}>
            <Plus className="mr-1.5 size-4" />
            New Definition
          </Link>
        </Button>
      </div>

      {isEmpty && !hasActiveFilters ? (
        <DefinitionsEmpty workspaceId={workspaceId} />
      ) : (
        <>
          <div className="mb-4 flex items-center gap-3">
            <div className="relative flex-1">
              <Search className="text-muted-foreground absolute left-3 top-1/2 size-4 -translate-y-1/2" />
              <Input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search definitions..."
                className="pl-9"
              />
            </div>
            <div className="flex gap-1">
              {CATEGORY_FILTERS.map(({ value, label }) => (
                <button
                  key={label}
                  type="button"
                  onClick={() => setCategoryFilter(value)}
                  className={cn(
                    'rounded-full px-3 py-1 text-xs transition-colors',
                    categoryFilter === value
                      ? 'bg-foreground text-background'
                      : 'bg-muted text-muted-foreground hover:text-foreground',
                  )}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>

          <div className="bg-card rounded-lg border">
            {filtered.length === 0 ? (
              <p className="text-muted-foreground px-4 py-8 text-center text-sm">
                No definitions match your {search.trim() ? 'search' : 'filters'}.
              </p>
            ) : (
              filtered.map((def) => (
                <DefinitionRow
                  key={def.id}
                  definition={def}
                  workspaceId={workspaceId}
                />
              ))
            )}
          </div>
        </>
      )}
    </div>
  );
};
