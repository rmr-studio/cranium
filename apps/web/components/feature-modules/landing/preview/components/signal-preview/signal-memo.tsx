import { cn } from '@/lib/utils';
import { Logo } from '@riven/ui/logo';
import { ClassNameProps } from '@riven/utils';
import type { FC, ReactNode } from 'react';
import { AppGlyph, ChatLink, Icon, icons, UserAvatar } from './primitives';

interface Props extends ClassNameProps {
  compact?: boolean;
}

export const SignalMemo: FC<Props> = ({ className, compact }) => {
  return (
    <div
      className={cn(
        'flex w-[16rem] shrink-0 flex-col border-l border-border bg-sidebar',
        className,
      )}
    >
      <MemoHeader />
      <div className="relative flex-1 overflow-hidden px-3.5 pt-3.5 pb-7">
        {!compact && <PrimaryEntityCard />}
        <RelatedEntities />
        <IntegrationData />
        {!compact && <MeetingNote />}
        <ActionItems />
        <StaticScrollbar thumbTop="6%" thumbHeight="48%" />
      </div>
    </div>
  );
};

function StaticScrollbar({
  thumbTop,
  thumbHeight,
}: {
  thumbTop: string;
  thumbHeight: string;
}) {
  return (
    <div
      aria-hidden
      className="pointer-events-none absolute top-0 right-0 bottom-0 w-3 border-l border-border bg-card/60"
    >
      <div
        className="absolute right-0.5 left-0.5 rounded-full bg-muted-foreground/30"
        style={{ top: thumbTop, height: thumbHeight }}
      />
    </div>
  );
}

function MemoHeader() {
  return (
    <div className="flex items-center gap-2 border-b border-border px-3.5 py-3">
      <Logo size={20} />
      <div className="min-w-0 flex-1">
        <div className="font-mono text-[9px] font-bold tracking-[0.06em] text-muted-foreground uppercase">
          Riven · context
        </div>
        <div className="truncate text-[13px] font-semibold tracking-tight text-heading">
          SIG-2041 · Demand spike
        </div>
      </div>
      <span className="flex size-7 items-center justify-center rounded-md text-muted-foreground">
        <Icon size={13}>{icons.external}</Icon>
      </span>
      <span className="flex size-7 items-center justify-center rounded-md text-muted-foreground">
        <Icon size={13}>{icons.close}</Icon>
      </span>
    </div>
  );
}

function Section({
  label,
  count,
  children,
}: {
  label: string;
  count?: number;
  children: ReactNode;
}) {
  return (
    <div className="mb-4">
      <div className="mb-2 font-mono text-[10px] font-bold tracking-[0.08em] text-muted-foreground uppercase">
        {label}
        {count != null && <span className="ml-1.5 text-muted-foreground">· {count}</span>}
      </div>
      {children}
    </div>
  );
}

function PrimaryEntityCard() {
  return (
    <Section label="Primary entity">
      <div className="relative overflow-hidden rounded-lg border border-border bg-card p-3.5">
        <div
          className="absolute inset-y-0 left-0 w-[3px]"
          style={{ background: 'oklch(0.85 0.05 70)' }}
        />
        <div className="mb-2.5 flex items-center gap-2.5">
          <span
            className="flex size-9 items-center justify-center rounded-md font-mono text-[11px] font-bold"
            style={{ background: 'oklch(0.92 0.03 70)', color: 'oklch(0.35 0.08 70)' }}
          >
            LFT
          </span>
          <div className="min-w-0 flex-1">
            <div className="text-[13px] font-semibold tracking-tight text-heading">
              Linen Field Tee · Sand
            </div>
            <div className="font-mono text-[10px] text-muted-foreground">
              SKU LFT-S-SND · Product entity
            </div>
          </div>
        </div>
        <div className="grid grid-cols-3 gap-2">
          <Mini label="In stock" value="84" />
          <Mini label="On order" value="0" />
          <Mini label="Velocity" value="20/d" hot />
        </div>
      </div>
    </Section>
  );
}

function Mini({ label, value, hot }: { label: string; value: string; hot?: boolean }) {
  return (
    <div className="rounded-md border border-border bg-background px-2 py-1.5">
      <div className="font-mono text-[9px] tracking-[0.06em] text-muted-foreground uppercase">
        {label}
      </div>
      <div
        className={cn(
          'mt-0.5 font-display text-[16px] leading-tight tabular-nums',
          hot ? '' : 'text-heading',
        )}
        style={hot ? { color: 'oklch(0.55 0.18 27)' } : undefined}
      >
        {value}
      </div>
    </div>
  );
}

function RelatedEntities() {
  const items = [
    {
      label: 'Mira Textiles',
      sub: 'Supplier · 32d lead time',
      initials: 'MT',
      color: 'oklch(0.72 0.1 200)',
    },
    {
      label: 'SS26 · Field Series',
      sub: 'Collection · 12 SKUs',
      initials: 'FS',
      color: 'oklch(0.7 0.12 145)',
    },
    {
      label: 'Low-cover waitlist',
      sub: 'Segment · 1,284 contacts',
      initials: 'LC',
      color: 'oklch(0.7 0.12 348)',
    },
    {
      label: 'TT — Linen drop',
      sub: 'Campaign · live · TikTok',
      initials: 'TT',
      color: 'oklch(0.6 0.18 27)',
    },
  ];
  return (
    <Section label="Related entities" count={items.length}>
      <div className="flex flex-col">
        {items.map((d) => (
          <div key={d.label} className="flex items-center gap-2.5 rounded-md px-2 py-1.5">
            <span
              className="flex size-6 shrink-0 items-center justify-center rounded-md font-mono text-[9px] font-bold text-white"
              style={{ background: d.color }}
            >
              {d.initials}
            </span>
            <span className="min-w-0 flex-1">
              <span className="block truncate text-[12px] font-medium text-heading">{d.label}</span>
              <span className="block font-mono text-[10px] text-muted-foreground">{d.sub}</span>
            </span>
            <Icon size={11}>{icons.chevron}</Icon>
          </div>
        ))}
      </div>
    </Section>
  );
}

function IntegrationData() {
  const rows = [
    { app: 'Shopify', detail: 'Sales orders · 14d', value: '+241%', sub: 'sessions/PDP/SKU' },
    { app: 'Intercom', detail: 'add_to_cart events', value: '+198%', sub: '7d vs 28d baseline' },
    { app: 'Klaviyo', detail: '“Notify me” subscriptions', value: '540 new', sub: 'past 36h' },
    { app: 'Gorgias', detail: '“when restock” tickets', value: '12 open', sub: 'last 7d' },
    { app: 'Stripe', detail: 'AOV on this SKU', value: '$54.98', sub: '+$3.10 vs avg' },
  ];
  return (
    <Section label="Integration data" count={rows.length}>
      <div className="overflow-hidden rounded-md border border-border bg-card">
        {rows.map((r, i) => (
          <div
            key={r.app}
            className={cn('flex items-center gap-2.5 px-2.5 py-2', i && 'border-t border-border')}
          >
            <AppGlyph app={r.app} size={18} />
            <div className="min-w-0 flex-1">
              <div className="truncate text-[12px] font-medium text-heading">
                <span className="text-muted-foreground">{r.app} · </span>
                {r.detail}
              </div>
              <div className="font-mono text-[10px] text-muted-foreground">{r.sub}</div>
            </div>
            <span className="font-mono text-[11px] font-bold text-heading tabular-nums">
              {r.value}
            </span>
          </div>
        ))}
      </div>
    </Section>
  );
}

function MeetingNote() {
  return (
    <Section label="Meeting note" count={1}>
      <div className="rounded-lg border border-border bg-card p-3">
        <div className="mb-2 flex items-center gap-1.5">
          <Icon size={11}>{icons.calendar}</Icon>
          <span className="text-[11px] font-semibold tracking-tight text-heading">
            Weekly ops · Mar 18
          </span>
          <span className="flex-1" />
          <span className="font-mono text-[9px] text-muted-foreground">5 attendees</span>
        </div>
        <div
          className="pl-2 font-mono text-[11px] leading-relaxed text-content"
          style={{ borderLeft: '2px solid oklch(0.85 0.05 70)' }}
        >
          <span className="text-muted-foreground">{'// agreed'}</span>
          <br />
          <span className="rounded-[2px] px-1" style={{ background: 'oklch(0.96 0.04 70)' }}>
            Any SKU that crosses the demand-spike threshold goes on pre-order before the next 48h.
          </span>{' '}
          Volume = 90% of last reorder × spike multiplier. Maya owns customer comms.
        </div>
        <div className="mt-2 flex items-center gap-1.5">
          {['Jared Tucker', 'Maya Okafor', 'Jordan Tan', 'George Lin', 'Terry Park'].map((n) => (
            <UserAvatar key={n} name={n} size={16} />
          ))}
          <span className="flex-1" />
          <ChatLink>Open transcript</ChatLink>
        </div>
      </div>
    </Section>
  );
}

function ActionItems() {
  const items: { text: string; owner: string; auto?: boolean }[] = [
    { text: 'Open Shopify pre-order page · ships May 28', owner: 'Riven', auto: true },
    { text: 'Draft PO to Mira Textiles · 600u · NET-30', owner: 'Riven', auto: true },
    { text: 'Notify low-cover Klaviyo segment', owner: 'Maya' },
    { text: 'Confirm 32d lead time with supplier', owner: 'Jared' },
  ];
  return (
    <Section label="Action items" count={items.length}>
      <div className="flex flex-col gap-1">
        {items.map((it) => (
          <div
            key={it.text}
            className="flex gap-2.5 rounded-md border border-border bg-card px-2.5 py-2"
          >
            <span className="mt-0.5 flex size-4 shrink-0 items-center justify-center rounded-sm border-[1.5px] border-border" />
            <div className="min-w-0 flex-1">
              <div className="text-[12px] leading-snug text-content">{it.text}</div>
              <div className="mt-1 flex items-center gap-1.5">
                <span className="font-mono text-[10px] text-muted-foreground">{it.owner}</span>
                {it.auto && (
                  <>
                    <span className="size-0.5 rounded-full bg-border" />
                    <span
                      className="rounded-[3px] px-1 font-mono text-[9px] font-bold tracking-[0.06em]"
                      style={{
                        background: 'oklch(0.92 0.04 263)',
                        color: 'oklch(0.4 0.16 263)',
                      }}
                    >
                      AUTO
                    </span>
                  </>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
    </Section>
  );
}
