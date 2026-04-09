import { DailyActionAccordion } from '@/components/feature-modules/landing/actions/components/action-accordion';
import { ShowcaseSection, type FeatureCard } from '@/components/ui/showcase-section';

const FEATURES: FeatureCard[] = [
  {
    title: 'Your stack, one workspace',
    description: 'Keep your tools, ditch the tabs. Every important action in one place.',
  },
  {
    title: 'See it move',
    description: 'Watch the impact of every action ripple through your numbers in real time.',
  },
];

export const DailyActions = () => {
  return (
    <ShowcaseSection
      className="px-0!"
      heading={
        <h2 className="mt-4 font-serif text-3xl leading-none md:text-4xl lg:px-12 lg:text-6xl">
          More Results. <br /> Fewer Tabs
        </h2>
      }
      features={FEATURES}
    >
      <DailyActionAccordion />
    </ShowcaseSection>
  );
};
