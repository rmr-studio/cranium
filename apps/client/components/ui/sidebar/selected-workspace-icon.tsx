'use client';

import { WorkspaceIcon } from '@/components/feature-modules/workspace/components/workspace-icon';
import { type FC } from 'react';

interface SelectedWorkspaceIconProps {
  avatarUrl?: string;
  name?: string;
}

export const SelectedWorkspaceIcon: FC<SelectedWorkspaceIconProps> = ({ name, avatarUrl }) => {
  return <WorkspaceIcon name={name} avatarUrl={avatarUrl} />;
};
