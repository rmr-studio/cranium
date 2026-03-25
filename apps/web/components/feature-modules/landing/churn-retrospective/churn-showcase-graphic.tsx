'use client';

import { useContainerScale } from '@/hooks/use-container-scale';
import { useIsMobile } from '@riven/hooks';
import { motion } from 'motion/react';

import { MockChurnRetrospective } from '@/components/feature-modules/landing/churn-retrospective/mock-churn-retrospective';

const DESKTOP_WIDTH = 1920;
const DESKTOP_HEIGHT = 1050;
const MOBILE_WIDTH = 800;
const MOBILE_HEIGHT = Math.round(DESKTOP_HEIGHT * (MOBILE_WIDTH / DESKTOP_WIDTH));

export const ChurnShowcase = () => {
  const isMobile = useIsMobile();
  const width = isMobile ? MOBILE_WIDTH : DESKTOP_WIDTH;
  const height = isMobile ? MOBILE_HEIGHT : DESKTOP_HEIGHT;
  const { containerRef, scale } = useContainerScale(width);

  return (
    <div
      ref={containerRef}
      className="relative w-full translate-x-24 translate-y-8 scale-150 px-0 sm:translate-x-12 sm:scale-130 md:px-12 lg:translate-x-0 lg:translate-y-0 lg:scale-100"
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
          <motion.div
            initial={{ opacity: 0, y: 12 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5 }}
            className="absolute inset-x-0 top-0"
          >
            <MockChurnRetrospective />
          </motion.div>
        </div>
      </div>
    </div>
  );
};
