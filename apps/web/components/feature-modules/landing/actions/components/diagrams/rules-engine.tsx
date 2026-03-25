'use client';

import {
  inViewProps,
  useAnimateOnMount,
} from '@/components/feature-modules/landing/actions/components/animate-context';
import { BrandInstagram } from '@/components/ui/diagrams/brand-icons';
import { ShowcaseIconRail, ShowcaseSubPanel } from '@/components/ui/diagrams/brand-ui-primitives';
import { GlowBorder } from '@/components/ui/glow-border';
import { WindowControls } from '@/components/ui/window-controls';
import { Bell, CogIcon, LayoutGrid, Search } from 'lucide-react';
import { motion } from 'motion/react';
import { DashboardContent } from '../../../hero/components/dashboard/mock-dashboard';

// ── Filter Chip (same style as query-builder) ───────────────────────

const FilterChip = ({ icon, children }: { icon?: React.ReactNode; children: React.ReactNode }) => (
  <div className="flex h-[18px] items-center gap-0.5 rounded border border-border bg-card px-1 shadow-sm">
    {icon}
    <span className="text-[10px] whitespace-nowrap text-muted-foreground">{children}</span>
  </div>
);

// ── Condition Label ─────────────────────────────────────────────────

const ConditionLabel = ({ children }: { children: React.ReactNode }) => (
  <span className="w-12 text-[8px] font-semibold text-muted-foreground">{children}</span>
);

// ── Trigger Entry ───────────────────────────────────────────────────

const TriggerEntry = ({ color, children }: { color: string; children: React.ReactNode }) => (
  <div className="flex items-center gap-1.5">
    <div className="size-1.5 shrink-0 rounded-full" style={{ backgroundColor: color }} />
    <span className="text-[8px] text-muted-foreground">{children}</span>
  </div>
);

// ── Notification Item ───────────────────────────────────────────────

interface NotificationItem {
  dotColor: string;
  title: string;
  detail: string;
  timestamp: string;
}

const NOTIFICATIONS: NotificationItem[] = [
  {
    dotColor: '#ef4444',
    title: 'Instagram churn hit 11.2%',
    detail: '8 customers flagged, $14,200 ARR',
    timestamp: '2 days ago',
  },
  {
    dotColor: '#f59e0b',
    title: 'At-risk segment grew by 12',
    detail: 'Week-over-week increase',
    timestamp: '3 days ago',
  },
  {
    dotColor: '#ef4444',
    title: 'Payment failure streak',
    detail: '3 accounts, $6,800 ARR',
    timestamp: '5 days ago',
  },
  {
    dotColor: '#3b82f6',
    title: 'Email open rate below 12%',
    detail: '7 consecutive days',
    timestamp: '1 week ago',
  },
  {
    dotColor: '#f59e0b',
    title: 'MRR dropped 6.2% w/w',
    detail: 'Above 5% threshold',
    timestamp: '1 week ago',
  },
];

const INBOX_NAV_ICONS = [
  { icon: <LayoutGrid className="size-5" /> },
  { icon: <LayoutGrid className="size-5" /> },
  { icon: <Bell className="size-5" />, active: true },
  { icon: <Search className="size-5" /> },
  { icon: <CogIcon className="size-5" /> },
];

// ── Main Component ──────────────────────────────────────────────────

export const RulesEngineGraphic = () => {
  const onMount = useAnimateOnMount();

  return (
    <>
      {/* Rule Definition — front, left */}
      <GlowBorder className="absolute bottom-0 left-0 z-10 hidden w-[520px] sm:block md:scale-110 lg:bottom-16 lg:left-16 lg:scale-120">
        <motion.div
          className="paper-lite w-full rounded-md border border-border bg-card p-5 shadow-lg"
          initial={{ opacity: 0, y: 12 }}
          {...inViewProps(onMount, { opacity: 1, y: 0 })}
          transition={{ duration: 0.4 }}
        >
          {/* Window Controls */}
          <WindowControls size={6} />

          {/* Title */}
          <p className="mt-2.5 text-[12px] font-semibold text-foreground">
            Instagram churn crosses 10%
          </p>

          {/* Status Row */}
          <div className="mt-1.5 flex items-center gap-2">
            <span className="rounded-full border border-emerald-500/40 bg-emerald-500/10 px-1.5 py-px text-[6.5px] font-semibold text-emerald-600">
              Active
            </span>
            <span className="text-[8px] text-muted-foreground">Last triggered: 2 days ago</span>
          </div>

          {/* Condition Block */}
          <div className="mt-3 space-y-2 rounded-lg bg-muted p-3">
            {/* WHEN */}
            <div className="flex items-center gap-1">
              <ConditionLabel>WHEN</ConditionLabel>
              <FilterChip>Churn Rate</FilterChip>
              <FilterChip>for</FilterChip>
              <FilterChip icon={<BrandInstagram size={10} />}>Instagram</FilterChip>
              <FilterChip>exceeds</FilterChip>
              <FilterChip>10%</FilterChip>
            </div>

            {/* OVER */}
            <div className="flex items-center gap-1">
              <ConditionLabel>OVER</ConditionLabel>
              <FilterChip>Rolling 30-day window</FilterChip>
            </div>

            {/* THEN */}
            <div className="flex items-center gap-1">
              <ConditionLabel>THEN</ConditionLabel>
              <FilterChip>Add to morning queue</FilterChip>
              <FilterChip>Flag affected customers</FilterChip>
            </div>
          </div>

          {/* Recent Triggers */}
          <div className="mt-3">
            <span className="text-[7px] font-semibold text-muted-foreground">Recent Triggers</span>
            <div className="mt-1 space-y-1">
              <TriggerEntry color="#22c55e">
                Mar 22 — Rate hit 11.2% (8 customers, $14,200 ARR)
              </TriggerEntry>
              <TriggerEntry color="#22c55e">
                Mar 8 — Rate hit 10.4% (5 customers, $8,900 ARR)
              </TriggerEntry>
              <TriggerEntry color="#9ca3af">
                Feb 19 — Rate hit 10.1% (3 customers, $6,100 ARR)
              </TriggerEntry>
            </div>
          </div>

          {/* Footer */}
          <p className="mt-3 text-[8px] text-muted-foreground/50">Runs every night at 2:00 AM</p>
        </motion.div>
      </GlowBorder>

      {/* Notification Inbox — behind, right, overlapping */}

      <motion.div
        className="absolute translate-y-32 scale-90 overflow-hidden rounded-sm bg-card shadow-lg sm:translate-y-24 md:translate-x-64 md:scale-80 lg:translate-y-12 lg:scale-100"
        initial={{ opacity: 0, y: 12 }}
        {...inViewProps(onMount, { opacity: 1, y: 0 })}
        transition={{ duration: 0.4, delay: 0.15 }}
      >
        <div className="flex lg:w-[1920px]" style={{ height: 800 }}>
          <ShowcaseIconRail icons={INBOX_NAV_ICONS} />
          <ShowcaseSubPanel>
            {/* Header */}
            <div className="paper-lite flex h-12 shrink-0 items-center border-b border-border px-4">
              <span className="text-sm font-semibold text-foreground">Notifications</span>
            </div>

            {/* Notification List */}
            <div className="flex-1 overflow-hidden">
              {NOTIFICATIONS.map((item, i) => (
                <div
                  key={i}
                  className="flex items-start gap-2 border-b border-border/40 px-3 py-2.5"
                >
                  {/* Severity Dot */}
                  <div
                    className="mt-1 size-2 shrink-0 rounded-full"
                    style={{ backgroundColor: item.dotColor }}
                  />
                  {/* Content */}
                  <div className="min-w-0">
                    <p className="text-xs font-semibold text-foreground">{item.title}</p>
                    <p className="text-xs text-muted-foreground/60">{item.detail}</p>
                    <p className="mt-0.5 text-[10px] text-muted-foreground/40">{item.timestamp}</p>
                  </div>
                </div>
              ))}
            </div>
          </ShowcaseSubPanel>
          <div className="w-32 lg:hidden" />
          <DashboardContent className="hidden lg:block" />
        </div>
      </motion.div>
    </>
  );
};
