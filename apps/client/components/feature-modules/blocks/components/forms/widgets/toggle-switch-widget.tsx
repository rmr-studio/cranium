'use client';

import { OptionalTooltip } from '@/components/ui/optional-tooltip';
import { Switch } from '@/components/ui/switch';
import { Label } from '@riven/ui/label';
import { cn } from '@riven/utils';
import { CircleAlert } from 'lucide-react';
import { FC } from 'react';
import { FormWidgetProps } from '../form-widget.types';

export const ToggleSwitchWidget: FC<FormWidgetProps<boolean>> = ({
  value,
  onChange,
  onBlur,
  label,
  description,
  errors,
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
        <div className="flex items-center space-x-2">
          <Switch
            id={label}
            checked={value || false}
            onCheckedChange={(checked) => {
              onChange(checked);
              onBlur?.();
            }}
            disabled={disabled}
          />
          <Label htmlFor={label} className={cn('cursor-pointer', hasErrors && 'text-destructive')}>
            {label}
          </Label>
          {displayError === 'tooltip' && hasErrors && (
            <CircleAlert className="size-4 fill-background text-destructive" />
          )}
        </div>
        {description && <p className="ml-12 text-sm text-muted-foreground">{description}</p>}
        {displayError === 'message' && hasErrors && (
          <div className="ml-12 space-y-1">
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
