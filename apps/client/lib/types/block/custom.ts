// Custom types specific to block domain

// Runtime enum re-exports from generated models
export { BlockMetadataType } from '../models/BlockMetadataType';
export { NodeType } from '../models/NodeType';
export { RenderType } from '../models/RenderType';
export { BlockOperationType } from '../models/BlockOperationType';
export { BlockListOrderingMode } from '../models/BlockListOrderingMode';
export { ListFilterLogicType } from '../models/ListFilterLogicType';
export { BlockReferenceFetchPolicy } from '../models/BlockReferenceFetchPolicy';
export { ValidationScope } from '../models/ValidationScope';
export { SortDir } from '../models/SortDir';
export { Presentation } from '../models/Presentation';
export { ReferenceType } from '../models/ReferenceType';

import type { ChildNodeProps } from '@cranium/utils';
import type { EntityType } from '@/lib/types/entity';
import type { ReactNode } from 'react';
import type { GridStack, GridStackNode, GridStackOptions, GridStackWidget } from 'gridstack';
import type {
  ContentNode,
  ReferenceNode,
  EntityReference,
  BlockTreeReference,
  BlockContentMetadata,
  BlockReferenceMetadata,
  EntityReferenceMetadata,
  BlockHydrationResult,
  HydrateBlocksRequest,
  RenderContent,
  BlockTree,
  BlockEnvironment,
  BlockTreeLayout,
  AddBlockOperation,
  RemoveBlockOperation,
  MoveBlockOperation,
  UpdateBlockOperation,
  ReorderBlockOperation,
  StructuralOperationRequest,
  GridRect,
  LayoutGrid,
  LayoutGridItem,
} from './models';
import { type EntityReferenceRequest } from '@/lib/types/entity';

/* -------------------------------------------------------------------------- */
/*                           Composite / Alias Types                          */
/* -------------------------------------------------------------------------- */

// Composite types for convenience
export type BlockNode = ContentNode | ReferenceNode;
export type MetadataUnion = BlockContentMetadata | BlockReferenceMetadata | EntityReferenceMetadata;
export type ReferencePayloadUnion = EntityReference | BlockTreeReference;

// Semantic aliases for clarity
export type EntityReferencePayload = EntityReference;
export type BlockReferencePayload = BlockTreeReference;
export type WidgetRenderStructure = RenderContent;
export type EntityReferenceHydrationRequest = EntityReferenceRequest;
export type HydrateBlockRequest = HydrateBlocksRequest;
export type HydrateBlockResponse = Record<string, BlockHydrationResult>;

/* -------------------------------------------------------------------------- */
/*                          Command System Types                              */
/* -------------------------------------------------------------------------- */

/**
 * Snapshot of the entire layout state at a point in time
 * Used for rollback and conflict resolution
 */
export interface LayoutSnapshot {
  /** BlockEnvironment state (structural - hierarchy, trees, etc.) */
  blockEnvironment: EditorEnvironment;

  /** GridStack layout (positioning, sizing, nesting) */
  gridLayout: GridStackOptions;

  /** Timestamp when snapshot was created */
  timestamp: string;

  /** Version number from backend (for optimistic locking) */
  version: number;
}

/**
 * Base interface for all layout commands
 * Implements the Command Pattern for undo/redo functionality
 */
export interface LayoutCommand {
  /** Unique identifier for this command */
  id: string;

  /** Type of command for categorization and filtering */
  type: LayoutCommandType;

  /** Human-readable description for UI display */
  description: string;

  /** Timestamp when command was created */
  timestamp: number;

  /** Execute the command (forward operation) */
  execute(): void;

  /** Undo the command (reverse operation) */
  undo(): void;

  /** Optional: Check if command can be executed */
  canExecute?(): boolean;

  /** Optional: Check if command can be undone */
  canUndo?(): boolean;

  /** Metadata for debugging and analytics */
  metadata?: Record<string, unknown>;
}

/**
 * Command types categorize different kinds of operations
 */
export enum LayoutCommandType {
  /** Adding a new block to the environment */
  ADD_BLOCK = 'ADD_BLOCK',

  /** Removing a block from the environment */
  REMOVE_BLOCK = 'REMOVE_BLOCK',

  /** Moving a block to a different parent (re-parenting) */
  MOVE_BLOCK = 'MOVE_BLOCK',

  /** Resizing a block (width/height change) */
  RESIZE_BLOCK = 'RESIZE_BLOCK',

  /** Repositioning a block within same parent (x/y change in GridStack) */
  REPOSITION_BLOCK = 'REPOSITION_BLOCK',

  /** Reordering a block within a list (orderIndex change) */
  REORDER_BLOCK = 'REORDER_BLOCK',

  /** Updating block content/configuration */
  UPDATE_BLOCK = 'UPDATE_BLOCK',

  /** Batch operation containing multiple commands */
  BATCH = 'BATCH',
}

/**
 * Categorizes commands as structural or layout-only
 */
export type CommandCategory = 'structural' | 'layout';

/**
 * Helper to determine if a command type is structural
 */
export function isStructuralCommand(type: LayoutCommandType): boolean {
  return [
    LayoutCommandType.ADD_BLOCK,
    LayoutCommandType.REMOVE_BLOCK,
    LayoutCommandType.MOVE_BLOCK,
    LayoutCommandType.UPDATE_BLOCK,
    LayoutCommandType.REORDER_BLOCK,
  ].includes(type);
}

/**
 * Helper to get category from command type
 */
export function getCommandCategory(
  type: LayoutCommandType,
  command?: LayoutCommand,
): CommandCategory {
  if (type === LayoutCommandType.BATCH && command) {
    // Check if batch contains any structural commands
    // Note: BatchCommand interface check - commands property exists on batch commands
    const batchCmd = command as LayoutCommand & { commands?: LayoutCommand[] };
    if (batchCmd.commands?.some((cmd) => isStructuralCommand(cmd.type))) {
      return 'structural';
    }
  }
  return isStructuralCommand(type) ? 'structural' : 'layout';
}

/**
 * Context provided to commands for execution
 * Contains all necessary providers and utilities
 */
export interface CommandContext {
  /** BlockEnvironment operations */
  blockEnvironment: {
    addBlock: (block: BlockNode, parentId?: string | null, index?: number | null) => string;
    removeBlock: (blockId: string) => void;
    moveBlock: (blockId: string, targetParentId: string | null) => void;
    updateBlock: (blockId: string, updatedContent: BlockNode) => void;
    getBlock: (blockId: string) => BlockNode | undefined;
    getParentId: (blockId: string) => string | null;
  };

  /** GridStack operations */
  gridStack: {
    updateWidget: (id: string, widget: Partial<GridStackWidget>) => void;
    getWidget: (id: string) => GridStackWidget | undefined;
  };
}

/**
 * Configuration for the command history system
 */
export interface CommandHistoryConfig {
  /** Maximum number of commands to keep in history */
  maxHistorySize?: number;

  /** Enable automatic command merging (e.g., multiple resize events) */
  enableMerging?: boolean;

  /** Time window for merging similar commands (ms) */
  mergingWindow?: number;
}

/**
 * State of the command history
 */
export interface CommandHistoryState {
  /** Stack of executed commands (for undo) */
  undoStack: LayoutCommand[];

  /** Stack of undone commands (for redo) */
  redoStack: LayoutCommand[];

  /** Current snapshot (last saved state) */
  currentSnapshot: LayoutSnapshot | null;

  /** Pending snapshot (current working state before save) */
  pendingSnapshot: LayoutSnapshot | null;

  /** Whether there are unsaved changes */
  hasUnsavedChanges: boolean;
}

/**
 * Conflict resolution options when save fails due to version mismatch
 */
export interface ConflictResolution {
  /** What action to take */
  action: 'keep-mine' | 'use-theirs' | 'review' | 'cancel';

  /** Optional: User's decision after review */
  reviewDecision?: 'keep-mine' | 'use-theirs';
}

/**
 * Structural operation type for serialization
 */
export type StructuralOperationType =
  | LayoutCommandType.ADD_BLOCK
  | LayoutCommandType.REMOVE_BLOCK
  | LayoutCommandType.MOVE_BLOCK
  | LayoutCommandType.UPDATE_BLOCK
  | LayoutCommandType.REORDER_BLOCK;

export type StructuralOperationData = StructuralOperationRequest['data'];

// Re-export operation types from models for convenience
export type {
  StructuralOperationRequest,
  AddBlockOperation,
  RemoveBlockOperation,
  MoveBlockOperation,
  UpdateBlockOperation,
  ReorderBlockOperation,
};

/* -------------------------------------------------------------------------- */
/*                          Editor Environment Types                          */
/* -------------------------------------------------------------------------- */

/** Metadata describing the environment itself. */
export interface EditorEnvironmentMetadata {
  name: string;
  description?: string;
  workspaceId: string;
  createdAt: Date;
  updatedAt: Date;
}

export interface DetachResult {
  success: boolean;
  root: BlockNode;
  detachedNode: BlockNode | null;
}

export interface InsertResult<T> {
  payload: T;
  success: boolean;
}

/**
 * Internal environment model used by the provider.
 * - `trees` holds each top-level block tree.
 * - `hierarchy` maps blockId -> parentBlockId (null for roots).
 * - `treeIndex` maps blockId -> owning tree root id.
 * - `layouts` and `uiMetadata` store per-block editor state.
 */
export interface EditorEnvironment {
  trees: BlockTree[];
  metadata: EditorEnvironmentMetadata;
  // Lookup for parent IDs (null for top-level)
  hierarchy: Map<string, string | null>;
  // Lookup for which root a node belongs to
  treeIndex: Map<string, string>;
}

export interface BlockEnvironmentProviderProps extends ChildNodeProps {
  /** Workspace and entity context for the environment */
  workspaceId: string;
  entityId: string;
  entityType: EntityType;

  /** Initial block environment to load */
  environment: BlockEnvironment;
}

/** Context contract exposed to consumers. */
export interface BlockEnvironmentContextValue {
  environment: EditorEnvironment;

  // Entity Context
  workspaceId: string;
  entityId: string;
  entityType: EntityType;

  /** Full layout object with metadata */
  layout: BlockTreeLayout;

  /** Layout ID for persistence operations */
  layoutId?: string;
  isInitialized: boolean;
  setIsInitialized(value: boolean): void;
  addBlock(tree: BlockNode, parentId?: string | null): string;
  removeBlock(blockId: string): void;
  updateBlock(blockId: string, updatedContent: BlockNode): void;

  getBlock(blockId: string): BlockNode | undefined;
  getTrees(): BlockTree[];

  insertBlock(child: BlockNode, parentId: string, index: number | null): string;
  moveBlock(blockId: string, targetParentId: string | null): void;

  // Parent Retrieval
  getParentId(blockId: string): string | null;
  /**
   * Retrieves the parent block of a given block ID.
   * Note: The children of the parent block will NOT be populated.
   * @param blockId The ID of the block whose parent is to be retrieved.
   * @returns The parent BlockNode, or null if it has no parent.
   */
  getParent(blockId: string): BlockNode | null;

  // Children Retrieval
  getChildren(blockId: string): string[];
  getDescendants(blockId: string): Record<string, string>;
  isDescendantOf(blockId: string, ancestorId: string): boolean;
  updateHierarchy(blockId: string, newParentId: string | null): void;

  reorderBlock(blockId: string, parentId: string, targetIndex: number): void;

  clear(): void;

  /** Replace the entire environment with a snapshot (used when discarding changes). */
  hydrateEnvironment(snapshot: EditorEnvironment): void;

  /** Capture the current environment state as a detached snapshot. */
  getEnvironmentSnapshot(): EditorEnvironment;
}

/* -------------------------------------------------------------------------- */
/*                            Grid System Types                               */
/* -------------------------------------------------------------------------- */

// Environment model for GridStack integration
export interface GridEnvironment {
  widgetMetaMap: Map<string, GridStackWidget>;
  addedWidgets: Set<string>;
}

export interface GridProviderProps extends ChildNodeProps {
  initialOptions: GridStackOptions;
}

export interface GridActionResult<T extends GridStackWidget> {
  success: boolean;
  node: T | null;
}

export interface GridstackContextValue {
  initialOptions: GridStackOptions;
  environment: GridEnvironment;
  save: () => GridStackOptions | undefined;
  gridStack: GridStack | null;
  setGridStack: React.Dispatch<React.SetStateAction<GridStack | null>>;
  addWidget: (
    widget: GridStackWidget,
    meta: WidgetRenderStructure,
    parent?: GridStackNode,
  ) => GridActionResult<GridStackNode>;
  removeWidget: (id: string) => void;
  widgetExists: (id: string) => boolean;
  findWidget: (id: string) => GridActionResult<GridStackNode>;
  reloadEnvironment: (layout: GridStackOptions) => void;
}

// Re-export layout types from models for convenience
export type { GridRect, LayoutGrid, LayoutGridItem, BlockTreeLayout };

/* -------------------------------------------------------------------------- */
/*                            Panel System Types                              */
/* -------------------------------------------------------------------------- */

export type ResizePosition = 'nw' | 'ne' | 'sw' | 'se';

export interface SlashMenuItem {
  id: string;
  label: string;
  description?: string;
  icon?: ReactNode;
  onSelect?: () => void;
}

export interface QuickActionItem {
  id: string;
  label: string;
  // Takes in block id
  onSelect: (id: string) => void;
  shortcut?: string;
  description?: string;
}

/* -------------------------------------------------------------------------- */
/*                           Render System Types                              */
/* -------------------------------------------------------------------------- */

export interface ProviderProps {
  onUnknownType?: (args: CallbackProvider) => void;
  wrapElement?: (args: WrapElementProvider) => ReactNode;
}

export interface CallbackProvider {
  widget: GridStackWidget;
  content: WidgetRenderStructure;
}

export interface WrapElementProvider extends CallbackProvider, ChildNodeProps {}

export type RenderElementContextValue = {
  widget: {
    id: string;
    container: HTMLElement | null;
    requestResize: () => void;
  };
};

/* -------------------------------------------------------------------------- */
/*                        Save Environment Types                              */
/* -------------------------------------------------------------------------- */

// Re-export request/response types from models
export type { SaveEnvironmentRequest, SaveEnvironmentResponse } from './models';
