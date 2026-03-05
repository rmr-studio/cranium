'use client';

import { DataType, IconColour, IconType, SchemaType } from '@/lib/types/common';
import { AttributeSchemaType, attributeTypes } from '@/lib/util/form/schema.util';
import { cn } from '@riven/utils';
import { Check, ChevronsUpDown } from 'lucide-react';
import { Dispatch, FC, SetStateAction, useMemo } from 'react';
import { Button } from './button';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from './command';
import { FormControl } from './form';
import { IconCell } from './icon/icon-cell';
import { Popover, PopoverContent, PopoverTrigger } from './popover';

interface Props {
  disabled?: boolean;
  type: SchemaType | 'RELATIONSHIP';
  onChange: (value: SchemaType | 'RELATIONSHIP') => void;
  open: boolean;
  setOpen: Dispatch<SetStateAction<boolean>>;
}

export type AttributeKey = DataType | 'RELATIONSHIP';

export const AttributeTypeDropdown: FC<Props> = ({
  onChange,
  type,
  open,
  setOpen,
  disabled = false,
}) => {
  const commonAttributes: AttributeSchemaType[] = useMemo(() => {
    return [
      attributeTypes.TEXT,
      attributeTypes.NUMBER,
      attributeTypes.SELECT,
      attributeTypes.MULTI_SELECT,
      attributeTypes.CHECKBOX,
      attributeTypes.DATETIME,
    ];
  }, []);

  const groupedAttributes = useMemo(() => {
    const groups: Partial<Record<DataType, AttributeSchemaType[]>> = {
      [DataType.String]: [],
      [DataType.Number]: [],
      [DataType.Boolean]: [],
      [DataType.Object]: [],
      [DataType.Array]: [],
    };

    Object.values(attributeTypes).forEach((attr) => {
      if (groups[attr.type]) {
        groups[attr.type]!.push(attr);
      }
    });

    return groups;
  }, []);

  const selectedAttribute = useMemo(() => {
    if (type === 'RELATIONSHIP') {
      return {
        label: 'Relationship',
        icon: {
          type: IconType.Link2,
          colour: IconColour.Neutral,
        },
      };
    }

    return attributeTypes[type];
  }, [type]);

  const getGroupName = (type: DataType): string => {
    switch (type) {
      case DataType.String:
        return 'Text';
      case DataType.Number:
        return 'Number';
      case DataType.Boolean:
        return 'Boolean';
      case DataType.Object:
        return 'Object';
      case DataType.Array:
        return 'Array';
      default:
        return type;
    }
  };

  return (
    <Popover modal={true} onOpenChange={setOpen} open={open}>
      <FormControl>
        <PopoverTrigger asChild>
          <Button
            variant="outline"
            role="combobox"
            className="w-full justify-between"
            disabled={disabled || open}
          >
            <div className="flex items-center gap-2">
              <IconCell
                readonly
                className="mr-1 size-4"
                type={selectedAttribute.icon.type}
                colour={selectedAttribute.icon.colour}
              />
              <span>{selectedAttribute.label}</span>
            </div>
            <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
          </Button>
        </PopoverTrigger>
      </FormControl>
      <PopoverContent
        className="w-[400px] p-0"
        align="start"
        portal={true}
        onEscapeKeyDown={() => setOpen(false)}
      >
        <Command>
          <CommandInput placeholder="Search attribute types..." />
          <CommandList>
            <CommandEmpty>No attribute type found.</CommandEmpty>
            <CommandGroup heading="Relationship">
              <CommandItem
                className="px-0 text-sm text-primary/80"
                value="relationship"
                onSelect={() => {
                  onChange('RELATIONSHIP');
                  setOpen(false);
                }}
              >
                <Check
                  className={cn(
                    'mr-1 size-3.5',
                    type === 'RELATIONSHIP' ? 'opacity-100' : 'opacity-0',
                  )}
                />
                <IconCell readonly type={IconType.Link2} colour={IconColour.Neutral} />
                Relationship
              </CommandItem>
            </CommandGroup>
            <CommandGroup heading="Common Types">
              {commonAttributes.map((attr) => (
                <CommandItem
                  className="px-0 text-sm text-primary/80"
                  key={`common-${attr.key}`}
                  value={attr.key}
                  onSelect={() => {
                    onChange(attr.key);
                    setOpen(false);
                  }}
                >
                  <Check
                    className={cn('mr-1 size-3.5', type === attr.key ? 'opacity-100' : 'opacity-0')}
                  />
                  <IconCell
                    readonly
                    className="mr-1 size-4"
                    type={attr.icon.type}
                    colour={attr.icon.colour}
                  />
                  {attr.label}
                </CommandItem>
              ))}
            </CommandGroup>

            {Object.entries(groupedAttributes).map(
              ([groupKey, attrs]) =>
                attrs &&
                attrs.length > 0 && (
                  <CommandGroup key={groupKey} heading={getGroupName(groupKey as DataType)}>
                    {attrs.map((attr) => (
                      <CommandItem
                        className="px-0 text-sm text-primary/80"
                        key={attr.key}
                        value={attr.key}
                        onSelect={() => {
                          onChange(attr.key);
                          setOpen(false);
                        }}
                      >
                        <Check
                          className={cn(
                            'mr-1 size-3.5',
                            type === attr.key ? 'opacity-100' : 'opacity-0',
                          )}
                        />
                        <IconCell
                          readonly
                          className="mr-1 size-4"
                          type={attr.icon.type}
                          colour={attr.icon.colour}
                        />
                        {attr.label}
                      </CommandItem>
                    ))}
                  </CommandGroup>
                ),
            )}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
};
