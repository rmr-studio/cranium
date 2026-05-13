import { CommandItem } from '@cranium/ui/command';
import type { ClassNameProps } from '@cranium/utils';
import { IconColour, IconType } from '@/lib/types/common';
import { cn } from '@cranium/utils';
import { memo } from 'react';
import { ICON_COLOUR_MAP, ICON_REGISTRY } from './icon-mapper';

interface Props extends ClassNameProps {
  type: IconType | undefined;
  colour: IconColour | undefined;
  selected?: boolean;
  onSelect?: (icon: IconType) => void;
  className?: string;
  readonly?: true;
}

export const IconCell = memo(
  ({
    type = IconType.Database,
    colour = IconColour.Neutral,
    selected,
    onSelect,
    className,
    readonly,
  }: Props) => {
    const Icon = ICON_REGISTRY[type];

    if (readonly) {
      return <Icon className={cn('size-4', ICON_COLOUR_MAP[colour], className)} />;
    }

    return (
      <CommandItem
        value={type}
        onSelect={() => onSelect && onSelect(type)}
        className={cn(
          'flex h-10 w-10 cursor-pointer items-center justify-center p-0',
          selected && 'bg-accent',
          className,
        )}
      >
        <Icon className={cn('size-4', ICON_COLOUR_MAP[colour])} />
      </CommandItem>
    );
  },
);

IconCell.displayName = 'IconCell';
