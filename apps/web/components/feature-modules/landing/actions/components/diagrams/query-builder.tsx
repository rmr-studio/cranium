'use client';

import { MockDataTable } from '@/components/feature-modules/landing/hero/components/showcase/mock-data-table';
import { MockIconRail } from '@/components/feature-modules/landing/hero/components/showcase/mock-shell';
import { customerScenario } from '@/components/feature-modules/landing/hero/components/showcase/scenario-data';
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
      <div className="translate relative flex h-full w-full">
        <div
          className="hidden aspect-video scale-80 border border-border shadow-lg lg:flex"
          style={{ height: 700 }}
        >
          <MockIconRail />
          <MockDataTable scenario={customerScenario} />
        </div>
      </div>
      <QuerySection />
    </>
  );
};

export const QuerySection = () => {
  return (
    <GlowBorder className="absolute bottom-10 left-20 w-[500px] scale-120 sm:top-1/2 sm:left-1/2 sm:-translate-x-1/2 sm:-translate-y-1/2 lg:bottom-40 lg:left-16 lg:translate-x-0 lg:translate-y-0">
      <div className="glass-panel w-full rounded-md border border-border p-3 shadow-lg backdrop-blur-xl">
        <div className="mb-2.5">
          <WindowControls size={6} />
        </div>

        {/* Type selector */}
        <div className="flex items-center gap-1.5">
          <span className="text-[10px] font-medium text-muted-foreground">Type</span>
          <div className="flex items-center gap-1 rounded border border-border bg-card px-1.5 py-0.5 shadow-sm">
            <Users className="h-2 w-2 text-muted-foreground" />
            <span className="text-[10px] text-muted-foreground">Customer</span>
          </div>
        </div>

        {/* Filter area */}
        <div className="mt-2.5 rounded-lg border bg-card/80 p-2.5">
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
      </div>
    </GlowBorder>
  );
};
