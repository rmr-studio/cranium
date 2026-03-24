'use client';

import { useReadingProgress } from '@/hooks/use-reading-progress';

export function ReadingProgress() {
  const progress = useReadingProgress();

  return (
    <div
      className="fixed left-0 top-0 z-50 h-0.5 bg-foreground transition-[width] duration-150 ease-out"
      style={{ width: `${progress}%` }}
      role="progressbar"
      aria-valuenow={Math.round(progress)}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-label="Reading progress"
    />
  );
}
