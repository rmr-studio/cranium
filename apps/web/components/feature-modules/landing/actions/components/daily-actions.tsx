import { DailyActionAccordion } from '@/components/feature-modules/landing/actions/components/action-accordion';
import { ShowcaseSection, type FeatureCard } from '@/components/ui/showcase-section';

const FEATURES: FeatureCard[] = [
  {
    title: 'One platform',
    description:
      'Keep your tools, ditch the tabs. Perform your most important actions in one place.',
  },
  {
    title: 'Real-time monitoring and results',
    description:
      'See the impact of your actions as it happens, and keep an eye on what matters most.',
  },
];

export const DailyActions = () => {
  return (
    <ShowcaseSection
      className="px-0!"
      heading={
        <h2 className="mt-4 font-serif text-3xl leading-none md:text-4xl lg:px-12 lg:text-6xl">
          More Results. <br/> Fewer Tabs
        </h2>
      }
      features={FEATURES}
    >
      <DailyActionAccordion />
    </ShowcaseSection>
  );
};
