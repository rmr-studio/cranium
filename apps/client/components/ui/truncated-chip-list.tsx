'use client';

import { IconColour, IconType } from '@/lib/types/common';
import { Badge } from '@riven/ui/badge';
import { cn } from '@riven/utils';
import { FC, ReactNode } from 'react';
import { IconCell } from '@/components/ui/icon/icon-cell';

export interface ChipItem {
  id: string;
  label: string;
  icon?: { type: IconType; colour: IconColour };
  subtitle?: string;
}

interface TruncatedChipListProps {
  items: ChipItem[];
  maxVisible?: number;
  onChipClick?: (item: ChipItem) => void;
  onOverflowClick?: () => void;
  emptyState?: ReactNode;
  className?: string;
}

export const TruncatedChipList: FC<TruncatedChipListProps> = ({
  items,
  maxVisible = 3,
  onChipClick,
  onOverflowClick,
  emptyState,
  className,
}) => {
  if (items.length === 0) {
    return emptyState ? <>{emptyState}</> : null;
  }

  const visibleItems = items.slice(0, maxVisible);
  const overflowCount = items.length - maxVisible;

  return (
    <div className={cn('flex flex-wrap items-center gap-1', className)}>
      {visibleItems.map((item) => (
        <Badge
          key={item.id}
          variant="secondary"
          className="cursor-pointer gap-1 text-xs font-normal"
          onClick={(e) => {
            e.stopPropagation();
            onChipClick?.(item);
          }}
        >
          {item.icon && (
            <IconCell
              type={item.icon.type}
              colour={item.icon.colour}
              readonly
              className="size-3"
            />
          )}
          <span className="max-w-24 truncate">{item.label}</span>
          {item.subtitle && (
            <span className="text-muted-foreground">{item.subtitle}</span>
          )}
        </Badge>
      ))}
      {overflowCount > 0 && (
        <Badge
          variant="outline"
          className="cursor-pointer text-xs font-normal"
          onClick={(e) => {
            e.stopPropagation();
            onOverflowClick?.();
          }}
        >
          +{overflowCount} more
        </Badge>
      )}
    </div>
  );
};
