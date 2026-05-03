import { BrandNotion, BrandShopify, BrandSlack } from '@/components/ui/diagrams/brand-icons';
import { cn } from '@/lib/utils';
import { BookOpen, CalendarDays, Star } from 'lucide-react';

/**
 * Static entity-wiki diagram.
 *
 * Two layouts share the same node + entity content:
 *
 * - **Portrait** (default → md): entity centred, 4 nodes at corners, wires
 *   diagonally. Fills the tall portrait reel-card.
 * - **Landscape** (lg+): entity centred, 6 nodes in a 3×2 ring, wires
 *   horizontal. Original radial layout.
 *
 * Wires are SVG paths in fixed viewBox space with `preserveAspectRatio="none"`
 * so they stretch with the container. No measurement, no JS.
 */

interface NodeContent {
  id: string;
  kind: string;
  title: string;
  meta?: string;
  quote?: string;
  pill?: { text: string; tone: 'neutral' | 'warn' };
  icon: React.ReactNode;
  danger?: boolean;
}

function FallbackBadge({ children, bg }: { children: React.ReactNode; bg: string }) {
  return (
    <span
      className="flex h-3.5 w-3.5 shrink-0 items-center justify-center rounded-[3px] text-white"
      style={{ background: bg }}
    >
      {children}
    </span>
  );
}

const SALES: NodeContent = {
  id: 'sales',
  kind: 'Performance · sales drop',
  title: 'Weekly sell-through 312 → 194 units',
  meta: 'Shopify · 7d window',
  pill: { text: '▼ 38%', tone: 'warn' },
  icon: <BrandShopify size={14} />,
  danger: true,
};

const REVIEW: NodeContent = {
  id: 'review',
  kind: 'Review · 2★ flagged',
  title: '"Runs a full size small after one wash."',
  meta: 'Yotpo · 14 similar reviews this week',
  pill: { text: 'sizing', tone: 'warn' },
  icon: (
    <FallbackBadge bg="oklch(0.62 0.22 27)">
      <Star className="h-2 w-2 fill-current" />
    </FallbackBadge>
  ),
  danger: true,
};

const NOTE: NodeContent = {
  id: 'note',
  kind: 'Memo · pinned',
  title: 'Feb stock-out post-mortem',
  meta: 'Notion · cites this SKU · 161 missed orders',
  icon: <BrandNotion size={14} />,
};

const MEETING: NodeContent = {
  id: 'meeting',
  kind: 'Meeting note · Mar 18',
  title: 'Weekly ops · agreed action',
  quote: 'Pre-order any SKU crossing demand threshold within 48h.',
  icon: (
    <FallbackBadge bg="oklch(0.65 0.13 75)">
      <CalendarDays className="h-2 w-2" />
    </FallbackBadge>
  ),
};

const GLOSSARY: NodeContent = {
  id: 'glossary',
  kind: 'Glossary term',
  title: 'Sand colorway · "Field Series" SS26',
  meta: 'Internal · 12 SKUs share this attribute',
  icon: (
    <FallbackBadge bg="oklch(0.5 0.12 277)">
      <BookOpen className="h-2 w-2" />
    </FallbackBadge>
  ),
};

const SLACK: NodeContent = {
  id: 'slack',
  kind: 'Slack · #merch',
  title: 'Maya: "should we pull this from the homepage?"',
  quote: 'Jared → "let’s wait for the supplier QC report first."',
  pill: { text: '8 msgs', tone: 'neutral' },
  icon: <BrandSlack size={14} />,
};

export function MockEntityWiki() {
  return (
    <div className="flex h-full w-full flex-col gap-2 px-3 pb-3 sm:px-4 sm:pb-4">
      <div className="relative h-full w-full lg:hidden">
        <PortraitLayout />
      </div>
      <div className="relative hidden h-full w-full lg:block">
        <LandscapeLayout />
      </div>

      <div className="hidden items-center gap-3 px-1 font-mono text-[10px] text-muted-foreground sm:flex">
        <LegendDot tone="neutral" /> linked context
        <LegendDot tone="danger" /> negative signal
        <span className="ml-auto tabular-nums">9 of 23 nodes shown</span>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Portrait layout — tall card (default, sm, md)
// ---------------------------------------------------------------------------

const P_VB_W = 600;
const P_VB_H = 800;

// Entity centred in viewBox, generous size.
const P_ENTITY = { cx: 300, cy: 400, w: 240, h: 170 };

interface PortraitNode {
  node: NodeContent;
  /** Corner of the diagram. */
  corner: 'tl' | 'tr' | 'bl' | 'br';
  /** SVG endpoints in P viewBox. */
  wire: { fromX: number; fromY: number; toX: number; toY: number };
}

const P_NODES: PortraitNode[] = [
  {
    node: SALES,
    corner: 'tl',
    wire: { fromX: 180, fromY: 195, toX: P_ENTITY.cx - P_ENTITY.w / 2, toY: P_ENTITY.cy - 30 },
  },
  {
    node: REVIEW,
    corner: 'tr',
    wire: { fromX: 420, fromY: 195, toX: P_ENTITY.cx + P_ENTITY.w / 2, toY: P_ENTITY.cy - 30 },
  },
  {
    node: NOTE,
    corner: 'bl',
    wire: { fromX: 180, fromY: 605, toX: P_ENTITY.cx - P_ENTITY.w / 2, toY: P_ENTITY.cy + 30 },
  },
  {
    node: MEETING,
    corner: 'br',
    wire: { fromX: 420, fromY: 605, toX: P_ENTITY.cx + P_ENTITY.w / 2, toY: P_ENTITY.cy + 30 },
  },
];

function cornerClasses(corner: PortraitNode['corner']) {
  switch (corner) {
    case 'tl':
      return 'top-[2%] left-[2%]';
    case 'tr':
      return 'top-[2%] right-[2%]';
    case 'bl':
      return 'bottom-[2%] left-[2%]';
    case 'br':
      return 'bottom-[2%] right-[2%]';
  }
}

function PortraitLayout({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        'relative h-full w-full overflow-hidden rounded-lg border border-border',
        'bg-card bg-[radial-gradient(circle,oklch(0_0_0/0.07)_1px,transparent_1.2px)] bg-[length:14px_14px]',
        className,
      )}
    >
      <svg
        className="pointer-events-none absolute inset-0 h-full w-full"
        viewBox={`0 0 ${P_VB_W} ${P_VB_H}`}
        preserveAspectRatio="none"
      >
        {P_NODES.map(({ node, wire }) => (
          <Wire key={node.id} wire={wire} danger={node.danger} curveAxis="x" />
        ))}
      </svg>

      {/* Centre entity */}
      <div className="absolute top-1/2 left-1/2 z-10 w-[clamp(260px,52%,280px)] -translate-x-1/2 -translate-y-1/2">
        <EntityCard />
      </div>

      {P_NODES.map(({ node, corner }) => (
        <div
          key={node.id}
          className={cn('absolute z-[2] w-[clamp(120px,42%,220px)]', cornerClasses(corner))}
        >
          <NodeCard node={node} />
        </div>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Landscape layout — wide card (lg+)
// ---------------------------------------------------------------------------

const L_VB_W = 1000;
const L_VB_H = 600;

const L_ENTITY = { left: 380, right: 620, cy: 300 };

interface LandscapeNode {
  node: NodeContent;
  pos: { topPct?: number; bottomPct?: number; leftPct?: number; rightPct?: number };
  wire: { fromX: number; fromY: number; toX: number; toY: number };
}

function leftWire(fromX: number, fromY: number) {
  return { fromX, fromY, toX: L_ENTITY.left, toY: L_ENTITY.cy };
}
function rightWire(fromX: number, fromY: number) {
  return { fromX, fromY, toX: L_ENTITY.right, toY: L_ENTITY.cy };
}

const L_NODES: LandscapeNode[] = [
  { node: SALES, pos: { topPct: 4, leftPct: 1 }, wire: leftWire(250, 80) },
  { node: REVIEW, pos: { topPct: 4, rightPct: 1 }, wire: rightWire(750, 80) },
  { node: GLOSSARY, pos: { topPct: 38, leftPct: 1 }, wire: leftWire(250, 290) },
  { node: SLACK, pos: { topPct: 38, rightPct: 1 }, wire: rightWire(750, 290) },
  { node: NOTE, pos: { bottomPct: 4, leftPct: 1 }, wire: leftWire(250, 520) },
  { node: MEETING, pos: { bottomPct: 4, rightPct: 1 }, wire: rightWire(750, 520) },
];

function LandscapeLayout({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        'relative h-full w-full overflow-hidden rounded-lg border border-border',
        'bg-card bg-[radial-gradient(circle,oklch(0_0_0/0.07)_1px,transparent_1.2px)] bg-[length:14px_14px]',
        className,
      )}
    >
      <svg
        className="pointer-events-none absolute inset-0 h-full w-full"
        viewBox={`0 0 ${L_VB_W} ${L_VB_H}`}
        preserveAspectRatio="none"
      >
        {L_NODES.map(({ node, wire }) => (
          <Wire key={node.id} wire={wire} danger={node.danger} curveAxis="x" />
        ))}
      </svg>

      <div className="absolute top-1/2 left-1/2 z-10 w-[clamp(180px,24%,260px)] -translate-x-1/2 -translate-y-1/2">
        <EntityCard />
      </div>

      {L_NODES.map(({ node, pos }) => (
        <div
          key={node.id}
          style={{
            top: pos.topPct !== undefined ? `${pos.topPct}%` : undefined,
            bottom: pos.bottomPct !== undefined ? `${pos.bottomPct}%` : undefined,
            left: pos.leftPct !== undefined ? `${pos.leftPct}%` : undefined,
            right: pos.rightPct !== undefined ? `${pos.rightPct}%` : undefined,
          }}
          className="absolute z-[2] w-[clamp(120px,24%,200px)]"
        >
          <NodeCard node={node} />
        </div>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Shared cards
// ---------------------------------------------------------------------------

function EntityCard() {
  return (
    <div className="overflow-hidden rounded-lg border border-border bg-card shadow-md">
      <div className="flex items-center gap-2.5 bg-foreground px-3 py-2.5 text-background">
        <div
          className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md font-mono text-[10px] font-bold"
          style={{ background: 'oklch(0.92 0.03 70)', color: 'oklch(0.35 0.08 70)' }}
        >
          LFT
        </div>
        <div className="min-w-0 flex-1">
          <div className="truncate text-xs font-semibold tracking-tight">
            Linen Field Tee · Sand
          </div>
          <div className="truncate font-mono text-[9px] tracking-wide opacity-60">
            SKU LFT-S-SND · PRODUCT
          </div>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-2 p-3">
        <Stat label="In stock" value="84" />
        <Stat label="7d sales" value="−38%" hot />
        <Stat label="Reviews" value="3.6 ★" />
        <Stat label="Cover" value="4.2d" hot />
      </div>
      <div className="flex items-center gap-1.5 border-t border-border px-3 py-2 font-mono text-[10px] text-muted-foreground">
        <span
          className="inline-block h-1.5 w-1.5 rounded-full"
          style={{
            background: 'oklch(0.7 0.12 348)',
            boxShadow: '0 0 0 3px oklch(0.7 0.12 348 / 0.18)',
          }}
        />
        <span className="truncate">9 context links · updated 14m ago</span>
      </div>
    </div>
  );
}

function NodeCard({ node }: { node: NodeContent }) {
  return (
    <div
      className={cn(
        'rounded-md border bg-card p-1.5 shadow-sm sm:p-2.5',
        node.danger ? 'border-[oklch(0.85_0.08_27)]' : 'border-border',
      )}
    >
      <div className="mb-1.5 flex items-center gap-1.5">
        {node.icon}
        <span className="flex-1 truncate font-mono text-[8px] tracking-wider text-muted-foreground uppercase sm:text-[9px]">
          {node.kind}
        </span>
        {node.pill && <Pill tone={node.pill.tone}>{node.pill.text}</Pill>}
      </div>
      <div className="text-[10px] leading-snug font-medium tracking-tight text-heading sm:text-[11px]">
        {node.title}
      </div>
      {node.meta && (
        <div className="mt-1 font-mono text-[9px] leading-tight text-muted-foreground sm:text-[10px]">
          {node.meta}
        </div>
      )}
      {node.quote && (
        <div className="mt-1.5 rounded-r-[3px] border-l-2 border-border bg-background px-1.5 py-1 font-mono text-[9px] leading-snug text-content sm:text-[10px]">
          {node.quote}
        </div>
      )}
    </div>
  );
}

function Wire({
  wire,
  danger,
  curveAxis,
}: {
  wire: { fromX: number; fromY: number; toX: number; toY: number };
  danger?: boolean;
  /** Direction of the bezier handle — 'x' for horizontal-leaning, 'y' for vertical. */
  curveAxis: 'x' | 'y';
}) {
  const { fromX, fromY, toX, toY } = wire;
  let d: string;
  if (curveAxis === 'x') {
    const midX = (fromX + toX) / 2;
    d = `M ${fromX} ${fromY} C ${midX} ${fromY}, ${midX} ${toY}, ${toX} ${toY}`;
  } else {
    const midY = (fromY + toY) / 2;
    d = `M ${fromX} ${fromY} C ${fromX} ${midY}, ${toX} ${midY}, ${toX} ${toY}`;
  }
  const stroke = danger ? 'oklch(0.62 0.22 27 / 0.55)' : 'oklch(0.5 0.005 92 / 0.45)';
  const dot = danger ? 'oklch(0.62 0.22 27)' : 'oklch(0.5 0.005 92)';
  return (
    <g>
      <path
        d={d}
        fill="none"
        stroke={stroke}
        strokeWidth={1.4}
        strokeDasharray="4 4"
        vectorEffect="non-scaling-stroke"
      />
      <circle cx={toX} cy={toY} r={3} fill={dot} />
      <circle cx={fromX} cy={fromY} r={2.5} fill={dot} />
    </g>
  );
}

function Stat({ label, value, hot }: { label: string; value: string; hot?: boolean }) {
  return (
    <div className="rounded-[5px] border border-border bg-background px-1.5 py-1">
      <div className="font-mono text-[8px] tracking-wider text-muted-foreground uppercase">
        {label}
      </div>
      <div
        className={cn(
          'mt-0.5 font-display text-xs leading-none tracking-tight tabular-nums',
          hot ? 'text-[oklch(0.55_0.18_27)]' : 'text-heading',
        )}
      >
        {value}
      </div>
    </div>
  );
}

function Pill({ tone, children }: { tone: 'neutral' | 'warn'; children: React.ReactNode }) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border px-1.5 py-px font-mono text-[8px] sm:text-[9px]',
        tone === 'warn'
          ? 'border-[oklch(0.85_0.08_27)] bg-[oklch(0.97_0.025_27)] text-[oklch(0.45_0.18_27)]'
          : 'border-border bg-muted text-muted-foreground',
      )}
    >
      {children}
    </span>
  );
}

function LegendDot({ tone }: { tone: 'neutral' | 'danger' }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span
        className={cn(
          'inline-block h-px w-3 border-t border-dashed',
          tone === 'danger' ? 'border-[oklch(0.55_0.18_27)]' : 'border-[oklch(0.5_0.005_92)]',
        )}
      />
    </span>
  );
}
