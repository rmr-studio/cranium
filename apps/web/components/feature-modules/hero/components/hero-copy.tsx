'use client';

import { CtaButton } from '@/components/ui/cta-button';
import { scrollToSection } from '@/lib/scroll';
import { ChevronDown } from 'lucide-react';

import { motion } from 'motion/react';
import Link from 'next/link';

export const HeroCopy = () => {
  return (
    <div className="content-container relative z-20 mt-[clamp(1.5rem,6svh,6rem)] flex h-[calc(100svh-6rem)] flex-col items-center pb-4 sm:pb-6 md:items-start md:justify-start md:pt-8 md:pb-0">
      {/* Headings — grouped together */}

      <div className="leading-tight md:mt-0">
        <motion.h2
          className="items-center overflow-hidden text-center font-sans text-[clamp(1.65rem,7.5vw,2.5rem)] font-bold tracking-tighter text-heading sm:text-5xl sm:leading-[0.9] md:flex-row md:gap-4 md:text-start md:text-[5rem] lg:gap-8 lg:text-[5.75rem] 2xl:text-[6.75rem]"
          initial={{ opacity: 0, filter: 'blur(8px)' }}
          animate={{ opacity: 1, filter: 'blur(0px)' }}
          transition={{ duration: 0.5, delay: 0.1, ease: [0.22, 1, 0.36, 1] }}
        >
          <span className="mt-3 text-heading/90">Your Business.</span>
          <br className="sm:hidden" />{' '}
          <span className="text-[clamp(2.5rem,14vw,4.25rem)] sm:text-5xl md:text-[5rem] lg:text-[5.75rem] 2xl:text-[6.75rem]">
            One Platform
          </span>
        </motion.h2>
        <motion.h2
          className="lg::leading-17 ml- mt-4 mb-2 overflow-hidden text-center font-sans text-[clamp(1.5rem,1vw,3rem)] leading-6 font-medium tracking-tighter text-heading/90 italic sm:mt-0 sm:mb-4 sm:text-3xl sm:leading-10 md:flex-row md:gap-4 md:text-left md:text-4xl lg:gap-8"
          initial={{ opacity: 0, filter: 'blur(8px)' }}
          animate={{ opacity: 1, filter: 'blur(0px)' }}
          transition={{ duration: 0.5, delay: 0.2, ease: [0.22, 1, 0.36, 1] }}
        >
          All your tools, together. <br className="xl:hidden" /> Revealing what none of them can see
          alone.
        </motion.h2>
      </div>

      <motion.section
        initial={{ opacity: 0, filter: 'blur(8px)' }}
        animate={{ opacity: 1, filter: 'blur(0px)' }}
        transition={{ duration: 0.5, delay: 0.3, ease: [0.22, 1, 0.36, 1] }}
        className="2xs relative flex h-auto grow flex-col items-center pt-[5%] sm:pt-0 md:items-start"
      >
        {/* Bottom-left: description */}
        <div className="max-w-sm px-4 text-center font-sans text-[0.85rem] leading-snug font-medium tracking-tighter text-primary/75 sm:max-w-[28rem] sm:max-w-md sm:px-0 sm:text-[0.95rem] md:max-w-5xl md:text-left md:text-xl lg:pb-1">
          The collaborative operational intelligence platform.{' '}
          <span className="font-bold">Connect your CRM</span> and{' '}
          <span className="font-semibold italic">you see your customers</span>.{' '}
          <span className="font-bold">Add your payment data</span>,{' '}
          <span className="font-semibold italic">you see which of them are the most valuable</span>.{' '}
          <span className="font-bold">Add support and usage data</span>,{' '}
          <span className="font-semibold italic">you understand why</span>, and{' '}
          <span className="font-semibold italic">what's about to change</span>. Riven's AI reasons
          across every source, comparing historical patterns against what's happening now to surface
          the risks and opportunities hiding between your tools.
        </div>
        <div className="mt-6 flex w-full flex-col items-center gap-3 sm:mt-10 sm:flex-row sm:items-center md:mb-0 md:items-start lg:gap-5">
          <Link
            href="/#waitlist"
            className="w-auto"
            onClick={(e) => {
              e.preventDefault();
              scrollToSection('waitlist');
            }}
          >
            <CtaButton className="mx-auto w-full justify-center" size="xl">
              <div className="flex flex-col items-center gap-0.5 md:items-start">
                Join the waitlist
              </div>
            </CtaButton>
          </Link>
          <Link
            href="/#features"
            onClick={(e) => {
              e.preventDefault();
              scrollToSection('features');
            }}
            className="mt-1 flex items-center gap-1.5 rounded-lg border border-border px-5 py-2.5 font-mono text-sm tracking-wide text-muted-foreground transition-colors hover:border-foreground/20 hover:text-foreground md:gap-2 md:text-sm"
          >
            Learn More
            <ChevronDown className="h-4 w-4 md:h-5 md:w-5" />
          </Link>
        </div>
      </motion.section>
    </div>
  );
};
