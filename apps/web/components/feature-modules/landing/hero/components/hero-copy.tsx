'use client';

import { Button } from '@/components/ui/button';
import { GlowBorder } from '@/components/ui/glow-border';
import { scrollToSection } from '@/lib/scroll';
import { Mail, Phone } from 'lucide-react';
import Link from 'next/link';

export const HeroCopy = () => {
  return (
    <div className="relative z-20 mx-auto mt-10 w-full max-w-full items-center rounded-lg px-4 sm:w-fit sm:px-0">
      <div>
        <GlowBorder rounded="lg" className="mx-auto mb-8 w-fit max-w-11/12" opacity={70}>
          <div className="mx-auto rounded-lg border border-primary/30 bg-card/90 px-2.5 py-1.5 font-display text-[clamp(0.60rem,3vw,0.875rem)] tracking-tight whitespace-nowrap text-content/80 sm:text-base">
            AI proactive company brain for ecomm ops
          </div>
        </GlowBorder>
        <h1 className="text-center font-bit text-[clamp(1.5rem,8.5vw,2.25rem)] leading-[0.8] text-primary/90 sm:text-3xl md:text-[4rem] xl:text-[6rem]">
          The Brain <br className="sm:hidden" /> That Never Sleeps
        </h1>
      </div>

      <div
        className={`mx-auto mt-4 max-w-xs text-center font-display text-sm leading-[1.1] tracking-tighter text-heading/85 sm:max-w-3xl sm:px-0 sm:text-base md:mt-8 md:max-w-4xl lg:mt-3 xl:max-w-260`}
      >
        The connective layers bringing in context from every creak and crevice of your brand.
        surfacing, executing monitoring and learning from the most impactful opportunities, trends,
        and risks, before they even arise.
      </div>

      <section className="relative z-[70] mt-8 flex w-full flex-col items-center justify-center gap-4 sm:flex-row">
        <Link
          href={'https://calendly.com/jared-rmr/30min'}
          target="_blank"
          className="w-auto cursor-pointer rounded-md"
        >
          <Button variant="outline" className="border-primary/70 font-display">
            <Phone size={16} />
            Talk To The Founders
          </Button>
        </Link>
        <Link
          href="/#waitlist"
          onClick={(e) => {
            e.preventDefault();
            scrollToSection('waitlist');
          }}
          className="hidden min-[360px]:block"
        >
          <Button className="border-primary/70 font-display" variant={'outline'}>
            <Mail size={16} />
            Join the Waitlist
          </Button>
        </Link>
      </section>
    </div>
  );
};
