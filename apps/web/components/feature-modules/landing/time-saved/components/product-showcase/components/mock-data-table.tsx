import { cn } from '@/lib/utils';
import { CogIcon, Filter, Search } from 'lucide-react';
import { AnimatePresence, motion } from 'motion/react';

import { MockBreadcrumb, TableHeader } from '@/components/ui/diagrams/brand-ui-primitives';
import type { ShowcaseScenario } from '@/components/feature-modules/landing/time-saved/components/product-showcase/scenario-types';

export function MockDataTable({ scenario }: { scenario: ShowcaseScenario }) {
  return (
    <div className="paper-lite flex flex-1 flex-col bg-background">
      {/* Top bar: breadcrumb */}
      <div className="flex h-12 shrink-0 items-center justify-between border-b border-border px-6">
        <MockBreadcrumb items={['Home', '...', 'Entities', scenario.tableTitle]} />
      </div>

      <AnimatePresence mode="wait">
        <motion.div
          key={scenario.key}
          initial={{ opacity: 0, y: 6 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -6 }}
          transition={{ duration: 0.2, ease: 'easeOut' }}
          className="flex flex-1 flex-col"
        >
          {/* Title area */}
          <div className="px-6 pt-5 pb-2">
            <div className="flex items-start gap-3">
              <div className="flex size-8 items-center justify-center rounded-lg border border-border bg-muted/30">
                <span className={cn('size-4', scenario.entityColor)}>{scenario.entityIcon}</span>
              </div>
              <div>
                <h2 className="text-2xl leading-none font-bold -tracking-[0.02em] text-foreground">
                  {scenario.tableTitle}
                </h2>
                <p className="mt-1 text-xs text-muted-foreground">{scenario.tableSubtitle}</p>
              </div>
            </div>

            {/* Tabs + search row */}
            <div className="mt-4 flex items-center justify-between">
              <div className="flex items-center gap-1">
                <span className="rounded-md bg-foreground px-3 py-1 text-xs font-medium text-background">
                  Entities
                </span>
                <span className="flex items-center gap-1 rounded-md px-3 py-1 text-xs text-muted-foreground">
                  <CogIcon className="size-3" />
                  Settings
                </span>
              </div>
              <button className="flex items-center gap-1.5 rounded-md border border-border px-3 py-1.5 text-xs text-foreground">
                <Filter className="size-3" />
                Filter
              </button>
            </div>

            {/* Search bar */}
            <div className="mt-3 flex items-center gap-2 rounded-lg border border-border px-3 py-2">
              <Search className="size-4 text-muted-foreground/40" />
              <span className="text-sm text-muted-foreground/40">{scenario.searchPlaceholder}</span>
            </div>
          </div>

          {/* Table */}
          <div className="flex-1 overflow-hidden px-4">
            {/* Header row */}
            <div
              className="grid items-center gap-x-2 border-b border-border px-2 py-2"
              style={{ gridTemplateColumns: scenario.tableColTemplate }}
            >
              <div className="flex items-center justify-center">
                <div className="size-3.5 rounded-sm border border-border" />
              </div>
              {scenario.tableHeaders.map((h, i) => (
                <TableHeader key={i} icon={h.icon} label={h.label} />
              ))}
            </div>

            {/* Data rows */}
            {scenario.tableRows.map((row, i) => (
              <div
                key={i}
                className="grid items-center gap-x-2 border-b border-border/40 px-2 py-1"
                style={{ gridTemplateColumns: scenario.tableColTemplate }}
              >
                <div className="flex items-center justify-center">
                  <div className="size-3.5 rounded-sm border border-border/60" />
                </div>
                {row.cells.map((cell, j) => (
                  <div key={j}>{cell}</div>
                ))}
              </div>
            ))}
          </div>
        </motion.div>
      </AnimatePresence>
    </div>
  );
}
