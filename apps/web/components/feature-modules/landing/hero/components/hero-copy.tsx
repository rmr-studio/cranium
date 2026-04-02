'use client';

import { CtaButton } from '@/components/ui/cta-button';
import { scrollToSection } from '@/lib/scroll';
import { ArrowRight } from 'lucide-react';

import Link from 'next/link';

export const HeroCopy = () => {
  return (
    <div className="relative z-20 mt-10 items-center px-4 pb-4 sm:pb-6 md:px-8 md:pt-8 md:pb-0 lg:px-0">
      <div>
        <h1 className="text-center font-serif text-[clamp(1.75rem,8vw,2.75rem)] leading-none tracking-tighter sm:text-5xl md:text-[3.5rem] lg:text-start lg:text-[4rem]">
          Contextual Insights For People
          <br />
          Drowning In Spreadsheets And Tabs.
        </h1>
      </div>

      <h2
        className={`mt-8 max-w-5xl text-center text-base leading-tight tracking-tighter text-heading/85 sm:px-0 md:text-lg lg:mt-3 lg:text-start lg:text-xl`}
      >
        The intelligence layer that understands your customer lifecycle data spread across every
        tool in your stack to find the connections and patterns you never knew existed. Learn how to
        engage cohorts, understand their behaviour, and know what keeps them paying. One platform to
        elevate your strategy, act on trends, and drive growth with powerful, real-time, data-driven
        analytics.
      </h2>

      <section
        className={`relative flex w-full items-center justify-center sm:pt-0 md:items-start lg:justify-start`}
      >
        <Link
          href="/#waitlist"
          className="mt-8 w-auto"
          onClick={(e) => {
            e.preventDefault();
            scrollToSection('waitlist');
          }}
        >
          <CtaButton className="paper-lite mx-auto justify-center border bg-background p-6 text-primary hover:bg-accent/40 md:w-64 lg:p-2">
            <div className="font-sans text-lg font-semibold tracking-tight">Join the waitlist</div>
            <ArrowRight className="size-4" />
          </CtaButton>
        </Link>
      </section>
    </div>
  );
};
