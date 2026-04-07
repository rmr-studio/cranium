'use client';

import { Component, Suspense, type ReactNode } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useCurrentView, useStackDepth, useSidePanelActions } from '../context/side-panel-provider';
import { viewRegistry } from './panel-view-registry';
import { PanelSkeleton } from './panel-skeleton';
import { PanelViewFrame } from './panel-view-frame';
import { PanelErrorFallback } from './panel-error-fallback';

// Error boundary for lazy-loaded view components
interface ErrorBoundaryState { hasError: boolean }

class PanelErrorBoundary extends Component<
  { fallback: ReactNode; children: ReactNode; resetKey: string },
  ErrorBoundaryState
> {
  constructor(props: { fallback: ReactNode; children: ReactNode; resetKey: string }) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  componentDidUpdate(prevProps: { resetKey: string }) {
    if (prevProps.resetKey !== this.props.resetKey) {
      this.setState({ hasError: false });
    }
  }

  render() {
    if (this.state.hasError) return this.props.fallback;
    return this.props.children;
  }
}

/**
 * Renders the current stack view using the registry.
 * Returns null when the stack is empty (caller should render panel root instead).
 */
export function PanelViewRenderer() {
  const currentView = useCurrentView();
  const stackDepth = useStackDepth();
  const { popView, clearStack } = useSidePanelActions();

  if (!currentView) return null;

  const ViewComponent = viewRegistry[currentView.type];
  const viewKey = `${currentView.type}-${JSON.stringify(currentView)}`;

  return (
    <PanelViewFrame
      title={currentView.title}
      onBack={stackDepth > 0 ? popView : undefined}
      onClose={clearStack}
    >
      <PanelErrorBoundary
        resetKey={viewKey}
        fallback={<PanelErrorFallback onRetry={() => clearStack()} />}
      >
        <AnimatePresence mode="wait">
          <motion.div
            key={viewKey}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15, ease: 'easeOut' }}
            className="h-full"
          >
            <Suspense fallback={<PanelSkeleton />}>
              <ViewComponent {...(currentView as any)} />
            </Suspense>
          </motion.div>
        </AnimatePresence>
      </PanelErrorBoundary>
    </PanelViewFrame>
  );
}
