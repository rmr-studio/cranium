import { Button } from '@/components/ui/button';
import { ArrowRight } from 'lucide-react';

export function CtaStep({ onStart }: { onStart: () => void }) {
  return (
    <div className="py-16 text-center md:py-24">
      <h2 className="gap-x-3 text-4xl leading-none tracking-tighter text-heading md:text-5xl">
        Know more, <span className="font-serif font-normal italic">Do More,</span> Earn more{' '}
      </h2>
      <div className="mx-auto mt-5 flex max-w-3xl flex-col space-y-6 leading-tight tracking-tight text-muted-foreground sm:text-lg">
        <p>
          Join the waitlist, receive updates and secure your spot as a founding team and help shape
          the platform before it launches. Join the waitlist and lock in early-access pricing, with
          up to 50% off your first year when Riven goes live.{' '}
          <span className="font-bold">Takes under 2 minutes.</span>
        </p>
      </div>
      <Button size="lg" onClick={onStart} className="mt-10 gap-2 px-8 text-base">
        Join the Waitlist
        <ArrowRight className="h-4 w-4" />
      </Button>
    </div>
  );
}
