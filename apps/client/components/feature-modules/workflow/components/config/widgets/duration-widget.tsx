"use client";

import type { FC } from "react";
import { Input } from '@riven/ui/input';
import { Label } from '@riven/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { ConfigWidgetProps } from "./config-widget.types";

const DURATION_UNITS = {
  s: "Seconds",
  m: "Minutes",
  h: "Hours",
  d: "Days",
};

/**
 * Duration widget for time-based configuration (e.g., "30m", "1h", "7d")
 * Stores value as string in ISO 8601 duration-like format
 */
export const DurationWidget: FC<ConfigWidgetProps<string>> = ({
  value,
  onChange,
  label,
  description,
  required,
  errors,
  disabled,
}) => {
  // Parse existing value like "30m" into number and unit
  const match = (value ?? "").match(/^(\d+)([smhd])?$/);
  const numericValue = match ? parseInt(match[1], 10) : 0;
  const unit = match?.[2] ?? "m";

  const handleNumberChange = (num: number) => {
    onChange(`${num}${unit}`);
  };

  const handleUnitChange = (newUnit: string) => {
    onChange(`${numericValue}${newUnit}`);
  };

  return (
    <div className="space-y-2">
      {label && (
        <Label>
          {label}
          {required && <span className="text-destructive ml-1">*</span>}
        </Label>
      )}
      <div className="flex gap-2">
        <Input
          type="number"
          value={numericValue}
          onChange={(e) => handleNumberChange(e.target.valueAsNumber || 0)}
          disabled={disabled}
          className={`flex-1 ${errors?.length ? "border-destructive" : ""}`}
          min={0}
        />
        <Select value={unit} onValueChange={handleUnitChange} disabled={disabled}>
          <SelectTrigger className="w-28">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {Object.entries(DURATION_UNITS).map(([key, displayLabel]) => (
              <SelectItem key={key} value={key}>
                {displayLabel}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
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
