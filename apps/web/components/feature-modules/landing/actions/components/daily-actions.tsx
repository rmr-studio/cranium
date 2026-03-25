import {
  ShowcaseCard,
  ShowcaseSection,
  type FeatureCard,
} from '@/components/ui/showcase-section';
import { DailyActionAccordion } from '@/components/feature-modules/landing/actions/components/action-accordion';

const FEATURES: FeatureCard[] = [
  {
    title: 'Cross-domain view',
    description: 'Unified lifecycle data from every connected tool in one glance',
  },
  {
    title: 'AI-powered briefs',
    description: 'Your morning brief learns what matters most to your business',
  },
];

export const DailyActions = () => {
  return (
    <ShowcaseSection
      lazyRender
      heading={
        <h2 className="mt-4 font-sans text-3xl text-primary-foreground md:text-4xl lg:text-5xl">
          More Results. <span className="font-serif font-normal italic">Less Tabs</span>
        </h2>
      }
      features={FEATURES}
    >
      <ShowcaseCard>
        <DailyActionAccordion />
      </ShowcaseCard>
    </ShowcaseSection>
  );
};
