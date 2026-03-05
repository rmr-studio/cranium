'use client';

import { Input } from '@riven/ui/input';
import { Label } from '@riven/ui/label';
import { OptionalTooltip } from '@/components/ui/optional-tooltip';
import { cn } from '@riven/utils';
import { CircleAlert } from 'lucide-react';
import { FC, useState } from 'react';
import { FormWidgetProps } from '../form-widget.types';

export const CurrencyInputWidget: FC<FormWidgetProps<number>> = ({
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
  const [input, setInput] = useState('');

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    let cleaned = e.target.value.replace(/[^0-9.]/g, '');
    // Allow only one decimal point
    const parts = cleaned.split('.');
    if (parts.length > 2) {
      cleaned = parts[0] + '.' + parts.slice(1).join('');
    }
    setInput(cleaned);
  };

  const handleBlur = () => {
    // Only update if the user actually typed something, otherwise preserve original value
    if (input) {
      const parsedValue = parseFloat(input);
      onChange(parsedValue);
      setInput('');
    }
    onBlur?.();
  };

  const displayValue = input || (value ? value.toFixed(2) : '');

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
          <span className="absolute top-1/2 left-3 -translate-y-1/2 text-muted-foreground">$</span>
          <Input
            autoFocus={autoFocus}
            id={label}
            type="text"
            value={displayValue}
            onChange={handleChange}
            onBlur={handleBlur}
            placeholder={placeholder || '0.00'}
            disabled={disabled}
            className={cn('pl-7', hasErrors && 'border-destructive focus-visible:ring-destructive')}
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
