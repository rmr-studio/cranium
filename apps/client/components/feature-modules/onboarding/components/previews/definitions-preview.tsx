'use client';

import { Skeleton } from '@/components/ui/skeleton';
import { AnimatePresence, motion } from 'motion/react';
import React from 'react';
import { useOnboardLiveData } from '@/components/feature-modules/onboarding/hooks/use-onboard-store';
import { CATEGORY_LABELS } from '@/components/feature-modules/onboarding/config/definition-defaults';
import type { DefinitionsLiveData } from '@/components/feature-modules/onboarding/components/forms/definitions-step-form';

const fadeProps = {
  initial: { opacity: 0 },
  animate: { opacity: 1 },
  exit: { opacity: 0 },
  transition: { duration: 0.3, ease: 'easeInOut' as const },
};

const SKELETON_COUNT = 4;

export const DefinitionsPreview: React.FC = () => {
  const liveData = useOnboardLiveData<DefinitionsLiveData>('definitions');
  const definitions = liveData?.definitions ?? [];

  const filledDefinitions = definitions.filter((d) => d.definition.trim().length > 0);

  return (
    <div className="flex w-full max-w-lg flex-col gap-4">
      <p className="text-muted-foreground text-xs font-semibold uppercase tracking-widest">
        Definitions
      </p>
      <AnimatePresence mode="wait">
        {filledDefinitions.length > 0 ? (
          <motion.div
            key="filled"
            {...fadeProps}
            className="flex flex-col gap-3"
          >
            {filledDefinitions.map((def, i) => (
              <div
                key={`${def.term}-${i}`}
                className="bg-card flex flex-col gap-1.5 rounded-xl p-5 shadow-sm"
              >
                <div className="flex items-center gap-2">
                  <span className="text-sm font-semibold">{def.term}</span>
                  <span className="bg-muted text-muted-foreground rounded-full px-2 py-0.5 text-xs">
                    {CATEGORY_LABELS[def.category]}
                  </span>
                </div>
                <p className="text-muted-foreground line-clamp-2 text-xs">
                  {def.definition}
                </p>
              </div>
            ))}
          </motion.div>
        ) : (
          <motion.div key="skeleton" {...fadeProps} className="flex flex-col gap-3">
            {Array.from({ length: SKELETON_COUNT }).map((_, i) => (
              <div key={i} className="bg-card flex flex-col gap-3 rounded-xl p-5 shadow-sm">
                <div className="flex items-center gap-2">
                  <Skeleton className="h-4 w-28" />
                  <Skeleton className="h-5 w-16 rounded-full" />
                </div>
                <Skeleton className="h-3 w-full" />
                <Skeleton className="h-3 w-4/5" />
              </div>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};
