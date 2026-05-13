'use client';

import { Label } from '@cranium/ui/label';
import { OptionalTooltip } from '@/components/ui/optional-tooltip';
import { PhoneInput } from '@/components/ui/phone-input';
import { cn } from '@cranium/utils';
import { CircleAlert } from 'lucide-react';
import { FC, useCallback, useEffect, useRef } from 'react';
import { FormWidgetProps } from '../form-widget.types';

export const PhoneInputWidget: FC<FormWidgetProps<string>> = ({
  value,
  onChange,
  onBlur,
  label,
  description,
  placeholder,
  errors,
  displayError = 'message',
  disabled,
  autoFocus,
}) => {
  const hasErrors = errors && errors.length > 0;
  const containerRef = useRef<HTMLDivElement>(null);
  const blurTimeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  // Handle blur with delayed check to support popover interactions
  // When clicking the country select popover, focus moves outside the container
  // but we don't want to trigger save until the popover interaction completes
  const handleBlur = useCallback(
    (e: React.FocusEvent) => {
      // Clear any existing timeout
      clearTimeout(blurTimeoutRef.current);

      // Check if focus is moving within the container
      const relatedTarget = e.relatedTarget as HTMLElement | null;
      if (relatedTarget && containerRef.current?.contains(relatedTarget)) {
        return;
      }

      // Delay blur check to allow popover to receive focus
      blurTimeoutRef.current = setTimeout(() => {
        const activeElement = document.activeElement;
        const isWithinContainer = containerRef.current?.contains(activeElement);
        // Check if focus is in a Radix popover (rendered in portal)
        const isWithinPopover = activeElement?.closest('[data-radix-popper-content-wrapper]');

        if (!isWithinContainer && !isWithinPopover) {
          onBlur?.();
        }
      }, 50);
    },
    [onBlur],
  );

  // Cancel pending blur when focus returns to container
  const handleFocus = useCallback(() => {
    clearTimeout(blurTimeoutRef.current);
  }, []);

  // Cleanup timeout on unmount
  useEffect(() => {
    return () => clearTimeout(blurTimeoutRef.current);
  }, []);

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
        <div ref={containerRef} className="relative" onBlur={handleBlur} onFocus={handleFocus}>
          <PhoneInput
            defaultCountry="AU"
            id={label}
            value={value || ''}
            onChange={(value) => onChange(value)}
            placeholder={placeholder}
            disabled={disabled}
            autoFocus={autoFocus}
            className={cn(hasErrors && 'border-destructive focus-visible:ring-destructive')}
          />
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
