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
  amber: {
    base: '#1a1a2a',
    colors: ['#2a5e2a', '#4a7a3e', '#72a85a'] as [string, string, string],
  },
};

export const DailyActionAccordion = () => {
  const { activeIndex, select } = useAutoAdvance(ACTION_CONTENT.length);

  const gradients: ThemeStaticImages = {
    light: 'images/texture/static-gradient-6.webp',
    dark: 'images/texture/static-gradient-6.webp',
    amber: 'images/texture/static-gradient-6.webp',
  };

  return (
    <div className="mt-20 w-full">
      {/* Desktop */}
      <div className="hidden lg:flex lg:h-[46rem]">
        <div className="z-20 flex w-5/12 shrink-0 flex-col overflow-hidden px-8 py-10 lg:px-12 lg:py-16">
          <h3 className="text-2xl leading-none tracking-tighter sm:text-4xl">
            Powerful alone. <span className="font-serif italic"> Unstoppable together.</span>
          </h3>

          <p className="mt-4 max-w-md text-base leading-none tracking-tighter text-content/90 dark:text-content">
            Each one works on its own. Put them together and your connected data becomes something
            your team actually works from. Query across tools, tag what matters, set rules, act on
            what comes up.
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
          className="dark ml-6 flex w-full flex-col rounded-r-none p-0 shadow-lg"
        >
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
