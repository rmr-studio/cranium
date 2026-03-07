'use client';

import { useWorkspaceStore } from '@/components/feature-modules/workspace/provider/workspace-provider';
import { cn } from '@riven/utils';
import { Building2 } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

export function OverviewPanel() {
  const selectedWorkspaceId = useWorkspaceStore((s) => s.selectedWorkspaceId);
  const pathname = usePathname();

  if (!selectedWorkspaceId) {
    return null;
  }

  const workspaceUrl = `/dashboard/workspace/${selectedWorkspaceId}`;
  const isActive = pathname === workspaceUrl;

  return (
    <div className="flex flex-col gap-1">
      <Link
        href={workspaceUrl}
        className={cn(
          'flex items-center gap-2 rounded-md px-3 py-2 text-sm text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground',
          isActive && 'bg-sidebar-accent text-sidebar-foreground',
        )}
      >
        <Building2 className="size-4" />
        Workspace
      </Link>
    </div>
  );
}
