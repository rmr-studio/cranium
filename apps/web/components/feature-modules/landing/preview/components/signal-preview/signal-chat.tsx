import { cn } from '@/lib/utils';
import { Logo } from '@riven/ui/logo';
import type { ReactNode } from 'react';
import {
  ChatLink,
  EntityChip,
  Icon,
  SeverityDot,
  SystemLabel,
  UserAvatar,
  icons,
} from './primitives';

export function SignalChat({ density = 'comfortable' }: { density?: 'comfortable' | 'compact' }) {
  return (
    <div className="flex min-w-0 flex-1 flex-col bg-background">
      <ChatHeader />
      <div
        className={cn('flex-1 overflow-hidden', density === 'compact' ? 'px-4 py-4' : 'px-6 py-5')}
      >
        <Thread />
      </div>
      <Composer />
    </div>
  );
}

function ChatHeader() {
  return (
    <div className="flex h-[3.25rem] items-center gap-2.5 border-b border-border bg-background px-4">
      <HeaderBtn>
        <Icon size={13}>{icons.arrowL}</Icon>
      </HeaderBtn>
      <HeaderBtn>
        <Icon size={13}>{icons.arrowR}</Icon>
      </HeaderBtn>
      <HeaderBtn>
        <Icon size={13}>{icons.clock}</Icon>
      </HeaderBtn>
      <div className="ml-1.5 flex min-w-0 items-center gap-2">
        <SeverityDot severity="high" pulse />
        <span className="truncate font-display text-sm tracking-tight text-heading">
          Demand spike · Linen Field Tee
        </span>
        <span className="hidden items-center gap-1 rounded-sm border border-[oklch(0.88_0.06_27)] bg-[oklch(0.97_0.025_27)] px-1.5 py-px text-[10px] font-medium text-[oklch(0.45_0.18_27)] sm:inline-flex">
          <span className="size-1.5 rounded-full bg-[oklch(0.62_0.22_27)]" />
          High
        </span>
        <SystemLabel className="hidden sm:inline">SIG-2041</SystemLabel>
      </div>
      <span className="flex-1" />
      <div className="hidden items-center md:flex">
        {['Jordan Tan', 'Edwin Reyes', 'Maya Okafor'].map((n, i) => (
          <span
            key={n}
            className="border-2 border-background"
            style={{ marginLeft: i ? -6 : 0, borderRadius: 9999, display: 'inline-flex' }}
          >
            <UserAvatar name={n} size={20} />
          </span>
        ))}
      </div>
      <span className="hidden h-5 w-px bg-border sm:block" />
      <button className="hidden items-center gap-1 rounded-sm border border-border bg-card px-2 py-1 text-[11px] font-medium text-heading shadow-sm sm:inline-flex">
        <Icon size={10}>{icons.bookmark}</Icon>
        Pin
      </button>
      <HeaderBtn>
        <Icon size={13}>{icons.dots}</Icon>
      </HeaderBtn>
    </div>
  );
}

function HeaderBtn({ children }: { children: ReactNode }) {
  return (
    <span className="flex size-7 items-center justify-center rounded-md text-muted-foreground">
      {children}
    </span>
  );
}

function Thread() {
  return (
    <div className="flex max-w-[44rem] flex-col gap-5">
      <DayDivider label="Today · 09:14" />

      <Message author="Riven" time="09:14" appBadge>
        <div className="mb-2.5 leading-relaxed">
          Heads up — I&apos;m raising a{' '}
          <strong className="text-heading">demand spike signal</strong> on{' '}
          <EntityChip>Linen Field Tee · Sand</EntityChip>. Demand intent and sell-through have both
          crossed your threshold in the last 36 hours.
        </div>

        <SignalCard />

        <div className="mt-3 mb-2 leading-relaxed">
          <strong className="text-heading">Why this matters.</strong> The last time this SKU tripped
          both thresholds was <ChatLink>Feb 03 → Feb 09</ChatLink>. We were out of stock for 4 days
          and the inventory log shows{' '}
          <strong className="text-destructive">161 missed orders</strong> (~$8,855 GMV) before the
          next replenishment landed.
        </div>

        <MissedOpportunityCallout />

        <div className="mt-3 leading-relaxed">
          In the <ChatLink>Mar 18 ops weekly</ChatLink>, the team agreed:{' '}
          <em>
            &ldquo;any SKU that crosses the demand threshold goes on pre-order before the next
            48h.&rdquo;
          </em>{' '}
          I&apos;ve drafted the pre-order config based on that rule and historical cover.
        </div>

        <ProposedAction />
      </Message>

      <Message author="Edwin Reyes" time="09:18">
        How sure are we on the demand intent number? Last time the spike was driven by a single
        TikTok and it cooled off in 3 days.
      </Message>

      <Message author="Riven" time="09:18" appBadge>
        <div className="mb-2.5 leading-relaxed">
          Different shape this time. Confidence <strong className="text-heading">0.84</strong> — the
          spike is distributed across 6 channels rather than one viral source. Sell-through is also
          moving in lockstep with intent (corr 0.71 over 14d).{' '}
          <ChatLink>See full reasoning →</ChatLink>
        </div>
      </Message>
    </div>
  );
}

function DayDivider({ label }: { label: string }) {
  return (
    <div className="my-1 flex items-center gap-3">
      <div className="h-px flex-1 bg-border" />
      <span className="rounded-full border border-border bg-card px-2.5 py-0.5 font-mono text-[10px] font-bold tracking-[0.08em] text-muted-foreground uppercase">
        {label}
      </span>
      <div className="h-px flex-1 bg-border" />
    </div>
  );
}

function Message({
  author,
  time,
  appBadge,
  children,
}: {
  author: string;
  time: string;
  appBadge?: boolean;
  children: ReactNode;
}) {
  const isRiven = author === 'Riven';
  return (
    <div className="flex gap-3 text-[13px]">
      {isRiven ? <Logo size={32} /> : <UserAvatar name={author} size={32} square />}
      <div className="min-w-0 flex-1">
        <div className="mb-1 flex items-baseline gap-2">
          <span className="text-sm font-semibold tracking-tight text-heading">{author}</span>
          {appBadge && (
            <span className="rounded-sm bg-foreground px-1 py-px font-mono text-[9px] font-bold tracking-[0.06em] text-[oklch(0.97_0.008_92)]">
              APP
            </span>
          )}
          <span className="font-mono text-[10px] text-muted-foreground">{time}</span>
        </div>
        <div className="text-content">{children}</div>
      </div>
    </div>
  );
}

function SignalCard() {
  return (
    <div className="mt-1 overflow-hidden rounded-lg border border-border bg-card shadow-sm">
      <div className="flex items-center gap-2 border-b border-border px-3.5 py-2.5">
        <Icon size={12}>{icons.flow}</Icon>
        <SystemLabel tone="heading" className="flex-1">
          Signal · demand_spike
        </SystemLabel>
        <span className="font-mono text-[10px] text-muted-foreground">↑ 36h window</span>
      </div>
      <div className="grid grid-cols-3 border-b border-border">
        <Metric label="Demand intent" value="3.4×" delta="+241%" sub="vs 14-day baseline" />
        <Metric label="Sell-through" value="62%" delta="+48 pp" sub="last 7 days" border />
        <Metric label="Cover (days)" value="4.2" delta="−11.8" sub="at current velocity" />
      </div>
      <div className="flex items-center gap-2.5 px-3.5 py-2.5 text-[11px] text-muted-foreground">
        <Sparkline />
        <span className="flex-1 font-mono">14d intent · sell-through</span>
        <span className="font-mono text-heading tabular-nums">14 → 18 → 31 → 47</span>
      </div>
    </div>
  );
}

function Metric({
  label,
  value,
  delta,
  sub,
  border,
}: {
  label: string;
  value: string;
  delta: string;
  sub: string;
  border?: boolean;
}) {
  return (
    <div className={cn('px-3.5 py-2.5', border && 'border-x border-border')}>
      <div className="mb-1.5 font-mono text-[10px] tracking-[0.06em] text-muted-foreground uppercase">
        {label}
      </div>
      <div className="flex items-baseline gap-1.5">
        <span className="font-display text-[22px] leading-none text-heading tabular-nums">
          {value}
        </span>
        <span className="font-mono text-[11px] font-bold" style={{ color: 'oklch(0.55 0.18 27)' }}>
          {delta}
        </span>
      </div>
      <div className="mt-1 font-mono text-[10px] text-muted-foreground">{sub}</div>
    </div>
  );
}

function Sparkline() {
  const pts = [12, 14, 13, 15, 14, 16, 18, 17, 22, 28, 31, 39, 47, 52];
  const max = 52;
  const min = 12;
  const w = 90;
  const h = 22;
  const path = pts
    .map((v, i) => {
      const x = (i / (pts.length - 1)) * w;
      const y = h - ((v - min) / (max - min)) * h;
      return `${i === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`;
    })
    .join(' ');
  return (
    <svg width={w} height={h} className="shrink-0">
      <path d={path} stroke="oklch(0.55 0.18 27)" strokeWidth="1.5" fill="none" />
      <circle cx={w} cy={h - ((52 - min) / (max - min)) * h} r="2.5" fill="oklch(0.55 0.18 27)" />
    </svg>
  );
}

function MissedOpportunityCallout() {
  const stats: [string, string][] = [
    ['161', 'missed orders'],
    ['$8,855', 'missed GMV'],
    ['4 days', 'OOS duration'],
    ['34%', 'conversion drop'],
  ];
  return (
    <div
      className="mt-2.5 rounded-lg border px-3.5 py-3"
      style={{
        background: 'oklch(0.97 0.025 27)',
        borderColor: 'oklch(0.88 0.06 27)',
        borderLeftWidth: 3,
        borderLeftColor: 'oklch(0.62 0.22 27)',
      }}
    >
      <div className="mb-2 flex items-center gap-2">
        <SystemLabel tone="destructive">Historical · stock-out</SystemLabel>
        <span className="font-mono text-[10px] text-muted-foreground">FEB 03 — FEB 09</span>
      </div>
      <div className="grid grid-cols-4 gap-3.5">
        {stats.map(([n, l]) => (
          <div key={l}>
            <div className="font-display text-[18px] leading-none tracking-tight text-heading tabular-nums">
              {n}
            </div>
            <div className="mt-1 font-mono text-[10px] text-muted-foreground">{l}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

function ProposedAction() {
  return (
    <div className="mt-3 overflow-hidden rounded-lg border border-border bg-card shadow-sm">
      <div className="flex items-center gap-2 border-b border-border px-3.5 py-2.5">
        <Icon size={12}>{icons.sparkle}</Icon>
        <SystemLabel tone="heading" className="flex-1">
          Proposed action
        </SystemLabel>
        <span className="font-mono text-[10px] text-muted-foreground">
          derives from <ChatLink>weekly-ops/2026-03-18</ChatLink>
        </span>
      </div>
      <div className="px-3.5 py-3">
        <div className="mb-1.5 font-display text-[16px] tracking-tight text-heading">
          Open pre-order for 600 units
        </div>
        <div className="mb-3 text-xs leading-relaxed text-content">
          Cover the 32-day supplier lead-time gap based on projected velocity. Pre-order page goes
          live to inbound traffic; PO drafted to{' '}
          <strong className="text-heading">Mira Textiles</strong>.
        </div>

        <div className="mb-3 grid grid-cols-[auto_1fr] gap-x-3.5 gap-y-1.5 font-mono text-[11px]">
          <span className="text-muted-foreground">Volume</span>
          <span className="text-heading">600 units (90% of last reorder × spike multiplier)</span>
          <span className="text-muted-foreground">Pre-order page</span>
          <span className="text-heading">Shopify · ships May 28</span>
          <span className="text-muted-foreground">PO draft</span>
          <span className="text-heading">Mira Textiles · $4,860 · NET-30</span>
          <span className="text-muted-foreground">Customer email</span>
          <span className="text-heading">
            Klaviyo · &ldquo;back in stock&rdquo; → low-cover segment
          </span>
        </div>

        <div className="flex items-center gap-2">
          <button className="flex items-center gap-1.5 rounded-sm bg-foreground px-2.5 py-1.5 text-[12px] font-medium text-[oklch(0.97_0.008_92)]">
            <Icon size={10}>{icons.check}</Icon>
            Approve &amp; run
          </button>
          <button className="rounded-sm border border-border bg-card px-2.5 py-1.5 text-[12px] font-medium text-heading shadow-sm">
            Edit volume
          </button>
          <button className="rounded-sm px-2.5 py-1.5 text-[12px] font-medium text-muted-foreground">
            Snooze 24h
          </button>
          <span className="flex-1" />
          <span className="font-mono text-[10px] text-muted-foreground">
            1 of 4 steps will auto-execute
          </span>
        </div>
      </div>
    </div>
  );
}

function Composer() {
  return (
    <div className="px-4 pt-1.5 pb-4">
      <div className="overflow-hidden rounded-lg border border-border bg-card shadow-sm">
        <div className="flex items-center gap-px border-b border-border px-2.5 py-1.5">
          {(['bold', 'italic', 'list', 'link'] as const).map((k) => (
            <span
              key={k}
              className="flex h-6 w-7 items-center justify-center rounded-sm text-muted-foreground"
            >
              <Icon size={12}>{icons[k]}</Icon>
            </span>
          ))}
        </div>
        <div className="min-h-10 px-3.5 py-3 text-[13px] tracking-tight text-muted-foreground">
          Reply to signal · use{' '}
          <span className="rounded-sm bg-muted px-1.5 py-px font-mono text-[11px]">/run</span> to
          execute proposed action
        </div>
        <div className="flex items-center gap-1 border-t border-border px-2.5 py-1.5">
          {(['plus', 'paperclip', 'at', 'smile'] as const).map((k) => (
            <span
              key={k}
              className="flex h-6 w-7 items-center justify-center rounded-sm text-muted-foreground"
            >
              <Icon size={13}>{icons[k]}</Icon>
            </span>
          ))}
          <span className="flex-1" />
          <span className="flex h-6 w-7 items-center justify-center rounded-sm text-muted-foreground">
            <Icon size={13}>{icons.sparkle}</Icon>
          </span>
          <button className="flex items-center gap-1.5 rounded-sm bg-foreground px-2.5 py-1.5 text-[12px] font-medium text-[oklch(0.97_0.008_92)]">
            <Icon size={10}>{icons.send}</Icon>
            Send
          </button>
        </div>
      </div>
    </div>
  );
}
