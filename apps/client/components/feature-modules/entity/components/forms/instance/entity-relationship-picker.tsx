'use client';

import { Badge } from '@riven/ui/badge';
import { Button } from '@riven/ui/button';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem } from '@riven/ui/command';
import { Popover, PopoverContent, PopoverTrigger } from '@riven/ui/popover';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@riven/ui/tabs';
import { IconCell } from "@/components/ui/icon/icon-cell";
import {
    Entity,
    EntityLink,
    EntityRelationshipCardinality,
    EntityRelationshipDefinition,
    EntityType,
    isRelationshipPayload,
} from "@/lib/types/entity";
import { uuid } from "@/lib/util/utils";
import { Check, Loader2, X } from "lucide-react";
import { useParams } from "next/navigation";
import { FC, useEffect, useMemo, useState } from "react";
import { useEntityTypes } from "../../../hooks/query/type/use-entity-types";
import { useEntitiesFromManyTypes } from "../../../hooks/query/use-entities";

export interface EntityRelationshipPickerProps {
  relationship: EntityRelationshipDefinition;
  autoFocus?: boolean;
  value: EntityLink[];
  errors?: string[];
  handleBlur: () => Promise<void>;
  handleChange: (values: EntityLink[]) => void;
  handleRemove: (entityId: string) => void;
}

export const EntityRelationshipPicker: FC<EntityRelationshipPickerProps> = ({
  relationship,
  autoFocus,
  value,
  errors,
  handleBlur,
  handleChange,
  handleRemove,
}) => {
  // Allows selecting a specific entity type instead of viewin all entities
  const [selectedType, setSelectedType] = useState<string>('ALL');
  const [popoverOpen, setPopoverOpen] = useState(false);

  const { workspaceId } = useParams<{ workspaceId: string }>();
  const { data: entityTypes } = useEntityTypes(workspaceId);

  const isSingleSelect =
    relationship.cardinality === EntityRelationshipCardinality.OneToOne ||
    relationship.cardinality === EntityRelationshipCardinality.ManyToOne;

  const types: EntityType[] = useMemo(() => {
    return (
      (relationship.allowPolymorphic
        ? entityTypes
        : entityTypes?.filter((et) => (relationship.entityTypeKeys ?? []).includes(et.key))) ?? []
    );
  }, [entityTypes, relationship]);

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

  // Group each queried entity by its type
  const groupedEntities: Record<string, Entity[]> = useMemo(() => {
    return entities.reduce(
      (acc, entity) => {
        const typeId = entity.typeId;
        if (!acc[typeId]) {
          acc[typeId] = [];
        }
        acc[typeId].push(entity);
        return acc;
      },
      {} as Record<string, Entity[]>,
    );
  }, [entities, types]);

  const filteredEntities = useMemo(() => {
    if (selectedType === 'ALL') return entities;

    return groupedEntities[selectedType] || [];
  }, [groupedEntities, entities, selectedType]);

  const selectedEntities = entities.filter((entity: Entity) =>
    value.some((link) => entity.id === link.id),
  );

  const onSelectEntity = (entity: Entity) => {
    // If entity is already selected, do un-select
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
      fieldId: relationship.id,
      key: type.key,
      sourceEntityId: uuid(), // Dummy sourceEntityId; will be replaced on save
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

  const onRemoveEntity = (id: string) => {
    handleRemove(id);
  };

  const getEntityLabel = (entity: Entity): string | undefined => {
    const payload = entity.payload[entity.identifierKey].payload;
    if (isRelationshipPayload(payload)) return;
    return String(payload.value);
  };

  const getTypeLabel = (typeId: string) => {
    return entityTypeKeyIdMap[typeId]?.name.singular || 'Unknown Type';
  };

  // Auto-open popover when autoFocus is true (e.g., in table cell edit mode)
  useEffect(() => {
    if (!popoverOpen && !isLoading && !isError)
      if (autoFocus) {
        // Small delay to ensure DOM is ready
        const timer = setTimeout(() => setPopoverOpen(true), 0);
        return () => clearTimeout(timer);
      }
  }, [autoFocus, isLoading, isError]);

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" />
        Loading entities...
      </div>
    );
  }

  if (isError) {
    return <div className="text-sm text-destructive">Failed to load entities</div>;
  }

  return (
    <div className="space-y-3">
      <Popover
        open={popoverOpen}
        onOpenChange={async (isOpen) => {
          setPopoverOpen(isOpen);
          // Call onBlur when popover closes (handles both selection and click-outside)
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
            className="w-full justify-between"
          >
            <div className="flex flex-wrap gap-2">
              {selectedEntities?.map((entity) => (
                <Badge key={entity.id} variant="secondary" className="gap-1">
                  {getEntityLabel(entity)}
                  <div
                    onClick={() => onRemoveEntity(entity.id)}
                    className="ml-1 rounded-sm hover:text-destructive"
                  >
                    <X className="h-3 w-3" />
                  </div>
                </Badge>
              ))}{' '}
              {(selectedEntities?.length || 0) === 0 && (
                <span className="text-sm text-muted-foreground">Select entities...</span>
              )}
            </div>
          </Button>
        </PopoverTrigger>

        <PopoverContent className="mt-2 w-[360px] p-0" align="end">
          <Command>
            {/* Tabs for filtering by entity type */}

            <Tabs value={selectedType} onValueChange={setSelectedType}>
              {/* Only show tab list if there are multiple types */}
              {types.length > 1 && (
                <TabsList>
                  <TabsTrigger value="ALL">All</TabsTrigger>

                  {types.map((type) => (
                    <TabsTrigger key={type.id} value={type.id}>
                      {type.name.singular}
                    </TabsTrigger>
                  ))}
                </TabsList>
              )}
              <TabsContent value={selectedType} className="mt-2">
                <CommandInput placeholder="Search entities..." />
                <CommandEmpty>No entities found.</CommandEmpty>

                <CommandGroup>
                  {filteredEntities.map((entity) => {
                    const isSelected = value.some((link) => link.id === entity.id);

                    const { type, colour } = entity.icon ??
                      entityTypeKeyIdMap[entity.typeId]?.icon ?? {
                        type: 'FILE',
                        colour: 'NEUTRAL',
                      };

                    return (
                      <CommandItem
                        key={entity.id}
                        value={getEntityLabel(entity)}
                        onSelect={() => onSelectEntity(entity)}
                      >
                        <div className="flex w-full items-center justify-between">
                          <div className="flex items-center">
                            <IconCell
                              readonly
                              iconType={type}
                              colour={colour}
                              className="mr-2 size-6"
                            />
                            <div>
                              <div>{getEntityLabel(entity)}</div>
                              <div className="text-xs text-muted-foreground">
                                {getTypeLabel(entity.typeId)}
                              </div>
                            </div>
                          </div>

                          {isSelected && <Check className="h-4 w-4 opacity-100" />}
                        </div>
                      </CommandItem>
                    );
                  })}
                </CommandGroup>
              </TabsContent>
            </Tabs>
          </Command>
        </PopoverContent>
      </Popover>

      {errors && <p className="text-sm text-destructive">{errors}</p>}
    </div>
  );
};
