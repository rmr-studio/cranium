import { CogIcon, LayoutGrid, Search, Tag } from 'lucide-react';

import {
  BrandGorgias,
  BrandHubSpot,
  BrandIntercom,
  BrandKlaviyo,
  BrandShopify,
  BrandStripe,
} from '@/components/ui/diagrams/brand-icons';
import {
  EntityChip,
  MockBreadcrumb,
  ShowcaseIconRail,
} from '@/components/ui/diagrams/brand-ui-primitives';

// ── Icon Rail Config ────────────────────────────────────────────────

const TAGGING_NAV_ICONS = [
  { icon: <LayoutGrid className="size-5" /> },
  { icon: <LayoutGrid className="size-5" /> },
  { icon: <Tag className="size-5" />, active: true },
  { icon: <Search className="size-5" /> },
  { icon: <CogIcon className="size-5" /> },
];

// ── Tag Pill ────────────────────────────────────────────────────────

function TagPill({
  label,
  variant,
  small,
}: {
  label: string;
  variant: 'at-risk' | 'high-value' | 'follow-up';
  small?: boolean;
}) {
  const styles = {
    'at-risk': 'bg-destructive/10 text-destructive border border-destructive/20',
    'high-value': 'bg-emerald-500/10 text-emerald-600 border border-emerald-500/20',
    'follow-up': 'border bg-yellow-500/10 text-yellow-600 border-yellow-500/20',
  };

  return (
    <span
      className={`inline-block rounded-full ${styles[variant]} ${small ? 'px-2 py-0.5 text-[10px]' : 'px-3 py-1 text-xs'} font-medium`}
    >
      {label}
    </span>
  );
}

// ── Bulk Action Button ──────────────────────────────────────────────

function BulkAction({ label }: { label: string }) {
  return (
    <span className="font-display text-xs tracking-[0.05em] text-foreground/70 uppercase">
      {label}
    </span>
  );
}

// ── Row Data ────────────────────────────────────────────────────────

interface TaggingRow {
  name: string;
  sources: { icon: React.ReactNode; label: string }[];
  ltv: string;
  tags: { label: string; variant: 'at-risk' | 'high-value' | 'follow-up' }[];
  lastActivity: string;
  outcome: string;
  outcomeColor: 'green' | 'destructive' | 'muted';
}

const ROWS: TaggingRow[] = [
  {
    name: 'Sarah Chen',
    sources: [
      { icon: <BrandShopify size={12} />, label: 'Shopify' },
      { icon: <BrandStripe size={12} />, label: 'Stripe' },
    ],
    ltv: '$2,840',
    tags: [
      { label: 'At Risk', variant: 'at-risk' },
      { label: 'High Value', variant: 'high-value' },
    ],
    lastActivity: '3 days ago',
    outcome: 'Re-engaged',
    outcomeColor: 'green',
  },
  {
    name: 'Marcus Webb',
    sources: [
      { icon: <BrandIntercom size={12} />, label: 'Intercom' },
      { icon: <BrandKlaviyo size={12} />, label: 'Klaviyo' },
    ],
    ltv: '$1,200',
    tags: [{ label: 'At Risk', variant: 'at-risk' }],
    lastActivity: '12 days ago',
    outcome: 'Churned',
    outcomeColor: 'destructive',
  },
  {
    name: 'Lena Park',
    sources: [
      { icon: <BrandShopify size={12} />, label: 'Shopify' },
      { icon: <BrandGorgias size={12} />, label: 'Gorgias' },
    ],
    ltv: '$4,100',
    tags: [
      { label: 'At Risk', variant: 'at-risk' },
      { label: 'Needs Follow-up', variant: 'follow-up' },
    ],
    lastActivity: '1 day ago',
    outcome: 'Pending',
    outcomeColor: 'muted',
  },
  {
    name: 'James Liu',
    sources: [
      { icon: <BrandStripe size={12} />, label: 'Stripe' },
      { icon: <BrandHubSpot size={12} />, label: 'HubSpot' },
    ],
    ltv: '$3,600',
    tags: [{ label: 'High Value', variant: 'high-value' }],
    lastActivity: '5 days ago',
    outcome: 'Re-engaged',
    outcomeColor: 'green',
  },
  {
    name: 'Ana Costa',
    sources: [
      { icon: <BrandKlaviyo size={12} />, label: 'Klaviyo' },
      { icon: <BrandIntercom size={12} />, label: 'Intercom' },
    ],
    ltv: '$890',
    tags: [{ label: 'At Risk', variant: 'at-risk' }],
    lastActivity: '8 days ago',
    outcome: 'Pending',
    outcomeColor: 'muted',
  },
  {
    name: 'David Okafor',
    sources: [
      { icon: <BrandShopify size={12} />, label: 'Shopify' },
      { icon: <BrandStripe size={12} />, label: 'Stripe' },
    ],
    ltv: '$5,200',
    tags: [
      { label: 'At Risk', variant: 'at-risk' },
      { label: 'High Value', variant: 'high-value' },
    ],
    lastActivity: '2 days ago',
    outcome: 'Re-engaged',
    outcomeColor: 'green',
  },
  {
    name: 'Kai Tanaka',
    sources: [
      { icon: <BrandGorgias size={12} />, label: 'Gorgias' },
      { icon: <BrandKlaviyo size={12} />, label: 'Klaviyo' },
    ],
    ltv: '$1,800',
    tags: [{ label: 'At Risk', variant: 'at-risk' }],
    lastActivity: '15 days ago',
    outcome: 'Churned',
    outcomeColor: 'destructive',
  },
];

// ── Outcome Text ────────────────────────────────────────────────────

function OutcomeText({
  outcome,
  color,
}: {
  outcome: string;
  color: 'green' | 'destructive' | 'muted';
}) {
  if (color === 'green') {
    return (
      <span className="text-sm font-semibold" style={{ color: 'oklch(0.65 0.17 145)' }}>
        {outcome}
      </span>
    );
  }
  if (color === 'destructive') {
    return <span className="text-sm font-semibold text-destructive">{outcome}</span>;
  }
  return <span className="text-sm text-muted-foreground">{outcome}</span>;
}

// ── Main Export ──────────────────────────────────────────────────────

export function MockTaggingView() {
  return (
    <div
      className="flex overflow-hidden rounded-xl border border-primary/50 shadow-lg"
      style={{ height: 950 }}
    >
      <ShowcaseIconRail icons={TAGGING_NAV_ICONS} />

      {/* Main content */}
      <div className="paper-lite glass-panel relative flex flex-1 flex-col overflow-hidden backdrop-blur-xl">
        {/* Breadcrumb bar */}
        <div className="flex h-12 shrink-0 items-center border-b border-border px-6">
          <MockBreadcrumb items={['Workspace', 'Segments', 'At-Risk Q1']} />
        </div>

        {/* Content area */}
        <div className="flex-1 overflow-y-auto px-8 pt-6 pb-8">
          {/* Header row */}
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-3">
              <h2 className="text-4xl font-normal -tracking-[0.02em] text-foreground">
                At-Risk Q1
              </h2>
              <span className="rounded-full border border-border bg-muted/30 px-3 py-0.5 text-xs text-muted-foreground">
                23 records
              </span>
            </div>
            <div className="flex items-center gap-6">
              <BulkAction label="Tag All" />
              <BulkAction label="Export" />
              <BulkAction label="Push to Klaviyo" />
            </div>
          </div>

          {/* Active tag filters */}
          <div className="mt-4 flex gap-2">
            <TagPill label="At Risk" variant="at-risk" />
            <TagPill label="High Value" variant="high-value" />
            <TagPill label="Needs Follow-up" variant="follow-up" />
          </div>

          {/* Data table */}
          <div className="mt-6">
            {/* Table header */}
            <div className="flex border-b border-border/60 pb-2">
              <span className="w-44 font-display text-xs tracking-wide text-muted-foreground/70 uppercase">
                Name
              </span>
              <span className="w-40 font-display text-xs tracking-wide text-muted-foreground/70 uppercase">
                Source
              </span>
              <span className="w-24 font-display text-xs tracking-wide text-muted-foreground/70 uppercase">
                LTV
              </span>
              <span className="w-48 font-display text-xs tracking-wide text-muted-foreground/70 uppercase">
                Tags
              </span>
              <span className="w-32 font-display text-xs tracking-wide text-muted-foreground/70 uppercase">
                Last Activity
              </span>
              <span className="flex-1 font-display text-xs tracking-wide text-muted-foreground/70 uppercase">
                Outcome
              </span>
            </div>

            {/* Table rows */}
            {ROWS.map((row) => (
              <div key={row.name} className="flex items-center border-b border-border/40 py-3">
                <span className="w-44 text-sm font-semibold text-foreground">{row.name}</span>
                <span className="flex w-40 gap-1">
                  {row.sources.map((s) => (
                    <EntityChip key={s.label} icon={s.icon} label={s.label} />
                  ))}
                </span>
                <span className="w-24 text-sm text-foreground">{row.ltv}</span>
                <span className="flex w-48 gap-1">
                  {row.tags.map((t) => (
                    <TagPill key={t.label} label={t.label} variant={t.variant} small />
                  ))}
                </span>
                <span className="w-32 text-sm text-muted-foreground">{row.lastActivity}</span>
                <span className="flex-1">
                  <OutcomeText outcome={row.outcome} color={row.outcomeColor} />
                </span>
              </div>
            ))}
          </div>

          {/* Summary bar */}
          <div className="mt-4 flex items-center gap-4 text-sm text-muted-foreground">
            <span className="inline-flex items-center gap-1.5">
              <span
                className="inline-block size-2.5 rounded-full"
                style={{ backgroundColor: 'oklch(0.65 0.17 145)' }}
              />
              4 re-engaged
            </span>
            <span className="text-muted-foreground/40">&middot;</span>
            <span className="inline-flex items-center gap-1.5">
              <span className="inline-block size-2.5 rounded-full bg-destructive" />2 churned
            </span>
            <span className="text-muted-foreground/40">&middot;</span>
            <span className="inline-flex items-center gap-1.5">
              <span className="inline-block size-2.5 rounded-full bg-muted-foreground/40" />
              17 still at risk
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
