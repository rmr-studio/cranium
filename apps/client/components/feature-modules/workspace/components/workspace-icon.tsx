'use client';

import { cn } from '@riven/utils';
import { FC } from 'react';

interface WorkspaceIconProps {
  name?: string;
  avatarUrl?: string;
  className?: string;
}

export const WorkspaceIcon: FC<WorkspaceIconProps> = ({ name, avatarUrl, className }) => {
  const letter = name?.charAt(0)?.toUpperCase() || 'W';

  return (
    <div
      className={cn(
        'flex size-8 items-center justify-center overflow-hidden rounded-md text-sm font-bold text-primary-foreground',
        className,
      )}
    >
      {avatarUrl ? (
        <img src={avatarUrl} alt={name} className="size-full object-cover" />
      ) : (
        <div className="size-8 bg-primary dark:bg-muted-foreground">{letter}</div>
      )}
    </div>
  );
};
