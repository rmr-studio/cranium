'use client';

import { useProfile } from '@/components/feature-modules/user/hooks/use-profile';
import { useWorkspaceStore } from '@/components/feature-modules/workspace/provider/workspace-provider';
import type { Workspace } from '@/lib/types/workspace';
import { cn } from '@riven/utils';
import { Check, PlusCircle } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Skeleton } from '../../skeleton';

export function WorkspacesPanel() {
  const router = useRouter();
  const { data, isPending, isLoadingAuth } = useProfile();
  const selectedWorkspaceId = useWorkspaceStore((s) => s.selectedWorkspaceId);
  const setSelectedWorkspace = useWorkspaceStore((s) => s.setSelectedWorkspace);

  const handleSelect = (workspace: Workspace) => {
    setSelectedWorkspace(workspace);
    router.push(`/dashboard/workspace/${workspace.id}`);
  };

  if (isPending || isLoadingAuth) {
    return (
      <div className="flex flex-col gap-2">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-9 w-full rounded-md" />
        ))}
      </div>
    );
  }

  const workspaces = data?.memberships.map((m) => m.workspace).filter(Boolean) as
  | Workspace[]
    | undefined;

  return (
    <div className="flex flex-col gap-1">
      {workspaces?.map((ws) => (
        <button
          key={ws.id}
          onClick={() => handleSelect(ws)}
          className={cn(
            'flex w-full items-center justify-between rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-sidebar-accent',
            ws.id === selectedWorkspaceId && 'bg-sidebar-accent',
          )}
        >
          <span className="truncate text-sidebar-foreground">{ws.name}</span>
          {ws.id === selectedWorkspaceId && (
            <Check className="size-4 shrink-0 text-sidebar-foreground/70" />
          )}
        </button>
      ))}

      <div className="my-2 h-px bg-sidebar-border" />

      <Link
        href="/dashboard/workspace/new"
        className="flex items-center gap-2 rounded-md px-3 py-2 text-sm text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground"
      >
        <PlusCircle className="size-4" />
        Create Workspace
      </Link>

      <Link
        href="/dashboard/workspace"
        className="flex items-center gap-2 rounded-md px-3 py-2 text-sm text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground"
      >
        View All Workspaces
      </Link>
    </div>
  );
}
