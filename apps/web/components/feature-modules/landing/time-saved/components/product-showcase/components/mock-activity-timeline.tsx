import { AnimatePresence, motion } from 'motion/react';

import { MockBreadcrumb } from '@/components/ui/diagrams/brand-ui-primitives';
import type { ShowcaseScenario } from '@/components/feature-modules/landing/time-saved/components/product-showcase/scenario-types';

export function MockActivityTimeline({ scenario }: { scenario: ShowcaseScenario }) {
  return (
    <div
      style={{ width: 520 }}
      className="paper-lite flex flex-col rounded-xl border border-border bg-card shadow-lg"
    >
      {/* Header breadcrumb */}
      <div className="border-b border-border px-5 pt-4 pb-2.5">
        <MockBreadcrumb items={scenario.timelineBreadcrumb} />
      </div>

      <AnimatePresence mode="wait">
        <motion.div
          key={scenario.key}
          initial={{ opacity: 0, y: 6 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -6 }}
          transition={{ duration: 0.2, ease: 'easeOut' }}
        >
          {/* Title */}
          <div className="flex items-center justify-between px-5 pt-4">
            <h3 className="text-base font-semibold text-foreground">{scenario.timelineTitle}</h3>
            <span className="font-display text-xs tracking-[0.05em] text-muted-foreground uppercase">
              Feed
            </span>
          </div>

          {/* Timeline items */}
          <div className="flex flex-col gap-0 px-5 pt-3 pb-5">
            {scenario.activities.map((item, i) => (
              <div key={i} className="flex gap-3">
                {/* Timeline icon + line */}
                <div className="flex flex-col items-center">
                  <div className="mt-0.5 shrink-0">{item.sourceIcon}</div>
                  {i < scenario.activities.length - 1 && <div className="w-px flex-1 bg-border" />}
                </div>

                {/* Content */}
                <div className="pb-5">
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-muted-foreground/60">{item.date}</span>
                    <span className="font-display text-xs tracking-[0.05em] text-muted-foreground/50 uppercase">
                      {item.source}
                    </span>
                  </div>
                  <p className="mt-1 text-sm leading-snug font-medium text-foreground">
                    {item.title}
                  </p>
                  {item.detail && <p className="text-xs text-muted-foreground">{item.detail}</p>}
                </div>
              </div>
            ))}
          </div>
        </motion.div>
      </AnimatePresence>
    </div>
  );
}
