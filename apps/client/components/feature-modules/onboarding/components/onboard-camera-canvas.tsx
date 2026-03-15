'use client';

import { ONBOARD_STEPS } from '@/components/feature-modules/onboarding/config/onboard-steps';
import { useOnboardStepState } from '@/components/feature-modules/onboarding/hooks/use-onboard-store';
import { Propless } from '@/lib/interfaces/interface';
import { useAnimate } from 'framer-motion';
import { FC, useEffect, useRef, useState } from 'react';

export const OnboardCameraCanvas: FC<Propless> = () => {
  const { currentStep } = useOnboardStepState();
  const [scope, animate] = useAnimate();
  const containerRef = useRef<HTMLDivElement>(null);
  const isAnimating = useRef(false);
  const prevStepRef = useRef(currentStep);
  const [isZoomedOut, setIsZoomedOut] = useState(false);
  const [sectionWidth, setSectionWidth] = useState(800);

  // Measure container on mount and resize
  useEffect(() => {
    const measure = () => {
      if (containerRef.current) {
        setSectionWidth(containerRef.current.offsetWidth);
      }
    };
    measure();
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, []);

  // Initial mount: position immediately at step 0 without animation
  useEffect(() => {
    if (!scope.current) return;
    animate(scope.current, { x: 0, scale: 1 }, { duration: 0 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Three-phase camera animation on step change: zoom-out → pan → zoom-in
  useEffect(() => {
    if (!scope.current) return;
    if (currentStep === prevStepRef.current) return;
    prevStepRef.current = currentStep;

    const run = async () => {
      if (isAnimating.current) return;
      isAnimating.current = true;

      const targetX = -(currentStep * sectionWidth);

      try {
        // Phase 1 — zoom out
        setIsZoomedOut(true);
        await animate(scope.current, { scale: 0.85 }, { duration: 0.2, ease: 'easeOut' });

        // Phase 2 — pan to target section
        await animate(
          scope.current,
          { x: targetX },
          { duration: 0.2, ease: [0.4, 0, 0.2, 1] as [number, number, number, number] },
        );

        // Phase 3 — zoom in
        await animate(scope.current, { scale: 1 }, { duration: 0.2, ease: 'easeIn' });
        setIsZoomedOut(false);
      } catch {
        setIsZoomedOut(false);
      } finally {
        isAnimating.current = false;
      }
    };

    run();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentStep, sectionWidth]);

  return (
    <div ref={containerRef} className="relative flex h-full w-full items-center overflow-hidden">
      <div ref={scope} className="flex h-full items-center">
        {ONBOARD_STEPS.map((step, index) => {
          const { PreviewComponent } = step;
          const isInactive = isZoomedOut && index !== currentStep;

          return (
            <div
              key={step.id}
              className="flex h-full shrink-0 items-center justify-center px-16 transition-opacity duration-200"
              style={{ width: sectionWidth }}
            >
              <div
                className={`w-full max-w-lg ${isInactive ? 'opacity-30' : 'opacity-100'}`}
              >
                <PreviewComponent />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
