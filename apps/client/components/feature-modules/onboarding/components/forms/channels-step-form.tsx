'use client';

import { AcquisitionChannel } from '@/lib/types/workspace';
import { cn } from '@/lib/util/utils';
import { FC, useEffect, useState } from 'react';
import {
  useOnboardStoreApi,
  useOnboardFormControls,
  useOnboardNavigation,
} from '@/components/feature-modules/onboarding/hooks/use-onboard-store';

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

const ALL_CHANNELS = Object.values(AcquisitionChannel);

export interface ChannelsLiveData {
  selectedChannels: AcquisitionChannel[];
}

export const ChannelsStepForm: FC = () => {
  const storeApi = useOnboardStoreApi();
  const { setLiveData, registerFormTrigger, clearFormTrigger } = useOnboardFormControls();
  const { skip } = useOnboardNavigation();

  const [restoredData] = useState(
    () => storeApi.getState().liveData['channels'] as ChannelsLiveData | undefined,
  );

  const [selectedChannels, setSelectedChannels] = useState<AcquisitionChannel[]>(
    restoredData?.selectedChannels ?? [],
  );

  // Optional step — always valid
  useEffect(() => {
    registerFormTrigger(async () => true);
    return () => clearFormTrigger();
  }, [registerFormTrigger, clearFormTrigger]);

  // Sync liveData
  useEffect(() => {
    setLiveData('channels', { selectedChannels });
  }, [selectedChannels, setLiveData]);

  const toggleChannel = (channel: AcquisitionChannel) => {
    setSelectedChannels((prev) =>
      prev.includes(channel)
        ? prev.filter((c) => c !== channel)
        : [...prev, channel],
    );
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        {ALL_CHANNELS.map((channel) => {
          const isSelected = selectedChannels.includes(channel);
          return (
            <button
              key={channel}
              type="button"
              onClick={() => toggleChannel(channel)}
              aria-pressed={isSelected}
              className={cn(
                'rounded-lg border px-3 py-2.5 text-left text-sm transition-colors',
                isSelected
                  ? 'border-primary bg-primary/5 font-medium ring-2 ring-primary'
                  : 'border-border hover:border-muted-foreground/40',
              )}
            >
              {CHANNEL_LABELS[channel]}
            </button>
          );
        })}
      </div>

      <div className="flex items-center">
        <button
          type="button"
          onClick={skip}
          className="text-muted-foreground hover:text-foreground ml-auto text-xs transition-colors"
        >
          Skip channels
        </button>
      </div>
    </div>
  );
};
