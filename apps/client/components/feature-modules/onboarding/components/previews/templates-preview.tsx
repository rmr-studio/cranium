'use client';

import { Skeleton } from '@/components/ui/skeleton';
import { BundleDetail, ManifestSummary } from '@/lib/types';
import { AnimatePresence, motion } from 'motion/react';
import React from 'react';
import { useOnboardLiveData } from '@/components/feature-modules/onboarding/hooks/use-onboard-store';

interface TemplatesLiveData {
  selectedBundleKey: string | null;
  bundles?: BundleDetail[];
  templates?: ManifestSummary[];
}

const fadeProps = {
  initial: { opacity: 0 },
  animate: { opacity: 1 },
  exit: { opacity: 0 },
  transition: { duration: 0.3, ease: 'easeInOut' as const },
};

const GRID_SLOT_COUNT = 4;

const SkeletonCard: React.FC = () => (
  <div className="bg-card flex flex-col gap-4 rounded-xl p-5 shadow-sm">
    <Skeleton className="size-10 rounded-md" />
    <div className="flex flex-col gap-2.5">
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-4 w-4/5" />
    </div>
    <Skeleton className="h-3 w-3/5" />
  </div>
);

export const TemplatesPreview: React.FC = () => {
  const liveTemplates = useOnboardLiveData<TemplatesLiveData>('templates');

  const selectedBundleKey = liveTemplates?.selectedBundleKey ?? null;
  const bundles = liveTemplates?.bundles ?? [];
  const templates = liveTemplates?.templates ?? [];

  const selectedBundle = selectedBundleKey
    ? bundles.find((b) => b.key === selectedBundleKey) ?? null
    : null;

  const selectedTemplates: ManifestSummary[] = selectedBundle
    ? selectedBundle.templateKeys
        .map((key) => templates.find((t) => t.key === key))
        .filter((t): t is ManifestSummary => t !== undefined)
    : [];

  const fillerCount = Math.max(0, GRID_SLOT_COUNT - selectedTemplates.length);

  return (
    <div className="flex w-full max-w-lg flex-col gap-4">
      <p className="text-muted-foreground text-xs font-semibold uppercase tracking-widest">
        Templates
      </p>
      <AnimatePresence mode="wait">
        {selectedBundle ? (
          <motion.div
            key={selectedBundleKey ?? 'skeleton'}
            {...fadeProps}
            className="grid grid-cols-2 gap-4"
          >
            {selectedTemplates.map((tmpl) => (
              <div
                key={tmpl.key}
                className="bg-card flex flex-col gap-3 rounded-xl p-5 shadow-sm"
              >
                <Skeleton className="size-10 rounded-md" />
                <div className="flex flex-col gap-1.5">
                  <span className="text-sm font-semibold">{tmpl.name}</span>
                  <span className="text-muted-foreground text-xs">
                    {tmpl.entityTypeCount} entity type{tmpl.entityTypeCount !== 1 ? 's' : ''}
                  </span>
                </div>
              </div>
            ))}
            {Array.from({ length: fillerCount }).map((_, i) => (
              <SkeletonCard key={`filler-${i}`} />
            ))}
          </motion.div>
        ) : (
          <motion.div key="skeleton" {...fadeProps} className="grid grid-cols-2 gap-4">
            {Array.from({ length: GRID_SLOT_COUNT }).map((_, i) => (
              <SkeletonCard key={i} />
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};
