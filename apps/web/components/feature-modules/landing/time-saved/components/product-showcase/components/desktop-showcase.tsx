import { useContainerScale } from '@/hooks/use-container-scale';
import { motion } from 'motion/react';

import { FeatureTag } from '@/components/ui/diagrams/brand-ui-primitives';
import { scenarios } from '@/components/feature-modules/landing/time-saved/components/product-showcase/scenario-data';
import { MockActivityTimeline } from '@/components/feature-modules/landing/time-saved/components/product-showcase/components/mock-activity-timeline';
import { MockDataTable } from '@/components/feature-modules/landing/time-saved/components/product-showcase/components/mock-data-table';
import { MockKnowledgePanel } from '@/components/feature-modules/landing/time-saved/components/product-showcase/components/mock-knowledge-panel';
import { MockIconRail, MockSubPanel } from '@/components/feature-modules/landing/time-saved/components/product-showcase/components/mock-shell';

const DESKTOP_WIDTH = 1920;

export function DesktopShowcase({
  activeScenario,
  onScenarioChange,
}: {
  activeScenario: number;
  onScenarioChange: (i: number) => void;
}) {
  const { containerRef, scale } = useContainerScale(DESKTOP_WIDTH);
  const scenario = scenarios[activeScenario];

  return (
    <div ref={containerRef} className="relative w-full">
      <div
        className="origin-top-left"
        style={{
          width: DESKTOP_WIDTH,
          transform: `scale(${scale})`,
          height: 1200 * scale,
        }}
      >
        <div className="relative" style={{ height: 1200 }}>
          {/* Tag: Flexible Data Models */}
          <motion.div
            initial={{ opacity: 0, y: 12 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5 }}
            className="absolute -top-8 left-12"
          >
            <FeatureTag>Flexible Data Models</FeatureTag>
          </motion.div>

          {/* Main App Frame */}
          <motion.div
            initial={{ opacity: 0, y: 12 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5 }}
            className="absolute inset-x-0 top-0 flex overflow-hidden rounded-xl border border-border shadow-lg"
            style={{ height: 1000 }}
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
          </motion.div>

          {/* Activity Timeline */}
          <motion.div
            initial={{ opacity: 0, y: 16 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: 0.3 }}
            className="absolute top-120 -left-10 z-10"
          >
            <div className="relative">
              <MockActivityTimeline scenario={scenario} />
              <div className="absolute right-4 -bottom-4">
                <FeatureTag>Cross-domain activity</FeatureTag>
              </div>
            </div>
          </motion.div>

          {/* Knowledge Base Panel + tag */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: 0.5 }}
            className="absolute top-40 -right-10 z-10"
          >
            <div className="relative">
              <MockKnowledgePanel scenario={scenario} />
              <div className="absolute -bottom-4 left-4">
                <FeatureTag>AI knowledge base</FeatureTag>
              </div>
            </div>
          </motion.div>
        </div>
      </div>
    </div>
  );
}
