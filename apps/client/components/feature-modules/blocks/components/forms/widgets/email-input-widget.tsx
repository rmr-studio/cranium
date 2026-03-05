'use client';

import { Input } from '@riven/ui/input';
import { Label } from '@riven/ui/label';
import { OptionalTooltip } from '@/components/ui/optional-tooltip';
import { cn } from '@riven/utils';
import { CircleAlert } from 'lucide-react';
import { FC } from 'react';
import { FormWidgetProps } from '../form-widget.types';

export const EmailInputWidget: FC<FormWidgetProps<string>> = ({
  value,
  onChange,
  onBlur,
  label,
  description,
  placeholder,
  errors,
  autoFocus,
  displayError = 'message',
  disabled,
}) => {
  const hasErrors = errors && errors.length > 0;

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
          <Input
            id={label}
            type="email"
            autoFocus={autoFocus}
            value={value || ''}
            onChange={(e) => onChange(e.target.value)}
            onBlur={onBlur}
            placeholder={placeholder || 'email@example.com'}
            disabled={disabled}
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
