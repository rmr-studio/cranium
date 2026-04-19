import { Section } from '@/components/ui/section';
import { SectionDivider } from '@/components/ui/section-divider';
import { ShaderContainer } from '@/components/ui/shader-container';
import { CohortBehaviourShowcase } from './behaviour-showcase-graphic';

export const CohortBehaviour = () => {
  const gradient = 'images/landing/background-gradient-2.webp';

  return (
    <Section id="churn-retrospectives" size={24}>
      <div className="space-y-10 md:space-y-14">
        <SectionDivider>Cohort Behaviour</SectionDivider>

        <div className="max-w-4xl px-4 sm:px-8 xl:max-w-5xl">
          <h2 className="font-serif text-3xl leading-none tracking-tighter text-primary md:text-4xl lg:text-6xl">
            Your best customers are telling you something.
          </h2>

          <p className="mt-4 max-w-4xl text-sm leading-[1.1] tracking-tight text-content/80 md:text-base">
            They pay more. They stick around. They send their friends. Riven stitches their
            behaviour together across every connected source and shows you what they actually do
            differently. The key moments, the shared signals, the interactions they had. All
            together so you can double down on whatever keeps them coming back.
          </p>
        </div>
      </div>

      <ShaderContainer
        staticImage={gradient}
        staticOnly
        className="z-50 mx-0! rounded-none! p-0 shadow-foreground/40 lg:rounded-lg! lg:shadow-lg"
      >
        <section className="p-4 lg:p-12">
          <div className="pointer-events-none absolute inset-0 z-10 opacity-60 shadow-[inset_20px_0_40px_rgba(0,0,0,0.5),inset_-20px_0_40px_rgba(0,0,0,0.5)] md:shadow-[inset_32px_0_60px_rgba(0,0,0,0.55),inset_-32px_0_60px_rgba(0,0,0,0.25)]" />
          <CohortBehaviourShowcase />
        </section>
      </ShaderContainer>
    </Section>
  );
};
