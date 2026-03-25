import { cn } from '@/lib/utils';
import { ArrowUp, CogIcon, Download, LayoutGrid, Search, Sparkles } from 'lucide-react';

import { ShowcaseIconRail } from '@/components/ui/diagrams/brand-ui-primitives';
import { ClassNameProps } from '@riven/utils';
import { FC } from 'react';

const DASHBOARD_NAV_ICONS = [
  { icon: <LayoutGrid className="size-5" />, active: false },
  { icon: <LayoutGrid className="size-5" />, active: true },
  { icon: <Search className="size-5" />, active: false },
  { icon: <CogIcon className="size-5" />, active: false },
];

// ── Insight Card Shell ──────────────────────────────────────────────

function InsightCard({
  label,
  labelColor,
  timestamp,
  accentBorder,
  children,
  actions,
}: {
  label: string;
  labelColor?: string;
  timestamp?: string;
  accentBorder?: string;
  children: React.ReactNode;
  actions: React.ReactNode;
}) {
  return (
    <div
      className={cn(
        'flex flex-col justify-between rounded-lg border border-border bg-card p-5',
        accentBorder,
      )}
      style={{ minHeight: 200 }}
    >
      {/* Header */}
      <div>
        <div className="flex items-center justify-between">
          <span
            className={cn(
              'font-display text-xs tracking-[0.05em] uppercase',
              labelColor ?? 'text-muted-foreground/50',
            )}
          >
            {label}
          </span>
          {timestamp && (
            <span className="font-display text-xs tracking-[0.05em] text-muted-foreground/30 uppercase">
              {timestamp}
            </span>
          )}
        </div>

        {/* Body */}
        <div className="mt-4">{children}</div>
      </div>

      {/* Actions */}
      <div className="mt-6">{actions}</div>
    </div>
  );
}

function CardAction({ label, muted }: { label: string; muted?: boolean }) {
  return (
    <span
      className={cn(
        'font-display text-xs tracking-[0.05em] uppercase',
        muted ? 'text-muted-foreground/30' : 'text-foreground/70',
      )}
    >
      {label}
    </span>
  );
}

// ── Bar Chart (simple decorative) ───────────────────────────────────

function MiniBarChart() {
  const bars = [85, 72, 55, 40, 35, 28];
  return (
    <div className="flex items-end gap-2.5" style={{ height: 80 }}>
      {bars.map((h, i) => (
        <div
          key={i}
          className="w-12 rounded-sm bg-foreground/80 dark:bg-foreground/60"
          style={{
            height: `${h}%`,
            opacity: i < 2 ? 1 : i < 4 ? 0.5 : 0.25,
          }}
        />
      ))}
    </div>
  );
}

// ── Quick Action Pills ──────────────────────────────────────────────

function ActionPill({ label, dark }: { label: string; dark?: boolean }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-4 py-1.5 text-xs',
        dark
          ? 'border-foreground bg-foreground text-background'
          : 'border-border text-foreground/70',
      )}
    >
      {dark && <Download className="size-3" />}
      {label}
    </span>
  );
}

// ── Main Dashboard Component ────────────────────────────────────────

export function MockDashboard() {
  return (
    <div
      className="z-20 flex overflow-hidden rounded-xl border-border bg-card shadow-lg dark:border"
      style={{ height: 1200 }}
    >
      <ShowcaseIconRail icons={DASHBOARD_NAV_ICONS} />
      <DashboardContent />
    </div>
  );
}

export const DashboardContent: FC<ClassNameProps> = ({ className }) => {
  return (
    <div
      className={cn(
        'paper-lite relative flex flex-1 flex-col overflow-hidden bg-background',
        className,
      )}
    >
      {/* Gradient accent line */}
      <div
        className="h-px w-full"
        style={{
          background:
            'linear-gradient(90deg, var(--cta-g1), var(--cta-g2), var(--cta-g3), transparent)',
        }}
      />

      <div className="px-12 pt-10 pb-16">
        {/* Greeting */}
        <h2 className="font-serif text-5xl font-normal -tracking-[0.02em] text-foreground">
          Morning Jared
        </h2>
        <p className="mt-2 text-sm" style={{ color: 'oklch(0.65 0.17 145)' }}>
          here&apos;s your customer lifecycle at a glance.
        </p>

        {/* Card Grid */}
        <div className="mt-8 grid grid-cols-3 gap-4">
          {/* Card 1: Lifecycle Summary */}
          <InsightCard
            label="Lifecycle Summary"
            timestamp="Just now"
            actions={
              <div className="flex items-center gap-6">
                <CardAction label="View channel breakdown" />
                <CardAction label="Dismiss" muted />
              </div>
            }
          >
            <p className="text-sm leading-relaxed text-foreground/80">
              312 customers acquired this quarter across 5 channels. Instagram volume up{' '}
              <span className="font-semibold" style={{ color: 'oklch(0.65 0.17 145)' }}>
                34%
              </span>
              , but churn rate is{' '}
              <span className="font-semibold" style={{ color: 'oklch(0.577 0.245 27.325)' }}>
                2.1x
              </span>{' '}
              the average. 3 at-risk segments flagged.
            </p>
          </InsightCard>

          {/* Card 2: Avg Customer LTV */}
          <InsightCard label="Avg Customer LTV" actions={<CardAction label="See by channel" />}>
            <p className="text-sm text-foreground/80">
              Your average lifetime value across all channels is{' '}
              <strong className="text-foreground">$538</strong>
            </p>
            <div className="mt-4">
              <MiniBarChart />
            </div>
          </InsightCard>

          {/* Card 3: Channel Performance */}
          <InsightCard
            label="Channel Performance"
            actions={<CardAction label="See all channels" />}
          >
            <p className="text-sm leading-relaxed text-foreground/80">
              Best retention channel is{' '}
              <strong className="text-foreground">Organic / Direct</strong> at{' '}
              <span className="font-semibold" style={{ color: 'oklch(0.65 0.17 145)' }}>
                95%
              </span>{' '}
              90-day retention across two verticals
            </p>
          </InsightCard>

          {/* Card 4: Churn Rate & Trend */}
          <InsightCard
            label="Churn Rate & Trend"
            actions={<CardAction label="See churn cohorts" />}
          >
            <p className="text-sm text-foreground/80">
              Your current churn rate is 9.4% &amp; the 90-day trend is
            </p>
            <p
              className="mt-3 font-serif text-4xl font-normal italic"
              style={{ color: 'oklch(0.65 0.17 145)' }}
            >
              Improving
            </p>
          </InsightCard>

          {/* Card 5: Lifecycle Coverage */}
          <InsightCard label="Lifecycle Coverage" actions={<CardAction label="Connect a tool" />}>
            <p className="text-sm leading-relaxed text-foreground/80">
              5 connected domains, automatically resolving across acquisition, support, revenue,
              onboarding, engagement
            </p>
            <div className="mt-3 rounded-md border border-border bg-muted/30 px-4 py-2.5">
              <span className="text-sm text-muted-foreground/40">
                Product Usage — not connected
              </span>
            </div>
          </InsightCard>

          {/* Card 6: At-Risk Customers */}
          <InsightCard
            label="At-Risk Customers"
            labelColor="text-destructive"
            actions={<CardAction label="View at-risk segment" />}
          >
            <p className="text-sm leading-relaxed text-foreground/80">
              You currently have <strong className="text-foreground">47 at-risk</strong> and{' '}
              <strong style={{ color: 'oklch(0.577 0.245 27.325)' }}>$34,200 ARR</strong> exposed in
              declining-engagement customers
            </p>
          </InsightCard>
        </div>

        {/* Quick Actions */}
        <div className="mt-8 flex flex-wrap items-center justify-center gap-2">
          <ActionPill label="Channel Performance" />
          <ActionPill label="Cohort Overview" />
          <ActionPill label="Support Correlation" />
          <ActionPill label="At-Risk Segment" />
          <ActionPill label="Churn Retrospective" />
          <ActionPill label="Export" dark />
        </div>
        <DashboardPromptInput />
      </div>
    </div>
  );
};

export const DashboardPromptInput: FC<ClassNameProps> = ({ className }) => {
  return (
    <div className={cn('mx-auto mt-10 max-w-2xl', className)}>
      {/* Autocomplete suggestions — floating above input */}
      <div className="rounded-t-xl border border-b-0 border-border bg-card px-2 py-2 shadow-sm">
        {[
          {
            prefix: 'I want to',
            rest: ' know which channels produce customers who actually stay',
          },
          {
            prefix: 'I want to',
            rest: ' see why Instagram customers are churning more than Google',
          },
          {
            prefix: 'I want to',
            rest: ' trace what happened before my last 10 cancellations',
          },
        ].map((suggestion, i) => (
          <div
            key={i}
            className="rounded-lg px-3 py-2.5 text-sm text-muted-foreground/50 transition-colors hover:bg-muted/40"
          >
            <span className="font-medium text-foreground/70">{suggestion.prefix}</span>
            {suggestion.rest}
          </div>
        ))}
      </div>

      {/* Chat input */}
      <div className="flex items-center gap-3 rounded-b-xl border border-border bg-card px-4 py-3 shadow-sm">
        <span className="flex-1 text-sm text-foreground/70">I want to</span>
        <div className="flex items-center gap-2">
          <Sparkles className="size-4 text-muted-foreground/30" />
          <button className="flex size-8 items-center justify-center rounded-lg border border-border bg-foreground text-background">
            <ArrowUp className="size-4" />
          </button>
        </div>
      </div>
    </div>
  );
};
