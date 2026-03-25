'use client';

import { ScaledShowcase } from '@/components/ui/scaled-showcase';
import { MockEntityDetail } from '@/components/feature-modules/landing/actions/components/diagrams/entity-detail';

export const EntityDetailShowcase = ({ className }: { className?: string }) => (
  <ScaledShowcase desktopHeight={950} className={className}>
    <MockEntityDetail />
  </ScaledShowcase>
);
