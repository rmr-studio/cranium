import { WaitlistForm } from '@/components/feature-modules/waitlist/components/waitlist-form';
import { Section } from '@/components/ui/section';

export const Waitlist = () => {
  return (
    <Section
      size={24}
      id="waitlist"
      className="mx-auto border-x border-x-content/25 2xl:max-w-[min(90dvw,var(--breakpoint-3xl))]"
    >
      <WaitlistForm className="relative z-10 p-4" />
    </Section>
  );
};
