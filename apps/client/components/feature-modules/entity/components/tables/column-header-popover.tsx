'use client';

import { Input } from '@cranium/ui/input';
import { Popover, PopoverContent, PopoverAnchor } from '@cranium/ui/popover';
import { cn } from '@cranium/utils';
import { Separator } from '@/components/ui/separator';
import { useDataTableActions } from '@/components/ui/data-table';
import {
  EntityAttributeDefinition,
  EntityPropertyType,
  EntityType,
  EntityTypeDefinition,
  isRelationshipDefinition,
  RelationshipDefinition,
} from '@/lib/types/entity';
import {
  ArrowDown,
  ArrowLeftToLine,
  ArrowRightToLine,
  ArrowUp,
  EyeOff,
  Loader2,
  Settings2,
  Trash2,
} from 'lucide-react';
import { FC, useCallback, useEffect, useRef, useState } from 'react';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { useSaveDefinitionMutation } from '../../hooks/mutation/type/use-save-definition-mutation';
import { EntityRow } from './entity-table-utils';

interface ColumnHeaderPopoverProps {
  columnId: string | null;
  entityType: EntityType;
  workspaceId: string;
  anchorEl: HTMLElement | null;
  onClose: () => void;
  onEditProperties: (definition: EntityTypeDefinition) => void;
  onDelete: (definition: EntityTypeDefinition) => void;
  onInsert: (position: 'left' | 'right', referenceColumnId: string) => void;
  onHide: (columnId: string) => void;
}

export const ColumnHeaderPopover: FC<ColumnHeaderPopoverProps> = ({
  columnId,
  entityType,
  workspaceId,
  anchorEl,
  onClose,
  onEditProperties,
  onDelete,
  onInsert,
  onHide,
}) => {
  const { setSorting } = useDataTableActions<EntityRow>();
  const [renameValue, setRenameValue] = useState('');
  const [isRenaming, setIsRenaming] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const { mutateAsync: saveDefinition } = useSaveDefinitionMutation(workspaceId);

  // Resolve the column to its definition
  const columnInfo = getColumnDefinitionInfo(columnId, entityType);

  // Sync rename value when popover opens
  useEffect(() => {
    if (columnId && columnInfo) {
      setRenameValue(columnInfo.name);
    }
  }, [columnId, columnInfo]);

  // Focus input when popover opens
  useEffect(() => {
    if (columnId) {
      setTimeout(() => inputRef.current?.select(), 100);
    }
  }, [columnId]);

  const handleRename = useCallback(async () => {
    if (!columnId || !columnInfo || renameValue.trim() === columnInfo.name || !renameValue.trim()) {
      return;
    }

    setIsRenaming(true);
    try {
      const definition = columnInfo.definition;
      if (isRelationshipDefinition(definition)) {
        await saveDefinition({
          definition: {
            ...definition,
            name: renameValue.trim(),
          },
        });
      } else {
        await saveDefinition({
          definition: {
            ...definition,
            schema: {
              ...definition.schema,
              label: renameValue.trim(),
            },
          },
        });
      }
    } finally {
      setIsRenaming(false);
    }
  }, [columnId, columnInfo, renameValue, saveDefinition]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleRename();
    }
    if (e.key === 'Escape') {
      onClose();
    }
  };

  const isIdentifier = columnId === entityType.identifierKey;
  const isProtected = columnInfo?.isProtected ?? false;
  const isTargetSide = columnInfo?.isTargetSide ?? false;

  if (!columnId || !columnInfo) return null;

  return (
    <Popover open={!!columnId} onOpenChange={(open) => !open && onClose()}>
      <PopoverAnchor virtualRef={{ current: anchorEl }} />
      <PopoverContent className="w-56 p-0" align="start" sideOffset={4}>
        {/* Inline rename */}
        <div className="p-2 pb-1">
          <div className="relative">
            <Input
              ref={inputRef}
              value={renameValue}
              onChange={(e) => setRenameValue(e.target.value)}
              onBlur={handleRename}
              onKeyDown={handleKeyDown}
              className="h-8 text-sm"
              disabled={isRenaming || isTargetSide}
            />
            {isRenaming && (
              <Loader2 className="absolute top-1/2 right-2 size-3.5 -translate-y-1/2 animate-spin text-muted-foreground" />
            )}
          </div>
        </div>

        <div className="p-1">
          <PopoverMenuItem
            icon={Settings2}
            label="Edit properties"
            onClick={() => {
              onEditProperties({
                id: columnId,
                type: columnInfo.propertyType,
                definition: columnInfo.definition,
              });
              onClose();
            }}
          />

          <Separator className="my-1" />

          <PopoverMenuItem
            icon={ArrowUp}
            label="Sort ascending"
            onClick={() => {
              setSorting([{ id: columnId, desc: false }]);
              onClose();
            }}
          />
          <PopoverMenuItem
            icon={ArrowDown}
            label="Sort descending"
            onClick={() => {
              setSorting([{ id: columnId, desc: true }]);
              onClose();
            }}
          />

          <Separator className="my-1" />

          <PopoverMenuItem
            icon={EyeOff}
            label="Hide column"
            onClick={() => {
              onHide(columnId);
              onClose();
            }}
            disabled={isIdentifier}
            title={isIdentifier ? 'Identifier column cannot be hidden' : undefined}
          />

          <Separator className="my-1" />

          <PopoverMenuItem
            icon={ArrowLeftToLine}
            label="Insert left"
            onClick={() => {
              onInsert('left', columnId);
              onClose();
            }}
          />
          <PopoverMenuItem
            icon={ArrowRightToLine}
            label="Insert right"
            onClick={() => {
              onInsert('right', columnId);
              onClose();
            }}
          />

          <Separator className="my-1" />

          <PopoverMenuItem
            icon={Trash2}
            label="Delete property"
            destructive
            onClick={() => {
              onDelete({
                id: columnId,
                type: columnInfo.propertyType,
                definition: columnInfo.definition,
              });
              onClose();
            }}
            disabled={isIdentifier || isProtected || isTargetSide}
            title={
              isIdentifier
                ? 'Identifier column cannot be deleted'
                : isTargetSide
                  ? 'Managed by source entity type'
                  : undefined
            }
          />
        </div>
      </PopoverContent>
    </Popover>
  );
};

// Helper component for menu items
interface PopoverMenuItemProps {
  icon: FC<{ className?: string }>;
  label: string;
  onClick: () => void;
  destructive?: boolean;
  disabled?: boolean;
  title?: string;
}

const PopoverMenuItem: FC<PopoverMenuItemProps> = ({
  icon: Icon,
  label,
  onClick,
  destructive,
  disabled,
  title,
}) => {
  const button = (
    <button
      type="button"
      className={cn(
        'flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none transition-colors',
        destructive ? 'text-destructive hover:bg-destructive/10' : 'hover:bg-accent',
        disabled ? 'opacity-50' : 'cursor-default',
      )}
      onClick={onClick}
      disabled={disabled}
    >
      <Icon className="size-4" />
      <span>{label}</span>
    </button>
  );

  if (disabled && title) {
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <span className="block">{button}</span>
        </TooltipTrigger>
        <TooltipContent side="left" className="text-xs">
          {title}
        </TooltipContent>
      </Tooltip>
    );
  }

  return button;
};

// Helper to resolve column ID to definition info
function getColumnDefinitionInfo(
  columnId: string | null,
  entityType: EntityType,
): {
  name: string;
  propertyType: EntityPropertyType;
  definition: EntityAttributeDefinition | RelationshipDefinition;
  isProtected: boolean;
  isTargetSide: boolean;
} | null {
  if (!columnId) return null;

  // Check attributes
  const attr = entityType.schema.properties?.[columnId];
  if (attr) {
    return {
      name: attr.label || columnId,
      propertyType: EntityPropertyType.Attribute,
      definition: { id: columnId, schema: attr },
      isProtected: attr._protected ?? false,
      isTargetSide: false,
    };
  }

  // Check relationships
  const rel = entityType.relationships?.find((r) => r.id === columnId);
  if (rel) {
    const isTargetSide = rel.sourceEntityTypeId !== entityType.id;
    return {
      name: isTargetSide
        ? rel.targetRules?.find((r) => r.targetEntityTypeId === entityType.id)?.inverseName || rel.name
        : rel.name,
      propertyType: EntityPropertyType.Relationship,
      definition: rel,
      isProtected: rel._protected ?? false,
      isTargetSide,
    };
  }

  return null;
}
