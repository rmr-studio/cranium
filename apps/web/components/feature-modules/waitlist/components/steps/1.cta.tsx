import { CtaButton } from '@/components/ui/cta-button';
import { ArrowRight } from 'lucide-react';

export function CtaStep({ onStart }: { onStart: () => void }) {
  return (
    <div className="mx-auto flex flex-col items-center py-16 text-center md:py-24">
      <h2 className="gap-x-3 px-2 font-serif text-4xl leading-none tracking-tighter md:text-7xl">
        Your next breakthrough, On Us{' '}
      </h2>
      <div className="mx-auto mt-5 mb-10 flex max-w-2xl flex-col space-y-6 px-4 leading-tight tracking-tight text-muted-foreground sm:mx-0 sm:text-lg xl:max-w-3xl">
        Join the waitlist, receive updates and secure your spot as a founding team and help shape
        the platform before it launches. Join the waitlist and lock in early-access pricing, with up
        to 50% off your first year when Riven goes live.{' '}
        <span className="font-bold">Takes under 2 minutes.</span>
      </div>
      <CtaButton onClick={onStart}>
        Join the Waitlist
        <ArrowRight className="h-4 w-4" />
      </CtaButton>
    </div>
  );
}
