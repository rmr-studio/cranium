import { Mail, MessageSquare, SquareDashedMousePointer, Users } from 'lucide-react';

import type { ShowcaseScenario } from '@/components/feature-modules/landing/hero/components/showcase/scenario-types';
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
      What do <PlatformChip icon={<BrandGoogle size={11} />} label="Google" /> customers do
      differently that makes them retain at 2x the rate of{' '}
      <PlatformChip icon={<BrandInstagram size={11} />} label="Instagram" /> customers?
    </>
  ),
  kbRetrieved: ['Sarah Chen', 'Marcus Webb', 'Priya Patel', 'Julian Voet', 'Elena Rodriguez'],
  kbAnalysedTitle: (
    <>
      Retention patterns for <PlatformChip icon={<BrandGoogle size={11} />} label="Google" /> cohort
      vs <PlatformChip icon={<BrandInstagram size={11} />} label="Instagram" />
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
      What <BrandGoogle size={11} /> Google Search customers do differently
    </>
  ),
  kbResponse: (
    <>
      <p className="text-sm leading-relaxed text-foreground/80">
        Your <PlatformChip icon={<BrandGoogle size={11} />} label="Google Search" /> cohort retains
        at <strong>93.2%</strong> — nearly double the{' '}
        <PlatformChip icon={<BrandInstagram size={11} />} label="Instagram" /> cohort at{' '}
        <strong>85.8%</strong>. Here&apos;s what they do differently.
      </p>

      <div className="rounded-md border-l-2 border-foreground/20 pl-3">
        <p className="text-sm font-semibold text-foreground">Higher Product-Intent at Entry</p>
        <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
          <PlatformChip icon={<BrandGoogle size={10} />} label="Google" /> customers arrive with
          search intent that aligns with your core utility. They complete onboarding at{' '}
          <strong>2.4x</strong> the rate and connect integrations within the first 48 hours.
        </p>
      </div>

      <div className="rounded-md border-l-2 border-foreground/20 pl-3">
        <p className="text-sm font-semibold text-foreground">Repeat Purchase Behaviour</p>
        <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
          Top retained customers include{' '}
          <span className="rounded bg-muted px-1 text-foreground/70">Sarah Chen</span> ,{' '}
          <span className="rounded bg-muted px-1 text-foreground/70">Marcus Webb</span> ,{' '}
          <span className="rounded bg-muted px-1 text-foreground/70">Priya Patel</span> , and{' '}
          <span className="rounded bg-muted px-1 text-muted-foreground">+15 more</span> who average{' '}
          <strong>3.1 orders/month</strong> with a repeat purchase rate of <strong>68%</strong>.
        </p>
      </div>

      <p className="text-sm leading-relaxed text-foreground/80">
        The Google cohort&apos;s 90-day LTV is <strong>$284</strong> vs <strong>$112</strong> for
        Instagram — a <strong>2.5x</strong> gap that widens each month. Doubling down on this
        channel could shift blended retention by 4-6 points.
      </p>

      <div className="flex items-center gap-1.5 pt-1 text-sm font-medium text-foreground">
        <span>View segment</span>
        <span>&rarr;</span>
      </div>
    </>
  ),
};
