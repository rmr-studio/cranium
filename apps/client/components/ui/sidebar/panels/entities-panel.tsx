'use client';

import { useEntityTypes } from '@/components/feature-modules/entity/hooks/query/type/use-entity-types';
import { useWorkspaceStore } from '@/components/feature-modules/workspace/provider/workspace-provider';
import { IconCell } from '@/components/ui/icon/icon-cell';
import { cn } from '@riven/utils';
import { PlusCircle, SquareDashedMousePointer } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Skeleton } from '../../skeleton';

export function EntitiesPanel() {
  const selectedWorkspaceId = useWorkspaceStore((s) => s.selectedWorkspaceId);
  const { data: entityTypes, isLoading } = useEntityTypes(selectedWorkspaceId);
  const pathname = usePathname();

  const basePath = `/dashboard/workspace/${selectedWorkspaceId}/entity`;

  if (isLoading || !selectedWorkspaceId) {
    return (
      <div className="flex flex-col gap-2">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="flex items-center gap-2 px-3 py-2">
            <Skeleton className="size-4 rounded-sm" />
            <Skeleton className="h-4 flex-1 rounded" />
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-1">
      {entityTypes?.map((entityType) => {
        const url = `${basePath}/${entityType.key}`;
        const isActive = pathname === url || pathname.startsWith(url + '/');

        return (
          <Link
            key={entityType.key}
            href={url}
            className={cn(
              'flex items-center gap-2 rounded-md px-3 py-2 text-sm text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground',
              isActive && 'bg-sidebar-accent text-sidebar-foreground',
            )}
          >
            <IconCell readonly type={entityType.icon.type} colour={entityType.icon.colour} />
            <span className="truncate">{entityType.name.plural}</span>
          </Link>
        );
      })}

      {entityTypes && entityTypes.length > 0 && <div className="my-2 h-px bg-sidebar-border" />}

      <Link
        href={basePath}
        className="flex items-center gap-2 rounded-md px-3 py-2 text-sm text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground"
      >
        <SquareDashedMousePointer className="size-4" />
        View All Entities
      </Link>

      <Link
        href={`${basePath}?new`}
        className="flex items-center gap-2 rounded-md px-3 py-2 text-sm text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground"
      >
        <PlusCircle className="size-4" />
        New Entity Type
      </Link>
    </div>
  );
}
