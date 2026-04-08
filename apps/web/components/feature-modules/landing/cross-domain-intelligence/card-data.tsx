import {
  ApiUsageIcon,
  CalendarIcon,
  ChangelogIcon,
  ChecklistIcon,
  ClockIcon,
  CompanyIcon,
  FeatureUsageIcon,
  GmailIcon,
  GoogleAdsIcon,
  InfrastructureIcon,
  IntegrationIcon,
  IntercomIcon,
  InvoiceIcon,
  SalesPipelineIcon,
  StripeIcon,
  TeamMemberIcon,
} from '@/components/feature-modules/landing/cross-domain-intelligence/icons';

export interface EntityDef {
  icon: React.ReactNode;
  label: string;
}

export interface InsightCard {
  title: string;
  body: React.ReactNode;
  entities: EntityDef[];
}

export const INSIGHT_CARDS: InsightCard[] = [
  {
    title: 'Retention pattern detection',
    body: (
      <>
        Your top-performing cohort — <b>organic search signups</b> — retains at{' '}
        <b style={{ color: '#5BA6A8' }}>93%</b> after 90 days, vs <b style={{ color: '#A83832' }}>71%</b>{' '}
        for paid social. They complete onboarding <b style={{ color: '#5BA6A8' }}>2.4x</b> faster,
        file <b>60% fewer</b> support tickets, and average <b>$284 LTV</b> — the signal to double
        down on.
      </>
    ),
    entities: [
      { icon: <CompanyIcon size={13} />, label: 'Company' },
      { icon: <StripeIcon size={13} />, label: 'Subscription' },
      { icon: <IntercomIcon size={13} />, label: 'Support Ticket' },
      { icon: <FeatureUsageIcon size={13} />, label: 'Feature Usage' },
      { icon: <CalendarIcon size={13} />, label: 'Subscription Renewal' },
    ],
  },
  {
    title: 'Acquisition channel profitability',
    body: (
      <>
        Customers acquired through <b>paid search</b> cost{' '}
        <b style={{ color: '#A83832' }}>2.8x</b> more to support in their first 60 days than
        organic signups, but their average contract value is only{' '}
        <b style={{ color: '#5BA6A8' }}>1.3x</b> higher. Your effective CAC for paid search is{' '}
        <b>$340</b> when accounting for support overhead — <b style={{ color: '#A83832' }}>62% higher</b> than
        what Google Ads reports alone.
      </>
    ),
    entities: [
      { icon: <CompanyIcon size={13} />, label: 'Company' },
      { icon: <GoogleAdsIcon size={13} />, label: 'Marketing Campaign' },
      { icon: <IntercomIcon size={13} />, label: 'Support Ticket' },
      { icon: <StripeIcon size={13} />, label: 'Subscription' },
      { icon: <ClockIcon size={13} />, label: 'Team Time Log' },
    ],
  },
  {
    title: 'Pricing tier misalignment',
    body: (
      <>
        <b>14 accounts</b> on the Pro plan are consistently exceeding Enterprise-level usage
        thresholds — averaging <b style={{ color: '#A83832' }}>3.2x</b> the API call limit and{' '}
        <b style={{ color: '#A83832' }}>2.1x</b> the seat count. Combined, these represent{' '}
        <b>$47,600</b> in unrealised annual upgrade revenue. None have been contacted by sales in the
        last <b>90 days</b>.
      </>
    ),
    entities: [
      { icon: <CompanyIcon size={13} />, label: 'Company' },
      { icon: <StripeIcon size={13} />, label: 'Subscription' },
      { icon: <FeatureUsageIcon size={13} />, label: 'Feature Usage' },
      { icon: <ApiUsageIcon size={13} />, label: 'API Usage Metrics' },
      { icon: <GmailIcon size={13} />, label: 'Contact Activity' },
      { icon: <SalesPipelineIcon size={13} />, label: 'Sales Pipeline' },
    ],
  },
  {
    title: 'Internal bottleneck detection',
    body: (
      <>
        <b>Sarah Chen</b> has been assigned <b style={{ color: '#A83832' }}>74%</b> of all critical
        support tickets this month across 8 accounts. Her average resolution time has increased from{' '}
        <b style={{ color: '#5BA6A8' }}>2.1 hours</b> to{' '}
        <b style={{ color: '#A83832' }}>6.8 hours</b> over the past 3 weeks. 4 of her currently
        open tickets are linked to accounts with renewals in the next <b>14 days</b>.
      </>
    ),
    entities: [
      { icon: <TeamMemberIcon size={13} />, label: 'Team Member' },
      { icon: <IntercomIcon size={13} />, label: 'Support Ticket' },
      { icon: <CalendarIcon size={13} />, label: 'Subscription Renewal' },
      { icon: <CompanyIcon size={13} />, label: 'Company' },
      { icon: <CalendarIcon size={13} />, label: 'Calendar Events' },
    ],
  },
  {
    title: 'Onboarding success signals',
    body: (
      <>
        Accounts that connect <b>at least one integration within 7 days</b> retain at{' '}
        <b style={{ color: '#5BA6A8' }}>94%</b> after 60 days. Of this month&apos;s 12 new accounts,{' '}
        <b>7 have already hit this milestone</b>. The remaining 5 haven&apos;t connected yet — two
        are annual contracts worth a combined <b>$31,200</b>. Nudge them now while the pattern still
        holds.
      </>
    ),
    entities: [
      { icon: <CompanyIcon size={13} />, label: 'Company' },
      { icon: <ChecklistIcon size={13} />, label: 'Onboarding Checklist' },
      { icon: <IntegrationIcon size={13} />, label: 'Integration Log' },
      { icon: <StripeIcon size={13} />, label: 'Subscription' },
      { icon: <IntercomIcon size={13} />, label: 'Support Ticket' },
      { icon: <GmailIcon size={13} />, label: 'Email Thread' },
    ],
  },
  {
    title: 'Vendor cost anomaly',
    body: (
      <>
        Your total spend across Intercom, Slack, and AWS has increased{' '}
        <b style={{ color: '#A83832' }}>34%</b> this quarter while active customer count grew only{' '}
        <b style={{ color: '#5BA6A8' }}>8%</b>. The primary driver is a{' '}
        <b style={{ color: '#A83832' }}>3x spike</b> in Intercom message volume tied to 6 accounts
        that were migrated to a new API version last month. Estimated unnecessary cost:{' '}
        <b>$2,400/mo</b>.
      </>
    ),
    entities: [
      { icon: <InvoiceIcon size={13} />, label: 'Invoice' },
      { icon: <IntegrationIcon size={13} />, label: 'Integration Health' },
      { icon: <IntercomIcon size={13} />, label: 'Support Ticket' },
      { icon: <CompanyIcon size={13} />, label: 'Company' },
      { icon: <InfrastructureIcon size={13} />, label: 'Infrastructure' },
      { icon: <ChangelogIcon size={13} />, label: 'Product Changelog' },
    ],
  },
];
