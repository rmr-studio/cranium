import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem } from '@cranium/ui/command';
import type { EntityType } from "@/lib/types/entity";
import type { BlockType } from "@/lib/types/block";
import { FC, Fragment, RefObject } from "react";
import { useBlockTypes } from "../../../hooks/use-block-types";
import { BlockTypeSelectorList } from "../../shared/block-type-selector-list";

interface PanelQuickInsertProps {
  searchRef: RefObject<HTMLInputElement | null>;
  /** Workspace ID for fetching block types */
  workspaceId: string;
  /** Entity type for contextual filtering */
  entityType?: EntityType;
  /** Optional filter: only show these block type keys (for block list restrictions) */
  allowedTypes?: string[] | null;
  /** Callback when a block type is selected */
  onSelectBlockType: (blockType: BlockType) => void;
  /** Callback to show all options (opens full dialog) */
  onShowAllOptions: () => void;
  /** Callback to open quick actions menu */
  onOpenQuickActions: () => void;
}

/**
 * PanelQuickInsert - Quick insert menu for adding blocks from toolbar.
 *
 * Features:
 * - Search and select block types
 * - Filtered by block list restrictions (allowedTypes)
 * - Categorized display (Layout, Content, Reference, Custom)
 * - Shortcuts to full dialog and quick actions
 *
 * @example
 * <Popover>
 *   <PopoverContent>
 *     <PanelQuickInsert
 *       searchRef={searchRef}
 *       workspaceId={workspaceId}
 *       allowedTypes={["note", "task", "text_block"]}
 *       onSelectBlockType={handleInsert}
 *       onShowAllOptions={openFullDialog}
 *       onOpenQuickActions={openQuickActions}
 *     />
 *   </PopoverContent>
 * </Popover>
 */
const PanelQuickInsert: FC<PanelQuickInsertProps> = ({
  searchRef,
  workspaceId,
  entityType,
  allowedTypes = null,
  onSelectBlockType,
  onShowAllOptions,
  onOpenQuickActions,
}) => {
  const { data: blockTypes, isLoading, error } = useBlockTypes(workspaceId);

  return (
    <Command>
      <CommandInput ref={searchRef} placeholder="Search blocks..." />
      {/* Shortcuts section */}
      <CommandGroup heading="Shortcuts">
        <CommandItem onSelect={onShowAllOptions}>See all options…</CommandItem>
        <CommandItem onSelect={onOpenQuickActions}>Open quick actions</CommandItem>
      </CommandGroup>

      {/* Block type list with optional filtering */}
      <BlockTypeSelectorList
        blockTypes={blockTypes}
        isLoading={isLoading}
        error={error}
        allowedTypes={allowedTypes}
        onSelect={onSelectBlockType}
        emptyMessage={
          allowedTypes && allowedTypes.length > 0
            ? 'No allowed block types for this list.'
            : 'No block types available.'
        }
      />
    </Command>
  );
};

export default PanelQuickInsert;
