import { Section } from '@/components/ui/section';
import { SectionDivider } from '@/components/ui/section-divider';

export function ConnectedEcosystem() {
  return (
    <Section id="connected-ecosystem" size={24}>
      <div className="clamp">
        <div className="space-y-10 md:space-y-14">
          <SectionDivider>
            <div className="flex gap-1">
              <div className="hidden sm:block">One Connected Ecosystem. </div>
              <div>One Connected Business.</div>
            </div>
          </SectionDivider>

          <div className="max-w-4xl mx-auto text-center xl:max-w-6xl">
            <h2 className="text-3xl leading-[1.2] -tracking-[0.02em] text-primary md:text-4xl lg:text-5xl">
              <span className="font-sans font-semibold">Your tools know more than </span>{' '}
              <span className="font-serif font-normal italic">what they are telling you.</span>
            </h2>

            <p className="mx-auto mt-4 max-w-3xl text-sm leading-relaxed text-content/80 md:text-base">
              From acquisition through onboarding, support, and billing to retention or churn. See
              channel performance, cohort health, and churn patterns. Connect all of your tools and
              reveal the hidden patterns, risks and trends that none of them could ever show you
              alone.
            </p>
          </div>
        </div>

        <div className="mt-10 md:mt-14"></div>
      </div>
    </Section>
  );
}
