'use client';

import { AuthFrame } from '@/components/feature-modules/authentication/components/auth-frame';
import { BGPattern } from '@/components/ui/background/grids';
import { cn } from '@cranium/utils';

const INSET_VARS =
  '[--auth-ix:2rem] [--auth-iy:2.5rem] sm:[--auth-ix:2.5rem] sm:[--auth-iy:3rem] md:[--auth-ix:3rem] md:[--auth-iy:3.5rem] lg:[--auth-ix:4rem] lg:[--auth-iy:4rem]';

export function AuthBackground() {
  return (
    <div className={cn('pointer-events-none fixed inset-0 overflow-hidden', INSET_VARS)}>
      {/* Cave illustration — full bleed, faded */}
      <div className="absolute inset-0 z-0">
    

        {/* Radial vignette — darkens edges, keeps centre clear for the form */}
        <div
          className="absolute inset-0 z-10"
          style={{
            background:
              'radial-gradient(ellipse 60% 50% at 50% 50%, transparent 0%, var(--background) 100%)',
          }}
        />
      </div>

      {/* Colour wash */}
      <div
        className={cn(
          'absolute inset-0 z-20 opacity-0',
          'bg-linear-to-br from-rose-500/15 via-transparent to-amber-500/10 dark:from-purple-600/10 dark:to-orange-500/8',
          'animate-[auth-wash_1s_ease-out_0.3s_forwards]',
        )}
        aria-hidden="true"
      />

      {/* Depth gradient */}
      <div className="absolute inset-0 z-20 bg-linear-to-b from-background/80 via-transparent to-background/90" />

      {/* Dot grid pattern — fills entire background, persists below the frame fade */}
      <BGPattern
        variant="dots"
        size={24}
        mask="none"
        fill="currentColor"
        className="z-25 text-foreground/20"
      />

      {/* Decorative frame — border, grid lines, corner arcs (fades out at ~80% height) */}
      <AuthFrame />
    </div>
  );
}
