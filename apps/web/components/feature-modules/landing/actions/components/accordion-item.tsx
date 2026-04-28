import { cn } from '@/lib/utils';
import { AnimatePresence, motion } from 'motion/react';

interface AccordionItemProps {
  title: React.ReactNode;
  description: string;
  isActive: boolean;
  onClick: () => void;
  compact?: boolean;
}

export function AccordionItem({
  title,
  description,
  isActive,
  onClick,
  compact,
}: AccordionItemProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        ':border-foreground/10 border-b border-white/10 text-left last:border-b-0',
        compact
          ? ':border-foreground/10 border-primary-foreground/10 py-4'
          : 'flex cursor-pointer items-start gap-4 py-5',
      )}
    >
      {!compact && (
        <div className="flex h-7 w-4 shrink-0 items-center justify-center">
          <div
            className={cn(
              'rounded-full transition-all duration-300',
              isActive ? 'h-2 w-2 bg-primary/90' : 'h-2 w-2 bg-content/50',
            )}
          />
        </div>
      )}

      <div className="min-w-0 flex-1">
        <div
          className={cn(
            'font-semibold transition-colors duration-300',
            compact ? 'text-base' : 'text-lg',
            isActive ? 'text-primary' : 'text-content/80',
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
              className="overflow-hidden text-sm leading-relaxed text-content/90"
            >
              <span className="block pt-2">{description}</span>
            </motion.p>
          )}
        </AnimatePresence>
      </div>
    </button>
  );
}
