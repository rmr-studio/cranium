'use client';

import { CtaButton } from '@/components/ui/cta-button';
import { scrollToSection } from '@/lib/scroll';
import { ArrowRight } from 'lucide-react';

import Link from 'next/link';

export const HeroCopy = () => {
  return (
    <div className="relative z-20 mt-10 items-center px-2 pb-4 sm:px-4 sm:pb-6 md:px-8 md:pt-8 md:pb-0 lg:px-0">
      <div>
        <h1 className="text-center font-serif text-xl leading-[1.1] tracking-tighter text-primary/90 sm:text-3xl md:text-[3rem] xl:text-[4rem]">
          Change is happening faster than ever.
          <br />
          <span className="text-center font-serif text-4xl leading-[1.1] tracking-tighter text-primary sm:text-5xl md:text-[5rem] lg:text-[5.5rem] xl:text-[6.5rem]">
            Don't get left behind.
          </span>
        </h1>
      </div>

      <h2
        className={`mx-auto mt-4 max-w-5xl text-center text-sm leading-[1.1] tracking-tighter text-heading/85 sm:px-0 sm:text-base md:mt-8 md:text-lg lg:mt-3 lg:text-xl`}
      >
        Riven closes the gap between insight and action. A platform where powerful AI meets your
        entire customer lifecycle stack. Surface the trends, patterns and shifts that matter. All
        actionable and tracked to expand, pivot or double down in one click.{' '}
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
          <CtaButton className="order:1 paper-lite mx-auto justify-center border bg-background p-6 text-primary hover:bg-accent/40 md:w-64">
            <div className="font-sans text-lg font-semibold tracking-tight">Join the Waitlist</div>
            <ArrowRight className="size-4" />
          </CtaButton>
        </Link>
      </section>
    </div>
  );
};
