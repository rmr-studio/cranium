'use client';

import { AccordionItem } from '@/components/feature-modules/landing/actions/components/accordion-item';
import { ACTION_CONTENT } from '@/components/feature-modules/landing/actions/config/accordion-content';
import { useAutoAdvance } from '@/components/feature-modules/landing/actions/hooks/use-auto-advance';
import { ShaderContainer, ThemeStaticImages } from '@/components/ui/shader-container';
import { AnimatePresence, motion } from 'motion/react';

const actionsShaders = {
  light: {
    base: '#6e7e6e',
    colors: ['#2e6e4a', '#5a9a6e', '#a0c8a8'] as [string, string, string],
  },
  dark: {
    base: '#d7cbc6',
    colors: ['#0f2e1e', '#1a2e22', '#0d1e14'] as [string, string, string],
  },
};

export const DailyActionAccordion = () => {
  const { activeIndex, select } = useAutoAdvance(ACTION_CONTENT.length);

  const gradients: ThemeStaticImages = {
    light: 'images/texture/static-gradient-6.webp',
    dark: 'images/texture/static-gradient-6.webp',
  };

  return (
    <div className="mt-20 w-full">
      {/* Desktop */}
      <div className="hidden h-[50rem] lg:flex">
        <div className="z-20 flex w-5/12 shrink-0 flex-col overflow-hidden px-8 py-10 lg:px-12 lg:py-16">
          <h3 className="font-serif text-2xl leading-none tracking-tighter sm:text-4xl md:text-5xl">
            Powerful alone. <br /> Unstoppable together.
          </h3>

          <p className="mt-4 text-base leading-none tracking-tighter text-content/90 dark:text-content">
            Each tool works perfectly on its own. Put them together and your connected data becomes
            something your team actually works from. Query across tools, tag what matters, set
            rules, act on what comes up.
          </p>

          <div className="mt-8 border-t border-white/10" />

          <div className="mt-2 flex flex-col">
            {ACTION_CONTENT.map((item, index) => (
              <AccordionItem
                key={index}
                title={item.title}
                description={item.description}
                isActive={activeIndex === index}
                onClick={() => select(index)}
              />
            ))}
          </div>
        </div>

        <ShaderContainer
          staticImages={gradients}
          shaders={actionsShaders}
          className="dark flex w-full flex-col p-0 shadow-lg shadow-foreground/40 lg:rounded-lg dark:shadow-none"
        >
          <div className="pointer-events-none absolute inset-y-0 left-0 z-10 hidden w-24 bg-gradient-to-r from-black/60 via-black/25 to-transparent md:w-40 3xl:block" />
          <div className="pointer-events-none absolute inset-y-0 right-0 z-10 w-24 bg-gradient-to-l from-black/60 via-black/25 to-transparent md:w-40" />
          <div className="pointer-events-none absolute inset-0 z-10 opacity-60 shadow-[inset_20px_0_40px_rgba(0,0,0,0.5),inset_-20px_0_40px_rgba(0,0,0,0.5)] md:shadow-[inset_32px_0_60px_rgba(0,0,0,0.55),inset_-32px_0_60px_rgba(0,0,0,0.25)]" />
          <div className="relative h-full min-h-80 flex-1 overflow-hidden">
            <AnimatePresence mode="wait">
              <motion.div
                key={activeIndex}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -12 }}
                transition={{ duration: 0.3, ease: 'easeInOut' }}
                className="flex h-full items-center pt-4"
              >
                {ACTION_CONTENT[activeIndex]?.content}
              </motion.div>
            </AnimatePresence>
          </div>
        </ShaderContainer>
      </div>

      {/* Mobile — only active content rendered */}
      <div className="flex flex-col lg:hidden">
        <ShaderContainer
          staticImages={gradients}
          shaders={actionsShaders}
          className="mt-0 ml-0 rounded-none px-0 py-0 sm:ml-0 lg:ml-0"
        >
          <motion.div
            key={activeIndex}
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -12 }}
            transition={{ duration: 0.3, ease: 'easeInOut' }}
            className="relative h-[400px] w-full overflow-hidden sm:h-[500px]"
          >
            <div className="pointer-events-none w-full">{ACTION_CONTENT[activeIndex]?.content}</div>
          </motion.div>
        </ShaderContainer>

        <div className="flex flex-col px-6 pb-6">
          {ACTION_CONTENT.map((item, index) => (
            <AccordionItem
              key={index}
              title={item.title}
              description={item.description}
              isActive={activeIndex === index}
              onClick={() => select(index)}
              compact
            />
          ))}
        </div>
      </div>
    </div>
  );
};
