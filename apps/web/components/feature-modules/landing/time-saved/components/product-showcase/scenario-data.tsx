import {
  Activity,
  CreditCard,
  Mail,
  MessageSquare,
  Repeat,
  SquareDashedMousePointer,
  Ticket,
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
import { EntityChip, PlatformChip, StatusDot } from '@/components/ui/diagrams/brand-ui-primitives';
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

// ── Scenario 2: Subscriptions ──────────────────────────────────────────

export const subscriptionScenario: ShowcaseScenario = {
  key: 'subscriptions',
  entityName: 'Subscriptions',
  entityIcon: <Repeat className="size-3.5" />,
  entityColor: 'text-green-500',

  tableTitle: 'Subscriptions',
  tableSubtitle: 'Track subscription lifecycle and revenue',
  searchPlaceholder: 'Search subscriptions...',
  tableHeaders: [
    { icon: <Repeat className="size-3" />, label: 'Plan Name' },
    { icon: <Activity className="size-3" />, label: 'Status' },
    { icon: <CreditCard className="size-3" />, label: 'MRR ($)' },
    { icon: <Mail className="size-3" />, label: 'Renewal Date' },
  ],
  tableColTemplate: '20px 1fr 1fr 0.8fr 1fr',
  tableRows: [
    {
      cells: [
        <span key="n" className="text-sm text-foreground">
          Pro Monthly — Acme Corp
        </span>,
        <StatusDot key="s" color="oklch(0.55 0.15 145)" label="Active" />,
        <span key="m" className="text-sm text-foreground/80 tabular-nums">
          $2,400
        </span>,
        <span key="d" className="text-sm text-muted-foreground">
          Apr 12, 2026
        </span>,
      ],
    },
    {
      cells: [
        <span key="n" className="text-sm text-foreground">
          Enterprise — Nexus Dev
        </span>,
        <StatusDot key="s" color="oklch(0.55 0.15 145)" label="Active" />,
        <span key="m" className="text-sm text-foreground/80 tabular-nums">
          $8,900
        </span>,
        <span key="d" className="text-sm text-muted-foreground">
          May 1, 2026
        </span>,
      ],
    },
    {
      cells: [
        <span key="n" className="text-sm text-foreground">
          Pro Monthly — Stoic Co
        </span>,
        <StatusDot key="s" color="oklch(0.577 0.245 27.325)" label="Churned" />,
        <span key="m" className="text-sm text-foreground/80 tabular-nums">
          $0
        </span>,
        <span key="d" className="text-sm text-muted-foreground">
          &mdash;
        </span>,
      ],
    },
    {
      cells: [
        <span key="n" className="text-sm text-foreground">
          Starter — Quasar IO
        </span>,
        <StatusDot key="s" color="oklch(0.75 0.15 75)" label="Trial" />,
        <span key="m" className="text-sm text-foreground/80 tabular-nums">
          $0
        </span>,
        <span key="d" className="text-sm text-muted-foreground">
          Mar 28, 2026
        </span>,
      ],
    },
    {
      cells: [
        <span key="n" className="text-sm text-foreground">
          Enterprise — Zenith
        </span>,
        <StatusDot key="s" color="oklch(0.55 0.15 145)" label="Active" />,
        <span key="m" className="text-sm text-foreground/80 tabular-nums">
          $12,500
        </span>,
        <span key="d" className="text-sm text-muted-foreground">
          Jun 15, 2026
        </span>,
      ],
    },
    {
      cells: [
        <span key="n" className="text-sm text-foreground">
          Pro Annual — Terra Co
        </span>,
        <StatusDot key="s" color="oklch(0.55 0.15 145)" label="Active" />,
        <span key="m" className="text-sm text-foreground/80 tabular-nums">
          $1,800
        </span>,
        <span key="d" className="text-sm text-muted-foreground">
          Nov 3, 2026
        </span>,
      ],
    },
    {
      cells: [
        <span key="n" className="text-sm text-foreground">
          Starter — Rivendor IO
        </span>,
        <StatusDot key="s" color="oklch(0.75 0.15 75)" label="Trial" />,
        <span key="m" className="text-sm text-foreground/80 tabular-nums">
          $0
        </span>,
        <span key="d" className="text-sm text-muted-foreground">
          Apr 2, 2026
        </span>,
      ],
    },
    {
      cells: [
        <span key="n" className="text-sm text-foreground">
          Pro Monthly — Helix Labs
        </span>,
        <StatusDot key="s" color="oklch(0.577 0.245 27.325)" label="Churned" />,
        <span key="m" className="text-sm text-foreground/80 tabular-nums">
          $0
        </span>,
        <span key="d" className="text-sm text-muted-foreground">
          &mdash;
        </span>,
      ],
    },
  ],

  timelineTitle: 'Recent Activity: Pro Monthly — Acme Corp',
  timelineBreadcrumb: ['Workspace', 'Entities', 'Subscriptions', 'Acme Corp'],
  activities: [
    {
      date: 'Mar 19',
      source: 'Stripe',
      sourceIcon: <BrandStripe size={14} />,
      title: 'Subscription renewed successfully',
      detail: 'Amount: $2,400 / Monthly',
    },
    {
      date: 'Mar 15',
      source: 'Intercom',
      sourceIcon: <BrandIntercom size={14} />,
      title: 'Usage limit reached (85%)',
      detail: 'Customer notified via in-app message',
    },
    {
      date: 'Mar 10',
      source: 'Stripe',
      sourceIcon: <BrandStripe size={14} />,
      title: 'Upgrade to Enterprise initiated',
      detail: 'Pending approval from account owner',
    },
    {
      date: 'Mar 4',
      source: 'Slack',
      sourceIcon: <BrandSlack size={14} />,
      title: 'Payment method updated',
      detail: 'Visa ending in 4242 replaced',
    },
  ],

  kbQuery: <>Which subscription tiers have the highest expansion revenue this quarter?</>,
  kbRetrieved: ['Acme Corp', 'Nexus Dev', 'Zenith', 'Terra Co', 'Helix Labs'],
  kbAnalysedTitle: (
    <>
      87 upgrade events across <PlatformChip icon={<BrandStripe size={11} />} label="Stripe" />{' '}
      billing data
    </>
  ),
  kbAnalysedCards: [
    {
      icon: <BrandStripe size={12} />,
      title: 'sub_1RxPro',
      detail: 'Pro → Enterprise upgrade, $6,500 expansion..',
    },
    {
      icon: <BrandIntercom size={12} />,
      title: 'Ticket #5102',
      detail: 'Seat limit increase request for 25+ users...',
    },
  ],
  kbIdentified: (
    <>
      Expansion pattern in <BrandStripe size={11} /> Pro → Enterprise upgrades
    </>
  ),
  kbResponse: (
    <>
      <p className="text-sm leading-relaxed text-foreground/80">
        Analysis of Q1 billing data shows that{' '}
        <PlatformChip icon={<BrandStripe size={11} />} label="Pro Monthly" /> subscriptions account
        for <strong>72%</strong> of all expansion revenue, with an average upgrade value of{' '}
        <strong>$4,200/mo</strong>. Enterprise conversions from Pro tier outpace Starter upgrades by{' '}
        <strong>3.8x</strong>.
      </p>

      <div className="rounded-md border-l-2 border-foreground/20 pl-3">
        <p className="text-sm font-semibold text-foreground">Upgrade Catalyst</p>
        <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
          Teams hitting the 80% usage threshold on{' '}
          <PlatformChip icon={<BrandStripe size={10} />} label="Pro" /> plans convert within 14 days
          at a rate of <strong>34%</strong>. Usage-based nudges via{' '}
          <PlatformChip icon={<BrandIntercom size={10} />} label="Intercom" /> drive 2.1x higher
          conversion than email-only outreach.
        </p>
      </div>

      <div className="rounded-md border-l-2 border-foreground/20 pl-3">
        <p className="text-sm font-semibold text-foreground">Revenue Concentration</p>
        <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
          Top expansion accounts include{' '}
          <span className="rounded bg-muted px-1 text-foreground/70">Acme Corp</span> ,{' '}
          <span className="rounded bg-muted px-1 text-foreground/70">Nexus Dev</span> ,{' '}
          <span className="rounded bg-muted px-1 text-foreground/70">Zenith</span> , and{' '}
          <span className="rounded bg-muted px-1 text-muted-foreground">+9 more</span> who upgraded
          after exceeding seat or storage limits.
        </p>
      </div>

      <p className="text-sm leading-relaxed text-foreground/80">
        Notably, accounts referred through{' '}
        <PlatformChip icon={<BrandSlack size={11} />} label="Slack Connect" /> channels show 2.4x
        higher lifetime expansion compared to organic signups. These teams typically upgrade during
        the &quot;Team Onboarding&quot; phase within the first 45 days.
      </p>

      <div className="flex items-center gap-1.5 pt-1 text-sm font-medium text-foreground">
        <span>View expansion report</span>
        <span>&rarr;</span>
      </div>
    </>
  ),
};

// ── Scenario 3: Support Tickets ────────────────────────────────────────

export const supportTicketScenario: ShowcaseScenario = {
  key: 'support-tickets',
  entityName: 'Support Tickets',
  entityIcon: <Ticket className="size-3.5" />,
  entityColor: 'text-orange-500',

  tableTitle: 'Support Tickets',
  tableSubtitle: 'Track and resolve customer issues',
  searchPlaceholder: 'Search tickets...',
  tableHeaders: [
    { icon: <Ticket className="size-3" />, label: 'Ticket ID' },
    { icon: <Users className="size-3" />, label: 'Customer' },
    { icon: <Activity className="size-3" />, label: 'Priority' },
    { icon: <MessageSquare className="size-3" />, label: 'Status' },
  ],
  tableColTemplate: '20px 0.8fr 1fr 0.8fr 0.8fr',
  tableRows: [
    {
      cells: [
        <span key="n" className="font-mono text-sm text-foreground">
          INC-4891
        </span>,
        <span key="c" className="text-sm text-foreground">
          Kenneth Hernandez
        </span>,
        <StatusDot key="p" color="oklch(0.577 0.245 27.325)" label="High" />,
        <StatusDot key="s" color="oklch(0.75 0.15 75)" label="Open" />,
      ],
    },
    {
      cells: [
        <span key="n" className="font-mono text-sm text-foreground">
          INC-5012
        </span>,
        <span key="c" className="text-sm text-foreground">
          Priya Patel
        </span>,
        <StatusDot key="p" color="oklch(0.577 0.245 27.325)" label="High" />,
        <StatusDot key="s" color="oklch(0.585 0.204 277.12)" label="In Progress" />,
      ],
    },
    {
      cells: [
        <span key="n" className="font-mono text-sm text-foreground">
          INC-4953
        </span>,
        <span key="c" className="text-sm text-foreground">
          Julian Voet
        </span>,
        <StatusDot key="p" color="oklch(0.75 0.15 75)" label="Med" />,
        <StatusDot key="s" color="oklch(0.585 0.204 277.12)" label="In Progress" />,
      ],
    },
    {
      cells: [
        <span key="n" className="font-mono text-sm text-foreground">
          INC-4722
        </span>,
        <span key="c" className="text-sm text-foreground">
          Kenneth Hernandez
        </span>,
        <StatusDot key="p" color="oklch(0.55 0.15 145)" label="Low" />,
        <StatusDot key="s" color="oklch(0.55 0.15 145)" label="Resolved" />,
      ],
    },
    {
      cells: [
        <span key="n" className="font-mono text-sm text-foreground">
          INC-5044
        </span>,
        <span key="c" className="text-sm text-foreground">
          Sarah Chen
        </span>,
        <StatusDot key="p" color="oklch(0.577 0.245 27.325)" label="High" />,
        <StatusDot key="s" color="oklch(0.75 0.15 75)" label="Open" />,
      ],
    },
    {
      cells: [
        <span key="n" className="font-mono text-sm text-foreground">
          INC-4998
        </span>,
        <span key="c" className="text-sm text-foreground">
          Elena Rodriguez
        </span>,
        <StatusDot key="p" color="oklch(0.75 0.15 75)" label="Med" />,
        <StatusDot key="s" color="oklch(0.55 0.15 145)" label="Resolved" />,
      ],
    },
    {
      cells: [
        <span key="n" className="font-mono text-sm text-foreground">
          INC-5067
        </span>,
        <span key="c" className="text-sm text-foreground">
          David Reyes
        </span>,
        <StatusDot key="p" color="oklch(0.55 0.15 145)" label="Low" />,
        <StatusDot key="s" color="oklch(0.585 0.204 277.12)" label="In Progress" />,
      ],
    },
    {
      cells: [
        <span key="n" className="font-mono text-sm text-foreground">
          INC-5081
        </span>,
        <span key="c" className="text-sm text-foreground">
          Martha Rogers
        </span>,
        <StatusDot key="p" color="oklch(0.75 0.15 75)" label="Med" />,
        <StatusDot key="s" color="oklch(0.75 0.15 75)" label="Open" />,
      ],
    },
  ],

  timelineTitle: 'Recent Activity: Ticket #4891 — Kenneth Hernandez',
  timelineBreadcrumb: ['Workspace', 'Entities', 'Support Tickets', 'INC-4891'],
  activities: [
    {
      date: 'Mar 18',
      source: 'Intercom',
      sourceIcon: <BrandIntercom size={14} />,
      title: 'Ticket created via Intercom',
      detail: 'Subject: "Billing discrepancy on last invoice"',
    },
    {
      date: 'Mar 18',
      source: 'Slack',
      sourceIcon: <BrandSlack size={14} />,
      title: 'Auto-assigned to Billing team',
      detail: 'Rule: "billing" keyword → #billing-support',
    },
    {
      date: 'Mar 19',
      source: 'Gmail',
      sourceIcon: <BrandGmail size={14} />,
      title: 'Customer replied with invoice screenshot',
      detail: 'Attachment: invoice_mar2026.pdf',
    },
    {
      date: 'Mar 19',
      source: 'Intercom',
      sourceIcon: <BrandIntercom size={14} />,
      title: 'Escalated to P1 — SLA breach in 4h',
      detail: 'Escalation: auto-triggered by response time',
    },
  ],

  kbQuery: <>What are the most common support themes this month?</>,
  kbRetrieved: ['INC-4891', 'INC-5012', 'INC-4953', 'INC-5044', 'INC-5067'],
  kbAnalysedTitle: (
    <>
      218 tickets across <PlatformChip icon={<BrandIntercom size={11} />} label="Intercom" /> and{' '}
      <PlatformChip icon={<BrandGmail size={11} />} label="Gmail" /> channels
    </>
  ),
  kbAnalysedCards: [
    {
      icon: <BrandIntercom size={12} />,
      title: 'Cluster: Billing',
      detail: '47 tickets — invoice discrepancies, double charges..',
    },
    {
      icon: <BrandGmail size={12} />,
      title: 'Cluster: Onboarding',
      detail: '31 tickets — setup wizard failures, SSO config...',
    },
  ],
  kbIdentified: (
    <>
      Recurring pattern in <BrandIntercom size={11} /> billing-related escalations
    </>
  ),
  kbResponse: (
    <>
      <p className="text-sm leading-relaxed text-foreground/80">
        Support volume analysis for March shows{' '}
        <PlatformChip icon={<BrandIntercom size={11} />} label="Billing disputes" /> as the dominant
        theme at <strong>47 tickets (21.6%)</strong>, up from 12% last month. Onboarding issues via{' '}
        <PlatformChip icon={<BrandGmail size={11} />} label="Gmail" /> account for{' '}
        <strong>31 tickets (14.2%)</strong>.
      </p>

      <div className="rounded-md border-l-2 border-foreground/20 pl-3">
        <p className="text-sm font-semibold text-foreground">Root Cause — Billing</p>
        <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
          A <PlatformChip icon={<BrandStripe size={10} />} label="Stripe" /> webhook
          misconfiguration on March 8 caused duplicate charge events for 23 accounts. Affected
          customers include{' '}
          <span className="rounded bg-muted px-1 text-foreground/70">Kenneth Hernandez</span> ,{' '}
          <span className="rounded bg-muted px-1 text-foreground/70">Sarah Chen</span> , and{' '}
          <span className="rounded bg-muted px-1 text-muted-foreground">+21 more</span>.
        </p>
      </div>

      <div className="rounded-md border-l-2 border-foreground/20 pl-3">
        <p className="text-sm font-semibold text-foreground">SLA Impact</p>
        <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
          Average first-response time for billing tickets is <strong>6.2 hours</strong>, exceeding
          the 4-hour SLA target. Tickets routed via{' '}
          <PlatformChip icon={<BrandSlack size={10} />} label="Slack" /> auto-assignment resolve{' '}
          <strong>1.8x faster</strong> than manual triage through{' '}
          <PlatformChip icon={<BrandIntercom size={10} />} label="Intercom" /> queue.
        </p>
      </div>

      <p className="text-sm leading-relaxed text-foreground/80">
        Recommendation: Implement <PlatformChip icon={<BrandStripe size={11} />} label="Stripe" />{' '}
        idempotency checks and prioritize billing-tagged tickets in the auto-assignment rules. The
        23 affected accounts should receive proactive refund notifications.
      </p>

      <div className="flex items-center gap-1.5 pt-1 text-sm font-medium text-foreground">
        <span>View ticket analysis</span>
        <span>&rarr;</span>
      </div>
    </>
  ),
};

export const scenarios: ShowcaseScenario[] = [
  customerScenario,
  subscriptionScenario,
  supportTicketScenario,
];
