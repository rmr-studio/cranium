'use client';

import { cn } from '@riven/utils';
import { FC } from 'react';

interface WorkspaceIconProps {
  name: string;
  avatarUrl?: string;
  className?: string;
}

export const WorkspaceIcon: FC<WorkspaceIconProps> = ({ name, avatarUrl, className }) => {
  const letter = name?.charAt(0)?.toUpperCase() || 'W';

  return (
    <div
      className={cn(
        'flex size-8 items-center justify-center overflow-hidden rounded-md bg-primary text-sm font-bold text-primary-foreground dark:bg-muted-foreground',
        className,
      )}
    >
      {avatarUrl ? (
        <img src={avatarUrl} alt={name} className="size-full object-cover" />
      ) : (
        letter
      )}
    </div>
  );
};
