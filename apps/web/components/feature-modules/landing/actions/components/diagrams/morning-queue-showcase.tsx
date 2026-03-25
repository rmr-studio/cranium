'use client';

import { ScaledShowcase } from '@/components/ui/scaled-showcase';
import { MockMorningQueue } from '@/components/feature-modules/landing/actions/components/diagrams/morning-queue';

export const MorningQueueShowcase = ({ className }: { className?: string }) => (
  <ScaledShowcase desktopHeight={1200} className={className}>
    <MockMorningQueue />
  </ScaledShowcase>
);
