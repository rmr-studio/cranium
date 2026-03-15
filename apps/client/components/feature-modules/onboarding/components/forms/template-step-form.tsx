'use client';

import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/util/utils';
import { AnimatePresence, motion } from 'motion/react';
import { ChevronDown, ChevronUp, X } from 'lucide-react';
import { FC, useEffect, useState } from 'react';
import { useBundles } from '@/components/feature-modules/onboarding/hooks/query/use-bundles';
import {
  useOnboardStoreApi,
  useOnboardFormControls,
  useOnboardNavigation,
} from '@/components/feature-modules/onboarding/hooks/use-onboard-store';

interface TemplatesLiveData {
  selectedBundleKey: string | null;
  bundles?: import('@/lib/types').BundleDetail[];
  templates?: import('@/lib/types').ManifestSummary[];
}

export function toggleBundleSelection(
  current: string | null,
  clicked: string,
): string | null {
  return current === clicked ? null : clicked;
}

export const TemplateStepForm: FC = () => {
  const storeApi = useOnboardStoreApi();
  const { setLiveData, registerFormTrigger, clearFormTrigger } = useOnboardFormControls();
  const { skip } = useOnboardNavigation();
  const [restoredData] = useState(
    () => storeApi.getState().liveData['templates'] as TemplatesLiveData | undefined,
  );

  const [selectedBundleKey, setSelectedBundleKey] = useState<string | null>(
    restoredData?.selectedBundleKey ?? null,
  );
  const [expandedBundleKey, setExpandedBundleKey] = useState<string | null>(null);

  const { bundles, templates, isLoading } = useBundles();

  // Register always-true formTrigger on mount (optional step)
  useEffect(() => {
    registerFormTrigger(async () => true);
    return () => clearFormTrigger();
  }, [registerFormTrigger, clearFormTrigger]);

  // Sync live data whenever selection or data changes
  useEffect(() => {
    setLiveData('templates', { selectedBundleKey, bundles, templates });
  }, [selectedBundleKey, bundles, templates, setLiveData]);

  const handleBundleClick = (key: string) => {
    const next = toggleBundleSelection(selectedBundleKey, key);
    setSelectedBundleKey(next);
  };

  const handleExpandToggle = (e: React.MouseEvent, key: string) => {
    e.stopPropagation();
    setExpandedBundleKey((prev) => (prev === key ? null : key));
  };

  if (isLoading) {
    return (
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="bg-card rounded-xl border p-5 shadow-sm">
            <Skeleton className="mb-3 h-4 w-3/4" />
            <Skeleton className="mb-2 h-3 w-full" />
            <Skeleton className="mb-4 h-3 w-4/5" />
            <Skeleton className="h-5 w-16 rounded-full" />
          </div>
        ))}
      </div>
    );
  }

  if (bundles.length === 0) {
    return (
      <p className="text-muted-foreground text-sm">
        No template bundles available yet. You can set up entity types manually from your
        workspace.
      </p>
    );
  }

  const handleSkipTemplates = () => {
    setSelectedBundleKey(null);
    setLiveData('templates', { selectedBundleKey: null, bundles, templates });
    skip();
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
      {bundles.map((bundle) => {
        const isSelected = selectedBundleKey === bundle.key;
        const isExpanded = expandedBundleKey === bundle.key;
        const matchedTemplates = bundle.templateKeys
          .map((key) => templates.find((t) => t.key === key))
          .filter(Boolean) as import('@/lib/types').ManifestSummary[];

        return (
          <div key={bundle.key} className="flex flex-col">
            <div
              role="button"
              tabIndex={0}
              onClick={() => handleBundleClick(bundle.key)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  handleBundleClick(bundle.key);
                }
              }}
              className={cn(
                'bg-card flex flex-col rounded-xl border p-5 shadow-sm transition-all',
                'cursor-pointer text-left',
                isSelected
                  ? 'ring-primary border-primary ring-2'
                  : 'border-border hover:border-muted-foreground/40',
              )}
            >
              <span className="text-sm font-semibold">{bundle.name}</span>

              {bundle.description && (
                <span className="text-muted-foreground mt-1 line-clamp-2 text-xs">
                  {bundle.description}
                </span>
              )}

              <div className="mt-4 flex items-center justify-between">
                <span className="text-muted-foreground bg-muted rounded-full px-2 py-0.5 text-xs">
                  {bundle.templateKeys.length} template
                  {bundle.templateKeys.length !== 1 ? 's' : ''}
                </span>

                <button
                  type="button"
                  onClick={(e) => handleExpandToggle(e, bundle.key)}
                  onKeyDown={(e) => e.stopPropagation()}
                  className="text-muted-foreground hover:text-foreground flex items-center gap-1 text-xs transition-colors"
                >
                  Preview
                  {isExpanded ? (
                    <ChevronUp className="size-3" />
                  ) : (
                    <ChevronDown className="size-3" />
                  )}
                </button>
              </div>
            </div>

            <AnimatePresence initial={false}>
              {isExpanded && (
                <motion.div
                  key="expanded"
                  initial={{ height: 0, opacity: 0 }}
                  animate={{ height: 'auto', opacity: 1 }}
                  exit={{ height: 0, opacity: 0 }}
                  transition={{ duration: 0.2, ease: 'easeInOut' }}
                  className="overflow-hidden"
                >
                  <div className="bg-muted/50 mt-1 rounded-xl p-4">
                    <p className="text-muted-foreground mb-2 text-xs font-semibold uppercase tracking-widest">
                      Templates in this bundle
                    </p>
                    <div className="flex flex-col gap-2">
                      {bundle.templateKeys.map((key) => {
                        const tmpl = templates.find((t) => t.key === key);
                        return (
                          <div
                            key={key}
                            className="bg-card flex items-center justify-between rounded-lg px-3 py-2"
                          >
                            <span className="text-sm font-medium">
                              {tmpl ? tmpl.name : key}
                            </span>
                            {tmpl && (
                              <span className="text-muted-foreground text-xs">
                                {tmpl.entityTypeCount} entity type
                                {tmpl.entityTypeCount !== 1 ? 's' : ''}
                              </span>
                            )}
                          </div>
                        );
                      })}
                      {matchedTemplates.length === 0 && (
                        <p className="text-muted-foreground text-xs">
                          Template details not available.
                        </p>
                      )}
                    </div>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        );
      })}
      </div>

      <div className="flex items-center gap-3">
        {selectedBundleKey && (
          <button
            type="button"
            onClick={() => setSelectedBundleKey(null)}
            className="text-muted-foreground hover:text-foreground flex items-center gap-1 text-xs transition-colors"
          >
            <X className="size-3" />
            Clear selection
          </button>
        )}

        <button
          type="button"
          onClick={handleSkipTemplates}
          className="text-muted-foreground hover:text-foreground ml-auto text-xs transition-colors"
        >
          Skip templates
        </button>
      </div>
    </div>
  );
};
