import { useContainerScale } from '@/hooks/use-container-scale';

import { MockActivityTimeline } from '@/components/feature-modules/landing/time-saved/components/product-showcase/components/mock-activity-timeline';
import { MockDataTable } from '@/components/feature-modules/landing/time-saved/components/product-showcase/components/mock-data-table';
import { MockKnowledgePanel } from '@/components/feature-modules/landing/time-saved/components/product-showcase/components/mock-knowledge-panel';
import {
  MockIconRail,
  MockSubPanel,
} from '@/components/feature-modules/landing/time-saved/components/product-showcase/components/mock-shell';
import { scenarios } from '@/components/feature-modules/landing/time-saved/components/product-showcase/scenario-data';
import { FeatureTag } from '@/components/ui/diagrams/brand-ui-primitives';

const MOBILE_WIDTH = 1100;
const MOBILE_CONTENT_HEIGHT = 1800;

export function MobileShowcase({
  activeScenario,
  onScenarioChange,
}: {
  activeScenario: number;
  onScenarioChange: (i: number) => void;
}) {
  const { containerRef, scale } = useContainerScale(MOBILE_WIDTH);
  const scenario = scenarios[activeScenario];

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
          {/* Tag */}
          <div className="absolute -top-8 left-4">
            <FeatureTag>Flexible Data Models</FeatureTag>
          </div>

          {/* Main App Frame — back layer */}
          <div
            className="absolute inset-x-0 top-0 flex overflow-hidden rounded-xl shadow-lg"
            style={{ height: 820 }}
          >
            <MockIconRail />
            <MockSubPanel
              activeEntity={scenario.entityName}
              onEntityClick={(name) => {
                const idx = scenarios.findIndex((s) => s.entityName === name);
                if (idx !== -1) onScenarioChange(idx);
              }}
            />
            <MockDataTable scenario={scenario} />
          </div>

          {/* Knowledge Base — right side, overlapping */}
          <div className="absolute top-80 right-20 z-10">
            <div className="relative">
              <MockKnowledgePanel scenario={scenario} />
              <div className="absolute -bottom-4 left-4">
                <FeatureTag>AI knowledge base</FeatureTag>
              </div>
            </div>
          </div>

          {/* Activity Timeline — bottom left, overlapping */}
          <div className="absolute top-180 right-20 z-20">
            <div className="relative">
              <MockActivityTimeline scenario={scenario} />
              <div className="absolute right-4 -bottom-4">
                <FeatureTag>Cross-domain activity</FeatureTag>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
