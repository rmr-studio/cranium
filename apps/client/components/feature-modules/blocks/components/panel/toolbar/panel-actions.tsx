/**
 * PanelActions - Toolbar actions menu component
 *
 * IMPORTANT: This component uses Popover instead of DropdownMenu.
 * Toolbar components should always use Popover-based menus, NOT Dropdown menus.
 *
 * Reason: DropdownMenu causes DOM focus conflicts with keyboard navigation.
 * When a dropdown button is clicked, it retains DOM focus even after closing,
 * causing Enter key presses to re-trigger that button instead of the keyboard-
 * focused toolbar button. Popover doesn't have this issue and integrates cleanly
 * with the toolbar's keyboard navigation system.
 */

import { Button } from '@cranium/ui/button';
import { Command, CommandGroup, CommandItem, CommandList } from '@cranium/ui/command';
import { Popover, PopoverContent, PopoverTrigger } from '@cranium/ui/popover';
import { Tooltip, TooltipContent, TooltipTrigger } from '@cranium/ui/tooltip';
import { cn } from '@cranium/utils';
import { MoreHorizontalIcon } from "lucide-react";
import { FC, useEffect, useRef } from "react";
import type { QuickActionItem } from "@/lib/types/block";

interface PanelActionsProps {
  menuActions: QuickActionItem[];
  toolbarButtonClass: string;
  onMenuAction: (action: QuickActionItem) => void;
  actionsOpen?: boolean;
  onActionsOpenChange?: (open: boolean) => void;
}

const PanelActions: FC<PanelActionsProps> = ({
  menuActions,
  toolbarButtonClass,
  onMenuAction,
  actionsOpen,
  onActionsOpenChange,
}) => {
  const commandRef = useRef<HTMLDivElement>(null);

  // Auto-focus the Command component when menu opens for keyboard navigation
  useEffect(() => {
    if (actionsOpen) {
      requestAnimationFrame(() => {
        commandRef.current?.focus();
      });
    }
  }, [actionsOpen]);

  if (menuActions.length === 0) return null;

  return (
    <Popover open={actionsOpen} onOpenChange={onActionsOpenChange}>
      <Tooltip>
        <TooltipTrigger asChild>
          <PopoverTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              aria-label="More actions"
              className={toolbarButtonClass}
            >
              <MoreHorizontalIcon className="size-3.5" />
            </Button>
          </PopoverTrigger>
        </TooltipTrigger>
        <TooltipContent>More actions</TooltipContent>
      </Tooltip>
      <PopoverContent className="w-64 p-0" align="start">
        <Command ref={commandRef} tabIndex={0}>
          <CommandList>
            <CommandGroup heading="Actions">
              {menuActions.map((action) => (
                <CommandItem
                  key={action.id}
                  onSelect={() => {
                    onMenuAction(action);
                    onActionsOpenChange?.(false);
                  }}
                  className={cn(
                    'gap-2',
                    (action.id === 'delete' || action.id === '__delete') &&
                      'text-destructive focus:text-destructive',
                  )}
                >
                  <span>{action.label}</span>
                  {action.shortcut ? (
                    <span className="ml-auto text-xs tracking-wide text-muted-foreground uppercase">
                      {action.shortcut}
                    </span>
                  ) : null}
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
};

export default PanelActions;
