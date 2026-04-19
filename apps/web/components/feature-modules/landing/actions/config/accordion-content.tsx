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
    title: <div>We know where it belongs</div>,
    description: `When a new Stripe charge syncs, Riven already knows which company it belongs to, which support tickets are open on 
  ▎ that account, and what they cost you in ad spend. No manual mapping`,
    content: (
      <EntityDetailShowcase className="absolute -right-12 -bottom-16 sm:-right-48 sm:scale-130 md:bottom-8" />
    ),
  },
  {
    title: <div>Track performance</div>,
    description: `Every action you take in Riven is tracked back to the customers, channels and cohorts it touched. See which moves correlate with better retention and higher LTV. Experiment, learn, repeat. All without the guesswork.`,
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
      <TaggingViewShowcase className="translate-x-8 translate-y-0 translate-y-4 scale-90 sm:translate-x-0 sm:scale-80 md:m-6 md:scale-60 lg:scale-100" />
    ),
  },
];
