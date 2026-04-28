'use client';

import { useProfile } from '@/components/feature-modules/user/hooks/use-profile';
import { useWorkspaceStore } from '@/components/feature-modules/workspace/provider/workspace-provider';
import { Kbd, KbdGroup } from '@/components/ui/kbd';
import { SelectedWorkspaceIcon } from '@/components/ui/sidebar/selected-workspace-icon';
import {
  useSelectedPanel,
  useSidePanelActions,
} from '@/components/ui/sidebar/context/side-panel-provider';
import type { PanelId } from '@/components/ui/sidebar/types/side-panel.types';
import { useIsMobile } from '@riven/hooks';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@riven/ui/tooltip';
import { cn } from '@riven/utils';
import { BookOpen, SquareDashedMousePointer, StickyNote, TrendingUpDown } from 'lucide-react';

interface RailButton {
  id: PanelId;
  icon: React.ReactNode;
  label: string;
}

const navItems: RailButton[] = [
  { id: 'entities', icon: <SquareDashedMousePointer className="size-5" />, label: 'Entities' },
  { id: 'knowledge', icon: <BookOpen className="size-5" />, label: 'Knowledge' },
  { id: 'notes', icon: <StickyNote className="size-5" />, label: 'Notes' },
  { id: 'billing', icon: <TrendingUpDown className="size-5" />, label: 'Billing' },
];

export function IconRail() {
  const selectedPanel = useSelectedPanel();
  const { togglePanel } = useSidePanelActions();
  const selectedWorkspaceId = useWorkspaceStore((s) => s.selectedWorkspaceId);
  const { data: profile } = useProfile();

  const isMobile = useIsMobile();

  if (isMobile || !selectedWorkspaceId || !profile) return null;

  const workspace = profile.memberships.find(
    (m) => m.workspace?.id === selectedWorkspaceId,
  )?.workspace;

  if (!workspace) return null;

  return (
    <TooltipProvider delayDuration={0}>
      <aside className="flex h-full w-(--icon-rail-width) shrink-0 flex-col items-center bg-foreground dark:bg-secondary">
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
                <SelectedWorkspaceIcon
                  avatarUrl={workspace.avatarUrl}
                  name={workspace.name}
                />
              </button>
            </TooltipTrigger>
            <TooltipContent side="right">{workspace.name}</TooltipContent>
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
