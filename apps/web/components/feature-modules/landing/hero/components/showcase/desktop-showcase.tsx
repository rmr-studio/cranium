import { useContainerScale } from '@/hooks/use-container-scale';
import { MockActivityTimeline } from './mock-activity-timeline';
import { MockDataTable } from './mock-data-table';
import { MockIconRail, MockSubPanel } from './mock-shell';
import { customerScenario } from './scenario-data';

export function DesktopShowcase() {
  const DESKTOP_WIDTH = 1300;
  const DESKTOP_HEIGHT = 1100;
  const { containerRef, scale } = useContainerScale(DESKTOP_WIDTH);
  return (
    <div ref={containerRef} className="relative w-full scale-100 px-12 lg:block">
      <div
        className="origin-top-left"
        style={{
          width: DESKTOP_WIDTH,
          transform: `scale(${scale})`,
          height: DESKTOP_HEIGHT * scale,
        }}
      >
        <div className="relative" style={{ height: DESKTOP_HEIGHT * scale }}>
          <div
            className="relative flex translate-y-10 overflow-hidden rounded-lg"
            style={{ height: 800 }}
          >
            <MockIconRail />
            <MockSubPanel activeEntity={customerScenario.entityName} />
            <MockDataTable scenario={customerScenario} />
          </div>
          {/* Activity Timeline */}
          <div className="absolute top-1/3 -left-8 z-10 origin-top translate-y-1/3 md:-right-12">
            <div className="relative">
              <MockActivityTimeline scenario={customerScenario} />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
