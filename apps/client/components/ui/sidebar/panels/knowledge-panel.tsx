'use client';

import { useDefinitions } from '@/components/feature-modules/knowledge/hooks/query/use-definitions';
import { useWorkspaceStore } from '@/components/feature-modules/workspace/provider/workspace-provider';
import { DefinitionCategory, WorkspaceBusinessDefinition } from '@/lib/types/workspace';
import { Button } from '@cranium/ui/button';
import { Input } from '@cranium/ui/input';
import { cn } from '@cranium/utils';
import { BookOpen, Plus, Search } from 'lucide-react';
import Link from 'next/link';
import { useMemo, useState } from 'react';
import { Skeleton } from '@/components/ui/skeleton';

const CATEGORY_LABELS: Record<DefinitionCategory, string> = {
  [DefinitionCategory.Metric]: 'Metric',
  [DefinitionCategory.Segment]: 'Segment',
  [DefinitionCategory.Status]: 'Status',
  [DefinitionCategory.LifecycleStage]: 'Lifecycle',
  [DefinitionCategory.Custom]: 'Custom',
};

const CATEGORY_FILTERS: Array<{ value: DefinitionCategory | undefined; label: string }> = [
  { value: undefined, label: 'All' },
  { value: DefinitionCategory.Metric, label: 'Metrics' },
  { value: DefinitionCategory.Segment, label: 'Segments' },
  { value: DefinitionCategory.Status, label: 'Statuses' },
  { value: DefinitionCategory.LifecycleStage, label: 'Lifecycle' },
  { value: DefinitionCategory.Custom, label: 'Custom' },
];

function PanelDefinitionCard({
  definition,
  workspaceId,
}: {
  definition: WorkspaceBusinessDefinition;
  workspaceId: string;
}) {
  return (
    <Link
      href={`/dashboard/workspace/${workspaceId}/definitions/${definition.id}`}
      className={cn(
        'flex flex-col gap-1 rounded-md px-3 py-2.5 text-sm transition-colors',
        'text-sidebar-foreground/70 hover:bg-sidebar-accent hover:text-sidebar-foreground',
      )}
    >
      <div className="flex items-center gap-2">
        <span className="truncate font-medium">{definition.term}</span>
        <span className="bg-muted text-muted-foreground shrink-0 rounded-full px-1.5 py-0.5 text-xs">
          {CATEGORY_LABELS[definition.category]}
        </span>
      </div>
      <p className="line-clamp-1 text-xs text-muted-foreground">
        {definition.definition}
      </p>
    </Link>
  );
}

export function KnowledgePanel() {
  const selectedWorkspaceId = useWorkspaceStore((s) => s.selectedWorkspaceId);
  const [search, setSearch] = useState('');
  const [categoryFilter, setCategoryFilter] = useState<DefinitionCategory | undefined>(undefined);

  const { data, isLoading, isError, isLoadingAuth } = useDefinitions(selectedWorkspaceId, undefined, categoryFilter);

  const filtered = useMemo(() => {
    if (!data) return [];
    if (!search.trim()) return data;
    const q = search.toLowerCase();
    return data.filter(
      (d) => d.term.toLowerCase().includes(q) || d.definition.toLowerCase().includes(q),
    );
  }, [data, search]);

  const basePath = `/dashboard/workspace/${selectedWorkspaceId}/definitions`;

  if (!selectedWorkspaceId) return null;

  if (isLoadingAuth) {
    return (
      <div className="flex flex-col gap-2">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="flex flex-col gap-1.5 px-3 py-2.5">
            <Skeleton className="h-4 w-3/4 rounded" />
            <Skeleton className="h-3 w-1/2 rounded" />
          </div>
        ))}
      </div>
    );
  }

  if (isError) return null;

  return (
    <div className="flex flex-col gap-3">
      {/* Search */}
      <div className="relative">
        <Search className="absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
        <Input
          placeholder="Search definitions..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="pl-9"
          aria-label="Search definitions"
        />
      </div>

      {/* Category filter pills */}
      <div className="flex flex-wrap gap-1">
        {CATEGORY_FILTERS.map(({ value, label }) => (
          <button
            key={label}
            type="button"
            onClick={() => setCategoryFilter(value)}
            className={cn(
              'rounded-full px-2 py-0.5 text-xs transition-colors',
              categoryFilter === value
                ? 'bg-foreground text-background'
                : 'bg-muted text-muted-foreground hover:text-foreground',
            )}
          >
            {label}
          </button>
        ))}
      </div>

      {/* View All */}
      <Link
        href={basePath}
        className="flex items-center gap-2 rounded-md px-3 py-2 text-sm text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground"
      >
        <BookOpen className="size-4" />
        View All Definitions
      </Link>

      {/* New Definition */}
      <Button variant="default" size="sm" className="w-full" asChild>
        <Link href={`${basePath}/new`}>
          <Plus className="mr-1.5 size-3.5" />
          New Definition
        </Link>
      </Button>

      {/* Content */}
      {isLoading ? (
        <div className="flex flex-col gap-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="flex flex-col gap-1.5 px-3 py-2.5">
              <Skeleton className="h-4 w-3/4 rounded" />
              <Skeleton className="h-3 w-1/2 rounded" />
            </div>
          ))}
        </div>
      ) : filtered.length === 0 ? (
        search ? (
          <div className="flex flex-col items-center gap-2 py-8 text-center">
            <p className="text-sm text-muted-foreground">
              No definitions matching &apos;{search}&apos;
            </p>
            <button
              onClick={() => setSearch('')}
              className="text-sm text-primary hover:underline"
            >
              Clear search
            </button>
          </div>
        ) : (
          <div className="flex flex-col items-center gap-3 py-8 text-center">
            <BookOpen className="size-8 text-muted-foreground/40" />
            <p className="text-sm text-muted-foreground">
              No definitions yet. Define your business language to create a shared vocabulary.
            </p>
            <Button variant="default" size="sm" asChild>
              <Link href={`${basePath}/new`}>
                <Plus className="mr-1.5 size-3.5" />
                New Definition
              </Link>
            </Button>
          </div>
        )
      ) : (
        <div className="flex flex-col gap-1">
          {filtered.map((def) => (
            <PanelDefinitionCard
              key={def.id}
              definition={def}
              workspaceId={selectedWorkspaceId}
            />
          ))}
        </div>
      )}
    </div>
  );
}
