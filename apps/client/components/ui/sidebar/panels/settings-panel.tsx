'use client';

import { useWorkspaceStore } from '@/components/feature-modules/workspace/provider/workspace-provider';
import { cn } from '@riven/utils';
import { CogIcon, UserIcon } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

const linkClass =
  'flex items-center gap-2 rounded-md px-3 py-2 text-sm text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground';

export function SettingsPanel() {
  const selectedWorkspaceId = useWorkspaceStore((s) => s.selectedWorkspaceId);
  const pathname = usePathname();

  const workspaceSettingsUrl = selectedWorkspaceId
    ? `/dashboard/workspace/${selectedWorkspaceId}/settings`
    : null;
  const accountUrl = '/dashboard/settings/account';

  return (
    <div className="flex flex-col gap-4">
      {workspaceSettingsUrl && (
        <div className="flex flex-col gap-1">
          <span className="px-3 text-xs font-medium text-sidebar-foreground/50">Workspace</span>
          <Link
            href={workspaceSettingsUrl}
            className={cn(
              linkClass,
              pathname.startsWith(workspaceSettingsUrl) && 'bg-sidebar-accent text-sidebar-foreground',
            )}
          >
            <CogIcon className="size-4" />
            Workspace Settings
          </Link>
        </div>
      )}
      <div className="flex flex-col gap-1">
        <span className="px-3 text-xs font-medium text-sidebar-foreground/50">Account</span>
        <Link
          href={accountUrl}
          className={cn(
            linkClass,
            pathname.startsWith(accountUrl) && 'bg-sidebar-accent text-sidebar-foreground',
          )}
        >
          <UserIcon className="size-4" />
          Account
        </Link>
      </div>
    </div>
  );
}
