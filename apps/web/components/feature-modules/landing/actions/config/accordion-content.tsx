'use client';

import { EntityDetailShowcase } from '@/components/feature-modules/landing/actions/components/diagrams/entity-detail-showcase';
import { QueryBuilderGraphic } from '@/components/feature-modules/landing/actions/components/diagrams/query-builder';
import { RulesEngineGraphic } from '@/components/feature-modules/landing/actions/components/diagrams/rules-engine';
import { TaggingViewShowcase } from '@/components/feature-modules/landing/actions/components/diagrams/tagging-view-showcase';
import { GlowBorder } from '@/components/ui/glow-border';
import { DashboardPromptInput } from '../../hero/components/dashboard/mock-dashboard';
import { MockKnowledgePanel } from '../../time-saved/components/product-showcase/components/mock-knowledge-panel';
import { subscriptionScenario } from '../../time-saved/components/product-showcase/scenario-data';

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
      <TaggingViewShowcase className="mx-auto translate-x-8 translate-y-12 sm:translate-x-24 sm:scale-120 xl:translate-x-48 xl:scale-130" />
    ),
  },
  {
    title: <div>Set Rules. Get Morning updates.</div>,
    description: `Tell Riven what to watch. "Alert me when Instagram churn crosses 10%." "Flag when the at-risk segment grows
  by more than 10 in a week." Rules run overnight and results land in your morning queue.`,
    content: (
      <RulesEngineGraphic/>
    ),
  },
  {
    title: <div>Understand your data. Ask anything.</div>,
    description:
      'Query your entire data ecosystem in natural language. No exports, no cross-referencing, no spreadsheets. Ask a question that spans customers, revenue, support, and product usage — and get an answer in seconds. The kind of answer that used to take a morning now takes a sentence.',
    content: (
      <>
        <GlowBorder className="absolute bottom-8 left-8 z-30 w-full max-w-xl">
          <DashboardPromptInput className="paper-lite mt-0" />
        </GlowBorder>
        <MockKnowledgePanel
          scenario={subscriptionScenario}
          className="relative hidden translate-x-32 translate-y-12 scale-80 sm:block md:translate-x-80 lg:scale-100"
        />
      </>
    ),
  },
  {
    title: <div>New data finds its home automatically</div>,
    description:
      'Automatic entity resolution and identity matching means your data model stays up to date without manual intervention. New data synced is connected and linked to existing entities across your ecosystem.',
    content: (
      <EntityDetailShowcase className="absolute -right-12 scale-130 sm:-right-48 sm:-bottom-16 md:bottom-8" />
    ),
  },
];
