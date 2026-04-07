import { lazy, type ComponentType, type LazyExoticComponent } from 'react';
import type { SidePanelView, SidePanelViewType } from '../types/side-panel.types';

/**
 * Props that every detail view component receives.
 * The component gets the full SidePanelView object matching its type.
 */
export type ViewComponentProps<T extends SidePanelViewType> = Extract<SidePanelView, { type: T }>;

/**
 * Registry mapping each view type to its lazy-loaded component.
 *
 * To add a new view:
 * 1. Add a member to `SidePanelView` in `side-panel.types.ts`
 * 2. Create the component in `components/ui/sidebar/views/`
 * 3. Add a lazy entry here
 *
 * TypeScript enforces exhaustiveness via the `satisfies` check.
 */
export const viewRegistry: Record<SidePanelViewType, LazyExoticComponent<ComponentType<any>>> = {
  'definition-detail': lazy(() =>
    import('../views/definition-detail-view').then((m) => ({ default: m.DefinitionDetailView })),
  ),
  'entity-notes': lazy(() =>
    import('../views/entity-notes-view').then((m) => ({ default: m.EntityNotesView })),
  ),
  'integration-detail': lazy(() =>
    import('../views/integration-detail-view').then((m) => ({ default: m.IntegrationDetailView })),
  ),
} satisfies Record<SidePanelViewType, LazyExoticComponent<ComponentType<any>>>;
