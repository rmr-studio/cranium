import { cn } from '@/lib/utils';
import { AnimatePresence, motion } from 'motion/react';

interface AccordionItemProps {
  title: React.ReactNode;
  description: string;
  isActive: boolean;
  onClick: () => void;
  compact?: boolean;
}

export function AccordionItem({ title, description, isActive, onClick, compact }: AccordionItemProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'border-b border-white/10 text-left last:border-b-0 dark:border-foreground/10',
        compact
          ? 'border-primary-foreground/10 py-4 dark:border-foreground/10'
          : 'flex cursor-pointer items-start gap-4 py-5',
      )}
    >
      {!compact && (
        <div className="flex h-7 w-4 shrink-0 items-center justify-center">
          <div
            className={cn(
              'rounded-full transition-all duration-300',
              isActive
                ? 'h-3 w-3 bg-primary-foreground dark:bg-foreground'
                : 'h-2 w-2 bg-primary-foreground/20 dark:bg-foreground/20',
            )}
          />
        </div>
      )}

      <div className="min-w-0 flex-1">
        <div
          className={cn(
            'font-semibold transition-colors duration-300',
            compact ? 'text-base' : 'text-lg',
            isActive
              ? 'text-primary-foreground dark:text-foreground'
              : 'text-primary-foreground/40 dark:text-foreground/40',
          )}
        >
          {title}
        </div>

        <AnimatePresence initial={false}>
          {isActive && (
            <motion.p
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={{ duration: 0.3, ease: 'easeInOut' }}
              className="overflow-hidden text-sm leading-relaxed text-primary-foreground/50 dark:text-foreground/50"
            >
              <span className="block pt-2">{description}</span>
            </motion.p>
          )}
        </AnimatePresence>
      </div>
    </button>
  );
}
