import { cn } from '@/lib/utils';
import {
  ArrowRight,
  Calendar,
  Clock,
  CogIcon,
  DollarSign,
  FileText,
  Globe,
  LayoutGrid,
  Package,
  Search,
  Sparkles,
  Ticket,
  TrendingUp,
  XIcon,
} from 'lucide-react';

import {
  BrandIntercom,
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

const NAV_ICONS = [
  { icon: <LayoutGrid className="size-5" /> },
  { icon: <LayoutGrid className="size-5" /> },
  { icon: <TrendingUp className="size-5" />, active: true },
  { icon: <Search className="size-5" /> },
  { icon: <CogIcon className="size-5" /> },
];

// ── Cohort Customer List ────────────────────────────────────────────

interface CohortCustomer {
  name: string;
  ltv: string;
}

const POWER_USERS: CohortCustomer[] = [
  { name: 'Elena Vasquez', ltv: '$4,280' },
  { name: 'Jordan Mitchell', ltv: '$3,910' },
  { name: 'Priya Sharma', ltv: '$3,640' },
];

const RISING_STARS: CohortCustomer[] = [
  { name: 'Tobias Keller', ltv: '$2,180' },
  { name: 'Mei-Lin Chen', ltv: '$1,950' },
  { name: 'Aisha Okonkwo', ltv: '$1,820' },
  { name: 'Lucas Bergström', ltv: '$1,710' },
  { name: 'Sofia Reyes', ltv: '$1,560' },
  { name: 'Ryan Gallagher', ltv: '$1,430' },
];

function CohortSubPanel() {
  return (
    <ShowcaseSubPanel>
      {/* Header */}
      <div className="flex h-12 shrink-0 items-center border-b border-border px-4">
        <span className="text-sm font-semibold text-foreground">Most Valuable Cohort</span>
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
        {/* Power Users */}
        <div className="mb-3">
          <span className="px-2 font-display text-xs tracking-[0.05em] text-muted-foreground/50 uppercase">
            Power users ({POWER_USERS.length})
          </span>
          <div className="mt-1.5 flex flex-col gap-0.5">
            {POWER_USERS.map((c, i) => (
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
                    LTV {c.ltv}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Rising Stars */}
        <div>
          <span className="px-2 font-display text-xs tracking-[0.05em] text-muted-foreground/50 uppercase">
            Rising stars ({RISING_STARS.length})
          </span>
          <div className="mt-1.5 flex flex-col gap-0.5">
            {RISING_STARS.map((c) => (
              <div
                key={c.name}
                className="flex items-center gap-2 rounded-md px-3 py-2 text-sm text-muted-foreground/60"
              >
                <FileText className="size-3.5 shrink-0" />
                <div className="min-w-0">
                  <div className="truncate">{c.name}</div>
                  <div className="font-display text-xs tracking-[0.03em] text-muted-foreground/40 uppercase">
                    LTV {c.ltv}
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

interface CohortTimelineEvent {
  date: string;
  source: string;
  sourceIcon: React.ReactNode;
  title: React.ReactNode;
  detail: string;
  positive?: boolean;
}

const TIMELINE_EVENTS: CohortTimelineEvent[] = [
  {
    date: 'Mar 28, 2026',
    source: 'Shopify',
    sourceIcon: <BrandShopify size={14} />,
    title: '5th repeat purchase — Order #1247',
    detail: 'Full-price purchase, no discount code. Average order value 28% above cohort mean.',
    positive: true,
  },
  {
    date: 'Mar 18, 2026',
    source: 'Intercom',
    sourceIcon: <BrandIntercom size={14} />,
    title: 'NPS response — Score 10',
    detail: 'Left detailed qualitative feedback. Mentioned product quality and shipping speed.',
    positive: true,
  },
  {
    date: 'Mar 12, 2026',
    source: 'Stripe',
    sourceIcon: <BrandStripe size={14} />,
    title: 'Upgraded to annual plan',
    detail: 'Switched from monthly to annual billing. Locked in for 12 months.',
    positive: true,
  },
  {
    date: 'Feb 26, 2026',
    source: 'Shopify',
    sourceIcon: <BrandShopify size={14} />,
    title: 'Referred a new customer',
    detail: 'Referral link used by Jordan Mitchell, who converted within 48 hours.',
    positive: true,
  },
  {
    date: 'Feb 14, 2026',
    source: 'Klaviyo',
    sourceIcon: <BrandKlaviyo size={14} />,
    title: 'Opened 3 emails this week',
    detail: 'Engaged with "New Arrivals", "Member Exclusive", and "Style Guide" campaigns.',
  },
  {
    date: 'Jan 30, 2026',
    source: 'Shopify',
    sourceIcon: <BrandShopify size={14} />,
    title: '4th repeat purchase — Order #1180',
    detail: 'Cross-category purchase (apparel + accessories). First accessories buy.',
    positive: true,
  },
  {
    date: 'Jan 8, 2026',
    source: 'Stripe',
    sourceIcon: <BrandStripe size={14} />,
    title: 'Subscription renewed',
    detail: 'Auto-renewed monthly plan. 14th consecutive successful payment.',
  },
];

// ── Main Content ────────────────────────────────────────────────────

function CohortContent() {
  return (
    <div className="relative flex flex-1 flex-col overflow-hidden bg-amber-50/70 backdrop-blur-xl">
      {/* Breadcrumb bar */}
      <div className="flex h-12 shrink-0 items-center border-b border-border px-6">
        <MockBreadcrumb items={['Workspace', 'Cohorts', 'Elena Vasquez']} />
      </div>

      {/* Scrollable content */}
      <div className="flex-1 overflow-y-auto px-8 pt-6 pb-8">
        {/* Title row */}
        <div className="flex items-start justify-between">
          <h2 className="text-4xl font-normal -tracking-[0.02em] text-foreground">Elena Vasquez</h2>
          <span className="rounded-md bg-green-300 px-3 py-1 font-display text-xs tracking-[0.05em] text-background uppercase">
            Most Valuable
          </span>
        </div>

        {/* Meta row */}
        <div className="mt-3 flex flex-wrap items-center gap-4 text-xs text-muted-foreground">
          <span className="inline-flex items-center gap-1.5">
            <Calendar className="size-3" />
            <span className="font-display tracking-[0.03em] uppercase">Joined Jun 12, 2024</span>
          </span>
          <span className="inline-flex items-center gap-1.5">
            <Clock className="size-3" />
            <span className="font-display tracking-[0.03em] uppercase">
              Active 21 months - Pro Tier
            </span>
          </span>
          <span className="inline-flex items-center gap-1.5">
            <DollarSign className="size-3" />
            <span className="font-display tracking-[0.03em] uppercase">LTV 4,280</span>
          </span>
          <span className="inline-flex items-center gap-1.5">
            <Globe className="size-3" />
            <span className="font-display tracking-[0.03em] uppercase">Referral</span>
          </span>
        </div>

        {/* Pattern Card */}
        <div className="mt-6 rounded-lg border border-border bg-background p-5 shadow-lg">
          <div className="flex items-start gap-4">
            <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-green-400/10">
              <Sparkles className="size-5 text-green-400" />
            </div>
            <div className="flex-1">
              <p className="text-sm font-semibold text-foreground">Retention pattern identified</p>
              <p className="mt-1.5 text-xs leading-relaxed text-muted-foreground">
                Elena follows the high-value loop: repeat purchase within 30 days, cross-category
                expansion, then referral.{' '}
                <span className="font-medium text-green-400">
                  74% of customers matching this pattern retain for 24+ months.
                </span>
              </p>
              <div className="mt-2 flex items-center gap-2">
                <span className="font-display text-xs tracking-[0.05em] text-green-400/80 uppercase">
                  View 42 matching customers
                </span>
                <ArrowRight className="size-3 text-green-400/80" />
              </div>
            </div>
          </div>
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
                    event.positive ? 'text-green-400' : 'text-foreground',
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

      {/* Suggested Actions — sticky at bottom */}
      <div className="hidden shrink-0 border-t border-border bg-card px-5 py-4 md:block">
        <div className="flex items-start gap-2">
          <XIcon className="mt-0.5 size-3 text-neutral-400" />
          <p className="mb-3 font-display text-xs tracking-[0.05em] text-muted-foreground/50 uppercase">
            Suggested actions
          </p>
        </div>
        <div className="flex gap-3">
          <div className="flex items-center gap-2.5 rounded-md border border-border px-3 py-2">
            <Package className="size-3.5 shrink-0 text-green-400" />
            <span className="text-xs text-muted-foreground">Launch cross-category starter kit</span>
          </div>
          <div className="flex items-center gap-2.5 rounded-md border border-border px-3 py-2">
            <Ticket className="size-3.5 shrink-0 text-green-400" />
            <span className="text-xs text-muted-foreground">Add referral reward incentive</span>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Main Export ──────────────────────────────────────────────────────

export function MockCohortBehaviours() {
  return (
    <div className="flex overflow-hidden rounded-xl" style={{ height: 950 }}>
      <ShowcaseIconRail icons={NAV_ICONS} />
      <CohortSubPanel />
      <CohortContent />
    </div>
  );
}
