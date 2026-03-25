import { cn } from '@/lib/utils';
import { CogIcon, LayoutGrid, Search, TrendingDown } from 'lucide-react';

import {
  BrandGorgias,
  BrandHubSpot,
  BrandIntercom,
  BrandKlaviyo,
  BrandShopify,
  BrandStripe,
} from '@/components/ui/diagrams/brand-icons';
import { MockBreadcrumb, ShowcaseIconRail } from '@/components/ui/diagrams/brand-ui-primitives';

// -- Icon Rail Config --------------------------------------------------------

const ENTITY_NAV_ICONS = [
  { icon: <LayoutGrid className="size-5" /> },
  { icon: <LayoutGrid className="size-5" />, active: true },
  { icon: <Search className="size-5" /> },
  { icon: <TrendingDown className="size-5" /> },
  { icon: <CogIcon className="size-5" /> },
];

// -- Connected Records Data --------------------------------------------------

interface ConnectedRecord {
  icon: React.ReactNode;
  platform: string;
  metric1Label: string;
  metric1Value: string;
  metric2: string;
  isNew?: boolean;
}

const CONNECTED_RECORDS: ConnectedRecord[] = [
  {
    icon: <BrandStripe size={14} />,
    platform: 'Stripe',
    metric1Value: '3',
    metric1Label: 'Invoices',
    metric2: '$2,840 LTV',
  },
  {
    icon: <BrandIntercom size={14} />,
    platform: 'Intercom',
    metric1Value: '2',
    metric1Label: 'Tickets',
    metric2: '1 Open',
  },
  {
    icon: <BrandShopify size={14} />,
    platform: 'Shopify',
    metric1Value: '8',
    metric1Label: 'Orders',
    metric2: 'Last: 3 days ago',
  },
  {
    icon: <BrandKlaviyo size={14} />,
    platform: 'Klaviyo',
    metric1Value: '12',
    metric1Label: 'Campaigns',
    metric2: '4 Opened',
  },
  {
    icon: <BrandHubSpot size={14} />,
    platform: 'HubSpot',
    metric1Value: '1',
    metric1Label: 'Contact',
    metric2: 'via email',
    isNew: true,
  },
];

// -- Timeline Events ---------------------------------------------------------

interface EntityTimelineEvent {
  date: string;
  source: string;
  sourceIcon: React.ReactNode;
  title: string;
  titleColor?: string;
  detail: string;
  isNew?: boolean;
}

const TIMELINE_EVENTS: EntityTimelineEvent[] = [
  {
    date: 'Mar 18, 2026',
    source: 'Gorgias',
    sourceIcon: <BrandGorgias size={14} />,
    title: 'Ticket #4891 opened',
    detail: 'billing discrepancy \u2014 High Priority',
  },
  {
    date: 'Mar 14, 2026',
    source: 'Stripe',
    sourceIcon: <BrandStripe size={14} />,
    title: 'Payment retry failed',
    detail: 'Ref: strnt_1QxMkl',
  },
  {
    date: 'Mar 11, 2026',
    source: 'Shopify',
    sourceIcon: <BrandShopify size={14} />,
    title: 'Return initiated',
    detail: 'Order #1847',
  },
  {
    date: 'Mar 3, 2026',
    source: 'Klaviyo',
    sourceIcon: <BrandKlaviyo size={14} />,
    title: 'Opened email',
    detail: 'Campaign: Spring_Retention_24',
  },
  {
    date: 'Feb 28, 2026',
    source: 'HubSpot',
    sourceIcon: <BrandHubSpot size={14} />,
    title: 'Contact created',
    titleColor: 'text-emerald-600',
    detail: 'Auto-linked via email match',
    isNew: true,
  },
];

// -- Main Export -------------------------------------------------------------

export function MockEntityDetail() {
  return (
    <div
      className="flex overflow-hidden rounded-xl border border-primary/50 bg-card shadow-lg"
      style={{ height: 950 }}
    >
      <ShowcaseIconRail icons={ENTITY_NAV_ICONS} />

      {/* Main content */}
      <div className="paper-lite relative flex flex-1 flex-col overflow-hidden bg-background">
        {/* Breadcrumb bar */}
        <div className="flex h-12 shrink-0 items-center border-b border-border px-6">
          <MockBreadcrumb items={['Workspace', 'Entities', 'Customers', 'Sarah Chen']} />
        </div>

        {/* Scrollable content */}
        <div className="flex-1 overflow-y-auto px-8 pt-6 pb-8">
          {/* Header section */}
          <div className="flex items-start justify-between">
            <h2 className="font-serif text-4xl font-normal -tracking-[0.02em] text-foreground">
              Sarah Chen
            </h2>
            <span className="rounded-md bg-emerald-500/10 px-3 py-1 font-display text-xs tracking-[0.05em] text-emerald-600 uppercase">
              Active
            </span>
          </div>

          {/* Subtitle */}
          <div className="mt-2 flex items-center gap-3 text-xs text-muted-foreground">
            <span>Customer</span>
            <span className="text-muted-foreground/30">&middot;</span>
            <span>5 connected sources</span>
          </div>

          {/* New entity notification */}
          <div className="mt-4 rounded-lg border border-emerald-500/30 bg-emerald-500/5 p-4">
            <div className="flex items-center gap-2.5">
              <BrandHubSpot size={14} />
              <span className="text-sm font-semibold text-foreground">
                HubSpot Contact linked automatically
              </span>
              <span className="rounded-full border border-emerald-500/40 bg-emerald-500/10 px-1.5 py-px text-[6.5px] font-semibold text-emerald-600">
                NEW
              </span>
            </div>
            <p className="mt-1.5 text-xs text-muted-foreground">
              Matched via email address &mdash; sarah.chen@acme.co
            </p>
          </div>

          {/* Connected Records */}
          <div className="mt-6">
            <span className="font-display text-xs tracking-[0.05em] text-muted-foreground/50 uppercase">
              Connected Records
            </span>
            <div className="mt-3 grid grid-cols-5 gap-3">
              {CONNECTED_RECORDS.map((record) => (
                <div
                  key={record.platform}
                  className="rounded-lg border border-border bg-card p-4"
                  style={{ minHeight: 120 }}
                >
                  {record.icon}
                  <p className="mt-2 text-xs font-semibold text-foreground">{record.platform}</p>
                  <p className="mt-1.5 text-xs text-muted-foreground">
                    <span className="font-semibold text-foreground">{record.metric1Value}</span>{' '}
                    {record.metric1Label}
                  </p>
                  <p className="mt-0.5 text-xs text-muted-foreground/60">
                    {record.metric2}
                    {record.isNew && <span className="ml-1.5 text-emerald-600">NEW</span>}
                  </p>
                </div>
              ))}
            </div>
          </div>

          {/* Recent Activity */}
          <div className="mt-6">
            <span className="font-display text-xs tracking-[0.05em] text-muted-foreground/50 uppercase">
              Recent Activity
            </span>
            <div className="mt-4 flex flex-col gap-0">
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
                        event.titleColor ?? 'text-foreground',
                      )}
                    >
                      {event.title}
                    </p>
                    <p className="mt-0.5 flex items-center gap-2 font-display text-xs leading-relaxed tracking-[0.02em] text-muted-foreground/60 uppercase">
                      {event.detail}
                      {event.isNew && (
                        <span className="rounded-full border border-emerald-500/40 bg-emerald-500/10 px-1.5 py-px text-[6.5px] font-semibold text-emerald-600 normal-case">
                          NEW
                        </span>
                      )}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
