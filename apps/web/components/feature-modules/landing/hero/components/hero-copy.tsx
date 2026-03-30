'use client';

import { CtaButton } from '@/components/ui/cta-button';
import { scrollToSection } from '@/lib/scroll';
import { ArrowRight } from 'lucide-react';

import Link from 'next/link';

export const HeroCopy = () => {
  return (
    <div className="relative z-20 mt-10 items-center px-4 pb-4 sm:pb-6 md:px-8 md:pt-8 md:pb-0 lg:px-0">
      <div>
        <h1 className="text-center font-serif text-[clamp(1.75rem,8vw,2.75rem)] leading-none tracking-tighter sm:text-5xl md:text-[4rem] lg:text-start lg:text-[4.75rem]">
          One Workspace. <span className="">Every Tool.</span>
          <br />
          Turn scattered customer data into actionable insights.
        </h1>
      </div>

      <h2
        className={`mt-8 max-w-6xl text-center text-base leading-none tracking-tighter text-heading/85 sm:px-0 md:text-lg lg:mt-3 lg:text-start lg:text-xl`}
      >
        The intelligence layer that unifies and operates across your entire customer lifecycle
        stack. Learn how to engage customers, and know what keeps them paying. One platform to
        elevate your strategy and drive growth with powerful, real-time, data-driven insights and
        actions.
        <br className="block md:hidden" />{' '}
        <span className="font-semibold">Built for your entire team.</span>
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
