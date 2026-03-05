'use client';

import { OptionalTooltip } from '@/components/ui/optional-tooltip';
import { Label } from '@riven/ui';
import { Slider } from '@/components/ui/slider';
import { cn } from '@riven/utils';
import { CircleAlert } from 'lucide-react';
import { FC } from 'react';
import { FormWidgetProps } from '../form-widget.types';

export const SliderWidget: FC<FormWidgetProps<number>> = ({
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
        <div className="flex items-center justify-between">
          {label && (
            <Label htmlFor={label} className={cn(hasErrors && 'text-destructive')}>
              {label}
            </Label>
          )}
          <div className="flex items-center gap-2">
            <span className="text-sm text-muted-foreground">{value || 0}</span>
            {displayError === 'tooltip' && hasErrors && (
              <CircleAlert className="size-4 fill-background text-destructive" />
            )}
          </div>
        </div>
        {description && <p className="text-sm text-muted-foreground">{description}</p>}
        <Slider
          id={label}
          value={[value || 0]}
          onValueChange={([newValue]) => onChange(newValue)}
          onValueCommit={() => onBlur?.()}
          disabled={disabled}
          min={0}
          max={100}
          step={1}
          className={cn(hasErrors && 'accent-destructive')}
        />
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
