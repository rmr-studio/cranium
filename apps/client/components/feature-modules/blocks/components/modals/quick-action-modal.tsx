import { CommandDialog, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@riven/ui/command';
import { CommandIcon } from "lucide-react";
import { FC } from "react";
import type { QuickActionItem } from "@/lib/types/block";

interface Props {
  open: boolean;
  setOpen: (open: boolean) => void;
  onInsert?: () => void;
  onActionSelect: (action: QuickActionItem) => void;
  actions: QuickActionItem[];
  allowInsert?: boolean;
}

const QuickActionModal: FC<Props> = ({
  open,
  setOpen,
  onInsert,
  onActionSelect,
  actions,
  allowInsert = true,
}) => {
  return (
    <CommandDialog open={open} onOpenChange={setOpen} title="Quick actions">
      <CommandInput placeholder="Quick actions…" />
      <CommandList>
        <CommandEmpty>No actions available.</CommandEmpty>
        {allowInsert && onInsert ? (
          <CommandGroup heading="Insert">
            <CommandItem onSelect={onInsert}>
              <CommandIcon className="size-4 opacity-60" />
              <div className="flex flex-col items-start">
                <span>Insert Block…</span>
                <span className="text-xs text-muted-foreground">Add a new block to this panel</span>
              </div>
            </CommandItem>
          </CommandGroup>
        ) : null}
        <CommandGroup heading="Actions">
          {actions.map((action) => (
            <CommandItem key={action.id} onSelect={() => onActionSelect(action)} className="gap-2">
              <CommandIcon className="size-4 opacity-60" />
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
    </CommandDialog>
  );
};

export default QuickActionModal;
