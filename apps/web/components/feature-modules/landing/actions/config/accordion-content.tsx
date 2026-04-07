'use client';

import { EntityDetailShowcase } from '@/components/feature-modules/landing/actions/components/diagrams/entity-detail-showcase';
import { QueryBuilderGraphic } from '@/components/feature-modules/landing/actions/components/diagrams/query-builder';
import { TaggingViewShowcase } from '@/components/feature-modules/landing/actions/components/diagrams/tagging-view-showcase';
import { MockDashboard } from '../../dashboard/components/mock-dashboard';

export interface KnowledgeLayerSectionContent {
  title: React.ReactNode;
  description: string;
  content: React.ReactNode;
}

export const ACTION_CONTENT: KnowledgeLayerSectionContent[] = [
  {
    title: <div>One question. Five tools</div>,
    description: `Which customers from our best-performing cohort made a repeat purchase in the last 30 days — and what channel brought them in? Five platforms, hours of manual work. Riven answers it in seconds because your data is already connected. You get the actual list of customers back — click through, flag them, push them to a Klaviyo list.`,
    content: <QueryBuilderGraphic />,
  },
  {
    title: <div>New data finds its home automatically</div>,
    description:
      "When new data syncs, Riven figures out where it belongs. A new Stripe charge gets matched to the right company, linked to their support tickets, ad spend, and everything else. You don't have to map it yourself.",
    content: (
      <EntityDetailShowcase className="dark absolute -right-12 -bottom-16 sm:-right-48 sm:scale-130 md:bottom-8" />
    ),
  },
  {
    title: <div>Track performance</div>,
    description: `All actions are tracked and attributed back to the right customers, channels, and cohorts. See which actions correlate with better retention, higher LTV, and more. Keep an eye on how your most important metrics are impacted by the actions you take, experiment and learn what moves the needle for your business without the guesswork.`,
    content: (
      <MockDashboard className="translate-x-12 translate-x-48! translate-y-12 scale-120 sm:translate-x-32 sm:translate-y-24 md:scale-140! lg:translate-y-0 xl:translate-x-64!" />
    ),
  },
  {
    title: <div>Tag it. Flag it. Bag it.</div>,
    description: `See something in an analytics view or a query result? Tag it. "At Risk." "High Value." "Needs Follow-up."
  Tags follow the record everywhere and are pushed to the relevant tools, so you won't lose the thread. Tracked something last tuesday? Riven tracks what happened and when. Four re-engaged. Two churned anyway.
  Seventeen still at risk. No spreadsheet required.`,
    content: (
      <TaggingViewShowcase className="dark translate-x-8 translate-y-0 translate-y-4 scale-90 sm:translate-x-0 sm:scale-80 md:m-6 md:scale-60 lg:scale-100" />
    ),
  },
];
