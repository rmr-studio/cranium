"use client";

import type { FC } from "react";
import { Input } from '@cranium/ui/input';
import { Label } from '@cranium/ui/label';
import type { ConfigWidgetProps } from "./config-widget.types";

export const NumberWidget: FC<ConfigWidgetProps<number>> = ({
  value,
  onChange,
  onBlur,
  label,
  description,
  placeholder,
  required,
  errors,
  disabled,
}) => {
  return (
    <div className="space-y-2">
      {label && (
        <Label>
          {label}
          {required && <span className="text-destructive ml-1">*</span>}
        </Label>
      )}
      <Input
        type="number"
        value={value ?? ""}
        onChange={(e) => onChange(e.target.valueAsNumber || 0)}
        onBlur={onBlur}
        placeholder={placeholder}
        disabled={disabled}
        className={errors?.length ? "border-destructive" : ""}
      />
      {description && !errors?.length && (
        <p className="text-xs text-muted-foreground">{description}</p>
      )}
      {errors?.map((error, i) => (
        <p key={i} className="text-xs text-destructive">
          {error}
        </p>
      ))}
    </div>
  );
};
