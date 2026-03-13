'use client';

import { useState } from 'react';
import { Check, ChevronsUpDown } from 'lucide-react';
import { Input } from '@riven/ui/input';
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
import { SchemaType } from '@/lib/types/models/SchemaType';
import { FilterOperator } from '@/lib/types/models/FilterOperator';
import type { SchemaUUID } from '@/lib/types/models/SchemaUUID';
import { isNullaryOperator, isMultiValueOperator } from './query-builder.utils';

interface FilterValueInputProps {
  schemaType: SchemaType;
  schema?: SchemaUUID;
  operator?: FilterOperator;
  value: unknown;
  onChange: (value: unknown) => void;
}

export function FilterValueInput({
  schemaType,
  schema,
  operator,
  value,
  onChange,
}: FilterValueInputProps) {
  if (!operator || isNullaryOperator(operator)) return null;

  // Checkbox: operator IS the value (Equals true / Equals false)
  if (schemaType === SchemaType.Checkbox) {
    return <CheckboxValueSelect value={value as boolean} onChange={onChange} />;
  }

  // Select enum values
  if (
    (schemaType === SchemaType.Select || schemaType === SchemaType.MultiSelect) &&
    schema?.options?._enum
  ) {
    if (isMultiValueOperator(operator) || schemaType === SchemaType.MultiSelect) {
      return (
        <MultiEnumSelect
          options={schema.options._enum}
          value={(value as string[]) ?? []}
          onChange={onChange}
        />
      );
    }
    return (
      <SingleEnumSelect
        options={schema.options._enum}
        value={value as string}
        onChange={onChange}
      />
    );
  }

  // Multi-value text input for IN/NOT_IN
  if (isMultiValueOperator(operator)) {
    return (
      <Input
        type="text"
        placeholder="value1, value2, ..."
        className="h-7 min-w-32 text-xs"
        value={Array.isArray(value) ? (value as string[]).join(', ') : (value as string) ?? ''}
        onChange={(e) => {
          const parts = e.target.value.split(',').map((s) => s.trim()).filter(Boolean);
          onChange(parts);
        }}
      />
    );
  }

  // Number inputs
  if (
    schemaType === SchemaType.Number ||
    schemaType === SchemaType.Currency ||
    schemaType === SchemaType.Percentage ||
    schemaType === SchemaType.Rating
  ) {
    return (
      <Input
        type="number"
        placeholder="Value"
        className="h-7 w-24 text-xs"
        value={value != null ? String(value) : ''}
        onChange={(e) => {
          const num = e.target.value === '' ? undefined : Number(e.target.value);
          onChange(num);
        }}
      />
    );
  }

  // Date inputs
  if (schemaType === SchemaType.Date) {
    return (
      <Input
        type="date"
        className="h-7 w-36 text-xs"
        value={(value as string) ?? ''}
        onChange={(e) => onChange(e.target.value || undefined)}
      />
    );
  }
  if (schemaType === SchemaType.Datetime) {
    return (
      <Input
        type="datetime-local"
        className="h-7 w-44 text-xs"
        value={(value as string) ?? ''}
        onChange={(e) => onChange(e.target.value || undefined)}
      />
    );
  }

  // Default: text input
  return (
    <Input
      type="text"
      placeholder="Value"
      className="h-7 min-w-32 text-xs"
      value={(value as string) ?? ''}
      onChange={(e) => onChange(e.target.value || undefined)}
    />
  );
}

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

function CheckboxValueSelect({
  value,
  onChange,
}: {
  value: boolean;
  onChange: (v: boolean) => void;
}) {
  const [open, setOpen] = useState(false);
  const label = value === true ? 'true' : value === false ? 'false' : 'Select...';

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          size="sm"
          className="h-7 w-20 justify-between px-2.5 text-xs font-medium"
        >
          {label}
          <ChevronsUpDown className="size-3 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-28 p-0" align="start">
        <Command>
          <CommandList>
            <CommandGroup>
              <CommandItem onSelect={() => { onChange(true); setOpen(false); }} className="text-xs">
                <Check className={cn('mr-1.5 size-3', value === true ? 'opacity-100' : 'opacity-0')} />
                true
              </CommandItem>
              <CommandItem onSelect={() => { onChange(false); setOpen(false); }} className="text-xs">
                <Check className={cn('mr-1.5 size-3', value === false ? 'opacity-100' : 'opacity-0')} />
                false
              </CommandItem>
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}

function SingleEnumSelect({
  options,
  value,
  onChange,
}: {
  options: string[];
  value: string;
  onChange: (v: string) => void;
}) {
  const [open, setOpen] = useState(false);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          size="sm"
          className={cn(
            'h-7 w-auto min-w-24 justify-between px-2.5 text-xs font-medium',
            !value && 'text-muted-foreground',
          )}
        >
          {value || 'Select...'}
          <ChevronsUpDown className="size-3 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-48 p-0" align="start">
        <Command>
          <CommandInput placeholder="Search..." className="h-8 text-xs" />
          <CommandList>
            <CommandEmpty>No options.</CommandEmpty>
            <CommandGroup>
              {options.map((opt) => (
                <CommandItem
                  key={opt}
                  value={opt}
                  onSelect={() => { onChange(opt); setOpen(false); }}
                  className="text-xs"
                >
                  <Check className={cn('mr-1.5 size-3', value === opt ? 'opacity-100' : 'opacity-0')} />
                  {opt}
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}

function MultiEnumSelect({
  options,
  value,
  onChange,
}: {
  options: string[];
  value: string[];
  onChange: (v: string[]) => void;
}) {
  const [open, setOpen] = useState(false);

  const toggle = (opt: string) => {
    const next = value.includes(opt) ? value.filter((v) => v !== opt) : [...value, opt];
    onChange(next);
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          size="sm"
          className={cn(
            'h-7 w-auto min-w-24 justify-between px-2.5 text-xs font-medium',
            value.length === 0 && 'text-muted-foreground',
          )}
        >
          {value.length > 0 ? `${value.length} selected` : 'Select...'}
          <ChevronsUpDown className="size-3 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-48 p-0" align="start">
        <Command>
          <CommandInput placeholder="Search..." className="h-8 text-xs" />
          <CommandList>
            <CommandEmpty>No options.</CommandEmpty>
            <CommandGroup>
              {options.map((opt) => (
                <CommandItem
                  key={opt}
                  value={opt}
                  onSelect={() => toggle(opt)}
                  className="text-xs"
                >
                  <Check
                    className={cn(
                      'mr-1.5 size-3',
                      value.includes(opt) ? 'opacity-100' : 'opacity-0',
                    )}
                  />
                  {opt}
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
