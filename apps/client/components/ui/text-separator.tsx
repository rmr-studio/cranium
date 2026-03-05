import { Separator } from '@riven/ui';
import { cn } from '@riven/utils';
import * as React from 'react';

interface TextSeparatorProps {
  children?: React.ReactNode;
  className?: string;
  position?: 'center' | 'left' | 'right';
  orientation?: 'horizontal' | 'vertical';
  decorative?: boolean;
}

const TextSeparator = React.forwardRef<React.ElementRef<typeof Separator>, TextSeparatorProps>(
  (
    {
      children,
      className,
      position = 'center',
      orientation = 'horizontal',
      decorative = true,
      ...props
    },
    ref,
  ) => {
    if (!children) {
      return (
        <Separator
          ref={ref}
          orientation={orientation}
          decorative={decorative}
          className={className}
          {...props}
        />
      );
    }

    if (orientation === 'vertical') {
      return (
        <div className={cn('flex flex-col items-center', className)}>
          <Separator orientation="vertical" decorative={decorative} className="flex-1" />
          <div className="px-2 py-1 text-sm text-muted-foreground">{children}</div>
          <Separator orientation="vertical" decorative={decorative} className="flex-1" />
        </div>
      );
    }

    // flex ratios based on position
    const flexRatios =
      position === 'left'
        ? { left: 'flex-[1]', right: 'flex-[12]' } // 1/12
        : position === 'right'
          ? { left: 'flex-[12]', right: 'flex-[1]' } // 4/5
          : { left: 'flex-1', right: 'flex-1' }; // equal for center

    return (
      <div className={cn('relative flex items-center', className)} ref={ref} {...props}>
        <div className={cn(flexRatios.left)}>
          <Separator decorative={decorative} />
        </div>
        <div className="px-4 text-sm text-muted-foreground">{children}</div>
        <div className={cn(flexRatios.right)}>
          <Separator decorative={decorative} />
        </div>
      </div>
    );
  },
);

TextSeparator.displayName = 'TextSeparator';

export { TextSeparator };
