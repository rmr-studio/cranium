'use client';

import { useDefinitions } from '@/components/feature-modules/knowledge/hooks/query/use-definitions';
import { useWorkspaceStore } from '@/components/feature-modules/workspace/provider/workspace-provider';
import { Skeleton } from '@/components/ui/skeleton';
import { DefinitionCategory } from '@/lib/types/workspace';
import { BookOpen } from 'lucide-react';
import Link from 'next/link';
import { FC, useMemo } from 'react';

const CATEGORY_LABELS: Record<DefinitionCategory, string> = {
  [DefinitionCategory.Metric]: 'Metric',
  [DefinitionCategory.Segment]: 'Segment',
  [DefinitionCategory.Status]: 'Status',
  [DefinitionCategory.LifecycleStage]: 'Lifecycle',
  [DefinitionCategory.Custom]: 'Custom',
};

interface Props {
  entityTypeId: string;
}

export const LinkedDefinitionsSection: FC<Props> = ({ entityTypeId }) => {
  const selectedWorkspaceId = useWorkspaceStore((s) => s.selectedWorkspaceId);

  if (!selectedWorkspaceId) return null;

  const { data: definitions, isLoading, isError, isLoadingAuth } = useDefinitions(selectedWorkspaceId);

  const linked = useMemo(() => {
    if (!definitions) return [];
    return definitions.filter((d) => d.entityTypeRefs?.includes(entityTypeId));
  }, [definitions, entityTypeId]);

  if (isLoading || isLoadingAuth) {
    return <Skeleton className="h-20 w-full rounded" />;
  }
  if (isError) return null;

  return (
    <div className="p-5">
      <p className="mb-3.5 text-xs font-medium uppercase tracking-widest text-muted-foreground/70">
        Business Definitions
      </p>
      {linked.length > 0 ? (
        <div className="flex flex-col gap-2">
          {linked.map((def) => (
            <Link
              key={def.id}
              href={`/dashboard/workspace/${selectedWorkspaceId}/definitions/${def.id}`}
              className="flex items-center gap-3 rounded-md border px-3 py-2 text-sm transition-colors hover:bg-muted"
            >
              <BookOpen className="size-4 shrink-0 text-muted-foreground" />
              <span className="flex-1 truncate font-medium">{def.term}</span>
              <span className="bg-muted text-muted-foreground shrink-0 rounded-full px-2 py-0.5 text-xs">
                {CATEGORY_LABELS[def.category]}
              </span>
            </Link>
          ))}
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">
          No business definitions linked to this entity type.{' '}
          <Link
            href={`/dashboard/workspace/${selectedWorkspaceId}/definitions`}
            className="text-primary hover:underline"
          >
            Manage definitions
          </Link>
        </p>
      )}
    </div>
  );
};
