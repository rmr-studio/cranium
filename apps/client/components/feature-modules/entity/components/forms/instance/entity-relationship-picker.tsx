'use client';

import { IconCell } from '@/components/ui/icon/icon-cell';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Entity,
  EntityLink,
  EntityRelationshipCardinality,
  EntityType,
  isRelationshipPayload,
  RelationshipDefinition,
} from '@/lib/types/entity';
import { uuid } from '@/lib/util/utils';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@riven/ui/tooltip';
import { Button } from '@riven/ui/button';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem } from '@riven/ui/command';
import { Popover, PopoverContent, PopoverTrigger } from '@riven/ui/popover';
import { ScrollArea } from '@riven/ui/scroll-area';
import { cn } from '@riven/utils';
import { Check, ChevronDown, Minus, X } from 'lucide-react';
import { useParams } from 'next/navigation';
import { FC, useEffect, useMemo, useState } from 'react';
import { useEntityTypes } from '@/components/feature-modules/entity/hooks/query/type/use-entity-types';
import { useEntitiesFromManyTypes } from '@/components/feature-modules/entity/hooks/query/use-entities';
import { getConstrainedEntities } from '@/components/feature-modules/entity/util/relationship-constraint.util';

export interface EntityRelationshipPickerProps {
  relationship: RelationshipDefinition;
  isTargetSide?: boolean;
  autoFocus?: boolean;
  value: EntityLink[];
  errors?: string[];
  sourceEntityId?: string;
  handleBlur: () => Promise<void>;
  handleChange: (values: EntityLink[]) => void;
  handleRemove: (entityId: string) => void;
}

export const EntityRelationshipPicker: FC<EntityRelationshipPickerProps> = ({
  relationship,
  isTargetSide,
  autoFocus,
  value,
  errors,
  sourceEntityId,
  handleBlur,
  handleChange,
  handleRemove,
}) => {
  const [selectedType, setSelectedType] = useState<string>('ALL');
  const [popoverOpen, setPopoverOpen] = useState(false);

  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { data: entityTypes } = useEntityTypes(workspaceId);

  const isSingleSelect =
    relationship.cardinalityDefault === EntityRelationshipCardinality.OneToOne ||
    relationship.cardinalityDefault === EntityRelationshipCardinality.ManyToOne;

  const types: EntityType[] = useMemo(() => {
    if (!entityTypes) return [];

    if (isTargetSide) {
      return entityTypes.filter((et) => et.id === relationship.sourceEntityTypeId);
    }

    if (relationship.allowPolymorphic) return entityTypes;

    const targetTypeIds = new Set(
      relationship.targetRules
        .filter((rule) => rule.targetEntityTypeId)
        .map((rule) => rule.targetEntityTypeId),
    );

    const semanticGroups = new Set(
      relationship.targetRules
        .filter((rule) => rule.semanticTypeConstraint)
        .map((rule) => rule.semanticTypeConstraint),
    );

    return entityTypes.filter(
      (et) => targetTypeIds.has(et.id) || semanticGroups.has(et.semanticGroup),
    );
  }, [entityTypes, relationship, isTargetSide]);

  const {
    data: entities = [],
    isLoading,
    isError,
  } = useEntitiesFromManyTypes(
    workspaceId,
    types.map((type) => type.id),
  );

  const entityTypeKeyIdMap: Record<string, EntityType> = useMemo(() => {
    return types.reduce(
      (acc, type) => {
        acc[type.id] = type;
        return acc;
      },
      {} as Record<string, EntityType>,
    );
  }, [entityTypes]);

  const selectedIds = useMemo(() => new Set(value.map((link) => link.id)), [value]);

  const constrainedEntities = useMemo(
    () => getConstrainedEntities(entities, relationship, !!isTargetSide, sourceEntityId),
    [entities, relationship, isTargetSide, sourceEntityId],
  );

  const selectedEntities = entities.filter((entity: Entity) => selectedIds.has(entity.id));

  const filteredEntities = useMemo(() => {
    const base = selectedType === 'ALL' ? entities : entities.filter((e) => e.typeId === selectedType);
    // Exclude already-selected entities — they appear in the "selected" section
    return base.filter((e) => !selectedIds.has(e.id));
  }, [entities, selectedType, selectedIds]);

  const onSelectEntity = (entity: Entity) => {
    if (value.some((link) => link.id === entity.id)) {
      handleChange(value.filter((link) => link.id !== entity.id));
      return;
    }

    const type: EntityType | undefined = entityTypes?.find((et) => et.id === entity.typeId);
    if (!type) return;

    const label = getEntityLabel(entity);
    if (!label) return;

    const link: EntityLink = {
      id: entity.id,
      workspaceId: workspaceId,
      definitionId: relationship.id,
      key: type.key,
      sourceEntityId: uuid(),
      icon: entity.icon ?? type.icon,
      label,
    };

    if (isSingleSelect) {
      handleChange([link]);
      setPopoverOpen(false);
      handleBlur();
      return;
    }

    if (value.some((link) => link.id === entity.id)) return;

    handleChange([...value, link]);
  };

  const getEntityLabel = (entity: Entity): string | undefined => {
    const payload = entity.payload[entity.identifierKey].payload;
    if (isRelationshipPayload(payload)) return;
    return String(payload.value);
  };

  // Auto-open popover when autoFocus is true (e.g., in table cell edit mode)
  // No loading gate — popover opens immediately, loading is shown inside
  useEffect(() => {
    if (!popoverOpen && autoFocus) {
      const timer = setTimeout(() => setPopoverOpen(true), 0);
      return () => clearTimeout(timer);
    }
  }, [autoFocus]);

  // Resolve the active type name for the filter indicator
  const activeTypeName = useMemo(() => {
    if (selectedType === 'ALL') return null;
    return entityTypeKeyIdMap[selectedType]?.name.singular ?? null;
  }, [selectedType, entityTypeKeyIdMap]);

  return (
    <div>
      <Popover
        open={popoverOpen}
        onOpenChange={async (isOpen) => {
          setPopoverOpen(isOpen);
          if (!isOpen) {
            await handleBlur();
          }
        }}
      >
        <PopoverTrigger asChild>
          <Button
            variant="outline"
            role="combobox"
            aria-expanded={popoverOpen}
            className="h-auto min-h-9 w-full justify-between"
          >
            <div className="flex flex-wrap gap-1">
              {value.length > 0 ? (
                value.map((link) => (
                  <span
                    key={link.id}
                    className="inline-flex items-center gap-1 rounded-sm bg-secondary px-1.5 py-0.5 text-xs font-medium"
                  >
                    <IconCell
                      readonly
                      type={link.icon?.type}
                      colour={link.icon?.colour}
                      className="size-3"
                    />
                    {link.label}
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        e.preventDefault();
                        handleRemove(link.id);
                      }}
                      onPointerDown={(e) => e.stopPropagation()}
                      className="ml-0.5 cursor-pointer rounded-sm opacity-50 transition-opacity hover:opacity-100"
                    >
                      <X className="size-3" />
                    </button>
                  </span>
                ))
              ) : (
                <span className="text-sm text-muted-foreground">Select entities...</span>
              )}
            </div>
            <ChevronDown className="size-3.5 shrink-0 opacity-40" />
          </Button>
        </PopoverTrigger>

        <PopoverContent
          className="w-72 p-0"
          align="start"
          onKeyDown={(e) => {
            if (e.key === 'Escape' || e.key === 'Enter') {
              e.stopPropagation();
            }
          }}
        >
          <Command>
            {/* Search with type filter indicator */}
            <div className="relative flex items-center">
              <CommandInput placeholder="Search..." />
              {types.length > 1 && (
                <div className="absolute right-2 top-1/2 -translate-y-1/2">
                  <TypeFilterDropdown
                    types={types}
                    selectedType={selectedType}
                    activeTypeName={activeTypeName}
                    entityTypeKeyIdMap={entityTypeKeyIdMap}
                    onSelect={setSelectedType}
                  />
                </div>
              )}
            </div>

            {/* Selected items section */}
            {!isSingleSelect && value.length > 0 && (
              <div className="border-b">
                <div className="flex items-center justify-between px-3 py-1.5">
                  <span className="text-xs text-muted-foreground">
                    {value.length} selected
                  </span>
                </div>
                <div className="px-1 pb-1.5">
                  {value.map((link) => {
                    const entity = selectedEntities.find((e) => e.id === link.id);
                    const icon = link.icon ??
                      (entity ? entityTypeKeyIdMap[entity.typeId]?.icon : undefined) ?? {
                        type: 'FILE' as const,
                        colour: 'NEUTRAL' as const,
                      };

                    return (
                      <div
                        key={link.id}
                        className="group flex items-center justify-between rounded-md px-2 py-1 hover:bg-accent"
                      >
                        <div className="flex items-center gap-2 overflow-hidden">
                          <IconCell
                            readonly
                            type={icon.type}
                            colour={icon.colour}
                            className="size-4 shrink-0"
                          />
                          <span className="truncate text-sm">{link.label}</span>
                        </div>
                        <button
                          type="button"
                          aria-label={`Remove ${link.label}`}
                          onClick={() => handleRemove(link.id)}
                          className="flex size-5 shrink-0 items-center justify-center rounded opacity-0 transition-opacity hover:bg-muted group-hover:opacity-100"
                        >
                          <Minus className="size-3.5 text-muted-foreground" />
                        </button>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Entity list with internal loading */}
            {isLoading ? (
              <div className="space-y-1 p-2">
                {Array.from({ length: 5 }).map((_, i) => (
                  <div key={i} className="flex items-center gap-2 px-2 py-1">
                    <Skeleton className="size-4 rounded" />
                    <Skeleton
                      className={cn(
                        'h-3.5 rounded',
                        ['w-1/2', 'w-2/3', 'w-3/5', 'w-1/3', 'w-3/4'][i % 5],
                      )}
                    />
                  </div>
                ))}
              </div>
            ) : isError ? (
              <div className="px-3 py-4 text-center text-sm text-destructive">
                Failed to load entities
              </div>
            ) : (
              <ScrollArea className="max-h-56">
                <CommandEmpty className="py-4 text-center text-xs text-muted-foreground">
                  No entities found.
                </CommandEmpty>
                <CommandGroup className="p-1">
                  {filteredEntities.map((entity) => {
                    const isSelected = value.some((link) => link.id === entity.id);
                    const constraint = constrainedEntities.get(entity.id);
                    const isConstrained = !!constraint;
                    const { type, colour } = entity.icon ??
                      entityTypeKeyIdMap[entity.typeId]?.icon ?? {
                        type: 'FILE' as const,
                        colour: 'NEUTRAL' as const,
                      };

                    const item = (
                      <CommandItem
                        key={entity.id}
                        value={getEntityLabel(entity)}
                        disabled={isConstrained}
                        onSelect={() => !isConstrained && onSelectEntity(entity)}
                        className={cn(
                          'flex items-center justify-between gap-2 rounded-md px-2 py-1',
                          isConstrained && 'cursor-not-allowed opacity-50',
                        )}
                      >
                        <div className="flex min-w-0 items-center gap-2">
                          <IconCell
                            readonly
                            type={type}
                            colour={colour}
                            className="size-4 shrink-0"
                          />
                          <span className="truncate text-sm">{getEntityLabel(entity)}</span>
                          {isConstrained && (
                            <span className="shrink-0 rounded bg-muted px-1.5 py-0.5 text-xs leading-none text-muted-foreground">
                              {constraint.linkedLabel}
                            </span>
                          )}
                        </div>
                        {isSelected && (
                          <Check className="size-3.5 shrink-0 text-muted-foreground" />
                        )}
                      </CommandItem>
                    );

                    if (isConstrained) {
                      return (
                        <TooltipProvider key={entity.id} delayDuration={300}>
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <div>{item}</div>
                            </TooltipTrigger>
                            <TooltipContent side="right">
                              <p className="text-xs">{constraint.reason}</p>
                            </TooltipContent>
                          </Tooltip>
                        </TooltipProvider>
                      );
                    }

                    return item;
                  })}
                </CommandGroup>
              </ScrollArea>
            )}
          </Command>
        </PopoverContent>
      </Popover>

      {errors && <p className="text-sm text-destructive">{errors}</p>}
    </div>
  );
};

/**
 * Inline type filter dropdown — shown as a compact indicator to the right of search
 */
const TypeFilterDropdown: FC<{
  types: EntityType[];
  selectedType: string;
  activeTypeName: string | null;
  entityTypeKeyIdMap: Record<string, EntityType>;
  onSelect: (typeId: string) => void;
}> = ({ types, selectedType, activeTypeName, entityTypeKeyIdMap, onSelect }) => {
  const [open, setOpen] = useState(false);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          type="button"
          className={cn(
            'mr-2 flex shrink-0 items-center gap-1 rounded-md px-2 py-0.5 text-xs transition-colors',
            activeTypeName
              ? 'bg-secondary font-medium text-secondary-foreground'
              : 'text-muted-foreground hover:text-foreground',
          )}
        >
          {activeTypeName && (
            <IconCell
              readonly
              type={entityTypeKeyIdMap[selectedType]?.icon.type}
              colour={entityTypeKeyIdMap[selectedType]?.icon.colour}
              className="size-3"
            />
          )}
          {activeTypeName ?? 'Filter'}
          <ChevronDown className="size-3 opacity-50" />
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-40 p-1" align="end" side="bottom">
        <button
          type="button"
          onClick={() => {
            onSelect('ALL');
            setOpen(false);
          }}
          className={cn(
            'flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm transition-colors hover:bg-accent',
            selectedType === 'ALL' && 'bg-accent',
          )}
        >
          All types
        </button>
        {types.map((type) => (
          <button
            key={type.id}
            type="button"
            onClick={() => {
              onSelect(type.id);
              setOpen(false);
            }}
            className={cn(
              'flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm transition-colors hover:bg-accent',
              selectedType === type.id && 'bg-accent',
            )}
          >
            <IconCell
              readonly
              type={type.icon.type}
              colour={type.icon.colour}
              className="size-3.5"
            />
            {type.name.singular}
          </button>
        ))}
      </PopoverContent>
    </Popover>
  );
};
