import { BrandShopify, BrandSlack } from '@/components/ui/diagrams/brand-icons';
import { cn } from '@/lib/utils';
import { Logo } from '@riven/ui/logo';
import { ClassNameProps } from '@riven/utils';
import { Mail, Search } from 'lucide-react';

/**
 * Static action-memory diagram.
 *
 * Story: a user teaches Riven a procedure once in chat → Riven runs it →
 * the procedure becomes saved memory + a glossary term.
 *
 * - **Portrait** (default → md): vertical narrative stack. Chat → run →
 *   memory + glossary side by side. Fits the tall reel-card.
 * - **Landscape** (lg+): two-column composition. Chat anchors the left,
 *   run / memory / glossary stack on the right.
 */

export function MockActionMemory() {
  return (
    <div className="flex h-full w-full flex-col gap-2 px-3 pb-3 sm:px-4 sm:pb-4">
      <div className="h-full lg:hidden">
        <PortraitLayout />
      </div>
      <div className="hidden h-full lg:block">
        <LandscapeLayout />
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Portrait layout — tall card (default → md)
// ---------------------------------------------------------------------------

function PortraitLayout() {
  return (
    <div
      className={cn(
        'relative flex h-full w-full flex-col gap-3 overflow-hidden p-3 pt-8',
        'bg-card bg-[radial-gradient(circle,oklch(0_0_0/0.07)_1px,transparent_1.2px)] bg-[length:14px_14px]',
      )}
    >
      <div className="mx-auto hidden w-5/6 md:block">
        <ChatCard />
      </div>

      <FlowArrow />

      <div className="mx-auto flex flex-col space-y-8 sm:w-2/3">
        <MemoryCard compact />
        <GlossaryCard compact />
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Landscape layout — wide card (lg+)
// ---------------------------------------------------------------------------

function LandscapeLayout() {
  return (
    <div
      className={cn(
        'relative grid h-full w-full gap-3 overflow-hidden rounded-lg border border-border p-4',
        'bg-card bg-[radial-gradient(circle,oklch(0_0_0/0.07)_1px,transparent_1.2px)] bg-[length:14px_14px]',
      )}
    >
      <div className="absolute top-1/2 left-8 h-full w-full translate-x-24 -translate-y-[25%] md:scale-120 lg:w-1/2 xl:scale-130 3xl:scale-110">
        <StageMarker n={1} />
        <ChatCard />
      </div>
      <div className="absolute right-12 bottom-12 flex flex-col space-y-4">
        <StageMarker n={3} />
        <MemoryCard />
        <GlossaryCard />
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Stage helpers
// ---------------------------------------------------------------------------

function Stage({ n, children }: { n: number; children: React.ReactNode }) {
  return (
    <div className="relative">
      <StageMarker n={n} />
      {children}
    </div>
  );
}

function StageMarker({ n }: { n: number }) {
  return (
    <div className="absolute -top-2 -left-2 z-20 flex h-5 w-5 items-center justify-center rounded-full bg-foreground font-mono text-[10px] font-bold text-background shadow-md">
      {n}
    </div>
  );
}

function FlowArrow() {
  return (
    <div className="flex justify-center">
      <svg width="14" height="20" viewBox="0 0 14 20" className="text-muted-foreground/60">
        <path
          d="M 7 0 L 7 14"
          stroke="currentColor"
          strokeWidth="1.4"
          strokeDasharray="3 3"
          fill="none"
        />
        <path d="M 3 12 L 7 18 L 11 12" stroke="currentColor" strokeWidth="1.4" fill="none" />
      </svg>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Card 1 — Chat extract
// ---------------------------------------------------------------------------

interface ChatCardProps extends ClassNameProps {
  compact?: boolean;
}

export const ChatCard: React.FC<ChatCardProps> = ({ compact, className }) => {
  return (
    <div
      className={cn(
        'h-fit overflow-hidden rounded-lg border border-border bg-card shadow-md',
        className,
      )}
    >
      <CardRibbon left="Chat · #merch-ops" right="Today · 14:02" />

      <div className={cn('flex flex-col gap-3', compact ? 'px-3 py-3' : 'px-4 py-4')}>
        <Message author="Riven" badge="APP" time="14:02" avatar={<></>}>
          <span>Flagging </span>
          <b className="text-heading">Linen Field Tee · Sand</b>
          <span> — </span>
          <SignalPill>demand spike</SignalPill>
          <span>
            {' '}
            <b className="text-heading">+340%</b> units sold in the last 24h vs. 30d baseline (
            <b className="text-heading">612 units</b>, 4.2× normal velocity). <Term>Inventory</Term>{' '}
            at <b className="text-heading">18 days cover</b>, down from 41. Pulled from{' '}
            <Term>Shopify</Term>, cross-referenced with <Term>Klaviyo</Term> campaign send 36h ago.
          </span>
          <div className="mt-2 rounded-md border border-dashed border-border bg-background px-2.5 py-2 text-[11px] leading-relaxed">
            <b className="text-heading">No prior playbook in action memory</b> for a spike on this
            product class — first time I&apos;ve seen this signal on a{' '}
            <Term>&quot;hero SKU&quot;</Term>. How does the team handle it?
          </div>
        </Message>

        <Message
          author="Jared Tucker"
          time="14:05"
          avatar={<Avatar bg="oklch(0.7 0.12 348)">ER</Avatar>}
        >
          <span>A </span>
          <Term>hero SKU</Term>
          <span>
            {' '}
            is anything in the top 5% of last-30d revenue. When demand spikes on a hero, do this
            every time:
          </span>
          <Steps
            items={[
              <>
                <b>Ping #merch-ops</b>, tag <Mention>@Maya</Mention>
              </>,
              <>
                <b>Email Mira Textiles</b> for lead time + 600u quote
              </>,
              <>
                <b>Run a competitor scan</b> on linen tees in our band
              </>,
              <>
                <b>Draft a pre-order page</b> — wait for me
              </>,
            ]}
          />
        </Message>

        {!compact && (
          <Message author="Riven" badge="APP" time="14:05" avatar={<Logo size={18} />}>
            Got it. Running it now and saving this as the standard for hero-SKU spikes →
          </Message>
        )}
      </div>
    </div>
  );
};

function Avatar({ bg, children }: { bg: string; children: React.ReactNode }) {
  return (
    <div
      className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-[11px] font-semibold text-white"
      style={{ background: bg }}
    >
      {children}
    </div>
  );
}

function Message({
  author,
  badge,
  time,
  avatar,
  children,
}: {
  author: string;
  badge?: string;
  time: string;
  avatar: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <div className="flex gap-2.5 text-[12px]">
      {avatar}
      <div className="min-w-0 flex-1">
        <div className="mb-1 flex items-baseline gap-2">
          <span className="text-[12px] font-semibold tracking-tight text-heading">{author}</span>
          {badge && (
            <span
              className="rounded-[3px] px-1 py-px font-mono text-[9px] font-bold tracking-wider"
              style={{ background: 'var(--foreground)', color: 'oklch(0.97 0.008 92)' }}
            >
              {badge}
            </span>
          )}
          <span className="font-mono text-[10px] text-muted-foreground">{time}</span>
        </div>
        <div className="leading-relaxed text-content">{children}</div>
      </div>
    </div>
  );
}

function Steps({ items }: { items: React.ReactNode[] }) {
  return (
    <ol className="mt-2 space-y-0.5 rounded-md border border-border bg-card px-2.5 py-2 font-mono text-[10.5px] leading-relaxed text-content">
      {items.map((item, i) => (
        <li key={i} className="grid grid-cols-[14px_1fr] gap-1.5">
          <span className="font-bold text-muted-foreground">{i + 1}.</span>
          <span>{item}</span>
        </li>
      ))}
    </ol>
  );
}

function SignalPill({ children }: { children: React.ReactNode }) {
  return (
    <span
      className="inline-flex items-center gap-1.5 rounded-full border px-2 py-px font-mono text-[10px]"
      style={{
        background: 'oklch(0.97 0.025 27)',
        color: 'oklch(0.45 0.18 27)',
        borderColor: 'oklch(0.85 0.08 27)',
      }}
    >
      <span
        className="inline-block h-1 w-1 rounded-full"
        style={{ background: 'oklch(0.62 0.22 27)' }}
      />
      {children}
    </span>
  );
}

function Term({ children }: { children: React.ReactNode }) {
  return (
    <span
      className="cursor-help underline underline-offset-2"
      style={{ color: 'oklch(0.5 0.18 263)' }}
    >
      {children}
    </span>
  );
}

function Mention({ children }: { children: React.ReactNode }) {
  return (
    <span
      className="rounded-[3px] px-1 font-medium"
      style={{ background: 'oklch(0.92 0.04 263)', color: 'oklch(0.4 0.16 263)' }}
    >
      {children}
    </span>
  );
}

// ---------------------------------------------------------------------------
// Card 2 — Running procedure
// ---------------------------------------------------------------------------

interface RunStep {
  icon: React.ReactNode;
  iconBg: string;
  what: string;
  detail: string;
  status: 'done' | 'run' | 'queue';
}

const RUN_STEPS: RunStep[] = [
  {
    icon: <BrandSlack size={12} />,
    iconBg: 'oklch(0.99 0.004 81)',
    what: 'Posted to Slack #merch-ops',
    detail: 'Tagged @Maya · 312-word summary',
    status: 'done',
  },
  {
    icon: <Mail className="h-3 w-3 text-white" strokeWidth={2.2} />,
    iconBg: 'oklch(0.55 0.13 145)',
    what: 'Email · Mira Textiles',
    detail: 'Lead time + 600-unit quote',
    status: 'done',
  },
  {
    icon: <Search className="h-3 w-3 text-white" strokeWidth={2.2} />,
    iconBg: 'oklch(0.5 0.18 263)',
    what: 'Web research · 4 competitors',
    detail: 'Pricing pulled · 1 underprice flagged',
    status: 'run',
  },
  {
    icon: <BrandShopify size={12} />,
    iconBg: 'oklch(0.99 0.004 81)',
    what: 'Drafting pre-order · Shopify',
    detail: 'Saved as draft · awaiting Edwin',
    status: 'queue',
  },
];

function RunCard({ compact }: { compact?: boolean } = {}) {
  return (
    <div className="overflow-hidden rounded-lg border border-border bg-card shadow-md">
      <div
        className="flex items-center gap-2.5 border-b border-border px-3 py-2.5"
        style={{
          background: 'linear-gradient(to bottom, var(--card), var(--background))',
        }}
      >
        <span
          className="inline-block h-2 w-2 shrink-0 animate-pulse rounded-full"
          style={{
            background: 'oklch(0.7 0.12 348)',
            boxShadow: '0 0 0 4px oklch(0.7 0.12 348 / 0.18)',
          }}
        />
        <div className="min-w-0 flex-1">
          <div className="text-[13px] font-medium tracking-tight text-heading">
            Running procedure
          </div>
          <div className="mt-0.5 font-mono text-[9px] tracking-wider text-muted-foreground uppercase">
            Riven · internal execution
          </div>
        </div>
      </div>

      <ol className="font-mono text-[11px]">
        {RUN_STEPS.map((step, i) => (
          <li
            key={i}
            className={cn(
              'grid grid-cols-[20px_1fr_auto] items-start gap-2 px-3 py-2',
              i < RUN_STEPS.length - 1 && 'border-b border-dashed border-border',
              compact && i > 2 && 'hidden sm:grid',
            )}
          >
            <span
              className="flex h-5 w-5 items-center justify-center rounded-[4px]"
              style={{ background: step.iconBg }}
            >
              {step.icon}
            </span>
            <span className="min-w-0">
              <span className="block font-medium text-heading">{step.what}</span>
              <span className="mt-0.5 block text-[9.5px] leading-snug text-muted-foreground">
                {step.detail}
              </span>
            </span>
            <RunStatus status={step.status} />
          </li>
        ))}
      </ol>

      <div className="flex items-center justify-between gap-2 border-t border-border bg-background px-3 py-2 font-mono text-[9.5px] text-muted-foreground">
        <span>Step 3 / 4</span>
        <div className="h-1 max-w-[120px] flex-1 overflow-hidden rounded-full bg-muted">
          <div className="h-full w-3/4" style={{ background: 'oklch(0.7 0.12 348)' }} />
        </div>
        <span className="tabular-nums">~12s left</span>
      </div>
    </div>
  );
}

function RunStatus({ status }: { status: RunStep['status'] }) {
  const cfg = {
    done: { bg: 'oklch(0.95 0.05 145)', fg: 'oklch(0.35 0.1 145)', label: 'Done' },
    run: { bg: 'oklch(0.92 0.04 263)', fg: 'oklch(0.4 0.16 263)', label: 'Running' },
    queue: { bg: 'var(--muted)', fg: 'var(--muted-foreground)', label: 'Queued' },
  }[status];
  return (
    <span
      className="rounded-[3px] px-1.5 py-0.5 text-[8.5px] font-bold tracking-wider uppercase"
      style={{ background: cfg.bg, color: cfg.fg }}
    >
      {cfg.label}
    </span>
  );
}

// ---------------------------------------------------------------------------
// Card 3 — Stored memory
// ---------------------------------------------------------------------------

function MemoryCard({ compact }: { compact?: boolean } = {}) {
  return (
    <div className="overflow-hidden rounded-lg border border-border bg-card shadow-md">
      <div
        className="flex items-center gap-2.5 px-3 py-2.5"
        style={{ background: 'var(--foreground)', color: 'oklch(0.97 0.008 92)' }}
      >
        <span
          className="flex h-6 w-6 shrink-0 items-center justify-center rounded-[5px] font-mono text-[11px] font-bold"
          style={{ background: 'oklch(1 0 0 / 0.1)' }}
        >
          M
        </span>
        <div className="min-w-0 flex-1">
          <div className="truncate text-[12px] font-medium tracking-tight">
            Hero-SKU spike — response
          </div>
          <div
            className="mt-0.5 truncate font-mono text-[9px] tracking-wider uppercase"
            style={{ opacity: 0.6 }}
          >
            New memory · learned from Edwin
          </div>
        </div>
        <span
          className="shrink-0 rounded-full px-2 py-0.5 font-mono text-[8.5px] font-bold tracking-wider text-white"
          style={{ background: 'oklch(0.55 0.13 145)' }}
        >
          SAVED
        </span>
      </div>

      <div className="px-3 py-2.5 text-[11px] leading-snug">
        <MemRow label="Trigger">
          <TriggerPill>demand_spike</TriggerPill>
          <TriggerPill>hero_sku</TriggerPill>
          {!compact && <TriggerPill>36h window</TriggerPill>}
        </MemRow>

        <MemRow label="Scope">
          Applies to <b className="font-semibold text-heading">any SKU</b> matching{' '}
          <b className="font-semibold text-heading">hero_sku</b>.
        </MemRow>

        {!compact && (
          <MemRow label="Approval">
            Edwin must approve <b className="font-semibold text-heading">step 4</b> before it runs.
          </MemRow>
        )}
      </div>

      <div className="flex items-center gap-2 border-t border-border bg-background px-3 py-1.5 font-mono text-[9.5px] text-muted-foreground">
        <span className="rounded-[3px] border border-border bg-card px-1.5 py-px text-heading">
          v1
        </span>
        <span className="ml-auto truncate">runs automatically next time</span>
      </div>
    </div>
  );
}

function MemRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-[60px_1fr] items-baseline gap-2 border-b border-dashed border-border py-1.5 last:border-b-0">
      <span className="font-mono text-[9px] tracking-wider text-muted-foreground uppercase">
        {label}
      </span>
      <span className="flex flex-wrap items-baseline gap-1 text-content">{children}</span>
    </div>
  );
}

function TriggerPill({ children }: { children: React.ReactNode }) {
  return (
    <span className="inline-block rounded-[4px] border border-border bg-muted px-1.5 py-px font-mono text-[9.5px] text-heading">
      {children}
    </span>
  );
}

// ---------------------------------------------------------------------------
// Card 4 — Glossary term
// ---------------------------------------------------------------------------

function GlossaryCard({ compact }: { compact?: boolean } = {}) {
  return (
    <div className="flex h-full flex-col overflow-hidden rounded-lg border border-border bg-card shadow-md">
      <div className="border-b border-dashed border-border px-3 py-2.5">
        <div className="mb-1 flex items-center gap-2 font-mono text-[8.5px] tracking-wider text-muted-foreground uppercase">
          <span
            className="font-serif text-[14px] tracking-tighter italic"
            style={{ color: 'oklch(0.5 0.12 277)', fontFamily: 'var(--font-instrument-serif)' }}
          >
            Aa
          </span>
          New glossary term
        </div>
        <div
          className="text-[20px] leading-none font-normal tracking-tight text-heading"
          style={{ fontFamily: 'var(--font-instrument-serif)' }}
        >
          hero SKU
        </div>
        <div className="mt-1 font-mono text-[9.5px] text-muted-foreground">
          noun · merchandising · <em>defined by Edwin</em>
        </div>
      </div>

      <div className="flex-1 px-3 py-2.5 text-[11px] leading-snug text-content">
        <p className="m-0">
          <span className="mr-1 font-mono text-[9.5px] text-muted-foreground">1.</span>A product in
          the <b className="font-semibold text-heading">top 5% of last-30d revenue</b>
          {compact ? '.' : '. Hero SKUs get prioritized inventory protection.'}
        </p>
        {!compact && (
          <div
            className="mt-2 rounded-r-[4px] px-2.5 py-1.5 font-mono text-[10px] leading-snug text-content"
            style={{
              background: 'oklch(0.99 0.01 348)',
              borderLeft: '2px solid oklch(0.7 0.12 348)',
            }}
          >
            <b className="text-heading">In context:</b> &quot;Sand is one. When demand spikes on a
            hero, do this every time…&quot;
          </div>
        )}
      </div>

      <div className="border-t border-dashed border-border px-3 py-2">
        <div className="mb-1.5 font-mono text-[8.5px] tracking-wider text-muted-foreground uppercase">
          Related · auto-resolved
        </div>
        <div className="flex flex-wrap gap-1">
          <Chip>demand_spike</Chip>
          <Chip>supplier_of_record</Chip>
          {!compact && <Chip>price_band</Chip>}
        </div>
      </div>

      <div className="flex items-center gap-1.5 border-t border-border bg-background px-3 py-1.5 font-mono text-[9.5px] text-muted-foreground">
        <span
          className="inline-block h-1.5 w-1.5 rounded-full"
          style={{ background: 'oklch(0.55 0.13 145)' }}
        />
        <span className="truncate">12 SKUs match</span>
      </div>
    </div>
  );
}

function Chip({ children }: { children: React.ReactNode }) {
  return (
    <span className="rounded-full border border-border bg-background px-2 py-px font-mono text-[9.5px] text-heading">
      {children}
    </span>
  );
}

// ---------------------------------------------------------------------------
// Shared
// ---------------------------------------------------------------------------

function CardRibbon({ left, right }: { left: string; right: string }) {
  return (
    <div className="flex items-center gap-2 border-b border-border bg-background px-3 py-2">
      <span className="font-mono text-[9px] tracking-wider text-muted-foreground uppercase">
        {left}
      </span>
      <span className="ml-auto font-mono text-[10px] text-muted-foreground">{right}</span>
    </div>
  );
}
