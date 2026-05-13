import type { ChildNodeProps } from '@cranium/utils';
import { cn } from '@cranium/utils';

import { FC } from "react";
import type { ResizePosition } from "@/lib/types/block";

interface Props extends ChildNodeProps {
  position: ResizePosition;
  visible: boolean;
}

export const ResizeHandle: FC<Props> = ({ position, visible, children }) => {
  const style: Record<ResizePosition, string> = {
    nw: '-top-3 -left-3 ui-resizeable-handle ui-resizeable-nw cursor-nwse-resize',
    ne: '-top-3 -right-3  ui-resizeable-handle ui-resizeable-ne cursor-nesw-resize',
    sw: '-bottom-3 -left-3 ui-resizeable-handle ui-resizeable-sw cursor-nesw-resize',
    se: '-bottom-3 -right-3 ui-resizeable-handle ui-resizeable-se cursor-nwse-resize',
  };

  return (
    <div
      className={cn(
        'absolute z-[100] flex items-center justify-center transition-all duration-200',
        'pointer-events-auto',
        visible ? 'opacity-100' : 'opacity-0',
        style[position],
      )}
      data-resize-handle={position}
    >
      <div
        className={cn(
          'flex items-center justify-center rounded-sm',
          'bg-primary text-primary-foreground',
          'border-2 border-background shadow-sm',
          'transition-transform duration-200',
          'h-6 w-6',
          'pointer-events-none', // Visual only - let clicks pass through
        )}
      >
        {children}
      </div>
    </div>
  );
};
