import { useContainerScale } from '@/hooks/use-container-scale';
import { MockKnowledgePanel } from './mock-knowledge-panel';
import { customerScenario } from './scenario-data';

export function KnowledgeShowcase() {
  const DESKTOP_WIDTH = 400;
  const DESKTOP_HEIGHT = 900;
  const { containerRef, scale } = useContainerScale(DESKTOP_WIDTH);
  return (
    <div ref={containerRef} className="relative w-full px-12">
      <div
        className="origin-top-left"
        style={{
          width: DESKTOP_WIDTH,
          transform: `scale(${scale})`,
          height: DESKTOP_HEIGHT * scale,
        }}
      >
        <div className="relative" style={{ height: DESKTOP_HEIGHT }}>
          <div className="dark relative z-10 origin-top-left" style={{ height: 940 }}>
            <MockKnowledgePanel
              scenario={customerScenario}
              className="translate-y-30 lg:translate-y-10"
            />
          </div>
        </div>
      </div>
    </div>
  );
}
