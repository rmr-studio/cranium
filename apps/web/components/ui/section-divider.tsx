import { cn } from '@/lib/utils';
import { ChildNodeProps } from '@cranium/utils';

interface SectionDividerProps extends ChildNodeProps {
  className?: string;
}

export function SectionDivider({ className, children }: SectionDividerProps) {
  return (
    <div className={cn('flex items-center justify-start gap-4', className)}>
      <div className={cn('h-px w-12', 'bg-primary/70')} />
      <span
        className={cn(
          'shrink-0 rounded-full border px-5 py-2 text-xs font-medium tracking-wider uppercase sm:text-sm',

          'border-primary/80 text-primary',
        )}
      >
        {children}
      </span>
      <div className={cn('h-px w-full max-w-1/8', 'bg-primary/70')} />
    </div>
  );
}
