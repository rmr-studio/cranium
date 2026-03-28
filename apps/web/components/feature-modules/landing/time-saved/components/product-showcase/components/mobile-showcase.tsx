import { useContainerScale } from '@/hooks/use-container-scale';

import { MockActivityTimeline } from '@/components/feature-modules/landing/time-saved/components/product-showcase/components/mock-activity-timeline';
import { MockDataTable } from '@/components/feature-modules/landing/time-saved/components/product-showcase/components/mock-data-table';
import { MockKnowledgePanel } from '@/components/feature-modules/landing/time-saved/components/product-showcase/components/mock-knowledge-panel';
import {
  MockIconRail,
  MockSubPanel,
} from '@/components/feature-modules/landing/time-saved/components/product-showcase/components/mock-shell';
import { customerScenario } from '@/components/feature-modules/landing/time-saved/components/product-showcase/scenario-data';

const MOBILE_WIDTH = 1400;
const MOBILE_CONTENT_HEIGHT = 1600;

export function MobileShowcase() {
  const { containerRef, scale } = useContainerScale(MOBILE_WIDTH);

  return (
    <div ref={containerRef} className="relative w-full">
      <div
        className="origin-top-left"
        style={{
          width: MOBILE_WIDTH,
          transform: `scale(${scale})`,
          height: MOBILE_CONTENT_HEIGHT * scale,
        }}
      >
        <div className="relative" style={{ width: MOBILE_WIDTH, height: MOBILE_CONTENT_HEIGHT }}>
          {/* Main App Frame — back layer */}
          <div
            className="absolute inset-x-0 top-0 flex overflow-hidden rounded-xl shadow-lg"
            style={{ height: 820 }}
          >
            <MockIconRail />
            <MockSubPanel activeEntity={customerScenario.entityName} />
            <MockDataTable scenario={customerScenario} />
          </div>

          {/* Knowledge Base — right side, overlapping */}
          <div className="absolute top-60 right-30 z-10">
            <div className="relative">
              <MockKnowledgePanel scenario={customerScenario} />
            </div>
          </div>

          {/* Activity Timeline — bottom left, overlapping */}
          <div className="absolute top-1/3 z-20">
            <div className="relative">
              <MockActivityTimeline scenario={customerScenario} />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
