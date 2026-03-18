'use client';

import { useProfile } from '@/components/feature-modules/user/hooks/use-profile';
import { WorkspaceIcon } from '@/components/feature-modules/workspace/components/workspace-icon';
import { useWorkspaceStore } from '@/components/feature-modules/workspace/provider/workspace-provider';
import { Logo } from '@riven/ui/logo';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@riven/ui/tooltip';
import { cn } from '@riven/utils';
import { Building2, CogIcon, SquareDashedMousePointer, TrendingUpDown } from 'lucide-react';
import { Kbd, KbdGroup } from '../kbd';
import { Skeleton } from '../skeleton';
import { type PanelId, useIconRail } from './icon-rail-context';

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

function SelectedWorkspaceIcon() {
  const { data, isPending, isLoadingAuth } = useProfile();
  const selectedWorkspaceId = useWorkspaceStore((s) => s.selectedWorkspaceId);

  const workspace = data?.memberships.find(
    (m) => m.workspace?.id === selectedWorkspaceId,
  )?.workspace;

  if (isPending || isLoadingAuth) {
    return <Skeleton className="size-8 rounded-md" />;
  }

  return (
    <WorkspaceIcon
      name={workspace?.name ?? 'Workspace'}
      avatarUrl={workspace?.avatarUrl}
    />
  );
}

export function IconRail() {
  const { selectedPanel, togglePanel, isMobile } = useIconRail();

  if (isMobile) return null;

  return (
    <TooltipProvider delayDuration={0}>
      <aside className="flex h-full w-(--icon-rail-width) shrink-0 flex-col items-center bg-foreground dark:bg-secondary">
        {/* Top section — matches header height */}
        <div className="flex h-(--header-height) w-full shrink-0 flex-col items-center justify-center gap-1 border-b border-background/15 [--logo-primary:var(--background)] dark:[--logo-primary:var(--foreground)]">
          <Logo size={24} />
        </div>

        {/* Workspace switcher */}
        <div className="pt-2">
          <Tooltip>
            <TooltipTrigger asChild>
              <button
                onClick={() => togglePanel('workspaces')}
                aria-label="Workspaces"
                className={cn(
                  'mb-1 flex items-center justify-center rounded-md p-1 transition-colors hover:bg-background/10',
                  selectedPanel === 'workspaces' && 'bg-background/15',
                )}
              >
                <SelectedWorkspaceIcon />
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
                  aria-label={item.label}
                  aria-pressed={selectedPanel === item.id}
                  className={cn(
                    'flex size-10 items-center justify-center rounded-md text-background/60 transition-colors hover:bg-background/10 hover:text-background dark:text-foreground/50',
                    selectedPanel === item.id &&
                      'bg-background/15 text-background dark:bg-foreground/20 dark:text-foreground',
                  )}
                >
                  {item.icon}
                </button>
              </TooltipTrigger>
              <TooltipContent side="right">{item.label}</TooltipContent>
            </Tooltip>
          ))}
        </nav>

        {/* Keyboard shortcut hint */}
        <div className="mt-auto mb-3 flex flex-col items-center">
          <KbdGroup className="scale-75 text-background/40">
            <Kbd className="h-4 min-w-4 bg-background/10 text-[10px] text-background/40">
              {typeof navigator !== 'undefined' && /Mac|iPod|iPhone|iPad/.test(navigator.platform)
                ? '⌘'
                : 'Ctrl'}
            </Kbd>
            <Kbd className="h-4 min-w-4 bg-background/10 text-[10px] text-background/40">B</Kbd>
          </KbdGroup>
        </div>
      </aside>
    </TooltipProvider>
  );
}
