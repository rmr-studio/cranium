import { FeatureTag } from '@/components/ui/diagrams/brand-ui-primitives';
import { useContainerScale } from '@/hooks/use-container-scale';
import { customerScenario } from '../scenario-data';
import { MockActivityTimeline } from './mock-activity-timeline';
import { MockDataTable } from './mock-data-table';
import { MockKnowledgePanel } from './mock-knowledge-panel';
import { MockIconRail, MockSubPanel } from './mock-shell';

export function DesktopShowcase() {
  const DESKTOP_WIDTH = 1700;
  const DESKTOP_HEIGHT = 1100;
  const { containerRef, scale } = useContainerScale(DESKTOP_WIDTH);
  return (
    <div ref={containerRef} className="relative hidden w-full px-12 lg:block">
      <div
        className="origin-top-left"
        style={{
          width: DESKTOP_WIDTH,
          transform: `scale(${scale})`,
          height: DESKTOP_HEIGHT * scale,
        }}
      >
        <div className="relative" style={{ height: DESKTOP_HEIGHT * scale }}>
          <div className="dark relative flex" style={{ height: 950 }}>
            <MockIconRail />
            <MockSubPanel activeEntity={customerScenario.entityName} />
            <MockDataTable scenario={customerScenario} />
            <div className="absolute -top-2 -right-4 z-40">
              <FeatureTag>Flexible Data Models</FeatureTag>
            </div>
          </div>
          {/* Activity Timeline */}
          <div className="dark absolute top-90 -left-12 z-10 origin-top translate-y-1/3">
            <div className="relative">
              <MockActivityTimeline scenario={customerScenario} />
              <div className="absolute right-4 -bottom-4">
                <FeatureTag>Cross-domain activity</FeatureTag>
              </div>
            </div>
          </div>
          {/* Knowledge Base Panel + tag */}

          <div
            className="dark rigin-top-left absolute top-30 -right-8 z-10"
            style={{ height: 600 }}
          >
            <div className="relative">
              <MockKnowledgePanel scenario={customerScenario} />
              <div className="absolute -bottom-4 left-4">
                <FeatureTag>AI knowledge base</FeatureTag>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
