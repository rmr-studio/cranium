'use client';

import { useWorkspaceStore } from '@/components/feature-modules/workspace/provider/workspace-provider';
import { cn } from '@riven/utils';
import { CogIcon, Plug, UserIcon } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

const linkClass =
  'flex items-center gap-2 rounded-md px-3 py-2 text-sm text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground';

export function SettingsPanel() {
  const selectedWorkspaceId = useWorkspaceStore((s) => s.selectedWorkspaceId);
  const pathname = usePathname();

  return (
    <div className="flex flex-col gap-4">
      {selectedWorkspaceId && (
        <div className="flex flex-col gap-1">
          <span className="px-3 text-xs font-medium text-sidebar-foreground/50">Workspace</span>
          <Link
            href={`/dashboard/workspace/${selectedWorkspaceId}/settings`}
            className={cn(
              linkClass,
              pathname.startsWith(`/dashboard/workspace/${selectedWorkspaceId}/settings`) &&
                'bg-sidebar-accent text-sidebar-foreground',
            )}
          >
            <CogIcon className="size-4" />
            Workspace Settings
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
        </div>
      )}
      <div className="flex flex-col gap-1">
        <span className="px-3 text-xs font-medium text-sidebar-foreground/50">Account</span>
        <Link
          href="/dashboard/settings/account"
          className={cn(
            linkClass,
            pathname.startsWith('/dashboard/settings/account') &&
              'bg-sidebar-accent text-sidebar-foreground',
          )}
        >
          <UserIcon className="size-4" />
          Account
        </Link>
      </div>
    </div>
  );
}
