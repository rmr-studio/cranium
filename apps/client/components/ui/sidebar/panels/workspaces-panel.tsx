'use client';

import { useProfile } from '@/components/feature-modules/user/hooks/use-profile';
import { WorkspaceIcon } from '@/components/feature-modules/workspace/components/workspace-icon';
import { useWorkspaceStore } from '@/components/feature-modules/workspace/provider/workspace-provider';
import { useSidePanelActions } from '@/components/ui/sidebar/context/side-panel-provider';
import { Skeleton } from '@/components/ui/skeleton';
import type { Workspace } from '@/lib/types/workspace';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@riven/ui/dropdown-menu';
import { Button } from '@riven/ui/button';
import { cn } from '@riven/utils';
import {
  Check,
  ChevronsUpDown,
  CogIcon,
  PanelLeftClose,
  Plug,
  PlusCircle,
  Users,
} from 'lucide-react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';

const linkClass =
  'flex items-center gap-2 rounded-md px-3 py-2 text-sm text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground';

export function WorkspacesPanel() {
  const router = useRouter();
  const pathname = usePathname();
  const { data, isPending, isError, isLoadingAuth } = useProfile();
  const { closePanel } = useSidePanelActions();
  const selectedWorkspaceId = useWorkspaceStore((s) => s.selectedWorkspaceId);
  const setSelectedWorkspace = useWorkspaceStore((s) => s.setSelectedWorkspace);

  const loading = isPending || isLoadingAuth;
  const workspaces = data?.memberships
    .map((m) => m.workspace)
    .filter((ws): ws is Workspace => Boolean(ws));
  const currentWorkspace = workspaces?.find((ws) => ws.id === selectedWorkspaceId);

  const handleSelect = (workspace: Workspace) => {
    setSelectedWorkspace(workspace);
    router.push(`/dashboard/workspace/${workspace.id}`);
  };

  return (
    <>
      {/* Header — workspace picker + close */}
      <div className="flex min-h-(--header-height) shrink-0 items-center justify-between gap-2 border-b px-3">
        {loading ? (
          <Skeleton className="h-9 flex-1 rounded-md" />
        ) : isError || !currentWorkspace ? (
          <span className="flex-1 truncate text-sm text-sidebar-foreground/60">
            {isError ? 'Failed to load workspaces' : 'No workspace selected'}
          </span>
        ) : (
          <DropdownMenu>
            <DropdownMenuTrigger
              className="flex flex-1 items-center gap-2 rounded-md px-2 py-1.5 text-left transition-colors hover:bg-sidebar-accent focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
              aria-label="Switch workspace"
            >
              <WorkspaceIcon
                name={currentWorkspace.name}
                avatarUrl={currentWorkspace.avatarUrl}
                className="size-6 shrink-0"
              />
              <span className="flex-1 truncate text-sm font-medium text-sidebar-foreground">
                {currentWorkspace.name}
              </span>
              <ChevronsUpDown className="size-4 shrink-0 text-sidebar-foreground/50" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" className="w-64">
              {workspaces?.map((ws) => (
                <DropdownMenuItem
                  key={ws.id}
                  onSelect={() => handleSelect(ws)}
                  className="flex items-center justify-between"
                >
                  <span className="flex items-center gap-2">
                    <WorkspaceIcon
                      name={ws.name}
                      avatarUrl={ws.avatarUrl}
                      className="size-5 shrink-0"
                    />
                    <span className="truncate">{ws.name}</span>
                  </span>
                  {ws.id === selectedWorkspaceId && (
                    <Check className="size-4 shrink-0 text-sidebar-foreground/70" />
                  )}
                </DropdownMenuItem>
              ))}
              <DropdownMenuSeparator />
              <DropdownMenuItem asChild>
                <Link href="/dashboard/workspace/new" className="flex items-center gap-2">
                  <PlusCircle className="size-4" />
                  Create Workspace
                </Link>
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        )}
        <Button
          variant="ghost"
          size="icon"
          onClick={closePanel}
          aria-label="Close panel"
          className="size-7 shrink-0 text-sidebar-foreground/70 hover:text-sidebar-foreground"
        >
          <PanelLeftClose className="size-4" />
        </Button>
      </div>

      {/* Content — workspace settings */}
      <div className="flex flex-1 flex-col gap-1 overflow-y-auto px-3 py-2">
        {selectedWorkspaceId && (
          <>
            <Link
              href={`/dashboard/workspace/${selectedWorkspaceId}/members`}
              className={cn(
                linkClass,
                pathname.startsWith(`/dashboard/workspace/${selectedWorkspaceId}/members`) &&
                  'bg-sidebar-accent text-sidebar-foreground',
              )}
            >
              <Users className="size-4" />
              People
            </Link>
            <Link
              href={`/dashboard/workspace/${selectedWorkspaceId}/integrations`}
              className={cn(
                linkClass,
                pathname.startsWith(`/dashboard/workspace/${selectedWorkspaceId}/integrations`) &&
                  'bg-sidebar-accent text-sidebar-foreground',
              )}
            >
              <Plug className="size-4" />
              Connections
            </Link>
            <Link
              href={`/dashboard/workspace/${selectedWorkspaceId}/settings`}
              className={cn(
                linkClass,
                pathname.startsWith(`/dashboard/workspace/${selectedWorkspaceId}/settings`) &&
                  'bg-sidebar-accent text-sidebar-foreground',
              )}
            >
              <CogIcon className="size-4" />
              Settings
            </Link>
          </>
        )}
      </div>
    </>
  );
}
