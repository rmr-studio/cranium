'use client';

import { GlowBorder } from '@/components/ui/glow-border';
import { cn } from '@/lib/utils';
import type { LucideIcon } from 'lucide-react';
import {
  ChevronDown,
  DollarSign,
  File,
  HeartHandshake,
  Layers,
  ListFilter,
  PawPrint,
  SquareUser,
  UserPen,
} from 'lucide-react';
import { motion } from 'motion/react';
import { inViewProps, useAnimateOnMount } from '../animate-context';

const planStyles = {
  Pro: 'bg-[#F9B7B7]/15 text-[#F9B7B7]',
  Enterprise: 'bg-[#A0D0AC]/15 text-[#A0D0AC]',
} as const;

const revenueStyles = {
  '10k - 20k': 'bg-[#EB9393]/15 text-[#EB9393]',
  '5k - 10k': 'bg-[#AEA7E0]/15 text-[#AEA7E0]',
  '3k - 5k': 'bg-[#F882E8]/15 text-[#F882E8]',
  '20k - 50k': 'bg-[#9ADA6A]/15 text-[#9ADA6A]',
} as const;

interface AccountRow {
  name: string;
  plan: keyof typeof planStyles;
  revenue: keyof typeof revenueStyles;
  interactions: string[];
  more?: number;
}

const accounts: AccountRow[] = [
  {
    name: 'Scalegrid AI',
    plan: 'Enterprise',
    revenue: '5k - 10k',
    interactions: [
      'Billing charged twice on plan upgrade',
      'Q3 contract renewal',
      'Data export returning empty CSV',
    ],
    more: 5,
  },
  {
    name: 'Vaultstack',
    plan: 'Enterprise',
    revenue: '5k - 10k',
    interactions: ['Production integration dropping events'],
  },
  {
    name: 'ScaleGrid AI',
    plan: 'Pro',
    revenue: '3k - 5k',
    interactions: ['Account locked out', 'Add Xero as a native integration'],
    more: 2,
  },
  {
    name: 'Brightspark',
    plan: 'Pro',
    revenue: '10k - 20k',
    interactions: ['API rate limiting causing failed syncs'],
  },
  {
    name: 'Kinetic Cloud',
    plan: 'Enterprise',
    revenue: '5k - 10k',
    interactions: [
      'Webhook delivery failing silently',
      'Technical deep-dive: API integrations',
      'Request to change billing contact email',
    ],
  },
  {
    name: 'Apeture Digital',
    plan: 'Enterprise',
    revenue: '10k - 20k',
    interactions: ['Accidentally removed 1,200 entity records'],
  },
  {
    name: 'Meridian Labs',
    plan: 'Pro',
    revenue: '3k - 5k',
    interactions: ['Scheduled workflow automations stopped firing'],
  },
  {
    name: 'Datapulse',
    plan: 'Enterprise',
    revenue: '20k - 50k',
    interactions: [
      'Data sync from Stripe 6 hours behind',
      'Querying across tools is a game-changer',
      'Onboarding kickoff — DataPulse team',
    ],
  },
];

const FilterChip = ({
  icon: Icon,
  children,
  hasChevron,
}: {
  icon?: LucideIcon;
  children: React.ReactNode;
  hasChevron?: boolean;
}) => (
  <div className="flex h-[18px] items-center gap-0.5 rounded border border-border bg-card px-1 shadow-sm">
    {Icon && <Icon className="h-2 w-2 text-muted-foreground" />}
    <span className="text-[10px] whitespace-nowrap text-muted-foreground">{children}</span>
    {hasChevron && <ChevronDown className="h-1.5 w-1.5 shrink-0 text-border" />}
  </div>
);

const Pill = ({ children, colorClass }: { children: React.ReactNode; colorClass: string }) => (
  <span
    className={cn(
      'inline-flex shrink-0 items-center rounded-full px-1.5 py-px text-[8px] font-medium whitespace-nowrap',
      colorClass,
    )}
  >
    {children}
  </span>
);

const TableRow = ({ account, delay, onMount }: { account: AccountRow; delay: number; onMount: boolean }) => (
  <motion.div
    className="flex border-b border-border/40 last:border-b-0"
    initial={{ opacity: 0, x: -6 }}
    {...inViewProps(onMount, { opacity: 1, x: 0 })}
    transition={{ duration: 0.25, delay }}
  >
    {/* Account Name */}
    <div className="flex w-[110px] shrink-0 items-center px-2.5 py-1.5">
      <span className="text-[10px] font-semibold tracking-tight whitespace-nowrap text-foreground">
        {account.name}
      </span>
    </div>

    {/* Plan */}
    <div className="flex w-[72px] shrink-0 items-center px-1.5 py-1.5">
      <Pill colorClass={planStyles[account.plan]}>{account.plan}</Pill>
    </div>

    {/* Revenue */}
    <div className="flex w-[64px] shrink-0 items-center px-1.5 py-1.5">
      <Pill colorClass={revenueStyles[account.revenue]}>{account.revenue}</Pill>
    </div>

    {/* Interactions */}
    <div className="flex-1 space-y-px px-2 py-1.5">
      {account.interactions.map((text, j) => (
        <div key={j} className="flex items-center gap-1">
          <div className="h-1 w-1 shrink-0 rounded-full bg-muted-foreground/40" />
          <span className="text-[9px] leading-tight tracking-tight whitespace-nowrap text-muted-foreground">
            {text}
          </span>
        </div>
      ))}
      {account.more && (
        <span className="pl-2 text-[8px] tracking-tight text-muted-foreground/60">
          +{account.more} more
        </span>
      )}
    </div>
  </motion.div>
);

export const QueryBuilderGraphic = ({ className }: { className?: string }) => {
  const onMount = useAnimateOnMount();
  return (
    <div className={cn('pointer-events-none relative', className)}>
      {/* Query Builder - front, left */}
      <GlowBorder className="relative z-10 w-[480px]">
        <motion.div
          className="w-full rounded-2xl border border-border bg-card p-3 shadow-lg"
          initial={{ opacity: 0, y: 12 }}
          {...inViewProps(onMount, { opacity: 1, y: 0 })}
          transition={{ duration: 0.4 }}
        >
          {/* Type selector */}
          <div className="flex items-center gap-1.5">
            <span className="text-[10px] font-medium text-muted-foreground">Type</span>
            <div className="flex items-center gap-1 rounded border border-border bg-muted px-1.5 py-0.5 shadow-sm">
              <SquareUser className="h-2 w-2 text-muted-foreground" />
              <span className="text-[10px] text-muted-foreground">Account</span>
            </div>
          </div>

          {/* Filter area */}
          <div className="mt-2.5 rounded-lg bg-muted p-2.5">
            {/* Row 1: Plan is Pro or Enterprise */}
            <div className="flex items-center gap-1">
              <FilterChip icon={File} hasChevron>
                Plan
              </FilterChip>
              <FilterChip hasChevron>is</FilterChip>
              <FilterChip icon={Layers} hasChevron>
                Pro
              </FilterChip>
              <FilterChip>or</FilterChip>
              <FilterChip icon={Layers} hasChevron>
                Enterprise
              </FilterChip>
            </div>

            {/* Row 2: MRR > $3,000.00 */}
            <div className="mt-1.5 flex items-center gap-1">
              <FilterChip icon={DollarSign} hasChevron>
                MRR
              </FilterChip>
              <FilterChip hasChevron>&gt;</FilterChip>
              <FilterChip>$3,000.00</FilterChip>
            </div>

            {/* Row 3: Interactions → Support Tickets is Critical */}
            <div className="mt-1.5 flex items-center gap-1">
              <FilterChip icon={UserPen} hasChevron>
                Interactions
              </FilterChip>
              <FilterChip icon={HeartHandshake} hasChevron>
                Support Tickets
              </FilterChip>
              <FilterChip hasChevron>is</FilterChip>
              <FilterChip icon={PawPrint} hasChevron>
                Critical
              </FilterChip>
            </div>
          </div>

          {/* Footer */}
          <div className="mt-2 flex items-center justify-between">
            <div className="flex items-center gap-1 rounded border border-border bg-card px-1.5 py-0.5 shadow-sm">
              <ListFilter className="h-2 w-2 text-muted-foreground" />
              <span className="text-[10px] text-muted-foreground">Add Condition</span>
            </div>
            <span className="text-[10px] font-medium text-muted-foreground">Remove All</span>
          </div>
        </motion.div>
      </GlowBorder>

      {/* Results Table - behind, right, overlapping */}
      <GlowBorder className="-mt-[140px] ml-[170px] w-[560px]">
        <motion.div
          className="w-full overflow-hidden rounded-2xl border border-border bg-card shadow-lg"
          initial={{ opacity: 0, y: 12 }}
          {...inViewProps(onMount, { opacity: 1, y: 0 })}
          transition={{ duration: 0.4, delay: 0.15 }}
        >
          {/* Table Header */}
          <div className="flex border-b border-border/60">
            <div className="w-[110px] shrink-0 px-2.5 py-1.5">
              <span className="text-[9px] font-medium tracking-wide text-muted-foreground/70 uppercase">
                Account
              </span>
            </div>
            <div className="w-[72px] shrink-0 px-1.5 py-1.5">
              <span className="text-[9px] font-medium tracking-wide text-muted-foreground/70 uppercase">
                Plan
              </span>
            </div>
            <div className="w-[64px] shrink-0 px-1.5 py-1.5">
              <span className="text-[9px] font-medium tracking-wide text-muted-foreground/70 uppercase">
                MRR
              </span>
            </div>
            <div className="flex-1 px-2 py-1.5">
              <span className="text-[9px] font-medium tracking-wide text-muted-foreground/70 uppercase">
                Interactions
              </span>
            </div>
          </div>

          {/* Data Rows */}
          {accounts.map((account, i) => (
            <TableRow key={i} account={account} delay={0.2 + i * 0.05} onMount={onMount} />
          ))}
        </motion.div>
      </GlowBorder>
    </div>
  );
};
