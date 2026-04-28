'use client';

import { useContainerScale } from '@/hooks/use-container-scale';
import { useIsMobile } from '@riven/hooks';

import { MockCohortBehaviours } from './mock-valuable-cohorts';

const DESKTOP_WIDTH = 1920;
const DESKTOP_HEIGHT = 1050;
const MOBILE_WIDTH = 1000;
const MOBILE_HEIGHT = Math.round(DESKTOP_HEIGHT * (MOBILE_WIDTH / DESKTOP_WIDTH));

export const CohortBehaviourShowcase = () => {
  const isMobile = useIsMobile();
  const width = isMobile ? MOBILE_WIDTH : DESKTOP_WIDTH;
  const height = isMobile ? MOBILE_HEIGHT : DESKTOP_HEIGHT;
  const { containerRef, scale } = useContainerScale(width);

  return (
    <div
      ref={containerRef}
      className="pointer-events-none relative w-full -translate-x-64 translate-y-8 scale-60 sm:translate-x-12 sm:scale-100 md:px-12 lg:translate-x-0 lg:translate-y-0"
    >
      <div
        className="origin-top-left"
        style={{
          width,
          transform: `scale(${scale})`,
          height: height * scale,
        }}
      >
        <div className="relative" style={{ height }}>
          <MockCohortBehaviours />
        </div>
      </div>
    </div>
  );
};
