'use client';

import { CtaButton } from '@/components/ui/cta-button';
import { scrollToSection } from '@/lib/scroll';
import { useIsMobile } from '@riven/hooks';
import { ArrowRight } from 'lucide-react';

import Link from 'next/link';

export const HeroCopy = () => {
  const isMobile = useIsMobile();

  // undefined = SSR/not yet resolved, true = mobile — skip animations in both cases
  const animate = isMobile === false;

  return (
    <div className="relative z-20 mt-10 items-center px-4 pb-4 sm:pb-6 md:px-8 md:pt-8 md:pb-0 lg:px-0">
      <div>
        <h1 className="text-start font-sans text-[clamp(1.75rem,8vw,2.75rem)] leading-none tracking-tighter text-heading sm:text-5xl md:text-[4rem] lg:text-[5rem]">
          One Workspace. <span className="font-serif italic">Every Tool.</span>
          <br />
          Immediate insight <span className="font-serif italic">to action.</span>
        </h1>
      </div>

      <h2
        className={`mt-3 text-start leading-none tracking-tighter text-heading/85 sm:max-w-lg sm:px-0 sm:text-2xl md:max-w-5xl`}
      >
        Finally. The intelligence layer that unifies and operates across your entire customer
        lifecycle stack. Learn how to engage customers, and know what keeps them paying. One
        platform to elevate your strategy and drive growth with powerful, data-driven insights and
        actions.
        <br className="block md:hidden" />{' '}
        <span className="font-semibold">Built for your entire team.</span>
      </h2>

      <section
        className={`relative flex h-auto w-fit grow flex-col items-center sm:pt-0 md:items-start`}
      >
        <Link
          href="/#waitlist"
          className="mt-8 w-auto"
          onClick={(e) => {
            e.preventDefault();
            scrollToSection('waitlist');
          }}
        >
          <CtaButton className="paper-lite mx-auto justify-center border bg-accent/40 text-primary hover:bg-accent/40 md:w-64">
            <div className="font-sans text-lg tracking-tight">Join the waitlist</div>
            <ArrowRight className="size-4" />
          </CtaButton>
        </Link>
      </section>
    </div>
  );
};
