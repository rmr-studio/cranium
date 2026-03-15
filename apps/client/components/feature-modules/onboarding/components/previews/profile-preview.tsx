'use client';

import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/components/provider/auth-context';
import { AnimatePresence, motion } from 'motion/react';
import Image from 'next/image';
import React from 'react';
import { useOnboardLiveData } from '@/components/feature-modules/onboarding/hooks/use-onboard-store';
import { getInitials, getPaletteColor } from '@/components/feature-modules/onboarding/utils/avatar-helpers';

interface ProfileLiveData {
  displayName?: string;
  avatarPreviewUrl?: string;
}

const fadeProps = {
  initial: { opacity: 0 },
  animate: { opacity: 1 },
  exit: { opacity: 0 },
  transition: { duration: 0.3, ease: 'easeInOut' as const },
};

export const ProfilePreview: React.FC = () => {
  const liveProfile = useOnboardLiveData<ProfileLiveData>('profile');
  const { user } = useAuth();

  const displayName = liveProfile?.displayName;
  const avatarPreviewUrl = liveProfile?.avatarPreviewUrl;
  const email = user?.email;

  const hasName = displayName && displayName.length >= 1;
  const hasAvatar = Boolean(avatarPreviewUrl);

  return (
    <div className="flex w-full max-w-lg flex-col gap-4">
      <p className="text-muted-foreground text-xs font-semibold uppercase tracking-widest">
        Profile
      </p>
      <div className="bg-card flex flex-col gap-5 rounded-xl p-8 shadow-sm">
        {/* Avatar + name row */}
        <div className="flex items-center gap-5">
          {/* Avatar area */}
          <div className="relative size-20 shrink-0">
            <AnimatePresence mode="wait">
              {hasAvatar ? (
                <motion.div
                  key="avatar-image"
                  {...fadeProps}
                  className="absolute inset-0 overflow-hidden rounded-full"
                >
                  <Image
                    src={avatarPreviewUrl!}
                    alt={displayName ?? 'Avatar'}
                    fill
                    className="object-cover"
                  />
                </motion.div>
              ) : hasName ? (
                <motion.div
                  key="avatar-initials"
                  {...fadeProps}
                  className={`absolute inset-0 flex items-center justify-center rounded-full ${getPaletteColor(displayName!)}`}
                >
                  <span className="text-lg font-semibold text-white">
                    {getInitials(displayName!)}
                  </span>
                </motion.div>
              ) : (
                <motion.div key="avatar-skeleton" {...fadeProps} className="absolute inset-0">
                  <Skeleton className="size-20 rounded-full" />
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          {/* Name + email */}
          <div className="flex flex-1 flex-col gap-2.5">
            <AnimatePresence mode="wait">
              {hasName ? (
                <motion.span key="name-text" {...fadeProps} className="h-5 text-sm font-medium">
                  {displayName}
                </motion.span>
              ) : (
                <motion.div key="name-skeleton" {...fadeProps}>
                  <Skeleton className="h-5 w-44" />
                </motion.div>
              )}
            </AnimatePresence>

            <AnimatePresence mode="wait">
              {email ? (
                <motion.span
                  key="email-text"
                  {...fadeProps}
                  className="text-muted-foreground h-4 text-xs"
                >
                  {email}
                </motion.span>
              ) : (
                <motion.div key="email-skeleton" {...fadeProps}>
                  <Skeleton className="h-4 w-28" />
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>

        {/* Bio lines — wireframe decoration, future profile fields */}
        <div className="flex flex-col gap-2.5">
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-5/6" />
          <Skeleton className="h-4 w-3/4" />
        </div>

        {/* Contact info — wireframe decoration */}
        <div className="flex gap-3">
          <Skeleton className="h-9 flex-1 rounded-md" />
          <Skeleton className="h-9 flex-1 rounded-md" />
        </div>
      </div>
    </div>
  );
};
