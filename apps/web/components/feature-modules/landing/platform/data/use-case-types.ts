import type { ReactNode } from 'react';
import type { ShowcaseScenario } from '@/components/feature-modules/landing/hero/components/showcase/scenario-types';

export interface SkillCard {
  icon: ReactNode;
  title: string;
  bullets: ReactNode[];
}

export interface AgentScenario
  extends Pick<
    ShowcaseScenario,
    | 'key'
    | 'kbQuery'
    | 'kbRetrieved'
    | 'kbAnalysedTitle'
    | 'kbAnalysedCards'
    | 'kbIdentified'
    | 'kbResponse'
    | 'kbIntegrations'
  > {
  id: string;
  tabTitle: string;
  tabSubtitle: string;
  tabIcon: ReactNode;
  version: 'V1' | 'V2' | 'V3';
  skills: SkillCard[];
}
