'use client';

import {
  DefinitionCategory,
  DefinitionStatus,
  WorkspaceBusinessDefinition,
} from '@/lib/types/workspace';
import { cn } from '@/lib/util/utils';
import Link from 'next/link';

const CATEGORY_LABELS: Record<DefinitionCategory, string> = {
  [DefinitionCategory.Metric]: 'Metric',
  [DefinitionCategory.Segment]: 'Segment',
  [DefinitionCategory.Status]: 'Status',
  [DefinitionCategory.LifecycleStage]: 'Lifecycle',
  [DefinitionCategory.Custom]: 'Custom',
};

const STATUS_DOT: Record<DefinitionStatus, string> = {
  [DefinitionStatus.Active]: 'bg-emerald-500',
  [DefinitionStatus.Suggested]: 'bg-amber-500',
};

export function DefinitionRow({ definition, workspaceId }: { definition: WorkspaceBusinessDefinition; workspaceId: string }) {
  return (
    <Link
      href={`/dashboard/workspace/${workspaceId}/definitions/${definition.id}`}
      className="hover:bg-muted flex items-center gap-4 border-b px-4 py-3 transition-colors"
    >
      <div
        className={cn('size-2 shrink-0 rounded-full', STATUS_DOT[definition.status])}
        title={definition.status === DefinitionStatus.Active ? 'Active' : 'Needs review'}
      />
      <div className="min-w-0 flex-1">
        <span className="text-sm font-medium">{definition.term}</span>
        <p className="text-muted-foreground line-clamp-1 text-xs">{definition.definition}</p>
      </div>
      <span className="bg-muted text-muted-foreground shrink-0 rounded-full px-2 py-0.5 text-xs">
        {CATEGORY_LABELS[definition.category]}
      </span>
    </Link>
  );
}
