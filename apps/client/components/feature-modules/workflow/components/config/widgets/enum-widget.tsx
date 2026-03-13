"use client";

import type { FC } from "react";
import { Label } from '@riven/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@riven/ui/select';
import type { ConfigWidgetProps } from "./config-widget.types";

export const EnumWidget: FC<ConfigWidgetProps<string>> = ({
  value,
  onChange,
  label,
  description,
  placeholder,
  required,
  errors,
  options,
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
      <Select value={value ?? ""} onValueChange={onChange} disabled={disabled}>
        <SelectTrigger className={errors?.length ? "border-destructive" : ""}>
          <SelectValue placeholder={placeholder ?? "Select..."} />
        </SelectTrigger>
        <SelectContent>
          {options &&
            Object.entries(options).map(([key, displayLabel]) => (
              <SelectItem key={key} value={key}>
                {displayLabel}
              </SelectItem>
            ))}
        </SelectContent>
      </Select>
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
