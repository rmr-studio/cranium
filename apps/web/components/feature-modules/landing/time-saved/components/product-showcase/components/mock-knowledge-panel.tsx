import { Sparkles } from 'lucide-react';
import { AnimatePresence, motion } from 'motion/react';

import type { ShowcaseScenario } from '@/components/feature-modules/landing/time-saved/components/product-showcase/scenario-types';
import { MockBreadcrumb } from '@/components/ui/diagrams/brand-ui-primitives';
import { ClassNameProps, cn } from '@riven/utils';
import { FC } from 'react';

interface Props extends ClassNameProps {
  scenario: ShowcaseScenario;
}

export const MockKnowledgePanel: FC<Props> = ({ scenario, className }) => {
  return (
    <div
      style={{ width: 680 }}
      className={cn(
        'paper-lite flex flex-col rounded-xl border border-border bg-card shadow-xl',
        className,
      )}
    >
      {/* Breadcrumb */}
      <div className="border-b border-border px-5 pt-4 pb-1.5 pb-3 text-muted-foreground/60">
        <MockBreadcrumb items={['Workspace', 'Knowledge Base']} />
      </div>

      <AnimatePresence mode="wait">
        <motion.div
          key={scenario.key}
          initial={{ opacity: 0, y: 6 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -6 }}
          transition={{ duration: 0.2, ease: 'easeOut' }}
        >
          {/* Query input */}
          <div className="px-5 pt-4 pb-3">
            <div className="flex items-center gap-2.5 rounded-lg border border-border bg-muted/20 px-4 py-3">
              <Sparkles className="size-4 text-muted-foreground/50" />
              <span className="text-sm text-foreground/80">{scenario.kbQuery}</span>
            </div>
          </div>

          {/* Retrieved section */}
          <div className="space-y-4 px-5 pb-3">
            <div className="flex items-start gap-3">
              <span className="mt-0.5 shrink-0 font-display text-xs tracking-[0.08em] text-muted-foreground/50 uppercase">
                Retrieved
              </span>
              <div className="flex flex-wrap gap-1.5">
                {scenario.kbRetrieved.map((name) => (
                  <span
                    key={name}
                    className="rounded bg-muted px-2 py-0.5 text-xs text-foreground/70"
                  >
                    {name}
                  </span>
                ))}
                <span className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                  +131 more
                </span>
              </div>
            </div>

            {/* Analysed section */}
            <div className="flex items-start gap-3">
              <span className="mt-0.5 shrink-0 font-display text-xs tracking-[0.08em] text-muted-foreground/50 uppercase">
                Analysed
              </span>
              <div>
                <p className="text-sm font-medium text-foreground">{scenario.kbAnalysedTitle}</p>
                <div className="mt-2 flex gap-3">
                  {scenario.kbAnalysedCards.map((card, i) => (
                    <div
                      key={i}
                      className="flex items-start gap-2 rounded-md border border-border/50 px-3 py-1.5"
                    >
                      {card.icon}
                      <div>
                        <p className="text-xs font-medium text-muted-foreground">{card.title}</p>
                        <p className="text-xs text-muted-foreground/60">{card.detail}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* Identified section */}
            <div className="flex items-start gap-3">
              <span className="mt-0.5 shrink-0 font-display text-xs tracking-[0.08em] text-muted-foreground/50 uppercase">
                Identified
              </span>
              <div>
                <button className="flex items-center gap-1.5 rounded-full bg-foreground px-3.5 py-1 text-xs font-medium text-background">
                  <span>+</span>
                  {scenario.kbIdentified}
                </button>
              </div>
            </div>
          </div>

          {/* AI Response */}
          <div className="border-t border-border px-5 pt-4 pb-5">
            <div className="flex items-start gap-2.5">
              <Sparkles className="mt-0.5 size-4 shrink-0 text-muted-foreground/40" />
              <div className="space-y-3">{scenario.kbResponse}</div>
            </div>
          </div>
        </motion.div>
      </AnimatePresence>
    </div>
  );
};
