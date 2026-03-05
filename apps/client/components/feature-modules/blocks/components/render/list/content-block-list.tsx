/**
 * ContentBlockList - Renders a list of content blocks with drag-and-drop reordering.
 *
 * Uses dnd-kit for list item reordering (MANUAL mode) or displays items in sorted order (SORTED mode).
 * This component handles the internal list logic, while the list block itself remains a GridStack panel.
 */

'use client';

import { Button } from '@riven/ui/button';
import { BlockListOrderingMode, ListFilterLogicType } from "@/lib/types/block";
import { cn } from '@riven/utils';
import {
  closestCenter,
  DndContext,
  DragEndEvent,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useBlockFocus } from '../../../context/block-focus-provider';
import { useTrackedEnvironment } from '../../../context/tracked-environment-provider';
import {
    type BlockListConfiguration,
    type BlockNode,
    isContentNode,
} from "@/lib/types/block";
import {
  filterChildren,
  FilterSpec,
  getUniformBlockType,
  isUniBlockList,
  sortChildren,
  SortSpec,
} from '../../../util/list/list-sorting.util';
import { ListFilterControls } from './ListFilterControls';
import { ListSortControls } from './ListSortControls';
import { ListPanel } from './list.container';
import { ListItem } from './list.item';

interface ContentBlockListProps {
  id: string;
  config: BlockListConfiguration;
  children: BlockNode[] | undefined;
  render: (node: BlockNode) => ReactNode;
  renderControlsInWrapper?: boolean; // If true, don't render controls here (they'll be in PanelWrapper)
}

interface ListControlsProps {
  uniformBlockType: BlockNode['block']['type'] | null;
  isUniBlock: boolean;
  currentMode: BlockListOrderingMode;
  onModeChange: (mode: BlockListOrderingMode) => void;
  activeSort: SortSpec | undefined;
  setActiveSort: (sort: SortSpec | undefined) => void;
  activeFilters: FilterSpec[];
  setActiveFilters: (filters: FilterSpec[]) => void;
  filterLogic: ListFilterLogicType;
}

/**
 * Separate component for list sort/filter controls
 * Can be used standalone or within ContentBlockList
 */
export const ListControls: React.FC<ListControlsProps> = ({
  uniformBlockType,
  isUniBlock,
  currentMode,
  onModeChange,
  activeSort,
  setActiveSort,
  activeFilters,
  setActiveFilters,
  filterLogic,
}) => {
    const isSortedMode = currentMode === BlockListOrderingMode.Sorted;
    const isManualMode = currentMode === BlockListOrderingMode.Manual;

    return (
        <div className="space-y-2">
            {/* Mode toggle - only show for uni-block lists */}
            {isUniBlock && (
                <div className="flex items-center justify-between">
                    <span className="text-sm font-medium">List Mode:</span>
                    <div className="flex items-center gap-2">
                        <Button
                            type="button"
                            size="xs"
                            className={cn(
                                isManualMode
                                    ? "bg-primary text-primary-foreground"
                                    : "bg-muted text-muted-foreground hover:bg-muted/80"
                            )}
                            onClick={() => onModeChange(BlockListOrderingMode.Manual)}
                        >
                            Manual Order
                        </Button>
                        <Button
                            type="button"
                            size="xs"
                            className={cn(
                                isSortedMode
                                    ? "bg-primary text-primary-foreground"
                                    : "bg-muted text-muted-foreground hover:bg-muted/80"
                            )}
                            onClick={() => onModeChange(BlockListOrderingMode.Sorted)}
                        >
                            Sorted View
                        </Button>
                    </div>
                </div>
            )}

            {/* Sort/filter controls - only show in SORTED mode */}
            {isSortedMode && uniformBlockType && (
                <>
                    <ListSortControls
                        blockType={uniformBlockType}
                        currentSort={activeSort}
                        onSortChange={setActiveSort}
                    />
                    <ListFilterControls
                        blockType={uniformBlockType}
                        currentFilters={activeFilters}
                        filterLogic={filterLogic}
                        onFiltersChange={setActiveFilters}
                    />
                </>
            )}

            {/* Manual mode hint */}
            {isManualMode && isUniBlock && (
                <div className="text-xs text-muted-foreground">
                    Drag and drop items to reorder manually
                </div>
            )}
        

      {/* Sort/filter controls - only show in SORTED mode */}
      {isSortedMode && uniformBlockType && (
        <>
          <ListSortControls
            blockType={uniformBlockType}
            currentSort={activeSort}
            onSortChange={setActiveSort}
          />
          <ListFilterControls
            blockType={uniformBlockType}
            currentFilters={activeFilters}
            filterLogic={filterLogic}
            onFiltersChange={setActiveFilters}
          />
        </>
      )}

      {/* Manual mode hint */}
      {isManualMode && isUniBlock && (
        <div className="text-xs text-muted-foreground">Drag and drop items to reorder manually</div>
      )}
    </div>
  );
};

/**
 * Main list component that handles drag-and-drop reordering for content block lists.
 */
export const ContentBlockList: React.FC<ContentBlockListProps> = ({
  id,
  config: blockListConfig,
  children = [],
  render,
  renderControlsInWrapper = false,
}) => {
  const { reorderTrackedBlock, updateTrackedBlock, blockEnvironment } = useTrackedEnvironment();
  const { getBlock } = blockEnvironment;
  const { acquireLock } = useBlockFocus();
  const { config: listConfig } = blockListConfig;

  const dragLockRef = useRef<(() => void) | null>(null);
  const configUpdateTimerRef = useRef<NodeJS.Timeout | null>(null);

  // Detect if list contains uniform block types
  const isUniBlock = useMemo(() => isUniBlockList(children), [children]);
  const uniformType = useMemo(() => getUniformBlockType(children), [children]);
  const uniformBlockType = useMemo(
    () => (uniformType && children[0] ? children[0].block.type : null),
    [uniformType, children],
  );

  // Runtime sort/filter/mode overrides (starts with config defaults)
  const [activeSort, setActiveSort] = useState<SortSpec | undefined>(listConfig.sort);
  const [activeFilters, setActiveFilters] = useState<FilterSpec[]>(listConfig.filters || []);
  const [activeMode, setActiveMode] = useState<BlockListOrderingMode>(listConfig.mode);

    // Determine effective mode - force MANUAL if not uni-block
    const effectiveMode = isUniBlock ? activeMode : BlockListOrderingMode.Manual;

  // Debounced persistence of configuration changes to backend
  useEffect(() => {
    // Clear any existing timer
    if (configUpdateTimerRef.current) {
      clearTimeout(configUpdateTimerRef.current);
    }

    // Check if config has changed from the original
    const configChanged =
      activeMode !== listConfig.mode ||
      JSON.stringify(activeSort) !== JSON.stringify(listConfig.sort) ||
      JSON.stringify(activeFilters) !== JSON.stringify(listConfig.filters || []);

    if (!configChanged) {
      return; // No changes to persist
    }

    // Debounce: wait 1.5 seconds after last change before persisting
    configUpdateTimerRef.current = setTimeout(() => {
      const block = getBlock(id);
      if (!block || !isContentNode(block)) return;

      const updatedConfig: BlockListConfiguration = {
        ...blockListConfig,
        config: {
          ...listConfig,
          mode: activeMode,
          sort: activeSort,
          filters: activeFilters,
        },
      };

      // Update the listConfig in the block's payload
      const updatedBlock: BlockNode = {
        ...block,
        block: {
          ...block.block,
          payload: {
            ...block.block.payload,
            listConfig: updatedConfig,
          },
        },
      };

      // Persist to backend via tracked environment
      updateTrackedBlock(id, updatedBlock);
    }, 1500);

    // Cleanup timer on unmount
    return () => {
      if (configUpdateTimerRef.current) {
        clearTimeout(configUpdateTimerRef.current);
      }
    };
  }, [activeMode, activeSort, activeFilters, blockListConfig, id, getBlock, updateTrackedBlock]);

  // Apply sorting/filtering to children
  const processedChildren = useMemo(() => {
    if (effectiveMode !== 'SORTED') return children;

    let result = [...children];

    // Apply filters first
    if (activeFilters.length > 0 && uniformType) {
      result = filterChildren(result, activeFilters, listConfig.filterLogic || 'AND');
    }

    // Apply sorting
    if (activeSort && uniformType) {
      result = sortChildren(result, activeSort);
    }

    return result;
  }, [children, effectiveMode, activeSort, activeFilters, listConfig.filterLogic, uniformType]);

  // Configure dnd-kit sensors for pointer and keyboard interaction
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 4, // Require 8px movement before drag starts (prevents accidental drags)
      },
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  );

  const handleDragStart = useCallback(() => {
    if (dragLockRef.current) return;
    dragLockRef.current = acquireLock({
      id: `list-drag-${id}`,
      reason: 'List drag in progress',
      suppressHover: true,
      suppressSelection: true,
    });
  }, [acquireLock, id]);

  const releaseDragLock = useCallback(() => {
    if (!dragLockRef.current) return;
    dragLockRef.current();
    dragLockRef.current = null;
  }, []);

  useEffect(() => {
    return () => {
      releaseDragLock();
    };
  }, [releaseDragLock]);

  // Handle drag end event to reorder blocks
  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      releaseDragLock();
      const { active, over } = event;

      if (over && active.id !== over.id) {
        const oldIndex = children.findIndex((child) => child.block.id === active.id);
        const newIndex = children.findIndex((child) => child.block.id === over.id);

        if (oldIndex !== -1 && newIndex !== -1) {
          // Calculate the new array to determine target index
          const newOrder = arrayMove(children, oldIndex, newIndex);
          const targetIndex = newOrder.findIndex((child) => child.block.id === active.id);

          // Call reorderTrackedBlock to update the environment and record operation
          reorderTrackedBlock(active.id as string, id, targetIndex);
        }
      }
    },
    [children, id, reorderTrackedBlock, releaseDragLock],
  );

  const isManualMode = effectiveMode === 'MANUAL';
  const isSortedMode = effectiveMode === 'SORTED';
  const isEmpty = children.length === 0;
  const isFiltered = processedChildren.length === 0 && children.length > 0;

  return (
    <ListPanel
      blockId={id}
      listControls={
        isUniBlock && !renderControlsInWrapper ? (
          <ListControls
            uniformBlockType={uniformBlockType}
            isUniBlock={isUniBlock}
            currentMode={effectiveMode}
            onModeChange={setActiveMode}
            activeSort={activeSort}
            setActiveSort={setActiveSort}
            activeFilters={activeFilters}
            setActiveFilters={setActiveFilters}
            filterLogic={listConfig.filterLogic || 'AND'}
          />
        ) : undefined
      }
    >
      {isEmpty && (
        <div className="p-4 text-sm text-muted-foreground">
          No items yet. Add one to get started!
        </div>
      )}

      {isFiltered && (
        <div className="p-4 text-center text-sm text-muted-foreground">
          No items match the current filters.
          <Button
            onClick={() => setActiveFilters([])}
            className="ml-2 underline"
            variant={'ghost'}
            size={'sm'}
          >
            Clear filters
          </Button>
        </div>
      )}

      {isManualMode ? (
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragEnd={handleDragEnd}
          onDragStart={handleDragStart}
          onDragCancel={releaseDragLock}
        >
          <SortableContext
            items={children.map((child) => child.block.id)}
            strategy={verticalListSortingStrategy}
          >
            <div className="flex flex-col gap-3">
              {children.map((child) => (
                <ListItem
                  key={child.block.id}
                  id={child.block.id}
                  item={child}
                  config={blockListConfig}
                  isDraggable={true}
                  render={render}
                />
              ))}
            </div>
          </SortableContext>
        </DndContext>
      ) : (
        <div className="flex flex-col gap-3">
          {processedChildren.map((child) => (
            <ListItem
              key={child.block.id}
              id={child.block.id}
              item={child}
              config={blockListConfig}
              isDraggable={false}
              render={render}
            />
          ))}
        </div>
      )}
    </ListPanel>
  );
};
