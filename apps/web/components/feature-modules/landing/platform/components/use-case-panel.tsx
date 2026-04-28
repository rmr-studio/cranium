'use client';

import { FC } from 'react';

import { MockKnowledgePanel } from '@/components/feature-modules/landing/hero/components/showcase/mock-knowledge-panel';
import { useContainerScale } from '@/hooks/use-container-scale';

import type { AgentScenario } from '../data/use-case-types';
import { SkillGrid } from './skill-grid';

const PANEL_WIDTH = 800;
const PANEL_HEIGHT = 940;

interface Props {
  scenario: AgentScenario;
}

export const UseCasePanel: FC<Props> = ({ scenario }) => {
  const { containerRef, scale } = useContainerScale(PANEL_WIDTH);

  return (
    <div className="mx-auto h-[40rem] w-full gap-8 xl:flex xl:h-[45rem]">
      {/* Skill cards — re-keyed so only this side fades on scenario change */}
      <div
        key={scenario.id}
        className="absolute -right-8 w-full sm:-right-48 xl:relative xl:right-auto xl:w-full"
      >
        <SkillGrid skills={scenario.skills} />
      </div>

      {/* Knowledge chat — outer panel frame stays mounted.
          MockKnowledgePanel keys its own inner content on scenario.key,
          so only the content inside the panel fades in. */}
      <div ref={containerRef} className="relative hidden w-fit xl:block">
        <div
          className="absolute -right-24 -bottom-38 origin-top-left scale-91 xl:relative xl:right-auto xl:bottom-auto"
          style={{
            width: PANEL_WIDTH,
            transform: `scale(${scale})`,
            height: PANEL_HEIGHT * scale,
          }}
        >
          <div className="relative z-10" style={{ height: PANEL_HEIGHT }}>
            <MockKnowledgePanel scenario={scenario} />
          </div>
        </div>
      </div>
    </div>
  );
};
