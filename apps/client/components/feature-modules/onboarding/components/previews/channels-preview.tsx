'use client';

import { Skeleton } from '@/components/ui/skeleton';
import { AcquisitionChannel } from '@/lib/types/workspace';
import { AnimatePresence, motion } from 'motion/react';
import React from 'react';
import { useOnboardLiveData } from '@/components/feature-modules/onboarding/hooks/use-onboard-store';
import type { ChannelsLiveData } from '@/components/feature-modules/onboarding/components/forms/channels-step-form';

const fadeProps = {
  initial: { opacity: 0 },
  animate: { opacity: 1 },
  exit: { opacity: 0 },
  transition: { duration: 0.3, ease: 'easeInOut' as const },
};

const CHANNEL_LABELS: Record<AcquisitionChannel, string> = {
  [AcquisitionChannel.GoogleAds]: 'Google Ads',
  [AcquisitionChannel.Linkedin]: 'LinkedIn',
  [AcquisitionChannel.Twitter]: 'Twitter',
  [AcquisitionChannel.ProductHunt]: 'Product Hunt',
  [AcquisitionChannel.Referral]: 'Referral',
  [AcquisitionChannel.OrganicSearch]: 'Organic Search',
  [AcquisitionChannel.Direct]: 'Direct',
  [AcquisitionChannel.ContentMarketing]: 'Content Marketing',
  [AcquisitionChannel.Facebook]: 'Facebook',
  [AcquisitionChannel.Instagram]: 'Instagram',
  [AcquisitionChannel.Tiktok]: 'TikTok',
  [AcquisitionChannel.Youtube]: 'YouTube',
  [AcquisitionChannel.Podcast]: 'Podcast',
  [AcquisitionChannel.Event]: 'Event',
  [AcquisitionChannel.Other]: 'Other',
};

const SKELETON_COUNT = 6;

export const ChannelsPreview: React.FC = () => {
  const liveData = useOnboardLiveData<ChannelsLiveData>('channels');
  const selectedChannels = liveData?.selectedChannels ?? [];

  return (
    <div className="flex w-full max-w-lg flex-col gap-4">
      <p className="text-muted-foreground text-xs font-semibold uppercase tracking-widest">
        Channels
      </p>
      <AnimatePresence mode="wait">
        {selectedChannels.length > 0 ? (
          <motion.div
            key="selected"
            {...fadeProps}
            className="flex flex-wrap gap-2"
          >
            {selectedChannels.map((channel) => (
              <span
                key={channel}
                className="bg-card rounded-lg border px-3 py-2 text-sm font-medium shadow-sm"
              >
                {CHANNEL_LABELS[channel]}
              </span>
            ))}
          </motion.div>
        ) : (
          <motion.div key="skeleton" {...fadeProps} className="flex flex-wrap gap-2">
            {Array.from({ length: SKELETON_COUNT }).map((_, i) => (
              <Skeleton key={i} className="h-10 w-24 rounded-lg" />
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};
