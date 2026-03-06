'use client';

import { useProfile } from '@/components/feature-modules/user/hooks/useProfile';
import { useWorkspaceStore } from '@/components/feature-modules/workspace/provider/workspace-provider';
import { cn } from '@riven/utils';
import { Logo } from '@riven/ui/logo';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@riven/ui/tooltip';
import {
  Building2,
  CogIcon,
  SquareDashedMousePointer,
  TrendingUpDown,
} from 'lucide-react';
import { type PanelId, useIconRail } from './icon-rail-context';
import { Avatar, AvatarFallback } from '../avatar';
import { Skeleton } from '../skeleton';

interface RailButton {
  id: PanelId;
  icon: React.ReactNode;
  label: string;
}

const navItems: RailButton[] = [
  { id: 'overview', icon: <Building2 className="size-5" />, label: 'Overview' },
  { id: 'entities', icon: <SquareDashedMousePointer className="size-5" />, label: 'Entities' },
  { id: 'billing', icon: <TrendingUpDown className="size-5" />, label: 'Billing' },
  { id: 'settings', icon: <CogIcon className="size-5" />, label: 'Settings' },
];

function WorkspaceIcon() {
  const { data, isPending, isLoadingAuth } = useProfile();
  const selectedWorkspaceId = useWorkspaceStore((s) => s.selectedWorkspaceId);

  const workspace = data?.memberships.find(
    (m) => m.workspace?.id === selectedWorkspaceId,
  )?.workspace;

  if (isPending || isLoadingAuth) {
    return <Skeleton className="size-8 rounded-md" />;
  }

  const letter = workspace?.name?.charAt(0)?.toUpperCase() ?? 'W';

  return (
    <div className="flex size-8 items-center justify-center rounded-md bg-primary text-sm font-bold text-primary-foreground">
      {letter}
    </div>
  );
}

function UserAvatar() {
  const { data, isPending, isLoadingAuth } = useProfile();

  if (isPending || isLoadingAuth) {
    return <Skeleton className="size-8 rounded-full" />;
  }

  const initials = data
    ? `${data.firstName?.charAt(0) ?? ''}${data.lastName?.charAt(0) ?? ''}`
    : '?';

  return (
    <Avatar className="size-8">
      <AvatarFallback className="bg-background/15 text-xs text-background">{initials}</AvatarFallback>
    </Avatar>
  );
}

export function IconRail() {
  const { activePanel, togglePanel, isMobile } = useIconRail();

  if (isMobile) return null;

  return (
    <TooltipProvider delayDuration={0}>
    <aside className="flex h-full w-[--icon-rail-width] shrink-0 flex-col items-center bg-foreground">
      {/* Top section — matches header height */}
      <div className="flex h-[--header-height] w-full shrink-0 flex-col items-center justify-center gap-1 border-b border-background/15 [--logo-primary:var(--background)]">
        <Logo size={24} />
      </div>

      {/* Workspace switcher */}
      <div className="pt-2">
        <Tooltip>
          <TooltipTrigger asChild>
            <button
              onClick={() => togglePanel('workspaces')}
              className={cn(
                'mb-1 flex items-center justify-center rounded-md p-1 transition-colors hover:bg-background/10',
                activePanel === 'workspaces' && 'bg-background/15',
              )}
            >
              <WorkspaceIcon />
            </button>
          </TooltipTrigger>
          <TooltipContent side="right">Workspaces</TooltipContent>
        </Tooltip>
      </div>

      {/* Separator */}
      <div className="mx-auto my-2 h-px w-8 bg-background/20" />

      {/* Nav items */}
      <nav className="flex flex-1 flex-col items-center gap-1">
        {navItems.map((item) => (
          <Tooltip key={item.id}>
            <TooltipTrigger asChild>
              <button
                onClick={() => togglePanel(item.id)}
                className={cn(
                  'flex size-10 items-center justify-center rounded-md text-background/60 transition-colors hover:bg-background/10 hover:text-background',
                  activePanel === item.id &&
                    'bg-background/15 text-background',
                )}
              >
                {item.icon}
              </button>
            </TooltipTrigger>
            <TooltipContent side="right">{item.label}</TooltipContent>
          </Tooltip>
        ))}
      </nav>

      {/* User avatar at bottom */}
      <div className="mt-auto pt-2">
        <UserAvatar />
      </div>
    </aside>
    </TooltipProvider>
  );
}
