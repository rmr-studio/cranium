'use client';

import { Skeleton } from '@/components/ui/skeleton';
import { AnimatePresence, motion } from 'motion/react';
import React, { useMemo } from 'react';
import { useOnboardLiveData } from '@/components/feature-modules/onboarding/hooks/use-onboard-store';
import {
  CATEGORY_LABELS,
  CATEGORY_ORDER,
} from '@/components/feature-modules/onboarding/config/definition-defaults';
import type { DefinitionsLiveData } from '@/components/feature-modules/onboarding/components/forms/definitions-step-form';
import type { DefinitionCategory } from '@/lib/types/workspace';

const fadeProps = {
  initial: { opacity: 0 },
  animate: { opacity: 1 },
  exit: { opacity: 0 },
  transition: { duration: 0.3, ease: 'easeInOut' as const },
};

const SKELETON_CATEGORIES: DefinitionCategory[] = [
  CATEGORY_ORDER[0],
  CATEGORY_ORDER[1],
];

export const DefinitionsPreview: React.FC = () => {
  const liveData = useOnboardLiveData<DefinitionsLiveData>('definitions');

  const grouped = useMemo(() => {
    const filled = (liveData?.definitions ?? []).filter(
      (d) => d.definition.trim().length > 0,
    );
    return CATEGORY_ORDER.map((cat) => ({
      category: cat,
      items: filled.filter((d) => d.category === cat),
    })).filter((g) => g.items.length > 0);
  }, [liveData]);

  const totalFilled = grouped.reduce((sum, g) => sum + g.items.length, 0);

  return (
    <div className="flex w-full max-w-lg flex-col gap-3">
      <div className="flex items-baseline justify-between">
        <p className="text-muted-foreground text-xs font-semibold tracking-widest uppercase">
          Definitions
        </p>
        {totalFilled > 0 && (
          <span className="text-muted-foreground/80 font-mono text-[10px]">
            {totalFilled} term{totalFilled === 1 ? '' : 's'}
          </span>
        )}
      </div>

      <div className="scrollbar-thin max-h-[calc(100vh-12rem)] overflow-y-auto pr-1">
        <AnimatePresence mode="wait">
          {grouped.length > 0 ? (
            <motion.div key="filled" {...fadeProps} className="flex flex-col gap-4">
              {grouped.map(({ category, items }) => (
                <div key={category} className="flex flex-col gap-2">
                  <div className="flex items-center gap-2">
                    <p className="text-muted-foreground/80 font-mono text-[10px] font-bold tracking-widest uppercase">
                      {CATEGORY_LABELS[category]}
                    </p>
                    <div className="bg-border/60 h-px flex-1" />
                    <span className="text-muted-foreground/60 font-mono text-[10px]">
                      {items.length}
                    </span>
                  </div>
                  <div className="flex flex-col gap-2">
                    {items.map((def, i) => (
                      <div
                        key={`${def.term}-${i}`}
                        className="bg-card hover:border-border flex flex-col gap-1 rounded-lg border border-transparent p-3 shadow-sm transition-colors"
                      >
                        <span className="text-sm font-semibold">{def.term}</span>
                        <p className="text-muted-foreground line-clamp-2 text-xs leading-relaxed">
                          {def.definition}
                        </p>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </motion.div>
          ) : (
            <motion.div key="skeleton" {...fadeProps} className="flex flex-col gap-4">
              {SKELETON_CATEGORIES.map((cat) => (
                <div key={cat} className="flex flex-col gap-2">
                  <div className="flex items-center gap-2">
                    <Skeleton className="h-2.5 w-16" />
                    <div className="bg-border/40 h-px flex-1" />
                  </div>
                  <div className="flex flex-col gap-2">
                    {Array.from({ length: 2 }).map((_, i) => (
                      <div
                        key={i}
                        className="bg-card flex flex-col gap-2 rounded-lg p-3 shadow-sm"
                      >
                        <Skeleton className="h-3.5 w-24" />
                        <Skeleton className="h-2.5 w-full" />
                        <Skeleton className="h-2.5 w-3/4" />
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
};
