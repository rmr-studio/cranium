'use client';

import {
  inViewProps,
  useAnimateOnMount,
} from '@/components/feature-modules/landing/actions/components/animate-context';
import { MockDataTable } from '@/components/feature-modules/landing/time-saved/components/product-showcase/components/mock-data-table';
import { MockIconRail } from '@/components/feature-modules/landing/time-saved/components/product-showcase/components/mock-shell';
import { customerScenario } from '@/components/feature-modules/landing/time-saved/components/product-showcase/scenario-data';
import {
  BrandGorgias,
  BrandInstagram,
  BrandKlaviyo,
  BrandStripe,
} from '@/components/ui/diagrams/brand-icons';
import { GlowBorder } from '@/components/ui/glow-border';
import { WindowControls } from '@/components/ui/window-controls';
import type { LucideIcon } from 'lucide-react';
import {
  ChevronDown,
  CircleDot,
  ListFilter,
  Mail,
  Megaphone,
  Tag,
  TicketCheck,
  Users,
} from 'lucide-react';
import { motion } from 'motion/react';

const FilterChip = ({
  icon: Icon,
  brandIcon,
  children,
  hasChevron,
}: {
  icon?: LucideIcon;
  brandIcon?: React.ReactNode;
  children: React.ReactNode;
  hasChevron?: boolean;
}) => (
  <div className="flex h-[18px] items-center gap-0.5 rounded border border-border bg-card px-1 shadow-sm">
    {brandIcon}
    {Icon && <Icon className="h-2 w-2 text-muted-foreground" />}
    <span className="text-[10px] whitespace-nowrap text-muted-foreground">{children}</span>
    {hasChevron && <ChevronDown className="h-1.5 w-1.5 shrink-0 text-border" />}
  </div>
);

const PlatformLabel = ({ icon, label }: { icon: React.ReactNode; label: string }) => (
  <span className="flex items-center gap-1 text-[9px] font-medium tracking-wide text-muted-foreground/60 uppercase">
    {icon}
    {label}
  </span>
);

export const QueryBuilderGraphic = ({ className }: { className?: string }) => {
  return (
    <>
      <div
        style={{ height: 700 }}
        className="translate relative flex h-full w-full translate-x-24 translate-y-8 scale-60 rounded-xl border border-border shadow-lg sm:translate-y-0 sm:scale-80"
      >
        <MockIconRail />
        <MockDataTable scenario={customerScenario} />
      </div>
      <QuerySection />
    </>
  );
};

export const QuerySection = () => {
  const onMount = useAnimateOnMount();
  return (
    <GlowBorder className="absolute -bottom-4 left-4 z-10 w-[480px] sm:left-32 md:bottom-24 md:left-48 md:scale-160">
      <motion.div
        className="paper-lite w-full rounded-md border border-border bg-card p-3 shadow-lg"
        initial={{ opacity: 0, y: 12 }}
        {...inViewProps(onMount, { opacity: 1, y: 0 })}
        transition={{ duration: 0.4 }}
      >
        <div className="mb-2.5">
          <WindowControls size={6} />
        </div>

        {/* Type selector */}
        <div className="flex items-center gap-1.5">
          <span className="text-[10px] font-medium text-muted-foreground">Type</span>
          <div className="flex items-center gap-1 rounded border border-border bg-muted px-1.5 py-0.5 shadow-sm">
            <Users className="h-2 w-2 text-muted-foreground" />
            <span className="text-[10px] text-muted-foreground">Customer</span>
          </div>
        </div>

        {/* Filter area */}
        <div className="mt-2.5 rounded-lg bg-muted p-2.5">
          {/* Row 1: Acquisition source — Instagram campaign "March Stories" */}
          <div className="flex items-center gap-1">
            <PlatformLabel icon={<BrandInstagram size={10} />} label="Ads" />
            <FilterChip icon={Megaphone} hasChevron>
              Campaign
            </FilterChip>
            <FilterChip hasChevron>is</FilterChip>
            <FilterChip brandIcon={<BrandInstagram size={10} />} hasChevron>
              March Stories
            </FilterChip>
          </div>

          {/* Row 2: Support — Gorgias ticket status is Open */}
          <div className="mt-1.5 flex items-center gap-1">
            <PlatformLabel icon={<BrandGorgias size={10} />} label="Support" />
            <FilterChip icon={TicketCheck} hasChevron>
              Ticket Status
            </FilterChip>
            <FilterChip hasChevron>is</FilterChip>
            <FilterChip icon={CircleDot} hasChevron>
              Open
            </FilterChip>
          </div>

          {/* Row 3: Email — Klaviyo last opened > 30 days */}
          <div className="mt-1.5 flex items-center gap-1">
            <PlatformLabel icon={<BrandKlaviyo size={10} />} label="Email" />
            <FilterChip icon={Mail} hasChevron>
              Last Opened
            </FilterChip>
            <FilterChip hasChevron>&gt;</FilterChip>
            <FilterChip>30 days ago</FilterChip>
          </div>

          {/* Row 4: Revenue — Stripe MRR > $1,000 */}
          <div className="mt-1.5 flex items-center gap-1">
            <PlatformLabel icon={<BrandStripe size={10} />} label="Billing" />
            <FilterChip icon={Tag} hasChevron>
              MRR
            </FilterChip>
            <FilterChip hasChevron>&gt;</FilterChip>
            <FilterChip>$1,000.00</FilterChip>
          </div>
        </div>

        {/* Results preview */}
        <div className="mt-2 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="flex items-center gap-1 rounded border border-border bg-card px-1.5 py-0.5 shadow-sm">
              <ListFilter className="h-2 w-2 text-muted-foreground" />
              <span className="text-[10px] text-muted-foreground">Add Condition</span>
            </div>
            <span className="text-[10px] text-muted-foreground/50">
              across{' '}
              <span className="inline-flex items-center gap-0.5">
                <BrandInstagram size={8} />
                <BrandGorgias size={8} />
                <BrandKlaviyo size={8} />
                <BrandStripe size={8} />
              </span>{' '}
              4 platforms
            </span>
          </div>
          <span className="text-[10px] font-medium text-muted-foreground">Remove All</span>
        </div>
      </motion.div>
    </GlowBorder>
  );
};
