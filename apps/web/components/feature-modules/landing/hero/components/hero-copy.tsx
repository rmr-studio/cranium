'use client';

import { CtaButton } from '@/components/ui/cta-button';
import { scrollToSection } from '@/lib/scroll';
import { ArrowRight } from 'lucide-react';

import Link from 'next/link';

export const HeroCopy = () => {
  return (
    <div className="relative z-20 mt-10 items-center px-2 pb-4 sm:px-4 sm:pb-6 md:px-8 md:pt-8 md:pb-0 lg:px-0">
      <div>
        <h1 className="text-center font-serif text-[clamp(1.75rem,8vw,2.75rem)] leading-[1.1] tracking-tighter sm:text-5xl md:text-[4rem]">
          Scale, grow and expand with contextual customer insights.
          <br />
          Unlock the ability to act, track and attack immediately.
        </h1>
      </div>

      <h2
        className={`mx-auto mt-8 max-w-6xl text-center text-base leading-[1.1] tracking-tighter text-heading/85 sm:px-0 md:text-lg lg:mt-3`}
      >
        Unite the customer lifecycle data spread across every tool in your stack your tools. Unlock
        immediate insights, patterns and trends about your most valuable cohorts. All immediately
        actionable to double down and expand quickly with confidence.
      </h2>

      <section
        className={`relative flex w-full items-center justify-center sm:pt-0 md:items-start`}
      >
        <Link
          href="/#waitlist"
          className="mt-8 w-auto"
          onClick={(e) => {
            e.preventDefault();
            scrollToSection('waitlist');
          }}
        >
          <CtaButton className="paper-lite mx-auto justify-center border bg-background p-6 text-primary hover:bg-accent/40 md:w-64">
            <div className="font-sans text-lg font-semibold tracking-tight">
              Unlock early access
            </div>
            <ArrowRight className="size-4" />
          </CtaButton>
        </Link>
      </section>
    </div>
  );
};
