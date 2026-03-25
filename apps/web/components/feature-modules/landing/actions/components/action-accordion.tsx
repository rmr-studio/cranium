'use client';

import { AccordionItem } from '@/components/feature-modules/landing/actions/components/accordion-item';
import { AnimateOnMountContext } from '@/components/feature-modules/landing/actions/components/animate-context';
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
    base: '#080d0a',
    colors: ['#0f2e1e', '#1a2e22', '#0d1e14'] as [string, string, string],
  },
  amber: {
    base: '#1a2818',
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
    <div className="w-full">
      {/* Desktop */}
      <div className="hidden lg:flex lg:h-[46rem]">
        <div className="z-20 flex w-5/12 shrink-0 flex-col overflow-hidden px-8 py-10 lg:px-12 lg:py-16">
          <h3 className="text-2xl leading-none tracking-tighter text-background sm:text-4xl dark:text-foreground">
            Powerful alone. <span className="font-serif italic"> Unstoppable together.</span>
          </h3>

          <p className="mt-4 max-w-md text-base leading-none tracking-tighter text-primary-foreground/65 dark:text-content">
            Riven explores and surfaces patterns trends and insights hidden deep in your data. From
            channel performance to cohort health to churn risks. Answer critical questions at a
            glance with the most powerful, connected dashboard you have ever seen.
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
          className="flex w-full flex-col p-0 lg:m-6"
        >
          <div className="relative min-h-80 flex-1 overflow-hidden">
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

      {/* Mobile — all content kept mounted, toggled via opacity + visibility */}
      <div className="flex flex-col lg:hidden">
        <ShaderContainer
          staticImages={gradients}
          shaders={actionsShaders}
          className="mt-0 ml-0 rounded-none px-0 py-0 sm:ml-0 lg:ml-0"
        >
          <div className="relative h-[400px] w-full overflow-hidden sm:h-[450px]">
            <AnimateOnMountContext.Provider value={true}>
              {ACTION_CONTENT.map((item, index) => (
                <div
                  key={index}
                  className="pointer-events-none absolute inset-0 flex items-center justify-center transition-opacity duration-300"
                  style={{
                    opacity: activeIndex === index ? 1 : 0,
                    visibility: activeIndex === index ? 'visible' : 'hidden',
                  }}
                >
                  {item.content}
                </div>
              ))}
            </AnimateOnMountContext.Provider>
          </div>
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
