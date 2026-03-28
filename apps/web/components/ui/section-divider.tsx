import { cn } from '@/lib/utils';
import { ChildNodeProps } from '@riven/utils';

interface SectionDividerProps extends ChildNodeProps {
  inverse?: boolean;
  className?: string;
}

export function SectionDivider({ inverse, className, children }: SectionDividerProps) {
  return (
    <div className={cn('flex items-center justify-start gap-4', className)}>
      <div className={cn('h-px w-12', inverse ? 'bg-primary-foreground/70' : 'bg-primary/70')} />
      <span
        className={cn(
          'shrink-0 rounded-full border px-5 py-2 text-xs font-medium tracking-wider uppercase sm:text-sm',
          inverse
            ? 'border-primary-foreground/80 text-primary-foreground'
            : 'border-primary/80 text-primary',
        )}
      >
        {children}
      </span>
      <div
        className={cn(
          'h-px w-full max-w-1/8',
          inverse ? 'bg-primary-foreground/70' : 'bg-primary/70',
        )}
      />
    </div>
  );
}
