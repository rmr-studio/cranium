'use client';

import { BrandGorgias, BrandIntercom, BrandShopify } from '@/components/ui/diagrams/brand-icons';
import { WindowControls } from '@/components/ui/window-controls';
import { useContainerScale } from '@/hooks/use-container-scale';
import { cn } from '@/lib/utils';

// ── Source Chip ──────────────────────────────────────────────────────────

function SourceChip({ icon, label }: { icon: React.ReactNode; label: string }) {
  return (
    <div className="inline-flex items-center gap-[3px] rounded-[3px] border border-border/50 bg-card px-1 py-px">
      {icon}
      <span className="text-[5.5px] font-medium -tracking-[0.03em] text-muted-foreground">
        {label}
      </span>
    </div>
  );
}

// ── Bar Chart Data ──────────────────────────────────────────────────────

const barData = [
  { label: 'Shipping delays', pct: 42, bg: 'bg-foreground/60' },
  { label: 'Product quality', pct: 28, bg: 'bg-foreground/40' },
  { label: 'Billing issues', pct: 18, bg: 'bg-foreground/25' },
  { label: 'Other', pct: 12, bg: 'bg-foreground/15' },
];

// ── Table Data ──────────────────────────────────────────────────────────

const tableRows = [
  {
    name: 'Aperture Digital',
    ltv: '$12,400',
    tickets: '3',
    source: <SourceChip icon={<BrandGorgias size={8} />} label="Gorgias" />,
  },
  {
    name: 'Kinetic Cloud',
    ltv: '$8,200',
    tickets: '2',
    source: <SourceChip icon={<BrandIntercom size={8} />} label="Intercom" />,
  },
  {
    name: 'Datapulse',
    ltv: '$6,800',
    tickets: '1',
    source: <SourceChip icon={<BrandGorgias size={8} />} label="Gorgias" />,
  },
  {
    name: 'Meridian Labs',
    ltv: '$4,100',
    tickets: '2',
    source: <SourceChip icon={<BrandIntercom size={8} />} label="Intercom" />,
  },
];

// ── Main Component ──────────────────────────────────────────────────────

const INTERNAL_WIDTH = 580;

export const KnowledgeChatGraphic = ({ className }: { className?: string }) => {
  const { containerRef, scale } = useContainerScale(INTERNAL_WIDTH);

  return (
    <div ref={containerRef} className={cn('relative w-full', className)}>
      <div
        className="origin-top-left"
        style={{
          width: INTERNAL_WIDTH,
          fontFamily: 'var(--font-mono)',
          transform: `scale(${scale})`,
        }}
      >
        {/* ── Outer Card ── */}
        <div className="relative flex flex-col overflow-hidden rounded-xl border border-border bg-card shadow-lg">
          <div className="flex-1 space-y-4 p-6 pb-4">
            {/* ── Window Controls ── */}
            <WindowControls size={6} />

            {/* ── User Message 1 ── */}
            <div className="flex justify-end">
              <div className="rounded-sm bg-foreground/[0.05] px-2 py-1">
                <p className="text-[9px] leading-[1.5] -tracking-[0.03em] text-foreground">
                  What&apos;s driving the increase in support tickets this month?
                </p>
              </div>
            </div>

            {/* ── AI Response 1 ── */}
            <div className="space-y-2">
              <h3 className="text-[10px] font-semibold text-foreground">Support Ticket Analysis</h3>
              <p className="text-[8px] leading-[1.5] -tracking-[0.03em] text-muted-foreground">
                Support volume is up 34% month-over-month, driven by:
              </p>

              {/* ── Horizontal Bar Chart ── */}
              <div className="space-y-1.5">
                {barData.map((bar) => (
                  <div key={bar.label} className="flex items-center">
                    <span className="w-24 shrink-0 text-[7px] -tracking-[0.03em] text-muted-foreground">
                      {bar.label}
                    </span>
                    <div
                      className={cn('h-3 rounded-sm', bar.bg)}
                      style={{ width: `${bar.pct}%` }}
                    />
                    <span className="ml-1 text-[7px] text-muted-foreground">{bar.pct}%</span>
                  </div>
                ))}
              </div>

              {/* ── Source Chips ── */}
              <div className="flex gap-1">
                <SourceChip icon={<BrandGorgias size={8} />} label="Gorgias" />
                <SourceChip icon={<BrandIntercom size={8} />} label="Intercom" />
                <SourceChip icon={<BrandShopify size={8} />} label="Shopify" />
              </div>
            </div>

            {/* ── User Message 2 ── */}
            <div className="flex justify-end">
              <div className="rounded-sm bg-foreground/[0.05] px-2 py-1">
                <p className="text-[9px] leading-[1.5] -tracking-[0.03em] text-foreground">
                  Which customers affected by shipping delays have the highest LTV?
                </p>
              </div>
            </div>

            {/* ── AI Response 2 ── */}
            <div className="space-y-2">
              <h3 className="text-[10px] font-semibold text-foreground">
                Affected High-LTV Customers
              </h3>

              {/* ── Compact Table ── */}
              <div>
                {/* Header */}
                <div className="flex border-b border-border/60 pb-1">
                  <span className="flex-1 text-[7px] font-medium tracking-wide text-muted-foreground/70 uppercase">
                    Name
                  </span>
                  <span className="w-16 text-[7px] font-medium tracking-wide text-muted-foreground/70 uppercase">
                    LTV
                  </span>
                  <span className="w-14 text-[7px] font-medium tracking-wide text-muted-foreground/70 uppercase">
                    Tickets
                  </span>
                  <span className="w-20 text-[7px] font-medium tracking-wide text-muted-foreground/70 uppercase">
                    Source
                  </span>
                </div>

                {/* Rows */}
                {tableRows.map((row) => (
                  <div key={row.name} className="flex items-center border-b border-border/40 py-1">
                    <span className="flex-1 text-[8px] -tracking-[0.03em] text-foreground">
                      {row.name}
                    </span>
                    <span className="w-16 text-[8px] -tracking-[0.03em] text-foreground">
                      {row.ltv}
                    </span>
                    <span className="w-14 text-[8px] -tracking-[0.03em] text-muted-foreground">
                      {row.tickets}
                    </span>
                    <span className="w-20">{row.source}</span>
                  </div>
                ))}
              </div>

              <p className="mt-2 text-[8px] leading-[1.5] -tracking-[0.03em] text-muted-foreground">
                These 4 customers represent $31,500 in lifetime value. Two have tickets open longer
                than 7 days.
              </p>
            </div>
          </div>

          {/* ── Chat Input Bar ── */}
          <div className="border-t border-border/40 px-5 py-3">
            <div className="mb-1.5 flex items-center rounded-lg border border-border/50 bg-card px-3 py-2">
              <span className="text-[8px] -tracking-[0.03em] text-muted-foreground/40">
                Draft a re-engagement email for these customers...
              </span>
              <div className="ml-auto flex h-5 w-5 items-center justify-center rounded-md bg-foreground/[0.07]">
                <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                  <path
                    d="M5 8V2M5 2L2.5 4.5M5 2L7.5 4.5"
                    className="stroke-muted-foreground"
                    strokeOpacity="0.5"
                    strokeWidth="1.2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </div>
            </div>
            <div className="flex items-center gap-3">
              {[
                // Plus icon
                <svg key="plus" width="10" height="10" viewBox="0 0 10 10" fill="none">
                  <path
                    d="M5 2v6M2 5h6"
                    stroke="currentColor"
                    strokeWidth="1"
                    strokeLinecap="round"
                  />
                </svg>,
                // Lightning icon
                <svg key="bolt" width="10" height="10" viewBox="0 0 10 10" fill="none">
                  <path
                    d="M5.5 1L2.5 6H5L4.5 9L7.5 4H5L5.5 1Z"
                    stroke="currentColor"
                    strokeWidth="0.8"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>,
                // Globe icon
                <svg key="globe" width="10" height="10" viewBox="0 0 10 10" fill="none">
                  <circle cx="5" cy="5" r="4" stroke="currentColor" strokeWidth="0.8" />
                  <path
                    d="M1 5h8M5 1c1.5 1.5 1.5 6.5 0 8M5 1c-1.5 1.5-1.5 6.5 0 8"
                    stroke="currentColor"
                    strokeWidth="0.8"
                  />
                </svg>,
                // Refresh icon
                <svg key="refresh" width="10" height="10" viewBox="0 0 10 10" fill="none">
                  <path
                    d="M1.5 5a3.5 3.5 0 0 1 6.4-2M8.5 5a3.5 3.5 0 0 1-6.4 2"
                    stroke="currentColor"
                    strokeWidth="0.8"
                    strokeLinecap="round"
                  />
                  <path
                    d="M7 1.5L8 3l-1.5.5M3 8.5L2 7l1.5-.5"
                    stroke="currentColor"
                    strokeWidth="0.8"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>,
              ].map((icon, i) => (
                <div key={i} className="text-muted-foreground/30">
                  {icon}
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

// ── Re-export alias for backward compatibility ──────────────────────────
export const ChatResponseGraphic = KnowledgeChatGraphic;
