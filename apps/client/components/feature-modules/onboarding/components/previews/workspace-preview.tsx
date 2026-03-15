'use client';

import { Skeleton } from '@/components/ui/skeleton';
import { WorkspacePlan } from '@/lib/types/workspace';
import { cn } from '@/lib/util/utils';
import { AnimatePresence, motion } from 'motion/react';
import Image from 'next/image';
import React from 'react';
import { useOnboardLiveData } from '@/components/feature-modules/onboarding/hooks/use-onboard-store';
import { getInitials, getPaletteColor } from '@/components/feature-modules/onboarding/utils/avatar-helpers';

interface WorkspaceLiveData {
  displayName?: string;
  plan?: WorkspacePlan;
  avatarPreviewUrl?: string;
}

const PLAN_BADGE_STYLES: Record<WorkspacePlan, string> = {
  [WorkspacePlan.Free]: 'bg-muted text-muted-foreground',
  [WorkspacePlan.Startup]: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  [WorkspacePlan.Scale]: 'bg-violet-100 text-violet-700 dark:bg-violet-900/30 dark:text-violet-400',
  [WorkspacePlan.Enterprise]: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
};

const PLAN_LABELS: Record<WorkspacePlan, string> = {
  [WorkspacePlan.Free]: 'Free',
  [WorkspacePlan.Startup]: 'Startup',
  [WorkspacePlan.Scale]: 'Scale',
  [WorkspacePlan.Enterprise]: 'Enterprise',
};

const fadeProps = {
  initial: { opacity: 0 },
  animate: { opacity: 1 },
  exit: { opacity: 0 },
  transition: { duration: 0.3, ease: 'easeInOut' as const },
};

export const WorkspacePreview: React.FC = () => {
  const liveWorkspace = useOnboardLiveData<WorkspaceLiveData>('workspace');

  const displayName = liveWorkspace?.displayName;
  const plan = liveWorkspace?.plan;
  const avatarPreviewUrl = liveWorkspace?.avatarPreviewUrl;

  const hasName = displayName && displayName.length >= 1;
  const hasAvatar = Boolean(avatarPreviewUrl);
  const hasPlan = Boolean(plan);

  return (
    <div className="flex w-full max-w-lg flex-col gap-4">
      <p className="text-muted-foreground text-xs font-semibold uppercase tracking-widest">
        Workspace
      </p>
      <div className="bg-card flex flex-col gap-5 rounded-xl p-8 shadow-sm">
        {/* Header: logo + name + plan badge */}
        <div className="flex items-center gap-4">
          {/* Avatar area */}
          <div className="relative size-14 shrink-0">
            <AnimatePresence mode="wait">
              {hasAvatar ? (
                <motion.div
                  key="avatar-image"
                  {...fadeProps}
                  className="absolute inset-0 overflow-hidden rounded-lg"
                >
                  <Image
                    src={avatarPreviewUrl!}
                    alt={displayName ?? 'Workspace logo'}
                    fill
                    className="object-cover"
                  />
                </motion.div>
              ) : hasName ? (
                <motion.div
                  key="avatar-initials"
                  {...fadeProps}
                  className={cn(
                    'absolute inset-0 flex items-center justify-center rounded-lg',
                    getPaletteColor(displayName!),
                  )}
                >
                  <span className="text-base font-semibold text-white">
                    {getInitials(displayName!)}
                  </span>
                </motion.div>
              ) : (
                <motion.div key="avatar-skeleton" {...fadeProps} className="absolute inset-0">
                  <Skeleton className="size-14 rounded-lg" />
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          {/* Name + plan badge */}
          <div className="flex flex-1 flex-col gap-2.5">
            <AnimatePresence mode="wait">
              {hasName ? (
                <motion.span key="name-text" {...fadeProps} className="h-5 text-sm font-semibold">
                  {displayName}
                </motion.span>
              ) : (
                <motion.div key="name-skeleton" {...fadeProps}>
                  <Skeleton className="h-5 w-44" />
                </motion.div>
              )}
            </AnimatePresence>

            <AnimatePresence mode="wait">
              {hasPlan ? (
                <motion.span
                  key="plan-badge"
                  {...fadeProps}
                  className={cn(
                    'inline-flex h-4 w-fit items-center rounded-full px-2 text-xs font-medium',
                    PLAN_BADGE_STYLES[plan!],
                  )}
                >
                  {PLAN_LABELS[plan!]}
                </motion.span>
              ) : (
                <motion.div key="plan-skeleton" {...fadeProps}>
                  <Skeleton className="h-4 w-28" />
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>

        {/* Description — wireframe decoration, future workspace fields */}
        <div className="flex flex-col gap-2">
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-4/5" />
        </div>

        {/* Stat blocks — wireframe decoration */}
        <div className="flex gap-3">
          <Skeleton className="h-14 flex-1 rounded-lg" />
          <Skeleton className="h-14 flex-1 rounded-lg" />
          <Skeleton className="h-14 flex-1 rounded-lg" />
        </div>
      </div>
    </div>
  );
};
