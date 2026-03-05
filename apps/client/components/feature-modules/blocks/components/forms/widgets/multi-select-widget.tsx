'use client';

import { Badge } from '@riven/ui/badge';
import { Button } from '@riven/ui/button';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem } from '@riven/ui/command';
import { Label } from '@riven/ui/label';
import { Popover, PopoverContent, PopoverTrigger } from '@riven/ui/popover';
import { OptionalTooltip } from '@/components/ui/optional-tooltip';
import { cn } from '@riven/utils';
import { Check, ChevronsUpDown, CircleAlert, X } from 'lucide-react';
import { FC, useState, useEffect } from 'react';
import { FormWidgetProps } from '../form-widget.types';

export const MultiSelectWidget: FC<FormWidgetProps<string[]>> = ({
  value = [],
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

  const selectedOptions = options.filter((opt) => value.includes(opt.value));

  const handleSelect = (selectedValue: string) => {
    const newValue = value.includes(selectedValue)
      ? value.filter((v) => v !== selectedValue)
      : [...value, selectedValue];
    onChange(newValue);
  };

  const handleRemove = (valueToRemove: string, e: React.MouseEvent) => {
    e.stopPropagation();
    onChange(value.filter((v) => v !== valueToRemove));
  };

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
                  'h-auto min-h-10 w-full justify-between',
                  !value.length && 'text-muted-foreground',
                  hasErrors && 'border-destructive',
                )}
              >
                <div className="flex flex-1 flex-wrap gap-1">
                  {selectedOptions.length > 0 ? (
                    selectedOptions.map((option) => (
                      <Badge key={option.value} variant="secondary" className="mr-1">
                        {option.label}

                        <div
                          className="ml-1 rounded-full outline-none focus:ring-2 focus:ring-ring focus:ring-offset-1"
                          onClick={(e) => handleRemove(option.value, e)}
                        >
                          <X className="h-3 w-3" />
                          <span className="sr-only">Remove {option.label}</span>
                        </div>
                      </Badge>
                    ))
                  ) : (
                    <span>{placeholder || 'Select options...'}</span>
                  )}
                </div>
                <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-full p-0" align="start">
              <Command>
                <CommandInput placeholder="Search..." />
                <CommandEmpty>No option found.</CommandEmpty>
                <CommandGroup className="max-h-64 overflow-auto">
                  {options.map((option) => {
                    const isSelected = value.includes(option.value);
                    return (
                      <CommandItem
                        key={option.value}
                        value={option.value}
                        onSelect={() => {
                          handleSelect(option.value);
                        }}
                      >
                        <Check
                          className={cn('mr-2 h-4 w-4', isSelected ? 'opacity-100' : 'opacity-0')}
                        />
                        {option.label}
                      </CommandItem>
                    );
                  })}
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
