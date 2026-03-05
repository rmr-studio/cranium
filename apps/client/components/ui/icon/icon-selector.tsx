'use client';

import { useVirtualizer } from '@tanstack/react-virtual';
import { FC, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Button } from '@riven/ui/button';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandList } from '@riven/ui/command';
import { Popover, PopoverContent, PopoverTrigger } from '@riven/ui/popover';

import type { ClassNameProps } from '@riven/utils';
import { IconColour, IconType } from '@/lib/types/common';
import { cn } from '@riven/utils';
import { FileQuestionMark } from 'lucide-react';

import { type Icon } from '@/lib/types/common';
import { IconCell } from './icon-cell';
import { getAllIconTypes, ICON_COLOUR_MAP, ICON_REGISTRY, iconTypeToLabel } from './icon-mapper';

interface Props extends ClassNameProps {
  onSelect: (icon: Icon) => void;
  icon: Icon;
  displayIconClassName?: string;
}

const ICON_BUTTON_COLOUR_MAP: Record<IconColour, string> = {
  NEUTRAL: 'bg-primary',
  PURPLE: 'bg-purple-500',
  BLUE: 'bg-blue-500',
  TEAL: 'bg-teal-500',
  GREEN: 'bg-green-500',
  YELLOW: 'bg-yellow-500',
  ORANGE: 'bg-orange-500',
  RED: 'bg-red-500',
  PINK: 'bg-pink-500',
  GREY: 'bg-gray-500',
};

export const IconSelector: FC<Props> = ({
  onSelect,

  icon: { type, colour },
  className,
  displayIconClassName,
}) => {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');

  const [currentColour, setCurrentColour] = useState(colour);

  const allIcons = useMemo(getAllIconTypes, []);

  const filteredIcons = useMemo(() => {
    if (!search) return allIcons;
    const lower = search.toLowerCase();
    return allIcons.filter((icon) => iconTypeToLabel(icon).toLowerCase().includes(lower));
  }, [allIcons, search]);

  const handleSelect = useCallback(
    (iconType: IconType) => {
      onSelect({
        type: iconType,
        colour: currentColour,
      });
      setOpen(false);
      setSearch('');
    },
    [onSelect, currentColour],
  );

  const parentRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const columns = 8;
  const rowVirtualizer = useVirtualizer({
    count: Math.ceil(filteredIcons.length / columns),
    getScrollElement: () => parentRef.current,
    estimateSize: () => 44,
    overscan: 5,
    enabled: true,
  });

  useEffect(() => {
    if (!open) return;

    // One layout frame AFTER mount
    requestAnimationFrame(() => {
      rowVirtualizer.measure();
      inputRef.current?.focus();
    });
  }, [open, filteredIcons.length, rowVirtualizer]);

  const SelectedIcon = ICON_REGISTRY[type || IconType.Database];

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          type="button"
          className={cn('flex w-full items-center justify-center', className)}
        >
          {SelectedIcon ? (
            <SelectedIcon
              className={cn(ICON_COLOUR_MAP[colour || IconColour.Neutral], displayIconClassName)}
            />
          ) : (
            <FileQuestionMark className={cn(displayIconClassName)} />
          )}
        </Button>
      </PopoverTrigger>

      <PopoverContent className="w-[420px] p-0" align="start">
        {open && (
          <div className="flex flex-col">
            {/* Color selector */}
            <div className="flex flex-wrap gap-2 border-b p-3">
              {Object.values(IconColour).map((c) => (
                <button
                  type="button"
                  key={c}
                  onClick={() => setCurrentColour(c)}
                  className={cn(
                    'h-6 w-6 rounded-full border-2',
                    ICON_BUTTON_COLOUR_MAP[c],
                    currentColour === c && 'ring-2 ring-offset-2',
                  )}
                />
              ))}
            </div>

            <Command shouldFilter={false}>
              <CommandInput
                ref={inputRef}
                placeholder="Filter icons..."
                value={search}
                onValueChange={setSearch}
              />

              <CommandList className="overflow-y-hidden">
                <CommandEmpty>No icons found.</CommandEmpty>
                <CommandGroup>
                  <div ref={parentRef} className="h-[300px] overflow-y-auto">
                    <div
                      style={{
                        height: rowVirtualizer.getTotalSize(),
                        position: 'relative',
                      }}
                    >
                      {rowVirtualizer.getVirtualItems().map((row) => {
                        const start = row.index * columns;
                        const items = filteredIcons.slice(start, start + columns);

                        return (
                          <div
                            key={row.key}
                            style={{
                              position: 'absolute',
                              top: 0,
                              transform: `translateY(${row.start}px)`,
                            }}
                            className="grid grid-cols-8 gap-1 px-2"
                          >
                            {items.map((iconType) => (
                              <IconCell
                                key={iconType}
                                type={iconType}
                                colour={currentColour}
                                selected={type === iconType}
                                onSelect={handleSelect}
                              />
                            ))}
                          </div>
                        );
                      })}
                    </div>
                  </div>
                </CommandGroup>
              </CommandList>
            </Command>
          </div>
        )}
      </PopoverContent>
    </Popover>
  );
};
