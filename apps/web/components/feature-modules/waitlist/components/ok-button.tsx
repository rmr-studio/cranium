import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { Loader2 } from 'lucide-react';

export function OkButton({
  onClick,
  disabled,
  loading,
  label = 'Next',
  className,
}: {
  onClick: () => void;
  disabled?: boolean;
  loading?: boolean;
  label?: string;
  className?: string;
}) {
  return (
    <Button
      variant={'outline'}
      type="button"
      onClick={onClick}
      disabled={disabled || loading}
      className={cn(
        'inline-flex cursor-pointer items-center gap-1.5 rounded-md bg-teal-700 px-5 py-2 text-sm font-medium text-white transition-colors hover:bg-teal-600 disabled:cursor-not-allowed disabled:opacity-40',
        'border-primary/70 bg-transparent px-1.5 py-0.5 font-display text-xs text-content hover:bg-accent hover:text-accent-foreground',
        className,
      )}
    >
      {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : label}
    </Button>
  );
}
