import React from 'react';
import { ProfilePreview } from '@/components/feature-modules/onboarding/components/previews/profile-preview';
import { WorkspacePreview } from '@/components/feature-modules/onboarding/components/previews/workspace-preview';
import { TemplatesPreview } from '@/components/feature-modules/onboarding/components/previews/templates-preview';
import { TeamPreview } from '@/components/feature-modules/onboarding/components/previews/team-preview';

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
    id: 'templates',
    label: 'Templates',
    description: 'Choose starter templates',
    optional: true,
    cameraX: SECTION_WIDTH * 2,
    PreviewComponent: TemplatesPreview,
  },
  {
    id: 'team',
    label: 'Team',
    description: 'Invite your team',
    optional: true,
    cameraX: SECTION_WIDTH * 3,
    PreviewComponent: TeamPreview,
  },
];
