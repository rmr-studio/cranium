'use client';

import { Section } from '@/components/ui/section';
import { SectionDivider } from '@/components/ui/section-divider';
import { FC, useState } from 'react';

import { ShaderContainer } from '@/components/ui/shader-container';
import { MockKnowledgePanel } from '../hero/components/showcase/mock-knowledge-panel';
import { UseCasePanel } from './components/use-case-panel';
import { UseCaseTabs } from './components/use-case-tabs';
import { USE_CASES } from './data/use-case-scenarios';

export const PlatformSection: FC = () => {
  const [activeId, setActiveId] = useState(USE_CASES[0].id);
  const active = USE_CASES.find((u) => u.id === activeId) ?? USE_CASES[0];

  return (
    <Section id="features" size={24} className="mx-0! px-0!">
      <div className="relative z-10 mb-10 px-4 sm:px-8 md:px-12">
        <SectionDivider>One Platform</SectionDivider>

        <div className="my-10 px-4 sm:px-8">
          <h2 className="font-serif text-2xl leading-none tracking-tighter md:text-4xl lg:text-6xl">
            An End-to-End Platform <br /> From Signal to Agent Deployment.
          </h2>
          <p className="mt-4 max-w-3xl text-sm leading-relaxed text-content/90 md:text-base">
            Go from time-consuming process to working agent in minutes. Connect your data sources,
            ask questions in plain English, and deploy AI agents that take action on your behalf.
            All in one platform, no code required.
          </p>
        </div>
        <UseCaseTabs
          items={USE_CASES}
          activeId={activeId}
          onSelect={setActiveId}
          className="mt-10 px-4 pb-10 sm:px-8"
        />
      </div>
      <ShaderContainer
        className="m-0! border px-4! shadow-lg sm:px-4 md:px-8 lg:px-12 xl:px-16"
        staticOnly
        staticImage="images/landing/background-gradient-1.webp"
      >
        <UseCasePanel scenario={active} />
      </ShaderContainer>
      <ShaderContainer
        className="mx-0! border shadow-lg xl:hidden"
        staticOnly
        staticImage="images/landing/background-gradient-2.webp"
      >
        <div className="relative h-[40rem]">
          <div className="absolute top-0 -left-12 origin-top-left sm:left-0">
            <div className="relative z-10 h-[40rem] scale-80 sm:scale-100">
              <MockKnowledgePanel scenario={active} />
            </div>
          </div>
        </div>
      </ShaderContainer>
    </Section>
  );
};
