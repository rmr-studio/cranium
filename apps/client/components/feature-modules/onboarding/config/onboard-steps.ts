import { DefinitionsPreview } from '@/components/feature-modules/onboarding/components/previews/definitions-preview';
import { ProfilePreview } from '@/components/feature-modules/onboarding/components/previews/profile-preview';
import { TeamPreview } from '@/components/feature-modules/onboarding/components/previews/team-preview';
import { WorkspacePreview } from '@/components/feature-modules/onboarding/components/previews/workspace-preview';
import React from 'react';

export const SECTION_WIDTH = 800;

export interface OnboardStepConfig {
  id: string;
  label: string;
  description: string;
  optional: boolean;
  cameraX: number;
  PreviewComponent: React.ComponentType;
}

export const ONBOARD_STEPS: OnboardStepConfig[] = [
  {
    id: 'profile',
    label: 'Profile',
    description: 'Tell us about yourself',
    optional: false,
    cameraX: 0,
    PreviewComponent: ProfilePreview,
  },
  {
    id: 'workspace',
    label: 'Workspace',
    description: 'Set up your workspace',
    optional: false,
    cameraX: SECTION_WIDTH,
    PreviewComponent: WorkspacePreview,
  },
  {
    id: 'definitions',
    label: 'Definitions',
    description: 'Define your business language',
    optional: true,
    cameraX: SECTION_WIDTH * 2,
    PreviewComponent: DefinitionsPreview,
  },
  {
    id: 'channels',
    label: 'Channels',
    description: 'Your acquisition channels',
    optional: true,
    cameraX: SECTION_WIDTH * 3,
    PreviewComponent: () => null,
  },
  {
    id: 'team',
    label: 'Team',
    description: 'Invite your team',
    optional: true,
    cameraX: SECTION_WIDTH * 4,
    PreviewComponent: TeamPreview,
  },
];
