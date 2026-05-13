'use client';

import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/util/utils';
import { Command, CommandGroup, CommandInput, CommandItem, CommandList } from '@cranium/ui/command';
import { Popover, PopoverContent, PopoverTrigger } from '@cranium/ui/popover';
import { Plus, X } from 'lucide-react';
import * as React from 'react';

interface TagInputProps {
  value: string[];
  onChange: (tags: string[]) => void;
  placeholder?: string;
  className?: string;
  maxTags?: number;
}

const MAX_VISIBLE_TAGS = 3;

function TagInput({
  value = [],
  onChange,
  placeholder = 'Add tags...',
  className,
  maxTags,
}: TagInputProps) {
  const [open, setOpen] = React.useState(false);
  const [inputValue, setInputValue] = React.useState('');

  const trimmedInput = inputValue.trim();
  const canCreate =
    trimmedInput.length > 0 &&
    !value.some((t) => t.toLowerCase() === trimmedInput.toLowerCase()) &&
    (!maxTags || value.length < maxTags);

  const addTag = (tag: string) => {
    const trimmed = tag.trim();
    if (!trimmed) return;
    if (value.some((t) => t.toLowerCase() === trimmed.toLowerCase())) return;
    if (maxTags && value.length >= maxTags) return;
    onChange([...value, trimmed]);
    setInputValue('');
  };

  const removeTag = (tag: string) => {
    onChange(value.filter((t) => t !== tag));
  };

  const visibleTags = value.slice(0, MAX_VISIBLE_TAGS);
  const hiddenCount = value.length - MAX_VISIBLE_TAGS;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          type="button"
          onPointerDown={(e) => {
            e.stopPropagation();
          }}
          className={cn(
            'flex min-h-9 w-full flex-wrap items-center gap-1.5 rounded-md border border-input bg-transparent px-2 py-1.5 text-left shadow-xs transition-[color,box-shadow] dark:bg-input/30',
            'focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50',
            'hover:bg-accent/50',
            className,
          )}
        >
          {value.length === 0 && (
            <span className="text-sm text-muted-foreground">{placeholder}</span>
          )}
          {visibleTags.map((tag) => (
            <Badge key={tag} variant="secondary" className="gap-1 py-0.5 pr-1 pl-2 text-xs">
              {tag}
              <span
                role="button"
                tabIndex={-1}
                onClick={(e) => {
                  e.stopPropagation();
                  e.preventDefault();
                  removeTag(tag);
                }}
                onPointerDown={(e) => {
                  e.stopPropagation();
                  e.preventDefault();
                }}
                className="rounded-full p-0.5 transition-colors hover:bg-foreground/10"
              >
                <X className="size-2.5" />
              </span>
            </Badge>
          ))}
          {hiddenCount > 0 && (
            <Badge variant="outline" className="py-0.5 text-xs">
              +{hiddenCount} more
            </Badge>
          )}
        </button>
      </PopoverTrigger>
      <PopoverContent
        className="w-[var(--radix-popover-trigger-width)] p-0"
        align="start"
        onCloseAutoFocus={(e) => {
          e.preventDefault();
        }}
        onOpenAutoFocus={(e) => {
          e.preventDefault();
        }}
        onInteractOutside={(e) => {
          e.stopPropagation();
        }}
      >
        <Command shouldFilter={false}>
          <CommandInput
            placeholder="Search or create..."
            value={inputValue}
            onValueChange={setInputValue}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && canCreate) {
                e.preventDefault();
                addTag(inputValue);
              }
            }}
          />
          <CommandList>
            {canCreate && (
              <CommandGroup>
                <CommandItem
                  onSelect={() => {
                    addTag(inputValue);
                  }}
                >
                  <Plus />
                  Create &ldquo;{trimmedInput}&rdquo;
                </CommandItem>
              </CommandGroup>
            )}
            {value.length > 0 && (
              <CommandGroup heading="Tags">
                {value
                  .filter(
                    (tag) =>
                      !trimmedInput || tag.toLowerCase().includes(trimmedInput.toLowerCase()),
                  )
                  .map((tag) => (
                    <CommandItem
                      key={tag}
                      onSelect={() => {
                        removeTag(tag);
                      }}
                    >
                      <X className="text-muted-foreground" />
                      {tag}
                    </CommandItem>
                  ))}
              </CommandGroup>
            )}
            {value.length === 0 && !canCreate && (
              <div className="py-6 text-center text-sm text-muted-foreground">
                Type to create a tag
              </div>
            )}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}

export { TagInput };
export type { TagInputProps };
