'use client';

import { OptionalTooltip } from '@/components/ui/optional-tooltip';
import { Button, Label } from '@riven/ui';
import { Upload, X, CircleAlert } from 'lucide-react';
import { cn } from '@riven/utils';
import { FC, useRef } from 'react';
import { FormWidgetProps } from '../form-widget.types';

export const FileUploadWidget: FC<FormWidgetProps<string>> = ({
  value,
  onChange,
  onBlur,
  label,
  description,
  errors,
  displayError = 'message',
  disabled,
}) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const hasErrors = errors && errors.length > 0;

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      // For now, store the file name. In production, you'd upload to a server
      // and store the URL
      onChange(file.name);
      onBlur?.();
    }
  };

  const handleClear = () => {
    onChange('');
    if (inputRef.current) {
      inputRef.current.value = '';
    }
    onBlur?.();
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
          <div className="flex items-center gap-2">
            <input
              ref={inputRef}
              id={label}
              type="file"
              onChange={handleFileChange}
              disabled={disabled}
              className="hidden"
            />
            <Button
              type="button"
              variant="outline"
              onClick={() => inputRef.current?.click()}
              disabled={disabled}
              className={cn('flex-1', hasErrors && 'border-destructive')}
            >
              <Upload className="mr-2 h-4 w-4" />
              {value || 'Choose file'}
            </Button>
            {value && (
              <Button
                type="button"
                variant="ghost"
                size="icon"
                onClick={handleClear}
                disabled={disabled}
              >
                <X className="h-4 w-4" />
              </Button>
            )}
            {displayError === 'tooltip' && hasErrors && (
              <CircleAlert className="size-4 fill-background text-destructive" />
            )}
          </div>
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
