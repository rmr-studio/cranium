import { cn } from '@/lib/utils';
import { IconRail } from './icon-rail';
import { SignalChat } from './signal-chat';
import { SignalMemo } from './signal-memo';
import { SignalsPanel } from './signals-panel';

const FRAME_WIDTH = 1280;
const FRAME_HEIGHT = 720;

const MOBILE_FRAME_WIDTH = 720;
const MOBILE_FRAME_HEIGHT = 900;

function FullLayout() {
  return (
    <div className="flex h-full w-full bg-background">
      <IconRail />
      <SignalsPanel />
      <SignalChat />
      <SignalMemo />
    </div>
  );
}

function MobileLayout() {
  return (
    <div className="h-full w-full bg-background">
      <SignalChat density="compact" />
    </div>
  );
}

export function SignalPreview({ className }: { className?: string }) {
  return (
    <div
      className={cn('relative h-full w-full overflow-hidden bg-background', className)}
      style={{ containerType: 'inline-size' }}
    >
      {/* Wide (2xl+): fluid 4-col fills the card */}
      <div className="hidden h-full w-full 2xl:flex">
        <FullLayout />
      </div>

      {/* Mid range (sm to 2xl): full 4-col, fixed pixel size, scaled to fit width, anchored bottom.
          Aligned with parent card's aspect-[16/9] which kicks in at sm. */}
      <div className="absolute bottom-0 left-1/2 hidden sm:block 2xl:hidden">
        <div
          style={{
            width: FRAME_WIDTH,
            height: FRAME_HEIGHT,
            transform: `translateX(-50%) scale(calc(100cqw / ${FRAME_WIDTH}px))`,
            transformOrigin: 'bottom center',
          }}
        >
          <FullLayout />
        </div>
      </div>

      {/* Mobile (< sm): chat at fixed portrait pixel size, scaled to container width, anchored bottom.
          Aligned with parent card's aspect-[4/5] portrait below sm. */}
      <div className="absolute bottom-0 left-1/2 sm:hidden">
        <div
          style={{
            width: MOBILE_FRAME_WIDTH,
            height: MOBILE_FRAME_HEIGHT,
            transform: `translateX(-50%) scale(calc(100cqw / ${MOBILE_FRAME_WIDTH}px))`,
            transformOrigin: 'bottom center',
          }}
        >
          <MobileLayout />
        </div>
      </div>
    </div>
  );
}
