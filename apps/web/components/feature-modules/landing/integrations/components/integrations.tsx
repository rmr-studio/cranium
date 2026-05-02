'use client';

import { BrandIcons, Integration } from '@/components/ui/diagrams/brand-icons';
import { Dither } from '@/components/ui/dither';
import { Section } from '@/components/ui/section';
import { cn } from '@/lib/utils';
import { FC, useRef } from 'react';

type Cell = { kind: 'icon'; brand: Integration } | { kind: 'highlight' } | { kind: 'empty' };

const COLS = 11;
const ROWS = 6;

// Inclusive bounds — keep this region clear so the centered heading has room.
const CENTER_CLEAR = { colStart: 3, colEnd: 7, rowStart: 1, rowEnd: 4 };

const isInCenterClear = (col: number, row: number) =>
  col >= CENTER_CLEAR.colStart &&
  col <= CENTER_CLEAR.colEnd &&
  row >= CENTER_CLEAR.rowStart &&
  row <= CENTER_CLEAR.rowEnd;

const ICON_PLACEMENTS: ReadonlyArray<readonly [number, number, Integration]> = [
  [1, 0, 'Gmail'],
  [9, 0, 'Klaviyo'],
  [2, 1, 'Slack'],
  [8, 1, 'Stripe'],
  [0, 2, 'Shopify'],
  [0, 3, 'Gorgias'],
  [10, 3, 'Instagram'],
  [1, 4, 'Intercom'],
  [7, 5, 'Facebook'],
  [3, 0, 'Google'],
  [7, 0, 'GoogleMeet'],
  [4, 1, 'GoogleSheets'],
  [6, 1, 'Cin7'],
];

const HIGHLIGHT_PLACEMENTS: ReadonlyArray<readonly [number, number]> = [
  [3, 0],
  [5, 0],
  [7, 0],
  [4, 1],
  [6, 1],
  [1, 2],
  [9, 2],
  [2, 3],
  [8, 3],
  [3, 4],
  [5, 4],
  [7, 4],
  [1, 5],
  [5, 5],
  [9, 5],
];

const layout: Cell[] = (() => {
  const cells: Cell[] = Array.from({ length: COLS * ROWS }, () => ({ kind: 'empty' }));
  for (const [col, row, brand] of ICON_PLACEMENTS) {
    if (isInCenterClear(col, row)) continue;
    cells[row * COLS + col] = { kind: 'icon', brand };
  }
  for (const [col, row] of HIGHLIGHT_PLACEMENTS) {
    if (isInCenterClear(col, row)) continue;
    if (cells[row * COLS + col].kind === 'empty') {
      cells[row * COLS + col] = { kind: 'highlight' };
    }
  }
  return cells;
})();

interface IntegrationsProps {
  className?: string;
}

export function Integrations({ className }: IntegrationsProps) {
  const sectionRef = useRef<HTMLElement>(null);

  return (
    <Section
      className={cn('relative w-full overflow-hidden bg-background py-32 md:py-44', className)}
    >
      <section ref={sectionRef} className="absolute inset-0 z-[60] h-[60rem]">
        <Dither
          sectionRef={sectionRef}
          fillColor="oklch(0.145 0 0)"
          pattern="noise"
          seed={7}
          inverse
          direction="bottom-up"
          startWeight={-0.5}
        />
      </section>

      <div className="mx-auto mt-[20rem] w-full 2xl:max-w-[min(90dvw,var(--breakpoint-3xl))]">
        <div className="pointer-events-none z-10 mb-8 flex flex-col items-center px-6 text-center md:hidden">
          <span className="rounded-full bg-card px-3 py-1 font-mono text-xs tracking-widest text-muted-foreground uppercase shadow-sm ring-1 ring-foreground/8">
            Integrations
          </span>
          <h2 className="mt-6 max-w-3xl font-bit text-3xl leading-tight tracking-tight text-foreground sm:text-5xl md:text-6xl">
            Riven brings in context from everywhere
          </h2>
          <p className="mt-6 max-w-xl font-display text-base leading-snug tracking-tight text-content/70">
            Plug in the systems you already run on. Riven listens, links, and learns across every
            tool — so context never gets lost between tabs.
          </p>
        </div>
        <div className="relative px-4 sm:px-8 md:px-12">
          <IntegrationGrid />

          <div className="pointer-events-none absolute inset-x-0 top-1/2 z-10 hidden -translate-y-1/2 flex-col items-center px-6 text-center md:flex">
            <span className="rounded-full bg-card px-3 py-1 font-mono text-xs tracking-widest text-muted-foreground uppercase shadow-sm ring-1 ring-foreground/8">
              Integrations
            </span>
            <h2 className="mt-6 max-w-3xl font-bit text-3xl leading-tight tracking-tight text-foreground sm:text-5xl md:text-6xl">
              Riven brings in context from everywhere
            </h2>
            <p className="mt-6 max-w-xl font-display text-base leading-snug tracking-tight text-content/70">
              Plug in the systems you already run on. Riven listens, links, and learns across every
              tool — so context never gets lost between tabs.
            </p>
          </div>
        </div>
      </div>
    </Section>
  );
}

function IntegrationGrid() {
  return (
    <div
      className="grid w-full gap-1.5 sm:gap-2"
      style={{ gridTemplateColumns: `repeat(${COLS}, minmax(0, 1fr))` }}
      aria-hidden
    >
      {layout.map((cell, i) => (
        <GridCell key={i} cell={cell} />
      ))}
    </div>
  );
}

const GridCell: FC<{ cell: Cell }> = ({ cell }) => {
  if (cell.kind === 'icon') {
    const Icon = BrandIcons[cell.brand];
    return (
      <div className="relative flex aspect-square items-center justify-center rounded-sm bg-card shadow-md ring-1 ring-foreground/5">
        <Icon size={64} />
      </div>
    );
  }
  if (cell.kind === 'highlight') {
    return (
      <div className="aspect-square rounded-sm bg-foreground/[0.035] ring-1 ring-foreground/[0.04] ring-inset" />
    );
  }
  return <div className="aspect-square" />;
};
