import { cn } from '@/lib/utils';
import { IconRail } from './icon-rail';
import { SignalChat } from './signal-chat';
import { SignalMemo } from './signal-memo';
import { SignalsPanel } from './signals-panel';

const FRAME_WIDTH = 1280;
const FRAME_HEIGHT = 720;

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

export function SignalPreview({ className }: { className?: string }) {
  return (
    <div
      className={cn('relative h-full w-full bg-background sm:overflow-hidden', className)}
      style={{ containerType: 'inline-size' }}
    >
      {/* Wide (lg+): fluid 4-col fills the card */}
      <div className="hidden h-full w-full 2xl:flex">
        <FullLayout />
      </div>

      {/* Mid range (sm to lg): same 4-col, fixed pixel size, scaled to fit, anchored bottom */}
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

      {/* Mobile (< sm): two cards overlapping, context layer pushed down */}
      <div className="relative h-full w-full p-3 sm:hidden">
        {/* Chat — primary card, full inset */}
        <div className="absolute inset-x-3 top-3 bottom-3 overflow-hidden rounded-xl border border-border bg-background shadow-md ring-1 ring-foreground/5">
          <SignalChat density="compact" />
        </div>
        {/* Memo — secondary card, overlapping right side, shifted down past the chat top */}
        <div className="absolute right-2 -bottom-12 w-[64%] overflow-hidden rounded-xl border border-border bg-sidebar shadow-lg ring-1 ring-foreground/5">
          <SignalMemo className="h-full w-full border-l-0" />
        </div>
      </div>
    </div>
  );
}
