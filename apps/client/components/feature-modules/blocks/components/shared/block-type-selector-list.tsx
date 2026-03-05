import { Badge, CommandEmpty, CommandGroup, CommandItem, CommandList } from '@riven/ui';
import { Skeleton } from '@/components/ui/skeleton';
import {
  BoxIcon,
  CheckSquareIcon,
  FileTextIcon,
  FolderKanbanIcon,
  LayoutGridIcon,
  LinkIcon,
  ListIcon,
  MapPinIcon,
  TypeIcon,
} from 'lucide-react';
import { FC, useMemo } from 'react';
import { categorizeBlockTypes } from '../../hooks/use-block-types';
import type { BlockType } from '@/lib/types/block';

/**
 * Props for BlockTypeSelectorList component
 */
export interface BlockTypeSelectorListProps {
  /** Array of block types to display */
  blockTypes: BlockType[] | undefined;
  /** Loading state */
  isLoading?: boolean;
  /** Error state */
  error?: Error | null;
  /** Callback when a block type is selected */
  onSelect: (blockType: BlockType) => void;
  /** Optional filter: only show these block type keys (for block list restrictions) */
  allowedTypes?: string[] | null;
  /** Optional custom empty state message */
  emptyMessage?: string;
}

/**
 * BlockTypeSelectorList - Shared component for displaying and selecting block types.
 *
 * Features:
 * - Categorized display (Layout, Content, Reference, Custom)
 * - Loading and error states
 * - Filtering by allowed types (for block list restrictions)
 * - Icons and badges for system/custom blocks
 * - Reusable in dialogs and popovers
 *
 * @example
 * // In a dialog
 * <CommandDialog open={open}>
 *   <CommandInput placeholder="Search..." />
 *   <BlockTypeSelectorList
 *     blockTypes={blockTypes}
 *     isLoading={isLoading}
 *     error={error}
 *     onSelect={handleSelect}
 *   />
 * </CommandDialog>
 *
 * @example
 * // In a popover with filtering
 * <Popover>
 *   <PopoverContent>
 *     <Command>
 *       <CommandInput placeholder="Search..." />
 *       <BlockTypeSelectorList
 *         blockTypes={blockTypes}
 *         allowedTypes={["text_block", "note", "task"]}
 *         onSelect={handleSelect}
 *       />
 *     </Command>
 *   </PopoverContent>
 * </Popover>
 */
export const BlockTypeSelectorList: FC<BlockTypeSelectorListProps> = ({
  blockTypes,
  isLoading = false,
  error = null,
  onSelect,
  allowedTypes = null,
  emptyMessage = 'No block types available.',
}) => {
  // Filter block types if allowedTypes is specified
  const filteredBlockTypes = useMemo(() => {
    if (!blockTypes) return [];
    if (!allowedTypes || allowedTypes.length === 0) return blockTypes;

    return blockTypes.filter((type) => allowedTypes.includes(type.key));
  }, [blockTypes, allowedTypes]);

  // Categorize block types for organized display
  const categories = useMemo(() => categorizeBlockTypes(filteredBlockTypes), [filteredBlockTypes]);

  // Loading state
  if (isLoading) {
    return (
      <CommandList>
        <div className="space-y-2 p-4">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      </CommandList>
    );
  }

  // Error state
  if (error) {
    return (
      <CommandList>
        <div className="p-4 text-sm text-destructive">
          Failed to load block types: {error.message}
        </div>
      </CommandList>
    );
  }

  // Empty state
  if (filteredBlockTypes.length === 0) {
    return (
      <CommandList>
        <CommandEmpty>{emptyMessage}</CommandEmpty>
      </CommandList>
    );
  }

  const handleSelect = (blockType: BlockType) => {
    onSelect(blockType);
  };

  return (
    <CommandList>
      <CommandEmpty>No matching block types found.</CommandEmpty>

      {/* Layout Blocks */}
      {categories.layout.length > 0 && (
        <CommandGroup heading="Layout">
          {categories.layout.map((type) => (
            <BlockTypeItem key={type.id} blockType={type} onSelect={handleSelect} />
          ))}
        </CommandGroup>
      )}

      {/* Content Blocks */}
      {categories.content.length > 0 && (
        <CommandGroup heading="Content">
          {categories.content.map((type) => (
            <BlockTypeItem key={type.id} blockType={type} onSelect={handleSelect} />
          ))}
        </CommandGroup>
      )}

      {/* Reference Blocks */}
      {categories.reference.length > 0 && (
        <CommandGroup heading="References">
          {categories.reference.map((type) => (
            <BlockTypeItem key={type.id} blockType={type} onSelect={handleSelect} />
          ))}
        </CommandGroup>
      )}

      {/* Custom Blocks */}
      {categories.custom.length > 0 && (
        <CommandGroup heading="Custom">
          {categories.custom.map((type) => (
            <BlockTypeItem key={type.id} blockType={type} onSelect={handleSelect} />
          ))}
        </CommandGroup>
      )}
    </CommandList>
  );
};

/**
 * Individual block type item within the command list
 */
interface BlockTypeItemProps {
  blockType: BlockType;
  onSelect: (blockType: BlockType) => void;
}

const BlockTypeItem: FC<BlockTypeItemProps> = ({ blockType, onSelect }) => {
  const icon = getBlockTypeIcon(blockType.key);

  return (
    <CommandItem onSelect={() => onSelect(blockType)} className="cursor-pointer gap-2">
      <div className="flex flex-1 items-center gap-2">
        {icon}
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <span className="font-medium">{blockType.name}</span>
            {blockType.system && (
              <Badge variant="secondary" className="text-xs">
                System
              </Badge>
            )}
          </div>
          {blockType.description && (
            <p className="mt-0.5 text-xs text-muted-foreground">{blockType.description}</p>
          )}
          <p className="mt-0.5 font-mono text-xs text-muted-foreground/60">{blockType.key}</p>
        </div>
      </div>
    </CommandItem>
  );
};

/**
 * Returns an appropriate icon for a block type based on its key
 */
function getBlockTypeIcon(key: string): React.ReactNode {
  const iconClass = 'size-4 text-muted-foreground';

  // Specific block type icons
  switch (key) {
    case 'layout_container':
      return <BoxIcon className={iconClass} />;
    case 'project_overview':
      return <FolderKanbanIcon className={iconClass} />;
    case 'note':
      return <FileTextIcon className={iconClass} />;
    case 'project_task':
      return <CheckSquareIcon className={iconClass} />;
    case 'postal_address':
      return <MapPinIcon className={iconClass} />;
    case 'block_list':
    case 'content_block_list':
      return <ListIcon className={iconClass} />;
    case 'block_reference':
    case 'entity_reference':
    case 'entity_reference_list':
      return <LinkIcon className={iconClass} />;
    default:
      // Fallback patterns
      if (key.includes('container')) {
        return <BoxIcon className={iconClass} />;
      }
      if (key.includes('layout')) {
        return <LayoutGridIcon className={iconClass} />;
      }
      if (key.includes('list')) {
        return <ListIcon className={iconClass} />;
      }
      if (key.includes('reference')) {
        return <LinkIcon className={iconClass} />;
      }
      if (key.includes('text') || key.includes('note')) {
        return <FileTextIcon className={iconClass} />;
      }
      // Default icon
      return <TypeIcon className={iconClass} />;
  }
}
