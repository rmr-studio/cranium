import { cn } from '@/lib/utils';
import { CogIcon, LayoutGrid, Search } from 'lucide-react';

import { BrandKlaviyo, BrandShopify, BrandStripe } from '@/components/ui/diagrams/brand-icons';
import { ShowcaseIconRail } from '@/components/ui/diagrams/brand-ui-primitives';

const QUEUE_NAV_ICONS = [
  { icon: <LayoutGrid className="size-5" />, active: true },
  { icon: <LayoutGrid className="size-5" />, active: false },
  { icon: <Search className="size-5" />, active: false },
  { icon: <CogIcon className="size-5" />, active: false },
];

// -- Queue Item Card Shell ------------------------------------------------

function QueueCard({
  label,
  labelColor,
  timestamp,
  borderColor,
  icon,
  title,
  revenue,
  description,
  actions,
  className,
}: {
  label: string;
  labelColor: string;
  timestamp: string;
  borderColor: string;
  icon: React.ReactNode;
  title: string;
  revenue?: string;
  description?: string;
  actions: { primary: string; secondary: string };
  className?: string;
}) {
  return (
    <div
      className={cn('rounded-lg border border-l-4 border-border bg-card p-5', className)}
      style={{ borderLeftColor: borderColor }}
    >
      {/* Header */}
      <div className="flex items-center justify-between">
        <span
          className="font-display text-xs tracking-[0.05em] uppercase"
          style={{ color: labelColor }}
        >
          {label}
        </span>
        <span className="font-display text-xs tracking-[0.05em] text-muted-foreground/30 uppercase">
          {timestamp}
        </span>
      </div>

      {/* Title row */}
      <div className="mt-4 flex items-center gap-2.5">
        {icon}
        <span className="text-sm font-semibold text-foreground">{title}</span>
      </div>

      {/* Revenue badge */}
      {revenue && (
        <div className="mt-3">
          <span className="rounded-md bg-destructive/10 px-2 py-0.5 text-xs font-semibold text-destructive">
            {revenue}
          </span>
        </div>
      )}

      {/* Description */}
      {description && (
        <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{description}</p>
      )}

      {/* Actions */}
      <div className="mt-5 flex items-center gap-6">
        <span className="font-display text-xs tracking-[0.05em] text-foreground/70 uppercase">
          {actions.primary}
        </span>
        <span className="font-display text-xs tracking-[0.05em] text-muted-foreground/30 uppercase">
          {actions.secondary}
        </span>
      </div>
    </div>
  );
}

// -- Queue Item Data ------------------------------------------------------

const QUEUE_ITEMS = [
  {
    label: 'AT-RISK CUSTOMER',
    labelColor: 'oklch(0.577 0.245 27.325)',
    timestamp: '6h ago',
    borderColor: 'oklch(0.577 0.245 27.325)',
    icon: <BrandStripe size={18} />,
    title: 'Scalegrid AI \u2014 usage dropped 42% in 30 days',
    revenue: '$8,400/mo ARR',
    description: undefined,
    actions: { primary: 'View', secondary: 'Dismiss' },
  },
  {
    label: 'SEGMENT SHIFT',
    labelColor: 'oklch(0.8 0.15 75)',
    timestamp: 'Overnight',
    borderColor: 'oklch(0.8 0.15 75)',
    icon: <BrandShopify size={18} />,
    title: 'Instagram cohort grew by 14 this week, churn rate 2.1x avg',
    revenue: undefined,
    description: undefined,
    actions: { primary: 'Explore', secondary: 'Dismiss' },
  },
  {
    label: 'RULE TRIGGERED',
    labelColor: 'oklch(0.6 0.15 250)',
    timestamp: '4h ago',
    borderColor: 'oklch(0.6 0.15 250)',
    icon: <BrandKlaviyo size={18} />,
    title: 'Email open rate below 12% for 7 consecutive days',
    revenue: '$22,100 ARR exposed',
    description: undefined,
    actions: { primary: 'Act', secondary: 'Snooze' },
  },
] as const;

// Partial 4th card (faded, clipped by parent overflow)
const GHOST_CARD = {
  label: 'USAGE ANOMALY',
  labelColor: 'oklch(0.6 0.15 250)',
  timestamp: '12h ago',
  borderColor: 'oklch(0.6 0.15 250)',
  icon: <BrandStripe size={18} />,
  title: 'API call volume spiked 3x for Acme Corp',
  revenue: '$14,800/mo ARR',
  actions: { primary: 'Investigate', secondary: 'Dismiss' },
};

// -- Main Component -------------------------------------------------------

export function MockMorningQueue() {
  return (
    <div
      className=":border z-20 flex overflow-hidden rounded-xl border-border bg-card shadow-lg"
      style={{ height: 1200 }}
    >
      <ShowcaseIconRail icons={QUEUE_NAV_ICONS} />

      {/* Main content */}
      <div className="paper relative flex flex-1 flex-col overflow-hidden bg-background">
        {/* Gradient accent line */}
        <div
          className="h-px w-full"
          style={{
            background:
              'linear-gradient(90deg, var(--cta-g1), var(--cta-g2), var(--cta-g3), transparent)',
          }}
        />

        {/* Content area */}
        <div className="px-12 pt-10 pb-16">
          {/* Greeting */}
          <h2 className="text-5xl font-normal -tracking-[0.02em] text-foreground">Good morning</h2>
          <p className="mt-2 text-sm" style={{ color: 'oklch(0.65 0.17 145)' }}>
            3 items need your attention
          </p>

          {/* Queue cards — single column stack */}
          <div className="mt-8 flex flex-col gap-4">
            {QUEUE_ITEMS.map((item, i) => (
              <QueueCard key={i} {...item} />
            ))}

            {/* Ghost 4th card — partially visible */}
            <QueueCard {...GHOST_CARD} className="opacity-40" />
          </div>
        </div>
      </div>
    </div>
  );
}
