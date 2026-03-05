'use client';

import { Checkbox } from '@/components/ui/checkbox';
import { Button } from '@riven/ui/button';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@riven/ui/command';
import { Popover, PopoverContent, PopoverTrigger } from '@riven/ui/popover';
import { EntityType } from '@/lib/types/entity';
import { cn } from '@riven/utils';
import { Check, ChevronsUpDown, Repeat } from 'lucide-react';
import { FC, useState } from 'react';

interface Props {
  availableTypes?: EntityType[];
  allowSelectAll?: boolean;
  selectedKeys: string[];
  allowPolymorphic: boolean;
  onSelectionChange: (keys: string[], allowPolymorphic: boolean) => void;
  disabled?: boolean;
  hasError?: boolean;
  currentEntityKey?: string;
}

export const EntityTypeMultiSelect: FC<Props> = ({
  availableTypes = [],
  allowSelectAll = true,
  selectedKeys,
  allowPolymorphic,
  onSelectionChange,
  disabled = false,
  hasError = false,
  currentEntityKey,
}) => {
  const [open, setOpen] = useState(false);

  const handleAllEntitiesToggle = () => {
    if (allowPolymorphic) {
      // If "Allow All Entities" is currently selected, deselect it
      onSelectionChange([], false);
    } else {
      // Select "Allow All Entities" and clear all other selections
      onSelectionChange([], true);
    }
  };

  const handleEntityTypeToggle = (key: string) => {
    if (allowPolymorphic) {
      // If "Allow All Entities" is selected, deselect it and select this entity
      onSelectionChange([key], false);
    } else {
      // Toggle the entity type selection
      const newSelection = selectedKeys.includes(key)
        ? selectedKeys.filter((k) => k !== key)
        : [...selectedKeys, key];
      onSelectionChange(newSelection, false);
    }
  };

  const getDisplayText = () => {
    if (allowPolymorphic) {
      return 'Allow All Entities';
    }
    if (selectedKeys.length === 0) {
      return 'Select entity types...';
    }
    if (selectedKeys.length === 1) {
      const selected = availableTypes.find((et) => et.key === selectedKeys[0]);
      if (!selected) return '1 selected';

      return selected.name.plural;
    }

    // Display x,y and [...] entities selected if multiple
    const names = selectedKeys
      .map((key) => availableTypes.find((et) => et.key === key))
      .filter((et): et is EntityType => et !== undefined)
      .map((et) => et.name.plural);

    if (names.length <= 2) {
      return names.join(', ');
    }

    return `${names.slice(0, 2).join(', ')} and ${names.length - 2} more`;
  };

  return (
    <Popover open={open} onOpenChange={setOpen} modal={true}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className={cn(
            'w-full justify-between',
            hasError && 'border-destructive focus-visible:ring-destructive',
          )}
          disabled={disabled || open}
        >
          <span className="truncate">{getDisplayText()}</span>
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        className="w-full p-0"
        align="start"
        portal={true}
        onOpenAutoFocus={(e) => e.preventDefault()}
        onEscapeKeyDown={(e) => {
          e.stopPropagation();
          setOpen(false);
        }}
      >
        <Command>
          <CommandInput placeholder="Search entity types..." />
          <CommandList>
            <CommandEmpty>No entity type found.</CommandEmpty>
            <CommandGroup>
              {/* Allow All Entities Option */}
              {allowSelectAll && (
                <CommandItem onSelect={handleAllEntitiesToggle} className="cursor-pointer">
                  <Checkbox checked={allowPolymorphic} className="pointer-events-none mr-2" />
                  <div className="flex items-center gap-2 font-medium">
                    <span>Allow All Entities</span>
                  </div>
                </CommandItem>
              )}

              {/* Separator */}
              <div className="my-1 h-px bg-border" />

              {/* Entity Types List */}
              {availableTypes.map((entityType) => {
                const isSelected = selectedKeys.includes(entityType.key);
                const isSelfReference = entityType.key === currentEntityKey;
                const displayName =
                  isSelfReference && !entityType.name.plural
                    ? 'This entity'
                    : entityType.name.plural;

                return (
                  <CommandItem
                    key={entityType.key}
                    value={displayName}
                    onSelect={() => handleEntityTypeToggle(entityType.key)}
                    className="cursor-pointer"
                  >
                    <Checkbox checked={isSelected} className="pointer-events-none mr-2" />
                    <div className="flex items-center gap-2">
                      {isSelfReference ? (
                        <div className="flex h-5 w-5 items-center justify-center rounded bg-primary/10">
                          <Repeat className="h-3 w-3 text-primary" />
                        </div>
                      ) : (
                        <div className="flex h-5 w-5 items-center justify-center rounded bg-primary/10">
                          <span className="text-xs">{displayName.charAt(0)}</span>
                        </div>
                      )}
                      <span>{displayName}</span>
                    </div>
                    {isSelected && <Check className="ml-auto h-4 w-4 text-primary" />}
                  </CommandItem>
                );
              })}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
};
