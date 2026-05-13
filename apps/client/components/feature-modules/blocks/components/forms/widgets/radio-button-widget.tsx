'use client';

import { OptionalTooltip } from '@/components/ui/optional-tooltip';
import { Label } from '@cranium/ui/label';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { cn } from '@cranium/utils';
import { CircleAlert } from 'lucide-react';
import { FC } from 'react';
import { FormWidgetProps } from '../form-widget.types';

export const RadioButtonWidget: FC<FormWidgetProps<string>> = ({
  value,
  onChange,
  onBlur,
  label,
  description,
  errors,
  displayError = 'message',
  disabled,
  options = [],
}) => {
  const hasErrors = errors && errors.length > 0;

  return (
    <OptionalTooltip
      content={errors?.join(', ') || ''}
      disabled={displayError !== 'tooltip' || !hasErrors}
    >
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          {label && <Label className={cn(hasErrors && 'text-destructive')}>{label}</Label>}
          {displayError === 'tooltip' && hasErrors && (
            <CircleAlert className="size-4 fill-background text-destructive" />
          )}
        </div>
        {description && <p className="text-sm text-muted-foreground">{description}</p>}
        <RadioGroup
          value={value || ''}
          onValueChange={(newValue) => {
            onChange(newValue);
            onBlur?.();
          }}
          disabled={disabled}
        >
          {options.map((option) => (
            <div key={option.value} className="flex items-center space-x-2">
              <RadioGroupItem value={option.value} id={`${label}-${option.value}`} />
              <Label htmlFor={`${label}-${option.value}`} className="cursor-pointer font-normal">
                {option.label}
              </Label>
            </div>
          ))}
        </RadioGroup>
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
