'use client';

import { useState } from 'react';
import { Check, ChevronsUpDown, Link2 } from 'lucide-react';
import { Button } from '@riven/ui/button';
import { Popover, PopoverContent, PopoverTrigger } from '@riven/ui/popover';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@riven/ui/command';
import { cn } from '@riven/utils';
import type { EntityType, RelationshipDefinition } from '@/lib/types/entity';
import { attributeTypes } from '@/lib/util/form/schema.util';
import {
  type AttributeInfo,
  getAttributesFromEntityType,
  getRelationshipsFromEntityType,
} from './query-builder.utils';

interface FilterAttributePickerProps {
  entityType: EntityType;
  selectedAttributeId?: string;
  selectedRelationshipId?: string;
  onSelectAttribute: (attr: AttributeInfo) => void;
  onSelectRelationship: (rel: RelationshipDefinition) => void;
}

export function FilterAttributePicker({
  entityType,
  selectedAttributeId,
  selectedRelationshipId,
  onSelectAttribute,
  onSelectRelationship,
}: FilterAttributePickerProps) {
  const [open, setOpen] = useState(false);

  const attributes = getAttributesFromEntityType(entityType);
  const relationships = getRelationshipsFromEntityType(entityType);

  const selectedLabel = selectedAttributeId
    ? attributes.find((a) => a.id === selectedAttributeId)?.label ?? selectedAttributeId
    : selectedRelationshipId
      ? relationships.find((r) => r.id === selectedRelationshipId)?.name ??
        'Relationship'
      : undefined;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          size="sm"
          role="combobox"
          aria-expanded={open}
          className={cn(
            'h-7 justify-between gap-1 px-2.5 text-xs font-medium',
            !selectedLabel && 'text-muted-foreground',
          )}
        >
          {selectedLabel ?? 'Select field'}
          <ChevronsUpDown className="size-3 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-56 p-0" align="start">
        <Command>
          <CommandInput placeholder="Search fields..." className="h-8 text-xs" />
          <CommandList>
            <CommandEmpty>No fields found.</CommandEmpty>
            {attributes.length > 0 && (
              <CommandGroup heading="Attributes">
                {attributes.map((attr) => {
                  const typeInfo = attributeTypes[attr.schemaType];
                  return (
                    <CommandItem
                      key={attr.id}
                      value={attr.label}
                      onSelect={() => {
                        onSelectAttribute(attr);
                        setOpen(false);
                      }}
                      className="text-xs"
                    >
                      <Check
                        className={cn(
                          'mr-1.5 size-3',
                          selectedAttributeId === attr.id ? 'opacity-100' : 'opacity-0',
                        )}
                      />
                      <span className="truncate">
                        {attr.label}
                      </span>
                      <span className="text-muted-foreground ml-auto text-[10px]">
                        {typeInfo?.label ?? attr.schemaType}
                      </span>
                    </CommandItem>
                  );
                })}
              </CommandGroup>
            )}
            {relationships.length > 0 && (
              <CommandGroup heading="Relationships">
                {relationships.map((rel) => (
                  <CommandItem
                    key={rel.id}
                    value={rel.name}
                    onSelect={() => {
                      onSelectRelationship(rel);
                      setOpen(false);
                    }}
                    className="text-xs"
                  >
                    <Check
                      className={cn(
                        'mr-1.5 size-3',
                        selectedRelationshipId === rel.id ? 'opacity-100' : 'opacity-0',
                      )}
                    />
                    <Link2 className="mr-1 size-3 shrink-0 opacity-70" />
                    <span className="truncate">{rel.name}</span>
                  </CommandItem>
                ))}
              </CommandGroup>
            )}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
