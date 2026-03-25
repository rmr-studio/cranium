import { cn } from '@/lib/utils';
import {
  Calendar,
  Clock,
  CogIcon,
  DollarSign,
  FileText,
  Globe,
  LayoutGrid,
  Search,
  Sparkles,
  TrendingDown,
} from 'lucide-react';

import {
  BrandGorgias,
  BrandKlaviyo,
  BrandShopify,
  BrandStripe,
} from '@/components/ui/diagrams/brand-icons';
import {
  MockBreadcrumb,
  ShowcaseIconRail,
  ShowcaseSubPanel,
} from '@/components/ui/diagrams/brand-ui-primitives';

// ── Icon Rail Config ────────────────────────────────────────────────

const CHURN_NAV_ICONS = [
  { icon: <LayoutGrid className="size-5" /> },
  { icon: <LayoutGrid className="size-5" /> },
  { icon: <TrendingDown className="size-5" />, active: true },
  { icon: <Search className="size-5" /> },
  { icon: <CogIcon className="size-5" /> },
];

// ── Churned Customer List ───────────────────────────────────────────

interface ChurnedCustomer {
  name: string;
  date: string;
}

const THIS_MONTH: ChurnedCustomer[] = [
  { name: 'Marcus Aurelius', date: 'Mar 16, 2026' },
  { name: 'Steven Webb', date: 'Mar 08, 2026' },
  { name: 'Rachel Simmons', date: 'Mar 02, 2026' },
];

const EARLIER_THIS_YEAR: ChurnedCustomer[] = [
  { name: 'Kai Tanaka', date: 'Feb 22, 2026' },
  { name: 'Ana Costa', date: 'Feb 12, 2026' },
  { name: 'James Liu', date: 'Feb 03, 2026' },
  { name: 'Lena Park', date: 'Jan 28, 2026' },
  { name: 'David Okafor', date: 'Jan 19, 2026' },
  { name: 'Carlos Mendez', date: 'Jan 03, 2026' },
];

function ChurnSubPanel() {
  return (
    <ShowcaseSubPanel>
      {/* Header */}
      <div className="paper-lite flex h-12 shrink-0 items-center border-b border-border px-4">
        <span className="text-sm font-semibold text-foreground">Churned Customers</span>
      </div>

      {/* Search */}
      <div className="px-3 pt-2.5 pb-2">
        <div className="flex items-center gap-1.5 rounded-md border border-border bg-muted/30 px-2.5 py-1.5">
          <Search className="size-3.5 text-muted-foreground/50" />
          <span className="text-xs text-muted-foreground/40">Search records...</span>
        </div>
      </div>

      {/* Groups */}
      <div className="flex-1 overflow-hidden px-2.5">
        {/* This Month */}
        <div className="mb-3">
          <span className="px-2 font-display text-xs tracking-[0.05em] text-muted-foreground/50 uppercase">
            This month ({THIS_MONTH.length})
          </span>
          <div className="mt-1.5 flex flex-col gap-0.5">
            {THIS_MONTH.map((c, i) => (
              <div
                key={c.name}
                className={cn(
                  'flex items-center gap-2 rounded-md px-3 py-2 text-sm',
                  i === 0 ? 'bg-accent font-medium text-foreground' : 'text-muted-foreground/60',
                )}
              >
                <FileText className="size-3.5 shrink-0" />
                <div className="min-w-0">
                  <div className="truncate">{c.name}</div>
                  <div className="font-display text-xs tracking-[0.03em] text-muted-foreground/40 uppercase">
                    {c.date}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Earlier This Year */}
        <div>
          <span className="px-2 font-display text-xs tracking-[0.05em] text-muted-foreground/50 uppercase">
            Earlier this year ({EARLIER_THIS_YEAR.length})
          </span>
          <div className="mt-1.5 flex flex-col gap-0.5">
            {EARLIER_THIS_YEAR.map((c) => (
              <div
                key={c.name}
                className="flex items-center gap-2 rounded-md px-3 py-2 text-sm text-muted-foreground/60"
              >
                <FileText className="size-3.5 shrink-0" />
                <div className="min-w-0">
                  <div className="truncate">{c.name}</div>
                  <div className="font-display text-xs tracking-[0.03em] text-muted-foreground/40 uppercase">
                    {c.date}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </ShowcaseSubPanel>
  );
}

// ── Timeline Events ─────────────────────────────────────────────────

interface ChurnTimelineEvent {
  date: string;
  source: string;
  sourceIcon: React.ReactNode;
  title: React.ReactNode;
  detail: string;
  negative?: boolean;
}

const TIMELINE_EVENTS: ChurnTimelineEvent[] = [
  {
    date: 'Mar 16, 2026',
    source: 'Stripe',
    sourceIcon: <BrandStripe size={14} />,
    title: 'Subscription cancelled',
    detail: 'Automatic cancellation after final billing failure.',
    negative: true,
  },
  {
    date: 'Mar 14, 2026',
    source: 'Gorgias',
    sourceIcon: <BrandGorgias size={14} />,
    title: 'Ticket #4702 — Unresolved',
    detail: 'Customer complained about damaged packaging. Ticket closed without resolution.',
    negative: true,
  },
  {
    date: 'Mar 9, 2026',
    source: 'Gorgias',
    sourceIcon: <BrandGorgias size={14} />,
    title: 'Ticket #4688 — Resolved',
    detail: 'Inquiry regarding delivery timeframe. Customer satisfied with response.',
  },
  {
    date: 'Mar 5, 2026',
    source: 'Shopify',
    sourceIcon: <BrandShopify size={14} />,
    title: 'Return initiated',
    detail: 'Items from order #1002 returned (inconsistent fit).',
  },
  {
    date: 'Feb 22, 2026',
    source: 'Klaviyo',
    sourceIcon: <BrandKlaviyo size={14} />,
    title: 'Opened email',
    detail: 'Campaign: "Spring Arrivals is Here". Clicks recorded for apparel category.',
  },
  {
    date: 'Feb 18, 2026',
    source: 'Stripe',
    sourceIcon: <BrandStripe size={14} />,
    title: 'Payment failed',
    detail: 'Card declined (insufficient funds). First retry scheduled for Feb 21.',
    negative: true,
  },
  {
    date: 'Feb 1, 2026',
    source: 'Shopify',
    sourceIcon: <BrandShopify size={14} />,
    title: 'Return initiated',
    detail: 'Partial return of order #998 (color not as expected).',
  },
];

// ── Main Content ────────────────────────────────────────────────────

function ChurnContent() {
  return (
    <div className="paper-lite relative flex flex-1 flex-col overflow-hidden bg-background">
      {/* Breadcrumb bar */}
      <div className="flex h-12 shrink-0 items-center border-b border-border px-6">
        <MockBreadcrumb items={['Workspace', 'Churned', 'Marcus Aurelius']} />
      </div>

      {/* Scrollable content */}
      <div className="flex-1 overflow-y-auto px-8 pt-6 pb-8">
        {/* Title row */}
        <div className="flex items-start justify-between">
          <h2 className="font-serif text-4xl font-normal -tracking-[0.02em] text-foreground">
            Churn Retrospective
          </h2>
          <span className="rounded-md bg-destructive px-3 py-1 font-display text-xs tracking-[0.05em] text-white uppercase">
            Churned
          </span>
        </div>

        {/* Meta row */}
        <div className="mt-3 flex flex-wrap items-center gap-4 text-xs text-muted-foreground">
          <span className="inline-flex items-center gap-1.5">
            <Calendar className="size-3" />
            <span className="font-display tracking-[0.03em] uppercase">Churned Mar 16, 2026</span>
          </span>
          <span className="inline-flex items-center gap-1.5">
            <Clock className="size-3" />
            <span className="font-display tracking-[0.03em] uppercase">Active 4 months</span>
          </span>
          <span className="inline-flex items-center gap-1.5">
            <DollarSign className="size-3" />
            <span className="font-display tracking-[0.03em] uppercase">LTV #212</span>
          </span>
          <span className="inline-flex items-center gap-1.5">
            <Globe className="size-3" />
            <span className="font-display tracking-[0.03em] uppercase">Reddit Ads</span>
          </span>
        </div>

        {/* Timeline */}
        <div className="mt-8 flex flex-col gap-0">
          {TIMELINE_EVENTS.map((event, i) => (
            <div key={i} className="flex gap-3">
              {/* Timeline icon + line */}
              <div className="flex flex-col items-center">
                <div className="mt-0.5 shrink-0">{event.sourceIcon}</div>
                {i < TIMELINE_EVENTS.length - 1 && <div className="w-px flex-1 bg-border" />}
              </div>

              {/* Content */}
              <div className="flex-1 pb-6">
                <div className="flex items-center justify-between">
                  <span className="text-xs text-muted-foreground/60">{event.date}</span>
                  <span className="font-display text-xs tracking-[0.05em] text-muted-foreground/50 uppercase">
                    {event.source}
                  </span>
                </div>
                <p
                  className={cn(
                    'mt-1 text-sm leading-snug font-semibold',
                    event.negative ? 'text-destructive' : 'text-foreground',
                  )}
                >
                  {event.title}
                </p>
                <p className="mt-0.5 font-display text-xs leading-relaxed tracking-[0.02em] text-muted-foreground/60 uppercase">
                  {event.detail}
                </p>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Pattern Detected — sticky toast at bottom */}
      <div className="shrink-0 border-t border-border bg-card px-5 py-4">
        <div className="flex items-center gap-4">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-muted">
            <Sparkles className="size-5 text-muted-foreground" />
          </div>
          <div className="flex-1">
            <p className="text-sm font-semibold text-foreground">Pattern detected</p>
            <p className="mt-0.5 text-xs leading-relaxed text-muted-foreground">
              Marcus experienced two return events followed by an unresolved support ticket within
              45 days. High churn correlation identified.
            </p>
          </div>
          <span className="shrink-0 font-display text-xs tracking-[0.05em] text-muted-foreground/60 uppercase">
            View 18 matching customers &rarr;
          </span>
        </div>
      </div>
    </div>
  );
}

// ── Main Export ──────────────────────────────────────────────────────

export function MockChurnRetrospective() {
  return (
    <div
      className="flex overflow-hidden rounded-xl border border-primary/50 bg-card shadow-lg"
      style={{ height: 950 }}
    >
      <ShowcaseIconRail icons={CHURN_NAV_ICONS} />
      <ChurnSubPanel />
      <ChurnContent />
    </div>
  );
}
