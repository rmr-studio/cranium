'use client';

import { useWorkspaceStore } from '@/components/feature-modules/workspace/provider/workspace-provider';
import { cn } from '@riven/utils';
import { CogIcon } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

export function SettingsPanel() {
  const selectedWorkspaceId = useWorkspaceStore((s) => s.selectedWorkspaceId);
  const pathname = usePathname();

  if (!selectedWorkspaceId) {
    return null;
  }

  const settingsUrl = `/dashboard/workspace/${selectedWorkspaceId}/settings`;
  const isActive = pathname.startsWith(settingsUrl);

  return (
    <div className="flex flex-col gap-1">
      <Link
        href={settingsUrl}
        className={cn(
          'flex items-center gap-2 rounded-md px-3 py-2 text-sm text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground',
          isActive && 'bg-sidebar-accent text-sidebar-foreground',
        )}
      >
        <CogIcon className="size-4" />
        Workspace Settings
      </Link>
    </div>
  );
}
