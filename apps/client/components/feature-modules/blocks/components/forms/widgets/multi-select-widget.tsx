'use client';

import { Badge } from '@riven/ui/badge';
import { Label } from '@riven/ui/label';
import { Popover, PopoverContent, PopoverAnchor } from '@riven/ui/popover';
import { OptionalTooltip } from '@/components/ui/optional-tooltip';
import { cn } from '@riven/utils';
import { Check, CircleAlert, Plus, X } from 'lucide-react';
import { FC, useCallback, useEffect, useRef, useState } from 'react';
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
  const [inputValue, setInputValue] = useState('');
  const [highlightedIndex, setHighlightedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  const hasErrors = errors && errors.length > 0;
  const safeValue = value ?? [];
  const trimmedInput = inputValue.trim().toLowerCase();

  // Merge schema options with any user-created values so they appear in the dropdown
  const allOptions = [
    ...options,
    ...safeValue
      .filter((v) => !options.some((opt) => opt.value === v))
      .map((v) => ({ label: v, value: v })),
  ];

  // Filter options based on input
  const filteredOptions = allOptions.filter(
    (opt) => !trimmedInput || opt.label.toLowerCase().includes(trimmedInput),
  );

  const canCreate =
    trimmedInput.length > 0 &&
    !allOptions.some((opt) => opt.label.toLowerCase() === trimmedInput) &&
    !safeValue.some((v) => v.toLowerCase() === trimmedInput);

  // Build the selectable items list: filtered options + optional create
  const selectableItems = [
    ...filteredOptions.map((opt) => ({ type: 'option' as const, option: opt })),
    ...(canCreate ? [{ type: 'create' as const, option: { label: inputValue.trim(), value: inputValue.trim() } }] : []),
  ];

  // Auto-open when autoFocus
  useEffect(() => {
    if (autoFocus && !disabled) {
      const timer = setTimeout(() => {
        setOpen(true);
        inputRef.current?.focus();
      }, 0);
      return () => clearTimeout(timer);
    }
  }, [autoFocus, disabled]);

  // Reset highlight when filtered list changes
  useEffect(() => {
    setHighlightedIndex(0);
  }, [inputValue]);

  // Scroll highlighted item into view
  useEffect(() => {
    if (!listRef.current) return;
    const items = listRef.current.querySelectorAll('[data-item]');
    items[highlightedIndex]?.scrollIntoView({ block: 'nearest' });
  }, [highlightedIndex]);

  const handleClose = useCallback(() => {
    setOpen(false);
    setInputValue('');
    onBlur?.();
  }, [onBlur]);

  const handleToggle = useCallback(
    (optionValue: string) => {
      const newValue = safeValue.includes(optionValue)
        ? safeValue.filter((v) => v !== optionValue)
        : [...safeValue, optionValue];
      onChange(newValue);
      setInputValue('');
      inputRef.current?.focus();
    },
    [safeValue, onChange],
  );

  const handleCreate = useCallback(
    (newTag: string) => {
      const trimmed = newTag.trim();
      if (!trimmed || safeValue.includes(trimmed)) return;
      onChange([...safeValue, trimmed]);
      setInputValue('');
      inputRef.current?.focus();
    },
    [safeValue, onChange],
  );

  const handleRemove = useCallback(
    (valueToRemove: string, e: React.MouseEvent) => {
      e.preventDefault();
      e.stopPropagation();
      onChange(safeValue.filter((v) => v !== valueToRemove));
      inputRef.current?.focus();
    },
    [safeValue, onChange],
  );

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      handleClose();
      return;
    }

    if (e.key === 'Backspace' && !inputValue && safeValue.length > 0) {
      onChange(safeValue.slice(0, -1));
      return;
    }

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlightedIndex((i) => Math.min(i + 1, selectableItems.length - 1));
      return;
    }

    if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlightedIndex((i) => Math.max(i - 1, 0));
      return;
    }

    if (e.key === 'Enter') {
      e.preventDefault();
      e.stopPropagation();
      const item = selectableItems[highlightedIndex];
      if (!item) return;
      if (item.type === 'create') {
        handleCreate(item.option.value);
      } else {
        handleToggle(item.option.value);
      }
    }
  };

  const isInline = !label && !description;
  const selectedOptions = allOptions.filter((opt) => safeValue.includes(opt.value));

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
              if (isOpen) {
                setOpen(true);
                inputRef.current?.focus();
              } else {
                handleClose();
              }
            }}
          >
            <PopoverAnchor asChild>
              <div
                ref={containerRef}
                onClick={() => {
                  if (!disabled) {
                    setOpen(true);
                    inputRef.current?.focus();
                  }
                }}
                className={cn(
                  'flex flex-wrap items-center gap-1 rounded-md bg-transparent text-sm',
                  isInline
                    ? 'min-h-8 px-1.5 py-1'
                    : 'min-h-10 border border-input px-2 py-1.5 shadow-xs dark:bg-input/30',
                  !isInline && 'focus-within:border-ring focus-within:ring-[3px] focus-within:ring-ring/50',
                  hasErrors && !isInline && 'border-destructive',
                  disabled && 'cursor-not-allowed opacity-50',
                )}
              >
                {selectedOptions.map((option) => (
                  <Badge key={option.value} variant="secondary" className="gap-1 py-0.5 pr-1 pl-2 text-xs">
                    {option.label}
                    {!disabled && (
                      <span
                        role="button"
                        tabIndex={-1}
                        onPointerDown={(e) => {
                          e.stopPropagation();
                          e.preventDefault();
                        }}
                        onClick={(e) => handleRemove(option.value, e)}
                        className="rounded-full p-0.5 transition-colors hover:bg-foreground/10"
                      >
                        <X className="size-2.5" />
                      </span>
                    )}
                  </Badge>
                ))}
                <input
                  ref={inputRef}
                  value={inputValue}
                  onChange={(e) => {
                    setInputValue(e.target.value);
                    if (!open) setOpen(true);
                  }}
                  onKeyDown={handleKeyDown}
                  onFocus={() => {
                    if (!open && !disabled) setOpen(true);
                  }}
                  disabled={disabled}
                  placeholder={safeValue.length === 0 ? (placeholder || 'Select options...') : ''}
                  className="min-w-16 flex-1 border-none bg-transparent py-0.5 text-sm outline-none placeholder:text-muted-foreground"
                />
              </div>
            </PopoverAnchor>
            <PopoverContent
              className="w-[var(--radix-popover-trigger-width)] p-0"
              align="start"
              sideOffset={4}
              onOpenAutoFocus={(e) => e.preventDefault()}
              onCloseAutoFocus={(e) => e.preventDefault()}
              onInteractOutside={(e) => {
                // Allow clicks within the anchor container
                if (containerRef.current?.contains(e.target as Node)) {
                  e.preventDefault();
                }
              }}
            >
              <div className="max-h-60 overflow-y-auto p-1" ref={listRef}>
                {filteredOptions.length === 0 && !canCreate && (
                  <div className="py-4 text-center text-sm text-muted-foreground">
                    {options.length === 0 ? 'Type to create an option' : 'No options found'}
                  </div>
                )}
                {filteredOptions.length > 0 && (
                  <p className="px-2 py-1.5 text-xs text-muted-foreground">
                    Select an option{options.length === 0 || canCreate ? ' or create one' : ''}
                  </p>
                )}
                {filteredOptions.map((option, idx) => {
                  const isSelected = safeValue.includes(option.value);
                  return (
                    <button
                      key={option.value}
                      data-item
                      type="button"
                      onPointerDown={(e) => e.preventDefault()}
                      onClick={() => handleToggle(option.value)}
                      onMouseEnter={() => setHighlightedIndex(idx)}
                      className={cn(
                        'flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-sm outline-none transition-colors',
                        highlightedIndex === idx && 'bg-accent',
                      )}
                    >
                      <Check
                        className={cn(
                          'size-3.5 shrink-0 transition-opacity',
                          isSelected ? 'opacity-100' : 'opacity-0',
                        )}
                      />
                      <Badge variant="secondary" className="text-xs">
                        {option.label}
                      </Badge>
                    </button>
                  );
                })}
                {canCreate && (
                  <button
                    data-item
                    type="button"
                    onPointerDown={(e) => e.preventDefault()}
                    onClick={() => handleCreate(inputValue)}
                    onMouseEnter={() => setHighlightedIndex(filteredOptions.length)}
                    className={cn(
                      'flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-sm outline-none transition-colors',
                      highlightedIndex === filteredOptions.length && 'bg-accent',
                    )}
                  >
                    <Plus className="size-3.5 shrink-0" />
                    <span className="flex items-center gap-1.5">
                      Create
                      <Badge variant="secondary" className="text-xs">
                        {inputValue.trim()}
                      </Badge>
                    </span>
                  </button>
                )}
              </div>
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
