'use client';

import { Button } from '@riven/ui/button';
import { Input } from '@riven/ui/input';
import { Popover, PopoverContent, PopoverTrigger } from '@riven/ui/popover';
import { Separator } from '@/components/ui/separator';
import { IconCell } from '@/components/ui/icon/icon-cell';
import { ColumnConfiguration, EntityType, SystemRelationshipType } from '@/lib/types/entity';
import {
  DndContext,
  DragEndEvent,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { arrayMove } from '@dnd-kit/sortable';
import { Eye, EyeOff, GripVertical, Search } from 'lucide-react';
import { FC, useMemo, useState } from 'react';

interface ColumnVisibilityPopoverProps {
  entityType: EntityType;
  columnConfiguration: ColumnConfiguration;
  onToggleVisibility: (columnId: string) => void;
  onReorder: (newOrder: string[]) => void;
  onShowAll: () => void;
  onHideAll: () => void;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  children: React.ReactNode;
}

interface ColumnInfo {
  id: string;
  name: string;
  iconType: string;
  iconColour: string;
  visible: boolean;
  isIdentifier: boolean;
}

interface SortableVisibilityItemProps {
  column: ColumnInfo;
  onToggle: (id: string) => void;
}

const SortableVisibilityItem: FC<SortableVisibilityItemProps> = ({ column, onToggle }) => {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: column.id,
    disabled: column.isIdentifier,
  });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      className="flex items-center gap-2 rounded px-2 py-1.5 hover:bg-muted/50"
    >
      <button
        type="button"
        className="cursor-grab text-muted-foreground/50 hover:text-muted-foreground"
        {...attributes}
        {...listeners}
      >
        <GripVertical className="size-3.5" />
      </button>
      <IconCell readonly type={column.iconType} colour={column.iconColour} className="size-4" />
      <span className="flex-1 truncate text-sm">{column.name}</span>
      <Button
        variant="ghost"
        size="icon"
        className="size-6"
        onClick={() => onToggle(column.id)}
        disabled={column.isIdentifier}
        title={column.isIdentifier ? 'Identifier column cannot be hidden' : undefined}
      >
        {column.visible ? (
          <Eye className="size-3.5" />
        ) : (
          <EyeOff className="size-3.5 text-muted-foreground/50" />
        )}
      </Button>
    </div>
  );
};

export const ColumnVisibilityPopover: FC<ColumnVisibilityPopoverProps> = ({
  entityType,
  columnConfiguration,
  onToggleVisibility,
  onReorder,
  onShowAll,
  onHideAll,
  open,
  onOpenChange,
  children,
}) => {
  const [search, setSearch] = useState('');

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  // Build column info list from order + entity type
  const allColumns = useMemo<ColumnInfo[]>(() => {
    const columns: ColumnInfo[] = [];
    const { order, overrides } = columnConfiguration;
    const seen = new Set<string>();

    // Process ordered columns first
    for (const id of order) {
      seen.add(id);
      const info = getColumnInfo(id, entityType, overrides[id]?.visible !== false);
      if (info) columns.push(info);
    }

    // Add any unordered columns (new attributes/relationships not yet in order)
    if (entityType.schema.properties) {
      for (const id of Object.keys(entityType.schema.properties)) {
        if (!seen.has(id)) {
          const info = getColumnInfo(id, entityType, overrides[id]?.visible !== false);
          if (info) columns.push(info);
        }
      }
    }

    entityType.relationships
      ?.filter((rel) => rel.systemType !== SystemRelationshipType.ConnectedEntities)
      .forEach((rel) => {
        if (!seen.has(rel.id)) {
          columns.push({
            id: rel.id,
            name: rel.sourceEntityTypeId !== entityType.id
              ? rel.targetRules?.find((r) => r.targetEntityTypeId === entityType.id)?.inverseName || rel.name
              : rel.name,
            iconType: rel.icon.type,
            iconColour: rel.icon.colour,
            visible: overrides[rel.id]?.visible !== false,
            isIdentifier: false,
          });
        }
      });

    return columns;
  }, [entityType, columnConfiguration]);

  // Filter by search
  const filteredColumns = useMemo(() => {
    if (!search.trim()) return allColumns;
    const searchLower = search.toLowerCase();
    return allColumns.filter((col) => col.name.toLowerCase().includes(searchLower));
  }, [allColumns, search]);

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;

    const oldIndex = allColumns.findIndex((c) => c.id === active.id);
    const newIndex = allColumns.findIndex((c) => c.id === over.id);
    if (oldIndex === -1 || newIndex === -1) return;

    const reordered = arrayMove(
      allColumns.map((c) => c.id),
      oldIndex,
      newIndex,
    );
    onReorder(reordered);
  };

  const visibleCount = allColumns.filter((c) => c.visible).length;

  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <PopoverTrigger asChild>{children}</PopoverTrigger>
      <PopoverContent className="w-72 p-0" align="end">
        <div className="p-3 pb-2">
          <p className="text-sm font-medium">Property visibility</p>
          <p className="text-xs text-muted-foreground">
            {visibleCount} of {allColumns.length} shown
          </p>
        </div>

        <div className="px-3 pb-2">
          <div className="relative">
            <Search className="absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search properties..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="h-8 pl-8 text-sm"
            />
          </div>
        </div>

        <div className="flex items-center justify-between px-3 pb-1">
          <span className="text-xs text-muted-foreground">
            {search ? 'Filtered' : 'All properties'}
          </span>
          <div className="flex gap-1">
            <Button variant="link" size="sm" className="h-auto p-0 text-xs" onClick={onShowAll}>
              Show all
            </Button>
            <span className="text-xs text-muted-foreground">/</span>
            <Button variant="link" size="sm" className="h-auto p-0 text-xs" onClick={onHideAll}>
              Hide all
            </Button>
          </div>
        </div>

        <Separator />

        <div className="max-h-64 overflow-y-auto p-1">
          {search ? (
            // When searching, show flat list (no drag)
            filteredColumns.map((col) => (
              <SortableVisibilityItem key={col.id} column={col} onToggle={onToggleVisibility} />
            ))
          ) : (
            // When not searching, allow drag reorder
            <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
              <SortableContext
                items={allColumns.map((c) => c.id)}
                strategy={verticalListSortingStrategy}
              >
                {allColumns.map((col) => (
                  <SortableVisibilityItem key={col.id} column={col} onToggle={onToggleVisibility} />
                ))}
              </SortableContext>
            </DndContext>
          )}

          {filteredColumns.length === 0 && (
            <p className="py-4 text-center text-xs text-muted-foreground">No properties found</p>
          )}
        </div>
      </PopoverContent>
    </Popover>
  );
};

function getColumnInfo(
  id: string,
  entityType: EntityType,
  visible: boolean,
): ColumnInfo | null {
  // Check if it's an attribute
  const attr = entityType.schema.properties?.[id];
  if (attr) {
    return {
      id,
      name: attr.label || id,
      iconType: attr.icon.type,
      iconColour: attr.icon.colour,
      visible,
      isIdentifier: id === entityType.identifierKey,
    };
  }

  // Check if it's a relationship
  const rel = entityType.relationships?.find((r) => r.id === id);
  if (rel) {
    const isTargetSide = rel.sourceEntityTypeId !== entityType.id;
    return {
      id,
      name: isTargetSide
        ? rel.targetRules?.find((r) => r.targetEntityTypeId === entityType.id)?.inverseName || rel.name
        : rel.name,
      iconType: rel.icon.type,
      iconColour: rel.icon.colour,
      visible,
      isIdentifier: false,
    };
  }

  return null;
}
