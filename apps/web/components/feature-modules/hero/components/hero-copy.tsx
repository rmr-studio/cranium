'use client';

import { CtaButton } from '@/components/ui/cta-button';
import { scrollToSection } from '@/lib/scroll';
import { ChevronDown } from 'lucide-react';

import { motion } from 'motion/react';
import Link from 'next/link';

export const HeroCopy = () => {
  return (
    <div className="content-container relative z-20 mt-[clamp(1.5rem,8svh,6rem)] flex h-[calc(100svh-6rem)] flex-col items-center pb-4 sm:pb-6 md:items-start md:justify-start md:pt-8 md:pb-0">
      {/* Headings — grouped together */}

      <div className="leading-tight md:mt-0">
        <motion.h2
          className="items-center overflow-hidden text-center font-sans text-[clamp(1.65rem,7.5vw,2.5rem)] leading-[0.9] font-bold tracking-tighter text-heading sm:text-5xl md:flex-row md:gap-4 md:text-start md:text-[5rem] lg:gap-8 lg:text-[5.75rem] 2xl:text-[6.75rem]"
          initial={{ opacity: 0, filter: 'blur(8px)' }}
          animate={{ opacity: 1, filter: 'blur(0px)' }}
          transition={{ duration: 0.5, delay: 0.1, ease: [0.22, 1, 0.36, 1] }}
        >
          <span className="mt-3 text-heading/90">Your Business.</span>
          <br className="sm:hidden" />{' '}
          <span className="text-4xl sm:text-5xl md:text-[5rem] lg:gap-8 lg:text-[5.75rem] 2xl:text-[6.75rem]">
            One Platform
          </span>
        </motion.h2>
        <motion.h2
          className="mt-4 mb-2 overflow-hidden text-center font-sans text-lg leading-5 font-medium tracking-tighter text-heading/90 italic sm:mt-0 sm:mb-4 sm:text-3xl sm:leading-10 md:flex-row md:gap-4 md:text-left md:text-[4rem] md:leading-17 lg:gap-8"
          initial={{ opacity: 0, filter: 'blur(8px)' }}
          animate={{ opacity: 1, filter: 'blur(0px)' }}
          transition={{ duration: 0.5, delay: 0.2, ease: [0.22, 1, 0.36, 1] }}
        >
          All your tools, <br className="sm:hidden" /> connected in a single workspace.
        </motion.h2>
      </div>

      <motion.section
        initial={{ opacity: 0, filter: 'blur(8px)' }}
        animate={{ opacity: 1, filter: 'blur(0px)' }}
        transition={{ duration: 0.5, delay: 0.3, ease: [0.22, 1, 0.36, 1] }}
        className="relative flex flex-col items-end md:items-start"
      >
        {/* Bottom-left: description */}
        <div className="max-w-sm px-4 text-center font-sans text-[0.85rem] leading-snug font-medium tracking-tighter text-primary/90 italic sm:max-w-[28rem] sm:max-w-md sm:px-0 sm:text-[0.95rem] md:max-w-5xl md:text-left md:text-2xl lg:pb-1">
          The complete operational intelligence platform for scaling businesses to connect, combine
          and analyze their data across all tools and departments. Powered by AI to reveal
          underlying patterns and trends that drive growth, and save you time.
        </div>
      </motion.section>
      <motion.div
        initial={{ opacity: 0, filter: 'blur(8px)' }}
        animate={{ opacity: 1, filter: 'blur(0px)' }}
        transition={{ duration: 0.5, delay: 0.4, ease: [0.22, 1, 0.36, 1] }}
        className="mt-4 flex flex-col items-center gap-2 sm:mt-8 sm:flex-row sm:items-center md:mb-0 md:items-start lg:gap-4"
      >
        <Link
          href="/#waitlist"
          onClick={(e) => {
            e.preventDefault();
            scrollToSection('waitlist');
          }}
        >
          <CtaButton className="md:w-80">Join the waitlist</CtaButton>
        </Link>
        <Link
          href="/#features"
          onClick={(e) => {
            e.preventDefault();
            scrollToSection('features');
          }}
          className="mt-1.5 flex items-center gap-1.5 px-4 py-1 font-mono text-sm tracking-wide text-muted-foreground transition-colors hover:text-foreground md:gap-2 md:px-5 md:text-sm"
        >
          Learn More
          <ChevronDown className="h-4 w-4 md:h-5 md:w-5" />
        </Link>
      </motion.div>
    </div>
  );
};
