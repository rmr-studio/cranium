import { WaitlistForm } from '@/components/feature-modules/waitlist/components/waitlist-form';
import { Section } from '@/components/ui/section';

export const Waitlist = () => {
  return (
    <Section size={24} id="waitlist" className="flex items-center justify-center">
      <WaitlistForm className="clamp relative z-10" />
    </Section>
  );
};
