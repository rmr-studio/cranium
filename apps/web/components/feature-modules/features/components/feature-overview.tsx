import { Section } from '@/components/ui/section';
import { VisualAccordionSection } from './feature-accordion';

export const FeaturesOverview = () => {
  return (
    <Section className="pb-0!" size={24}>
      <div className="content-container">
        <section className="mx-auto mb-12 flex max-w-xl flex-col gap-8 text-center font-serif text-3xl leading-[1.1] font-normal tracking-tight text-content/65 md:max-w-4xl md:text-4xl lg:max-w-5xl lg:text-5xl">
          <article>
            <span className="text-primary italic">A true focus on structural freedom.</span>{' '}
            <span className="text-content/65">
              Our data models and relationships adapt to how you work, not the other way around.
              Because your business is unique, so your platform should be too.
            </span>
          </article>
          <article>
            All unified within an <span className="text-primary">AI powered knowledge base. </span>
            Providing you with reasoning and insights across your entire business, not just one
            corner of it.
          </article>
        </section>

        <article id="features">
          <VisualAccordionSection />
        </article>
      </div>
    </Section>
  );
};
