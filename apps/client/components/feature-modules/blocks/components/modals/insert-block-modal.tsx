import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@riven/ui';
import { SearchIcon } from 'lucide-react';
import { FC } from 'react';
import { SlashMenuItem } from '../panel/panel-wrapper';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSelect: (item: SlashMenuItem) => void;
  items: SlashMenuItem[];
}

const InsertBlockModal: FC<Props> = ({ open, onOpenChange, onSelect, items }) => {
  return (
    <CommandDialog open={open} onOpenChange={onOpenChange}>
      <CommandInput placeholder="Search components or templates..." />
      <CommandList>
        <CommandEmpty>No matches found.</CommandEmpty>
        <CommandGroup heading="Insert block">
          {items.map((item) => (
            <CommandItem key={item.id} onSelect={() => onSelect(item)} className="gap-2">
              {item.icon ?? <SearchIcon className="size-4" />}
              <div className="flex flex-col items-start">
                <span>{item.label}</span>
                {item.description ? (
                  <span className="text-xs text-muted-foreground">{item.description}</span>
                ) : null}
              </div>
            </CommandItem>
          ))}
        </CommandGroup>
      </CommandList>
    </CommandDialog>
  );
};

export default InsertBlockModal;
