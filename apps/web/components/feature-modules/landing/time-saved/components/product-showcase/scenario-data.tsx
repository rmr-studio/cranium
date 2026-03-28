import {
  Mail,
  MessageSquare,
  SquareDashedMousePointer,
  Users,
} from 'lucide-react';

import {
  BrandFacebook,
  BrandGmail,
  BrandGoogle,
  BrandGorgias,
  BrandHubSpot,
  BrandInstagram,
  BrandIntercom,
  BrandKlaviyo,
  BrandLinkedIn,
  BrandShopify,
  BrandSlack,
  BrandStripe,
} from '@/components/ui/diagrams/brand-icons';
import { EntityChip, PlatformChip } from '@/components/ui/diagrams/brand-ui-primitives';
import type { ShowcaseScenario } from '@/components/feature-modules/landing/time-saved/components/product-showcase/scenario-types';

// ── Scenario 1: Customers ──────────────────────────────────────────────

export const customerScenario: ShowcaseScenario = {
  key: 'customers',
  entityName: 'Customers',
  entityIcon: <Users className="size-3.5" />,
  entityColor: 'text-blue-500',

  tableTitle: 'Customers',
  tableSubtitle: 'Manage your entities and their data',
  searchPlaceholder: 'Search customers...',
  tableHeaders: [
    { icon: <Users className="size-3" />, label: 'Name' },
    { icon: <SquareDashedMousePointer className="size-3" />, label: 'Connected Accounts' },
    { icon: <MessageSquare className="size-3" />, label: 'Support Tickets' },
    { icon: <Mail className="size-3" />, label: 'Email' },
  ],
  tableColTemplate: '20px 1fr 1.2fr 1.2fr 1.3fr',
  tableRows: [
    {
      cells: [
        <span key="n" className="text-sm text-foreground">
          Kenneth Hernandez
        </span>,
        <div key="c" className="flex flex-wrap gap-1">
          <EntityChip icon={<BrandGmail size={12} />} label="Gmail" />
          <EntityChip icon={<BrandLinkedIn size={12} />} label="LinkedIn" />
          <EntityChip icon={<BrandSlack size={12} />} label="Slack" />
        </div>,
        <div key="t" className="flex flex-wrap gap-1">
          <EntityChip icon={<BrandIntercom size={12} />} label="INC-4891" />
          <EntityChip icon={<BrandIntercom size={12} />} label="INC-4722" />
        </div>,
        <span key="e" className="truncate text-sm text-muted-foreground">
          kenneth.h@example.com
        </span>,
      ],
    },
    {
      cells: [
        <span key="n" className="text-sm text-foreground">
          Sarah Chen
        </span>,
        <div key="c" className="flex flex-wrap gap-1">
          <EntityChip icon={<BrandGmail size={12} />} label="Gmail" />
          <EntityChip icon={<BrandHubSpot size={12} />} label="HubSpot" />
        </div>,
        <div key="t" className="flex flex-wrap gap-1">
          <EntityChip icon={<BrandStripe size={12} />} label="pi_3QxM" />
        </div>,
        <span key="e" className="truncate text-sm text-muted-foreground">
          sarah.chen@rivendor.io
        </span>,
      ],
    },
    {
      cells: [
        <span key="n" className="text-sm text-foreground">
          Marcus Webb
        </span>,
        <div key="c" className="flex flex-wrap gap-1">
          <EntityChip icon={<BrandLinkedIn size={12} />} label="LinkedIn" />
          <EntityChip icon={<BrandFacebook size={12} />} label="Facebook" />
        </div>,
        <span key="t" className="text-xs text-muted-foreground/30">
          &mdash;
        </span>,
        <span key="e" className="truncate text-sm text-muted-foreground">
          marcus.webb@stoic.co
        </span>,
      ],
    },
    {
      cells: [
        <span key="n" className="text-sm text-foreground">
          Priya Patel
        </span>,
        <div key="c" className="flex flex-wrap gap-1">
          <EntityChip icon={<BrandGmail size={12} />} label="Gmail" />
          <EntityChip icon={<BrandSlack size={12} />} label="Slack" />
        </div>,
        <div key="t" className="flex flex-wrap gap-1">
          <EntityChip icon={<BrandIntercom size={12} />} label="INC-5012" />
        </div>,
        <span key="e" className="truncate text-sm text-muted-foreground">
          priya.p@quasar.io
        </span>,
      ],
    },
    {
      cells: [
        <span key="n" className="text-sm text-foreground">
          Julian Voet
        </span>,
        <div key="c" className="flex flex-wrap gap-1">
          <EntityChip icon={<BrandLinkedIn size={12} />} label="LinkedIn" />
          <EntityChip icon={<BrandHubSpot size={12} />} label="HubSpot" />
        </div>,
        <div key="t" className="flex flex-wrap gap-1">
          <EntityChip icon={<BrandStripe size={12} />} label="dp_9xKl" />
          <EntityChip icon={<BrandIntercom size={12} />} label="INC-4953" />
        </div>,
        <span key="e" className="truncate text-sm text-muted-foreground">
          julian.voet@nexus.dev
        </span>,
      ],
    },
    {
      cells: [
        <span key="n" className="text-sm text-foreground">
          Elena Rodriguez
        </span>,
        <div key="c" className="flex flex-wrap gap-1">
          <EntityChip icon={<BrandGmail size={12} />} label="Gmail" />
        </div>,
        <span key="t" className="text-xs text-muted-foreground/30">
          &mdash;
        </span>,
        <span key="e" className="truncate text-sm text-muted-foreground">
          elena.r@zenith.com
        </span>,
      ],
    },
    {
      cells: [
        <span key="n" className="text-sm text-foreground">
          David Reyes
        </span>,
        <div key="c" className="flex flex-wrap gap-1">
          <EntityChip icon={<BrandSlack size={12} />} label="Slack" />
          <EntityChip icon={<BrandLinkedIn size={12} />} label="LinkedIn" />
          <EntityChip icon={<BrandGmail size={12} />} label="Gmail" />
        </div>,
        <div key="t" className="flex flex-wrap gap-1">
          <EntityChip icon={<BrandShopify size={12} />} label="ORD-1847" />
        </div>,
        <span key="e" className="truncate text-sm text-muted-foreground">
          david.reyes@terra.co
        </span>,
      ],
    },
    {
      cells: [
        <span key="n" className="text-sm text-foreground">
          Martha Rogers
        </span>,
        <div key="c" className="flex flex-wrap gap-1">
          <EntityChip icon={<BrandHubSpot size={12} />} label="HubSpot" />
          <EntityChip icon={<BrandFacebook size={12} />} label="Facebook" />
        </div>,
        <span key="t" className="text-xs text-muted-foreground/30">
          &mdash;
        </span>,
        <span key="e" className="truncate text-sm text-muted-foreground">
          martha.r@quasar.io
        </span>,
      ],
    },
  ],

  timelineTitle: 'Recent Activity: Sarah Chen',
  timelineBreadcrumb: ['Workspace', 'Entities', 'Customers', 'Sarah Chen'],
  activities: [
    {
      date: 'Mar 18',
      source: 'Gorgias',
      sourceIcon: <BrandGorgias size={14} />,
      title: 'Ticket #4891 opened ("billing discrepancy")',
      detail: 'Status: High Priority / Unassigned',
    },
    {
      date: 'Mar 14',
      source: 'Stripe',
      sourceIcon: <BrandStripe size={14} />,
      title: 'Payment retry failed',
      detail: 'Ref: stmt_1QxMkl',
    },
    {
      date: 'Mar 11',
      source: 'Shopify',
      sourceIcon: <BrandShopify size={14} />,
      title: 'Return initiated (Order #1847)',
      detail: '',
    },
    {
      date: 'Mar 3',
      source: 'Klaviyo',
      sourceIcon: <BrandKlaviyo size={14} />,
      title: 'Opened re-engagement email',
      detail: 'Campaign: "Spring_Retention_24"',
    },
  ],

  kbQuery: (
    <>
      Why are <PlatformChip icon={<BrandInstagram size={11} />} label="Instagram" /> customers
      churning more than <PlatformChip icon={<BrandGoogle size={11} />} label="Google" /> customers
      this quarter?
    </>
  ),
  kbRetrieved: ['Sarah Chen', 'Marcus Webb', 'Priya Patel', 'Julian Voet', 'Elena Rodriguez'],
  kbAnalysedTitle: (
    <>
      342 support tickets for <PlatformChip icon={<BrandInstagram size={11} />} label="Instagram" />{' '}
      cohort
    </>
  ),
  kbAnalysedCards: [
    {
      icon: <BrandIntercom size={12} />,
      title: 'Ticket #4805',
      detail: 'Refund request for double billing..',
    },
    {
      icon: <BrandIntercom size={12} />,
      title: 'Ticket #4846',
      detail: 'Late delivery on international orders...',
    },
  ],
  kbIdentified: (
    <>
      Common patterns in <BrandInstagram size={11} /> Instagram Stories acquisition
    </>
  ),
  kbResponse: (
    <>
      <p className="text-sm leading-relaxed text-foreground/80">
        Analysis of your Q3 dataset indicates a significant variance in retention between
        acquisition channels. The{' '}
        <PlatformChip icon={<BrandInstagram size={11} />} label="Instagram" /> cohort shows a churn
        rate of <strong>14.2%</strong>, whereas the{' '}
        <PlatformChip icon={<BrandGoogle size={11} />} label="Google Search" /> cohort remains
        stable at <strong>6.8%</strong>.
      </p>

      <div className="rounded-md border-l-2 border-foreground/20 pl-3">
        <p className="text-sm font-semibold text-foreground">Expectation Gap</p>
        <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
          High-intent search users (
          <PlatformChip icon={<BrandGoogle size={10} />} label="Google" />) align more closely with
          the product&apos;s core utility, while social-referred users exhibit lower product-market
          fit post-onboarding.
        </p>
      </div>

      <div className="rounded-md border-l-2 border-foreground/20 pl-3">
        <p className="text-sm font-semibold text-foreground">Friction Points</p>
        <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
          Key individual contributors include{' '}
          <span className="rounded bg-muted px-1 text-foreground/70">Sarah Chen</span> ,{' '}
          <span className="rounded bg-muted px-1 text-foreground/70">Marcus Webb</span> ,{' '}
          <span className="rounded bg-muted px-1 text-foreground/70">Priya Patel</span> , and{' '}
          <span className="rounded bg-muted px-1 text-muted-foreground">+15 more</span> who reported
          issues with the mobile landing page loading times.
        </p>
      </div>

      <p className="text-sm leading-relaxed text-foreground/80">
        Specifically, users acquired via{' '}
        <PlatformChip icon={<BrandInstagram size={11} />} label="Instagram Stories ads" /> accounted
        for 19 of 23 documented churn events in the last 14 days. These users typically abandon
        during the &quot;Organization Setup&quot; phase.
      </p>

      <div className="flex items-center gap-1.5 pt-1 text-sm font-medium text-foreground">
        <span>View segment</span>
        <span>&rarr;</span>
      </div>
    </>
  ),
};

