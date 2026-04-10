import { cn } from '@/lib/utils';
import { ArrowUp, CogIcon, Download, LayoutGrid, Search, Sparkles } from 'lucide-react';

import { ShowcaseIconRail } from '@/components/ui/diagrams/brand-ui-primitives';
import { useContainerScale } from '@/hooks/use-container-scale';
import { ChildNodeProps, ClassNameProps } from '@riven/utils';
import { FC } from 'react';
import { useIsMobile } from '../../../../../../../packages/hooks/src/use-mobile';

const DASHBOARD_NAV_ICONS = [
  { icon: <LayoutGrid className="size-5" />, active: false },
  { icon: <LayoutGrid className="size-5" />, active: true },
  { icon: <Search className="size-5" />, active: false },
  { icon: <CogIcon className="size-5" />, active: false },
];

interface Props {
  label: string;
  labelColor?: string;
  timestamp?: string;
  accentBorder?: string;
  children: React.ReactNode;
  actions: React.ReactNode;
  minHeight?: number;
}
// ── Insight Card Shell ──────────────────────────────────────────────

const InsightCard: FC<Props> = ({
  label,
  labelColor,
  timestamp,
  accentBorder,
  minHeight = 120,
  children,
  actions,
}) => {
  return (
    <div
      className={cn(
        'flex flex-col justify-between rounded-sm border border-neutral-800 p-4 backdrop-blur-2xl',
        accentBorder,
      )}
      style={{ minHeight }}
    >
      {/* Header */}
      <div>
        <div className="flex items-center justify-between">
          <span className={cn('font-serif text-xs tracking-[0.05em] text-white uppercase')}>
            {label}
          </span>
          {timestamp && (
            <span className="font-display text-xs tracking-[0.05em] text-white/80 uppercase">
              {timestamp}
            </span>
          )}
        </div>

        {/* Body */}
        <div className="mt-2">{children}</div>
      </div>

      {/* Actions */}
      <div className="mt-2">{actions}</div>
    </div>
  );
};

function CardAction({ label, muted }: { label: string; muted?: boolean }) {
  return (
    <span
      className={cn(
        'font-display text-xs tracking-[0.05em] uppercase',
        muted ? 'text-neutral-500' : 'text-foreground/70',
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
          className="w-6 rounded-sm bg-white/80"
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

const ActionPill: React.FC<ChildNodeProps> = ({ children }) => {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-md border border-neutral-800 px-4 py-1.5 text-xs text-neutral-300 backdrop-blur-2xl',
      )}
    >
      {children}
    </span>
  );
};

// ── Main Dashboard Component ────────────────────────────────────────

export const MockDashboard: FC<ClassNameProps> = ({ className }) => {
  const DESKTOP_WIDTH = 1720;
  const DESKTOP_HEIGHT = 1050;
  const MOBILE_WIDTH = 1000;
  const MOBILE_HEIGHT = Math.round(DESKTOP_HEIGHT * (MOBILE_WIDTH / DESKTOP_WIDTH));
  const isMobile = useIsMobile();

  const height = isMobile ? MOBILE_HEIGHT : DESKTOP_HEIGHT;
  const width = isMobile ? MOBILE_WIDTH : DESKTOP_WIDTH;
  const { containerRef, scale } = useContainerScale(width);

  return (
    <div
      ref={containerRef}
      className={cn(
        'relative z-30 w-full -translate-x-24 scale-70 p-6 px-0 sm:translate-x-8 sm:scale-100 md:px-12 lg:translate-x-0 lg:translate-y-0',
        className,
      )}
    >
      <div
        className="origin-top-left"
        style={{
          width,
          transform: `scale(${scale})`,
          height: height * scale,
        }}
      >
        <div className="relative flex" style={{ height }}>
          <div className="dark flex overflow-hidden rounded-xl" style={{ height: 1050 }}>
            <ShowcaseIconRail icons={DASHBOARD_NAV_ICONS} />
            <DashboardContent />
          </div>
        </div>
      </div>
    </div>
  );
};

export const DashboardContent: FC<ClassNameProps> = ({ className }) => {
  return (
    <div
      className={cn(
        'glass-panel relative flex flex-1 flex-col overflow-hidden backdrop-blur-xl',
        className,
      )}
    >
      {/* Gradient accent line */}

      <div className="px-12 pt-10 pb-16">
        {/* Greeting */}
        <h2 className="font-serif text-3xl tracking-tighter text-white">Morning Jared</h2>
        <p className="font-display text-sm tracking-tighter text-content">
          here&apos;s your customer lifecycle at a glance.
        </p>

        {/* Card Grid */}
        <div className="mt-4 grid grid-cols-3 gap-2">
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
            <p className="text-sm leading-relaxed text-neutral-200">
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
              className="mt-3 font-display text-4xl font-normal"
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
          <ActionPill>Channel Performance</ActionPill>
          <ActionPill>Cohort Overview</ActionPill>
          <ActionPill>Support Correlation</ActionPill>
          <ActionPill>At-Risk Segment</ActionPill>
          <ActionPill>Churn Retrospective</ActionPill>
          <ActionPill>
            <Download className="size-3" />
            Export
          </ActionPill>
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
      <div className="rounded-t-xl border border-b-0 border-border px-2 py-2 shadow-sm">
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
      <div className="flex items-center gap-3 rounded-b-xl border border-border px-4 py-3 shadow-sm">
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
