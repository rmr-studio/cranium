"use client";
import type { ChildNodeProps, ClassNameProps } from '@cranium/utils';
import type { EntityType } from "@/lib/types/entity";
import { cn } from '@cranium/utils';
import { AnimatePresence } from "framer-motion";
import React, { FC, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useBlockEdit } from "../../context/block-edit-provider";
import { useBlockEnvironment } from "../../context/block-environment-provider";
import { useRenderElement } from "../../context/block-renderer-provider";
import { useLayoutChange } from "../../context/layout-change-provider";
import { useTrackedEnvironment } from "../../context/tracked-environment-provider";
import { useFocusSurface } from "../../hooks/use-focus-surface";
import { type BlockType, isContentMetadata, isContentNode, type QuickActionItem } from "@/lib/types/block";
import { createBlockInstanceFromType } from "../../util/block/factory/instance.factory";
import { BlockForm } from "../forms/block-form";
import QuickActionModal from "../modals/quick-action-modal";
// import { ENTITY_TYPE_OPTIONS, TypePickerModal } from "../modals/type-picker-modal";
import PanelActionContextMenu from "./action/panel-action-menu";
import { PanelWrapperProvider } from "./context/panel-wrapper-provider";
import { usePanelEditMode } from "./hooks/use-panel-edit-mode";
import { usePanelKeyboardNavigation } from "./hooks/use-panel-keyboard-navigation";
import { usePanelOverlayLock } from "./hooks/use-panel-overlay-lock";
import { usePanelToolbarIndices } from "./hooks/use-panel-toolbar-indices";
import PanelToolbar, { CustomToolbarAction } from "./toolbar/panel-toolbar";

interface Props extends ChildNodeProps, ClassNameProps {
  id: string;
  title?: string;
  titlePlaceholder?: string;
  description?: string;
  display?: React.ReactNode;
  form?: React.ReactNode;
  quickActions?: QuickActionItem[];
  onTitleChange?: (value: string) => void;
  allowInsert?: boolean;
  allowEdit?: boolean;
  allowDelete?: boolean;
  onDelete?: () => void;
  customControls?: React.ReactNode;
  customActions?: CustomToolbarAction[];
}

export const PanelWrapper: FC<Props> = ({
  id,
  title,
  titlePlaceholder = 'Untitled block',
  description,
  children,
  quickActions = [],
  onTitleChange,
  onDelete,
  className,
  allowInsert = false,
  allowEdit = true,
  allowDelete = true,
  customControls,
  customActions = [],
}) => {
  const [isHovered, setIsHovered] = useState(false);
  const [isQuickOpen, setQuickOpen] = useState(false);
  const [isInlineMenuOpen, setInlineMenuOpen] = useState(false);
  const [isDetailsOpen, setDetailsOpen] = useState(false);
  const [isActionsOpen, setActionsOpen] = useState(false);
  const [draftTitle, setDraftTitle] = useState(title ?? '');
  const [insertContext, setInsertContext] = useState<'nested' | 'sibling'>('nested');
  const [toolbarFocusIndex, setToolbarFocusIndex] = useState<number>(-1); // -1 = no toolbar focus
  const inlineSearchRef = useRef<HTMLInputElement | null>(null);
  const surfaceRef = useRef<HTMLDivElement | null>(null);

  // Type picker state for blocks that need additional configuration
  const [typePickerOpen, setTypePickerOpen] = useState(false);
  const [selectedBlockType, setSelectedBlockType] = useState<BlockType | null>(null);

  // Block edit state
  const { openDrawer, drawerState, startEdit, saveAndExit } = useBlockEdit();
  const { getBlock, getChildren, workspaceId, entityType: envEntityType } = useBlockEnvironment();
  const { addTrackedBlock } = useTrackedEnvironment();
  const { suppressEditModeTracking } = useLayoutChange();
  const block = getBlock(id);
  const hasChildren = getChildren(id).length > 0;

  // Get resize function for inline edit mode
  let requestResize: (() => void) | undefined;
  try {
    const renderContext = useRenderElement();
    requestResize = renderContext?.widget.requestResize;
  } catch {
    // Not in RenderElementProvider context, resize not available
    requestResize = undefined;
  }

  // Edit mode logic extracted to hook
  const { isEditMode, handleEditClick, handleSaveEditClick, handleDiscardEditClick } =
    usePanelEditMode({
      id,
      hasChildren,
      requestResize,
    });

  const menuActions = useMemo(() => {
    if (allowDelete && onDelete && !quickActions.some((action) => action.id === 'delete')) {
      return [
        ...quickActions,
        {
          id: '__delete',
          label: 'Delete block',
          onSelect: onDelete,
        },
      ];
    }
    return quickActions;
  }, [quickActions, onDelete]);

  const hasMenuActions = menuActions.length > 0;

  // Extract allowed block types from block metadata (for block list restrictions)
  const allowedTypes = useMemo(() => {
    if (!block || !isContentNode(block)) return null;
    const payload = block.block.payload;
    if (isContentMetadata(payload) && payload.listConfig?.listType) {
      return payload.listConfig.listType;
    }
    return null;
  }, [block]);

  // Calculate toolbar button indices using hook (single source of truth)
  const toolbarIndices = usePanelToolbarIndices({
    allowInsert,
    hasMenuActions,
    customActionsCount: customActions.length,
    isEditMode,
  });

  const {
    isSelected,
    focusSelf,
    setHovered: setFocusHover,
    disableHover,
    disableSelect,
  } = useFocusSurface({
    id,
    type: 'panel',
    onDelete,
    elementRef: surfaceRef,
    focusParentOnDelete: true,
  });

  const shouldHighlight =
    isSelected || isQuickOpen || (allowInsert && isInlineMenuOpen) || isHovered;

  // Close all menus when panel loses selection
  useEffect(() => {
    if (!isSelected) {
      setToolbarFocusIndex(-1);
      setQuickOpen(false);
      setInlineMenuOpen(false);
      setDetailsOpen(false);
      setActionsOpen(false);
    }
  }, [isSelected]);

  // Close menus when toolbar focus moves away from them (but keep panel selected)
  useEffect(() => {
    if (!isSelected) return; // Only applies when panel is selected
    if (toolbarFocusIndex === -1) return; // No keyboard focus, don't close menus

    const { quickActionsIndex, insertIndex, detailsIndex, actionsMenuIndex } = toolbarIndices;

    // Close menus that don't match the current toolbar focus
    if (toolbarFocusIndex !== quickActionsIndex && isQuickOpen) {
      setQuickOpen(false);
    }
    if (toolbarFocusIndex !== insertIndex && isInlineMenuOpen) {
      setInlineMenuOpen(false);
    }
    if (toolbarFocusIndex !== detailsIndex && isDetailsOpen) {
      setDetailsOpen(false);
    }
    if (toolbarFocusIndex !== actionsMenuIndex && isActionsOpen) {
      setActionsOpen(false);
    }
  }, [
    toolbarFocusIndex,
    isSelected,
    toolbarIndices,
    isQuickOpen,
    isInlineMenuOpen,
    isDetailsOpen,
    isActionsOpen,
  ]);

  // Overlay lock management extracted to hook
  usePanelOverlayLock({
    id,
    isQuickOpen,
    isInlineMenuOpen,
    isDetailsOpen,
    isActionsOpen,
    drawerStateIsOpen: drawerState.isOpen,
    setFocusHover,
  });

  useEffect(() => {
    setDraftTitle(title ?? '');
  }, [title]);

  useEffect(() => {
    if (isInlineMenuOpen) {
      requestAnimationFrame(() => inlineSearchRef.current?.focus());
    }
  }, [isInlineMenuOpen]);

  // Keyboard navigation extracted to hook
  usePanelKeyboardNavigation({
    id,
    isSelected,
    allowInsert,
    actionsLength: quickActions.length,
    toolbarFocusIndex,
    setToolbarFocusIndex,
    setInlineMenuOpen,
    setQuickOpen,
    setDetailsOpen,
    setActionsOpen,
    handleEditClick,
    handleSaveEditClick,
    handleDiscardEditClick,
    customActions,
    toolbarIndices,
    focusSelf,
    setInsertContext,
    hasMenuActions,
    isEditMode,
    hasChildren,
    openDrawer,
    saveAndExit,
    startEdit,
    suppressEditModeTracking,
  });

  const handleTitleBlur = useCallback(() => {
    if (draftTitle !== title) onTitleChange?.(draftTitle);
  }, [draftTitle, onTitleChange, title]);

  /**
   * Handle block type selection from quick insert menu.
   * Some block types require additional configuration (entity_reference, block_list).
   */
  const handleBlockTypeSelect = useCallback(
    (blockType: BlockType) => {
      if (!allowInsert) return;

      // Close the inline menu
      setInlineMenuOpen(false);

      // Check if block type requires additional configuration
      if (blockType.key === 'entity_reference') {
        // Entity reference needs entity type selection
        setSelectedBlockType(blockType);
        setTypePickerOpen(true);
        return;
      }

      // Create and insert block directly
      const newBlock = createBlockInstanceFromType(blockType, workspaceId, {
        name: blockType.name,
      });

      addTrackedBlock(newBlock, id);
    },
    [allowInsert, workspaceId, id, addTrackedBlock],
  );

  /**
   * Handle type selection from TypePickerModal (for entity_reference blocks).
   */
  const handleTypeSelect = useCallback(
    (selectedTypes: string[] | null) => {
      // if (!selectedBlockType) return;

      // if (selectedBlockType.key === 'entity_reference') {
      //   const selectedValue = selectedTypes?.[0];
      //   const entityType =
      //     selectedValue && Object.values(EntityType).includes(selectedValue as EntityType)
      //       ? (selectedValue as EntityType)
      //       : undefined;

      //   if (!entityType) return; // Required field

      //   const newBlock = createBlockInstanceFromType(selectedBlockType, workspaceId, {
      //     name: selectedBlockType.name,
      //     entityType,
      //   });

      //   addTrackedBlock(newBlock, id);
      // }

      // setSelectedBlockType(null);
      // setTypePickerOpen(false);
    },
    [selectedBlockType, workspaceId, id, addTrackedBlock],
  );

  const handleQuickSelect = useCallback(
    (item: QuickActionItem) => {
      setQuickOpen(false);
      item.onSelect(id);
    },
    [id, setQuickOpen],
  );

  const handleMenuAction = useCallback(
    (item: QuickActionItem) => {
      item.onSelect(id);
    },
    [id],
  );

  const handleQuickActionsOpen = useCallback(() => {
    setToolbarFocusIndex(toolbarIndices.quickActionsIndex);
    setQuickOpen(true);
    focusSelf();
  }, [focusSelf, setQuickOpen, toolbarIndices.quickActionsIndex]);

  const handleInlineInsertOpen = useCallback(() => {
    if (!allowInsert) return;
    setToolbarFocusIndex(toolbarIndices.insertIndex);
    setInsertContext('nested');
    setInlineMenuOpen(true);
    focusSelf();
  }, [allowInsert, focusSelf, setInlineMenuOpen, toolbarIndices.insertIndex]);

  const handleQuickInsertOpenQuickActions = useCallback(() => {
    if (!allowInsert) return;
    setInlineMenuOpen(false);
    setQuickOpen(true);
    focusSelf();
  }, [allowInsert, focusSelf, setInlineMenuOpen, setQuickOpen]);

  const handleDetailsOpenChange = useCallback(
    (open: boolean) => {
      if (open) {
        setToolbarFocusIndex(toolbarIndices.detailsIndex);
      }
      setDetailsOpen(open);
    },
    [toolbarIndices.detailsIndex],
  );

  const handleActionsOpenChange = useCallback(
    (open: boolean) => {
      if (open) {
        setToolbarFocusIndex(toolbarIndices.actionsMenuIndex);
      }
      setActionsOpen(open);
    },
    [toolbarIndices.actionsMenuIndex],
  );

  const handleInlineMenuOpenChange = useCallback(
    (open: boolean) => {
      if (open) {
        setToolbarFocusIndex(toolbarIndices.insertIndex);
      }
      setInlineMenuOpen(open);
    },
    [toolbarIndices.insertIndex],
  );

  // Context value for PanelWrapperProvider (eliminates prop drilling to PanelToolbar)
  const contextValue = useMemo(
    () => ({
      id,
      isQuickOpen,
      setQuickOpen,
      isInlineMenuOpen,
      setInlineMenuOpen,
      isDetailsOpen,
      setDetailsOpen,
      isActionsOpen,
      setActionsOpen,
      draftTitle,
      setDraftTitle,
      onTitleChange,
      titlePlaceholder,
      toolbarFocusIndex,
      setToolbarFocusIndex,
      allowInsert,
      hasMenuActions,
      description,
      shouldHighlight,
      isEditMode,
      hasChildren,
    }),
    [
      id,
      isQuickOpen,
      isInlineMenuOpen,
      isDetailsOpen,
      isActionsOpen,
      draftTitle,
      onTitleChange,
      titlePlaceholder,
      toolbarFocusIndex,
      allowInsert,
      hasMenuActions,
      description,
      shouldHighlight,
      isEditMode,
      hasChildren,
    ],
  );

  return (
    <PanelWrapperProvider value={contextValue}>
      <PanelActionContextMenu id={id} actions={menuActions} onDelete={onDelete}>
        <div
          ref={surfaceRef}
          className={cn(
            'group relative flex w-full flex-col rounded-sm border p-4 text-card-foreground shadow backdrop-blur-sm transition-colors',
            'break-words', // Fix for text overflow - ensure long text wraps
            allowInsert
              ? shouldHighlight
                ? 'border-primary bg-card shadow-sm ring-2 ring-primary/30'
                : isHovered
                  ? 'border-primary/40 bg-card/90 shadow-sm'
                  : 'border-border bg-card/80 shadow-sm'
              : shouldHighlight
                ? 'border-primary/70 bg-background/80'
                : 'border-border/50 bg-transparent',
            className,
          )}
          data-surface-id={id}
          tabIndex={-1}
          onPointerOver={(event) => {
            if (disableHover) return;
            const targetBlock = (event.target as HTMLElement | null)?.closest('[data-block-id]');
            if (targetBlock) {
              setIsHovered(false);
              setFocusHover(false);
              return;
            }
            const targetSurface = (event.target as HTMLElement | null)?.closest(
              '[data-surface-id]',
            );
            if (!targetSurface || targetSurface === event.currentTarget) {
              setIsHovered(true);
              setFocusHover(true);
            } else {
              setIsHovered(false);
              setFocusHover(false);
            }
          }}
          onPointerLeave={() => {
            setIsHovered(false);
            setFocusHover(false);
          }}
          onPointerDown={(event) => {
            if (disableSelect) return;
            // If the pointer interaction is happening inside another block surface,
            // let the child handle its own activation.
            const targetBlock = (event.target as HTMLElement | null)?.closest('[data-block-id]');
            if (targetBlock) return;
            const targetSurface = (event.target as HTMLElement | null)?.closest(
              '[data-surface-id]',
            ) as HTMLElement | null;
            if (targetSurface && targetSurface !== event.currentTarget) return;
            focusSelf();
          }}
          onFocusCapture={() => {
            focusSelf();
          }}
        >
          <AnimatePresence key={`panel-toolbar-${id}`}>
            {shouldHighlight && (
              <PanelToolbar
                visible={shouldHighlight}
                onQuickActionsClick={handleQuickActionsOpen}
                onInlineInsertClick={allowInsert ? handleInlineInsertOpen : undefined}
                onInlineMenuOpenChange={allowInsert ? handleInlineMenuOpenChange : undefined}
                inlineSearchRef={allowInsert ? inlineSearchRef : undefined}
                workspaceId={workspaceId}
                // entityType={envEntityType}
                allowedTypes={allowedTypes}
                onSelectBlockType={allowInsert ? handleBlockTypeSelect : undefined}
                onOpenQuickActionsFromInline={
                  allowInsert ? handleQuickInsertOpenQuickActions : undefined
                }
                onTitleBlur={handleTitleBlur}
                menuActions={menuActions}
                onMenuAction={handleMenuAction}
                onDetailsOpenChange={handleDetailsOpenChange}
                onActionsOpenChange={handleActionsOpenChange}
                onEditClick={allowEdit ? handleEditClick : undefined}
                onSaveEditClick={allowEdit ? handleSaveEditClick : undefined}
                onDiscardEditClick={allowEdit ? handleDiscardEditClick : undefined}
                customActions={customActions}
              />
            )}
          </AnimatePresence>

          {/* Custom controls section (e.g., list sort/filter controls) */}
          {customControls && <div className="mb-3 border-b pb-3">{customControls}</div>}

          {isEditMode && block && isContentNode(block) ? (
            <BlockForm
              blockId={id}
              blockType={block.block.type}
              mode="inline"
              onResize={requestResize}
            />
          ) : (
            children
          )}
        </div>
      </PanelActionContextMenu>

      <QuickActionModal
        open={isQuickOpen}
        setOpen={setQuickOpen}
        onActionSelect={handleQuickSelect}
        actions={quickActions}
      />

      {/* Type picker modal for blocks that need additional configuration */}
      {selectedBlockType && (
        // <TypePickerModal
        //   open={typePickerOpen}
        //   onOpenChange={setTypePickerOpen}
        //   title="Select Entity Type"
        //   description="Choose which type of entities this block will reference"
        //   // options={ENTITY_TYPE_OPTIONS}
        //   multiSelect={false}
        //   required={true}
        //   onSelect={handleTypeSelect}
        // />
      )}
    </PanelWrapperProvider>
  );
};

PanelWrapper.displayName = 'PanelWrapper';
