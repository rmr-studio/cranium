'use client';

import { ScaledShowcase } from '@/components/ui/scaled-showcase';
import { MockTaggingView } from '@/components/feature-modules/landing/actions/components/diagrams/tagging-view';

export const TaggingViewShowcase = ({ className }: { className?: string }) => (
  <ScaledShowcase desktopHeight={950} className={className}>
    <MockTaggingView />
  </ScaledShowcase>
);
