import { IconRail } from './icon-rail';
import { SignalChat } from './signal-chat';
import { SignalMemo } from './signal-memo';
import { SignalsPanel } from './signals-panel';

const FRAME_WIDTH = 1920;
const FRAME_HEIGHT = 1080;

function FullLayout() {
  return (
    <div className="flex h-full w-full overflow-hidden bg-background">
      <IconRail className="hidden lg:flex" />
      <SignalsPanel />
      <SignalChat />
      <SignalMemo />
    </div>
  );
}

function MobileLayout() {
  return (
    <div className="relative h-fit w-fit lg:hidden">
      <div className="relative z-70 h-full max-h-[40rem] max-h-[55rem] max-w-[90vw] overflow-hidden rounded-lg border bg-white shadow sm:w-full md:max-h-[60rem] lg:hidden">
        <SignalChat density="compact" />
      </div>
      <SignalMemo
        compact
        className="absolute -right-8 -bottom-8 z-80 hidden rounded-lg border shadow-lg sm:block lg:hidden"
      />
    </div>
  );
}

export function SignalPreview({ className }: { className?: string }) {
  return (
    <>
      <div
        className={`@container relative z-70 mx-auto hidden w-full max-w-[95vw] overflow-hidden rounded-md bg-background shadow-lg lg:block xl:max-w-[min(80vw,var(--breakpoint-3xl))] 3xl:max-w-(--breakpoint-2xl) ${className ?? ''}`}
        style={{ aspectRatio: `${FRAME_WIDTH} / ${FRAME_HEIGHT}` }}
      >
        {/* Desktop frame, lg viewport+ : render at 1440x810, scale via container width */}
        <div className="absolute top-0 left-0 hidden origin-top-left lg:block">
          <div
            className="origin-top-left"
            style={{
              width: FRAME_WIDTH,
              height: FRAME_HEIGHT,
              transform: `scale(calc(100cqw / ${FRAME_WIDTH}px))`,
            }}
          >
            <FullLayout />
          </div>
        </div>
      </div>
      {/* Mobile frame, < lg : render at 720x900, scale via container width */}

      <MobileLayout />
    </>
  );
}
