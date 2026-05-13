import type { ComponentProps } from 'react';
import { Handle, type HandleProps } from '@xyflow/react';

import { cn } from '@cranium/utils';

export type BaseHandleProps = HandleProps;

export function BaseHandle({ className, children, ...props }: ComponentProps<typeof Handle>) {
  return (
    <Handle
      {...props}
      className={cn(
        'h-[11px] w-[11px] rounded-full border border-slate-300 bg-slate-100 transition dark:border-secondary dark:bg-secondary',
        className,
      )}
    >
      {children}
    </Handle>
  );
}
