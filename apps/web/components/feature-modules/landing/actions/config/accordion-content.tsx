'use client';

import { EntityDetailShowcase } from '@/components/feature-modules/landing/actions/components/diagrams/entity-detail-showcase';
import { QueryBuilderGraphic } from '@/components/feature-modules/landing/actions/components/diagrams/query-builder';
import { RulesEngineGraphic } from '@/components/feature-modules/landing/actions/components/diagrams/rules-engine';
import { TaggingViewShowcase } from '@/components/feature-modules/landing/actions/components/diagrams/tagging-view-showcase';
import { GlowBorder } from '@/components/ui/glow-border';
import { DashboardPromptInput } from '../../hero/components/dashboard/mock-dashboard';
import { MockKnowledgePanel } from '../../time-saved/components/product-showcase/components/mock-knowledge-panel';
import { customerScenario } from '../../time-saved/components/product-showcase/scenario-data';

export interface KnowledgeLayerSectionContent {
  title: React.ReactNode;
  description: string;
  content: React.ReactNode;
}

export const ACTION_CONTENT: KnowledgeLayerSectionContent[] = [
  {
    title: <div>One question. Five tools</div>,
    description: `Which customers from our March Instagram campaign have an open support ticket and haven't opened an
  email in 30 days? 5 Platforms, hours of manual work. We answer it in seconds
  because your data is already connected. You get the actual list of customers back — click through, flag
  them, push them to a Klaviyo list.`,
    content: <QueryBuilderGraphic />,
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
  {
    title: <div>Set Rules. Get Morning updates.</div>,
    description: `Tell Riven what to watch. "Alert me when Instagram churn crosses 10%." "Flag when the at-risk segment grows
  by more than 10 in a week." Rules run overnight and results land in your morning queue.`,
    content: <RulesEngineGraphic />,
  },
  {
    title: <div>Understand your data. Ask anything.</div>,
    description:
      'Ask a question in plain English that touches customers, revenue, support, and product usage at the same time. No exports, no cross-referencing. You get an actual answer back in seconds. The kind of thing that used to eat a whole morning.',
    content: (
      <>
        <GlowBorder className="dark absolute bottom-8 left-8 z-30 w-full max-w-xl rounded-lg">
          <DashboardPromptInput className="glass-panel mt-0 rounded-lg backdrop-blur-lg" />
        </GlowBorder>
        <MockKnowledgePanel
          scenario={customerScenario}
          className="dark relative hidden scale-80 md:translate-x-80 lg:block"
        />
      </>
    ),
  },
  {
    title: <div>New data finds its home automatically</div>,
    description:
      "When new data syncs, Riven figures out where it belongs. A new Stripe charge gets matched to the right company, linked to their support tickets, ad spend, and everything else. You don't have to map it yourself.",
    content: (
      <EntityDetailShowcase className="dark absolute -right-12 -bottom-16 sm:-right-48 sm:scale-130 md:bottom-8" />
    ),
  },
];
