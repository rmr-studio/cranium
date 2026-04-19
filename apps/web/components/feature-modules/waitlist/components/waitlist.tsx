import { WaitlistForm } from '@/components/feature-modules/waitlist/components/waitlist-form';
import { Section } from '@/components/ui/section';

export const Waitlist = () => {
  return (
    <Section
      size={24}
      id="waitlist"
      className="flex items-center justify-center lg:max-w-[min(100dvw,var(--breakpoint-3xl))]"
    >
      <WaitlistForm className="clamp relative z-10 p-4" />
    </Section>
  );
};
