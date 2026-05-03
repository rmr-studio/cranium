import { IntegrationDefinitionModel } from '@/lib/types';
import type { ComponentType } from 'react';

/**
 * All valid panel IDs — used for runtime validation (e.g. localStorage hydration)
 * and as the source of truth for the PanelId union.
 */
export const PANEL_IDS = [
  'workspaces',
  'overview',
  'entities',
  'knowledge',
  'notes',
  'billing',
] as const;

/**
 * Identifies which root panel is selected in the icon rail.
 */
export type PanelId = (typeof PANEL_IDS)[number];

/**
 * Discriminated union of all views that can be pushed onto the side panel stack.
 * Add new members here when creating new detail views.
 *
 * Each member must have:
 * - `type`: unique string literal (used as registry key)
 * - `title`: display title for the panel header
 * - any data props the detail component needs
 */

export type DefinitionDetailView = {
  type: 'definition-detail';
  integration: IntegrationDefinitionModel;
  workspaceId: string;
};

export type EntityNotesView = {
  type: 'entity-notes';
  title: string;
  entityId: string;
  workspaceId: string;
};

export type IntegrationDetailView = {
  type: 'integration-detail';
  title: string;
  integrationId: string;
  workspaceId: string;
};

export type SidePanelView = DefinitionDetailView | EntityNotesView | IntegrationDetailView;

/**
 * Extract the `type` literal union from SidePanelView.
 */
export type SidePanelViewType = SidePanelView['type'];

/**
 * Registry entry for a panel root (icon rail section).
 */
export interface PanelRegistryEntry {
  title: string;
  component: ComponentType;
  /**
   * When true, the SubPanel skips its default title/close header and the
   * panel component is responsible for rendering its own header (including
   * a close affordance).
   */
  hideHeader?: boolean;
}

/**
 * Maximum number of views on the stack before oldest entries are dropped.
 */
export const SIDE_PANEL_MAX_STACK_DEPTH = 10;

/**
 * Default panel shown on first load.
 */
export const DEFAULT_PANEL: PanelId = 'overview';

/**
 * Resize constraints (percentage of viewport width).
 */
export const PANEL_MIN_SIZE_PCT = 15;
export const PANEL_MAX_SIZE_PCT = 40;
export const PANEL_DEFAULT_SIZE_PCT = 25;
