'use client';

import { Button } from '@riven/ui/button';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem } from '@riven/ui/command';
import { Label } from '@riven/ui/label';
import { Popover, PopoverContent, PopoverTrigger } from '@riven/ui/popover';
import { OptionalTooltip } from '@/components/ui/optional-tooltip';
import { cn } from '@riven/utils';
import { Check, ChevronsUpDown, CircleAlert } from 'lucide-react';
import { FC, useEffect, useState } from 'react';
import { FormWidgetProps } from '../form-widget.types';

export const DropdownWidget: FC<FormWidgetProps<string>> = ({
  value,
  onChange,
  onBlur,
  label,
  description,
  placeholder,
  errors,
  displayError = 'message',
  disabled,
  options = [],
  autoFocus,
}) => {
  const [open, setOpen] = useState(false);
  const hasErrors = errors && errors.length > 0;

  // Auto-open popover when autoFocus is true (e.g., in table cell edit mode)
  useEffect(() => {
    if (autoFocus && !disabled) {
      // Small delay to ensure DOM is ready
      const timer = setTimeout(() => setOpen(true), 0);
      return () => clearTimeout(timer);
    }
  }, [autoFocus, disabled]);

  const selectedOption = options.find((opt) => opt.value === value);

  return (
    <OptionalTooltip
      content={errors?.join(', ') || ''}
      disabled={displayError !== 'tooltip' || !hasErrors}
    >
      <div className="space-y-2">
        {label && (
          <Label htmlFor={label} className={cn(hasErrors && 'text-destructive')}>
            {label}
          </Label>
        )}
        {description && <p className="text-sm text-muted-foreground">{description}</p>}
        <div className="relative">
          <Popover
            open={open}
            onOpenChange={(isOpen) => {
              setOpen(isOpen);
              // Call onBlur when popover closes (handles both selection and click-outside)
              if (!isOpen) {
                onBlur?.();
              }
            }}
          >
            <PopoverTrigger asChild>
              <Button
                variant="outline"
                role="combobox"
                aria-expanded={open}
                disabled={disabled}
                className={cn(
                  'w-full justify-between',
                  !value && 'text-muted-foreground',
                  hasErrors && 'border-destructive',
                )}
              >
                {selectedOption?.label || placeholder || 'Select option...'}
                <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-full p-0">
              <Command>
                <CommandInput placeholder="Search..." />
                <CommandEmpty>No option found.</CommandEmpty>
                <CommandGroup>
                  {options.map((option) => (
                    <CommandItem
                      key={option.value}
                      value={option.label}
                      onSelect={() => {
                        onChange(option.value);
                        setOpen(false);
                      }}
                    >
                      <Check
                        className={cn(
                          'mr-2 h-4 w-4',
                          value === option.value ? 'opacity-100' : 'opacity-0',
                        )}
                      />
                      {option.label}
                    </CommandItem>
                  ))}
                </CommandGroup>
              </Command>
            </PopoverContent>
          </Popover>
          {displayError === 'tooltip' && hasErrors && (
            <CircleAlert className="absolute -right-1 -bottom-1 size-4 fill-background text-destructive" />
          )}
        </div>
        {displayError === 'message' && hasErrors && (
          <div className="space-y-1">
            {errors.map((error, idx) => (
              <p key={idx} className="text-sm text-destructive">
                {error}
              </p>
            ))}
          </div>
        )}
      </div>
    </OptionalTooltip>
  );
};
